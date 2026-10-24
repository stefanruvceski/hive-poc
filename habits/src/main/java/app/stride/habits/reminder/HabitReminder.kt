package app.stride.habits.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stride.habits.data.HabitStore
import app.stride.habits.domain.Routine
import app.stride.notify.Notifier
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Every ~15 min: posts the due habit reminders (pure rules in [Routine.due]), each once. */
object HabitReminder {
    private const val WORK = "habit-reminders"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<HabitReminderWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val data = HabitStore(context).load()
        Routine.due(data, now).forEach { r ->
            Notifier.postOnce(context, "habit_reminders", r.key) {
                Notifier.post(context, "habits", "Habits", 300 + Math.floorMod(r.key.substringBeforeLast('-').hashCode(), 50), r.title, r.text, "habits/today")
            }
        }
    }
}

class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { HabitReminder.check(applicationContext) }
        return Result.success()
    }
}
