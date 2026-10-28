package app.stride.invest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors

/** Broker words in one or two plain sentences, behind the small "?" next to them. */
object Glossary {
    val TERMS: Map<String, Pair<String, String>> = mapOf(
        "value" to ("Account value" to "Everything in the account: your positions at the latest price plus cash, in your base currency. IBKR calls it NAV (net asset value)."),
        "putin" to ("Put in" to "Money you deposited into Interactive Brokers, minus anything you withdrew. It's your own money, not a gain."),
        "gain" to ("Market gain" to "How much the value changed beyond the money you put in. Deposits are taken out, so this is only what the market did."),
        "return" to ("Return %" to "The market gain divided by the money that was invested during the period. A deposit made halfway through counts about half (the Modified Dietz method), so the number compares with what funds report."),
        "avg" to ("Average price" to "What you paid per unit on average, commissions included. IBKR calls it the cost basis price."),
        "cost" to ("Cost" to "What you paid in total for the units you still hold (cost basis)."),
        "unrealized" to ("Gain (not sold)" to "Value now minus what you paid. It's on paper: it only becomes a real (realized) gain or loss when you sell."),
        "realized" to ("Realized gain" to "The gain or loss locked in by a sale: sale price minus what those units cost."),
        "share" to ("Share of the account" to "How much of the account's value this position is. A big share means your result depends a lot on it."),
        "mix" to ("Mix" to "How the account is spread. IBKR lists ETFs under stocks. The currency view shows how much moves with each currency's exchange rate."),
        "cash" to ("Cash" to "Money in the account that isn't invested yet. A deposit is usually meant to be invested, so Hive points it out when it waits."),
        "withholding" to ("Tax withheld" to "The part of a dividend the paying company's country keeps before the money reaches you. It's taken automatically; you see the dividend after it."),
        "accruals" to ("Coming dividends" to "Dividends already declared whose ex-date has passed, so they're yours: the money arrives on the pay date."),
        "costs" to ("Costs" to "Commissions IBKR charged per trade plus other fees (e.g. market data). Interest you paid is not included."),
    )
}

/** A small "?" that explains [term] (a key in [Glossary.TERMS]). */
@Composable
fun InfoDot(term: String, modifier: Modifier = Modifier) {
    val t = Glossary.TERMS[term] ?: return
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.padding(start = 6.dp).size(18.dp).clip(CircleShape).background(StrideColors.Elev2).clickable { open = true },
        contentAlignment = Alignment.Center,
    ) { Text("?", color = StrideColors.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        title = { Text(t.first) },
        text = { Text(t.second) },
    )
}
