package app.stride.music.domain

import app.stride.music.model.DlOptions
import java.util.Locale

/** yt-dlp arguments and small helpers (pure, unit tested). The engine turns [buildArgs] into a request. */
object Args {
    /** Marks chapter files so the engine can tell them from the whole file. */
    const val CHAPTER_MARK = "‹"

    fun fmtSpeed(s: Float): String = String.format(Locale.US, "%.2f", s).trimEnd('0').trimEnd('.')

    fun summary(o: DlOptions): String =
        if (o.audioOnly) buildString {
            append(o.effectiveFormat.uppercase())
            if (o.effectiveFormat == "mp3" || o.effectiveFormat == "m4a") append(" ${o.audioKbps}k")
            if (o.speed != 1.0f) append(" · ${fmtSpeed(o.speed)}×")
        } else "MP4 " + if (o.videoHeight > 0) "${o.videoHeight}p" else "max"

    /** ffmpeg's atempo takes 0.5–2.0 per filter, so faster or slower speeds chain several. */
    fun atempoChain(speed: Float): String {
        var rest = speed.toDouble()
        val parts = mutableListOf<String>()
        while (rest > 2.0) { parts += "atempo=2.0"; rest /= 2.0 }
        while (rest < 0.5) { parts += "atempo=0.5"; rest /= 0.5 }
        parts += "atempo=" + String.format(Locale.US, "%.4f", rest)
        return parts.joinToString(",")
    }

    /** Splits a string into arguments, keeping quoted parts together. */
    fun splitArgs(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (c in s) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** The first link in shared text ("Check this out https://youtu.be/…"). */
    fun extractUrl(text: String): String? = Regex("https?://\\S+").find(text)?.value?.trimEnd(')', ',', '.')

    /** yt-dlp options for downloading [o] into [dir]: name to value (null = a flag). */
    fun buildArgs(o: DlOptions, dir: String): List<Pair<String, String?>> {
        val a = mutableListOf<Pair<String, String?>>()
        fun add(k: String, v: String? = null) { a += k to v }
        add("-P", dir)
        add("--no-mtime")
        if (o.playlist) { add("--yes-playlist"); add("--ignore-errors") } else add("--no-playlist")
        val base = if (o.playlist) "%(playlist_index)02d - %(title).120s" else "%(title).120s"
        add("-o", "$base.%(ext)s")
        if (o.splitChapters) {
            add("--split-chapters")
            add("-o", "chapter:%(title).60s $CHAPTER_MARK%(section_number)02d$CHAPTER_MARK %(section_title).80s.%(ext)s")
        }
        if (o.audioOnly) {
            val fmt = o.effectiveFormat
            add("-f", "bestaudio/best")
            add("-x")
            add("--audio-format", fmt)
            if ((fmt == "mp3" || fmt == "m4a") && o.audioKbps > 0) add("--audio-quality", "${o.audioKbps}K")
            // Speed, loudness and silence filters run in ffmpeg's audio extraction step.
            val filters = mutableListOf<String>()
            if (o.trimSilence) filters += "silenceremove=stop_periods=-1:stop_duration=1:stop_threshold=-45dB"
            if (o.normalize) filters += "loudnorm=I=-16:TP=-1.5:LRA=11"
            if (o.speed != 1.0f) filters += atempoChain(o.speed)
            val ppa = mutableListOf<String>()
            if (filters.isNotEmpty()) ppa += listOf("-filter:a", filters.joinToString(","))
            if (o.mono) ppa += listOf("-ac", "1")
            if (ppa.isNotEmpty()) add("--postprocessor-args", "ExtractAudio:" + ppa.joinToString(" "))
            if (o.embedMeta) add("--embed-metadata")
            if (o.embedThumb && (fmt == "mp3" || fmt == "m4a")) { add("--embed-thumbnail"); add("--convert-thumbnails", "jpg") }
        } else {
            add("-f", "bv*+ba/b")
            add("-S", (if (o.videoHeight > 0) "res:${o.videoHeight}," else "") + "vcodec:h264,acodec:aac")
            add("--merge-output-format", "mp4")
            if (o.embedMeta) add("--embed-metadata")
            if (o.embedThumb) { add("--embed-thumbnail"); add("--convert-thumbnails", "jpg") }
        }
        if (o.useAria2) { add("--downloader", "libaria2c.so"); add("--downloader-args", "aria2c:-x 8 -k 1M") }
        splitArgs(o.extraArgs).forEach { add(it) }
        return a
    }

    /** "Song ‹03‹ Chorus" → "Song · Chorus"-style display name for chapter files. */
    fun displayName(file: String): String = file.substringBeforeLast('.').replace(Regex("\\s*$CHAPTER_MARK(\\d+)$CHAPTER_MARK\\s*"), " · ")

    /** Status line for the list. */
    fun progressText(pct: Float, etaSec: Long): String = when {
        pct < 0 -> "Downloading…"
        pct >= 99.9f -> "Processing…"
        else -> String.format(Locale.US, "%.0f%%", pct) + if (etaSec > 0) "  ·  ${etaSec / 60}:${"%02d".format(etaSec % 60)} left" else ""
    }
}
