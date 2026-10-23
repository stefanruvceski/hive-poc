package app.stride.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
 * Dark map background for share images: CARTO "Dark Matter" tiles, built from OpenStreetMap data
 * (free for personal use with attribution, see [ATTRIBUTION]). Tiles are cached on disk so sharing
 * the same area again doesn't download anything.
 */
object MapTiles {
    const val ATTRIBUTION = "© OpenStreetMap contributors © CARTO"
    private const val TILE = 256
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** Card pixel = (Web Mercator world pixel at [zoom] - origin) * [k]. */
    class View(val zoom: Int, val k: Double, val originX: Double, val originY: Double) {
        fun project(p: Pair<Double, Double>): Pair<Float, Float> =
            ((worldX(p.second, zoom) - originX) * k).toFloat() to ((worldY(p.first, zoom) - originY) * k).toFloat()
    }

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
        val zoom = floor(log2(scale)).toInt().coerceIn(2, 17)
        val k = scale / (1 shl zoom)
        val cx = (xs.max() + xs.min()) / 2 * (1 shl zoom)
        val cy = (ys.max() + ys.min()) / 2 * (1 shl zoom)
        return View(zoom, k, cx - box.centerX() / k, cy - box.centerY() / k)
    }

    /** The map for the whole card, or null when too many tiles can't be loaded (offline). */
    suspend fun render(context: Context, view: View, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cell = TILE * view.k
        val x0 = floor(view.originX / TILE).toInt()
        val y0 = floor(view.originY / TILE).toInt()
        val x1 = floor((view.originX + width / view.k) / TILE).toInt()
        val y1 = floor((view.originY + height / view.k) / TILE).toInt()
        val max = 1 shl view.zoom
        val tiles = (y0..y1).flatMap { y -> (x0..x1).map { x -> x to y } }.filter { (_, y) -> y in 0 until max }
        val gate = Semaphore(6)
        val loaded = coroutineScope {
            tiles.map { (x, y) -> async { gate.withPermit { (x to y) to tile(context, view.zoom, Math.floorMod(x, max), y) } } }.awaitAll()
        }
        if (loaded.count { it.second != null } < tiles.size * 0.7) return@withContext null
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        loaded.forEach { (xy, bmp) ->
            bmp ?: return@forEach
            val left = ((xy.first * TILE - view.originX) * view.k).toFloat()
            val top = ((xy.second * TILE - view.originY) * view.k).toFloat()
            // +1 px overlap hides hairline seams between scaled tiles.
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF(left, top, left + cell.toFloat() + 1, top + cell.toFloat() + 1), paint)
        }
        out
    }

    private fun tile(context: Context, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(File(context.cacheDir, "tiles").apply { mkdirs() }, "dark_${z}_${x}_$y.png")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
        val sub = "abcd"[Math.floorMod(x + y, 4)]
        val request = Request.Builder()
            .url("https://$sub.basemaps.cartocdn.com/dark_all/$z/$x/$y@2x.png")
            .header("User-Agent", "Stride-Android (personal training diary)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val bytes = r.body?.bytes() ?: return null
                file.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
}
