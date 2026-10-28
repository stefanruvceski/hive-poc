package app.stride.garmin

import android.content.Context
import android.util.Base64
import app.stride.data.StrideJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Garmin's official Personal API (read-only): the user's own key from connect.garmin.com → Settings → Personal API Key
 * is traded for a 24 h bearer token, and the gateway answers the same service paths the Garmin Connect web app uses.
 * The key and token stay in prefs "garmin" (excluded from Android backup and from Hive's backups).
 */
class GarminPersonal(private val context: Context) {
    class PersonalException(message: String) : Exception(message)

    private val prefs = context.getSharedPreferences("garmin", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    val hasKey: Boolean get() = !prefs.getString(KEY, null).isNullOrBlank()

    fun setKey(key: String?) {
        prefs.edit().putString(KEY, key?.trim()?.ifEmpty { null }).remove(TOKEN).remove(TOKEN_UNTIL).remove(NAME).apply()
    }

    /** The bearer token, traded again from the key when less than an hour is left. */
    private fun token(force: Boolean = false): String {
        val cached = prefs.getString(TOKEN, null)
        if (!force && cached != null && System.currentTimeMillis() < prefs.getLong(TOKEN_UNTIL, 0) - 3_600_000) return cached
        val key = prefs.getString(KEY, null) ?: throw PersonalException("Add your Garmin Personal API key first.")
        val req = Request.Builder().url(BASE + TOKEN_PATH)
            .header("Accept", "*/*").header("Authorization", "ApiKey $key")
            .post(ByteArray(0).toRequestBody()).build()
        http.newCall(req).execute().use { r ->
            // The body isn't shown on errors: it may echo the key.
            when {
                r.code == 401 || r.code == 403 -> throw PersonalException("Garmin rejected the key (HTTP ${r.code}): it's wrong, revoked or expired. Create a new one.")
                r.code == 429 -> throw PersonalException("Garmin says too many requests; try again in a few minutes.")
                !r.isSuccessful -> throw PersonalException("Couldn't get a token (HTTP ${r.code}).")
            }
            val t = r.body?.string().orEmpty().trim().trim('"')
            if (t.isEmpty()) throw PersonalException("Garmin returned an empty token.")
            prefs.edit().putString(TOKEN, t).putLong(TOKEN_UNTIL, expiry(t)).apply()
            return t
        }
    }

    /** A JWT's "exp" in millis; 24 h from now when it can't be read. */
    private fun expiry(jwt: String): Long = runCatching {
        val payload = String(Base64.decode(jwt.split('.')[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val exp = StrideJson.parseToJsonElement(payload).jsonObject["exp"]!!.jsonPrimitive.longOrNull!!
        if (exp > 100_000_000_000) exp else exp * 1000
    }.getOrDefault(System.currentTimeMillis() + 24 * 3_600_000L)

    /** GET [path] on the gateway: (HTTP code, body). A 401 trades the key again once. */
    private fun get(path: String, retry: Boolean = true): Pair<Int, String> {
        val req = Request.Builder().url(BASE + path).header("Accept", "application/json").header("Authorization", "Bearer ${token()}").get().build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 && retry) { token(force = true); return get(path, retry = false) }
            return r.code to r.body?.string().orEmpty()
        }
    }

    private fun displayName(): String? {
        prefs.getString(NAME, null)?.let { return it }
        for (path in listOf("/userprofile-service/socialProfile", "/userprofile-service/userprofile/settings")) {
            val (code, body) = runCatching { get(path) }.getOrNull() ?: continue
            if (code != 200) continue
            val n = runCatching { (StrideJson.parseToJsonElement(body).jsonObject["displayName"] as? JsonPrimitive)?.content }.getOrNull()
            if (!n.isNullOrBlank()) { prefs.edit().putString(NAME, n).apply(); return n }
        }
        return null
    }

    /**
     * Intensity minutes for [days]: each day's summary plus one activity search over the range for the workouts'
     * share (`domain/Intensity`). Days without a summary (404, not synced yet) are left out.
     */
    suspend fun intensity(days: List<LocalDate>): List<app.stride.model.IntensityDay> = withContext(Dispatchers.IO) {
        val from = days.min(); val to = days.max()
        val (aCode, aBody) = get("/activitylist-service/activities/search/activities?startDate=$from&endDate=$to&limit=100")
        val share = if (aCode == 200) app.stride.domain.Intensity.inWorkouts(StrideJson.parseToJsonElement(aBody)) else emptyMap()
        days.mapNotNull { d ->
            val (code, body) = get("/usersummary-service/usersummary/daily?calendarDate=$d")
            if (code != 200) return@mapNotNull null
            val (m, v) = runCatching { app.stride.domain.Intensity.daily(StrideJson.parseToJsonElement(body)) }.getOrNull() ?: return@mapNotNull null
            app.stride.model.IntensityDay(d.toString(), m, v, share[d.toString()] ?: 0)
        }
    }

    /**
     * Asks the gateway for one day of what Hive could use (daily summary, 15-minute steps, intensity minutes,
     * detected moves, sleep, HRV, Body Battery, stress, activities) and writes every answer to
     * cache/garmin_personal_probe.json (never the key or token), so it can be shared. Returns "path → result" lines.
     */
    suspend fun probe(day: LocalDate = LocalDate.now().minusDays(1)): List<String> = withContext(Dispatchers.IO) {
        token()
        val name = displayName()?.let { URLEncoder.encode(it, "UTF-8") }
        // The documented Personal API paths (Garmin's Swagger, 2026-10-28). Several differ from the Connect web
        // app's (no display name in the path, other names), which is why the first probe got 404s.
        val week = day.minusDays(6)
        val paths = listOf(
            // Walks and other moves the watch detected by itself (wellness activities), and intensity minutes.
            "/wellnessactivity-service/activity/summary/range/$week/$day",
            "/usersummary-service/usersummary/daily?calendarDate=$day",
            "/usersummary-service/v2/dailysummaries?startDate=$day&endDate=$day",
            "/usersummary-service/stats/steps/daily/$day/$day",
            "/usersummary-service/stats/stress/daily/$day/$day",
            "/sleep-service/sleep/dailySleep?date=$day",
            "/wellness-service/wellness/dailyHeartRate?date=$day",
            "/hrv-service/hrv/daily/$week/$day",
            "/wellness-service/wellness/bodyBattery/reports/daily?startDate=$day&endDate=$day",
            "/weight-service/weight/range/$week/$day",
            "/activitylist-service/activities/search/activities?startDate=$day&endDate=$day&limit=20",
        )
        val answers = linkedMapOf<String, JsonElement>()
        val lines = mutableListOf("Display name: ${if (name != null) "found" else "not found"} · day $day")
        for (p in paths) {
            val (code, body) = runCatching { get(p) }.getOrElse { 0 to "error: ${it.message}" }
            val json = if (body.length > 30_000) JsonPrimitive(body.take(30_000) + "…(cut)")
                else runCatching { StrideJson.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body.take(2000)) }
            answers[p] = buildJsonObject { put("http", code); put("body", json) }
            lines += "${p.substringBefore('?').replace(name ?: "\u0000", "{name}")} → HTTP $code, ${body.length} chars"
        }
        runCatching { File(context.cacheDir, PROBE_FILE).writeText(JsonObject(answers).toString()) }
        lines
    }

    companion object {
        const val PROBE_FILE = "garmin_personal_probe.json"
        const val KEY_PAGE = "https://connect.garmin.com/app/settings/personalApiToken"
        private const val BASE = "https://personal.connectapi.garmin.com"
        private const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
        private const val KEY = "personal_api_key"
        private const val TOKEN = "personal_api_token"
        private const val TOKEN_UNTIL = "personal_api_token_until"
        private const val NAME = "personal_display_name"
    }
}
