package app.stride

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.reminder.PlanReminder
import app.stride.ui.StrideApp
import app.stride.ui.theme.StrideColors

/** Training and health: everything Stride has done so far, as the first module of the host. */
object HealthModule : AppModule {
    override val id = "health"
    override val title = "Health"
    override val icon: ImageVector = Icons.Filled.FitnessCenter
    override val accent = StrideColors.Accent

    override fun onAppStart(context: Context) {
        PlanReminder.schedule(context)
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        StrideApp(openRequest = open, onHome = onHome)
    }
}
