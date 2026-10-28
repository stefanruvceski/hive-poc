package app.stride.wallet.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import app.stride.host.Host
import app.stride.wallet.data.NoQrPhotos
import app.stride.wallet.domain.FiscalParse

/**
 * Share → "Hive Receipt", no screen of its own: a picture with a receipt's QR code (long-press the QR on the tax
 * office's page in Chrome → Share image) or a receipt link. Hive opens with the receipt saved, like a scan. A photo
 * of a receipt whose QR can't be read opens on its printed fields instead (`NoQrScreen`).
 */
class ReceiptShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val text = i?.getStringExtra(Intent.EXTRA_TEXT)?.let { FiscalParse.verificationUrl(it) }
        @Suppress("DEPRECATION")
        val image: Uri? = if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i?.getParcelableExtra(Intent.EXTRA_STREAM)
        when {
            text != null -> open(text)
            image != null -> Scanner.fromImage(this, image, qrOnly = true, onResult = { found ->
                val link = found.firstNotNullOfOrNull { FiscalParse.verificationUrl(it.value) }
                if (link != null) open(link) else printedFields(image)
            }, onError = { printedFields(image) })
            else -> fail("Share a receipt's QR code or its link")
        }
    }

    private fun open(link: String) {
        startActivity(Host.openIntent(this, "wallet/link:" + Uri.encode(link)))
        finish()
    }

    /** No QR read: the picture is copied into Hive (it can't keep the share's permission) and opens on its printed fields. */
    private fun printedFields(image: Uri) {
        Thread {
            val name = runCatching { NoQrPhotos.save(this, image) }.getOrNull()
            runOnUiThread {
                if (name == null) fail("No receipt QR code in that picture")
                else {
                    startActivity(Host.openIntent(this, "wallet/noqr-file:" + Uri.encode(name)))
                    finish()
                }
            }
        }.start()
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
