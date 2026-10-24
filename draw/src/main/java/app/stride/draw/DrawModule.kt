package app.stride.draw

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.ui.theme.StrideColors

/** Drawify: trace any picture onto paper through the camera. */
object DrawModule : AppModule {
    override val id = "draw"
    override val title = "Drawify"
    override val subtitle = "Trace any picture onto paper with the camera"
    override val icon: ImageVector = Icons.Filled.Draw
    override val accent = Color(0xFFA78BFA)

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        DrawApp(open, onHome)
    }

    @Composable
    override fun HomeSummary() {
        Text("Pick an image, set it over the camera and trace it. Share a picture to Hive to open it here.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
    }
}
