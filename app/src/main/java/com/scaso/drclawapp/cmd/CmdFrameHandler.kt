package com.scaso.drclawapp.cmd

import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Interface for device command handlers invoked by Ironjaw via cmd frames.
 * Implementations live outside data/ because they require Android Context.
 */
interface CmdFrameHandler {
    /** The tool name this handler responds to (e.g. "device.info"). */
    val tool: String

    /** Execute the command and return a JSON result payload. */
    suspend fun execute(params: JsonObject, context: Context): JsonElement
}
