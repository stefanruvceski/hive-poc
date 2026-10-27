package app.stride.music.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.music.domain.Args
import app.stride.music.player.Playback
import app.stride.ui.theme.StrideColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** The position isn't sent as an event, so it's polled while the player is on screen. */
@Composable
private fun rememberPosition(): Long {
    var pos by remember { mutableLongStateOf(Playback.position) }
    LaunchedEffect(Playback.isPlaying, Playback.currentId) {
        while (true) {
            pos = Playback.position
            delay(if (Playback.isPlaying) 250 else 1000)
        }
    }
    return pos
}

/** A round glass button without a ripple that shrinks a little when pressed. */
@Composable
private fun RoundButton(size: Dp, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = 800f), label = "press")
    Box(
        Modifier.size(size).scale(s).clip(CircleShape)
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(MusicBrand, MusicDeep))) else Modifier,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The glass capsule at the bottom: cover, title, play/pause, next and a thin progress line. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (!Playback.hasItem) return
    val pos = rememberPosition()
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 10.dp).fillMaxWidth().clip(shape)
            .glassSurface(shape, fill = MusicBg.copy(alpha = 0.55f), haze = LocalHaze.current)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(Playback.artwork, Modifier.size(48.dp), 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(Playback.title, color = MusicText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Playback.artist?.let { Text(it, color = MusicMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            RoundButton(44.dp, filled = false, onClick = { Playback.toggle() }) {
                Icon(if (Playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = MusicText, modifier = Modifier.size(28.dp))
            }
            RoundButton(44.dp, filled = false, enabled = Playback.hasNext, onClick = { Playback.next() }) {
                Icon(Icons.Filled.SkipNext, "Next", tint = if (Playback.hasNext) MusicText else StrideColors.Faint, modifier = Modifier.size(26.dp))
            }
        }
        val f = if (Playback.duration > 0) (pos.toFloat() / Playback.duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(f).height(2.dp).background(MusicAccent))
        }
    }
}

/** Full-screen player in the Apple Music manner: blurred cover behind, big cover, scrubber, controls, speed. */
@Composable
fun NowPlaying(onClose: () -> Unit) {
    val pos = rememberPosition()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val dur = Playback.duration
    val fraction = if (dragging) dragFraction else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val artScale by animateFloatAsState(if (Playback.isPlaying) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "art")

    Box(Modifier.fillMaxSize().background(MusicBg)) {
        Playback.artwork?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(90.dp).scale(1.4f), alpha = 0.6f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MusicBg.copy(alpha = 0.35f), MusicBg.copy(alpha = 0.7f), MusicBg))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = MusicMuted, modifier = Modifier.size(30.dp)) }
                Text("NOW PLAYING", color = MusicMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(Playback.artwork, Modifier.fillMaxWidth().aspectRatio(1f).scale(artScale), 28.dp)
            }
            Column(Modifier.fillMaxWidth()) {
                Text(Playback.title, color = MusicText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Playback.artist ?: " ", color = MusicMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(top = 24.dp))
            Scrubber(
                fraction = fraction, active = dragging,
                onDrag = { dragging = true; dragFraction = it },
                onRelease = {
                    dragging = false
                    if (dur > 0) Playback.seekTo((dragFraction * dur).toLong())
                },
            )
            val shown = if (dragging) (dragFraction * dur).toLong() else pos
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(fmtTime(shown), color = MusicMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("-" + fmtTime(dur - shown), color = MusicMuted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(64.dp, filled = false, onClick = { Playback.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous", tint = MusicText, modifier = Modifier.size(40.dp)) }
                RoundButton(88.dp, filled = true, onClick = { Playback.toggle() }) {
                    Icon(if (Playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause", tint = MusicText, modifier = Modifier.size(48.dp))
                }
                RoundButton(64.dp, filled = false, enabled = Playback.hasNext, onClick = { Playback.next() }) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = if (Playback.hasNext) MusicText else StrideColors.Faint, modifier = Modifier.size(40.dp))
                }
            }
            Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPill("${Args.fmtSpeed(Playback.speed)}× speed", selected = Playback.speed != 1f) { Playback.cycleSpeed() }
                GlassPill("Stop", selected = false) { Playback.stop(); onClose() }
            }
        }
    }
}

/** A thin bar that thickens while dragged, like the iOS player. */
@Composable
private fun Scrubber(fraction: Float, active: Boolean, onDrag: (Float) -> Unit, onRelease: () -> Unit) {
    val h by animateDpAsState(if (active) 12.dp else 7.dp, spring(stiffness = 600f), label = "scrub")
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onDrag((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, _ -> onDrag((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) { detectTapGestures { onDrag((it.x / size.width).coerceIn(0f, 1f)); onRelease() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxWidth(fraction).height(h).background(if (active) MusicText else MusicText.copy(alpha = 0.8f)))
        }
    }
}
