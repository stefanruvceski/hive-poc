package app.stride.habits.data

import android.content.Context
import app.stride.data.JsonStore
import app.stride.habits.model.HabitsData

/** Habits' data in habits.json, on the phone only (the shared [JsonStore]). */
class HabitStore(context: Context) : JsonStore<HabitsData>(context, "habits.json", HabitsData.serializer(), { HabitsData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}
