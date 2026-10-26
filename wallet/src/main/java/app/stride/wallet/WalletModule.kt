package app.stride.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.domain.Warranties
import app.stride.wallet.domain.WarrantyState
import app.stride.wallet.ui.WalletAccent
import app.stride.wallet.ui.WalletApp
import app.stride.wallet.ui.WalletViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The papers and cards in your wallet: fiscal receipts (items, warranties, fuel, business or personal) and loyalty
 * cards. The numbers (spending, budgets, reports) are Money's: receipts reach it through [app.stride.finance.FinanceHub].
 */
object WalletModule : AppModule {
    override val id = "wallet"
    override val title = "Wallet"
    override val subtitle = "Receipts, warranties and loyalty cards"
    override val icon: ImageVector = Icons.Filled.AccountBalanceWallet
    override val accent = WalletAccent
    override val dataFiles = listOf("wallet.json", "wallet_cards")

    override fun onAppStart(context: android.content.Context) {
        // Money counts the receipts as spending.
        app.stride.finance.FinanceHub.spending = app.stride.wallet.data.WalletSpending
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        WalletApp(open, onHome)
    }

    /** Receipts this month, cards and warranties ending (the money itself is on Money's card). */
    @Composable
    override fun HomeSummary() {
        val vm: WalletViewModel = viewModel()
        val d by vm.data.collectAsStateWithLifecycle()
        val m = YearMonth.now()
        val month = ReceiptStats.inMonth(d.receipts, m)
        val ending = Warranties.all(d.receipts, LocalDate.now()).count { it.state == WarrantyState.ENDING }
        Column {
            Text(
                if (month.isEmpty()) "Scan a receipt's QR code" else "${month.size} receipt${if (month.size == 1) "" else "s"} in ${m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
                style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1,
            )
            Text(
                "${d.cards.size} card${if (d.cards.size == 1) "" else "s"}" + if (ending > 0) " · $ending warrant${if (ending == 1) "y" else "ies"} ending" else "",
                style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim,
            )
        }
    }
}
