package app.stride.wallet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import app.stride.wallet.domain.Barcodes
import app.stride.wallet.domain.FiscalParse
import app.stride.wallet.domain.ReceiptPdfLayout
import app.stride.wallet.model.CardFormat
import app.stride.wallet.model.Receipt
import java.io.File

/** The receipt as printed, as a PDF: black monospace text centered on white A4 pages, shared with any app. */
object ReceiptPdf {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    /** Writes the PDF into cache/share (served by the app's FileProvider). Call off the main thread. */
    fun write(context: Context, r: Receipt): File =
        writeLines(
            context, ReceiptPdfLayout.lines(r.journal.orEmpty()), ReceiptPdfLayout.fileName(r.shop ?: r.company, r.time), centered = true,
            qr = FiscalParse.verificationUrl(r.url),
        )

    /**
     * Any monospace text as A4 pages: shrunk so the widest line fits (10 pt at most), centered or from the left margin.
     * [qr] (the receipt's tax-office link) is drawn as a QR code under the text, like on the paper receipt.
     */
    fun writeLines(context: Context, lines: List<String>, fileName: String, centered: Boolean, qr: String? = null): File {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.BLACK; textSize = 10f }
        val widest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        // The widest line fills the page width at most; never larger than 10 pt.
        if (widest > 0f) paint.textSize = (10f * (PAGE_W - 2 * MARGIN) / widest).coerceAtMost(10f)
        val lineHeight = paint.textSize * 1.35f
        val blockWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val left = if (centered) (PAGE_W - blockWidth) / 2 else MARGIN
        val perPage = ((PAGE_H - 2 * MARGIN) / lineHeight).toInt()

        val doc = PdfDocument()
        val pages = ReceiptPdfLayout.pages(lines, perPage)
        val matrix = qr?.let { Barcodes.matrix(it, CardFormat.QR_CODE) }
        val qrSize = 150f
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 8f; textAlign = Paint.Align.CENTER }
        var pageNo = 0
        fun page(draw: (android.graphics.Canvas) -> Unit) {
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())
            p.canvas.drawColor(Color.WHITE)
            draw(p.canvas)
            doc.finishPage(p)
        }
        fun drawQr(c: android.graphics.Canvas, top: Float) {
            val m = matrix ?: return
            val cell = qrSize / m.width
            val x0 = (PAGE_W - qrSize) / 2
            val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m.get(x, y)) c.drawRect(x0 + x * cell, top + y * cell, x0 + (x + 1) * cell, top + (y + 1) * cell, black)
            }
            c.drawText("Scan to check this receipt with the Tax Administration", PAGE_W / 2f, top + qrSize + 14f, caption)
        }
        pages.forEachIndexed { i, lines0 ->
            page { c ->
                lines0.forEachIndexed { n, line -> c.drawText(line, left, MARGIN + paint.textSize + n * lineHeight, paint) }
                // The QR goes under the last line when it fits, else on a page of its own.
                val bottom = MARGIN + lines0.size * lineHeight + 16f
                if (i == pages.lastIndex && matrix != null && bottom + qrSize + 24f <= PAGE_H - MARGIN) drawQr(c, bottom)
            }
        }
        if (matrix != null && MARGIN + pages.last().size * lineHeight + 16f + qrSize + 24f > PAGE_H - MARGIN) page { c -> drawQr(c, MARGIN) }
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** A text file (CSV…) in cache/share, ready for [share]. */
    fun writeText(context: Context, text: String, fileName: String): File =
        File(File(context.cacheDir, "share").apply { mkdirs() }, fileName).apply { writeText(text) }

    fun share(context: Context, file: File, mime: String = "application/pdf", title: String = "Share receipt") {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
