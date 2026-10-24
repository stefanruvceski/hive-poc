package app.stride.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import app.stride.host.AppInfo
import app.stride.model.ShareMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Map background for share images from tile services that need no API key:
 * Esri's dark gray canvas and World Imagery, and the standard OpenStreetMap tiles (darkened here).
 * Tiles are cached on disk so sharing the same area again doesn't download anything.
 */
object MapTiles {
    private const val TILE = 256
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** A keyless tile source. [url] gets z, x, y. Tiles are 256 px, so they're fetched one zoom level deeper for a sharp card. */
    private class Source(val key: String, val maxZoom: Int, val attribution: String, val url: (Int, Int, Int) -> String)

    private val ESRI_DARK = Source("esri_dark", 16, "Esri, HERE, Garmin, © OpenStreetMap contributors") { z, x, y ->
        "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$z/$y/$x"
    }
    private val ESRI_IMAGERY = Source("esri_sat", 18, "Esri, Maxar, Earthstar Geographics") { z, x, y ->
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
    }
    private val OSM = Source("osm", 18, "© OpenStreetMap contributors") { z, x, y -> "https://tile.openstreetmap.org/$z/$x/$y.png" }

    /** Sources to try for a style, best first; the next one is used when tiles don't load. */
    private fun sources(style: ShareMap): List<Pair<Source, ShareMap>> = when (style) {
        ShareMap.DARK -> listOf(ESRI_DARK to ShareMap.DARK, OSM to ShareMap.STREETS)
        ShareMap.STREETS -> listOf(OSM to ShareMap.STREETS, ESRI_DARK to ShareMap.DARK)
        ShareMap.SATELLITE -> listOf(ESRI_IMAGERY to ShareMap.SATELLITE, ESRI_DARK to ShareMap.DARK)
        ShareMap.NONE -> emptyList()
    }

    /** Card pixel = (Web Mercator world pixel at [zoom] - origin) * [k]. */
    class View(val zoom: Int, val k: Double, val originX: Double, val originY: Double) {
        fun project(p: Pair<Double, Double>): Pair<Float, Float> =
            ((worldX(p.second, zoom) - originX) * k).toFloat() to ((worldY(p.first, zoom) - originY) * k).toFloat()
    }

    /** A rendered map and the credit line to print on it. */
    class Map(val bitmap: Bitmap, val attribution: String)

    private fun worldX(lng: Double, z: Int) = (lng + 180) / 360 * TILE * (1 shl z)
    private fun worldY(lat: Double, z: Int): Double {
        val r = lat.coerceIn(-85.0, 85.0) * PI / 180
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * TILE * (1 shl z)
    }

    /** Zoom and offset so the route fills [box] (with a margin) and sits centred in it. */
    fun fit(route: List<Pair<Double, Double>>, box: RectF): View {
        val xs = route.map { worldX(it.second, 0) }
        val ys = route.map { worldY(it.first, 0) }
        val w0 = max(xs.max() - xs.min(), 1e-9)
        val h0 = max(ys.max() - ys.min(), 1e-9)
        val scale = min(box.width() / w0, box.height() / h0) * 0.86
        val zoom = floor(log2(scale)).toInt().coerceIn(2, 16)
        val k = scale / (1 shl zoom)
        val cx = (xs.max() + xs.min()) / 2 * (1 shl zoom)
        val cy = (ys.max() + ys.min()) / 2 * (1 shl zoom)
        return View(zoom, k, cx - box.centerX() / k, cy - box.centerY() / k)
    }

    /** The map for the whole card in [style], or null when no source loads (offline) or the style is NONE. */
    suspend fun render(context: Context, view: View, width: Int, height: Int, style: ShareMap): Map? = withContext(Dispatchers.IO) {
        clearOldCache(context)
        for ((source, look) in sources(style)) {
            val bmp = runCatching { renderWith(context, source, look, view, width, height) }.getOrNull()
            if (bmp != null) return@withContext Map(bmp, source.attribution)
        }
        null
    }

    private suspend fun renderWith(context: Context, source: Source, look: ShareMap, view: View, width: Int, height: Int): Bitmap? {
        // One level deeper than the view zoom: a 256 px tile then covers at most 256 card pixels.
        val tz = min(view.zoom + 1, source.maxZoom)
        val span = TILE.toDouble() / (1 shl (tz - view.zoom)) // world pixels (at view.zoom) per tile
        val cell = span * view.k
        val x0 = floor(view.originX / span).toInt()
        val y0 = floor(view.originY / span).toInt()
        val x1 = floor((view.originX + width / view.k) / span).toInt()
        val y1 = floor((view.originY + height / view.k) / span).toInt()
        val max = 1 shl tz
        val tiles = (y0..y1).flatMap { y -> (x0..x1).map { x -> x to y } }.filter { (_, y) -> y in 0 until max }
        val gate = Semaphore(6)
        val loaded = coroutineScope {
            tiles.map { (x, y) -> async { gate.withPermit { (x to y) to tile(context, source, tz, Math.floorMod(x, max), y) } } }.awaitAll()
        }
        if (loaded.count { it.second != null } < tiles.size * 0.7) return null
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF0A0F1E.toInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = filter(look) }
        loaded.forEach { (xy, bmp) ->
            bmp ?: return@forEach
            val left = ((xy.first * span - view.originX) * view.k).toFloat()
            val top = ((xy.second * span - view.originY) * view.k).toFloat()
            // +1 px overlap hides hairline seams between scaled tiles.
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF(left, top, left + cell.toFloat() + 1, top + cell.toFloat() + 1), paint)
        }
        return out
    }

    /** Colors per look: streets inverted into a navy night map, satellite dimmed, dark canvas tinted toward navy. */
    private fun filter(look: ShareMap): ColorMatrixColorFilter? = when (look) {
        ShareMap.STREETS -> {
            val gray = ColorMatrix().apply { setSaturation(0.15f) }
            val invert = ColorMatrix(
                floatArrayOf(
                    -0.62f, 0f, 0f, 0f, 170f,
                    0f, -0.62f, 0f, 0f, 178f,
                    0f, 0f, -0.55f, 0f, 196f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            ColorMatrixColorFilter(gray.apply { postConcat(invert) })
        }
        ShareMap.SATELLITE -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.7f); postConcat(ColorMatrix().apply { setScale(0.55f, 0.58f, 0.66f, 1f) }) })
        ShareMap.DARK -> ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.8f, 0.9f, 1.15f, 1f) })
        ShareMap.NONE -> null
    }

    private fun tile(context: Context, source: Source, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(File(context.cacheDir, "tiles").apply { mkdirs() }, "${source.key}_${z}_${x}_$y")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
        val request = Request.Builder()
            .url(source.url(z, x, y))
            // tile.openstreetmap.org requires an identifying User-Agent.
            .header("User-Agent", "Stride/${AppInfo.versionName} (Android; personal training diary; github.com/stefanruvceski/stride)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                if (r.header("Content-Type")?.startsWith("image/") == false) return null
                val bytes = r.body?.bytes() ?: return null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                file.writeBytes(bytes)
                bmp
            }
        }.getOrNull()
    }

    /** Tiles cached by build 32 came from CARTO, which now answers with "API key required" images. */
    private fun clearOldCache(context: Context) {
        File(context.cacheDir, "tiles").listFiles { f -> f.name.startsWith("dark_") }?.forEach { it.delete() }
    }
}
