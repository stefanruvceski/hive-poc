package app.stride.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stride.host.AppInfo
import app.stride.host.AppModule
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * One backup for all of Hive: a zip with every module's data files (no keys: those live in prefs no backup
 * carries), written once a day into a folder the user picked (their storage, so it outlives the app), with
 * two weeks of daily copies and one per month kept. Restore puts the files back and closes Hive.
 */
object HiveBackup {
    private const val PREFS = "hive_prefs"
    private const val KEY_TREE = "backup_tree"
    private const val KEY_LAST = "backup_last"
    private const val KEY_ERROR = "backup_error"
    private const val KEY_OFFER = "restore_offer_done"
    private const val WORK = "hive-backup"

    /** Set by the app shell at start (Modules.all). */
    var modules: List<AppModule> = emptyList()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)
        ?.takeIf { uri -> context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission } }

    fun lastBackup(context: Context): Instant? = prefs(context).getString(KEY_LAST, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    fun lastError(context: Context): String? = prefs(context).getString(KEY_ERROR, null)

    fun setFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        prefs(context).edit().putString(KEY_TREE, tree.toString()).putBoolean(KEY_OFFER, true).apply()
        schedule(context)
    }

    /** A new install with no data in any app yet: worth offering a restore (once). */
    fun shouldOfferRestore(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_OFFER, false) && modules.flatMap { it.dataFiles }.none { File(context.filesDir, it).exists() }

    fun dismissOffer(context: Context) = prefs(context).edit().putBoolean(KEY_OFFER, true).apply()

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).build(),
        )
    }

    // ------------------------------------------------------------------ backup

    private fun collect(dir: File, prefix: String, out: MutableMap<String, ByteArray>) {
        if (dir.isDirectory) dir.listFiles()?.forEach { collect(it, "$prefix/${it.name}", out) }
        else if (dir.isFile && !dir.name.endsWith(".tmp")) out[prefix] = dir.readBytes()
    }

    /** The zip's contents: manifest.json plus "<module>/<file>" for every data file. */
    fun files(context: Context): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        modules.forEach { m -> m.dataFiles.forEach { collect(File(context.filesDir, it), "${m.id}/$it", out) } }
        val manifest = """{"app":"Hive","version":"${AppInfo.versionName}","build":${AppInfo.versionCode},"created":"${Instant.now()}",""" +
            """"modules":[${modules.filter { m -> out.keys.any { it.startsWith("${m.id}/") } }.joinToString(",") { "\"${it.id}\"" }}]}"""
        out["manifest.json"] = manifest.toByteArray()
        return out
    }

    private data class Doc(val id: String, val name: String)

    private fun children(context: Context, tree: Uri): List<Doc> {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(Doc(c.getString(0), c.getString(1))) }
        }.orEmpty()
    }

    private fun docUri(tree: Uri, id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** Writes today's backup into the folder (replacing today's earlier one) and prunes old copies. Returns the file name. */
    fun backupNow(context: Context): String {
        val tree = folder(context) ?: throw IllegalStateException("Pick a backup folder first.")
        val bytes = BackupFormat.pack(files(context))
        val name = BackupFormat.fileName(LocalDate.now())
        val existing = children(context, tree)
        existing.filter { it.name == name }.forEach { DocumentsContract.deleteDocument(context.contentResolver, docUri(tree, it.id)) }
        val parent = docUri(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", name)
            ?: throw IllegalStateException("Couldn't create $name in the backup folder.")
        context.contentResolver.openOutputStream(doc, "wt")?.use { it.write(bytes) } ?: throw IllegalStateException("Couldn't write $name.")
        val gone = BackupFormat.toDelete(existing.map { it.name } + name).toSet()
        existing.filter { it.name in gone }.forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri(tree, it.id)) } }
        prefs(context).edit().putString(KEY_LAST, Instant.now().toString()).remove(KEY_ERROR).apply()
        return name
    }

    /** The newest Hive backup in the folder: its uri and file name. */
    fun newest(context: Context): Pair<Uri, String>? {
        val tree = folder(context) ?: return null
        val docs = children(context, tree)
        val name = BackupFormat.newest(docs.map { it.name }) ?: return null
        return docUri(tree, docs.first { it.name == name }.id) to name
    }

    // ------------------------------------------------------------------ restore

    /** Reads a backup zip; null when it isn't one (no manifest). */
    fun read(context: Context, uri: Uri): Map<String, ByteArray>? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return runCatching { BackupFormat.unpack(bytes) }.getOrNull()?.takeIf { "manifest.json" in it }
    }

    /**
     * Replaces each module's data files with the backup's (modules missing from the backup keep theirs).
     * Returns the titles of the apps restored. The app must close afterwards so every screen reloads.
     */
    fun restore(context: Context, files: Map<String, ByteArray>): List<String> = modules.mapNotNull { m ->
        val mine = BackupFormat.moduleFiles(files, m.id).filterKeys { path -> BackupFormat.safe(path) && m.dataFiles.any { path == it || path.startsWith("$it/") } }
        if (mine.isEmpty()) return@mapNotNull null
        m.dataFiles.forEach { File(context.filesDir, it).deleteRecursively() }
        mine.forEach { (path, bytes) ->
            val f = File(context.filesDir, path)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        m.title
    }.also { prefs(context).edit().putBoolean(KEY_OFFER, true).apply() }

    /** Closes Hive after a restore; the next start loads the restored files. */
    fun closeApp(activity: Activity) {
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (folder(applicationContext) == null) return Result.success()
            return try {
                backupNow(applicationContext)
                Result.success()
            } catch (e: Exception) {
                prefs(applicationContext).edit().putString(KEY_ERROR, e.message ?: e.javaClass.simpleName).apply()
                Result.retry()
            }
        }
    }
}
