package app.stride.habits.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.data.DataEvents
import app.stride.habits.data.HabitStore
import app.stride.habits.domain.Starter
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitReminders
import app.stride.habits.model.HabitsData
import app.stride.habits.model.MentalDay
import app.stride.habits.model.Todo
import app.stride.habits.widget.HabitWidgets
import app.stride.model.LayoutPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

/** The Habits app's state and every change to it; each change is saved right away (Streakli's store actions). */
class HabitsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = HabitStore(app)
    private val saveLock = Mutex()
    private val _data = MutableStateFlow(HabitsData())
    val data: StateFlow<HabitsData> = _data.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var d = store.load()
            if (!d.seeded) {
                d = d.copy(habits = d.habits.ifEmpty { Starter.habits(LocalDate.now()) }, seeded = true)
                store.save(d)
            }
            _data.value = d
            _loaded.value = true
        }
        // A widget or notification action changed the file: pick it up.
        viewModelScope.launch { DataEvents.changed.collect { reload() } }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) { _data.value = store.load() }
    }

    private fun update(f: (HabitsData) -> HabitsData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { store.save(_data.value) }
            HabitWidgets.refresh(getApplication())
        }
    }

    private fun day(d: HabitsData, date: LocalDate) = d.entries[date.toString()].orEmpty()

    private fun withDay(d: HabitsData, date: LocalDate, day: Map<String, Double>) =
        d.copy(entries = if (day.isEmpty()) d.entries - date.toString() else d.entries + (date.toString() to day))

    /** Yes/no habit: done ↔ not done. */
    fun toggle(h: Habit, date: LocalDate) = update { d ->
        val cur = day(d, date)
        withDay(d, date, if (cur.containsKey(h.id)) cur - h.id else cur + (h.id to 1.0))
    }

    /** Amount for a measurable habit; ≤ 0 clears it. */
    fun setAmount(h: Habit, date: LocalDate, amount: Double) = update { d ->
        val cur = day(d, date)
        withDay(d, date, if (amount.isFinite() && amount > 0) cur + (h.id to amount) else cur - h.id)
    }

    fun saveHabit(h: Habit) = update { d ->
        if (d.habits.any { it.id == h.id }) d.copy(habits = d.habits.map { if (it.id == h.id) h else it })
        else d.copy(habits = d.habits + h.copy(order = (d.habits.maxOfOrNull { it.order } ?: -1) + 1))
    }

    fun deleteHabit(id: String) = update { d ->
        d.copy(habits = d.habits.filter { it.id != id }, entries = d.entries.mapValues { it.value - id }.filterValues { it.isNotEmpty() })
    }

    fun archiveHabit(id: String, archived: Boolean) = update { d -> d.copy(habits = d.habits.map { if (it.id == id) it.copy(archived = archived) else it }) }

    fun reorder(ids: List<String>) = update { d ->
        val pos = ids.withIndex().associate { it.value to it.index }
        d.copy(habits = d.habits.map { it.copy(order = pos[it.id] ?: it.order) })
    }

    fun setMental(date: LocalDate, mood: Int? = null, motivation: Int? = null) = update { d ->
        val prev = d.mental[date.toString()] ?: MentalDay()
        d.copy(mental = d.mental + (date.toString() to MentalDay(mood ?: prev.mood, motivation ?: prev.motivation)))
    }

    fun addTodo(title: String, date: LocalDate, priority: Boolean = false) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        update { d ->
            val order = d.todos.filter { it.date == date.toString() }.maxOfOrNull { it.order + 1 } ?: 0
            d.copy(todos = d.todos + Todo(Starter.id("t"), clean, date.toString(), priority = priority, order = order, createdAt = Instant.now().toString()))
        }
    }

    fun toggleTodo(id: String) = update { d ->
        d.copy(todos = d.todos.map { if (it.id == id) it.copy(done = !it.done, doneAt = if (!it.done) Instant.now().toString() else null) else it })
    }

    fun starTodo(id: String) = update { d -> d.copy(todos = d.todos.map { if (it.id == id) it.copy(priority = !it.priority) else it }) }

    fun moveTodo(id: String, date: LocalDate) = update { d -> d.copy(todos = d.todos.map { if (it.id == id) it.copy(date = date.toString()) else it }) }

    fun deleteTodo(id: String) = update { d -> d.copy(todos = d.todos.filter { it.id != id }) }

    fun setReminders(r: HabitReminders) = update { d -> d.copy(settings = d.settings.copy(reminders = r)) }

    fun setLayout(l: LayoutPrefs) = update { d -> d.copy(settings = d.settings.copy(layout = l)) }
}
