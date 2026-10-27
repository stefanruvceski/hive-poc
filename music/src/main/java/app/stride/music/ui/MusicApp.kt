package app.stride.music.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.host.OpenRequest
import app.stride.music.data.MusicRepo
import app.stride.music.domain.Args
import app.stride.music.engine.DownloadService
import app.stride.music.engine.Engine
import app.stride.music.engine.Prog
import app.stride.music.engine.ProgressBus
import app.stride.music.engine.Registry
import app.stride.music.model.DlOptions
import app.stride.music.model.DlStatus
import app.stride.music.model.DownloadItem
import app.stride.music.player.Playback
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Hive Music (Tempo): paste or share a link, pick a preset and speed, download, listen. */
@Composable
fun MusicApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MusicRepo.init(context)
        if (MusicRepo.data.value.items.any { it.status == DlStatus.QUEUED }) runCatching { DownloadService.kick(context) }
    }
    DisposableEffect(Unit) {
        Playback.connect(context)
        onDispose { Playback.disconnect() }
    }
    var settings by rememberSaveable { mutableStateOf(false) }
    var player by rememberSaveable { mutableStateOf(false) }
    var sharedUrl by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        when {
            open.target == "player" -> player = true
            open.target.startsWith("add:") -> sharedUrl = open.target.removePrefix("add:")
        }
    }

    BackHandler(enabled = settings && !player) { settings = false }
    BackHandler(enabled = player) { player = false }
    Box(Modifier.fillMaxSize().musicGlow()) {
        if (settings) MusicSettings { settings = false }
        else MusicHome(onHome, sharedUrl, onSettings = { settings = true }, onPlayer = { player = true })
        AnimatedVisibility(visible = player, enter = slideInVertically(tween(380)) { it }, exit = slideOutVertically(tween(300)) { it }) {
            NowPlaying(onClose = { player = false })
        }
    }
}

