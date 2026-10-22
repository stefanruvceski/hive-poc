package app.stride.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import kotlin.math.roundToInt

/**
 * Slim banner under the top bar when a newer build is on GitHub, styled like
 * Streakli's UpdateBanner: accent-tinted row, one-line notes, "Install" action.
 * Tapping downloads the APK (progress shown in place) and opens the installer.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancelPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last release while the banner animates out.
    var shown by remember { mutableStateOf(state.release) }
    if (state.release != null) shown = state.release

    AnimatedVisibility(
        visible = state.release != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val release = shown ?: return@AnimatedVisibility
        val phase = state.phase
        Surface(
            onClick = onInstall,
            enabled = phase !is UpdatePhase.Downloading,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = StrideColors.Accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, StrideColors.Accent.copy(alpha = 0.33f)),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, tint = StrideColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (phase) {
                            is UpdatePhase.Downloading ->
                                phase.progress?.let { "Downloading… ${(it * 100).roundToInt()}%" } ?: "Downloading…"
                            else -> "New version · build ${release.build}"
                        },
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = StrideColors.Text,
                        maxLines = 1,
                    )
                    when {
                        phase is UpdatePhase.Downloading -> {
                            Spacer(Modifier.height(6.dp))
                            ProgressTrack(phase.progress ?: 0f)
                        }
                        phase is UpdatePhase.Failed -> Text(
                            "Download failed · tap to try again",
                            style = TextStyle(fontSize = 12.sp),
                            color = StrideColors.Danger,
                            maxLines = 1,
                        )
                        release.notes.isNotEmpty() -> Text(
                            release.notes,
                            style = TextStyle(fontSize = 12.sp),
                            color = StrideColors.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (phase !is UpdatePhase.Downloading) {
                    Text(
                        "Install",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                        color = StrideColors.Accent,
                        modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Dismiss", tint = StrideColors.Faint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (state.askPermission) {
        AlertDialog(
            onDismissRequest = onCancelPermission,
            containerColor = StrideColors.Elev,
            title = { Text("Allow Stride to install updates") },
            text = {
                Text(
                    "Android only installs apps from sources you allow. On the next screen, turn on " +
                        "\"Allow from this source\" for Stride, then go back. The update downloads and the installer opens.\n\n" +
                        "You only need to do this once.",
                    color = StrideColors.Dim,
                )
            },
            confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onCancelPermission) { Text("Not now") } },
        )
    }
}

/** Thin progress bar like Streakli's Bar: elev2 track, accent fill. */
@Composable
private fun ProgressTrack(value: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(StrideColors.Elev2)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(StrideColors.Accent))
    }
}
