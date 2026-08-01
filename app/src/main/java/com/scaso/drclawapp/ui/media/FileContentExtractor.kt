package com.scaso.drclawapp.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.scaso.drclawapp.data.websocket.RpcAttachment
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

/**
 * Extracts sendable content from various file types.
 *
 * - Images: passed through as-is (handled separately)
 * - PDFs: rendered page-by-page as JPEG images via [PdfRenderer]
 * - DOCX: text extracted from word/document.xml inside the ZIP
 * - XLSX: cell data extracted from shared strings + worksheet XML
 * - Text files: read as UTF-8 (handled separately)
 * - Other: attempted UTF-8 read with binary detection
 */
object FileContentExtractor {

    private const val MAX_PDF_PAGES = 10
    private const val PDF_RENDER_DPI = 150 // ~150 DPI for readability without huge size
    private const val JPEG_QUALITY = 80

    // --- PDF → images ---

    fun extractPdfAsImages(context: Context, uri: Uri): List<RpcAttachment> {
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            renderer = PdfRenderer(fd)
            val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
            val attachments = mutableListOf<RpcAttachment>()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // Scale based on target DPI (PDF points are 72 DPI)
                val scale = PDF_RENDER_DPI / 72f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                bitmap.recycle()

                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                attachments.add(
                    RpcAttachment(
                        mimeType = "image/jpeg",
                        content = base64,
                        fileName = "page_${i + 1}.jpg",
                    )
                )
            }

            return attachments
        } catch (_: Throwable) {
            return emptyList()
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { fd?.close() } catch (_: Throwable) {}
        }
    }

    // --- DOCX → text ---

    fun extractDocxText(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val zip = ZipInputStream(input)
            var result: String? = null

            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                    result = parseDocxXml(xml)
                }
                zip.closeEntry()
            }

            zip.close()
            result
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseDocxXml(xml: String): String {
        val sb = StringBuilder()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inParagraph = false
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "p" -> inParagraph = true
                            "br" -> sb.append("\n")
                            "tab" -> sb.append("\t")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        sb.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "p" && inParagraph) {
                            sb.append("\n")
                            inParagraph = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Throwable) {
            // Partial parse is OK
        }
        return sb.toString().trim()
    }

    // --- XLSX → text ---

    fun extractXlsxText(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val zip = ZipInputStream(input)
            val sharedStrings = mutableListOf<String>()
            val sheets = mutableMapOf<String, String>()

            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                        sharedStrings.addAll(parseSharedStrings(xml))
                    }
                    entry.name.startsWith("xl/worksheets/sheet") &&
                        entry.name.endsWith(".xml") -> {
                        sheets[entry.name] = zip.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
                zip.closeEntry()
            }

            zip.close()

            if (sheets.isEmpty()) return null

            val sb = StringBuilder()
            for ((name, xml) in sheets.entries.sortedBy { it.key }) {
                val sheetName = name.substringAfterLast("/").removeSuffix(".xml")
                if (sheets.size > 1) sb.append("--- $sheetName ---\n")
                sb.append(parseWorksheet(xml, sharedStrings))
                sb.append("\n")
            }

            sb.toString().trim()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inSi = false
            val currentText = StringBuilder()
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            inSi = true
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSi) currentText.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            strings.add(currentText.toString())
                            inSi = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Throwable) {}
        return strings
    }

    private fun parseWorksheet(xml: String, sharedStrings: List<String>): String {
        val rows = mutableMapOf<Int, MutableMap<String, String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var currentRow = 0
            var currentRef = ""
            var currentType = ""
            var inValue = false
            val cellValue = StringBuilder()
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                currentRow = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: 0
                            }
                            "c" -> {
                                currentRef = parser.getAttributeValue(null, "r") ?: ""
                                currentType = parser.getAttributeValue(null, "t") ?: ""
                            }
                            "v" -> {
                                inValue = true
                                cellValue.clear()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) cellValue.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "v") {
                            inValue = false
                            val value = if (currentType == "s") {
                                val idx = cellValue.toString().toIntOrNull()
                                if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else cellValue.toString()
                            } else {
                                cellValue.toString()
                            }
                            val col = currentRef.replace(Regex("[0-9]"), "")
                            rows.getOrPut(currentRow) { mutableMapOf() }[col] = value
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Throwable) {}

        if (rows.isEmpty()) return ""

        // Collect all column letters
        val allCols = rows.values.flatMap { it.keys }.toSortedSet()
        val sb = StringBuilder()
        for (rowNum in rows.keys.sorted()) {
            val row = rows[rowNum] ?: continue
            sb.append(allCols.joinToString("\t") { col -> row[col] ?: "" })
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- Generic binary detection ---

    /**
     * Attempts to read bytes as UTF-8 text. Returns null if the content
     * appears to be binary (>10% non-printable characters).
     */
    fun tryReadAsText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = bytes.decodeToString()
        val nonPrintable = text.count { ch ->
            ch != '\n' && ch != '\r' && ch != '\t' && ch.code < 32
        }
        return if (nonPrintable.toFloat() / text.length < 0.10f) text else null
    }
}
