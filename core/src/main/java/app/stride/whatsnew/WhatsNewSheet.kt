package app.stride.whatsnew

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.stride.host.AppInfo
import app.stride.host.Brand
import app.stride.ui.theme.StrideColors

private fun icon(name: String): ImageVector = when (name) {
    "apps" -> Icons.Filled.Apps
    "heart" -> Icons.Filled.Favorite
    "speed" -> Icons.Filled.Speed
    "bug" -> Icons.Filled.BugReport
    "watch" -> Icons.Filled.Watch
    "chart" -> Icons.Filled.Insights
    "bell" -> Icons.Filled.Notifications
    "palette" -> Icons.Filled.Palette
    "swim" -> Icons.Filled.Pool
    "run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "strength" -> Icons.Filled.FitnessCenter
    "music" -> Icons.Filled.MusicNote
    else -> Icons.Filled.AutoAwesome
}

private const val PREFS = "whats_new"

/**
 * Shows once after each update: the new release notes from assets/whats_new.json, or a short
 * "polished under the hood" note when an update had no visible changes. Nothing on a fresh install.
 */
@Composable
fun WhatsNewGate() {
    val context = LocalContext.current
    var result by remember { mutableStateOf<WhatsNewResult>(WhatsNewResult.Nothing) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = runCatching { context.assets.open("whats_new.json").bufferedReader().use { it.readText() } }.map(WhatsNew::parse).getOrDefault(emptyList())
        val current = AppInfo.versionCode
        result = WhatsNew.decide(entries, prefs.getString("last_id", null), prefs.getInt("last_version", 0), current, freshInstall(context))
        prefs.edit().putInt("last_version", current).putString("last_id", entries.firstOrNull()?.id ?: prefs.getString("last_id", null)).apply()
    }
    val r = result
    if (r is WhatsNewResult.Nothing) return
    Dialog(onDismissRequest = { result = WhatsNewResult.Nothing }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(StrideColors.Elev).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("WHAT'S NEW IN ${Brand.NAME.uppercase()}", style = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = StrideColors.Accent)
            Spacer(Modifier.height(10.dp))
            when (r) {
                is WhatsNewResult.Polish -> {
                    Text(r.emoji, fontSize = 52.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(r.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(r.text, style = TextStyle(fontSize = 15.sp), color = StrideColors.Dim, textAlign = TextAlign.Center)
                }
                is WhatsNewResult.Notes -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    r.entries.forEach { e ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(e.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                            e.items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(StrideColors.Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Icon(icon(item.icon), null, tint = StrideColors.Accent, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.text, style = TextStyle(fontSize = 15.sp), color = StrideColors.Text, modifier = Modifier.weight(1f).padding(top = 7.dp))
                                }
                            }
                        }
                    }
                }
                WhatsNewResult.Nothing -> Unit
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { result = WhatsNewResult.Nothing }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Nice!") }
            Spacer(Modifier.height(4.dp))
            Text("Build ${AppInfo.versionCode}", style = TextStyle(fontSize = 11.sp), color = StrideColors.Faint)
        }
    }
}

/** True on the very first launch after installing (not after an update). */
private fun freshInstall(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
    info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(false)
