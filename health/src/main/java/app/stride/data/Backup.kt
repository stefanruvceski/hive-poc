package app.stride.data

import app.stride.host.Brand
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.stride.model.AppData
import java.time.LocalDate
import java.time.LocalDateTime

/** Export/import of all app data as one JSON file. API keys are never written to a backup. */
object Backup {
    fun export(d: AppData): String = StrideJson.encodeToString(AppData.serializer(), sanitize(d))

    /** Drops secrets and device-only settings; pure, unit tested. */
    fun sanitize(d: AppData): AppData = d.copy(
        settings = d.settings.copy(apiKey = "", anthropicKey = "", backupFolder = null, lastBackup = null),
    )

    /**
     * Restores [text] over [current], keeping this phone's keys and backup folder.
     * Throws IllegalArgumentException when the file isn't a Stride backup.
     */
    fun restore(text: String, current: AppData): AppData {
        val d = runCatching { StrideJson.decodeFromString(AppData.serializer(), text) }.getOrNull()
            ?: throw IllegalArgumentException("This file isn't a ${Brand.NAME} backup.")
        if (d.strength.isEmpty() && d.activities.isEmpty() && d.weights.isEmpty() && d.weekPlans.isEmpty()) {
            throw IllegalArgumentException("This backup has no workouts, weights or plans in it.")
        }
        val s = current.settings
        return d.copy(settings = d.settings.copy(apiKey = s.apiKey, anthropicKey = s.anthropicKey, backupFolder = s.backupFolder, lastBackup = s.lastBackup))
    }

    fun fileName(date: LocalDate = LocalDate.now()) = "stride-backup-$date.json"

    fun write(context: Context, uri: Uri, d: AppData) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(export(d).toByteArray()) }
            ?: throw IllegalStateException("Can't write the backup file.")
    }

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Can't read the backup file.")

    /** Weekly automatic backup into the folder the user picked; true when a file was written. */
    fun autoBackupIfDue(context: Context, store: Store, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val d = store.load()
        val folder = d.settings.backupFolder ?: return false
        val last = d.settings.lastBackup?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (last != null && last.isAfter(now.minusDays(7))) return false
        val tree = Uri.parse(folder)
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(context.contentResolver, dir, "application/json", fileName(now.toLocalDate()))
            ?: return false
        write(context, file, d)
        store.update { it.copy(settings = it.settings.copy(lastBackup = now.toString())) }
        DataEvents.notifyChanged()
        return true
    }
}
