package app.stride.share

import app.stride.host.Brand
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.stride.core.R
import app.stride.model.ShareBackground
import app.stride.model.SharePalette
import app.stride.model.ShareStyle
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Story-format (1080x1920) share images, drawn directly on a bitmap so they look the same
 * on every phone. Layouts follow the approved mockups: run with a glowing GPS route,
 * strength with "kg lifted" and records, swim with pace per rep.
 */
object ShareCard {
    const val W = 1080
    const val H = 1920

    private const val BG = 0xFF0A0F1E.toInt()
    private const val ELEV2 = 0xFF16203A.toInt()
    private const val TEXT = 0xFFEEF2FF.toInt()
    private const val DIM = 0xFF9FB0D0.toInt()
    private const val FAINT = 0xFF64748B.toInt()
    const val EMERALD = 0xFF34D399.toInt()
    const val CYAN = 0xFF22D3EE.toInt()
    const val PINK = 0xFFF472B6.toInt()
    const val RED = 0xFFF87171.toInt()
    const val AMBER = 0xFFF59E0B.toInt()
    private const val BLUE = 0xFF60A5FA.toInt()
    private val ZONES = intArrayOf(FAINT, BLUE, EMERALD, AMBER, RED, PINK, 0xFFA855F7.toInt())

    /** Colors and options for one card: accent, two companion colors for gradients, and the user's toggles. */
    class Look(val a1: Int, val a2: Int, val a3: Int, val style: ShareStyle) {
        val background: ShareBackground get() = style.background
    }

    private val PALETTES = mapOf(
        SharePalette.EMERALD to Triple(EMERALD, CYAN, PINK),
        SharePalette.CYAN to Triple(CYAN, 0xFF2563EB.toInt(), 0xFFA78BFA.toInt()),
        SharePalette.PINK to Triple(PINK, 0xFFA855F7.toInt(), AMBER),
        SharePalette.AMBER to Triple(AMBER, RED, PINK),
        SharePalette.VIOLET to Triple(0xFFA78BFA.toInt(), PINK, CYAN),
        SharePalette.MONO to Triple(0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt(), 0xFF94A3B8.toInt()),
    )

    /** [auto] is the card's own palette, used when the user keeps "Auto". */
    fun look(style: ShareStyle, auto: Triple<Int, Int, Int>): Look {
        val (a, b, c) = PALETTES[style.palette] ?: auto
        return Look(a, b, c, style)
    }

    val RUN_AUTO = Triple(EMERALD, CYAN, PINK)
    val STRENGTH_AUTO = Triple(PINK, EMERALD, CYAN)
    val SWIM_AUTO = Triple(CYAN, 0xFF2563EB.toInt(), 0xFFA78BFA.toInt())

