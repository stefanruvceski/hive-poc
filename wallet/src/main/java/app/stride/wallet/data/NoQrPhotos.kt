package app.stride.wallet.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Receipt photos whose QR couldn't be read, kept in filesDir/wallet_noqr (big enough to read the printed fields). */
object NoQrPhotos {
    const val DIR = "wallet_noqr"

    fun file(context: Context, name: String) = File(File(context.filesDir, DIR), name)

    /** Blocking: call off the main thread. */
    fun save(context: Context, uri: Uri): String? = CardImages.saveScaled(context, uri, DIR, 2400, "receipt")

    fun delete(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }
}
