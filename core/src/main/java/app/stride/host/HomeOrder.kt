package app.stride.host

/**
 * The user's order and hidden apps on the home screen (pure, unit tested). Ids are module ids;
 * apps added later go to the end, unknown ids are ignored, and at least one app stays visible.
 */
data class HomeOrder(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    fun sorted(ids: List<String>): List<String> {
        val known = order.filter { it in ids }
        return known + ids.filter { it !in known }
    }

    fun visible(ids: List<String>): List<String> = sorted(ids).filter { it !in hidden }.ifEmpty { sorted(ids).take(1) }

    fun move(ids: List<String>, id: String, by: Int): HomeOrder {
        val list = sorted(ids).toMutableList()
        val i = list.indexOf(id)
        if (i < 0) return this
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i == j) return this
        list.add(j, list.removeAt(i))
        return copy(order = list)
    }

    fun toggle(ids: List<String>, id: String): HomeOrder {
        val next = if (id in hidden) hidden - id else hidden + id
        return if (ids.all { it in next }) this else copy(hidden = next)
    }

    /** "a,b,c|x,y" for SharedPreferences. */
    fun encode(): String = order.joinToString(",") + "|" + hidden.joinToString(",")

    companion object {
        fun decode(s: String?): HomeOrder {
            if (s.isNullOrBlank()) return HomeOrder()
            val (o, h) = (s.split("|") + "").let { it[0] to it[1] }
            return HomeOrder(o.split(",").filter { it.isNotBlank() }, h.split(",").filter { it.isNotBlank() }.toSet())
        }
    }
}
