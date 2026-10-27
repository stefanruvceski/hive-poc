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
import app.stride.host.AppInfo
import app.stride.host.Host
import app.stride.core.R
import app.stride.notify.Notifier
import app.stride.data.IntervalsApi
import app.stride.data.Store
import app.stride.data.Sync
import app.stride.data.Backup
import app.stride.data.DataEvents
import app.stride.coach.CoachClient
import app.stride.coach.CoachPrompt
import app.stride.domain.planFor
import app.stride.domain.mondayOf
import java.time.DayOfWeek
import app.stride.domain.Notice
import app.stride.domain.Notices
import app.stride.model.Activity
import app.stride.model.NotifType
import app.stride.update.UpdateLogic
import app.stride.update.Updater
import app.stride.widget.Widgets
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background work every 15 minutes: syncs watch data every ~2 hours (so workout and record
 * notifications can fire), checks for app updates twice a day, and posts the notifications
 * [Notices] says are due, each at most once.
 */
object PlanReminder {
    private const val WORK = "plan-reminder"
    private const val PREFS = "plan_reminder"
    private const val KEY_SENT = "sent"
    private const val KEY_MOVES = "last_auto_moves"
    private const val KEY_UPDATE_CHECK = "last_update_check"
    private const val KEY_LATEST_BUILD = "latest_build"

