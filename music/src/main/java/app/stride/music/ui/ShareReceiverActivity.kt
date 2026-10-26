package app.stride.music.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import app.stride.host.Host
import app.stride.music.data.MusicRepo
import app.stride.music.domain.Args
import app.stride.music.engine.DownloadService

/** Share → Hive Music: queues the link with the active preset (or opens Music to choose), without showing a screen. */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Args.extractUrl(text)
        MusicRepo.init(this)
        when {
            url == null -> Toast.makeText(this, "No link in what was shared", Toast.LENGTH_SHORT).show()
            MusicRepo.data.value.settings.askOnShare -> startActivity(Host.openIntent(this, "music/add:$url"))
            else -> {
                val preset = MusicRepo.activePreset()
                DownloadService.enqueue(this, url, preset.options)
                Toast.makeText(this, "Downloading · ${preset.name}", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
