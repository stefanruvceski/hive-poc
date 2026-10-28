package app.stride.invest

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.invest.domain.Portfolio
import app.stride.invest.ui.InvestAccent
import app.stride.invest.ui.InvestApp
import app.stride.invest.ui.InvestViewModel
import app.stride.invest.ui.money
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Interactive Brokers, read-only (Flex reports), and an investing plan with reminders. Everything stays on the phone. */
object InvestModule : AppModule {
    override val id = "invest"
    override val title = "Invest"
    override val subtitle = "Interactive Brokers portfolio and your investing plan"
    override val icon: ImageVector = Icons.AutoMirrored.Filled.TrendingUp
    override val accent = InvestAccent
    override val dataFiles = listOf("invest.json")

    override fun onAppStart(context: android.content.Context) {
        app.stride.invest.reminder.InvestReminder.schedule(context)
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        InvestApp(open, onHome)
    }

    /** The account's value and this period's plan progress. */
    @Composable
    override fun HomeSummary() {
        val vm: InvestViewModel = viewModel()
        val d by vm.data.collectAsStateWithLifecycle()
        val s = d.snapshot
        Column {
            Text(
                s?.nav?.let { "${money(it, 0)} ${s.baseCurrency}" } ?: "Connect Interactive Brokers",
                style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1,
            )
            if (d.plan.amount > 0) {
                val p = Portfolio.current(d, LocalDate.now())
                Text(
                    "${Portfolio.periodLabel(p.start, d.plan.every)}: ${money(p.invested, 0)} of ${money(p.target, 0)} ${d.plan.currency}",
                    style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim,
                )
                ThinBar(p.progress, accent)
            }
        }
    }
}
