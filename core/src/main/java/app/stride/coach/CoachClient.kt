package app.stride.coach

import app.stride.data.StrideJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls Claude with the user's own API key (entered in Settings, never part of the app). Shared by all modules.
 * One plain POST to the Messages API: the official Java SDK (with Jackson and its models) was the largest part of the
 * app's code for this one request.
 */
object CoachClient {
    private const val MODEL = "claude-opus-5-5"
    private const val URL = "https://api.anthropic.com/v1/messages"

    // A long answer without streaming can take minutes.
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(10, TimeUnit.MINUTES).callTimeout(11, TimeUnit.MINUTES).build()

    class CoachException(message: String) : IOException(message)

    suspend fun review(apiKey: String, system: String, prompt: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 16000)
            put("system", system)
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
            // If a safety classifier declines, re-run on Anthropic's recommended fallback model instead of failing.
            put("fallbacks", "default")
        }
        val req = Request.Builder().url(URL)
            .header("x-api-key", apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", "server-side-fallback-2026-07-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val (code, text) = try {
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw CoachException("Couldn't reach Anthropic (${e.message ?: "no connection"}). Try again later.")
        }
        when {
            code == 401 -> throw CoachException("The Anthropic API key was rejected. Check it in Settings.")
            code == 429 -> throw CoachException("Too many requests right now. Try again in a minute.")
            code !in 200..299 -> throw CoachException("Anthropic API error $code. Try again later.")
        }
        val json = runCatching { StrideJson.parseToJsonElement(text) as JsonObject }
            .getOrElse { throw CoachException("Anthropic sent an answer Hive couldn't read. Try again.") }
        if (json["stop_reason"]?.jsonPrimitive?.contentOrNull == "refusal") {
            throw CoachException("The coach couldn't answer this week. Try again later.")
        }
        (json["content"] as? JsonArray).orEmpty()
            .mapNotNull { block ->
                val b = block as? JsonObject ?: return@mapNotNull null
                if (b["type"]?.jsonPrimitive?.contentOrNull == "text") b["text"]?.jsonPrimitive?.contentOrNull else null
            }
            .joinToString("\n").trim()
            .ifEmpty { throw CoachException("The coach sent an empty answer. Try again.") }
    }
}
