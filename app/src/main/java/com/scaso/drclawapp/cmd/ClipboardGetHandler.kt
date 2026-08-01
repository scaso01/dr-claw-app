package com.scaso.drclawapp.cmd

import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads the current clipboard content.
 * Tool: clipboard.get
 */
class ClipboardGetHandler : CmdFrameHandler {
    override val tool: String = "clipboard.get"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context).toString()
        } else {
            ""
        }
        return buildJsonObject {
            put("text", text)
            put("hasClip", clip != null && clip.itemCount > 0)
        }
    }
}
