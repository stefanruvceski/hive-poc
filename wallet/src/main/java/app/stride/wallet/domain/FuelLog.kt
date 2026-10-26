package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import java.time.LocalDate
import java.time.YearMonth

/** One fill-up: from a fuel receipt, with the odometer if the user typed it. */
data class Fill(val receiptId: String, val date: LocalDate, val station: String, val fuel: String, val liters: Double, val pricePerLiter: Double, val amount: Double, val odometer: Int?)

/** A station's average price per liter over the fills there. */
data class StationPrice(val station: String, val pricePerLiter: Double, val fills: Int)

/** Consumption between two odometer readings (fill-to-full: the later fill's liters over the km driven). */
data class Consumption(val from: LocalDate, val to: LocalDate, val km: Int, val per100: Double)

/**
 * The fuel diary built from fuel receipts (pure, unit tested): price per liter over time, where it was cheapest
 * and, with odometer readings, liters per 100 km.
 */
object FuelLog {
    fun fills(rs: List<Receipt>): List<Fill> = rs.filter { it.status == ReceiptStatus.OK }.mapNotNull { r ->
        val f = ReceiptStats.fuel(r) ?: return@mapNotNull null
        val d = ReceiptStats.date(r).takeIf { it != LocalDate.MIN } ?: return@mapNotNull null
        Fill(r.id, d, station(r), f.name, f.liters, f.pricePerLiter, f.amount, r.odometer)
    }.sortedBy { it.date }

    /** "NIS · Bs Novi Sad 1". */
    fun station(r: Receipt): String {
        val brand = Brands.title(r)
        val shop = Brands.shopName(r.shop)
        return if (shop == null || shop == brand) brand else "$brand · $shop"
    }

    /** Average price per liter per month (weighted by liters), oldest first. */
    fun monthlyPrice(fills: List<Fill>): List<Pair<YearMonth, Double>> =
        fills.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, f) -> m to f.sumOf { it.amount } / f.sumOf { it.liters } }

    /** Stations by average price per liter, cheapest first. */
    fun stations(fills: List<Fill>): List<StationPrice> =
        fills.groupBy { it.station }.map { (s, f) -> StationPrice(s, f.sumOf { it.amount } / f.sumOf { it.liters }, f.size) }
            .sortedBy { it.pricePerLiter }

    /**
     * L/100 km between consecutive odometer readings: the liters bought at the later reading (and at fills without a
     * reading in between) over the km driven since the earlier one. Implausible stretches (under 20 km, over 3,000 km,
     * or outside 2–40 L/100 km) are dropped.
     */
    fun consumption(fills: List<Fill>): List<Consumption> {
        val out = mutableListOf<Consumption>()
        var last: Fill? = null
        var liters = 0.0
        for (f in fills) {
            if (last != null) liters += f.liters
            if (f.odometer == null) continue
            val prev = last
            if (prev?.odometer != null) {
                val km = f.odometer - prev.odometer
                val per100 = if (km > 0) liters / km * 100 else 0.0
                if (km in 20..3000 && per100 in 2.0..40.0) out += Consumption(prev.date, f.date, km, per100)
            }
            last = f
            liters = 0.0
        }
        return out
    }

    /** Liters and money in a year. */
    fun yearTotals(fills: List<Fill>, year: Int): Pair<Double, Double> =
        fills.filter { it.date.year == year }.let { f -> f.sumOf { it.liters } to f.sumOf { it.amount } }
}
