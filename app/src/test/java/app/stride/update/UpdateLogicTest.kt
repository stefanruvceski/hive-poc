package app.stride.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    private fun apk(name: String = "Stride.apk") = GhAsset(name, "https://example.com/$name", 1234)

    private fun release(tag: String, draft: Boolean = false, assets: List<GhAsset> = listOf(apk()), body: String? = "Notes") =
        GhRelease(tag = tag, body = body, draft = draft, assets = assets)

    @Test
    fun buildFromTag_parsesNumber() {
        assertEquals(1, UpdateLogic.buildFromTag("android-b1"))
        assertEquals(42, UpdateLogic.buildFromTag("android-b42"))
        assertEquals(1234, UpdateLogic.buildFromTag("android-b1234"))
    }

    @Test
    fun buildFromTag_rejectsOtherTags() {
        assertNull(UpdateLogic.buildFromTag("v42"))
        assertNull(UpdateLogic.buildFromTag("ios-b42"))
        assertNull(UpdateLogic.buildFromTag("android-b"))
        assertNull(UpdateLogic.buildFromTag("android-b0"))
        assertNull(UpdateLogic.buildFromTag("android-b-3"))
        assertNull(UpdateLogic.buildFromTag("android-b12rc"))
        assertNull(UpdateLogic.buildFromTag("android-b 12"))
        assertNull(UpdateLogic.buildFromTag("Android-b12"))
        assertNull(UpdateLogic.buildFromTag("android-b99999999999"))
    }

    @Test
    fun pickLatest_takesHighestBuildNotFirstInList() {
        val latest = UpdateLogic.pickLatest(listOf(release("android-b7"), release("android-b12"), release("android-b9")))
        assertEquals(12, latest?.build)
        assertEquals("android-b12", latest?.tag)
        assertEquals("https://example.com/Stride.apk", latest?.apkUrl)
        assertEquals(1234L, latest?.apkSize)
    }

    @Test
    fun pickLatest_comparesNumericallyNotAlphabetically() {
        assertEquals(10, UpdateLogic.pickLatest(listOf(release("android-b9"), release("android-b10")))?.build)
    }

    @Test
    fun pickLatest_skipsDrafts() {
        val latest = UpdateLogic.pickLatest(listOf(release("android-b20", draft = true), release("android-b15")))
        assertEquals(15, latest?.build)
    }

    @Test
    fun pickLatest_skipsReleasesWithoutApk() {
        val latest = UpdateLogic.pickLatest(
            listOf(
                release("android-b30", assets = emptyList()),
                release("android-b29", assets = listOf(apk("notes.txt"), apk("Stride.aab"))),
                release("android-b28", assets = listOf(apk("notes.txt"), apk("Stride.APK"))),
            ),
        )
        assertEquals(28, latest?.build)
        assertEquals("https://example.com/Stride.APK", latest?.apkUrl)
    }

    @Test
    fun pickLatest_skipsOtherPrefixes() {
        val latest = UpdateLogic.pickLatest(listOf(release("v99"), release("web-b50"), release("android-b3")))
        assertEquals(3, latest?.build)
    }

    @Test
    fun pickLatest_nullWhenNothingQualifies() {
        assertNull(UpdateLogic.pickLatest(emptyList()))
        assertNull(UpdateLogic.pickLatest(listOf(release("v1"), release("android-b2", draft = true), release("android-b3", assets = emptyList()))))
    }

    @Test
    fun newerRelease_comparesWithInstalledBuild() {
        val releases = listOf(release("android-b10"), release("android-b8"))
        assertEquals(10, UpdateLogic.newerRelease(releases, installedBuild = 9)?.build)
        assertNull(UpdateLogic.newerRelease(releases, installedBuild = 10))
        assertNull(UpdateLogic.newerRelease(releases, installedBuild = 11))
        assertNull(UpdateLogic.newerRelease(emptyList(), installedBuild = 1))
    }

    @Test
    fun shouldShow_respectsDismissedBuild() {
        val r10 = UpdateLogic.pickLatest(listOf(release("android-b10")))
        val r11 = UpdateLogic.pickLatest(listOf(release("android-b11")))
        assertTrue(UpdateLogic.shouldShow(r10, dismissedBuild = 0))
        assertFalse(UpdateLogic.shouldShow(r10, dismissedBuild = 10))
        assertTrue(UpdateLogic.shouldShow(r11, dismissedBuild = 10))
        assertFalse(UpdateLogic.shouldShow(null, dismissedBuild = 0))
    }

    @Test
    fun notesExcerpt_firstMeaningfulLineWithoutMarkdown() {
        assertEquals("Faster sync", UpdateLogic.notesExcerpt("\n\n## Faster sync\n- other"))
        assertEquals("Fix crash", UpdateLogic.notesExcerpt("- Fix crash"))
        assertEquals("", UpdateLogic.notesExcerpt(null))
        assertEquals("", UpdateLogic.notesExcerpt("  \n "))
        val long = "x".repeat(200)
        val excerpt = UpdateLogic.notesExcerpt(long, max = 20)
        assertEquals(20, excerpt.length)
        assertTrue(excerpt.endsWith("…"))
    }

    @Test
    fun parseReleases_readsGitHubJson() {
        val json = """
            [
              {"tag_name": "android-b5", "name": "Stride · build 5", "draft": false, "prerelease": false,
               "body": "Add update banner", "published_at": "2026-10-22T10:00:00Z",
               "assets": [{"name": "Stride.apk", "size": 9000000, "content_type": "application/vnd.android.package-archive",
                           "browser_download_url": "https://github.com/stefanruvceski/stride/releases/download/android-b5/Stride.apk"}]},
              {"tag_name": "android-b6", "draft": true, "body": null, "assets": []}
            ]
        """.trimIndent()
        val releases = UpdateLogic.parseReleases(json)
        assertEquals(2, releases.size)
        val latest = UpdateLogic.pickLatest(releases)
        assertEquals(5, latest?.build)
        assertEquals(9_000_000L, latest?.apkSize)
        assertEquals("Add update banner", latest?.notes)
        assertEquals("https://github.com/stefanruvceski/stride/releases/download/android-b5/Stride.apk", latest?.apkUrl)
    }
}
