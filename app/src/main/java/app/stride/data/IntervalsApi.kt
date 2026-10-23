package app.stride.data

import app.stride.model.Activity
import app.stride.model.Interval
import app.stride.model.IntervalsResponse
import app.stride.model.Wellness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

class IntervalsApi(apiKey: String, athleteId: String) {
    private val athlete = athleteId.ifBlank { "0" }
    private val auth = Credentials.basic("API_KEY", apiKey)

    private fun request(url: String, method: String = "GET", jsonBody: String? = null): String {
        val builder = Request.Builder().url(url).header("Authorization", auth).header("Accept", "application/json")
        if (jsonBody != null) {
            builder.method(method, jsonBody.toRequestBody("application/json".toMediaType()))
        }
        client.newCall(builder.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            return body
        }
    }

    suspend fun athleteName(): String = withContext(Dispatchers.IO) {
        val obj = StrideJson.decodeFromString(JsonObject.serializer(), request("$BASE/athlete/$athlete"))
        obj["name"]?.jsonPrimitive?.content ?: obj["firstname"]?.jsonPrimitive?.content ?: "?"
    }

    /** Everything intervals.icu returns, including Strava placeholders; callers filter. */
    suspend fun activities(oldest: LocalDate, newest: LocalDate): List<Activity> = withContext(Dispatchers.IO) {
        val text = request("$BASE/athlete/$athlete/activities?oldest=$oldest&newest=$newest")
        StrideJson.decodeFromString(JsonArray.serializer(), text).mapNotNull { decodeActivity(it) }
    }

    /**
     * One activity at a time, so a field with an unexpected type only costs the extra
     * details of that activity (retried with the core fields), never the whole sync.
     */
    private fun decodeActivity(e: JsonElement): Activity? {
        val obj = e as? JsonObject ?: return null
        return runCatching { StrideJson.decodeFromJsonElement(Activity.serializer(), obj) }.getOrNull()
            ?: runCatching { StrideJson.decodeFromJsonElement(Activity.serializer(), JsonObject(obj.filterKeys { it in CORE_KEYS })) }.getOrNull()
    }

    suspend fun wellness(oldest: LocalDate, newest: LocalDate): List<Wellness> = withContext(Dispatchers.IO) {
        val text = request("$BASE/athlete/$athlete/wellness?oldest=$oldest&newest=$newest")
        StrideJson.decodeFromString(ListSerializer(Wellness.serializer()), text)
    }

    suspend fun intervals(activityId: String): List<Interval> = withContext(Dispatchers.IO) {
        val text = request("$BASE/activity/$activityId/intervals")
        StrideJson.decodeFromString(IntervalsResponse.serializer(), text).intervals
    }

    suspend fun putWeight(date: LocalDate, kg: Double) = withContext(Dispatchers.IO) {
        val body = String.format(Locale.US, "{\"weight\":%.2f}", kg)
        request("$BASE/athlete/$athlete/wellness/$date", "PUT", body)
        Unit
    }

    companion object {
        private const val BASE = "https://intervals.icu/api/v1"
        private val CORE_KEYS = setOf(
            "id", "start_date_local", "type", "name", "moving_time", "elapsed_time", "distance",
            "average_heartrate", "max_heartrate", "icu_hr_zone_times", "icu_hr_zones", "icu_training_load", "source", "_note",
        )
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }
}

fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> when (code) {
        401, 403 -> "intervals.icu denied access. Check your API key and athlete ID."
        404 -> "intervals.icu found no data (check the athlete ID)."
        429 -> "Too many requests to intervals.icu, try again in a minute."
        else -> "intervals.icu error ${code}."
    }
    is SerializationException, is IllegalArgumentException -> "Unexpected response from intervals.icu."
    is IOException -> "Can't reach intervals.icu. Check your connection."
    else -> message ?: "Something went wrong."
}
