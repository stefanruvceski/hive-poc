package app.stride.wallet.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Asks the tax office (suf.purs.gov.rs, public, no key) for a receipt by its QR link. Parsing is in FiscalParse. */
object FiscalClient {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    sealed interface Answer {
        data class Body(val text: String) : Answer
        data class Http(val code: Int) : Answer
        data object Offline : Answer
    }

    /** Blocking: call off the main thread. JSON when the server gives it, else the HTML page. */
    fun fetch(url: String): Answer = runCatching {
        val req = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Hive/1.0 (Android; personal receipts)").build()
        http.newCall(req).execute().use { if (it.isSuccessful) Answer.Body(it.body?.string().orEmpty()) else Answer.Http(it.code) }
    }.getOrElse { Answer.Offline }
}
