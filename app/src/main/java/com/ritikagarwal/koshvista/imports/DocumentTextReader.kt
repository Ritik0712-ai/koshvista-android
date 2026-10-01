package com.ritikagarwal.koshvista.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Local OCR preview. Results remain unposted until an import adapter and user review approve them. */
class DocumentTextReader(private val context: Context) {
    suspend fun read(bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "Document must be under 20 MB" }
        val detectedType = when {
            bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF" -> "application/pdf"
            mimeType == "application/octet-stream" && bytes.size >= 4 && bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            else -> mimeType
        }
        when (detectedType) {
            "application/pdf" -> readPdf(bytes)
            "image/png", "image/jpeg", "image/webp" -> readImage(bytes)
            else -> error("Choose a PDF, PNG, JPEG or WebP image")
        }
    }

    private suspend fun readPdf(bytes: ByteArray): String {
        val temporary = File.createTempFile("ocr-", ".pdf", context.cacheDir)
        try {
            temporary.writeBytes(bytes)
            ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    require(pdf.pageCount in 1..30) { "PDF must have 1 to 30 pages" }
                    val pages = mutableListOf<String>()
                    for (index in 0 until pdf.pageCount) {
                        pdf.openPage(index).use { page ->
                            val scale = minOf(2f, 2200f / maxOf(page.width, page.height))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages += "Page ${index + 1}\n${recognise(bitmap)}"
                            } finally { bitmap.recycle() }
                        }
                    }
                    return pages.joinToString("\n\n")
                }
            }
        } finally { temporary.delete() }
    }

    private suspend fun readImage(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image is damaged" }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 8_000_000L) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Could not read image")
        return try { recognise(bitmap) } finally { bitmap.recycle() }
    }

    private suspend fun recognise(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
        } finally { recognizer.close() }
    }
}
