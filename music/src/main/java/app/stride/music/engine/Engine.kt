package app.stride.music.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.stride.music.domain.Args
import app.stride.music.model.DlOptions
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Info(val title: String? = null, val thumbnail: String? = null, val uploader: String? = null, val channel: String? = null)

data class Prog(val pct: Float, val eta: Long, val line: String)

/** Live progress per download (kept out of the store so it isn't written a hundred times a second). */
object ProgressBus {
    val map = MutableStateFlow<Map<Long, Prog>>(emptyMap())
    fun set(id: Long, p: Prog) = map.update { it + (id to p) }
    fun remove(id: Long) = map.update { it - id }
}

/** yt-dlp, ffmpeg and aria2c (youtubedl-android): unpacked once, yt-dlp updated once a day. */
object Engine {
    private val json = Json { ignoreUnknownKeys = true }
    private val AUDIO_EXT = setOf("mp3", "m4a", "opus", "ogg", "aac", "flac", "wav")
    private val OUT_EXT = AUDIO_EXT + setOf("mp4", "mkv", "webm", "mov")
    private const val PREFS = "music_engine"

    /** Completes when the binaries are unpacked; the download service waits for it before the first job. */
    val ready = CompletableDeferred<Result<Unit>>()
    @Volatile private var started = false

    /** Blocking: call off the main thread (Music's onAppStart does). */
    fun init(context: Context, autoUpdate: Boolean) {
        if (started) return
        started = true
        try {
            YoutubeDL.init(context)
            FFmpeg.init(context)
            Aria2c.init(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val day = 24L * 60 * 60 * 1000
            if (autoUpdate && System.currentTimeMillis() - prefs.getLong("last_update", 0) > day) {
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(appContext = context, updateChannel = YoutubeDL.UpdateChannel.STABLE)
                    prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
                }
            }
            ready.complete(Result.success(Unit))
        } catch (t: Throwable) {
            ready.complete(Result.failure(t))
        }
    }

    /** Updates yt-dlp now; returns the new version (or the error). Blocking. */
    fun updateNow(context: Context): String = runCatching {
        val status = YoutubeDL.getInstance().updateYoutubeDL(appContext = context, updateChannel = YoutubeDL.UpdateChannel.STABLE)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_update", System.currentTimeMillis()).apply()
        "yt-dlp: ${status?.name?.lowercase()?.replace('_', ' ') ?: "done"} (${version(context) ?: "?"})"
    }.getOrElse { "Update failed: ${it.message}" }

    fun version(context: Context): String? = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    fun fetchInfo(url: String, o: DlOptions, processId: String): Info? = runCatching {
        val r = YoutubeDLRequest(url)
        r.addOption("--dump-single-json")
        r.addOption("-R", "1")
        r.addOption("--socket-timeout", "10")
        if (o.playlist) r.addOption("--flat-playlist") else r.addOption("--no-playlist")
        val resp = YoutubeDL.getInstance().execute(request = r, processId = processId, callback = null)
        json.decodeFromString(Info.serializer(), resp.out)
    }.getOrNull()

    fun download(url: String, o: DlOptions, tmp: File, processId: String, cb: (Float, Long, String) -> Unit) {
        val r = YoutubeDLRequest(url)
        Args.buildArgs(o, tmp.absolutePath).forEach { (k, v) -> if (v == null) r.addOption(k) else r.addOption(k, v) }
        YoutubeDL.getInstance().execute(request = r, processId = processId, callback = cb)
    }

    /** The files yt-dlp made (no thumbnails, .part files…); chapter files when it split them. */
    fun outputs(tmp: File): List<File> {
        val all = tmp.walkTopDown().filter { it.isFile && it.extension.lowercase() in OUT_EXT }.sortedBy { it.name }.toList()
        val chapters = all.filter { Args.CHAPTER_MARK in it.name }
        return chapters.ifEmpty { all }
    }

    /** Copies [f] into Music/Hive Music (or Movies, or Download) via MediaStore, so Files and other players see it. */
    fun save(ctx: Context, f: File, toDownloads: Boolean): Uri {
        val ext = f.extension.lowercase()
        val isAudio = ext in AUDIO_EXT
        var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        if (isAudio && !mime.startsWith("audio/")) mime = "audio/mpeg"
        val (collection, rel) = when {
            toDownloads -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/Hive Music"
            isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/Hive Music"
            else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/Hive Music"
        }
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val cr = ctx.contentResolver
        val uri = cr.insert(collection, cv) ?: error("MediaStore refused the file")
        try {
            cr.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            cv.clear()
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            cr.update(uri, cv, null, null)
        } catch (t: Throwable) {
            cr.delete(uri, null, null)
            throw t
        }
        return uri
    }
}