    /** [c] mixed into the navy background, for glows and gradients. */
    private fun dim(c: Int, amount: Float = 0.24f): Int {
        fun ch(shift: Int) = (((c shr shift) and 0xFF) * amount + ((BG shr shift) and 0xFF) * (1 - amount)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun withAlpha(c: Int, alpha: Int) = (alpha shl 24) or (c and 0x00FFFFFF)

    /** Background for cards without a map: glow (default), vertical gradient or plain navy. */
    private fun Canvas.backdrop(look: Look, glowX: Float, glowY: Float) {
        drawColor(BG)
        when (look.background) {
            ShareBackground.GLOW -> radial(glowX, glowY, 1200f, dim(look.a1), 0.55f)
            ShareBackground.GRADIENT -> drawPaint(paint().apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(dim(look.a1, 0.38f), dim(look.a2, 0.16f), BG), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            })
            ShareBackground.PLAIN -> Unit
        }
    }

    data class Stat(val label: String, val value: String, val unit: String = "", val color: Int = TEXT)
    data class Row(val name: String, val sets: String, val kg: String, val fraction: Float, val record: Boolean)
    data class Rep(val label: String, val seconds: Double)

    private class Fonts(ctx: Context) {
        val bebas: Typeface = ResourcesCompat.getFont(ctx, R.font.bebas_neue) ?: Typeface.DEFAULT_BOLD
        val semi: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_semibold) ?: Typeface.DEFAULT
        val xbold: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_extrabold) ?: Typeface.DEFAULT_BOLD
        val black: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_black) ?: Typeface.DEFAULT_BOLD
    }

    // ------------------------------------------------------------------ helpers

    private fun paint(color: Int = TEXT) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** Draws text with its baseline at [y]; returns the width. [spacing] is letter spacing in px. */
    private fun Canvas.text(
        s: String, x: Float, y: Float, tf: Typeface, size: Float, color: Int,
        spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT,
    ): Float {
        val p = paint(color).apply {
            typeface = tf
            textSize = size
            letterSpacing = spacing / size
            textAlign = align
        }
        drawText(s, x, y, p)
        return p.measureText(s)
    }

    private fun width(s: String, tf: Typeface, size: Float, spacing: Float = 0f): Float =
        paint().apply { typeface = tf; textSize = size; letterSpacing = spacing / size }.measureText(s)

    private fun Canvas.radial(cx: Float, cy: Float, r: Float, color: Int, stop: Float = 0.6f) {
        val p = paint().apply { shader = RadialGradient(cx, cy, r, intArrayOf(color, color and 0x00FFFFFF), floatArrayOf(0f, stop), Shader.TileMode.CLAMP) }
        drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
    }

    private fun Canvas.statsRow(f: Fonts, stats: List<Stat>, labelY: Float) {
        val colW = (W - 160f) / 3
        stats.take(3).forEachIndexed { i, s ->
            val x = 80f + i * colW
            text(s.label.uppercase(), x, labelY, f.xbold, 22f, DIM, spacing = 4f)
            val w = text(s.value, x, labelY + 96f, f.bebas, 96f, s.color, spacing = 1f)
            if (s.unit.isNotEmpty()) text(s.unit, x + w + 6f, labelY + 96f, f.xbold, 26f, DIM)
        }
    }

    private fun Canvas.header(f: Fonts, kicker: String, subtitle: String, accent: Int) {
        text(kicker.uppercase(), 80f, 140f, f.black, 30f, accent, spacing = 8f)
        text(subtitle, 80f, 192f, f.semi, 30f, DIM)
    }

    private fun Canvas.footer(ctx: Context, f: Fonts, right: String) {
        val y = H - 90f
        ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)?.let { d ->
            // The icon art fills ~54% of its 108-unit canvas; draw it so the bell is ~58 px tall.
            val size = 112
            d.setBounds(52, (y - 28 - size / 2).toInt(), 52 + size, (y - 28 + size / 2).toInt())
            d.draw(this)
        }
        text(Brand.NAME.uppercase(), 158f, y - 16f, f.black, 34f, TEXT, spacing = 10f)
        text(right.uppercase(), W - 80f, y - 16f, f.xbold, 26f, DIM, spacing = 3f, align = Paint.Align.RIGHT)
    }

    private fun Canvas.zoneBar(times: List<Double>, top: Float) {
        val total = times.sum()
        if (total <= 0) return
        val r = RectF(80f, top, W - 80f, top + 16f)
        save()
        clipPath(Path().apply { addRoundRect(r, 8f, 8f, Path.Direction.CW) })
        var x = r.left
        times.forEachIndexed { i, t ->
            val w = (r.width() * t / total).toFloat()
            drawRect(x, r.top, x + w, r.bottom, paint(ZONES[min(i, ZONES.lastIndex)]))
            x += w
        }
        restore()
    }

    private fun bigNumber(c: Canvas, f: Fonts, value: String, unit: String, baseline: Float, size: Float, unitSize: Float, accent: Int) {
        val w = c.text(value, 80f, baseline, f.bebas, size, TEXT, spacing = 2f)
        c.text(unit, 80f + w + 18f, baseline, f.bebas, unitSize, accent, spacing = 2f)
    }

    // ------------------------------------------------------------------ run (and other GPS activities)

    fun run(
        ctx: Context, look: Look, kicker: String, subtitle: String, distance: String, unit: String,
        stats: List<Stat>, zones: List<Double>?, route: List<Pair<Double, Double>>?, footer: String,
        map: MapTiles.Map? = null, mapView: MapTiles.View? = null,
    ): Bitmap {
        val f = Fonts(ctx)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (map != null && mapView != null) {
            c.drawColor(BG)
            drawMap(c, f, map, look)
            if (route != null && route.size >= 2) drawRoute(c, f, route, ROUTE_BOX, look, mapView::project)
        } else {
            c.backdrop(look, 216f, 0f)
            if (look.background != ShareBackground.PLAIN) {
                // Topographic rings for texture.
                val rnd = Random(7)
                val topo = paint(0x09FFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
                for (r in 80 until 1400 step 55) {
                    c.save()
                    c.rotate(rnd.nextInt(-25, 25).toFloat(), 540f, 880f)
                    val cx = 540f + rnd.nextInt(-60, 60)
                    val cy = 880f + rnd.nextInt(-40, 40)
                    c.drawOval(RectF(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.62f), topo)
                    c.restore()
                }
            }
            if (route != null && route.size >= 2) drawRoute(c, f, route, ROUTE_BOX, look)
        }
        c.header(f, kicker, subtitle, look.a1)
        if (look.style.stats) {
            bigNumber(c, f, distance, unit, 1500f, 300f, 110f, look.a1)
            c.statsRow(f, stats, 1580f)
        } else {
            bigNumber(c, f, distance, unit, 1640f, 340f, 120f, look.a1)
        }
        if (look.style.zones) zones?.let { c.zoneBar(it, 1720f) }
        c.footer(ctx, f, footer)
        return bmp
    }

    /** Where the route goes on the card: between the header and the big distance. */
    val ROUTE_BOX = RectF(90f, 300f, 990f, 1160f)

    /** Map full-bleed, tinted toward the app's navy, faded out behind the header and the stats. */
    private fun drawMap(c: Canvas, f: Fonts, map: MapTiles.Map, look: Look) {
        c.drawBitmap(map.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(0x330A0F1E)
        if (look.background != ShareBackground.PLAIN) c.radial(216f, 0f, 1100f, withAlpha(dim(look.a1, 0.4f), 0x66), 0.6f)
        c.drawRect(0f, 0f, W.toFloat(), 560f, paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 560f, 0xF20A0F1E.toInt(), 0x000A0F1E, Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 1120f, W.toFloat(), H.toFloat(), paint().apply {
            shader = LinearGradient(0f, 1120f, 0f, 1420f, 0x000A0F1E, 0xF70A0F1E.toInt(), Shader.TileMode.CLAMP)
        })
        c.text(map.attribution, W - 80f, 1250f, f.semi, 18f, 0x99FFFFFF.toInt(), align = Paint.Align.RIGHT)
    }

    private fun drawRoute(
        c: Canvas, f: Fonts, route: List<Pair<Double, Double>>, box: RectF, look: Look,
        project: ((Pair<Double, Double>) -> Pair<Float, Float>)? = null,
    ) {
        val pts = if (project != null) route.map(project) else {
            // Equirectangular projection around the route's mean latitude: fine at city scale.
            val lat0 = route.map { it.first }.average() * PI / 180
            val xs = route.map { it.second * cos(lat0) }
            val ys = route.map { -it.first }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val scale = min(box.width() / max(maxX - minX, 1e-9), box.height() / max(maxY - minY, 1e-9)) * 0.92
            val ox = box.centerX() - (maxX - minX) * scale / 2
            val oy = box.centerY() - (maxY - minY) * scale / 2
            xs.indices.map { i -> ((xs[i] - minX) * scale + ox).toFloat() to ((ys[i] - minY) * scale + oy).toFloat() }
        }

        val path = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
        }
        val routeShader = LinearGradient(box.left, box.top, box.right, box.bottom, intArrayOf(look.a1, look.a2, look.a3), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fun stroke(w: Float) = paint().apply {
            style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, stroke(22f).apply { shader = routeShader; maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL); alpha = 170 })
        c.drawPath(path, stroke(11f).apply { shader = routeShader })
        c.drawPath(path, stroke(2.5f).apply { color = 0x8CFFFFFF.toInt() })

        // Kilometre markers along the track.
        var meters = 0.0
        var nextKm = 1
        for (i in 1 until if (look.style.kmMarkers) route.size else 0) {
            meters += haversine(route[i - 1], route[i])
            if (meters >= nextKm * 1000) {
                val (x, y) = pts[i]
                c.drawCircle(x, y, 17f, paint(BG))
                c.drawCircle(x, y, 17f, paint(TEXT).apply { style = Paint.Style.STROKE; strokeWidth = 3f })
                c.text("$nextKm", x, y + 7f, f.black, 19f, TEXT, align = Paint.Align.CENTER)
                nextKm++
            }
        }
        val (sx, sy) = pts.first()
        val (ex, ey) = pts.last()
        c.drawCircle(sx, sy, 22f, paint(look.a1))
        c.drawCircle(sx, sy, 22f, paint(BG).apply { style = Paint.Style.STROKE; strokeWidth = 7f })
        c.drawCircle(ex, ey, 70f, paint().apply { shader = RadialGradient(ex, ey, 70f, intArrayOf(withAlpha(look.a3, 0xE6), withAlpha(look.a3, 0)), null, Shader.TileMode.CLAMP) })
        c.drawCircle(ex, ey, 22f, paint(look.a3))
        c.drawCircle(ex, ey, 22f, paint(BG).apply { style = Paint.Style.STROKE; strokeWidth = 7f })
    }

    private fun haversine(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = (b.first - a.first) * PI / 180
        val dLng = (b.second - a.second) * PI / 180
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(a.first * PI / 180) * cos(b.first * PI / 180) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(h))
    }

    // ------------------------------------------------------------------ strength

    fun strength(
        ctx: Context, look: Look, kicker: String, subtitle: String, lifted: String, record: String?,
        rows: List<Row>, stats: List<Stat>, footer: String,
    ): Bitmap {
        val f = Fonts(ctx)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        when (look.background) {
            ShareBackground.GLOW -> {
                c.radial(918f, 192f, 1000f, dim(look.a1, 0.22f))
                c.radial(0f, H.toFloat(), 1100f, dim(look.a2, 0.2f))
            }
            else -> c.backdrop(look, 918f, 192f)
        }
        listOf(300f to look.a1, 200f to look.a2, 100f to look.a3).forEachIndexed { i, (r, col) ->
            c.drawCircle(980f, 420f, r, paint(withAlpha(col, if (i == 0) 0x1A else 0x14)).apply { style = Paint.Style.STROKE; strokeWidth = 40f })
        }

        c.header(f, kicker, subtitle, look.a1)
        c.text(lifted, 80f, 470f, f.bebas, 250f, TEXT, spacing = 2f)
        c.text("KG LIFTED", 80f, 560f, f.bebas, 90f, look.a1, spacing = 2f)

        var y = 620f
        if (record != null) {
            val box = RectF(80f, y, W - 80f, y + 170f)
            c.drawRoundRect(box, 34f, 34f, paint().apply {
                shader = LinearGradient(box.left, box.top, box.right, box.bottom, 0x38F59E0B, 0x0FF59E0B, Shader.TileMode.CLAMP)
            })
            c.drawRoundRect(box, 34f, 34f, paint(0x8CF59E0B.toInt()).apply { style = Paint.Style.STROKE; strokeWidth = 2f })
            c.text("🏆", 118f, y + 114f, Typeface.DEFAULT, 80f, TEXT)
            c.text("NEW RECORD", 232f, y + 70f, f.black, 26f, AMBER, spacing = 5f)
            val recordText = ellipsize(record, f.xbold, 40f, W - 80f - 232f - 30f)
            c.text(recordText, 232f, y + 124f, f.xbold, 40f, TEXT)
            y += 210f
        } else {
            y += 30f
        }

        val shown = if (look.style.details) rows.take(7) else emptyList()
        shown.forEach { r ->
            val nameBase = y + 36f
            val nameW = c.text(ellipsize(r.name, f.xbold, 32f, 560f), 80f, nameBase, f.xbold, 32f, TEXT)
            if (r.record) c.text(" ★", 80f + nameW, nameBase, f.xbold, 32f, AMBER)
            val kgW = c.text(r.kg, W - 80f, nameBase, f.xbold, 32f, TEXT, align = Paint.Align.RIGHT)
            c.text("${r.sets} · ", W - 80f - kgW, nameBase, f.xbold, 32f, DIM, align = Paint.Align.RIGHT)
            val track = RectF(80f, nameBase + 22f, W - 80f, nameBase + 34f)
            c.drawRoundRect(track, 6f, 6f, paint(ELEV2))
            val fill = RectF(track.left, track.top, track.left + track.width() * r.fraction.coerceIn(0.04f, 1f), track.bottom)
            c.drawRoundRect(fill, 6f, 6f, paint().apply { shader = LinearGradient(fill.left, 0f, track.right, 0f, look.a2, look.a3, Shader.TileMode.CLAMP) })
            y += 100f
        }
        if (shown.isNotEmpty() && rows.size > shown.size) {
            c.text("+${rows.size - shown.size} more", 80f, y + 30f, f.xbold, 28f, DIM)
            y += 50f
        }
        if (look.style.stats) c.statsRow(f, stats, y + 60f)
        c.footer(ctx, f, footer)
        return bmp
    }

    private fun ellipsize(s: String, tf: Typeface, size: Float, maxW: Float): String {
        if (width(s, tf, size) <= maxW) return s
        var t = s
        while (t.length > 1 && width("$t…", tf, size) > maxW) t = t.dropLast(1)
        return "$t…"
    }

    // ------------------------------------------------------------------ swim

    fun swim(
        ctx: Context, look: Look, kicker: String, subtitle: String, distance: String,
        stats: List<Stat>, reps: List<Rep>, repTitle: String, zones: List<Double>?, footer: String,
    ): Bitmap {
        val f = Fonts(ctx)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (look.background) {
            ShareBackground.GLOW -> c.drawPaint(paint().apply { shader = LinearGradient(0f, 0f, 0f, H * 0.6f, dim(look.a1, 0.12f), BG, Shader.TileMode.CLAMP) })
            else -> c.backdrop(look, 540f, 0f)
        }

        val lane = paint(0x0AFFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(40f, 30f), 0f) }
        for (x in 180 until W step 180) c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), lane)
        listOf(Triple(1080f, 0x59, 6f), Triple(1130f, 0x38, 5f), Triple(1180f, 0x24, 4f), Triple(1230f, 0x14, 3f), Triple(1280f, 0x0D, 3f)).forEach { (y, a, w) ->
            val p = Path().apply {
                moveTo(-50f, y)
                cubicTo(150f, y - 40f, 350f, y + 40f, 540f, y)
                cubicTo(730f, y - 40f, 930f, y + 40f, 1130f, y)
            }
            c.drawPath(p, paint(withAlpha(look.a1, a)).apply { style = Paint.Style.STROKE; strokeWidth = w })
        }

        c.header(f, kicker, subtitle, look.a1)
        bigNumber(c, f, distance, "M", 560f, 300f, 110f, look.a1)
        if (look.style.stats) c.statsRow(f, stats, 640f)

        if (!look.style.details) {
            // Distance and stats only.
        } else if (reps.size >= 2) {
            c.text(repTitle.uppercase(), 80f, 1290f, f.xbold, 22f, DIM, spacing = 4f)
            val fastest = reps.minOf { it.seconds }
            val slowest = reps.maxOf { it.seconds }
            val gap = 16f
            val bw = (W - 160f - gap * (reps.size - 1)) / reps.size
            val bottom = 1600f
            reps.forEachIndexed { i, r ->
                val h = 110f + if (slowest - fastest < 0.01) 60f else ((slowest - r.seconds) / (slowest - fastest) * 120).toFloat()
                val x = 80f + i * (bw + gap)
                val rect = RectF(x, bottom - h, x + bw, bottom)
                c.drawRoundRect(rect, 12f, 12f, paint().apply { shader = LinearGradient(0f, rect.top, 0f, rect.bottom, look.a1, look.a2, Shader.TileMode.CLAMP) })
                c.text(r.label, rect.centerX(), rect.top - 12f, f.xbold, if (reps.size > 10) 16f else 20f, TEXT, align = Paint.Align.CENTER)
                c.text("${i + 1}", rect.centerX(), bottom + 36f, f.xbold, 22f, DIM, align = Paint.Align.CENTER)
            }
        } else if (look.style.zones) {
            zones?.let {
                c.text("TIME IN HEART-RATE ZONES", 80f, 1290f, f.xbold, 22f, DIM, spacing = 4f)
                c.zoneBar(it, 1320f)
            }
        }
        c.footer(ctx, f, footer)
        return bmp
    }
}
