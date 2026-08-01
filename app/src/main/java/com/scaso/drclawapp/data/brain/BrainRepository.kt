package com.scaso.drclawapp.data.brain

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray

/**
 * Repository for Ironjaw brain (RAG memory) operations.
 * No Android imports -- KMP-extractable.
 */
class BrainRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _memories = MutableStateFlow<List<BrainMemory>>(emptyList())
    val memories: StateFlow<List<BrainMemory>> = _memories

    private val _entities = MutableStateFlow<List<GraphEntity>>(emptyList())
    val entities: StateFlow<List<GraphEntity>> = _entities

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _contextVars = MutableStateFlow<List<ContextVariable>>(emptyList())
    val contextVars: StateFlow<List<ContextVariable>> = _contextVars

    private val _summaries = MutableStateFlow<List<BrainMemory>>(emptyList())
    val summaries: StateFlow<List<BrainMemory>> = _summaries

    private val _timelineResults = MutableStateFlow<List<BrainMemory>>(emptyList())
    val timelineResults: StateFlow<List<BrainMemory>> = _timelineResults

    private val _brainStatus = MutableStateFlow<BrainStatus?>(null)
    val brainStatus: StateFlow<BrainStatus?> = _brainStatus

    private val _pendingMemories = MutableStateFlow<List<PendingMemory>>(emptyList())
    val pendingMemories: StateFlow<List<PendingMemory>> = _pendingMemories

    private val _snapshots = MutableStateFlow<List<BrainSnapshot>>(emptyList())
    val snapshots: StateFlow<List<BrainSnapshot>> = _snapshots

    fun loadSnapshots() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("brain.snapshot.list")
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(
                        SnapshotListResult.serializer(), response.payload!!
                    )
                    _snapshots.value = result.snapshots
                } else {
                    _error.value = response.error?.message ?: "Failed to load snapshots"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun createSnapshot(label: String): Boolean {
        return try {
            val params = buildJsonObject { put("label", label) }
            val response = gatewayClient.sendGenericRequest("brain.snapshot.create", params)
            if (!response.ok) _error.value = response.error?.message ?: "Snapshot failed"
            response.ok
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /** Destructive: atomically swaps the live memory set to the snapshot. */
    suspend fun restoreSnapshot(id: Long): SnapshotRestoreResult? {
        return try {
            val params = buildJsonObject { put("id", id) }
            val response = gatewayClient.sendGenericRequest("brain.snapshot.restore", params)
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(
                    SnapshotRestoreResult.serializer(), response.payload!!
                )
            } else {
                _error.value = response.error?.message ?: "Restore failed"
                null
            }
        } catch (e: Exception) {
            _error.value = e.message
            null
        }
    }

    suspend fun deleteSnapshot(id: Long): Boolean {
        return try {
            val params = buildJsonObject { put("id", id) }
            val response = gatewayClient.sendGenericRequest("brain.snapshot.delete", params)
            if (!response.ok) _error.value = response.error?.message ?: "Delete failed"
            response.ok
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    fun recall(query: String, detail: String = "full") {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val params = buildJsonObject {
                    put("query", query)
                    put("detail", detail)
                }
                val response = gatewayClient.sendGenericRequest("brain.recall", params)
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(RecallResult.serializer(), response.payload!!)
                    _memories.value = result.memories
                } else {
                    _error.value = response.error?.message ?: "Recall failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun remember(content: String): Boolean {
        return try {
            val params = buildJsonObject { put("content", content) }
            val response = gatewayClient.sendGenericRequest("brain.remember", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun forget(memoryId: String): Boolean {
        return try {
            val params = buildJsonObject { put("id", memoryId) }
            val response = gatewayClient.sendGenericRequest("brain.forget", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    fun loadGraph() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("brain.graph")
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(GraphResult.serializer(), response.payload!!)
                    _entities.value = result.entities
                } else {
                    _error.value = response.error?.message ?: "Failed to load graph"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStatus() {
        scope.launch {
            try {
                val response = gatewayClient.sendGenericRequest("brain.status")
                if (response.ok && response.payload != null) {
                    _brainStatus.value = json.decodeFromJsonElement(BrainStatus.serializer(), response.payload!!)
                }
            } catch (_: Exception) {
                // Status is best-effort
            }
        }
    }

    fun timeline(at: String, limit: Int = 10) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val params = buildJsonObject {
                    put("at", at)
                    put("limit", limit)
                }
                val response = gatewayClient.sendGenericRequest("brain.timeline", params)
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(RecallResult.serializer(), response.payload!!)
                    _timelineResults.value = result.memories
                } else {
                    _error.value = response.error?.message ?: "Timeline query failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSummaries(sessionId: String? = null, limit: Int = 20) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val params = buildJsonObject {
                    sessionId?.let { put("session_id", it) }
                    put("limit", limit)
                }
                val response = gatewayClient.sendGenericRequest("brain.summaries", params)
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(RecallResult.serializer(), response.payload!!)
                    _summaries.value = result.memories
                } else {
                    _error.value = response.error?.message ?: "Failed to load summaries"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadContextVars() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("brain.context.list")
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(ContextListResult.serializer(), response.payload!!)
                    _contextVars.value = result.variables
                } else {
                    _error.value = response.error?.message ?: "Failed to load context variables"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun contextSet(name: String, content: String): Boolean {
        return try {
            val params = buildJsonObject {
                put("name", name)
                put("content", content)
            }
            val response = gatewayClient.sendGenericRequest("brain.context.set", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun contextLoad(name: String, loaded: Boolean): Boolean {
        return try {
            val params = buildJsonObject {
                put("name", name)
                put("loaded", loaded)
            }
            val response = gatewayClient.sendGenericRequest("brain.context.load", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun contextDelete(name: String): Boolean {
        return try {
            val params = buildJsonObject { put("name", name) }
            val response = gatewayClient.sendGenericRequest("brain.context.delete", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun contextPeek(name: String, offset: Int = 0, limit: Int = 2000): PeekResult? {
        return try {
            val params = buildJsonObject {
                put("name", name)
                put("offset", offset)
                put("limit", limit)
            }
            val response = gatewayClient.sendGenericRequest("brain.context.peek", params)
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(PeekResult.serializer(), response.payload!!)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun listPending(limit: Int = 50) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val params = buildJsonObject {
                    put("limit", limit)
                    put("offset", 0)
                }
                val response = gatewayClient.sendGenericRequest("brain.pending.list", params)
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(PendingListResult.serializer(), response.payload!!)
                    _pendingMemories.value = result.memories
                } else {
                    _error.value = response.error?.message ?: "Failed to load pending memories"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approvePending(id: Long) {
        scope.launch {
            try {
                val params = buildJsonObject { put("id", id) }
                val response = gatewayClient.sendGenericRequest("brain.pending.approve", params)
                if (response.ok) {
                    _pendingMemories.value = _pendingMemories.value.filter { it.id != id }
                } else {
                    _error.value = response.error?.message ?: "Failed to approve memory"
                }
            } catch (e: Exception) {
                _error.value = "Failed to approve: ${e.message}"
            }
        }
    }

    fun rejectPending(id: Long) {
        scope.launch {
            try {
                val params = buildJsonObject { put("id", id) }
                val response = gatewayClient.sendGenericRequest("brain.pending.reject", params)
                if (response.ok) {
                    _pendingMemories.value = _pendingMemories.value.filter { it.id != id }
                } else {
                    _error.value = response.error?.message ?: "Failed to reject memory"
                }
            } catch (e: Exception) {
                _error.value = "Failed to reject: ${e.message}"
            }
        }
    }
}
