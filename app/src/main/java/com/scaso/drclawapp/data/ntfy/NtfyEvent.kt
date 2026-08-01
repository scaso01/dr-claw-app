package com.scaso.drclawapp.data.ntfy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A notification event received from an ntfy server via SSE.
 *
 * See https://docs.ntfy.sh/subscribe/api/#json-message-format
 */
@Serializable
data class NtfyNotification(
    val id: String,
    val time: Long,
    val event: String = "message",
    val topic: String = "",
    val title: String? = null,
    val message: String = "",
    val priority: Int = 3,
    val tags: List<String> = emptyList(),
    val click: String? = null,
)

/**
 * Extracts a session key from an ntfy notification's click URL.
 *
 * Primary format: `drclaw://session/<sessionKey>` (sent by Dr. CLAW)
 * Legacy format:  `drclaw://chat?session=<sessionKey>`
 */
fun NtfyNotification.extractSessionKey(): String? {
    val url = click ?: return null
    val pathPrefix = "drclaw://session/"
    if (url.startsWith(pathPrefix)) {
        return url.removePrefix(pathPrefix).takeIf { it.isNotBlank() }
    }
    val queryPrefix = "drclaw://chat?session="
    if (url.startsWith(queryPrefix)) {
        return url.removePrefix(queryPrefix).takeIf { it.isNotBlank() }
    }
    return null
}
