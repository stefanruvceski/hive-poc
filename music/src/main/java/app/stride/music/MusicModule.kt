package app.stride.music

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.music.data.MusicRepo
import app.stride.music.engine.DownloadService
import app.stride.music.engine.Engine
import app.stride.music.model.DlStatus
import app.stride.music.ui.MusicAccent
import app.stride.music.ui.MusicApp
import app.stride.ui.theme.StrideColors

/** Tempo inside Hive: yt-dlp downloads (sped up, pitch kept) and a player for them. Files go to the phone's Music folder. */
object MusicModule : AppModule {
    override val id = "music"
    override val title = "Music"
    override val subtitle = "Download audio and video, sped up, and play it"
    override val icon: ImageVector = Icons.Filled.MusicNote
    override val accent = MusicAccent
    override val dataFiles = listOf("music.json")

    override fun onAppStart(context: Context) {
        MusicRepo.init(context)
        DownloadService.channels(context)
        // Unpacking Python and ffmpeg takes a few seconds on the first start: off the main thread.
        val auto = MusicRepo.data.value.settings.autoUpdate
        Thread { Engine.init(context.applicationContext, auto) }.start()
        // Downloads that were running when the process died go back in the queue (resumed when Music opens).
        MusicRepo.resetStuck()
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        MusicApp(open, onHome)
    }

    @Composable
    override fun HomeSummary() {
        val context = LocalContext.current
        LaunchedEffect(Unit) { MusicRepo.init(context) }
        val d by MusicRepo.data.collectAsStateWithLifecycle()
        val active = d.items.count { it.status.active }
        val done = d.items.count { it.status == DlStatus.DONE }
        Column {
            Text(
                when {
                    active > 0 -> "Downloading $active…"
                    d.items.isEmpty() -> "Share a link to Hive Music"
                    else -> d.items.first().title.ifBlank { d.items.first().url }
                },
                style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1,
            )
            Text("$done download${if (done == 1) "" else "s"} · ${MusicRepo.activePreset(d).name}", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        }
    }
}
