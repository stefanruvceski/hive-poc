package app.stride.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.stride.domain.Analyzer
import app.stride.model.AppData
import app.stride.model.WeightEntry
import app.stride.model.Wellness
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Health Connect: reads steps, sleep, weight, HRV and resting HR (filling what intervals.icu
 * didn't provide) and writes finished strength workouts so other apps see them.
 */
object HealthSync {
    const val SOURCE = "Health Connect"

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Set<String> =
        if (!available(context)) emptySet() else HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /** Daily values read from Health Connect, keyed by ISO date (sleep and HRV on the date you wake up). */
    data class Day(
        val steps: Double? = null,
        val sleepSecs: Double? = null,
        val weight: Double? = null,
        val hrv: Double? = null,
        val restingHr: Double? = null,
    )

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val r = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += r.records
            token = r.pageToken
        } while (token != null && out.size < 20_000)
        return out
    }

    suspend fun read(context: Context, days: Long = 45): Map<String, Day> {
        val client = HealthConnectClient.getOrCreate(context)
        val have = client.permissionController.getGrantedPermissions()
        val zone = ZoneId.systemDefault()
        val to = Instant.now()
        val from = LocalDate.now().minusDays(days).atStartOfDay(zone).toInstant()
        fun date(i: Instant) = i.atZone(zone).toLocalDate().toString()
        val map = HashMap<String, Day>()
        fun upd(d: String, f: (Day) -> Day) { map[d] = f(map[d] ?: Day()) }

        if (HealthPermission.getReadPermission(StepsRecord::class) in have) {
            readAll(client, StepsRecord::class, from, to).groupBy { date(it.startTime) }
                .forEach { (d, list) -> upd(d) { it.copy(steps = list.sumOf { r -> r.count }.toDouble()) } }
        }
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in have) {
            readAll(client, SleepSessionRecord::class, from, to).groupBy { date(it.endTime) }
                .forEach { (d, list) -> upd(d) { it.copy(sleepSecs = list.sumOf { r -> (r.endTime.epochSecond - r.startTime.epochSecond).toDouble() }) } }
        }
        if (HealthPermission.getReadPermission(WeightRecord::class) in have) {
            readAll(client, WeightRecord::class, from, to).groupBy { date(it.time) }
                .forEach { (d, list) -> upd(d) { it.copy(weight = list.maxBy { r -> r.time }.weight.inKilograms) } }
        }
        if (HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in have) {
            readAll(client, HeartRateVariabilityRmssdRecord::class, from, to).groupBy { date(it.time) }
                .forEach { (d, list) -> upd(d) { it.copy(hrv = list.map { r -> r.heartRateVariabilityMillis }.average()) } }
        }
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in have) {
            readAll(client, RestingHeartRateRecord::class, from, to).groupBy { date(it.time) }
                .forEach { (d, list) -> upd(d) { it.copy(restingHr = list.map { r -> r.beatsPerMinute.toDouble() }.average()) } }
        }
        return map
    }

    /** Fills gaps only: values intervals.icu (Garmin) or the user entered win. Pure, unit tested. */
    fun merge(d: AppData, days: Map<String, Day>): AppData {
        val well = d.wellness.associateBy { it.id }.toMutableMap()
        for ((date, v) in days) {
            val w = well[date] ?: Wellness(date)
            well[date] = w.copy(
                steps = w.steps ?: v.steps,
                sleepSecs = w.sleepSecs ?: v.sleepSecs,
                hrv = w.hrv ?: v.hrv,
                restingHR = w.restingHR ?: v.restingHr,
            )
        }
        val weights = d.weights.associateBy { it.date }.toMutableMap()
        for ((date, v) in days) {
            val kg = v.weight ?: continue
            if (date !in weights) weights[date] = WeightEntry(date, kg, SOURCE)
        }
        return d.copy(
            wellness = well.values.filter { it.hasRecovery || it.steps != null }.sortedBy { it.id },
            weights = weights.values.sortedBy { it.date },
        )
    }

    /**
     * Writes finished strength sessions of the last 14 days that have no watch workout (the watch
     * already reports those) and weren't written before. Returns the ids written.
     */
    suspend fun writeStrength(context: Context, data: AppData): List<String> {
        val client = HealthConnectClient.getOrCreate(context)
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in client.permissionController.getGrantedPermissions()) return emptyList()
        val today = LocalDate.now()
        val since = today.minusDays(14).toString()
        val zone = ZoneId.systemDefault()
        val todo = data.strength.filter { it.finished && it.date >= since && it.id !in data.hcExported }
            .filter { s -> Analyzer.day(LocalDate.parse(s.date), today, data).matched.isEmpty() }
        if (todo.isEmpty()) return emptyList()
        val records = todo.map { s ->
            val date = LocalDate.parse(s.date)
            val time = Analyzer.day(date, today, data).plan?.time?.let { runCatching { LocalTime.parse(it.trim().padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(6, 0)
            val start = date.atTime(time).atZone(zone)
            val end = start.plusMinutes(60)
            ExerciseSessionRecord(
                startTime = start.toInstant(),
                startZoneOffset = start.offset,
                endTime = end.toInstant(),
                endZoneOffset = end.offset,
                metadata = Metadata.manualEntry(clientRecordId = "stride-${s.id}"),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = "Kettlebell strength",
                notes = s.exercises.filter { it.done || s.finished }.joinToString(", ") { it.name },
            )
        }
        client.insertRecords(records)
        return todo.map { it.id }
    }
}
