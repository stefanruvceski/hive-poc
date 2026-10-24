package app.stride.money.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stride.money.data.MoneyStore
import app.stride.notify.Notifier
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a month (Streakli's income reminder): on the chosen day and hour, a nudge to log last month's income. */
object MoneyReminder {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "money-reminder", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<MoneyReminderWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    /** Due when it's the chosen day, at or after the hour (pure, so the worker's window is easy to reason about). */
    fun due(enabled: Boolean, day: Int, hour: Int, now: LocalDateTime): Boolean =
        enabled && now.dayOfMonth == day.coerceIn(1, 28) && now.hour >= hour

    fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val s = MoneyStore(context).load().settings
        if (!due(s.monthlyReminder, s.reminderDay, s.reminderHour, now)) return
        Notifier.postOnce(context, "money_reminders", "income-${now.year}-${now.monthValue}") {
            Notifier.post(context, "money", "Money", 400, "Log last month's income", "Add what came in and mark paid invoices, so your goal stays up to date.", "money/add-income")
        }
    }
}

class MoneyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { MoneyReminder.check(applicationContext) }
        return Result.success()
    }
}
