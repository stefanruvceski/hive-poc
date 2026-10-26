package app.stride.wallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.UUID

/** Photos of loyalty cards' fronts, kept in the app's own files (scaled to 1000 px, JPEG). */
object CardImages {
    private const val DIR = "wallet_cards"
    private const val MAX = 1000

    fun file(context: Context, name: String) = File(File(context.filesDir, DIR), name)

    /** Blocking: call off the main thread. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val src = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val scale = (MAX.toFloat() / maxOf(s.width, s.height)).coerceAtMost(1f)
                d.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri).let { b ->
                val scale = (MAX.toFloat() / maxOf(b.width, b.height)).coerceAtMost(1f)
                Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
            }
        }
        val name = "card_${UUID.randomUUID().toString().take(12)}.jpg"
        val f = file(context, name)
        f.parentFile?.mkdirs()
        f.outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()

    fun delete(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }
}
