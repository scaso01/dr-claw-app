package com.scaso.drclawapp.data.approval

import kotlinx.serialization.json.JsonObject

/**
 * Gate consulted by GatewayClient before executing an incoming device `cmd` frame
 * (e.g. clipboard, location, calendar, activity, device info requests from the gateway).
 * No Android imports -- KMP-extractable.
 */
interface CmdApprovalGate {
    /**
     * Returns true if the command is approved for execution, false if it should be denied.
     * Implementations are expected to fail closed: any error, timeout, or unconfigured
     * state must resolve to false, never true.
     */
    suspend fun requestApproval(tool: String, params: JsonObject): Boolean
}
