package app.stride.coach

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Calls Claude with the user's own API key (entered in Settings, never part of the app). */
object CoachClient {
    private const val MODEL = "claude-opus-5-5"

    class CoachException(message: String) : IOException(message)

    suspend fun review(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(CoachPrompt.SYSTEM)
                .addUserMessage(prompt)
                // If a safety classifier declines, re-run on Anthropic's recommended fallback model instead of failing.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
            val response = client.beta().messages().create(params)
            if (response.stopReason().map { it.toString() }.orElse("") == "refusal") {
                throw CoachException("The coach couldn't answer this week. Try again later.")
            }
            response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
                .joinToString("\n").trim()
                .ifEmpty { throw CoachException("The coach sent an empty answer. Try again.") }
        } catch (e: UnauthorizedException) {
            throw CoachException("The Anthropic API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) {
            throw CoachException("Too many requests right now. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            throw CoachException("Anthropic API error ${e.statusCode()}. Try again later.")
        } finally {
            client.close()
        }
    }
}
