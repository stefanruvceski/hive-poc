package app.stride.music.model

import kotlinx.serialization.Serializable

/** Everything that can be set for one download (Tempo's options). */
@Serializable
data class DlOptions(
    val audioOnly: Boolean = true,
    /** mp3 | m4a | opus */
    val audioFormat: String = "mp3",
    val audioKbps: Int = 128,
    val mono: Boolean = false,
    /** 0 = best */
    val videoHeight: Int = 1080,
    /** 0.5 .. 3.0, audio only; the pitch stays the same. */
    val speed: Float = 1.0f,
    /** Even out loudness (loudnorm). */
    val normalize: Boolean = false,
    /** Cut long silences. */
    val trimSilence: Boolean = false,
    val embedMeta: Boolean = true,
    val embedThumb: Boolean = true,
    val playlist: Boolean = false,
    val splitChapters: Boolean = false,
    val useAria2: Boolean = false,
    val extraArgs: String = "",
) {
    /** Speed, loudness, silence and mono need re-encoding, which only works reliably to MP3. */
    val needsReencode: Boolean get() = audioOnly && (speed != 1.0f || normalize || trimSilence || mono)

    val effectiveFormat: String get() = if (needsReencode) "mp3" else audioFormat
}

@Serializable
data class Preset(val id: String, val name: String, val options: DlOptions, val builtin: Boolean = false)

/** Never rename: stored by name. */
@Serializable
enum class DlStatus { QUEUED, FETCHING, RUNNING, DONE, FAILED, CANCELED;
    val active: Boolean get() = this == QUEUED || this == FETCHING || this == RUNNING
}

@Serializable
data class DownloadItem(
    val id: Long,
    val url: String,
    val title: String = "",
    val thumbnail: String? = null,
    val uploader: String? = null,
    val status: DlStatus = DlStatus.QUEUED,
    val message: String? = null,
    /** content:// URIs of the saved files. */
    val outputs: List<String> = emptyList(),
    val options: DlOptions = DlOptions(),
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MusicSettings(
    /** Downloads at the same time (1–4). */
    val concurrency: Int = 2,
    /** Save into Download/Hive Music instead of Music/Hive Music (and Movies for video). */
    val saveToDownloads: Boolean = false,
    /** Share → Hive Music opens the app to choose options instead of queueing right away. */
    val askOnShare: Boolean = false,
    /** Update yt-dlp once a day (sites change, old versions break). */
    val autoUpdate: Boolean = true,
)

@Serializable
data class MusicData(
    val items: List<DownloadItem> = emptyList(),
    val presets: List<Preset> = DEFAULT_PRESETS,
    val activePresetId: String = "swim",
    val settings: MusicSettings = MusicSettings(),
)

val DEFAULT_PRESETS = listOf(
    Preset("swim", "Faster 1.5×", DlOptions(speed = 1.5f, audioKbps = 128), builtin = true),
    Preset("podcast", "Podcast 1.75×", DlOptions(speed = 1.75f, audioKbps = 96, trimSilence = true), builtin = true),
    Preset("music", "Music", DlOptions(audioFormat = "m4a", audioKbps = 256), builtin = true),
    Preset("video", "Video 1080p", DlOptions(audioOnly = false, videoHeight = 1080), builtin = true),
)
