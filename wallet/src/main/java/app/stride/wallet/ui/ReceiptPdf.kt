package app.stride.wallet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import app.stride.wallet.domain.ReceiptPdfLayout
import app.stride.wallet.model.Receipt
import java.io.File

/** The receipt as printed, as a PDF: black monospace text centered on white A4 pages, shared with any app. */
object ReceiptPdf {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    /** Writes the PDF into cache/share (served by the app's FileProvider). Call off the main thread. */
    fun write(context: Context, r: Receipt): File {
        val lines = ReceiptPdfLayout.lines(r.journal.orEmpty())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.BLACK; textSize = 10f }
        val widest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        // The widest line fills the page width at most; never larger than 10 pt.
        if (widest > 0f) paint.textSize = (10f * (PAGE_W - 2 * MARGIN) / widest).coerceAtMost(10f)
        val lineHeight = paint.textSize * 1.35f
        val blockWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val left = (PAGE_W - blockWidth) / 2
        val perPage = ((PAGE_H - 2 * MARGIN) / lineHeight).toInt()

        val doc = PdfDocument()
        ReceiptPdfLayout.pages(lines, perPage).forEachIndexed { i, page ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
            p.canvas.drawColor(Color.WHITE)
            page.forEachIndexed { n, line -> p.canvas.drawText(line, left, MARGIN + paint.textSize + n * lineHeight, paint) }
            doc.finishPage(p)
        }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, ReceiptPdfLayout.fileName(r.shop ?: r.company, r.time))
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
