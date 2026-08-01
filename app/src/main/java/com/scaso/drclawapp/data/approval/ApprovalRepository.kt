package com.scaso.drclawapp.data.approval

import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for HITL approval flow.
 * Listens for WaitForApproval gateway events and provides
 * approve/deny/modify response RPCs.
 *
 * No Android imports -- KMP-extractable.
 */
class ApprovalRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _pendingApprovals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = _pendingApprovals

    private val _approvalEvents = MutableSharedFlow<ApprovalRequest>(extraBufferCapacity = 16)
    val approvalEvents: SharedFlow<ApprovalRequest> = _approvalEvents

    /**
     * Callbacks for requests that originated locally on-device (e.g. an incoming
     * gateway `cmd` frame) rather than from a gateway `permission.request` RPC event.
     * [respond] resolves these via the callback instead of relaying an RPC response.
     */
    private val localResponders = java.util.concurrent.ConcurrentHashMap<String, (ApprovalDecision) -> Unit>()

    init {
        // Listen for permission.request gateway events and convert to ApprovalRequest
        scope.launch {
            gatewayClient.events.collect { event ->
                if (event is GatewayEvent.PermissionRequest) {
                    val request = ApprovalRequest(
                        id = event.requestId,
                        action = event.tool,
                        description = event.description,
                        createdAt = System.currentTimeMillis(),
                    )
                    _pendingApprovals.value = _pendingApprovals.value + request
                    _approvalEvents.emit(request)
                }
            }
        }
    }

    /**
     * Enqueue an approval request sourced locally on-device rather than from a gateway RPC
     * (e.g. an incoming `cmd` frame). [onDecision] is invoked once, synchronously, when the
     * user responds via [respond] -- it is never routed to the gateway as a permission RPC.
     */
    fun requestLocalApproval(request: ApprovalRequest, onDecision: (ApprovalDecision) -> Unit) {
        localResponders[request.id] = onDecision
        _pendingApprovals.value = _pendingApprovals.value + request
        scope.launch { _approvalEvents.emit(request) }
    }

    suspend fun respond(requestId: String, decision: ApprovalDecision, modification: String? = null): Boolean {
        val localResponder = localResponders.remove(requestId)
        if (localResponder != null) {
            _pendingApprovals.value = _pendingApprovals.value.filter { it.id != requestId }
            localResponder(decision)
            return true
        }

        return try {
            when (decision) {
                ApprovalDecision.APPROVE -> gatewayClient.approvePermission(requestId)
                ApprovalDecision.DENY -> gatewayClient.denyPermission(requestId)
                ApprovalDecision.MODIFY -> {
                    // Send modified approval with the adjustment
                    val params = buildJsonObject {
                        put("request_id", requestId)
                        put("decision", "modify")
                        modification?.let { put("modification", it) }
                    }
                    gatewayClient.sendGenericRequest("approval.respond", params)
                }
                ApprovalDecision.ALWAYS_ALLOW -> {
                    // RPC-sourced requests never offer this option (only local cmd requests do);
                    // treat as a plain approve if it is ever reached defensively.
                    gatewayClient.approvePermission(requestId)
                }
            }
            _pendingApprovals.value = _pendingApprovals.value.filter { it.id != requestId }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Phase 16 native-chat HITL: send a cmd.res frame with ok + scope data for a
     * [com.scaso.drclawapp.data.websocket.GatewayEvent.NativePermissionRequest].
     * Distinct from [respond] -- native requests aren't tracked in [pendingApprovals].
     */
    fun respondNative(requestId: String, ok: Boolean, scope: String) {
        gatewayClient.sendNativePermissionResponse(requestId, ok, scope)
    }

    fun dismissExpired() {
        val now = System.currentTimeMillis()
        _pendingApprovals.value = _pendingApprovals.value.filter { request ->
            now - request.createdAt < request.timeoutMs
        }
    }
}
