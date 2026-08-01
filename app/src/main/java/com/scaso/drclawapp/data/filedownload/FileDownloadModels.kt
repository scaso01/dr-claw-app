package com.scaso.drclawapp.data.filedownload

import kotlinx.serialization.Serializable

/**
 * File download models. Gateway proxies file reads from docker-host.
 *
 * No Android imports — KMP-extractable.
 */

@Serializable
data class FileGetResponse(
    val path: String = "",
    val fileName: String = "",
    val mimeType: String = "application/octet-stream",
    val content: String = "",
    val sizeBytes: Long = 0,
)

/**
 * Detects file path references in message text.
 * Matches Windows paths (C:\...) and explicit [file: ...] markers.
 */
object FilePathDetector {
    // Windows absolute path: C:\Users\... ending in a supported extension
    private val windowsPathRegex = Regex(
        """[A-Z]:\\(?:[^\s\\<>"|?*]+\\)*[^\s\\<>"|?*]+\.(?:md|pdf|csv|json|txt|docx)""",
        RegexOption.IGNORE_CASE,
    )

    // Explicit file marker: [file: path/to/file.ext]
    private val fileMarkerRegex = Regex(
        """\[file:\s*(.+?)]""",
        RegexOption.IGNORE_CASE,
    )

    fun findFilePaths(text: String): List<String> {
        val paths = mutableSetOf<String>()
        windowsPathRegex.findAll(text).forEach { paths.add(it.value) }
        fileMarkerRegex.findAll(text).forEach { paths.add(it.groupValues[1].trim()) }
        return paths.toList()
    }
}
