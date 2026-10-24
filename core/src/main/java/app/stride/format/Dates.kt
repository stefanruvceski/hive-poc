package app.stride.format

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** How dates are written across Hive. Stored by name; never rename. */
enum class DateStyle(val label: String, val example: String) {
    EU("Europe", "31.10.2026"),
    ISO("ISO", "2026-10-31"),
    US("US", "10/31/2026"),
}

/**
 * One date format for every module (Hive-wide setting, EU by default). The style is Compose state,
 * so screens redraw when it changes; formatting is pure and unit tested.
 */
object Dates {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private const val PREFS = "hive_prefs"
    private const val KEY = "date_style"

    var style by mutableStateOf(DateStyle.EU)
        private set

    /** Reads the saved style; called once when the app starts. */
    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        style = DateStyle.entries.firstOrNull { it.name == saved } ?: DateStyle.EU
    }

    fun set(context: Context, s: DateStyle) {
        style = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, s.name).apply()
    }

    /** 31.10.2026 · 2026-10-31 · 10/31/2026 */
    fun full(d: LocalDate, s: DateStyle = style): String = when (s) {
        DateStyle.EU -> "%d.%d.%d".format(d.dayOfMonth, d.monthValue, d.year)
        DateStyle.ISO -> d.toString()
        DateStyle.US -> "%d/%d/%d".format(d.monthValue, d.dayOfMonth, d.year)
    }

    /** An ISO date string ("2026-10-31") in the current style; anything unparsable comes back as is. */
    fun full(iso: String, s: DateStyle = style): String = runCatching { full(LocalDate.parse(iso.take(10)), s) }.getOrDefault(iso)

    /** Day and month in numbers: 31.10. · 10-31 · 10/31 */
    fun short(d: LocalDate, s: DateStyle = style): String = when (s) {
        DateStyle.EU -> "${d.dayOfMonth}.${d.monthValue}."
        DateStyle.ISO -> "%02d-%02d".format(d.monthValue, d.dayOfMonth)
        DateStyle.US -> "${d.monthValue}/${d.dayOfMonth}"
    }

    /** Day and month with the month's name: 31 Oct · Oct 31 (US). */
    fun text(d: LocalDate, s: DateStyle = style): String =
        if (s == DateStyle.US) "${MONTHS[d.monthValue - 1]} ${d.dayOfMonth}" else "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"

    /** A Monday-to-Sunday range: 19–25 Oct · Oct 19–25 · 27 Oct – 2 Nov. */
    fun range(from: LocalDate, to: LocalDate, s: DateStyle = style): String = when {
        from.month != to.month -> "${text(from, s)} – ${text(to, s)}"
        s == DateStyle.US -> "${MONTHS[from.monthValue - 1]} ${from.dayOfMonth}–${to.dayOfMonth}"
        else -> "${from.dayOfMonth}–${to.dayOfMonth} ${MONTHS[from.monthValue - 1]}"
    }
}
