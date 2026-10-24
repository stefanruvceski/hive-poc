package app.stride.money.domain

import app.stride.data.StrideJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Rate provider URLs and response parsing (pure, unit tested); [app.stride.money.data.FxClient] fetches. */
object FxParse {
    fun generalUrls(base: String, date: String? = null): List<String> {
        val b = base.lowercase()
        val v = if (date != null && date.length == 10) date else "latest"
        return listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$v/v1/currencies/$b.json",
            "https://$v.currency-api.pages.dev/v1/currencies/$b.json",
        )
    }

    fun nbsUrl(date: String? = null): String = "https://kurs.resenje.org/api/v1/rates/${if (date != null && date.length == 10) date else "today"}"

    /** currency-api gives code-per-base; we want base-per-code. */
    fun general(base: String, codes: List<String>, body: String): Map<String, Double> {
        val table = runCatching { StrideJson.parseToJsonElement(body).jsonObject[base.lowercase()]?.jsonObject }.getOrNull() ?: return emptyMap()
        return codes.filter { it != base }.mapNotNull { c ->
            table[c.lowercase()]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 }?.let { c to 1 / it }
        }.toMap()
    }

    /** NBS quotes RSD per [parity] units; returns base-per-code, cross-computed through RSD. */
    fun nbs(base: String, codes: List<String>, body: String): Map<String, Double> {
        val rows = runCatching { (StrideJson.parseToJsonElement(body) as JsonObject)["rates"]?.jsonArray }.getOrNull() ?: return emptyMap()
        val rsd = mutableMapOf("RSD" to 1.0)
        rows.forEach { r ->
            val o = r.jsonObject
            val code = o["code"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return@forEach
            val mid = o["exchange_middle"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
            val parity = o["parity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 } ?: 1.0
            if (mid > 0) rsd[code] = mid / parity
        }
        val baseRsd = rsd[base.uppercase()] ?: return emptyMap()
        return codes.filter { it != base }.mapNotNull { c -> rsd[c.uppercase()]?.let { c to it / baseRsd } }.toMap()
    }
}
