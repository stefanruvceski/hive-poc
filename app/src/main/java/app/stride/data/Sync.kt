package app.stride.data

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.SRC_GARMIN
import app.stride.model.SRC_MANUAL
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import java.time.LocalDate
import java.time.LocalDateTime

/** One fetch from intervals.icu, merged into the app's data by [apply]. Shared by the app and the background worker. */
class SyncResult(val activities: List<Activity>, val stravaSkipped: Int, val wellness: List<Wellness>, val today: LocalDate) {
    val oldest: LocalDate get() = today.minusDays(84)

    fun apply(d: AppData): AppData {
        val kept = d.activities.filter { a -> a.date()?.isBefore(oldest) ?: false }
        val byDate = d.weights.associateBy { it.date }.toMutableMap()
        for (w in wellness) {
            val kg = w.weight ?: continue
            val existing = byDate[w.id]
            if (existing == null || existing.source != SRC_MANUAL) byDate[w.id] = WeightEntry(w.id, kg, SRC_GARMIN)
        }
        val recoverySince = today.minusDays(120).toString()
        val recovery = (d.wellness + wellness.filter { it.hasRecovery }) // fresh values win; a failed fetch keeps the old ones
            .associateBy { it.id }.values
            .filter { it.id >= recoverySince }
            .sortedBy { it.id }
        return d.copy(
            wellness = recovery,
            activities = (kept + activities).distinctBy { it.id },
            weights = byDate.values.sortedBy { it.date },
            lastSync = LocalDateTime.now().toString(),
        )
    }
}

object Sync {
    suspend fun fetch(api: IntervalsApi, today: LocalDate = LocalDate.now()): SyncResult {
        val all = api.activities(today.minusDays(84), today.plusDays(1))
        val strava = all.count { it.isRestrictedStub }
        val acts = all.filter { !it.isRestrictedStub && it.type != null && it.startLocal != null }
        val well = runCatching { api.wellness(today.minusDays(180), today) }.getOrDefault(emptyList())
        return SyncResult(acts, strava, well, today)
    }
}
