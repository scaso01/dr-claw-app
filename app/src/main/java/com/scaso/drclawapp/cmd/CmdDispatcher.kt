package com.scaso.drclawapp.cmd

import android.content.Context
import com.scaso.drclawapp.data.websocket.CmdDispatcherInterface
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Dispatches incoming cmd frames from Ironjaw to the appropriate handler.
 * Implements the KMP-safe CmdDispatcherInterface from the data layer.
 * Lives outside data/ because handlers require Android Context.
 */
class CmdDispatcher(private val context: Context) : CmdDispatcherInterface {
    private val handlers = mutableMapOf<String, CmdFrameHandler>()

    init {
        register(DeviceInfoHandler())
        register(ClipboardGetHandler())
        register(ClipboardSetHandler())
        register(CalendarHandler())
        register(LocationHandler())
        register(ActivityHandler())
    }

    private fun register(handler: CmdFrameHandler) {
        handlers[handler.tool] = handler
    }

    /**
     * Dispatch a command to its handler.
     * @throws IllegalArgumentException if the tool is not registered.
     */
    override suspend fun dispatch(tool: String, params: JsonObject): JsonElement {
        val handler = handlers[tool]
            ?: throw IllegalArgumentException("Unknown command: $tool")
        return handler.execute(params, context)
    }

    /** List all registered tool names. */
    fun supportedTools(): List<String> = handlers.keys.toList()
}
