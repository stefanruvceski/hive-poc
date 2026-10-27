package app.stride.wallet.domain

import app.stride.wallet.model.AliasGroup
import app.stride.wallet.model.Receipt

/** Merging by hand: keys that are one product or one shop, with an optional chosen name (pure, tested). */
object Aliases {
    fun group(groups: List<AliasGroup>, key: String): AliasGroup? = groups.firstOrNull { key in it.keys }

    /** The key everything merged with [key] is filed under. */
    fun canonical(groups: List<AliasGroup>, key: String): String = group(groups, key)?.keys?.first() ?: key

    /** [b] (and everything merged with it) joins [a]; [a]'s name and key stay. */
    fun merge(groups: List<AliasGroup>, a: String, b: String): List<AliasGroup> {
        val ga = group(groups, a)
        val gb = group(groups, b)
        if (ga != null && ga === gb) return groups
        val keys = ((ga?.keys ?: listOf(a)) + (gb?.keys ?: listOf(b))).distinct()
        return groups.filter { it !== ga && it !== gb } + AliasGroup(keys, ga?.name ?: gb?.name)
    }

    /** Sets (or clears, with a blank name) the name shown for [key]'s group. */
    fun rename(groups: List<AliasGroup>, key: String, name: String?): List<AliasGroup> {
        val n = name?.trim()?.ifEmpty { null }
        val g = group(groups, key) ?: return if (n == null) groups else groups + AliasGroup(listOf(key), n)
        return groups.map { if (it === g) it.copy(name = n) else it }.filter { it.keys.size > 1 || it.name != null }
    }

    /** Splits [key]'s group back into separate keys (a chosen name is dropped with it). */
    fun unmerge(groups: List<AliasGroup>, key: String): List<AliasGroup> {
        val g = group(groups, key) ?: return groups
        return groups.filter { it !== g }
    }

    fun pair(a: String, b: String): String = listOf(a, b).sorted().joinToString("|")
}

/**
 * Shops by company, not branch: a known chain by its name (every NIS is "NIS"), anyone else by the company's tax
 * number (PIB), which is the same at every branch; the name only when there's no PIB. Hand merges go on top.
 */
object Chains {
    fun key(r: Receipt): String = Brands.of(r)?.name ?: r.tin?.trim()?.takeIf { it.isNotEmpty() }?.let { "pib:$it" } ?: Brands.title(r)

    /** (key, name) for a receipt's shop with the user's merges and names applied. */
    fun of(r: Receipt, groups: List<AliasGroup>): Pair<String, String> {
        val k = Aliases.canonical(groups, key(r))
        return k to (Aliases.group(groups, k)?.name ?: Brands.title(r))
    }
}
