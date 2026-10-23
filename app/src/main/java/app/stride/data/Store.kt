package app.stride.data

import android.content.Context
import app.stride.model.AppData
import kotlinx.serialization.json.Json
import java.io.File

val StrideJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

class Store(context: Context) {
    private val file = File(context.filesDir, "stride.json")

    fun load(): AppData = try {
        if (file.exists()) StrideJson.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    } catch (e: Exception) {
        AppData()
    }

    fun save(data: AppData) {
        val tmp = File(file.parentFile, "stride.json.tmp")
        tmp.writeText(StrideJson.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
