package app.stride.money.data

import app.stride.money.domain.FxParse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Live exchange rates, keyless (Streakli's providers): the free currency-api (any base) or the
 * National Bank of Serbia's middle rates via kurs.resenje.org (RSD-based, cross-computed for other bases).
 * Parsing is in [FxParse] (pure, unit tested); this does the network calls.
 */
object FxClient {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** Base-per-code rates for [codes] (empty on failure; callers keep the old rates). Blocking: call off the main thread. */
    fun fetch(base: String, codes: List<String>, nbs: Boolean, date: String? = null): Map<String, Double> {
        val wanted = codes.filter { it.isNotBlank() && it != base }.distinct()
        if (wanted.isEmpty()) return emptyMap()
        if (nbs) return get(FxParse.nbsUrl(date))?.let { FxParse.nbs(base, wanted, it) }.orEmpty()
        for (url in FxParse.generalUrls(base, date)) {
            val r = get(url)?.let { FxParse.general(base, wanted, it) }.orEmpty()
            if (r.isNotEmpty()) return r
        }
        return emptyMap()
    }

    /** The rate on [date] (past days only; today and later use the latest), falling back to the latest rate. */
    fun rateOn(base: String, code: String, date: String, nbs: Boolean): Double? {
        if (code == base) return 1.0
        val past = date < LocalDate.now().toString()
        return (if (past) fetch(base, listOf(code), nbs, date)[code] else null) ?: fetch(base, listOf(code), nbs)[code]
    }
}
