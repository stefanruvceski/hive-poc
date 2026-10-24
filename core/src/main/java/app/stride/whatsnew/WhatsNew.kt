package app.stride.whatsnew

import app.stride.data.StrideJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class WhatsNewItem(
    /** One of: apps, heart, spark, speed, bug, watch, chart, bell, palette, swim, run, strength. */
    val icon: String = "spark",
    val text: String,
)

/** One release note in assets/whats_new.json, newest first. [id] is unique and never reused. */
@Serializable
data class WhatsNewEntry(val id: String, val title: String, val items: List<WhatsNewItem> = emptyList())

/** What to show after an update (pure, unit tested). */
sealed interface WhatsNewResult {
    data object Nothing : WhatsNewResult
    /** New notes since the last one the user saw (newest first, at most 3). */
    data class Notes(val entries: List<WhatsNewEntry>) : WhatsNewResult
    /** An update without notes: fixes and polish only. */
    data class Polish(val emoji: String, val title: String, val text: String) : WhatsNewResult
}

object WhatsNew {
    fun parse(json: String): List<WhatsNewEntry> =
        runCatching { StrideJson.decodeFromString(ListSerializer(WhatsNewEntry.serializer()), json) }.getOrDefault(emptyList())

    private val POLISH = listOf(
        Triple("⚙️", "Tuned under the hood", "Nothing new to tap this time. We tightened a few bolts so everything runs a little smoother."),
        Triple("🐛", "Bugs squashed", "A few small bugs are gone. Same app, just calmer and more reliable."),
        Triple("⚡", "A bit faster", "We cleaned up behind the scenes, so screens open quicker and syncing is lighter."),
        Triple("✨", "Polished", "Small fixes and details you may not even notice, all to make your days easier."),
        Triple("🧰", "Maintenance done", "We did some housekeeping in the code so the next features land faster."),
    )

    /**
     * [lastVersion] 0 and [freshInstall]: first launch ever, nothing to show. Otherwise, after an update
     * (current > last): unseen notes, or a polish message when there are none.
     */
    fun decide(entries: List<WhatsNewEntry>, lastSeenId: String?, lastVersion: Int, current: Int, freshInstall: Boolean): WhatsNewResult {
        if (current <= lastVersion) return WhatsNewResult.Nothing
        if (lastVersion == 0 && freshInstall) return WhatsNewResult.Nothing
        val unseen = entries.takeWhile { it.id != lastSeenId }
        if (unseen.isNotEmpty() && lastSeenId != entries.firstOrNull()?.id) return WhatsNewResult.Notes(unseen.take(3))
        val (emoji, title, text) = POLISH[Math.floorMod(current, POLISH.size)]
        return WhatsNewResult.Polish(emoji, title, text)
    }
}
