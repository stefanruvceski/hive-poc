package app.stride.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * How modules open the app without depending on it: every notification, widget, tile and shortcut
 * starts the one host activity with [EXTRA_OPEN] naming the place to open.
 */
object Host {
    /** Where to open: "workout", a tab id, "day:YYYY-MM-DD", "plan-review", or "<module>/<target>" once there are several modules. */
    const val EXTRA_OPEN = "app.stride.OPEN"

    /** The host activity (in the :app module). */
    const val MAIN_ACTIVITY = "app.stride.MainActivity"

    fun component(context: Context) = ComponentName(context.packageName, MAIN_ACTIVITY)

    fun openIntent(context: Context, target: String? = null): Intent =
        Intent().setComponent(component(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .also { if (target != null) it.putExtra(EXTRA_OPEN, target) }
}

/** A request to open a place in the app; [id] grows so the same target can be asked twice. */
data class OpenRequest(val id: Int, val target: String)
