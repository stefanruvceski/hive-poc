package app.stride.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.data.IntervalsApi
import app.stride.data.Store
import app.stride.data.toUserMessage
import app.stride.model.AppData
import app.stride.model.Interval
import app.stride.model.SRC_GARMIN
import app.stride.model.SRC_MANUAL
import app.stride.model.Settings
import app.stride.model.StrengthSession
import app.stride.model.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // One-shot snackbar messages; a StateFlow would drop repeats and race with "consume".
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _intervals = MutableStateFlow<Map<String, List<Interval>>>(emptyMap())
    val intervals: StateFlow<Map<String, List<Interval>>> = _intervals.asStateFlow()

    init {
        val d = _data.value
        val last = d.lastSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (d.settings.apiKey.isNotBlank() && (last == null || Duration.between(last, LocalDateTime.now()).toMinutes() > 10)) {
            sync(silent = true)
        }
    }

    fun toast(text: String) {
        _messages.tryEmit(text)
    }

    private fun update(f: (AppData) -> AppData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { store.save(_data.value) }
        }
    }

    private fun api(): IntervalsApi? {
        val s = _data.value.settings
        if (s.apiKey.isBlank()) return null
        return IntervalsApi(s.apiKey.trim(), s.athleteId.trim())
    }

    fun sync(silent: Boolean = false) {
        val api = api()
        if (api == null) {
            if (!silent) toast("Add your intervals.icu API key in Settings first.")
            return
        }
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val today = LocalDate.now()
                val oldest = today.minusDays(84)
                val acts = api.activities(oldest, today.plusDays(1))
                val well = runCatching { api.wellness(today.minusDays(180), today) }.getOrDefault(emptyList())
                update { d ->
                    val kept = d.activities.filter { a -> a.date()?.isBefore(oldest) ?: false }
                    val byDate = d.weights.associateBy { it.date }.toMutableMap()
                    for (w in well) {
                        val kg = w.weight ?: continue
                        val existing = byDate[w.id]
                        if (existing == null || existing.source != SRC_MANUAL) byDate[w.id] = WeightEntry(w.id, kg, SRC_GARMIN)
                    }
                    d.copy(
                        activities = (kept + acts).distinctBy { it.id },
                        weights = byDate.values.sortedBy { it.date },
                        lastSync = LocalDateTime.now().toString(),
                    )
                }
                if (!silent) toast("Synced ${acts.size} workouts from the last 12 weeks")
            } catch (e: Exception) {
                toast(e.toUserMessage())
            } finally {
                _syncing.value = false
            }
        }
    }

    fun testConnection(apiKey: String, athleteId: String) {
        toast("Testing connection…")
        viewModelScope.launch {
            try {
                val name = IntervalsApi(apiKey.trim(), athleteId.trim()).athleteName()
                toast("Connected: $name")
            } catch (e: Exception) {
                toast(e.toUserMessage())
            }
        }
    }

    fun loadIntervals(activityId: String) {
        if (_intervals.value.containsKey(activityId)) return
        val api = api() ?: return
        viewModelScope.launch {
            try {
                val list = api.intervals(activityId)
                _intervals.value = _intervals.value + (activityId to list)
            } catch (e: Exception) {
                _intervals.value = _intervals.value + (activityId to emptyList())
            }
        }
    }

    fun saveSettings(s: Settings) {
        val keyChanged = s.apiKey != _data.value.settings.apiKey || s.athleteId != _data.value.settings.athleteId
        update { it.copy(settings = s) }
        if (keyChanged && s.apiKey.isNotBlank()) sync()
    }

    fun saveStrength(s: StrengthSession) {
        update { d -> d.copy(strength = (d.strength.filter { it.id != s.id } + s).sortedBy { it.date }) }
    }

    fun deleteStrength(id: String) {
        update { d -> d.copy(strength = d.strength.filter { it.id != id }) }
    }

    fun addWeight(date: LocalDate, kg: Double, pushToIntervals: Boolean) {
        update { d ->
            d.copy(weights = (d.weights.filter { it.date != date.toString() } + WeightEntry(date.toString(), kg, SRC_MANUAL)).sortedBy { it.date })
        }
        if (pushToIntervals) {
            val api = api() ?: return
            viewModelScope.launch {
                try {
                    api.putWeight(date, kg)
                    toast("Weight saved and sent to intervals.icu")
                } catch (e: Exception) {
                    toast("Saved in the app, but not sent: " + e.toUserMessage())
                }
            }
        } else {
            toast("Weight saved")
        }
    }

    fun deleteWeight(date: String) {
        update { d -> d.copy(weights = d.weights.filter { it.date != date }) }
    }
}
