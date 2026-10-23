package app.stride.live

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.stride.MainActivity
import app.stride.R
import app.stride.data.DataEvents
import app.stride.data.Store
import app.stride.domain.LiveSet
import app.stride.domain.fmtKg
import app.stride.model.StrengthSession
import app.stride.ui.screens.shortKind
import app.stride.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ongoing lock-screen notification during a workout: the current exercise and set, a rest
 * countdown, and buttons to tick off the set or add 30 s of rest without unlocking the phone.
 */
object LiveWorkout {
    private const val CHANNEL = "live_workout"
    private const val CHANNEL_REST = "rest_over"
    private const val ID = 7001
    private const val ID_REST = 7002
    private const val PREFS = "live_workout"
    private const val KEY_SESSION = "session"
    private const val KEY_REST_END = "rest_end"
    private const val KEY_REST_LEN = "rest_len"

    const val ACTION_DONE = "app.stride.live.DONE"
    const val ACTION_MORE = "app.stride.live.MORE"
    const val ACTION_REST_OVER = "app.stride.live.REST_OVER"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun restEnd(c: Context): Long = prefs(c).getLong(KEY_REST_END, 0L)

    /** Shows or refreshes the notification for [s]; [restEndsAt] (epoch ms, 0 = none) and [restLength] in seconds. */
    fun show(c: Context, s: StrengthSession, restEndsAt: Long, restLength: Int) {
        if (s.finished || LiveSet.current(s) == null) {
            stop(c)
            return
        }
        prefs(c).edit().putString(KEY_SESSION, s.id).putLong(KEY_REST_END, restEndsAt).putInt(KEY_REST_LEN, restLength).apply()
        post(c, s, restEndsAt)
        scheduleRestOver(c, restEndsAt)
    }

    fun stop(c: Context) {
        prefs(c).edit().clear().apply()
        NotificationManagerCompat.from(c).cancel(ID)
        scheduleRestOver(c, 0L)
    }

    private fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun broadcast(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(c, code, Intent(c, LiveWorkoutReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    @SuppressLint("MissingPermission")
    private fun post(c: Context, s: StrengthSession, restEndsAt: Long) {
        if (!allowed(c)) return
        val p = LiveSet.current(s) ?: return
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW))
        val work = p.sets.filter { !it.warmup }
        val setLabel = if (p.current.warmup) "warm-up" else "set ${work.indexOf(p.current) + 1} of ${work.size}"
        val kg = if (p.current.kg > 0) "${fmtKg(p.current.kg)} kg × ${p.current.reps}" else "${p.current.reps} reps"
        val resting = restEndsAt > System.currentTimeMillis()
        val open = PendingIntent.getActivity(
            c, ID,
            Intent(c, MainActivity::class.java).putExtra(Widgets.EXTRA_OPEN, "workout")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stride)
            .setContentTitle("${p.entry.name} · $setLabel")
            .setContentText(if (resting) "Rest, then $kg" else kg)
            .setSubText("${shortKind(s.kind)} · ${LiveSet.progressLabel(s)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, "✓ Set done", broadcast(c, ACTION_DONE, 1))
            .addAction(0, "+30 s rest", broadcast(c, ACTION_MORE, 2))
        if (resting) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(restEndsAt).setShowWhen(true)
        } else {
            b.setShowWhen(false)
        }
        NotificationManagerCompat.from(c).notify(ID, b.build())
    }

    /** A short buzz when the rest is over, via an inexact alarm (no exact-alarm permission needed). */
    private fun scheduleRestOver(c: Context, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = broadcast(c, ACTION_REST_OVER, 3)
        am.cancel(pi)
        if (at > System.currentTimeMillis()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    @SuppressLint("MissingPermission")
    private fun restOver(c: Context) {
        if (!allowed(c)) return
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_REST, "Rest over", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(c, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_stat_stride)
            .setContentTitle("Rest over")
            .setContentText("Next set")
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(c).notify(ID_REST, n)
    }

    internal fun handle(c: Context, action: String?) {
        val p = prefs(c)
        val id = p.getString(KEY_SESSION, null) ?: return
        val restLen = p.getInt(KEY_REST_LEN, 120)
        when (action) {
            ACTION_REST_OVER -> {
                restOver(c)
                Store(c).load().strength.firstOrNull { it.id == id }?.let { post(c, it, 0L) }
            }
            ACTION_MORE -> {
                val end = maxOf(p.getLong(KEY_REST_END, 0L), System.currentTimeMillis()) + 30_000
                Store(c).load().strength.firstOrNull { it.id == id }?.let { show(c, it, end, restLen) }
                DataEvents.notifyChanged()
            }
            ACTION_DONE -> {
                var updated: StrengthSession? = null
                Store(c).update { d ->
                    val s = d.strength.firstOrNull { it.id == id } ?: return@update d
                    val next = LiveSet.completeCurrent(s)
                    updated = next
                    d.copy(strength = d.strength.map { if (it.id == id) next else it })
                }
                updated?.let { show(c, it, System.currentTimeMillis() + restLen * 1000L, restLen) }
                DataEvents.notifyChanged()
            }
        }
    }
}

class LiveWorkoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                LiveWorkout.handle(context.applicationContext, intent.action)
                Widgets.refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
