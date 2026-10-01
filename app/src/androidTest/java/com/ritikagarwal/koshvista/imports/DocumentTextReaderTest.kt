package com.ritikagarwal.koshvista.imports

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTextReaderTest {
    @Test fun readsRenderedPdfWithoutSendingDocumentOffDevice() = runBlocking {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(800, 400, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("STATEMENT 98765", 40f, 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 78f
        })
        document.finishPage(page)
        val bytes = ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        document.close()
        val text = DocumentTextReader(InstrumentationRegistry.getInstrumentation().targetContext).read(bytes, "application/pdf")
        assertTrue(text.contains("98765"))
    }

    @Test fun readsLargePrintedTextFromImageLocally() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText("KOSHVISTA 12345", 45f, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 100f
        })
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val text = DocumentTextReader(InstrumentationRegistry.getInstrumentation().targetContext).read(bytes, "image/png")
        assertTrue(text.contains("12345"))
    }
}
