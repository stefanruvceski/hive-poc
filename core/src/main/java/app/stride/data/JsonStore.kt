package app.stride.data

import android.content.Context
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * One module's data as a single JSON file in the app's private storage (no database, no account:
 * everything lives on the phone). Writes go to a temp file first and are swapped in, so a crash
 * never leaves half a file. Shared by every module; each passes its own file name and type.
 */
open class JsonStore<T>(
    context: Context,
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val empty: () -> T,
    /** The app and background work share one process; a module's store and worker share this lock. */
    val lock: Any = Any(),
) {
    private val file = File(context.filesDir, fileName)

    fun load(): T = try {
        if (file.exists()) StrideJson.decodeFromString(serializer, file.readText()) else empty()
    } catch (e: Exception) {
        empty()
    }

    /** Read-modify-write under the lock, for background work. */
    fun update(f: (T) -> T): T = synchronized(lock) {
        f(load()).also { write(it) }
    }

    fun save(data: T) {
        synchronized(lock) { write(data) }
    }

    private fun write(data: T) {
        val tmp = File(file.parentFile, "$fileName.tmp")
        tmp.writeText(StrideJson.encodeToString(serializer, data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
