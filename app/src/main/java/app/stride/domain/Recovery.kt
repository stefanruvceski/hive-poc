package app.stride.domain

import app.stride.model.Wellness
import java.time.LocalDate

/**
 * Sleep, HRV and resting HR from intervals.icu wellness (synced from Garmin).
 * The night's values are stored on the date you wake up.
 */
object Recovery {
    enum class Readiness { GOOD, NORMAL, LOW }

    data class Summary(
        val day: Wellness,
        val hrvBaseline: Double?,
        val restingBaseline: Double?,
        val readiness: Readiness,
        val reason: String,
    )

    fun of(list: List<Wellness>, date: LocalDate): Wellness? = list.firstOrNull { it.id == date.toString() }

    /** Average of [field] over the [days] days before [date] (not including it), or null with fewer than 3 values. */
    fun baseline(list: List<Wellness>, date: LocalDate, days: Int = 7, field: (Wellness) -> Double?): Double? {
        val from = date.minusDays(days.toLong()).toString()
        val to = date.toString()
        val values = list.filter { it.id >= from && it.id < to }.mapNotNull(field)
        return if (values.size < 3) null else values.average()
    }

    fun summary(list: List<Wellness>, date: LocalDate): Summary? {
        val day = of(list, date)?.takeIf { it.hasRecovery } ?: return null
        val hrvBase = baseline(list, date) { it.hrv }
        val rhrBase = baseline(list, date) { it.restingHR }
        val sleepH = day.sleepSecs?.let { it / 3600 }
        val hrvDrop = if (day.hrv != null && hrvBase != null) (hrvBase - day.hrv) / hrvBase else null
        val rhrRise = if (day.restingHR != null && rhrBase != null) day.restingHR - rhrBase else null

        val (readiness, reason) = when {
            hrvDrop != null && hrvDrop > 0.12 -> Readiness.LOW to "HRV ${pct(hrvDrop)} below your 7-day average. Keep today easy."
            sleepH != null && sleepH < 6 -> Readiness.LOW to "Short night (${fmtHours(day.sleepSecs)}). Keep today easy."
            rhrRise != null && rhrRise >= 5 -> Readiness.LOW to "Resting HR ${fmtNum(rhrRise)} bpm above normal. Keep today easy."
            (hrvDrop == null || hrvDrop <= 0.05) && (sleepH == null || sleepH >= 7) ->
                Readiness.GOOD to "Recovered. Good day for the planned session."
            else -> Readiness.NORMAL to "Normal recovery."
        }
        return Summary(day, hrvBase, rhrBase, readiness, reason)
    }

    /** Weekly average of [field] for the week starting [monday]. */
    fun weekAverage(list: List<Wellness>, monday: LocalDate, field: (Wellness) -> Double?): Double? {
        val from = monday.toString()
        val to = monday.plusDays(7).toString()
        val values = list.filter { it.id >= from && it.id < to }.mapNotNull(field)
        return if (values.isEmpty()) null else values.average()
    }

    private fun pct(f: Double) = "${(f * 100).toInt()}%"
}

/** 7h 32m */
fun fmtHours(sec: Double?): String {
    if (sec == null) return "–"
    val m = (sec / 60).toInt()
    return "${m / 60}h ${m % 60}m"
}