@Composable
private fun MusicHome(onHome: (() -> Unit)?, initialUrl: String, onSettings: () -> Unit, onPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by MusicRepo.data.collectAsStateWithLifecycle()
    val progress by ProgressBus.map.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var opts by remember(d.activePresetId) { mutableStateOf(MusicRepo.activePreset(d).options) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val rows = d.items
    val active = rows.count { it.status.active }
    val finished = rows.count { !it.status.active }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (Playback.hasItem) 120.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onHome != null) GlassIconButton(Icons.Filled.Apps, "All apps", onClick = onHome)
                    Column(Modifier.weight(1f).padding(start = if (onHome != null) 12.dp else 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The site's eyebrow: a small teal dot with a glow, then the label in ice.
                            Box(Modifier.size(6.dp).shadow(6.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = MusicBrand2, spotColor = MusicBrand2).background(MusicBrand2, androidx.compose.foundation.shape.CircleShape))
                            Text("  AUDIO & VIDEO", color = MusicBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text("Music", color = MusicText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    GlassIconButton(Icons.Filled.Settings, "Music settings", onClick = onSettings)
                }
            }

            item {
                Glass(tint = MusicAccent) {
                    Text("New download", color = MusicMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    GlassField(
                        url, { url = it; error = null }, "Paste a link…", keyboardType = KeyboardType.Uri,
                        trailing = { GlassPill("Paste", selected = true) { clipboard.getText()?.text?.let { url = it; error = null } } },
                    )
                    error?.let { Text(it, color = StrideColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        "Download", subtitle = Args.summary(opts), enabled = url.isNotBlank(),
                        onClick = {
                            val link = Args.extractUrl(url)
                            if (link == null) error = "That doesn't look like a link"
                            else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                DownloadService.enqueue(ctx, link, opts)
                                url = ""
                            }
                        },
                    )
                }
            }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.presets.forEach { p -> GlassPill(p.name, d.activePresetId == p.id) { MusicRepo.setActive(p.id); opts = p.options } }
                }
            }

            if (opts.audioOnly) item { SpeedCard(opts) { opts = it } }

            item {
                Glass(onClick = { showMore = !showMore }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Download options", color = MusicText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (showMore) "Hide" else "Show", color = MusicBrand2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    if (showMore) OptionsBody(opts, { opts = it }, onSavePreset = { saveDialog = true })
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (active > 0) "In progress · $active" else "Downloads",
                        color = if (active > 0) StrideColors.Amber else MusicMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    if (finished > 0) GlassPill("Clear finished", selected = false) { MusicRepo.clearFinished() }
                }
            }
            if (rows.isEmpty()) item {
                Glass {
                    Text("Nothing here yet. Paste a link above, or tap Share → Hive Music in YouTube.", color = MusicMuted, fontSize = 13.sp)
                }
            }
            items(rows, key = { it.id }) { item ->
                DownloadRow(
                    item, progress[item.id],
                    nowPlaying = Playback.currentId?.substringBefore(':') == item.id.toString(),
                    onPlay = { scope.launch { Playback.playFrom(ctx, rows, item.id); onPlayer() } },
                    onCancel = { Registry.cancel(item.id) },
                    onRetry = { MusicRepo.requeue(item.id); DownloadService.kick(ctx) },
                    onDelete = { MusicRepo.delete(item.id) },
                )
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
        MiniPlayer(onOpen = onPlayer, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        GlassDialog("New preset", onDismiss = { saveDialog = false }) {
            Text(Args.summary(opts), color = MusicMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            GlassField(name, { name = it }, "Name, e.g. Pool 1.6×")
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPill("Cancel", selected = false, modifier = Modifier.weight(1f)) { saveDialog = false }
                GlassPill("Save", selected = true, enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) { MusicRepo.addPreset(name, opts); saveDialog = false }
            }
        }
    }
}

@Composable
private fun SpeedCard(o: DlOptions, onChange: (DlOptions) -> Unit) {
    val quick = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)
    Glass(tint = MusicAccent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.stride.ui.ProgressRing((o.speed - 0.5f) / 2.5f, size = 92, stroke = 9, color = MusicAccent, label = "${Args.fmtSpeed(o.speed)}×")
            Column(Modifier.padding(start = 18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Args.fmtSpeed(o.speed), color = MusicText, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" × speed", color = MusicMuted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(if (o.speed == 1f) "Original speed" else "Pitch stays the same", color = MusicMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassSlider(o.speed, 0.5f..3f, 0.05f) { onChange(o.copy(speed = (it * 20).roundToInt() / 20f)) }
        Spacer(Modifier.height(10.dp))
        Segmented(quick.map { Args.fmtSpeed(it) + "×" }, quick.indexOf(o.speed)) { onChange(o.copy(speed = quick[it])) }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, color = MusicMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun OptionsBody(o: DlOptions, onChange: (DlOptions) -> Unit, onSavePreset: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Labeled("Type") { Segmented(listOf("Audio", "Video"), if (o.audioOnly) 0 else 1) { onChange(o.copy(audioOnly = it == 0)) } }
        if (o.audioOnly) {
            val formats = listOf("mp3", "m4a", "opus")
            Labeled(if (o.needsReencode) "Format (speed and effects need MP3)" else "Format") {
                Segmented(formats.map { it.uppercase() }, formats.indexOf(o.effectiveFormat), formats.mapIndexed { i, _ -> i == 0 || !o.needsReencode }) {
                    onChange(o.copy(audioFormat = formats[it]))
                }
            }
            val kbps = listOf(64, 96, 128, 192, 256)
            Labeled("Quality (kbps)") { Segmented(kbps.map { "$it" }, kbps.indexOf(o.audioKbps)) { onChange(o.copy(audioKbps = kbps[it])) } }
            SwitchRow("Mono", o.mono, "Smaller file") { onChange(o.copy(mono = it)) }
            SwitchRow("Normalize loudness", o.normalize) { onChange(o.copy(normalize = it)) }
            SwitchRow("Trim long silences", o.trimSilence) { onChange(o.copy(trimSilence = it)) }
        } else {
            val heights = listOf(480, 720, 1080, 1440, 0)
            Labeled("Max resolution") { Segmented(heights.map { if (it == 0) "Max" else "${it}p" }, heights.indexOf(o.videoHeight)) { onChange(o.copy(videoHeight = heights[it])) } }
        }
        SwitchRow("Embed title and metadata", o.embedMeta) { onChange(o.copy(embedMeta = it)) }
        SwitchRow("Embed cover art", o.embedThumb) { onChange(o.copy(embedThumb = it)) }
        SwitchRow("Whole playlist", o.playlist) { onChange(o.copy(playlist = it)) }
        SwitchRow("Split by chapters", o.splitChapters) { onChange(o.copy(splitChapters = it)) }
        SwitchRow("aria2c", o.useAria2, "Faster on slow links") { onChange(o.copy(useAria2 = it)) }
        Labeled("Extra yt-dlp arguments") {
            GlassField(o.extraArgs, { onChange(o.copy(extraArgs = it)) }, "e.g. --sponsorblock-remove sponsor")
        }
        GlassPill("Save as preset…", selected = true, modifier = Modifier.padding(top = 8.dp), onClick = onSavePreset)
    }
}

private fun statusText(item: DownloadItem, prog: Prog?): String = when (item.status) {
    DlStatus.QUEUED -> "Queued · ${item.summary}"
    DlStatus.FETCHING -> "Reading info…"
    DlStatus.RUNNING -> if (prog == null) "Downloading…" else Args.progressText(prog.pct, prog.eta)
    DlStatus.DONE -> "Done · ${item.summary}" + (item.message?.let { " · $it" } ?: "")
    DlStatus.FAILED -> item.message ?: "Failed"
    DlStatus.CANCELED -> "Canceled"
}

@Composable
private fun DownloadRow(
    item: DownloadItem, prog: Prog?, nowPlaying: Boolean,
    onPlay: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val playable = Playback.playable(item)
    val tone = when (item.status) {
        DlStatus.QUEUED -> StrideColors.Amber
        DlStatus.FETCHING, DlStatus.RUNNING -> MusicBrand
        DlStatus.DONE -> MusicBrand2
        DlStatus.FAILED -> StrideColors.Danger
        DlStatus.CANCELED -> StrideColors.Faint
    }
    Glass(tint = if (nowPlaying) MusicBrand2 else if (item.status == DlStatus.RUNNING) MusicBrand else null, onClick = if (playable) onPlay else null, padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(item.thumbnail, Modifier.size(52.dp), 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title.ifBlank { item.url }, color = MusicText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.uploader?.let { Text(it, color = MusicMuted, fontSize = 12.sp, maxLines = 1) }
                Text(statusText(item, prog), color = tone, fontSize = 12.sp, maxLines = 2)
            }
        }
        if (item.status == DlStatus.RUNNING || item.status == DlStatus.FETCHING) {
            GlassProgress(prog?.pct?.takeIf { it >= 0 }?.let { it / 100f }, Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (item.status.active) GlassPill("Cancel", selected = true, tint = StrideColors.Danger, onClick = onCancel)
            if (playable) GlassPill(if (nowPlaying) "Playing" else "▶ Play", selected = true, onClick = onPlay)
            val first = item.outputs.firstOrNull()
            if (item.status == DlStatus.DONE && first != null) {
                if (!playable) GlassPill("Open", selected = true) { openUri(ctx, first) }
                GlassPill("Share", selected = false) { shareUri(ctx, first) }
            }
            if (item.status == DlStatus.FAILED || item.status == DlStatus.CANCELED) GlassPill("Retry", selected = true, onClick = onRetry)
            if (!item.status.active) GlassPill("Remove", selected = false, onClick = onDelete)
        }
    }
}

private fun openUri(ctx: Context, s: String) {
    val uri = Uri.parse(s)
    val type = ctx.contentResolver.getType(uri) ?: "*/*"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun shareUri(ctx: Context, s: String) {
    val uri = Uri.parse(s)
    ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(ctx.contentResolver.getType(uri) ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share",
        ),
    )
}

/** Concurrency, where files go, Share behaviour, yt-dlp updates, presets. */
@Composable
private fun MusicSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by MusicRepo.data.collectAsStateWithLifecycle()
    val s = d.settings
    var updateText by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { version = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Engine.version(ctx) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.size(12.dp))
                Text("Music settings", color = MusicText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Glass {
                Text("Downloads at once", color = MusicMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("1", "2", "3", "4"), s.concurrency - 1) { MusicRepo.setSettings(s.copy(concurrency = it + 1)) }
                SwitchRow("Save to Downloads", s.saveToDownloads, "Otherwise Music/Hive Music (Movies for video)") { MusicRepo.setSettings(s.copy(saveToDownloads = it)) }
                SwitchRow("Ask on Share", s.askOnShare, "Open Music to pick options instead of downloading right away") { MusicRepo.setSettings(s.copy(askOnShare = it)) }
                SwitchRow("Update yt-dlp daily", s.autoUpdate, "Sites change; old versions stop working") { MusicRepo.setSettings(s.copy(autoUpdate = it)) }
            }
        }
        item {
            Glass {
                Text("yt-dlp " + (version ?: "…"), color = MusicText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                GlassPill("Update now", selected = true, modifier = Modifier.padding(vertical = 8.dp)) {
                    updateText = "Updating…"
                    scope.launch {
                        updateText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Engine.updateNow(ctx) }
                        version = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Engine.version(ctx) }
                    }
                }
                updateText?.let { Text(it, color = MusicMuted, fontSize = 12.sp) }
            }
        }
        item {
            Glass {
                Text("Presets", color = MusicText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                d.presets.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = MusicText, fontSize = 15.sp)
                            Text(Args.summary(p.options), color = MusicMuted, fontSize = 12.sp)
                        }
                        if (!p.builtin) GlassPill("Delete", selected = true, tint = StrideColors.Danger) { MusicRepo.deletePreset(p.id) }
                    }
                }
            }
        }
    }
}
