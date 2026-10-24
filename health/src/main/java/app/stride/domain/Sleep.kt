package app.stride.domain

import app.stride.model.SleepNight
import app.stride.model.SleepStage
import app.stride.model.SleepStageKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sleep statistics on top of the watch's stages (pure, unit tested). The stages come from the device;
 * nobody can re-stage a night better without raw signals. What we add is the analysis, using the
 * standard sleep-medicine measures (AASM / National Sleep Foundation quality indicators):
 * efficiency, latency, wake after sleep onset (WASO), awakenings, stage shares, regularity (SRI)
 * and sleep debt, plus our own transparent 0-100 score.
 */
object Sleep {
    /** Healthy-adult reference ranges for stage shares of total sleep time. */
    val DEEP_RANGE = 13.0..23.0
    val REM_RANGE = 20.0..25.0

    data class Stats(
        val night: SleepNight,
        val inBedMin: Int,
        val asleepMin: Int,
        val awakeMin: Int,
        val deepMin: Int,
        val lightMin: Int,
        val remMin: Int,
        /** Asleep / in bed, %. */
        val efficiency: Int,
        /** Minutes from going to bed to the first sleep stage. */
        val latencyMin: Int,
        /** Awake minutes between falling asleep and the final wake-up. */
        val wasoMin: Int,
        /** Awake stretches of 2+ minutes after falling asleep. */
        val awakenings: Int,
        val staged: Boolean,
        val score: Int,
        val scoreParts: List<Pair<String, Int>>,
    ) {
        val deepPct: Double get() = if (asleepMin == 0) 0.0 else deepMin * 100.0 / asleepMin
        val remPct: Double get() = if (asleepMin == 0) 0.0 else remMin * 100.0 / asleepMin
        val lightPct: Double get() = if (asleepMin == 0) 0.0 else lightMin * 100.0 / asleepMin
    }

    private fun mins(s: SleepStage) = ((s.end - s.start) / 60.0)

    /** Stages, or the whole night as one ASLEEP stretch when the device didn't stage it. */
    fun stagesOf(n: SleepNight): List<SleepStage> =
        n.stages.ifEmpty { listOf(SleepStage(n.start, n.end, SleepStageKind.ASLEEP)) }

    fun stats(n: SleepNight, targetHours: Double = 8.0): Stats {
        val stages = stagesOf(n)
        val staged = n.stages.any { it.kind != SleepStageKind.ASLEEP }
        fun sum(k: SleepStageKind) = stages.filter { it.kind == k }.sumOf { mins(it) }
        val deep = sum(SleepStageKind.DEEP)
        val light = sum(SleepStageKind.LIGHT) + sum(SleepStageKind.ASLEEP).takeIf { staged }.let { it ?: 0.0 }
        val rem = sum(SleepStageKind.REM)
        val asleep = stages.filter { it.kind != SleepStageKind.AWAKE }.sumOf { mins(it) }
        val inBed = (n.end - n.start) / 60.0
        val firstSleep = stages.firstOrNull { it.kind != SleepStageKind.AWAKE }
        val lastSleep = stages.lastOrNull { it.kind != SleepStageKind.AWAKE }
        val latency = firstSleep?.let { (it.start - n.start) / 60.0 } ?: 0.0
        val inside = if (firstSleep != null && lastSleep != null) stages.filter { it.kind == SleepStageKind.AWAKE && it.start >= firstSleep.end && it.end <= lastSleep.start } else emptyList()
        val waso = inside.sumOf { mins(it) }
        val awakenings = inside.count { mins(it) >= 2 }
        val awake = max(0.0, inBed - asleep)
        val efficiency = if (inBed <= 0) 0.0 else asleep / inBed * 100

        val parts = mutableListOf<Pair<String, Int>>()
        // Duration, 40 points: 0 at 4 h, full at the target.
        parts += "Duration" to (((asleep / 60 - 4) / (targetHours - 4)).coerceIn(0.0, 1.0) * 40).roundToInt()
        // Efficiency, 20 points: 0 at 75 %, full at 90 % (85 %+ is "good" in sleep medicine).
        parts += "Efficiency" to (((efficiency - 75) / 15).coerceIn(0.0, 1.0) * 20).roundToInt()
        // Continuity, 10 points: full with WASO up to 20 min, 0 at 80 min.
        parts += "Continuity" to (((80 - waso) / 60).coerceIn(0.0, 1.0) * 10).roundToInt()
        if (staged && asleep > 0) {
            parts += "Deep sleep" to (rangeScore(deep / asleep * 100, DEEP_RANGE, 3.0, 35.0) * 15).roundToInt()
            parts += "REM sleep" to (rangeScore(rem / asleep * 100, REM_RANGE, 5.0, 40.0) * 15).roundToInt()
        }
        val max = if (staged && asleep > 0) 100 else 70
        val score = (parts.sumOf { it.second } * 100.0 / max).roundToInt().coerceIn(0, 100)
        return Stats(
            night = n,
            inBedMin = inBed.roundToInt(),
            asleepMin = asleep.roundToInt(),
            awakeMin = awake.roundToInt(),
            deepMin = deep.roundToInt(),
            lightMin = light.roundToInt(),
            remMin = rem.roundToInt(),
            efficiency = efficiency.roundToInt(),
            latencyMin = latency.roundToInt(),
            wasoMin = waso.roundToInt(),
            awakenings = awakenings,
            staged = staged,
            score = score,
            scoreParts = parts,
        )
    }

