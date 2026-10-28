package app.stride.wallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.stride.data.StrideJson
import app.stride.wallet.domain.FiscalParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hive's QR repair server (backend/ in the repo, the user's own Vercel project): a receipt photo whose QR code the
 * phone couldn't read goes there, and a repair reader (geometry fix + Reed-Solomon with erasures) answers with the
 * receipt's link or nothing. The key is the user's, kept in prefs `wallet_secrets` (out of every backup).
 */
object QrRepairClient {
    const val DEFAULT_URL = "https://hive-backend.ruvceski.com/api/qr"
    /** The same Vercel project's own address, used when the custom domain can't be reached. */
    const val FALLBACK_URL = "https://hive-chi-rosy.vercel.app/api/qr"
    private const val PREFS = "wallet_secrets"
    private const val KEY = "qr_key"
    private const val URL = "qr_url"
    /** Vercel takes at most 4.5 MB per request. */
    private const val MAX_BYTES = 4_000_000

    // The server gives up after about 4 minutes; the function may run 5.
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(310, TimeUnit.SECONDS).callTimeout(330, TimeUnit.SECONDS).build()

    sealed interface Answer {
        data class Link(val url: String) : Answer
        data object NotFound : Answer
        data class Failed(val message: String) : Answer
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(context: Context): String? = prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun setKey(context: Context, key: String?) {
        prefs(context).edit().putString(KEY, key?.trim()?.ifEmpty { null }).apply()
    }

    fun url(context: Context): String = prefs(context).getString(URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL

    /** Blocking: call off the main thread. */
    fun repair(context: Context, photo: File): Answer {
        val key = key(context) ?: return Answer.Failed("No key for the repair server.")
        val bytes = jpeg(photo) ?: return Answer.Failed("Couldn't open the photo.")
        for (url in listOf(url(context), FALLBACK_URL).distinct()) {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $key")
                .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
            post(req)?.let { return it }
        }
        return Answer.Failed("Couldn't reach the repair server.")
    }

    /** The server's answer; null when it couldn't be reached at all (the next address is tried). */
    private fun post(req: Request): Answer? =
        runCatching {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.code == 401 -> Answer.Failed("The repair server didn't accept the key.")
                    !r.isSuccessful -> Answer.Failed("The repair server answered ${r.code}.")
                    else -> {
                        val link = runCatching { (StrideJson.parseToJsonElement(body) as? JsonObject)?.get("link")?.jsonPrimitive?.contentOrNull }.getOrNull()
                        link?.let { FiscalParse.verificationUrl(it) }?.let { Answer.Link(it) } ?: Answer.NotFound
                    }
                }
            }
        }.getOrNull()

    /** The photo as a JPEG of at most 2000 px and 4 MB (the QR's modules stay several pixels wide). */
    private fun jpeg(file: File): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scaled = if (longest > 2000) Bitmap.createScaledBitmap(bmp, bmp.width * 2000 / longest, bmp.height * 2000 / longest, true) else bmp
        for (q in listOf(92, 80, 65)) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= MAX_BYTES) return out.toByteArray()
        }
        return null
    }
}
