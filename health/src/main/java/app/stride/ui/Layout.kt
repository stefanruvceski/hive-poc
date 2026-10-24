package app.stride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stride.model.LayoutPrefs
import app.stride.ui.theme.StrideColors

/** What a tab needs to draw its sections in order and, in edit mode, the controls around them. */
class SectionHost(
    val tab: TabDef,
    val prefs: LayoutPrefs,
    val editing: Boolean,
    val onPrefs: (LayoutPrefs) -> Unit,
    val onLongPress: () -> Unit,
) {
    /**
     * Emits the sections in the user's order. [content] maps a section id to its items; hidden
     * sections are skipped, and in edit mode every section gets a bar to hide or move it.
     */
    fun LazyListScope.sections(build: SectionContent.() -> Unit) {
        val content = SectionContent().apply(build).map
        val list = Layouts.sections(prefs, tab)
        list.forEachIndexed { i, s ->
            val hidden = Layouts.hidden(prefs, tab, s)
            if (editing) {
                item(key = "edit-${s.id}") {
                    EditBar(
                        title = s.title,
                        hidden = hidden,
                        canUp = i > 0,
                        canDown = i < list.lastIndex,
                        onToggle = { onPrefs(Layouts.toggleSection(prefs, tab, s)) },
                        onUp = { onPrefs(Layouts.moveSection(prefs, tab, s, -1)) },
                        onDown = { onPrefs(Layouts.moveSection(prefs, tab, s, 1)) },
                    )
                }
            }
            if (!hidden) content[s.id]?.invoke(this)
        }
    }
}

class SectionContent {
    val map = mutableMapOf<String, LazyListScope.() -> Unit>()

    fun section(id: String, body: LazyListScope.() -> Unit) {
        map[id] = body
    }
}

@Composable
fun EditBar(title: String, hidden: Boolean, canUp: Boolean, canDown: Boolean, onToggle: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape)
            .background(if (hidden) StrideColors.Elev.copy(alpha = 0.5f) else StrideColors.Accent.copy(alpha = 0.10f))
            .border(1.dp, if (hidden) StrideColors.Border else StrideColors.Accent.copy(alpha = 0.5f), shape)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (hidden) StrideColors.Faint else StrideColors.Text,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
        IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
        IconButton(onClick = onToggle) {
            Icon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hidden) "Show" else "Hide",
                tint = if (hidden) StrideColors.Faint else StrideColors.Accent)
        }
        Spacer(Modifier.width(2.dp))
    }
}

/**
 * Long press anywhere on a tab's content (cards included) to enter edit mode. Watches the
 * pointer in the Initial pass, so it works over buttons and clickable cards; a scroll or a
 * normal tap cancels it.
 */
fun Modifier.longPressToEdit(enabled: Boolean, onLongPress: () -> Unit): Modifier = if (!enabled) this else composed {
    val targets = LocalLongPressTargets.current
    val haptics = LocalHapticFeedback.current
    val origin = remember { arrayOf(Offset.Zero) }
    val action by rememberUpdatedState(onLongPress)
    onGloballyPositioned { origin[0] = it.positionInRoot() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var cancelled = false
            val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) {
                        cancelled = true
                        break
                    }
                }
            } == null
            if (timedOut && !cancelled) {
                // A card with its own long-press action (e.g. weekly targets) wins over section editing.
                val own = targets.find(origin[0] + down.position)
                if (own != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    own()
                } else {
                    action()
                }
                // Swallow the rest of this touch so the card under the finger doesn't also open.
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                    if (ev.changes.none { it.pressed }) break
                }
            }
        }
    }
}

/** Cards that open something of their own on a long press, by their place on screen. */
class LongPressTargets {
    private val regions = mutableMapOf<Any, Pair<Rect, () -> Unit>>()

    fun set(key: Any, bounds: Rect, action: () -> Unit) {
        regions[key] = bounds to action
    }

    fun remove(key: Any) {
        regions.remove(key)
    }

    fun find(p: Offset): (() -> Unit)? = regions.values.firstOrNull { it.first.contains(p) }?.second
}

val LocalLongPressTargets = staticCompositionLocalOf { LongPressTargets() }

/** Long-pressing this element runs [action] instead of entering section edit mode. */
fun Modifier.onLongPressOpen(key: Any, action: () -> Unit): Modifier = composed {
    val targets = LocalLongPressTargets.current
    val current by rememberUpdatedState(action)
    DisposableEffect(key) { onDispose { targets.remove(key) } }
    onGloballyPositioned { targets.set(key, it.boundsInRoot()) { current() } }
}
