package app.stride.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * Self-update for the sideloaded build, pure part (no Android, unit tested).
 *
 * Every push to main builds a signed APK in GitHub Actions and publishes it as a
 * GitHub Release tagged `android-b<N>` (N = versionCode). The app asks GitHub for
 * the newest such release and offers it when N is higher than the installed build.
 */

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class ReleaseInfo(
    val build: Int,
    val tag: String,
    val apkUrl: String,
    val apkSize: Long,
    val notes: String,
)

object UpdateLogic {
    const val TAG_PREFIX = "android-b"
    const val RELEASES_URL = "https://api.github.com/repos/stefanruvceski/stride/releases?per_page=20"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parseReleases(text: String): List<GhRelease> = json.decodeFromString(ListSerializer(GhRelease.serializer()), text)

    /** Build number from a tag like `android-b42`, or null for anything else. */
    fun buildFromTag(tag: String): Int? {
        if (!tag.startsWith(TAG_PREFIX)) return null
        val digits = tag.substring(TAG_PREFIX.length)
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }

    /** The highest-numbered non-draft `android-b<N>` release that has an APK attached. */
    fun pickLatest(releases: List<GhRelease>): ReleaseInfo? = releases
        .asSequence()
        .filterNot { it.draft }
        .mapNotNull { r ->
            val build = buildFromTag(r.tag) ?: return@mapNotNull null
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@mapNotNull null
            ReleaseInfo(build, r.tag, apk.downloadUrl, apk.size, notesExcerpt(r.body))
        }
        .maxByOrNull { it.build }

    /** The latest release if it is newer than [installedBuild], else null. */
    fun newerRelease(releases: List<GhRelease>, installedBuild: Int): ReleaseInfo? =
        pickLatest(releases)?.takeIf { it.build > installedBuild }

    /** Whether the banner should show [release] after the user dismissed [dismissedBuild] (0 = none). */
    fun shouldShow(release: ReleaseInfo?, dismissedBuild: Int): Boolean =
        release != null && release.build > dismissedBuild

    /** First meaningful line of the release notes, without Markdown markers, at most [max] chars. */
    fun notesExcerpt(body: String?, max: Int = 90): String {
        val line = body.orEmpty().lineSequence()
            .map { it.trim().trimStart('#', '-', '*', '>', ' ').trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
