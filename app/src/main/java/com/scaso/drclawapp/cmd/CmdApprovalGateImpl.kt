package com.scaso.drclawapp.cmd

import com.scaso.drclawapp.data.approval.ApprovalDecision
import com.scaso.drclawapp.data.approval.ApprovalRequest
import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.approval.ApprovalSeverity
import com.scaso.drclawapp.data.approval.CmdApprovalGate
import com.scaso.drclawapp.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Human-in-the-loop gate for incoming device `cmd` frames from Ironjaw.
 *
 * Every command requires an explicit user Approve tap unless the tool was previously
 * marked "always allow" (persisted in [AppPreferences.autoApproveTools], the same
 * allowlist the native-chat agentic tool flow already uses). Requests are surfaced via
 * the existing global [ApprovalDialog][com.scaso.drclawapp.ui.approval.ApprovalDialog]
 * (routed through [ApprovalRepository.requestLocalApproval]); if the app is backgrounded,
 * [notifyBackgrounded] is invoked so the caller can surface a notification. GatewayClient
 * enforces the timeout and fail-closed behavior -- this class just resolves true/false.
 *
 * @param isAppInForeground Reports current foreground state (production: [DrClawApp][com.scaso.drclawapp.DrClawApp.isAppInForeground]).
 * @param notifyBackgrounded Callback invoked with the tool name when a request arrives while
 * the app is backgrounded. Both are plain lambdas (rather than direct Android dependencies)
 * so this class stays Context-free and unit-testable.
 */
class CmdApprovalGateImpl(
    private val approvalRepository: ApprovalRepository,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope,
    private val isAppInForeground: () -> Boolean,
    private val notifyBackgrounded: (String) -> Unit,
) : CmdApprovalGate {

    override suspend fun requestApproval(tool: String, params: JsonObject): Boolean {
        if (appPreferences.autoApproveTools.first().contains(tool)) {
            return true
        }

        val request = ApprovalRequest(
            id = UUID.randomUUID().toString(),
            action = tool,
            description = "Gateway is requesting to run \"$tool\" on this device.",
            context = params.toString(),
            severity = ApprovalSeverity.HIGH,
            createdAt = System.currentTimeMillis(),
            allowAlwaysOption = true,
        )

        if (!isAppInForeground()) {
            notifyBackgrounded(tool)
        }

        return suspendCancellableCoroutine { cont ->
            approvalRepository.requestLocalApproval(request) { decision ->
                when (decision) {
                    ApprovalDecision.APPROVE -> {
                        if (cont.isActive) cont.resume(true)
                    }
                    ApprovalDecision.ALWAYS_ALLOW -> {
                        scope.launch { appPreferences.addAutoApproveTool(tool) }
                        if (cont.isActive) cont.resume(true)
                    }
                    ApprovalDecision.DENY, ApprovalDecision.MODIFY -> {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }
    }
}
