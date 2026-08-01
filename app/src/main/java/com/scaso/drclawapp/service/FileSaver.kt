package com.scaso.drclawapp.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.scaso.drclawapp.data.filedownload.FileGetResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Saves downloaded files to the device's Downloads/DrClaw folder via MediaStore.
 * Android-specific — not in data/ layer.
 */
@Singleton
class FileSaver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @OptIn(ExperimentalEncodingApi::class)
    fun save(response: FileGetResponse): Uri? {
        val fileName = response.fileName.ifEmpty {
            response.path.substringAfterLast('\\').substringAfterLast('/')
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/DrClaw",
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(Base64.decode(response.content))
        }

        return uri
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()
        return when (ext) {
            "md", "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}