    /** 1 inside [range], falling linearly to 0 at [zeroLow] / [zeroHigh]. */
    private fun rangeScore(v: Double, range: ClosedFloatingPointRange<Double>, zeroLow: Double, zeroHigh: Double): Double = when {
        v in range -> 1.0
        v < range.start -> ((v - zeroLow) / (range.start - zeroLow)).coerceIn(0.0, 1.0)
        else -> ((zeroHigh - v) / (zeroHigh - range.endInclusive)).coerceIn(0.0, 1.0)
    }

    fun word(score: Int): String = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 55 -> "Fair"
        else -> "Poor"
    }

    fun clock(epoch: Long, offsetMinutes: Int): LocalTime =
        Instant.ofEpochSecond(epoch).atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toLocalTime()

    /**
     * Sleep Regularity Index (Phillips et al. 2017): the chance of being in the same state (asleep or
     * awake) at any two moments 24 h apart, scaled to -100..100 (100 = identical days). Computed on
     * 5-minute epochs over consecutive recorded nights; null with fewer than 5 back-to-back nights.
     */
    fun regularity(nights: List<SleepNight>, today: LocalDate, days: Int = 7): Int? {
        val recent = nights.filter { it.date > today.minusDays(days.toLong()).toString() && it.date <= today.toString() }.sortedBy { it.date }
        if (recent.size < 5) return null
        val dates = recent.map { LocalDate.parse(it.date) }
        if (dates.zipWithNext().any { (a, b) -> b != a.plusDays(1) }) return null
        val epoch = 300L
        val from = recent.first().start - 6 * 3600
        val to = recent.last().end
        val sleepAt = HashSet<Long>()
        recent.forEach { n ->
            stagesOf(n).filter { it.kind != SleepStageKind.AWAKE }.forEach { s ->
                var t = (s.start / epoch) * epoch
                while (t < s.end) { sleepAt += t / epoch; t += epoch }
            }
        }
        var same = 0
        var total = 0
        var t = (from / epoch) * epoch
        while (t + 86_400 <= to) {
            val a = (t / epoch) in sleepAt
            val b = ((t + 86_400) / epoch) in sleepAt
            if (a == b) same++
            total++
            t += epoch
        }
        if (total == 0) return null
        return (200.0 * same / total - 100).roundToInt()
    }

    /** Standard deviation of bedtimes over recent nights, minutes (lower = steadier). */
    fun bedtimeSpread(nights: List<SleepNight>, today: LocalDate, days: Int = 14): Int? {
        val recent = nights.filter { it.date > today.minusDays(days.toLong()).toString() && it.date <= today.toString() }
        if (recent.size < 4) return null
        // Minutes from noon, so 23:30 and 00:30 are an hour apart, not 23.
        val m = recent.map { n -> val c = clock(n.start, n.offsetMinutes); ((c.hour * 60 + c.minute) - 12 * 60 + 1440) % 1440 }
        val avg = m.average()
        return sqrt(m.sumOf { (it - avg) * (it - avg) } / m.size).roundToInt()
    }

    /** Sleep debt over the last [days] nights versus [targetHours] a night, minutes (0 = none). */
    fun debt(nights: List<SleepNight>, today: LocalDate, targetHours: Double = 7.5, days: Int = 7): Int {
        val recent = nights.filter { it.date > today.minusDays(days.toLong()).toString() && it.date <= today.toString() }
        if (recent.isEmpty()) return 0
        val short = recent.sumOf { n -> max(0.0, targetHours * 60 - stats(n).asleepMin) }
        val extra = recent.sumOf { n -> max(0.0, stats(n).asleepMin - targetHours * 60) }
        return max(0.0, short - min(extra, short / 2)).roundToInt() // oversleeping repays at most half
    }

    fun fmtMin(m: Int): String = if (m >= 60) "${m / 60}h ${abs(m % 60)}m" else "${m}m"
}
