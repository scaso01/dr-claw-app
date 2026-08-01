package com.scaso.drclawapp.data.filedownload

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDownloadModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- FileGetResponse deserialization ---

    @Test
    fun `deserializes full FileGetResponse`() {
        val raw = """{
            "path": "C:\\Users\\deploy\\Projects\\report.pdf",
            "fileName": "report.pdf",
            "mimeType": "application/pdf",
            "content": "SGVsbG8gV29ybGQ=",
            "sizeBytes": 1024
        }"""
        val r = json.decodeFromString(FileGetResponse.serializer(), raw)
        assertEquals("C:\\Users\\deploy\\Projects\\report.pdf", r.path)
        assertEquals("report.pdf", r.fileName)
        assertEquals("application/pdf", r.mimeType)
        assertEquals("SGVsbG8gV29ybGQ=", r.content)
        assertEquals(1024L, r.sizeBytes)
    }

    @Test
    fun `deserializes minimal FileGetResponse`() {
        val raw = """{}"""
        val r = json.decodeFromString(FileGetResponse.serializer(), raw)
        assertEquals("", r.path)
        assertEquals("", r.fileName)
        assertEquals("application/octet-stream", r.mimeType)
        assertEquals("", r.content)
        assertEquals(0L, r.sizeBytes)
    }

    @Test
    fun `FileGetResponse round-trip preserves data`() {
        val original = FileGetResponse(
            path = "C:\\file.txt",
            fileName = "file.txt",
            mimeType = "text/plain",
            content = "dGVzdA==",
            sizeBytes = 4,
        )
        val serialized = json.encodeToString(FileGetResponse.serializer(), original)
        val deserialized = json.decodeFromString(FileGetResponse.serializer(), serialized)
        assertEquals(original, deserialized)
    }

    // --- FilePathDetector ---

    @Test
    fun `detects Windows absolute path with md extension`() {
        val text = "Check the file at C:\\Users\\deploy\\Projects\\README.md for details"
        val paths = FilePathDetector.findFilePaths(text)
        assertEquals(1, paths.size)
        assertEquals("C:\\Users\\deploy\\Projects\\README.md", paths[0])
    }

    @Test
    fun `detects Windows path with pdf extension`() {
        val text = "Report saved to D:\\Reports\\quarterly.pdf"
        val paths = FilePathDetector.findFilePaths(text)
        assertEquals(1, paths.size)
        assertEquals("D:\\Reports\\quarterly.pdf", paths[0])
    }

    @Test
    fun `detects multiple Windows paths`() {
        val text = "Compare C:\\data\\input.csv with C:\\data\\output.json"
        val paths = FilePathDetector.findFilePaths(text)
        assertEquals(2, paths.size)
        assertTrue(paths.contains("C:\\data\\input.csv"))
        assertTrue(paths.contains("C:\\data\\output.json"))
    }

    @Test
    fun `detects file marker format`() {
        val text = "Here is the file: [file: C:\\Users\\deploy\\report.pdf]"
        val paths = FilePathDetector.findFilePaths(text)
        assertTrue(paths.contains("C:\\Users\\deploy\\report.pdf"))
    }

    @Test
    fun `detects file marker with spaces around path`() {
        val text = "[file:  /home/user/data.csv  ]"
        val paths = FilePathDetector.findFilePaths(text)
        assertTrue(paths.contains("/home/user/data.csv"))
    }

    @Test
    fun `detects both marker and bare path in same text`() {
        val text = "See [file: C:\\docs\\notes.txt] and also C:\\docs\\backup.md"
        val paths = FilePathDetector.findFilePaths(text)
        assertTrue(paths.size >= 2)
        assertTrue(paths.contains("C:\\docs\\notes.txt"))
        assertTrue(paths.contains("C:\\docs\\backup.md"))
    }

    @Test
    fun `returns empty for text with no paths`() {
        val text = "This is just regular text with no file references."
        val paths = FilePathDetector.findFilePaths(text)
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `returns empty for empty string`() {
        val paths = FilePathDetector.findFilePaths("")
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `deduplicates when same path appears as both marker and bare`() {
        val text = "File: [file: C:\\data\\report.csv] is at C:\\data\\report.csv"
        val paths = FilePathDetector.findFilePaths(text)
        // Uses a Set internally, so should deduplicate
        assertEquals(1, paths.count { it == "C:\\data\\report.csv" })
    }

    @Test
    fun `detects docx extension`() {
        val text = "Open C:\\Documents\\resume.docx to review"
        val paths = FilePathDetector.findFilePaths(text)
        assertEquals(1, paths.size)
        assertEquals("C:\\Documents\\resume.docx", paths[0])
    }

    @Test
    fun `detects txt extension`() {
        val text = "Log at C:\\logs\\error.txt"
        val paths = FilePathDetector.findFilePaths(text)
        assertEquals(1, paths.size)
    }

    @Test
    fun `ignores unsupported extensions`() {
        val text = "Image at C:\\pics\\photo.png"
        val paths = FilePathDetector.findFilePaths(text)
        assertTrue(paths.isEmpty())
    }

    // --- DownloadState enum ---

    @Test
    fun `DownloadState has all expected values`() {
        assertEquals(3, DownloadState.entries.size)
        assertEquals(DownloadState.DOWNLOADING, DownloadState.valueOf("DOWNLOADING"))
        assertEquals(DownloadState.COMPLETE, DownloadState.valueOf("COMPLETE"))
        assertEquals(DownloadState.ERROR, DownloadState.valueOf("ERROR"))
    }
}
