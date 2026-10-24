package app.stride.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import app.stride.domain.Analyzer
import app.stride.domain.DayResult
import app.stride.domain.Records
import app.stride.domain.dayLong
import app.stride.domain.dateShort
import app.stride.domain.fmtDur
import app.stride.domain.fmtKg
import app.stride.domain.fmtNum
import app.stride.domain.mondayOf
import app.stride.domain.pace100
import app.stride.domain.paceKm
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Interval
import app.stride.model.ShareMap
import app.stride.model.ShareStyle
import app.stride.model.StrengthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Builds the share image for a day and hands it to Android's share sheet. */
object ShareContent {

    /** Whether the day has anything worth sharing. */
    fun canShare(d: DayResult): Boolean = d.strength?.logged == true || d.matched.isNotEmpty() || d.extra.isNotEmpty()

    private fun footer(data: AppData, date: LocalDate): String {
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        val s = Analyzer.week(mondayOf(date), date, data).summary
        return "Week $week · ${s.done}/${s.planned} done"
    }

    private fun partOfDay(a: Activity): String {
        val h = a.clock().take(2).toIntOrNull() ?: return ""
        return when (h) {
            in 4..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    }

    private fun typeName(a: Activity): String = when {
        DayKind.RUN.matches(a.type) -> "run"
        DayKind.REST_WALK.matches(a.type) -> if (a.type == "Hike") "hike" else "walk"
        a.type == "Ride" || a.type == "VirtualRide" -> "ride"
        else -> (a.type ?: "workout").lowercase()
    }

    /** Which options in [ShareStyle] change the card for [d] (the preview only offers those). */
    enum class Kind { ROUTE, STRENGTH, SWIM }

    fun kind(d: DayResult): Kind? {
        val act = d.matched.firstOrNull() ?: d.extra.firstOrNull()
        val strength = d.strength
        if (strength != null && strength.logged && (d.plan?.kind?.isStrength == true || act == null)) return Kind.STRENGTH
        act ?: return null
        return if (DayKind.SWIM_EASY.matches(act.type)) Kind.SWIM else Kind.ROUTE
    }

    /**
     * Renders the card for [d] in [style]; [route] fetches a GPS track for an activity id (may return null).
     * Tracks are cached per activity so switching styles in the preview doesn't refetch.
     */
    suspend fun render(
        context: Context,
        data: AppData,
        d: DayResult,
        intervals: Map<String, List<Interval>>,
        style: ShareStyle,
        route: suspend (String) -> List<Pair<Double, Double>>?,
    ): Bitmap? {
        val footer = footer(data, d.date)
        val strength = d.strength
        val act = d.matched.firstOrNull() ?: d.extra.firstOrNull()
        val subtitleDate = "${dayLong(d.date)}, ${dateShort(d.date)}"

        if (strength != null && strength.logged && (d.plan?.kind?.isStrength == true || act == null)) {
            return withContext(Dispatchers.Default) { strengthCard(context, ShareCard.look(style, ShareCard.STRENGTH_AUTO), data, d, strength, d.matched.firstOrNull(), subtitleDate, footer) }
        }
        act ?: return null

        if (DayKind.SWIM_EASY.matches(act.type)) {
            val reps = Analyzer.mainSet(Analyzer.workReps(intervals[act.id]))
            return withContext(Dispatchers.Default) {
                ShareCard.swim(
                    context,
                    ShareCard.look(style, ShareCard.SWIM_AUTO),
                    kicker = d.plan?.kind?.takeIf { it == DayKind.SWIM_EASY || it == DayKind.SWIM_INTENSE }?.label ?: "Swim",
                    subtitle = subtitleDate + (act.clock().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                    distance = String.format(Locale.US, "%,d", (act.distance ?: 0.0).toInt()),
                    stats = listOf(
                        ShareCard.Stat("Time", fmtDur(act.movingTime)),
                        ShareCard.Stat("Pace", act.pace100()?.let { fmtDur(it) } ?: "–", "/100m"),
                        if (reps.size >= 2) ShareCard.Stat("Max HR", act.maxHr?.let { fmtNum(it) } ?: "–", color = ShareCard.RED)
                        else ShareCard.Stat("Avg HR", act.avgHr?.let { fmtNum(it) } ?: "–", color = ShareCard.RED),
                    ),
                    reps = reps.map { r ->
                        val sec = r.movingTime!! / (r.distance!! / 100.0)
                        ShareCard.Rep(fmtDur(sec), sec)
                    },
                    repTitle = "${reps.size} × ${reps.map { it.distance!! }.average().toInt()} m · pace per 100 m",
                    zones = act.hrZoneTimes?.takeIf { it.sum() > 0 },
                    footer = footer,
                )
            }
        }

        val track = tracks[act.id] ?: route(act.id)?.also { tracks[act.id] = it }
        // Map behind the route; null offline or when switched off, then the plain background is used.
        val mapView = track?.takeIf { style.map != ShareMap.NONE && it.size >= 2 }?.let { MapTiles.fit(it, ShareCard.ROUTE_BOX) }
        val map = mapView?.let { runCatching { MapTiles.render(context, it, ShareCard.W, ShareCard.H, style.map) }.getOrNull() }
        val km = (act.distance ?: 0.0) / 1000
        val run = DayKind.RUN.matches(act.type)
        return withContext(Dispatchers.Default) {
            ShareCard.run(
                context,
                ShareCard.look(style, ShareCard.RUN_AUTO),
                kicker = listOf(dayLong(d.date), partOfDay(act), typeName(act)).filter { it.isNotEmpty() }.joinToString(" "),
                subtitle = dateShort(d.date) + (act.clock().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                distance = if (km > 0) String.format(Locale.US, "%.2f", km) else fmtDur(act.movingTime),
                unit = if (km > 0) "KM" else "",
                stats = listOfNotNull(
                    ShareCard.Stat("Time", fmtDur(act.movingTime)),
                    if (run) ShareCard.Stat("Pace", act.paceKm()?.let { fmtDur(it) } ?: "–", "/km")
                    else act.elevationGain?.takeIf { it >= 1 }?.let { ShareCard.Stat("Climb", fmtNum(it), "m") }
                        ?: ShareCard.Stat("Energy", act.calories?.let { fmtNum(it) } ?: "–", "kcal"),
                    ShareCard.Stat("Avg HR", act.avgHr?.let { fmtNum(it) } ?: "–", color = ShareCard.RED),
                ),
                zones = act.hrZoneTimes?.takeIf { it.sum() > 0 },
                route = track,
                footer = footer,
                map = map,
                mapView = mapView,
            )
        }
    }

    private val tracks = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Double, Double>>>()

    private fun strengthCard(context: Context, look: ShareCard.Look, data: AppData, d: DayResult, s: StrengthSession, act: Activity?, subtitleDate: String, footer: String): Bitmap {
        val earlier = data.strength.filter { it.id != s.id && it.date < s.date }
        val done = s.exercises.filter { it.done || s.finished }
        val records = done.mapNotNull { e -> Records.record(e, Records.history(earlier, e.name))?.let { e.name to it } }
        val best = records.firstOrNull { it.second.kind != Records.Kind.VOLUME } ?: records.firstOrNull()
        val maxVolume = done.maxOfOrNull { it.volume }?.takeIf { it > 0 } ?: 1.0
        val rows = done.map { e ->
            val work = e.workingSets
            val reps = work.map { it.reps }.distinct()
            ShareCard.Row(
                name = e.name,
                sets = "${work.size} × ${if (reps.size == 1) reps[0] else "${reps.min()}–${reps.max()}"}",
                kg = if (e.weightKg > 0) "${fmtKg(e.weightKg)} kg" else "BW",
                fraction = (e.volume / maxVolume).toFloat().takeIf { e.volume > 0 } ?: 0.5f,
                record = records.any { it.first == e.name },
            )
        }
        val time = act?.movingTime?.let { fmtDur(it) }
        return ShareCard.strength(
            context,
            look,
            kicker = (d.plan?.title?.substringBefore(" +") ?: s.kind.label) + " · kettlebell",
            subtitle = subtitleDate + (time?.let { " · $it" } ?: ""),
            lifted = String.format(Locale.US, "%,d", s.volume.toInt()),
            record = best?.let { (name, r) -> Records.describe(name, r) },
            rows = rows,
            stats = listOf(
                ShareCard.Stat("Sets", "${done.sumOf { it.workingSets.size }}"),
                act?.avgHr?.let { ShareCard.Stat("Avg HR", fmtNum(it), color = ShareCard.RED) } ?: ShareCard.Stat("Exercises", "${done.size}"),
                if (s.sauna) ShareCard.Stat("Sauna", "${s.saunaMinutes}", "min", ShareCard.PINK) else ShareCard.Stat("Time", time ?: "–"),
            ),
            footer = footer,
        )
    }

    /** Saves several images (Year in review) and opens the share sheet with all of them. */
    suspend fun shareMany(context: Context, bitmaps: List<Bitmap>, name: String) {
        val uris: ArrayList<Uri> = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            ArrayList(bitmaps.mapIndexed { i, b ->
                val file = File(dir, "$name-${i + 1}.png")
                file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            })
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/png")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share year in review"))
    }

    /** Saves the image to the cache and opens the share sheet. */
    suspend fun share(context: Context, bitmap: Bitmap, date: LocalDate) {
        val uri: Uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "stride-$date.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share workout"))
    }
}
