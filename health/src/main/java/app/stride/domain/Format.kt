package app.stride.domain

import app.stride.format.Dates
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAYS_LONG = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun dayShort(d: LocalDate) = DAYS_SHORT[d.dayOfWeek.value - 1]
fun dayLong(d: LocalDate) = DAYS_LONG[d.dayOfWeek.value - 1]
fun dowShort(dow: Int) = DAYS_SHORT[(dow - 1).coerceIn(0, 6)]
/** "31 Oct" (or "Oct 31" with US dates): follows the Hive date format setting. */
fun dateShort(d: LocalDate) = Dates.text(d)
/** "31.10." / "10-31" / "10/31". */
fun dateNum(d: LocalDate) = Dates.short(d)

fun weekRange(monday: LocalDate): String = Dates.range(monday, monday.plusDays(6))

/** 42:10 or 1:02:10 */
fun fmtDur(sec: Double?): String {
    if (sec == null || sec.isNaN()) return "–"
    val s = sec.roundToInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss) else String.format(Locale.US, "%d:%02d", m, ss)
}

fun fmtMin(sec: Double?): String = if (sec == null) "–" else "${(sec / 60).roundToInt()} min"

fun fmtKm(meters: Double?): String =
    if (meters == null) "–" else String.format(Locale.US, "%.2f km", meters / 1000)

fun fmtMeters(meters: Double?): String =
    if (meters == null) "–" else if (meters >= 1000) fmtKm(meters) else "${meters.roundToInt()} m"

fun fmtHr(hr: Double?): String = if (hr == null) "–" else "${hr.roundToInt()}"

fun fmtKg(kg: Double): String {
    val r = (kg * 10).roundToInt() / 10.0
    return if (r == r.toLong().toDouble()) "${r.toLong()}" else String.format(Locale.US, "%.1f", r)
}

fun fmtSigned(v: Double, unit: String = ""): String {
    val sign = if (v > 0) "+" else if (v < 0) "−" else "±"
    return "$sign${fmtKg(abs(v))}$unit"
}

fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun fmtNum(v: Double, decimals: Int = 0): String =
    if (decimals == 0) "${v.roundToInt()}" else String.format(Locale.US, "%.${decimals}f", v)

/** m/s → km/h */
fun fmtKmh(mps: Double): String = String.format(Locale.US, "%.1f", mps * 3.6)

/** Splits a duration into value + unit for tiles: "42:10" + "min" or "1:02:10" + "h". */
fun durParts(sec: Double): Pair<String, String> = fmtDur(sec) to if (sec >= 3600) "h:mm:ss" else "min:sec"

/** Distance for tiles: meters below 1 km for swims, km otherwise. */
fun distParts(meters: Double, swim: Boolean): Pair<String, String> =
    if (swim || meters < 1000) "${meters.roundToInt()}" to "m"
    else String.format(Locale.US, "%.2f", meters / 1000) to "km"

/** intervals.icu "feel": 1 strong … 5 weak. */
fun feelLabel(feel: Double): String = when (feel.roundToInt()) {
    1 -> "Strong"
    2 -> "Good"
    3 -> "Normal"
    4 -> "Poor"
    5 -> "Weak"
    else -> "–"
}
