package com.scaso.drclawapp.data.export

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.serialization.json.Json

/**
 * Repository for Ironjaw export/backup operations.
 * No Android imports -- KMP-extractable.
 */
class ExportRepository(
    private val gatewayClient: GatewayClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportConversations(): ExportResult {
        return try {
            val response = gatewayClient.sendGenericRequest("export.conversations")
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(ExportResult.serializer(), response.payload!!)
            } else {
                ExportResult(
                    success = false,
                    error = response.error?.message ?: "Export failed",
                )
            }
        } catch (e: Exception) {
            ExportResult(success = false, error = e.message)
        }
    }

    suspend fun exportBrain(): ExportResult {
        return try {
            val response = gatewayClient.sendGenericRequest("export.brain")
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(ExportResult.serializer(), response.payload!!)
            } else {
                ExportResult(
                    success = false,
                    error = response.error?.message ?: "Export failed",
                )
            }
        } catch (e: Exception) {
            ExportResult(success = false, error = e.message)
        }
    }

    suspend fun createBackup(): ExportResult {
        return try {
            val response = gatewayClient.sendGenericRequest("backup.create")
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(ExportResult.serializer(), response.payload!!)
            } else {
                ExportResult(
                    success = false,
                    error = response.error?.message ?: "Backup failed",
                )
            }
        } catch (e: Exception) {
            ExportResult(success = false, error = e.message)
        }
    }
}
