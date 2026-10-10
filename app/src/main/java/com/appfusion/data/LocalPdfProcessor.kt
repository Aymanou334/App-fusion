package com.appfusion.data

import android.content.ContentResolver
import android.net.Uri
import java.io.FilterInputStream
import java.io.InputStream
import com.appfusion.domain.WorkflowOperations
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalPdfProcessor(private val resolver: ContentResolver, private val initializePdfBox: () -> Unit) : WorkflowOperations {
    override suspend fun extractPdf(uri: String): String = withContext(Dispatchers.IO) {
        initializePdfBox()
        val stream = resolver.openInputStream(Uri.parse(uri)) ?: error("App Fusion could not open this shared file. Try selecting it again.")
        stream.use { source ->
            val input = BoundedPdfStream(source, MAX_PDF_BYTES)
            try {
              PDDocument.load(input).use { document ->
                require(!document.isEncrypted) { "This PDF is password protected. Unlock it before sharing." }
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                require(document.numberOfPages <= MAX_PAGES) { "This PDF has more than $MAX_PAGES pages. Split it into smaller files and retry." }
                val text = PDFTextStripper().getText(document).take(MAX_TEXT_CHARS).trim()
                require(text.isNotEmpty()) { "No selectable text was found. Scanned PDFs need OCR, which is not available yet." }
                text
              }
            } catch (error: Exception) {
                if (error.message?.contains("MAX_PDF_BYTES") == true) throw IllegalArgumentException("This PDF is larger than 50 MB. Choose a smaller file.", error)
                if (error is IllegalArgumentException) throw error
                throw IllegalArgumentException("This file could not be read as a PDF. Try exporting it again or sharing an unlocked copy.", error)
            }
        }
    }
    override fun summarize(text: String): String = text.split(Regex("(?<=[.!?])\\s+"))
        .filter(String::isNotBlank).take(3).joinToString(" ").ifBlank { text.trim() }
    companion object { const val MAX_PAGES = 200; const val MAX_TEXT_CHARS = 100_000; const val MAX_PDF_BYTES = 50L * 1024 * 1024 }
}

private class BoundedPdfStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int {
        val next = super.read()
        if (next >= 0 && ++count > limit) throw java.io.IOException("MAX_PDF_BYTES exceeded")
        return next
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val allowed = minOf(length.toLong(), (limit - count + 1).coerceAtLeast(1)).toInt()
        val read = super.read(buffer, offset, allowed)
        if (read > 0) {
            count += read
            if (count > limit) throw java.io.IOException("MAX_PDF_BYTES exceeded")
        }
        return read
    }
}
