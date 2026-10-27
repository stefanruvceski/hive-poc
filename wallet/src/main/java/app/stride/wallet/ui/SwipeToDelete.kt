package app.stride.wallet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe right to left to delete: a red bin grows behind the row. Let go past halfway (or flick) and it's deleted;
 * let go earlier and the row stays open on the bin, which can be tapped; a short swipe snaps back.
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reveal = with(density) { 84.dp.toPx() }
    var width by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }

    fun delete() = scope.launch {
        offset.animateTo(-width, tween(160))
        onDelete()
    }
    fun settle(velocity: Float) = scope.launch {
        val x = offset.value
        when {
            width > 0 && (x < -width * 0.5f || (velocity < -2500f && x < -reveal)) -> delete()
            x < -reveal * 0.5f || velocity < -600f -> offset.animateTo(-reveal, tween(160))
            else -> offset.animateTo(0f, tween(160))
        }
    }

    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        val shown = -offset.value
        if (shown > 0.5f) {
            Box(
                Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.fillMaxHeight().width(with(density) { shown.toDp() })
                        .clip(RoundedCornerShape(22.dp)).background(StrideColors.Danger)
                        .clickable { delete() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-width, 0f)) } },
                    Orientation.Horizontal,
                    onDragStopped = { v -> settle(v) },
                ),
        ) { content() }
    }
}
