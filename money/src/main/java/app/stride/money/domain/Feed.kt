package app.stride.money.domain

import app.stride.finance.FeedExpense
import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.MoneyData

/**
 * Spending from other modules (Wallet's receipts) inside Money (pure, unit tested): their kinds are filed under
 * Money's categories once (a category of the same name, else a new one), then they count like typed expenses in
 * budgets, reports and lists. They're never stored in money.json; Wallet stays their owner.
 */
object Feed {
    /** Names that mean the same kind, so "FUEL" lands in an existing "Car" or "Gorivo" category. */
    private val SYNONYMS = mapOf(
        "FUEL" to listOf("fuel", "gorivo", "gas", "petrol", "car", "auto", "transport"),
        "GROCERIES" to listOf("groceries", "namirnice", "food", "hrana", "supermarket"),
        "FOOD" to listOf("food & drinks", "restaurants", "eating out", "restorani", "kafane", "cafe"),
        "TECH" to listOf("tech", "tehnika", "electronics", "gadgets"),
        "HOME" to listOf("home & garden", "home", "kuća", "kuca", "stan", "household"),
        "CLOTHES" to listOf("clothes & shoes", "clothes", "odeća", "odeca", "shopping"),
        "SPORT" to listOf("sport", "sports", "fitness", "gym"),
        "HEALTH" to listOf("pharmacy & beauty", "health", "zdravlje", "apoteka", "pharmacy", "beauty"),
        "KIDS" to listOf("kids", "deca", "children", "baby"),
        "PETS" to listOf("pets", "ljubimci", "pet"),
        "BOOKS" to listOf("books", "knjige"),
        "OTHER" to listOf("other", "ostalo", "misc"),
    )

    /**
     * Files every kind in [feed] that has no category yet: a category with a matching name (or synonym) when there is
     * one, else a new category with the kind's name and color. Returns [d] unchanged when nothing is new.
     */
    fun mapKinds(d: MoneyData, feed: List<FeedExpense>, newId: () -> String, now: String = ""): MoneyData {
        val active = d.categories.filter { !it.archived }
        val kinds = feed.map { it.kind }.distinct().filter { k -> d.kindMap[k]?.let { id -> active.any { it.id == id } } != true }
        if (kinds.isEmpty()) return d
        var cats = d.categories
        val map = d.kindMap.toMutableMap()
        kinds.forEach { kind ->
            val sample = feed.first { it.kind == kind }
            val names = (SYNONYMS[kind].orEmpty() + sample.kindLabel.lowercase()).toSet()
            val hit = cats.filter { !it.archived }.firstOrNull { it.name.trim().lowercase() in names }
            val id = hit?.id ?: newId().also { id ->
                cats = cats + ExpenseCategory(id, sample.kindLabel, sample.kindColor, order = (cats.maxOfOrNull { it.order } ?: -1) + 1, createdAt = now)
            }
            map[kind] = id
        }
        return d.copy(categories = cats, kindMap = map)
    }

    /** The feed as Money expenses (a category picked on the receipt wins over the kind's). */
    fun expenses(d: MoneyData, feed: List<FeedExpense>): List<Expense> {
        val ids = d.categories.map { it.id }.toSet()
        return feed.map { f ->
            Expense(
                id = f.id, categoryId = f.categoryId?.takeIf { it in ids } ?: d.kindMap[f.kind].orEmpty(), date = f.date,
                amount = f.amount, currency = f.currency, note = f.label, company = f.company, open = f.open,
            )
        }
    }

    /** Money as the screens see it: typed expenses plus the feed (when receipts are counted). */
    fun view(d: MoneyData, feed: List<FeedExpense>): MoneyData =
        if (!d.settings.receipts || feed.isEmpty()) d else d.copy(expenses = d.expenses + expenses(d, feed))

    // ---------------------------------------------------------------- business or personal

    const val ALL = "all"
    const val PERSONAL = "personal"

    /** Companies that have spending, most used first. */
    fun companies(d: MoneyData): List<String> =
        d.expenses.mapNotNull { it.company?.takeIf { c -> c.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Only the spending for [scope] ([ALL], [PERSONAL] or a company name); income is left as it is. */
    fun scoped(d: MoneyData, scope: String): MoneyData = when (scope) {
        ALL -> d
        PERSONAL -> d.copy(expenses = d.expenses.filter { it.company.isNullOrBlank() })
        else -> d.copy(expenses = d.expenses.filter { it.company == scope })
    }
}
