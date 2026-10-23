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
import app.stride.domain.Analyzer
import app.stride.domain.DayStatus
import app.stride.domain.Recovery
import app.stride.domain.fmtHours
import app.stride.domain.fmtNum
import app.stride.domain.weekRange
import app.stride.model.DayKind
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background reminders, each posted at most once: Sunday 18:00+ to review next week's plan,
 * 06:00 with today's plan and recovery, 19:00 when a planned workout isn't done yet.
 */
object PlanReminder {
    const val EXTRA_REVIEW_PLAN = "app.stride.REVIEW_PLAN"
    private const val CHANNEL = "plan_review"
    private const val WORK = "plan-reminder"
    private const val PREFS = "plan_reminder"
    private const val KEY_LAST = "last_notified_week"

    private const val DAILY = "daily"
    private const val KEY_MORNING = "last_morning"
    private const val KEY_EVENING = "last_evening"

    fun schedule(context: Context) {
        // Every 15 minutes (WorkManager's minimum), so the 06:00 and 19:00 notes arrive on time.
        val request = PeriodicWorkRequestBuilder<PlanReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val data = Store(context).load()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = now.toLocalDate()
        val key = today.toString()

        // Sunday 18:00+: next week's plan not approved yet.
        if (data.settings.planReminder && now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) {
            val nextMonday = today.plusDays(1).toString()
            if (nextMonday !in data.weekPlans && prefs.getString(KEY_LAST, null) != nextMonday &&
                notify(context, CHANNEL, "Weekly plan review", 1, "Plan for next week",
                    "${weekRange(today.plusDays(1))}: keep it, move it or change days, then approve.", review = true)
            ) prefs.edit().putString(KEY_LAST, nextMonday).apply()
        }

        val day = Analyzer.day(today, today, data)
        val plan = day.plan ?: return
        if (plan.kind == DayKind.REST) return

        // 06:00-10:59: today's plan with last night's recovery.
        if (data.settings.morningReminder && now.hour in 6..10 && prefs.getString(KEY_MORNING, null) != key && day.status != DayStatus.DONE) {
            val r = Recovery.summary(data.wellness, today)
            val recovery = r?.let { s ->
                listOfNotNull(
                    s.day.sleepSecs?.let { "sleep ${fmtHours(it)}" },
                    s.day.hrv?.let { "HRV ${fmtNum(it)}" + (s.hrvBaseline?.let { b -> " (avg ${fmtNum(b)})" } ?: "") },
                ).joinToString(", ").let { line -> if (line.isEmpty()) s.reason else "${line.replaceFirstChar { c -> c.uppercase() }}. ${s.reason}" }
            } ?: "Have a good session."
            if (notify(context, DAILY, "Daily plan", 2, "Today: ${plan.title}", recovery)) {
                prefs.edit().putString(KEY_MORNING, key).apply()
            }
        }

        // 19:00-21:59: a planned workout that isn't done yet.
        if (data.settings.eveningReminder && now.hour in 19..21 && prefs.getString(KEY_EVENING, null) != key &&
            !plan.kind.isRest && day.status != DayStatus.DONE
        ) {
            if (notify(context, DAILY, "Daily plan", 3, "Still time for ${plan.title}?",
                    "Not done yet today. Did it without the watch? Open Stride and check it off.")
            ) prefs.edit().putString(KEY_EVENING, key).apply()
        }
    }

    // Permission is checked right above the notify call (Android 13+ only needs it).
    @SuppressLint("MissingPermission")
    private fun notify(context: Context, channel: String, channelName: String, id: Int, title: String, text: String, review: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_REVIEW_PLAN, review)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_stride)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}

class PlanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { PlanReminder.check(applicationContext) }
        return Result.success()
    }
}
