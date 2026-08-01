package com.scaso.drclawapp.cmd

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Sets the clipboard content.
 * Tool: clipboard.set
 * Params: { "text": "content to copy" }
 */
class ClipboardSetHandler : CmdFrameHandler {
    override val tool: String = "clipboard.set"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        val text = params["text"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing required param: text")

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Dr. CLAW", text)
        cm.setPrimaryClip(clip)

        return buildJsonObject {
            put("ok", true)
            put("length", text.length)
        }
    }
}
