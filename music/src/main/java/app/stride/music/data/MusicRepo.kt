package app.stride.music.data

import android.content.Context
import app.stride.data.JsonStore
import app.stride.music.model.DlOptions
import app.stride.music.model.DlStatus
import app.stride.music.model.DownloadItem
import app.stride.music.model.MusicData
import app.stride.music.model.MusicSettings
import app.stride.music.model.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Music's data in music.json (the shared [JsonStore]). */
class MusicStore(context: Context) : JsonStore<MusicData>(context, "music.json", MusicData.serializer(), { MusicData() }, LOCK) {
    companion object {
        val LOCK = Any()
    }
}

/**
 * The download list, presets and settings for the UI and the download service (one process): every change
 * goes through the store under its lock and is published on [data]. Replaces Tempo's Room database.
 */
object MusicRepo {
    private var store: MusicStore? = null
    private val _data = MutableStateFlow(MusicData())
    val data: StateFlow<MusicData> = _data.asStateFlow()

    fun init(context: Context) {
        if (store != null) return
        val s = MusicStore(context.applicationContext)
        store = s
        _data.value = s.load()
    }

    private fun update(f: (MusicData) -> MusicData): MusicData {
        val s = store ?: error("MusicRepo.init first")
        return s.update(f).also { _data.value = it }
    }

    private fun item(id: Long, f: (DownloadItem) -> DownloadItem) = update { d -> d.copy(items = d.items.map { if (it.id == id) f(it) else it }) }

    fun get(id: Long): DownloadItem? = _data.value.items.firstOrNull { it.id == id }
    fun queued(): List<DownloadItem> = _data.value.items.filter { it.status == DlStatus.QUEUED }.sortedBy { it.id }

    fun insert(url: String, o: DlOptions, summary: String): Long {
        var id = 0L
        update { d ->
            id = (d.items.maxOfOrNull { it.id } ?: 0L) + 1
            d.copy(items = listOf(DownloadItem(id, url, title = url, options = o, summary = summary)) + d.items)
        }
        return id
    }

    /** Keeps a canceled item canceled (the worker may still report in after a cancel). */
    fun setStatus(id: Long, s: DlStatus, message: String?) = item(id) { if (it.status == DlStatus.CANCELED && s != DlStatus.QUEUED) it else it.copy(status = s, message = message) }
    fun setInfo(id: Long, title: String?, thumbnail: String?, uploader: String?) =
        item(id) { it.copy(title = title?.ifBlank { null } ?: it.title, thumbnail = thumbnail ?: it.thumbnail, uploader = uploader ?: it.uploader) }
    fun setDone(id: Long, outputs: List<String>, message: String?) =
        item(id) { if (it.status == DlStatus.CANCELED) it else it.copy(status = DlStatus.DONE, outputs = outputs, message = message) }
    fun cancel(id: Long) = item(id) { if (it.status.active) it.copy(status = DlStatus.CANCELED) else it }
    fun requeue(id: Long) = item(id) { it.copy(status = DlStatus.QUEUED, message = null, outputs = emptyList()) }
    fun resetStuck() = update { d -> d.copy(items = d.items.map { if (it.status == DlStatus.FETCHING || it.status == DlStatus.RUNNING) it.copy(status = DlStatus.QUEUED) else it }) }
    fun delete(id: Long) = update { d -> d.copy(items = d.items.filter { it.id != id }) }
    fun clearFinished() = update { d -> d.copy(items = d.items.filter { it.status.active }) }

    fun activePreset(d: MusicData = _data.value): Preset = d.presets.firstOrNull { it.id == d.activePresetId } ?: d.presets.first()
    fun setActive(id: String) = update { it.copy(activePresetId = id) }
    fun addPreset(name: String, o: DlOptions) = update { d ->
        val id = "p" + System.currentTimeMillis()
        d.copy(presets = d.presets + Preset(id, name.trim().ifEmpty { "Preset" }, o), activePresetId = id)
    }
    fun deletePreset(id: String) = update { d ->
        val list = d.presets.filterNot { it.id == id && !it.builtin }
        d.copy(presets = list, activePresetId = if (list.any { it.id == d.activePresetId }) d.activePresetId else list.first().id)
    }
    fun setSettings(s: MusicSettings) = update { it.copy(settings = s.copy(concurrency = s.concurrency.coerceIn(1, 4))) }
}
