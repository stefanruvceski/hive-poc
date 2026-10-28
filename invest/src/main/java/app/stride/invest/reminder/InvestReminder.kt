package app.stride.invest.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stride.data.DataEvents
import app.stride.invest.data.FlexClient
import app.stride.invest.data.InvestStore
import app.stride.invest.domain.Portfolio
import app.stride.invest.ui.money
import app.stride.notify.Notifier
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Every few hours: once a day (from 08:00) the latest Interactive Brokers report is read, then the plan's reminders
 * are checked (a period starting, two weeks left while short), each posted once per period.
 */
object InvestReminder {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "invest-reminder", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<InvestWorker>(3, TimeUnit.HOURS).build(),
        )
    }

    suspend fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val store = InvestStore(context)
        var d = store.load()
        val stale = d.lastSync?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { ChronoUnit.HOURS.between(it, Instant.now()) >= 20 } ?: true
        if (FlexClient.connected(context) && stale && now.hour >= 8) {
            runCatching { FlexClient.fetch(context) }.onSuccess { s ->
                d = store.update { Portfolio.merge(it, s, Instant.now().toString()) }
                DataEvents.notifyChanged()
            }
        }
        if (now.hour < 9) return
        Portfolio.notices(d, now.toLocalDate()) { money(it) }.forEach { n ->
            Notifier.postOnce(context, "invest_reminders", n.key) {
                Notifier.post(context, "invest", "Invest", 700 + (n.key.hashCode() and 0xff), n.title, n.text, "invest/plan")
            }
        }
    }
}

class InvestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { InvestReminder.check(applicationContext) }
        return Result.success()
    }
}
