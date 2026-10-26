package app.stride.wallet.domain

import app.stride.wallet.model.LoyaltyCard

/** The order cards are shown in (pure, unit tested): pinned first, then the last used, then the newest. */
object CardOrder {
    fun sorted(cards: List<LoyaltyCard>): List<LoyaltyCard> =
        cards.sortedWith(compareByDescending<LoyaltyCard> { it.pinned }.thenByDescending { it.lastUsed ?: "" }.thenByDescending { it.addedAt })

    /** What the home-screen widget shows: the first [n] in list order. */
    fun forWidget(cards: List<LoyaltyCard>, n: Int = 4): List<LoyaltyCard> = sorted(cards).take(n)
}
