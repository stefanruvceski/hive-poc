package app.stride.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.stride.core.R
import app.stride.domain.YearStats
import app.stride.domain.fmtDur
import app.stride.domain.fmtHours
import java.util.Locale

/** The four story cards of Year in review (1080x1920), same look as the workout share images. */
object YearCards {
    private const val W = 1080
    private const val H = 1920
    private const val BG = 0xFF0A0F1E.toInt()
    private const val TEXT = 0xFFEEF2FF.toInt()
    private const val DIM = 0xFF9FB0D0.toInt()
    private const val EMERALD = 0xFF34D399.toInt()
    private const val CYAN = 0xFF22D3EE.toInt()
    private const val PINK = 0xFFF472B6.toInt()
    private const val AMBER = 0xFFF59E0B.toInt()
    private const val VIOLET = 0xFFA78BFA.toInt()

    private class Fonts(ctx: Context) {
        val bebas: Typeface = ResourcesCompat.getFont(ctx, R.font.bebas_neue) ?: Typeface.DEFAULT_BOLD
        val semi: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_semibold) ?: Typeface.DEFAULT
        val xbold: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_extrabold) ?: Typeface.DEFAULT_BOLD
        val black: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_black) ?: Typeface.DEFAULT_BOLD
    }

    private fun num(v: Double, dec: Int = 0) = if (dec == 0) String.format(Locale.US, "%,d", Math.round(v)) else String.format(Locale.US, "%,.${dec}f", v)

    private fun Canvas.t(s: String, x: Float, y: Float, tf: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) {
        drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; textAlign = align; letterSpacing = spacing / size })
    }

    private fun base(accent: Int, accent2: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        fun glow(x: Float, y: Float, r: Float, col: Int) = c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(x, y, r, intArrayOf((0x55 shl 24) or (col and 0xFFFFFF), col and 0xFFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        glow(900f, 200f, 1100f, accent)
        glow(100f, 1800f, 1000f, accent2)
        return bmp to c
    }

    private fun Canvas.header(f: Fonts, year: Int, page: Int, kicker: String, accent: Int) {
        t("STRIDE · $year", 80f, 150f, f.black, 30f, TEXT, spacing = 8f)
        t("$page / 4", W - 80f, 150f, f.xbold, 28f, DIM, Paint.Align.RIGHT)
        t(kicker.uppercase(), 80f, 300f, f.black, 34f, accent, spacing = 8f)
    }

    private fun Canvas.stat(f: Fonts, label: String, value: String, unit: String, x: Float, y: Float, color: Int = TEXT) {
        t(label.uppercase(), x, y, f.xbold, 24f, DIM, spacing = 4f)
        t(value, x, y + 110f, f.bebas, 120f, color)
        if (unit.isNotEmpty()) {
            val w = Paint().apply { typeface = f.bebas; textSize = 120f }.measureText(value)
            t(unit, x + w + 12f, y + 110f, f.xbold, 30f, DIM)
        }
    }

    private fun Canvas.footer(f: Fonts) {
        t("Year in review", W / 2f, H - 90f, f.semi, 30f, DIM, Paint.Align.CENTER)
    }

    fun render(ctx: Context, s: YearStats): List<Bitmap> {
        val f = Fonts(ctx)
        val cards = mutableListOf<Bitmap>()

        // 1 · Overview
        base(EMERALD, CYAN).let { (b, c) ->
            c.header(f, s.year, 1, "Your year", EMERALD)
            c.t(num(s.workoutsDone.toDouble()), 80f, 720f, f.bebas, 360f, TEXT)
            c.t("WORKOUTS DONE", 80f, 820f, f.bebas, 90f, EMERALD, spacing = 2f)
            c.stat(f, "Plan done", "${s.compliance}", "%", 80f, 1020f)
            c.stat(f, "Active days", num(s.activeDays.toDouble()), "", 560f, 1020f)
            c.stat(f, "With the watch", num(s.hours, 0), "h", 80f, 1300f)
            c.stat(f, "Perfect streak", "${s.longestPerfectStreakWeeks}", "weeks", 560f, 1300f, AMBER)
            c.footer(f)
            cards += b
        }
        // 2 · Cardio
        base(CYAN, VIOLET).let { (b, c) ->
            c.header(f, s.year, 2, "Swim and run", CYAN)
            c.stat(f, "Swum", num(s.swimKm, 1), "km", 80f, 460f, CYAN)
            c.stat(f, "Run", num(s.runKm, 1), "km", 560f, 460f, EMERALD)
            c.stat(f, "Best swim pace", s.bestSwimPace?.let { fmtDur(it) } ?: "–", "/100m", 80f, 780f)
            c.stat(f, "Best run pace", s.bestPaceKm?.let { fmtDur(it) } ?: "–", "/km", 80f, 1080f)
            c.stat(f, "Longest run", num(s.longestRunKm, 1), "km", 80f, 1380f)
            c.footer(f)
            cards += b
        }
        // 3 · Strength
        base(PINK, EMERALD).let { (b, c) ->
            c.header(f, s.year, 3, "Kettlebells", PINK)
            c.t(num(s.liftedKg), 80f, 680f, f.bebas, 260f, TEXT)
            c.t("KG LIFTED", 80f, 780f, f.bebas, 90f, PINK, spacing = 2f)
            c.stat(f, "Sessions", "${s.strengthSessions}", "", 80f, 980f)
            c.stat(f, "Records", "${s.records}", "🏆", 560f, 980f, AMBER)
            c.stat(f, "Sauna", num(s.saunaHours, 1), "h", 80f, 1280f, PINK)
            s.topExercise?.let {
                c.t("MOST WORK DONE", 80f, 1620f, f.xbold, 24f, DIM, spacing = 4f)
                c.t(it, 80f, 1680f, f.xbold, 44f, TEXT)
            }
            c.footer(f)
            cards += b
        }
        // 4 · Consistency and recovery
        base(AMBER, PINK).let { (b, c) ->
            c.header(f, s.year, 4, "Best of the year", AMBER)
            c.t(s.bestMonth?.uppercase() ?: "–", 80f, 640f, f.bebas, 200f, TEXT)
            c.t("BEST MONTH · ${s.bestMonthDone} WORKOUTS", 80f, 720f, f.xbold, 34f, AMBER, spacing = 4f)
            c.stat(f, "Average HRV", s.avgHrv?.let { num(it) } ?: "–", "ms", 80f, 920f)
            c.stat(f, "Average sleep", s.avgSleepSecs?.let { fmtHours(it) } ?: "–", "", 560f, 920f)
            val box = RectF(80f, 1250f, W - 80f, 1560f)
            c.drawRoundRect(box, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(box.left, box.top, box.right, box.bottom, 0x40F59E0B, 0x14F472B6, Shader.TileMode.CLAMP)
            })
            c.t("${s.workoutsDone} workouts.", 130f, 1360f, f.black, 52f, TEXT)
            c.t("${num(s.liftedKg)} kg. ${num(s.swimKm + s.runKm, 0)} km.", 130f, 1440f, f.black, 52f, TEXT)
            c.t("On to ${s.year + 1}.", 130f, 1520f, f.black, 52f, AMBER)
            c.footer(f)
            cards += b
        }
        return cards
    }
}
