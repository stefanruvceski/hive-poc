package app.stride

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.domain.Analyzer
import app.stride.domain.DayStatus
import app.stride.domain.mondayOf
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.reminder.PlanReminder
import app.stride.ui.AppViewModel
import app.stride.ui.StrideApp
import app.stride.ui.kindIcon
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Training and health: everything Stride did so far, as the first app inside the host. */
object HealthModule : AppModule {
    override val id = "health"
    override val title = "Health"
    override val subtitle = "Training, recovery, sleep and weight"
    override val icon: ImageVector = Icons.Filled.Favorite
    override val accent = StrideColors.Accent

    override fun onAppStart(context: Context) {
        PlanReminder.schedule(context)
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        StrideApp(openRequest = open, onHome = onHome)
    }

    /** Today's plan and how the week is going. */
    @Composable
    override fun HomeSummary() {
        val vm: AppViewModel = viewModel()
        val data by vm.data.collectAsStateWithLifecycle()
        val today = LocalDate.now()
        val day = remember(data) { Analyzer.day(today, today, data) }
        val week = remember(data) { Analyzer.week(mondayOf(today), today, data).summary }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(day.plan?.kind), null, tint = StrideColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val status = when (day.status) {
                    DayStatus.DONE -> " · done ✓"
                    DayStatus.TODAY -> day.plan?.time?.let { " · $it" } ?: ""
                    else -> ""
                }
                Text("Today: ${day.plan?.title ?: "rest"}$status", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1)
            }
            Text(
                "${week.done} of ${week.planned} workouts this week",
                style = MaterialTheme.typography.bodySmall,
                color = StrideColors.Dim,
                modifier = Modifier.padding(start = 26.dp),
            )
        }
    }
}
