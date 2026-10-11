package com.littlepages.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Creates and renders harmless sample pages to exercise the in-app PDF surface. */
object SamplePdfRenderer {
    private const val PAGE_COUNT = 24
    private const val PAGE_WIDTH = 720
    private const val PAGE_HEIGHT = 960

    @Synchronized
    fun render(context: Context, pageNumber: Int): Bitmap {
        val file = File(context.cacheDir, "reader-sample-preview.pdf")
        if (!file.exists() || file.length() == 0L) createSamplePdf(file)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage((pageNumber - 1).coerceIn(0, renderer.pageCount - 1)).use { page ->
                    val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun createSamplePdf(file: File) {
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(27, 68, 55); textSize = 38f; typeface = android.graphics.Typeface.create("sans-serif-medium", 0) }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(43, 49, 45); textSize = 28f }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(225, 239, 226) }
        try {
            repeat(PAGE_COUNT) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val canvas: Canvas = page.canvas
                canvas.drawColor(android.graphics.Color.rgb(255, 252, 242))
                canvas.drawRoundRect(42f, 48f, 678f, 908f, 32f, 32f, accentPaint)
                canvas.drawText("The Brave Little Fox", 82f, 140f, titlePaint)
                canvas.drawText("Sample PDF reader page ${index + 1}", 82f, 210f, bodyPaint)
                canvas.drawText("This preview uses sample text only.", 82f, 262f, bodyPaint)
                canvas.drawText("No licensed book content is included.", 82f, 314f, bodyPaint)
                canvas.drawText("Page ${index + 1} of $PAGE_COUNT", 82f, 850f, bodyPaint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
