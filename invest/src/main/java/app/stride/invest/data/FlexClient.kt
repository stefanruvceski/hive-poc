package app.stride.invest.data

import android.content.Context
import app.stride.invest.domain.FlexParse
import app.stride.invest.domain.FlexStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Interactive Brokers' Flex Web Service (read-only): the user's token and Query ID ask IBKR to build the report
 * (SendRequest → reference code), then the report is fetched (GetStatement), asking again while IBKR is still building
 * it. Token and Query ID live in prefs `invest_secrets`, out of every backup.
 */
object FlexClient {
    private const val BASE = "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/"
    private const val PREFS = "invest_secrets"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    class FlexException(message: String) : IOException(message)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String? = prefs(context).getString("token", null)?.takeIf { it.isNotBlank() }
    fun queryId(context: Context): String? = prefs(context).getString("query", null)?.takeIf { it.isNotBlank() }
    fun connected(context: Context) = token(context) != null && queryId(context) != null

    fun save(context: Context, token: String?, queryId: String?) {
        prefs(context).edit().putString("token", token?.trim()?.ifEmpty { null }).putString("query", queryId?.trim()?.ifEmpty { null }).apply()
    }

    private fun get(path: String, params: Map<String, String>): String {
        val url = (BASE + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        // IBKR turns away requests without a User-Agent.
        val req = Request.Builder().url(url).header("User-Agent", "Hive/1.0 (Android; personal portfolio)").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw FlexException("Interactive Brokers answered ${r.code}.")
            r.body?.string().orEmpty()
        }
    }

    /** The latest report for the saved query. Takes from a few seconds to about a minute. */
    suspend fun fetch(context: Context): FlexStatement = withContext(Dispatchers.IO) {
        val token = token(context) ?: throw FlexException("Add your Flex token first.")
        val query = queryId(context) ?: throw FlexException("Add your Flex Query ID first.")
        val sent = FlexParse.reply(get("SendRequest", mapOf("t" to token, "q" to query, "v" to "3")))
        val ref = sent.reference?.takeIf { sent.ok } ?: throw FlexException(sent.error ?: "Interactive Brokers didn't accept the request (${sent.errorCode ?: "no code"}).")
        var wait = 3_000L
        repeat(8) {
            delay(wait)
            val body = get("GetStatement", mapOf("t" to token, "q" to ref, "v" to "3"))
            if (FlexParse.isStatement(body)) return@withContext FlexParse.statement(body)
            val r = FlexParse.reply(body)
            if (!r.retry) throw FlexException(r.error ?: "Interactive Brokers sent no report (${r.errorCode ?: "no code"}).")
            wait = (wait * 3 / 2).coerceAtMost(15_000L)
        }
        throw FlexException("Interactive Brokers is still preparing the report. Try again in a minute.")
    }
}
