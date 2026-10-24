package app.stride.host

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One "app" inside the host (Health today; Habits and others later). A module owns its screens,
 * data, widgets and background work; the host only shows modules and routes open requests.
 */
interface AppModule {
    /** Stable id, also the prefix of open targets ("health/workout"). Never rename. */
    val id: String
    val title: String
    val icon: ImageVector
    val accent: Color

    /** Called once when the app process starts (schedule background work, create channels, …). */
    fun onAppStart(context: Context) {}

    /** The module's whole UI. [open] carries deep links; [onHome] is null when this module is the only one. */
    @Composable
    fun Content(open: OpenRequest, onHome: (() -> Unit)?)
}
