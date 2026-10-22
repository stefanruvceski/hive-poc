package app.stride.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Android side of self-update: GitHub API call, APK download, installer intents. */
object Updater {
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Release list from GitHub (public repo, no token), or null when offline, rate
     * limited or anything else goes wrong. Never throws.
     */
    suspend fun fetchReleases(): List<GhRelease>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UpdateLogic.RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Stride-Android")
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                UpdateLogic.parseReleases(r.body?.string() ?: return@withContext null)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    private fun apkFile(context: Context, release: ReleaseInfo) = File(updatesDir(context), "Stride-b${release.build}.apk")

    /**
     * Downloads the APK into cache/updates (reusing a complete earlier download) and
     * reports progress 0..1, or null while the size is unknown. Throws on failure.
     */
    suspend fun download(context: Context, release: ReleaseInfo, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = updatesDir(context)
        dir.mkdirs()
        val target = apkFile(context, release)
        if (release.apkSize > 0 && target.length() == release.apkSize) return@withContext target
        // Only the APK being installed is kept.
        dir.listFiles()?.forEach { it.delete() }

        val part = File(dir, target.name + ".part")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "Stride-Android").build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            onProgress(if (total != null) 0f else null)
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total != null) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (total != null && part.length() != total) throw IOException("Incomplete download")
        }
        if (!part.renameTo(target)) throw IOException("Could not save the update")
        target
    }

    /** Android needs "Install unknown apps" granted to Stride before it can open the installer for us. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" screen for Stride. */
    fun openInstallPermissionSettings(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
    )

    /** Hands the downloaded APK to Android's package installer. */
    fun launchInstaller(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
