package app.stride.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Pick a backup (newest in the folder or a file), confirm, restore, close Hive. Shared by the settings card and the first-run offer. */
private class RestoreFlow(val pickFolder: () -> Unit, val pickFile: () -> Unit, val fromFolder: () -> Unit)

@Composable
private fun rememberRestoreFlow(restoreAfterPick: Boolean = false, onFolderSet: () -> Unit = {}): Pair<RestoreFlow, @Composable () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, Map<String, ByteArray>>?>(null) }

    fun load(uri: Uri, label: String) {
        scope.launch {
            val files = withContext(Dispatchers.IO) { runCatching { HiveBackup.read(context, uri) }.getOrNull() }
            if (files == null) Toast.makeText(context, "That isn't a Hive backup", Toast.LENGTH_LONG).show() else pending = label to files
        }
    }

    fun fromFolder() {
        scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { HiveBackup.newest(context) }.getOrNull() }
            if (found == null) Toast.makeText(context, "No Hive backup in that folder yet", Toast.LENGTH_LONG).show() else load(found.first, found.second)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { HiveBackup.setFolder(context, uri) }
            if (restoreAfterPick) fromFolder() else onFolderSet()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "the file")
    }

    val dialog: @Composable () -> Unit = {
        pending?.let { (label, files) ->
            val apps = HiveBackup.modules.filter { m -> files.keys.any { it.startsWith("${m.id}/") } }.joinToString(", ") { it.title }
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text("Restore this backup?") },
                text = {
                    Text(
                        "$label\n\nReplaces the data in: ${apps.ifEmpty { "nothing (empty backup)" }}. Apps not in the backup keep theirs. " +
                            "Keys and sign-ins on this phone stay. Hive closes afterwards; open it again.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pending = null
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { runCatching { HiveBackup.restore(context, files) } }
                            done.onSuccess { restored ->
                                Toast.makeText(context, "Restored ${restored.joinToString(", ")}. Open Hive again.", Toast.LENGTH_LONG).show()
                                context.activity()?.let(HiveBackup::closeApp)
                            }.onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Restore", color = StrideColors.Danger) }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
            )
        }
    }
    return RestoreFlow({ folderPicker.launch(null) }, { filePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, ::fromFolder) to dialog
}

/** Hive settings → Backup: the folder, the last backup, back up now, restore, share. */
@Composable
fun BackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val folder = remember(tick) { HiveBackup.folder(context) }
    val last = remember(tick) { HiveBackup.lastBackup(context) }
    val error = remember(tick) { HiveBackup.lastError(context) }

    fun backup() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { HiveBackup.backupNow(context) } }
            busy = false
            tick++
            Toast.makeText(context, r.fold({ "Saved $it" }, { "Backup failed: ${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }
    val (flow, dialog) = rememberRestoreFlow(onFolderSet = { tick++; backup() })

    FCard {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every app in one file, saved once a day into a folder on your phone (two weeks of daily copies and one per month). " +
                "The folder stays when Hive is deleted: after a reinstall, restore from it here. Keys and sign-ins are never in it.",
            style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(
            when {
                folder == null -> "No folder yet. Pick one, e.g. Documents/Hive."
                else -> "Folder: ${folder.lastPathSegment?.substringAfter(':')?.ifBlank { "picked" } ?: "picked"}"
            },
            style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text,
        )
        last?.let {
            Text("Last backup: " + DateTimeFormatter.ofPattern("d.M.yyyy HH:mm").format(it.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        }
        error?.let { Text("Last try failed: $it", style = MaterialTheme.typography.bodySmall, color = StrideColors.Danger) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (folder == null) Button(onClick = flow.pickFolder, modifier = Modifier.weight(1f)) { Text("Pick folder") }
            else {
                Button(onClick = ::backup, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Back up now") }
                OutlinedButton(onClick = flow.pickFolder, modifier = Modifier.weight(1f)) { Text("Change folder") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (folder != null) TextButton(onClick = flow.fromFolder) { Text("Restore latest") }
            TextButton(onClick = flow.pickFile) { Text("Restore from file") }
            if (folder != null) TextButton(onClick = {
                scope.launch {
                    val f = withContext(Dispatchers.IO) { runCatching { HiveBackup.newest(context) }.getOrNull() }
                    if (f == null) Toast.makeText(context, "No backup yet", Toast.LENGTH_SHORT).show()
                    else context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, f.first).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Share ${f.second}",
                        ),
                    )
                }
            }) { Text("Share") }
        }
    }
    dialog()
}

/** On a fresh install with no data: offer to restore from a Hive backup folder or file. */
@Composable
fun RestoreOffer() {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(HiveBackup.shouldOfferRestore(context)) }
    if (!shown) return
    val (flow, dialog) = rememberRestoreFlow(restoreAfterPick = true)
    FCard {
        Text("Reinstalling Hive?", style = MaterialTheme.typography.titleMedium)
        Text(
            "If you have a Hive backup, pick its folder (e.g. Documents/Hive) and everything comes back: all apps, workouts, habits, money, books, wallet.",
            style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = flow.pickFolder, modifier = Modifier.weight(1f)) { Text("Pick backup folder") }
            OutlinedButton(onClick = flow.pickFile, modifier = Modifier.weight(1f)) { Text("Backup file") }
        }
        TextButton(onClick = { HiveBackup.dismissOffer(context); shown = false }) { Text("Start fresh") }
    }
    dialog()
}
