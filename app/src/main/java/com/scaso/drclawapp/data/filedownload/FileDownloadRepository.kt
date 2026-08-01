package com.scaso.drclawapp.data.filedownload

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for downloading files from docker-host via gateway proxy.
 *
 * No Android imports — KMP-extractable.
 */
class FileDownloadRepository(
    private val gatewayClient: GatewayClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadState>> = _downloadProgress

    /**
     * Request a file from the gateway. Returns base64 content or null on failure.
     */
    suspend fun downloadFile(path: String): FileGetResponse? {
        _downloadProgress.value = _downloadProgress.value + (path to DownloadState.DOWNLOADING)

        return try {
            val params = buildJsonObject { put("path", path) }
            val response = gatewayClient.sendGenericRequest("file.get", params)
            if (response.ok && response.payload != null) {
                val result = json.decodeFromString(
                    FileGetResponse.serializer(),
                    response.payload.toString(),
                )
                _downloadProgress.value = _downloadProgress.value + (path to DownloadState.COMPLETE)
                result
            } else {
                _downloadProgress.value = _downloadProgress.value + (path to DownloadState.ERROR)
                null
            }
        } catch (_: Exception) {
            _downloadProgress.value = _downloadProgress.value + (path to DownloadState.ERROR)
            null
        }
    }

    fun clearState(path: String) {
        _downloadProgress.value = _downloadProgress.value - path
    }
}

enum class DownloadState {
    DOWNLOADING,
    COMPLETE,
    ERROR,
}
