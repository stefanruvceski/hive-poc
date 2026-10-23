package app.stride.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stride.MainActivity
import app.stride.R
import app.stride.data.Store
import app.stride.domain.weekRange
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Sunday-evening reminder to review next week's plan. An hourly background check posts it
 * once per week, from 18:00 on Sunday, as long as next week isn't approved yet.
 */
object PlanReminder {
    const val EXTRA_REVIEW_PLAN = "app.stride.REVIEW_PLAN"
    private const val CHANNEL = "plan_review"
    private const val WORK = "plan-reminder"
    private const val PREFS = "plan_reminder"
    private const val KEY_LAST = "last_notified_week"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PlanReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        if (now.dayOfWeek != DayOfWeek.SUNDAY || now.hour < 18) return
        val data = Store(context).load()
        if (!data.settings.planReminder) return
        val nextMonday = now.toLocalDate().plusDays(1)
        val key = nextMonday.toString()
        if (key in data.weekPlans) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST, null) == key) return
        if (notify(context, "Plan for next week", "${weekRange(nextMonday)}: keep it, move it or change days, then approve.")) {
            prefs.edit().putString(KEY_LAST, key).apply()
        }
    }

    // Permission is checked right above the notify call (Android 13+ only needs it).
    @SuppressLint("MissingPermission")
    private fun notify(context: Context, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly plan review", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_REVIEW_PLAN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stride)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
        return true
    }
}

class PlanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { PlanReminder.check(applicationContext) }
        return Result.success()
    }
}
