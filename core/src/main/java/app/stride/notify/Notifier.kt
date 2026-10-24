package app.stride.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.stride.core.R
import app.stride.host.Host

/**
 * Posts one notification for any module: creates the channel, opens the app at [open]
 * ("<module>/<target>") when tapped, and returns false when notifications aren't allowed.
 */
object Notifier {
    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Permission is checked right above the notify call (Android 13+ only needs it).
    @SuppressLint("MissingPermission")
    fun post(context: Context, channel: String, channelName: String, id: Int, title: String, text: String, open: String? = null): Boolean {
        if (!allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, id, Host.openIntent(context, open), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    /** Runs [post] once per [key]: keys already in the module's sent set (prefs [prefsName]) are skipped. */
    fun postOnce(context: Context, prefsName: String, key: String, post: () -> Boolean) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("sent", emptySet()).orEmpty()
        if (key in sent) return
        if (post()) prefs.edit().putStringSet("sent", (sent.toList() + key).takeLast(300).toSet()).apply()
    }
}
