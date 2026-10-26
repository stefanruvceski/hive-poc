package app.stride.garmin

import android.content.Context
import android.util.Base64
import app.stride.data.StrideJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unofficial Garmin Connect access, the same way the Garmin Connect app and the open-source
 * "garth" library log in: SSO sign-in → OAuth1 token (valid about a year) → short-lived OAuth2
 * token for the API. Only the tokens are kept (private app storage); the password never is.
 * Garmin can change this flow at any time; errors say so plainly.
 */
class GarminClient(private val context: Context) {
    class GarminException(message: String) : IOException(message)

    /** Sign-in waiting for the code from the authenticator app or e-mail. */
    class PendingMfa internal constructor(internal val csrf: String)

    private val prefs = context.getSharedPreferences("garmin", Context.MODE_PRIVATE)

    private val cookies = HashMap<String, MutableList<Cookie>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = this@GarminClient.cookies.getOrPut(url.host) { mutableListOf() }
                cookies.forEach { c -> list.removeAll { it.name == c.name }; list.add(c) }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()
        })
        .build()

    private var lastUrl: String? = null

    val connected: Boolean get() = prefs.getString(KEY_TOKEN, null) != null
    val account: String? get() = prefs.getString(KEY_ACCOUNT, null)

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    // ---------------------------------------------------------------- sign-in

    private fun ssoUrl(path: String, params: Map<String, String>): HttpUrl {
        val b = "$SSO$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun get(url: HttpUrl): String = send(Request.Builder().url(url).get())

    private fun postForm(url: HttpUrl, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return send(Request.Builder().url(url).post(body))
    }

    private fun send(b: Request.Builder): String {
        b.header("User-Agent", BROWSER_UA)
        lastUrl?.let { b.header("Referer", it) }
        val req = b.build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 429) throw GarminException("Garmin is limiting sign-ins right now. Try again in an hour.")
            if (!r.isSuccessful) throw GarminException("Garmin sign-in failed (HTTP ${r.code}).")
            lastUrl = req.url.toString()
            return text
        }
    }

    private fun csrf(html: String): String =
        Regex("""name="_csrf"\s+value="(.+?)"""").find(html)?.groupValues?.get(1)
            ?: throw GarminException("Garmin's sign-in page changed; this needs an app update.")

    private fun title(html: String): String = Regex("""<title>(.+?)</title>""").find(html)?.groupValues?.get(1).orEmpty()

    /** Signs in; returns null when done, or a [PendingMfa] when Garmin asks for a code. */
    suspend fun signIn(email: String, password: String): PendingMfa? = withContext(Dispatchers.IO) {
        cookies.clear()
        lastUrl = null
        get(ssoUrl("/embed", EMBED_PARAMS))
        val page = get(ssoUrl("/signin", SIGNIN_PARAMS))
        val result = postForm(
            ssoUrl("/signin", SIGNIN_PARAMS),
            mapOf("username" to email.trim(), "password" to password, "embed" to "true", "_csrf" to csrf(page)),
        )
        val t = title(result)
        when {
            "MFA" in t -> PendingMfa(csrf(result))
            t == "Success" -> {
                finish(result, email)
                null
            }
            "locked" in result.lowercase() -> throw GarminException("Garmin locked the account for too many attempts. Wait and try again.")
            else -> throw GarminException("Wrong e-mail or password.")
        }
    }

    suspend fun submitMfa(pending: PendingMfa, code: String, email: String) = withContext(Dispatchers.IO) {
        val result = postForm(
            ssoUrl("/verifyMFA/loginEnterMfaCode", SIGNIN_PARAMS),
            mapOf("mfa-code" to code.trim(), "embed" to "true", "_csrf" to pending.csrf, "fromPage" to "setupEnterMfaCode"),
        )
        if (title(result) != "Success") throw GarminException("That code didn't work. Try a new one.")
        finish(result, email)
    }

    private fun finish(html: String, email: String) {
        val ticket = Regex("""embed\?ticket=([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: throw GarminException("Garmin didn't return a sign-in ticket.")
        val consumer = consumer()
        val url = "$API/oauth-service/oauth/preauthorized".toHttpUrl().newBuilder()
            .addQueryParameter("ticket", ticket)
            .addQueryParameter("login-url", "$SSO/embed")
            .addQueryParameter("accepts-mfa-tokens", "true")
            .build()
        val text = oauth1Call("GET", url, consumer, null, null)
        val q = text.split('&').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }.associate { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") }
        val token = q["oauth_token"] ?: throw GarminException("Garmin didn't return a token.")
        val secret = q["oauth_token_secret"] ?: throw GarminException("Garmin didn't return a token.")
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_SECRET, secret)
            .putString(KEY_MFA, q["mfa_token"])
            .putString(KEY_ACCOUNT, email.trim())
            .putString(KEY_CONSUMER, consumer.first)
            .putString(KEY_CONSUMER_SECRET, consumer.second)
            .apply()
        exchange()
    }

    /** Garmin's app credentials, from the same place garth gets them. */
    private fun consumer(): Pair<String, String> {
        val cached = prefs.getString(KEY_CONSUMER, null)?.let { it to prefs.getString(KEY_CONSUMER_SECRET, "")!! }
        if (cached != null) return cached
        val text = http.newCall(Request.Builder().url(CONSUMER_URL).build()).execute().use { it.body?.string().orEmpty() }
        val o = runCatching { StrideJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw GarminException("Couldn't get Garmin app credentials. Check the internet connection.")
        return o["consumer_key"]!!.jsonPrimitive.content to o["consumer_secret"]!!.jsonPrimitive.content
    }

    // ---------------------------------------------------------------- OAuth

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    /** OAuth1 HMAC-SHA1 signed call (query parameters and form fields are part of the signature). */
    private fun oauth1Call(method: String, url: HttpUrl, consumer: Pair<String, String>, token: String?, tokenSecret: String?, form: Map<String, String> = emptyMap()): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val oauth = sortedMapOf(
            "oauth_consumer_key" to consumer.first,
            "oauth_nonce" to nonce,
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "oauth_version" to "1.0",
        )
        token?.let { oauth["oauth_token"] = it }
        val all = ArrayList<Pair<String, String>>()
        oauth.forEach { (k, v) -> all += k to v }
        for (i in 0 until url.querySize) all += url.queryParameterName(i) to (url.queryParameterValue(i) ?: "")
        form.forEach { (k, v) -> all += k to v }
        val params = all.map { enc(it.first) to enc(it.second) }.sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
        val baseUrl = url.newBuilder().query(null).build().toString()
        val base = "$method&${enc(baseUrl)}&${enc(params)}"
        val key = "${enc(consumer.second)}&${enc(tokenSecret ?: "")}"
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA1")) }
        oauth["oauth_signature"] = Base64.encodeToString(mac.doFinal(base.toByteArray()), Base64.NO_WRAP)
        val header = "OAuth " + oauth.entries.joinToString(", ") { "${enc(it.key)}=\"${enc(it.value)}\"" }
        val b = Request.Builder().url(url).header("Authorization", header).header("User-Agent", APP_UA)
        if (method == "POST") b.post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()) else b.get()
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) throw GarminException("Garmin sign-in expired. Connect Garmin again in Settings.")
            if (!r.isSuccessful) throw GarminException("Garmin token request failed (HTTP ${r.code}).")
            return text
        }
    }

    /** OAuth1 → OAuth2 access token (about an hour); repeated whenever it runs out. */
    private fun exchange(): String {
        val token = prefs.getString(KEY_TOKEN, null) ?: throw GarminException("Connect Garmin in Settings first.")
        val secret = prefs.getString(KEY_SECRET, "")!!
        val form = prefs.getString(KEY_MFA, null)?.let { mapOf("mfa_token" to it) } ?: emptyMap()
        val text = oauth1Call("POST", "$API/oauth-service/oauth/exchange/user/2.0".toHttpUrl(), consumer(), token, secret, form)
        val o = StrideJson.parseToJsonElement(text).jsonObject
        val access = o["access_token"]?.jsonPrimitive?.content ?: throw GarminException("Garmin didn't return an access token.")
        val expires = o["expires_in"]?.jsonPrimitive?.long ?: 3600
        prefs.edit().putString(KEY_ACCESS, access).putLong(KEY_ACCESS_UNTIL, System.currentTimeMillis() + (expires - 120) * 1000).apply()
        return access
    }

    private fun access(): String {
        val a = prefs.getString(KEY_ACCESS, null)
        return if (a != null && System.currentTimeMillis() < prefs.getLong(KEY_ACCESS_UNTIL, 0)) a else exchange()
    }

    private fun api(method: String, path: String, json: JsonObject?): JsonObject? =
        apiRaw(method, path, json, "the workout")?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun apiRaw(method: String, path: String, json: JsonObject?, what: String): JsonElement? {
        val b = Request.Builder().url("$API$path")
            .header("Authorization", "Bearer ${access()}")
            .header("User-Agent", APP_UA)
            .header("NK", "NT")
        val body = (json?.toString() ?: "{}").toRequestBody("application/json".toMediaType())
        when (method) {
            "POST" -> b.post(body)
            "PUT" -> b.put(body)
            else -> b.get()
        }
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) {
                prefs.edit().remove(KEY_ACCESS).apply()
                throw GarminException("Garmin sign-in expired. Connect Garmin again in Settings.")
            }
            if (!r.isSuccessful) throw GarminException("Garmin rejected $what (HTTP ${r.code}). ${text.take(160)}")
            return runCatching { StrideJson.parseToJsonElement(text) }.getOrNull()
        }
    }

    /**
     * Replaces the sets of the strength activity the watch recorded on [date] with the phone's ([GarminSets]).
     * Null when Garmin has no strength activity that day yet (the watch hasn't synced). Retries without exact
     * exercise names when Garmin rejects them.
     */
    suspend fun copySets(session: app.stride.model.StrengthSession): GarminSets.Merge? = withContext(Dispatchers.IO) {
        val list = apiRaw("GET", "/activitylist-service/activities/search/activities?startDate=${session.date}&endDate=${session.date}&limit=20", null, "the activity search")
        val id = (list as? kotlinx.serialization.json.JsonArray)?.let { GarminSets.strengthActivityId(it) } ?: return@withContext null
        val watch = apiRaw("GET", "/activity-service/activity/$id/exerciseSets", null, "the activity's sets") as? JsonObject
            ?: throw GarminException("Garmin didn't return the watch's sets.")
        val withId = if (watch["activityId"] == null) JsonObject(watch + ("activityId" to kotlinx.serialization.json.JsonPrimitive(id))) else watch
        val merged = GarminSets.merge(withId, session)
        try {
            apiRaw("PUT", "/activity-service/activity/$id/exerciseSets", merged.body, "the sets")
            merged
        } catch (e: GarminException) {
            if (e.message?.contains("HTTP 4") != true || e.message?.contains("HTTP 401") == true) throw e
            GarminSets.merge(withId, session, exactNames = false).also { apiRaw("PUT", "/activity-service/activity/$id/exerciseSets", it.body, "the sets") }
        }
    }

    /**
     * Creates the workout in Garmin Connect and puts it on the calendar for [date]; returns the workout id.
     * When Garmin rejects [workout] (e.g. an exercise name it doesn't know), [fallback] is sent instead.
     */
    /** Uploads [workout]; on a 4xx (other than 401) tries each of [fallbacks] in turn, then schedules it on [date]. */
    suspend fun sendWorkout(workout: JsonObject, date: String, fallbacks: List<JsonObject> = emptyList()): Long = withContext(Dispatchers.IO) {
        var created: JsonObject? = null
        for ((i, w) in (listOf(workout) + fallbacks).withIndex()) {
            try {
                created = api("POST", "/workout-service/workout", w)
                break
            } catch (e: GarminException) {
                if (i == fallbacks.size || e.message?.contains("HTTP 4") != true || e.message?.contains("HTTP 401") == true) throw e
            }
        }
        val id = created?.get("workoutId")?.jsonPrimitive?.long ?: throw GarminException("Garmin didn't confirm the workout.")
        api("POST", "/workout-service/schedule/$id", kotlinx.serialization.json.buildJsonObject { put("date", kotlinx.serialization.json.JsonPrimitive(date)) })
        id
    }

    companion object {
        private const val SSO = "https://sso.garmin.com/sso"
        private const val API = "https://connectapi.garmin.com"
        private const val CONSUMER_URL = "https://thegarth.s3.amazonaws.com/oauth_consumer.json"
        private const val APP_UA = "com.garmin.android.apps.connectmobile"
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 16; Pixel 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"
        private const val KEY_TOKEN = "oauth1_token"
        private const val KEY_SECRET = "oauth1_secret"
        private const val KEY_MFA = "mfa_token"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_ACCESS = "oauth2_access"
        private const val KEY_ACCESS_UNTIL = "oauth2_until"
        private const val KEY_CONSUMER = "consumer_key"
        private const val KEY_CONSUMER_SECRET = "consumer_secret"

        private val EMBED_PARAMS = mapOf("id" to "gauth-widget", "embedWidget" to "true", "gauthHost" to SSO)
        private val SIGNIN_PARAMS = EMBED_PARAMS + mapOf(
            "gauthHost" to "$SSO/embed",
            "service" to "$SSO/embed",
            "source" to "$SSO/embed",
            "redirectAfterAccountLoginUrl" to "$SSO/embed",
            "redirectAfterAccountCreationUrl" to "$SSO/embed",
        )
    }
}
