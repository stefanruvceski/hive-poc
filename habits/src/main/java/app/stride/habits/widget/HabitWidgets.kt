package app.stride.habits.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import app.stride.data.DataEvents
import app.stride.habits.data.HabitStore
import app.stride.habits.domain.HabitStats
import app.stride.habits.domain.Routine
import app.stride.habits.model.HabitsData
import app.stride.ui.theme.StrideColors
import app.stride.widget.Frame
import app.stride.widget.Kicker
import app.stride.widget.WidgetArt
import app.stride.widget.density
import app.stride.widget.scale
import app.stride.widget.sp
import app.stride.widget.style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Redraws the Habits widgets after a change. Never throws. */
object HabitWidgets {
    suspend fun refresh(context: Context) {
        listOf(HabitsTodayWidget(), HabitsProgressWidget()).forEach { runCatching { it.updateAll(context) } }
    }
}

private suspend fun load(context: Context): HabitsData = withContext(Dispatchers.IO) { HabitStore(context).load() }

/** One row of today's list in a widget. */
data class WidgetRow(val id: String, val name: String, val color: Long, val done: Boolean, val detail: String?, val measurable: Boolean)

/** Today's habits by part of the day, for widgets (pure). */
fun todayRows(d: HabitsData, date: LocalDate): List<Pair<String, List<WidgetRow>>> =
    Routine.groups(d.active.filter { HabitStats.isScheduled(it, date) }).map { (t, list) ->
        "${t.emoji} ${t.label}" to list.map { h ->
            val amount = HabitStats.amountOn(d.entries, h, date)
            val detail = if (!h.measurable) null else h.target?.let { "${Routine.formatAmount(amount)} / ${Routine.formatAmount(it)} ${h.unit}".trim() }
                ?: if (amount > 0) "${Routine.formatAmount(amount)} ${h.unit}".trim() else "log"
            WidgetRow(h.id, h.name, h.color, HabitStats.isDone(d.entries, h, date), detail, h.measurable)
        }
    }

private val HabitKey = ActionParameters.Key<String>("habit")

/** Ticks a yes/no habit from the widget without opening the app. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitKey] ?: return
        val today = LocalDate.now().toString()
        withContext(Dispatchers.IO) {
            HabitStore(context).update { d ->
                val day = d.entries[today].orEmpty()
                val next = if (day.containsKey(id)) day - id else day + (id to 1.0)
                d.copy(entries = if (next.isEmpty()) d.entries - today else d.entries + (today to next))
            }
        }
        DataEvents.notifyChanged()
        HabitWidgets.refresh(context)
    }
}

// ---------------------------------------------------------------- Today list (4x2 and up)

class HabitsTodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = load(context)
        val today = LocalDate.now()
        val rows = todayRows(d, today)
        val c = HabitStats.dayCounts(d, today)
        provideContent { TodayContent(rows, c.done, c.total) }
    }
}

class HabitsTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsTodayWidget()
}

@Composable
private fun TodayContent(sections: List<Pair<String, List<WidgetRow>>>, done: Int, total: Int) {
    val k = scale(250f, 180f)
    Frame(open = "habits/today", padding = 12) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Habits", style = style(StrideColors.Text, sp(16f, k), bold = true), modifier = GlanceModifier.defaultWeight())
            Text("$done/$total", style = style(if (total > 0 && done == total) StrideColors.Accent else StrideColors.Dim, sp(13f, k), bold = true))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (sections.isEmpty()) {
            Text("No habits scheduled today", style = style(StrideColors.Faint, sp(13f, k)))
            return@Frame
        }
        LazyColumn {
            sections.forEach { (title, rows) ->
                item { Text(title, style = style(StrideColors.Dim, sp(11.5f, k), bold = true), modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp)) }
                items(rows, itemId = { it.id.hashCode().toLong() }) { r ->
                    val c = Color(r.color)
                    val rowMod = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).let {
                        if (r.measurable) it else it.clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HabitKey to r.id)))
                    }
                    Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.size((20 * k).dp).cornerRadius(6.dp).background(if (r.done) c else StrideColors.Elev2),
                            contentAlignment = Alignment.Center,
                        ) { if (r.done) Text("✓", style = style(StrideColors.Bg, sp(12f, k), bold = true, align = TextAlign.Center)) }
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(r.name, style = style(StrideColors.Text, sp(13f, k), bold = true), maxLines = 1)
                            if (r.detail != null) Text(r.detail, style = style(StrideColors.Dim, sp(10.5f, k)), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Progress ring (2x2)

class HabitsProgressWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = load(context)
        val today = LocalDate.now()
        val c = HabitStats.dayCounts(d, today)
        val best = d.active.maxByOrNull { HabitStats.currentStreak(d.entries, it, today) }
        val streak = best?.let { HabitStats.currentStreak(d.entries, it, today) } ?: 0
        val open = todayRows(d, today).firstOrNull { (_, rows) -> rows.any { !it.done } }
        val next = when {
            open != null -> "${open.first} · ${open.second.count { !it.done }} left"
            c.total > 0 -> "All done 🎉"
            else -> null
        }
        val bmp = WidgetArt.rings(listOf(0xFF34D399.toInt() to c.progress.toFloat()), (96 * density(context)).toInt())
        provideContent { ProgressContent(bmp, c.done, c.total, streak, best?.name, next) }
    }
}

class HabitsProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsProgressWidget()
}

@Composable
private fun ProgressContent(bmp: android.graphics.Bitmap, done: Int, total: Int, streak: Int, streakName: String?, next: String?) {
    val k = scale(150f, 150f)
    Frame(open = "habits/today", padding = 12) {
        Kicker("Habits today", k)
        Spacer(GlanceModifier.height(4.dp))
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Image(ImageProvider(bmp), "Today's progress", GlanceModifier.size((80 * k).dp))
            Text("$done/$total", style = style(StrideColors.Text, sp(16f, k), bold = true, align = TextAlign.Center))
        }
        if (streak > 0 && streakName != null) Text("🔥 $streak · $streakName", style = style(StrideColors.Amber, sp(11f, k), bold = true), maxLines = 1)
        if (next != null) Text(next, style = style(StrideColors.Dim, sp(10.5f, k)), maxLines = 1)
    }
}
