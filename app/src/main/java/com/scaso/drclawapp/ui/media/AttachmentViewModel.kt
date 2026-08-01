package com.scaso.drclawapp.ui.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import com.scaso.drclawapp.data.model.Attachment
import com.scaso.drclawapp.data.websocket.RpcAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class PreparedAttachments(
    val imageAttachments: List<RpcAttachment>,
    val inlineText: String,
    val unsupportedFiles: List<String>,
)

@HiltViewModel
class AttachmentViewModel @Inject constructor() : ViewModel() {

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    fun addAttachment(
        uri: Uri,
        mimeType: String,
        displayName: String,
        sizeBytes: Long = 0,
    ) {
        val attachment = Attachment(
            id = UUID.randomUUID().toString(),
            uri = uri,
            mimeType = mimeType,
            displayName = displayName,
            sizeBytes = sizeBytes,
        )
        _pendingAttachments.update { current -> current + attachment }
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.update { current ->
            current.filter { it.id != id }
        }
    }

    fun clearAttachments() {
        _pendingAttachments.value = emptyList()
    }

    fun prepareAttachments(context: Context): PreparedAttachments {
        val attachments = _pendingAttachments.value
        if (attachments.isEmpty()) return PreparedAttachments(emptyList(), "", emptyList())

        val images = mutableListOf<RpcAttachment>()
        val textParts = mutableListOf<String>()
        val unsupported = mutableListOf<String>()

        for (attachment in attachments) {
            try {
                val mime = attachment.mimeType
                val name = attachment.displayName
                val ext = name.substringAfterLast('.', "").lowercase()

                when {
                    // Images → base64 attachment (gateway supports natively)
                    mime.startsWith("image/") -> {
                        val bytes = readBytes(context, attachment.uri) ?: continue
                        val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        images.add(
                            RpcAttachment(
                                mimeType = mime,
                                content = base64Data,
                                fileName = name,
                            )
                        )
                    }

                    // PDF → render pages as images (Claude reads the images)
                    mime == "application/pdf" || ext == "pdf" -> {
                        val pdfImages = FileContentExtractor.extractPdfAsImages(context, attachment.uri)
                        if (pdfImages.isNotEmpty()) {
                            images.addAll(pdfImages)
                        } else {
                            unsupported.add(name)
                        }
                    }

                    // DOCX → extract text from ZIP/XML
                    ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                        val text = FileContentExtractor.extractDocxText(context, attachment.uri)
                        if (!text.isNullOrBlank()) {
                            textParts.add("[File: $name]\n$text")
                        } else {
                            unsupported.add(name)
                        }
                    }

                    // XLSX → extract cell data as text table
                    ext == "xlsx" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                        val text = FileContentExtractor.extractXlsxText(context, attachment.uri)
                        if (!text.isNullOrBlank()) {
                            textParts.add("[File: $name]\n$text")
                        } else {
                            unsupported.add(name)
                        }
                    }

                    // Known text MIME types → read as UTF-8
                    isTextMime(mime) -> {
                        val bytes = readBytes(context, attachment.uri) ?: continue
                        textParts.add("[File: $name]\n${bytes.decodeToString()}")
                    }

                    // Unknown → try to read as text, detect binary
                    else -> {
                        val bytes = readBytes(context, attachment.uri) ?: continue
                        val text = FileContentExtractor.tryReadAsText(bytes)
                        if (text != null) {
                            textParts.add("[File: $name]\n$text")
                        } else {
                            unsupported.add(name)
                        }
                    }
                }
            } catch (_: Throwable) {
                unsupported.add(attachment.displayName)
            }
        }

        return PreparedAttachments(
            imageAttachments = images,
            inlineText = textParts.joinToString("\n\n"),
            unsupportedFiles = unsupported,
        )
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun isTextMime(mime: String): Boolean {
        if (mime.startsWith("text/")) return true
        return mime in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
            "application/x-sh",
            "application/sql",
            "application/csv",
            "application/x-python",
            "application/x-kotlin",
            "application/x-java",
            "application/rtf",
        )
    }
}
