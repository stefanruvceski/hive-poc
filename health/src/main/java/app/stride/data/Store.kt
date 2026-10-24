package app.stride.data

import android.content.Context
import app.stride.model.AppData
import java.io.File

class Store(context: Context) {
    companion object {
        /** The app and the background worker share one process; this keeps their writes apart. */
        val LOCK = Any()
    }

    private val file = File(context.filesDir, "stride.json")

    fun load(): AppData = try {
        if (file.exists()) StrideJson.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    } catch (e: Exception) {
        AppData()
    }

    /** Read-modify-write under the shared lock, for the background worker. */
    fun update(f: (AppData) -> AppData): AppData = synchronized(LOCK) {
        f(load()).also { save(it) }
    }

    fun save(data: AppData) {
        synchronized(LOCK) { write(data) }
    }

    private fun write(data: AppData) {
        val tmp = File(file.parentFile, "stride.json.tmp")
        tmp.writeText(StrideJson.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
