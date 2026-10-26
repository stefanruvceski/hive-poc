package app.stride.habits

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.habits.domain.HabitStats
import app.stride.habits.reminder.HabitReminder
import app.stride.habits.ui.HabitsApp
import app.stride.habits.ui.HabitsViewModel
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Habits, to-dos and mood: Streakli, moved into Hive. Everything stays on the phone. */
object HabitsModule : AppModule {
    override val id = "habits"
    override val title = "Habits"
    override val subtitle = "Daily habits, to-dos, streaks and mood"
    override val icon: ImageVector = Icons.Filled.TaskAlt
    override val accent = Color(0xFFFACC15)
    override val dataFiles = listOf("habits.json")

    override fun onAppStart(context: Context) {
        HabitReminder.schedule(context)
    }

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        HabitsApp(open, onHome)
    }

    /** Today's progress and the best running streak. */
    @Composable
    override fun HomeSummary() {
        val vm: HabitsViewModel = viewModel()
        val d by vm.data.collectAsStateWithLifecycle()
        val today = LocalDate.now()
        val c = HabitStats.dayCounts(d, today)
        val best = d.active.maxOfOrNull { HabitStats.currentStreak(d.entries, it, today) } ?: 0
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (c.total == 0) "Nothing planned today" else if (c.done == c.total) "Day closed ✓ ${c.done} of ${c.total}" else "${c.done} of ${c.total} done today",
                    style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, modifier = Modifier.weight(1f),
                )
                if (best > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("🔥 $best", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Amber)
                }
            }
            if (c.total > 0) ThinBar(c.progress.toFloat(), accent, modifier = Modifier)
        }
    }
}
