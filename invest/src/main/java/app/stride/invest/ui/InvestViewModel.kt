package app.stride.invest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.data.DataEvents
import app.stride.invest.data.FlexClient
import app.stride.invest.data.InvestStore
import app.stride.invest.domain.Portfolio
import app.stride.invest.model.InvestData
import app.stride.invest.model.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Invest's state: the stored data, reading a new Interactive Brokers report, and the plan. */
class InvestViewModel(app: Application) : AndroidViewModel(app) {
    private val store = InvestStore(app)
    private val _data = MutableStateFlow(InvestData())
    val data: StateFlow<InvestData> = _data.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _connected = MutableStateFlow(FlexClient.connected(app))
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        viewModelScope.launch { reload() }
        // The background worker reads reports too.
        viewModelScope.launch { DataEvents.changed.collect { reload() } }
    }

    private suspend fun reload() {
        _data.value = withContext(Dispatchers.IO) { store.load() }
    }

    private fun update(f: (InvestData) -> InvestData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { store.update { f(it) } }
    }

    /** Reads the latest report; errors are kept on the data so the overview can say what went wrong. */
    fun sync() {
        if (!FlexClient.connected(getApplication()) || _syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            try {
                val s = FlexClient.fetch(getApplication())
                update { Portfolio.merge(it, s, Instant.now().toString()) }
            } catch (e: Exception) {
                update { it.copy(lastError = e.message ?: "Couldn't read the report.") }
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Refreshes when the last report is older than a few hours. */
    fun syncIfStale() {
        val last = _data.value.lastSync?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (last == null || java.time.Duration.between(last, Instant.now()).toHours() >= 6) sync()
    }

    fun connect(token: String, queryId: String) {
        FlexClient.save(getApplication(), token, queryId)
        _connected.value = FlexClient.connected(getApplication())
        sync()
    }

    fun disconnect() {
        FlexClient.save(getApplication(), null, null)
        _connected.value = false
    }

    fun setPlan(p: Plan) = update { it.copy(plan = p) }
}
