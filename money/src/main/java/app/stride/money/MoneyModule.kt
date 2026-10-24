package app.stride.money

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.money.domain.Finance
import app.stride.money.ui.MoneyApp
import app.stride.money.ui.MoneyViewModel
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.YearMonth

/** Income, spending and budgets: Streakli's Finance as its own app in Hive. Everything stays on the phone. */
object MoneyModule : AppModule {
    override val id = "money"
    override val title = "Money"
    override val subtitle = "Income, spending, budgets and a yearly goal"
    override val icon: ImageVector = Icons.Filled.AccountBalanceWallet
    override val accent = Color(0xFF60A5FA)

    override fun onAppStart(context: android.content.Context) {
        app.stride.money.reminder.MoneyReminder.schedule(context)
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        MoneyApp(open, onHome)
    }

    /** This year's income against the goal, and this month's spending. */
    @Composable
    override fun HomeSummary() {
        val vm: MoneyViewModel = viewModel()
        val d by vm.data.collectAsStateWithLifecycle()
        val today = LocalDate.now()
        val paid = Finance.yearTotal(d, today.year)
        val spent = Finance.monthExpenseTotal(d, YearMonth.from(today))
        Column {
            Text(
                "${Finance.money(paid, d.baseCurrency)} received in ${today.year}" + if (d.goal.target > 0) " · ${(paid / d.goal.target * 100).toInt()}% of goal" else "",
                style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1,
            )
            Text("${Finance.money(spent, d.baseCurrency)} spent this month", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            if (d.goal.target > 0) ThinBar((paid / d.goal.target).toFloat(), accent)
        }
    }
}
