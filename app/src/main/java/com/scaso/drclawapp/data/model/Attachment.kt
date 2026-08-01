package com.scaso.drclawapp.data.model

import android.net.Uri

data class Attachment(
    val id: String,
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long = 0,
)