    fun schedule(context: Context) {
        // Every 15 minutes (WorkManager's minimum), so time-of-day notes arrive on time.
        val request = PeriodicWorkRequestBuilder<PlanReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    suspend fun check(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val store = Store(context)
        var data = store.load()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Background sync between 06:00 and 23:59 when the last one is 2+ hours old.
        var fresh = emptyList<Activity>()
        val last = data.lastSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (data.settings.apiKey.isNotBlank() && now.hour >= 6 && (last == null || Duration.between(last, now).toHours() >= 2)) {
            runCatching {
                val result = Sync.fetch(IntervalsApi(data.settings.apiKey.trim(), data.settings.athleteId.trim()))
                val before = data.activities.map { it.id }.toSet()
                data = store.update { result.apply(it) }
                // The first sync ever would report the whole history; only report when there was data before.
                if (before.isNotEmpty()) fresh = data.activities.filter { it.id !in before }
                Widgets.refresh(context)
            }
        }

        // Walks the watch detected by itself (Garmin Move IQ), every ~2 h: they count as training time.
        val garmin = app.stride.garmin.GarminClient(context)
        if (garmin.connected && now.hour >= 6 && System.currentTimeMillis() - prefs.getLong(KEY_MOVES, 0) >= 2 * 3_600_000L) {
            runCatching {
                val today = now.toLocalDate()
                val days = garmin.autoMoves(listOf(today, today.minusDays(1)))
                prefs.edit().putLong(KEY_MOVES, System.currentTimeMillis()).apply()
                data = store.update { it.copy(autoMoves = app.stride.domain.AutoMoves.merge(it.autoMoves, days, today)) }
                DataEvents.notifyChanged()
                Widgets.refresh(context)
            }
        }

        // Weekly automatic backup to the folder picked in Settings.
        runCatching { Backup.autoBackupIfDue(context, store, now) }

        // Sunday 19:00+: the AI coach reviews the week (user's own Anthropic key).
        val monday = mondayOf(now.toLocalDate())
        val sentSoFar = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
        if (data.settings.notifyOn(NotifType.COACH) && data.settings.anthropicKey.isNotBlank() &&
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 19 && monday.toString() !in data.coach
        ) {
            runCatching {
                val text = CoachClient.review(data.settings.anthropicKey, CoachPrompt.SYSTEM, CoachPrompt.user(data, monday, now.toLocalDate()))
                data = store.update { it.copy(coach = it.coach + (monday.toString() to text)) }
                DataEvents.notifyChanged()
                val n = Notice(NotifType.COACH, monday.toString(), "Your week, reviewed by the coach",
                    text.lines().filter { it.isNotBlank() }.take(4).joinToString("\n"))
                if (n.dedupe !in sentSoFar && notify(context, n)) {
                    prefs.edit().putStringSet(KEY_SENT, (sentSoFar + n.dedupe).toList().takeLast(300).toSet()).apply()
                }
            }
        }
        // Without a key: a nudge to send the week to the Claude app (once per week), with an "Ask Claude" button.
        if (data.settings.notifyOn(NotifType.COACH) && data.settings.anthropicKey.isBlank() &&
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 19 && monday.toString() !in data.coach
        ) {
            val key = "coach:ask-$monday"
            if (key !in sentSoFar && app.stride.coach.CoachShare.notify(
                    context, app.stride.coach.CoachHandoff.Kind.WEEK, 100 + NotifType.COACH.ordinal * 10,
                    "Your week is ready for the coach", "Tap Ask Claude: the Claude app reviews your week, then share the reply to Hive Coach.",
                )
            ) {
                prefs.edit().putStringSet(KEY_SENT, (sentSoFar + key).toList().takeLast(300).toSet()).apply()
            }
        }

        // 06:00-12:00: the daily check-in (yesterday reviewed, advice for today) through the Claude app.
        val todayDate = now.toLocalDate()
        val dailyKey = "coach_daily:$todayDate"
        if (data.settings.notifyOn(NotifType.COACH_DAILY) && now.hour in 6..11 && todayDate.toString() !in data.coachDays) {
            val sentNow = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
            val plan = data.planFor(todayDate)
            if (dailyKey !in sentNow && app.stride.coach.CoachShare.notify(
                    context, app.stride.coach.CoachHandoff.Kind.DAY, 100 + NotifType.COACH_DAILY.ordinal * 10,
                    "Morning check-in" + (plan?.let { " · ${it.title}" } ?: " · rest day"),
                    "Tap Ask Claude: yesterday reviewed and advice for today, from your watch data.",
                )
            ) {
                prefs.edit().putStringSet(KEY_SENT, (sentNow + dailyKey).toList().takeLast(300).toSet()).apply()
            }
        }

        // Update check twice a day.
        var latest = prefs.getInt(KEY_LATEST_BUILD, 0).takeIf { it > 0 }
        if (!AppInfo.debug && System.currentTimeMillis() - prefs.getLong(KEY_UPDATE_CHECK, 0) > TimeUnit.HOURS.toMillis(12)) {
            Updater.fetchReleases()?.let { releases ->
                latest = UpdateLogic.pickLatest(releases)?.build
                prefs.edit().putLong(KEY_UPDATE_CHECK, System.currentTimeMillis()).putInt(KEY_LATEST_BUILD, latest ?: 0).apply()
            }
        }

        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableList()
        val due = Notices.due(data, now, fresh, latest, AppInfo.versionCode).filter { it.dedupe !in sent }
        for (n in due) {
            if (notify(context, n)) sent += n.dedupe
        }
        if (due.isNotEmpty()) prefs.edit().putStringSet(KEY_SENT, sent.takeLast(300).toSet()).apply()
    }

    private fun channel(t: NotifType): Pair<String, String> = when (t) {
        NotifType.PLAN_REVIEW -> "plan_review" to "Weekly plan review"
        NotifType.MORNING, NotifType.PRE_WORKOUT, NotifType.EVENING, NotifType.SAUNA, NotifType.REST_DAY, NotifType.MISSED, NotifType.RINGS_EVENING -> "daily" to "Daily plan"
        NotifType.WORKOUT_DONE, NotifType.PERSONAL_BEST, NotifType.TARGET_HIT, NotifType.RINGS_CLOSED, NotifType.WEEK_RECAP, NotifType.MONTH_RECAP -> "progress" to "Workouts and progress"
        NotifType.LOW_RECOVERY, NotifType.BEDTIME, NotifType.DELOAD, NotifType.WEIGH_IN -> "health" to "Recovery and health"
        NotifType.NO_DATA, NotifType.UPDATE -> "app" to "App and sync"
        NotifType.COACH, NotifType.BADGE -> "progress" to "Workouts and progress"
        NotifType.COACH_DAILY -> "daily" to "Daily plan"
    }

    private fun notify(context: Context, n: Notice): Boolean {
        val (channel, channelName) = channel(n.type)
        // One slot per type; workout notes get their own so two workouts don't replace each other.
        val id = 100 + n.type.ordinal * 10 + if (n.type == NotifType.WORKOUT_DONE || n.type == NotifType.PERSONAL_BEST) Math.floorMod(n.key.hashCode(), 10) else 0
        return Notifier.post(context, channel, channelName, id, n.title, n.text, when (n.type) {
            NotifType.PLAN_REVIEW -> "plan-review"
            NotifType.COACH, NotifType.COACH_DAILY -> "coach"
            else -> null
        })
    }

    /** A sample of [t] right now, for the test button in Settings. */
    fun test(context: Context, t: NotifType): Boolean =
        notify(context, Notice(t, "test-${System.currentTimeMillis()}", t.title, "${t.description}. This is how it will look."))
}

class PlanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { PlanReminder.check(applicationContext) }
        return Result.success()
    }
}
