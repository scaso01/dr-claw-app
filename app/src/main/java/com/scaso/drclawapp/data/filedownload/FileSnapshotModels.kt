package com.scaso.drclawapp.data.filedownload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * File snapshot models for checkpoint/undo system.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class FileSnapshot(
    val path: String,
    val timestamp: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val hash: String? = null,
)

@Serializable
data class FileSnapshotListResult(
    val snapshots: List<FileSnapshot> = emptyList(),
    val path: String = "",
)
