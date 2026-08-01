package com.scaso.drclawapp.data.chronicle

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Repository for CC Chronicle session data.
 * No Android imports -- KMP-extractable.
 */
class ChronicleRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessions = MutableStateFlow<List<ChronicleSession>>(emptyList())
    val sessions: StateFlow<List<ChronicleSession>> = _sessions

    private val _searchResults = MutableStateFlow<List<ChronicleSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ChronicleSearchResult>> = _searchResults

    private val _topics = MutableStateFlow<List<ChronicleTopic>>(emptyList())
    val topics: StateFlow<List<ChronicleTopic>> = _topics

    private val _stats = MutableStateFlow<ChronicleStats?>(null)
    val stats: StateFlow<ChronicleStats?> = _stats

    private val _insights = MutableStateFlow<ChronicleInsights?>(null)
    val insights: StateFlow<ChronicleInsights?> = _insights

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun getSessions() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("chronicle.sessions")
                if (response.ok && response.payload != null) {
                    // cc-chronicle wraps in {"sessions": [{session: {}, summary: {}}, ...]}
                    val obj = response.payload!!.jsonObject
                    val arr = obj["sessions"]?.jsonArray ?: run {
                        _error.value = "Unexpected response format: missing 'sessions' key"
                        _isLoading.value = false
                        return@launch
                    }
                    _sessions.value = arr.map { item ->
                        val itemObj = item.jsonObject
                        val session = itemObj["session"]?.jsonObject
                        val summary = itemObj["summary"]?.jsonObject
                        if (session != null) {
                            // Nested format: extract from session + summary
                            ChronicleSession(
                                id = session["session_id"]?.jsonPrimitive?.contentOrNull
                                    ?: session["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                title = summary?.get("title")?.jsonPrimitive?.contentOrNull,
                                machine = session["machine"]?.jsonPrimitive?.contentOrNull,
                                date = session["started_at"]?.jsonPrimitive?.contentOrNull,
                                summary = summary?.get("summary")?.jsonPrimitive?.contentOrNull,
                                project = session["project"]?.jsonPrimitive?.contentOrNull,
                            )
                        } else {
                            // Flat format fallback
                            json.decodeFromJsonElement(ChronicleSession.serializer(), item)
                        }
                    }
                } else {
                    _error.value = response.error?.message ?: "Failed to load sessions"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val params = buildJsonObject { put("query", query) }
                val response = gatewayClient.sendGenericRequest("chronicle.search", params)
                if (response.ok && response.payload != null) {
                    // cc-chronicle wraps in {"results": [...]}
                    val obj = response.payload!!.jsonObject
                    val arr = obj["results"]?.jsonArray ?: response.payload!!.jsonArray
                    _searchResults.value = arr.map { item ->
                        val itemObj = item.jsonObject
                        val session = itemObj["session"]?.jsonObject
                        val summary = itemObj["summary"]?.jsonObject
                        if (session != null) {
                            ChronicleSearchResult(
                                sessionId = session["session_id"]?.jsonPrimitive?.contentOrNull
                                    ?: session["id"]?.jsonPrimitive?.contentOrNull,
                                title = summary?.get("title")?.jsonPrimitive?.contentOrNull,
                                snippet = summary?.get("summary")?.jsonPrimitive?.contentOrNull,
                                score = itemObj["score"]?.jsonPrimitive?.floatOrNull,
                            )
                        } else {
                            json.decodeFromJsonElement(ChronicleSearchResult.serializer(), item)
                        }
                    }
                } else {
                    _error.value = response.error?.message ?: "Search failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getTopics() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("chronicle.topics")
                if (response.ok && response.payload != null) {
                    // cc-chronicle wraps in {"topics": [...]}
                    val obj = response.payload!!.jsonObject
                    val arr = obj["topics"]?.jsonArray ?: response.payload!!.jsonArray
                    _topics.value = arr.map { json.decodeFromJsonElement(ChronicleTopic.serializer(), it) }
                } else {
                    _error.value = response.error?.message ?: "Failed to load topics"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getStats() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("chronicle.stats")
                if (response.ok && response.payload != null) {
                    _stats.value = json.decodeFromJsonElement(ChronicleStats.serializer(), response.payload!!)
                } else {
                    _error.value = response.error?.message ?: "Failed to load stats"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getInsights() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("chronicle.insights")
                if (response.ok && response.payload != null) {
                    _insights.value = json.decodeFromJsonElement(
                        ChronicleInsights.serializer(),
                        response.payload!!,
                    )
                } else {
                    _error.value = response.error?.message ?: "Failed to load insights"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
