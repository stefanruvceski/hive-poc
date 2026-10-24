package app.stride.live

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.stride.host.Host
import app.stride.data.Store
import app.stride.domain.Analyzer
import app.stride.widget.Widgets
import java.time.LocalDate

/** Quick Settings tile: one tap opens today's workout; the subtitle shows today's plan. */
class StartWorkoutTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val today = LocalDate.now()
        val plan = runCatching { Analyzer.day(today, today, Store(this).load()).plan }.getOrNull()
        tile.label = "Start workout"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = plan?.title ?: "Stride"
        tile.state = if (plan?.kind?.isStrength == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Host.openIntent(this, "workout")
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
