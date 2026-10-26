package app.stride.wallet.ui

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage

/** A barcode that was read: its text and ML Kit's format (Barcode.FORMAT_*). */
data class Scanned(val value: String, val format: Int)

/**
 * Barcode reading through Google Play services: [live] opens Google's own scanner screen (no camera
 * permission for Hive), [fromImage] reads a picked photo or screenshot.
 */
object Scanner {
    /** [qrOnly] for receipts (their code is always QR), any format for loyalty cards. */
    fun live(context: Context, qrOnly: Boolean, onResult: (Scanned) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .apply { if (qrOnly) setBarcodeFormats(Barcode.FORMAT_QR_CODE) else setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS) }
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { onResult(Scanned(it, b.format)) } ?: onError("That code has no text in it") }
            .addOnFailureListener { onError(it.message ?: "The scanner didn't start") }
    }

    /** Every barcode found in the image (empty when there is none). */
    fun fromImage(context: Context, uri: Uri, qrOnly: Boolean, onResult: (List<Scanned>) -> Unit, onError: (String) -> Unit) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { onError("Couldn't open that picture"); return }
        val options = BarcodeScannerOptions.Builder()
            .apply { if (qrOnly) setBarcodeFormats(Barcode.FORMAT_QR_CODE) else setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS) }
            .build()
        val client = BarcodeScanning.getClient(options)
        client.process(image)
            .addOnSuccessListener { list -> onResult(list.mapNotNull { b -> b.rawValue?.let { Scanned(it, b.format) } }) }
            .addOnFailureListener { onError(it.message ?: "Couldn't read that picture") }
            .addOnCompleteListener { client.close() }
    }
}
