package app.stride.wallet.domain

import app.stride.wallet.model.Company
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import kotlin.math.abs

/** What an import found: the new receipts, the groups as companies, and how many rows were already here. */
data class ManiResult(val receipts: List<Receipt>, val companies: List<Company>, val duplicates: Int, val skipped: Int)

/**
 * Receipts exported from the Mani app (CSV, pure, unit tested). Mani's export has
 * `Tip, Datum i vreme, Naziv kompanije, Ime prodajnog mesta, Ukupna cena, Grupa, Korisnik, ID Korisnika`:
 * no QR link and no items, so an imported receipt is the date, company, shop and total. A Mani group
 * ("Obsidian IT") becomes a company; scanning the same receipt later replaces the import with the full one.
 */
object ManiImport {
    /** Company key for a Mani group until the user ties it to a real PIB. */
    const val GROUP = "group:"

    fun isGroup(pib: String) = pib.startsWith(GROUP)

    /** True when [text] looks like Mani's export (its header). */
    fun looksLikeMani(text: String): Boolean {
        val head = text.trimStart('﻿').lineSequence().firstOrNull().orEmpty().lowercase()
        return "datum i vreme" in head && "ukupna cena" in head
    }

    /**
     * Reads the CSV into new receipts, leaving out those already in [existing] (same minute and total).
     * [newId] makes ids; [now] is the import time.
     */
    fun parse(text: String, existing: List<Receipt>, newId: () -> String, now: String): ManiResult? {
        val rows = csv(text.trimStart('﻿'))
        if (rows.isEmpty()) return null
        val head = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = head.indexOfFirst { h -> names.any { h == it } }
        val iTime = col("datum i vreme", "datum")
        val iCompany = col("naziv kompanije", "kompanija")
        val iShop = col("ime prodajnog mesta", "prodajno mesto")
        val iTotal = col("ukupna cena", "iznos")
        val iGroup = col("grupa")
        val iType = col("tip")
        if (iTime < 0 || iTotal < 0) return null

        val out = mutableListOf<Receipt>()
        val groups = linkedSetOf<String>()
        var dup = 0
        var skipped = 0
        for (r in rows.drop(1)) {
            if (r.all { it.isBlank() }) continue
            fun at(i: Int) = r.getOrNull(i)?.trim()?.takeIf { i >= 0 && it.isNotEmpty() && it != "-" }
            val time = at(iTime)?.let(FiscalParse::printedTime)
            val total = at(iTotal)?.let { if (it.contains(',')) FiscalParse.number(it) else it.toDoubleOrNull() }
            if (time == null || total == null) { skipped++; continue }
            val refund = at(iType)?.lowercase()?.contains("refund") == true
            val amount = if (refund) -abs(total) else total
            val candidate = Receipt(
                id = "", url = "", status = ReceiptStatus.OK,
                company = at(iCompany), shop = at(iShop), total = amount, time = time, refund = refund,
                buyerTin = at(iGroup)?.let { GROUP + it }, source = "mani", addedAt = now,
            )
            if ((existing + out).any { same(it, candidate) }) { dup++; continue }
            at(iGroup)?.let(groups::add)
            out += candidate.copy(id = newId())
        }
        return ManiResult(out, groups.map { Company(GROUP + it, it) }, dup, skipped)
    }

    /** The same purchase: same minute and the same total (a re-import, or a scan of an imported receipt). */
    fun same(a: Receipt, b: Receipt): Boolean {
        val ta = a.time?.take(16) ?: return false
        val tb = b.time?.take(16) ?: return false
        val x = a.total ?: return false
        val y = b.total ?: return false
        return ta == tb && abs(x - y) < 0.005
    }

    /** RFC 4180 CSV: commas, quoted fields with commas, doubled quotes and line breaks inside. */
    fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val f = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { f.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += f.toString(); f.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += f.toString(); f.clear(); rows += row; row = mutableListOf()
                }
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row += f.toString(); rows += row }
        return rows
    }
}
