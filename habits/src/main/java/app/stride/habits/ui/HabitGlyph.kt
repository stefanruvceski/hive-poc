package app.stride.habits.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.data.StrideJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One shape of an icon on a 24×24 grid (Streakli's shared icon set). */
sealed interface IconEl {
    data class Stroke(val path: Path) : IconEl
    data class Fill(val path: Path) : IconEl
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : IconEl
    data class Circle(val cx: Float, val cy: Float, val r: Float, val filled: Boolean) : IconEl
    data class RoundRect(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float) : IconEl
}

data class HabitIcon(val id: String, val label: String, val category: String, val keys: String, val els: List<IconEl>)

/** The built-in icons from assets/habit_icons.json, parsed once. */
object HabitIcons {
    val CATEGORIES = listOf("Fitness", "Health", "Mind", "Productivity", "Learning", "Lifestyle", "Avoid")
    @Volatile private var cache: List<HabitIcon>? = null

    fun all(context: Context): List<HabitIcon> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    fun byId(context: Context, id: String?): HabitIcon? = id?.let { i -> all(context).firstOrNull { it.id == i } }

    /** Matches id, label, category and keywords, case-insensitive. */
    fun search(context: Context, query: String): List<HabitIcon> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all(context)
        return all(context).filter { "${it.id} ${it.label} ${it.category} ${it.keys}".lowercase().contains(q) }
    }

    private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

    private fun load(context: Context): List<HabitIcon> = runCatching {
        val arr = StrideJson.parseToJsonElement(context.assets.open("habit_icons.json").bufferedReader().readText()).jsonArray
        arr.map { el ->
            val o = el.jsonObject
            fun s(k: String) = o[k]!!.jsonPrimitive.content
            HabitIcon(s("id"), s("label"), s("cat"), s("keys"), (o["e"] as JsonArray).mapNotNull { raw ->
                val t = raw.jsonArray
                fun f(i: Int) = t[i].jsonPrimitive.content.toFloat()
                when (t[0].jsonPrimitive.content) {
                    "p" -> IconEl.Stroke(svg(t[1].jsonPrimitive.content))
                    "P" -> IconEl.Fill(svg(t[1].jsonPrimitive.content))
                    "y" -> IconEl.Stroke(svg("M" + t[1].jsonPrimitive.content.trim().split(Regex("\\s+")).chunked(2).joinToString(" L") { it.joinToString(",") }))
                    "l" -> IconEl.Line(f(1), f(2), f(3), f(4))
                    "c" -> IconEl.Circle(f(1), f(2), f(3), false)
                    "C" -> IconEl.Circle(f(1), f(2), f(3), true)
                    "r" -> IconEl.RoundRect(f(1), f(2), f(3), f(4), f(5))
                    else -> null
                }
            })
        }
    }.getOrDefault(emptyList())
}

/** A habit's built-in icon in its color, or its emoji when it has none. */
@Composable
fun HabitGlyph(icon: String?, emoji: String, color: Color, sizeDp: Int = 24) {
    val context = LocalContext.current
    val ic = HabitIcons.byId(context, icon)
    if (ic == null) {
        Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) { Text(emoji, fontSize = (sizeDp * 0.8f).sp) }
        return
    }
    Canvas(Modifier.size(sizeDp.dp)) {
        val k = size.minDimension / 24f
        val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(k, k, pivot = Offset.Zero) {
            ic.els.forEach { e ->
                when (e) {
                    is IconEl.Stroke -> drawPath(e.path, color, style = stroke)
                    is IconEl.Fill -> drawPath(e.path, color)
                    is IconEl.Line -> drawLine(color, Offset(e.x1, e.y1), Offset(e.x2, e.y2), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    is IconEl.Circle -> if (e.filled) drawCircle(color, e.r, Offset(e.cx, e.cy)) else drawCircle(color, e.r, Offset(e.cx, e.cy), style = stroke)
                    is IconEl.RoundRect -> drawRoundRect(color, Offset(e.x, e.y), Size(e.w, e.h), CornerRadius(e.rx), style = stroke)
                }
            }
        }
    }
}
