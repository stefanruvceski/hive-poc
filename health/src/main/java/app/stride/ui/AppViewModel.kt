package app.stride.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.data.IntervalsApi
import app.stride.data.Store
import app.stride.data.DataEvents
import app.stride.data.Backup
import app.stride.data.HealthSync
import app.stride.coach.CoachClient
import app.stride.garmin.GarminClient
import app.stride.garmin.GarminWorkout
import app.stride.coach.CoachPrompt
import app.stride.data.Sync
import app.stride.data.toUserMessage
import app.stride.domain.dateShort
import app.stride.domain.dayLong
import app.stride.domain.mondayOf
import app.stride.domain.swapDays
import app.stride.domain.weekPlan
import app.stride.widget.Widgets
import app.stride.model.AppData
import app.stride.model.Interval
import app.stride.model.SRC_GARMIN
import app.stride.model.SRC_MANUAL
import app.stride.model.PlanDay
import app.stride.model.Settings
import app.stride.model.LayoutPrefs
import app.stride.model.NotifType
import app.stride.model.ShareStyle
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

    // Declared before init: init runs sync(), which reaches these (properties below init are still null then).
    private val garmin by lazy { GarminClient(getApplication()) }
    private val _autoMoves = MutableStateFlow<String?>(null)
    /** How the last fetch of auto-detected moves went (Settings → Garmin), null before the first. */
    val autoMovesStatus: StateFlow<String?> = _autoMoves.asStateFlow()

    init {
        // Notification actions (tick off a set from the lock screen) write the store directly.
        viewModelScope.launch {
            DataEvents.changed.collect { _data.value = kotlinx.coroutines.withContext(Dispatchers.IO) { store.load() } }
        }
        val d = _data.value
        val last = d.lastSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (d.settings.apiKey.isNotBlank() && (last == null || Duration.between(last, LocalDateTime.now()).toMinutes() > 10)) {
            sync(silent = true)
        } else {
            syncHealth()
        }
    }

    fun toast(text: String) {
        _messages.tryEmit(text)
    }

    private fun update(f: (AppData) -> AppData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { store.save(_data.value) }
            Widgets.refresh(getApplication<Application>())
        }
    }

    private fun api(): IntervalsApi? {
        val s = _data.value.settings
        if (s.apiKey.isBlank()) return null
        return IntervalsApi(s.apiKey.trim(), s.athleteId.trim())
    }

    fun sync(silent: Boolean = false) {
        syncHealth(silent)
        syncAutoMoves()
        val api = api()
        if (api == null) {
            if (!silent && !_data.value.settings.healthConnect) toast("Add your intervals.icu API key in Settings first.")
            return
        }
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val result = Sync.fetch(api)
                val acts = result.activities
                val strava = result.stravaSkipped
                update { d -> result.apply(d) }
                if (!silent) when {
                    acts.isEmpty() && strava > 0 -> toast(
                        "intervals.icu has $strava workouts from Strava, but doesn't share Strava data with other apps. " +
                            "In intervals.icu → Settings, connect Garmin Connect directly.",
                    )
                    acts.isEmpty() -> toast(
                        "intervals.icu has no workouts from the last 12 weeks. " +
                            "Check that Garmin Connect is connected in intervals.icu → Settings.",
                    )
                    else -> toast(
                        "Synced ${acts.size} workouts from the last 12 weeks" +
                            if (strava > 0) " ($strava from Strava skipped)" else "",
                    )
                }
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

    /** GPS route for the share image; null when offline, no key, or no GPS. */
    suspend fun route(activityId: String): List<Pair<Double, Double>>? = api()?.let { a -> runCatching { a.route(activityId) }.getOrNull() }

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

    /** Picks up what the background sync wrote while the app was away (watch data only; app edits stay). */
    fun reloadFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val disk = store.load()
            val mine = _data.value
            if ((disk.lastSync ?: "") > (mine.lastSync ?: "")) {
                _data.value = mine.copy(activities = disk.activities, wellness = disk.wellness, weights = disk.weights, lastSync = disk.lastSync)
            }
        }
    }

    private val _coachBusy = MutableStateFlow(false)
    val coachBusy: StateFlow<Boolean> = _coachBusy.asStateFlow()

    /** Asks Claude for a review of the week starting [monday] with the user's own key. */
    fun askCoach(monday: LocalDate) {
        val key = _data.value.settings.anthropicKey
        if (key.isBlank()) {
            toast("Add your Anthropic API key in Settings → AI coach first.")
            return
        }
        if (_coachBusy.value) return
        viewModelScope.launch {
            _coachBusy.value = true
            try {
                val text = CoachClient.review(key, CoachPrompt.SYSTEM, CoachPrompt.user(_data.value, monday, LocalDate.now()))
                update { d -> d.copy(coach = d.coach + (monday.toString() to text)) }
            } catch (e: Exception) {
                toast(e.message ?: "The coach is unavailable right now.")
            } finally {
                _coachBusy.value = false
            }
        }
    }

    /** The Claude app's reply for the week starting [monday], pasted into the coach card. */
    fun pasteCoach(monday: LocalDate, reply: String) = pasteCoach(app.stride.coach.CoachHandoff.Kind.WEEK, monday, reply)

    /** A reply pasted into the weekly card ([date] = Monday) or the daily one ([date] = the day). */
    fun pasteCoach(kind: app.stride.coach.CoachHandoff.Kind, date: LocalDate, reply: String) {
        val text = app.stride.coach.CoachHandoff.clean(reply)
        if (text == null) {
            toast("Copy Claude's reply first, then tap Paste reply.")
            return
        }
        update { d -> app.stride.coach.CoachShare.put(d, kind, date, text) }
        app.stride.coach.CoachShare.recordReply(getApplication(), kind, date, text)
        toast(if (kind == app.stride.coach.CoachHandoff.Kind.DAY) "Daily check-in saved" else "Coach review saved")
    }

    /** The daily check-in for [day] with the user's own key (the Claude app is the free way). */
    fun askCoachDaily(day: LocalDate) {
        val key = _data.value.settings.anthropicKey
        if (key.isBlank() || _coachBusy.value) return
        viewModelScope.launch {
            _coachBusy.value = true
            try {
                val text = CoachClient.review(key, CoachPrompt.DAILY_SYSTEM, CoachPrompt.daily(_data.value, day))
                update { d -> app.stride.coach.CoachShare.put(d, app.stride.coach.CoachHandoff.Kind.DAY, day, text) }
            } catch (e: Exception) {
                toast(e.message ?: "The coach is unavailable right now.")
            } finally {
                _coachBusy.value = false
            }
        }
    }

    /** Health Connect: fill sleep/HRV/steps/weight gaps and write finished strength workouts. */
    fun syncHealth(silent: Boolean = true) {
        if (!_data.value.settings.healthConnect) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (!HealthSync.available(app)) {
                    if (!silent) toast("Health Connect isn't available on this phone.")
                    return@launch
                }
                val read = HealthSync.read(app)
                update { d -> HealthSync.merge(d, read.days, read.nights) }
                val written = HealthSync.writeStrength(app, _data.value)
                if (written.isNotEmpty()) update { d -> d.copy(hcExported = (d.hcExported + written).takeLast(500)) }
                if (!silent) toast("Health Connect synced" + if (written.isNotEmpty()) " · ${written.size} workouts written" else "")
            } catch (e: Exception) {
                if (!silent) toast("Health Connect: " + (e.message ?: "sync failed"))
            }
        }
    }

    fun exportBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { Backup.write(app, uri, _data.value) } }
                .onSuccess { toast("Backup saved") }
                .onFailure { toast("Backup failed: ${it.message}") }
        }
    }

    fun restoreBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { Backup.restore(Backup.read(app, uri), _data.value) } }
                .onSuccess { restored ->
                    update { restored }
                    toast("Restored: ${restored.strength.size} strength workouts, ${restored.weights.size} weights")
                }
                .onFailure { toast(it.message ?: "Restore failed") }
        }
    }

    fun setBackupFolder(uri: android.net.Uri?) {
        update { d -> d.copy(settings = d.settings.copy(backupFolder = uri?.toString(), lastBackup = null)) }
        if (uri != null) {
            val app = getApplication<Application>()
            viewModelScope.launch(Dispatchers.IO) {
                // Write the first one right away so the user sees it in the folder.
                saveLock.withLock { store.save(_data.value) }
                runCatching { Backup.autoBackupIfDue(app, store) }
                    .onSuccess { if (it) toast("Weekly backup on · first backup saved") }
                    .onFailure { toast("Can't write to that folder: ${it.message}") }
            }
        }
    }

    fun updateSettings(f: (Settings) -> Settings) {
        update { d -> d.copy(settings = f(d.settings)) }
    }

    // ---------------------------------------------------------------- Garmin Connect (gym workouts)

    data class GarminUi(val connected: Boolean, val account: String?, val needsMfa: Boolean = false, val busy: Boolean = false)

    private var pendingMfa: GarminClient.PendingMfa? = null
    private var pendingEmail = ""
    private val _garmin = MutableStateFlow(GarminUi(false, null))
    val garminUi: StateFlow<GarminUi> = _garmin.asStateFlow()

    fun refreshGarmin() {
        _garmin.value = GarminUi(garmin.connected, garmin.account)
    }

    fun garminSignIn(email: String, password: String) {
        if (_garmin.value.busy) return
        viewModelScope.launch {
            _garmin.value = _garmin.value.copy(busy = true)
            try {
                val mfa = garmin.signIn(email, password)
                pendingEmail = email
                pendingMfa = mfa
                if (mfa != null) _garmin.value = _garmin.value.copy(needsMfa = true, busy = false)
                else {
                    refreshGarmin()
                    toast("Garmin connected")
                }
            } catch (e: Exception) {
                _garmin.value = _garmin.value.copy(busy = false)
                toast(e.message ?: "Garmin sign-in failed")
            }
        }
    }

    fun garminMfa(code: String) {
        val p = pendingMfa ?: return
        viewModelScope.launch {
            _garmin.value = _garmin.value.copy(busy = true)
            try {
                garmin.submitMfa(p, code, pendingEmail)
                pendingMfa = null
                refreshGarmin()
                toast("Garmin connected")
            } catch (e: Exception) {
                _garmin.value = _garmin.value.copy(busy = false)
                toast(e.message ?: "Code not accepted")
            }
        }
    }

    fun garminCancelMfa() {
        pendingMfa = null
        refreshGarmin()
    }

    /** Walks the watch detected by itself (Move IQ), today and the two days before; they count as training time. */
    fun syncAutoMoves() {
        if (!garmin.connected) return
        viewModelScope.launch {
            val today = LocalDate.now()
            try {
                val days = garmin.autoMoves((0L..2L).map { today.minusDays(it) })
                update { d -> d.copy(autoMoves = app.stride.domain.AutoMoves.merge(d.autoMoves, days, today)) }
                val t = days[today].orEmpty()
                _autoMoves.value = if (t.isEmpty()) "No moves detected today yet." else
                    "Today: ${t.size} detected (${t.sumOf { it.minutes }.toInt()} min) · ${t.joinToString { app.stride.domain.AutoMoves.label(it.type) }}"
            } catch (e: Exception) {
                _autoMoves.value = "Couldn't read detected moves: ${e.message}"
            }
        }
    }

    fun garminDisconnect() {
        garmin.disconnect()
        refreshGarmin()
        toast("Garmin disconnected")
    }

    /** Creates the session as a Garmin strength workout scheduled on its date, ready to start on the watch. */
    fun sendToGarmin(session: StrengthSession, name: String) {
        if (!garmin.connected) {
            toast("Connect Garmin in Settings first.")
            return
        }
        if (session.exercises.isEmpty()) {
            toast("Add exercises first.")
            return
        }
        if (_garmin.value.busy) return
        viewModelScope.launch {
            _garmin.value = _garmin.value.copy(busy = true)
            try {
                garmin.sendWorkout(
                    GarminWorkout.build(name, session), session.date,
                    // Exact exercise names are a guess at Garmin's catalog, so retry without them, then without the sauna step.
                    fallbacks = listOf(GarminWorkout.build(name, session, exactNames = false), GarminWorkout.build(name, session, exactNames = false, sauna = false)),
                )
                toast("Sent to Garmin · sync the watch, then find it under Workouts or today's calendar")
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't send to Garmin")
            } finally {
                _garmin.value = _garmin.value.copy(busy = false)
            }
        }
    }

    /**
     * Copies the phone's sets into the strength activity the watch recorded that day, so Garmin Connect shows the
     * real reps and weights (the watch's timing, heart rate and the rest stay). [afterFinish]: called right after
     * finishing, when the watch often hasn't synced yet, so a missing activity only gets a hint.
     */
    fun copySetsToGarmin(session: StrengthSession, afterFinish: Boolean = false) {
        if (!garmin.connected || session.exercises.isEmpty() || _garmin.value.busy) return
        viewModelScope.launch {
            _garmin.value = _garmin.value.copy(busy = true)
            try {
                val m = garmin.copySets(session)
                if (m != null) _data.value.strength.firstOrNull { it.id == session.id }?.let { cur ->
                    saveStrength(cur.copy(garminSyncedAt = java.time.Instant.now().toString(), workSec = m.workSec.takeIf { m.watch > 0 }, restSec = m.restSec.takeIf { m.watch > 0 }, rests = m.rests.takeIf { m.watch > 0 }))
                }
                when {
                    m == null -> toast(
                        if (afterFinish) "Saved. Once the watch has synced, open the day and tap Copy sets to Garmin."
                        else "No strength activity from the watch on this day yet. Sync the watch and try again."
                    )
                    m.watch == 0 -> toast("Garmin has the activity but no sets in it yet; added ${m.phone} sets from the phone")
                    m.phone == m.watch -> toast("Garmin updated · ${m.phone} sets with your reps and weights")
                    else -> toast("Garmin updated · ${m.phone} sets from the phone, the watch had counted ${m.watch}")
                }
            } catch (e: Exception) {
                toast(e.message ?: "Couldn't update Garmin")
            } finally {
                _garmin.value = _garmin.value.copy(busy = false)
            }
        }
    }

    fun setShareStyle(s: ShareStyle) {
        if (s != _data.value.settings.share) update { d -> d.copy(settings = d.settings.copy(share = s)) }
    }

    fun setLayout(l: LayoutPrefs) {
        update { d -> d.copy(settings = d.settings.copy(layout = l)) }
    }

    fun setNotification(t: NotifType, on: Boolean) {
        update { d -> d.copy(settings = d.settings.copy(notifications = d.settings.notifications + (t.id to on))) }
    }

    fun saveSettings(s: Settings) {
        val keyChanged = s.apiKey != _data.value.settings.apiKey || s.athleteId != _data.value.settings.athleteId
        update { d -> withDefaultPlan(d, s.plan, from = mondayOf(LocalDate.now())).copy(settings = s) }
        if (keyChanged && s.apiKey.isNotBlank()) sync()
    }

    /**
     * Sets the default plan from week [from] on. Earlier weeks without their own plan get the
     * old default pinned first, so past weeks and trends keep the plan they were done against.
     */
    private fun withDefaultPlan(d: AppData, plan: List<PlanDay>, from: LocalDate): AppData {
        if (plan == d.settings.plan) return d
        val pinned = d.weekPlans.toMutableMap()
        for (back in 1..12) {
            val mon = from.minusWeeks(back.toLong()).toString()
            if (mon !in pinned) pinned[mon] = d.settings.plan
        }
        return d.copy(weekPlans = pinned, settings = d.settings.copy(plan = plan))
    }

    /** Approves the plan for the week starting [monday]; [keep] also makes it the plan for the weeks after. */
    fun approveWeek(monday: LocalDate, plan: List<PlanDay>, keep: Boolean) {
        update { d ->
            val base = if (keep) withDefaultPlan(d, plan, from = monday) else d
            base.copy(weekPlans = base.weekPlans + (monday.toString() to plan))
        }
        toast("Plan for the week of ${dateShort(monday)} approved")
    }

    /** Swaps today's planned workout with a rest day later this week (this week only). */
    fun swapToday(today: LocalDate, restDow: Int) {
        val monday = mondayOf(today)
        update { d -> d.copy(weekPlans = d.weekPlans + (monday.toString() to swapDays(d.weekPlan(today), today.dayOfWeek.value, restDow))) }
        toast("Swapped. Today is a rest day; the workout moved to ${dayLong(today.plusDays((restDow - today.dayOfWeek.value).toLong()))}")
    }

    fun setDeload(monday: LocalDate, on: Boolean) {
        val key = monday.toString()
        update { d -> d.copy(deloadWeeks = if (on) (d.deloadWeeks + key).distinct() else d.deloadWeeks - key) }
    }

    fun setManualDone(date: LocalDate, done: Boolean) {
        val key = date.toString()
        update { d -> d.copy(manualDone = if (done) (d.manualDone + key).distinct() else d.manualDone - key) }
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
