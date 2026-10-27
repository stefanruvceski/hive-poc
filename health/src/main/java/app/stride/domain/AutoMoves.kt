package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.AutoMove
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Walks (and other moves) the watch noticed by itself (Garmin Move IQ), from Garmin's daily events (pure, tested).
 * They count as training time when no recorded workout covers them, so a walk never needs to be started on the watch.
 */
object AutoMoves {
    private val START = listOf("startTimestampLocal", "startTimeLocal", "startLocal", "startTimestampGMT", "startTimeGMT", "startTimestamp", "startTime", "start")
    private val END = listOf("endTimestampLocal", "endTimeLocal", "endTimestampGMT", "endTimeGMT", "endTimestamp", "endTime", "end")
    private val SECONDS = listOf("durationInSeconds", "durationSecs", "durationSeconds", "duration", "elapsedDuration", "elapsedTime")
    private val MILLIS = listOf("durationInMillis", "durationMillis", "durationMs")
    private val NOT_MOVES = listOf("sleep", "nap", "stress", "rest", "relax", "breath", "meditat")
    private val TYPE = listOf("activityType", "activityTypeKey", "typeKey", "activityName", "eventType", "type", "name")

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString || it.longOrNull != null }?.content

    /** A local time from "2026-10-27T17:05:00(.0)", "… 17:05:00", an ISO instant or epoch millis/seconds. */
    private fun time(o: JsonObject, keys: List<String>, zone: ZoneId): LocalDateTime? {
        for (k in keys) {
            val v = o[k] as? JsonPrimitive ?: continue
            val gmt = k.contains("GMT")
            v.longOrNull?.let { n ->
                val inst = if (n > 100_000_000_000) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
                return LocalDateTime.ofInstant(inst, zone)
            }
            val s = v.content.trim().replace(' ', 'T')
            runCatching { return LocalDateTime.ofInstant(Instant.parse(s), zone) }
            val local = runCatching { LocalDateTime.parse(s.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() ?: continue
            return if (gmt) local.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime() else local
        }
        return null
    }

    private fun seconds(o: JsonObject): Double? {
        MILLIS.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.doubleOrNull }?.let { return it / 1000 }
        val s = SECONDS.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.doubleOrNull } ?: return null
        return if (s > 86_400) s / 1000 else s
    }

    private fun type(o: JsonObject): String {
        for (k in TYPE) {
            when (val v = o[k]) {
                is JsonPrimitive -> if (v.isString && v.content.isNotBlank()) return v.content
                is JsonObject -> (str(v, "typeKey") ?: str(v, "key"))?.let { return it }
                else -> {}
            }
        }
        return "walking"
    }

    private fun objects(e: JsonElement): Sequence<JsonObject> = when (e) {
        is JsonObject -> sequenceOf(e) + e.values.asSequence().flatMap { objects(it) }
        is JsonArray -> e.asSequence().flatMap { objects(it) }
        else -> emptySequence()
    }

    /**
     * Every event in Garmin's answer with a start and a length (or an end), wherever it sits in the JSON: the field
     * names are matched loosely because Garmin's daily events aren't documented. Kept: 3 min to 6 h, on [date].
     */
    fun parse(json: JsonElement, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<AutoMove> =
        objects(json).mapNotNull { o ->
            val start = time(o, START, zone) ?: return@mapNotNull null
            val secs = seconds(o) ?: time(o, END, zone)?.let { ChronoUnit.SECONDS.between(start, it).toDouble() } ?: return@mapNotNull null
            val kind = type(o).lowercase()
            if (start.toLocalDate() != date || secs < 180 || secs > 6 * 3600 || NOT_MOVES.any { it in kind }) return@mapNotNull null
            AutoMove(date.toString(), start.truncatedTo(ChronoUnit.SECONDS).toString(), secs / 60, kind)
        }.distinctBy { it.start }.sortedBy { it.start }.toList()

    private fun span(a: Activity): Pair<LocalDateTime, LocalDateTime>? {
        val s = a.startLocal?.let { runCatching { LocalDateTime.parse(it.take(19)) }.getOrNull() } ?: return null
        return s to s.plusSeconds(((a.elapsedTime ?: a.movingTime) ?: 0.0).toLong())
    }

    /** Minutes of [m] not covered by a recorded workout (a started walk shows up in both; it counts once). */
    fun uncovered(m: AutoMove, acts: List<Activity>): Double {
        val s = runCatching { LocalDateTime.parse(m.start) }.getOrNull() ?: return 0.0
        val e = s.plusSeconds((m.minutes * 60).toLong())
        val overlap = acts.mapNotNull { span(it) }.sumOf { (a, b) ->
            val from = maxOf(a, s); val to = minOf(b, e)
            if (to.isAfter(from)) ChronoUnit.SECONDS.between(from, to).toDouble() else 0.0
        }
        return ((m.minutes * 60 - overlap) / 60).coerceAtLeast(0.0)
    }

    /** The day's auto-detected moves with the minutes they add to training time. */
    fun forDay(data: AppData, date: LocalDate): List<Pair<AutoMove, Double>> {
        val acts = data.activities.filter { it.date() == date }
        return data.autoMoves.filter { it.date == date.toString() }.map { it to uncovered(it, acts) }.filter { it.second >= 1 }
    }

    /** Replaces the fetched days' moves, keeping 120 days. */
    fun merge(old: List<AutoMove>, days: Map<LocalDate, List<AutoMove>>, today: LocalDate): List<AutoMove> {
        val since = today.minusDays(120).toString()
        val fetched = days.keys.map { it.toString() }.toSet()
        return (old.filter { it.date !in fetched } + days.values.flatten()).filter { it.date >= since }.sortedBy { it.start }
    }

    /** "Walking" for "walking", "Hiking" for "hiking_v2"… */
    fun label(type: String): String = type.substringBefore("_v").replace('_', ' ').replaceFirstChar { it.uppercase() }
}
