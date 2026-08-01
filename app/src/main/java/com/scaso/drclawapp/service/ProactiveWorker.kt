package com.scaso.drclawapp.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scaso.drclawapp.data.context.ContextProvider
import com.scaso.drclawapp.data.websocket.GatewayClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that collects ambient context and sends it
 * to Ironjaw's brain.proactive RPC for proactive suggestions.
 *
 * Runs every 15 minutes when network is available. Suggestions are stored
 * in a shared StateFlow accessible via the companion object.
 */
@HiltWorker
class ProactiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gatewayClient: GatewayClient,
) : CoroutineWorker(appContext, workerParams) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val contextProvider = ContextProvider(applicationContext)
        val contextJson = contextProvider.collectContextJson()

        Log.d(TAG, "Collecting proactive suggestions with context: $contextJson")

        return try {
            val params = buildJsonObject {
                put("context", contextJson)
                put("max_suggestions", 3)
            }
            val response = gatewayClient.sendGenericRequest("brain.proactive", params)

            if (response.ok && response.payload != null) {
                val payload = response.payload!!
                val suggestions = mutableListOf<ProactiveSuggestion>()

                val suggestionsArray = payload.jsonObject["suggestions"]?.jsonArray
                suggestionsArray?.forEach { element ->
                    val obj = element.jsonObject
                    suggestions.add(
                        ProactiveSuggestion(
                            text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach,
                            action = obj["action"]?.jsonPrimitive?.contentOrNull,
                            category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "general",
                        )
                    )
                }

                if (suggestions.isNotEmpty()) {
                    Log.i(TAG, "Got ${suggestions.size} proactive suggestions")
                    _suggestions.value = suggestions
                } else {
                    Log.d(TAG, "No proactive suggestions returned")
                }
                Result.success()
            } else {
                Log.w(TAG, "brain.proactive RPC failed: ${response.error?.message}")
                Result.success() // Don't retry for server-side issues
            }
        } catch (e: Exception) {
            Log.w(TAG, "Proactive worker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ProactiveWorker"
        const val WORK_NAME = "proactive_suggestions"

        private val _suggestions = MutableStateFlow<List<ProactiveSuggestion>>(emptyList())
        val suggestions: StateFlow<List<ProactiveSuggestion>> = _suggestions.asStateFlow()

        fun dismissSuggestion(index: Int) {
            _suggestions.value = _suggestions.value.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
        }

        fun clearSuggestions() {
            _suggestions.value = emptyList()
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            Log.i(TAG, "Scheduled proactive worker (every 15 min)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            _suggestions.value = emptyList()
            Log.i(TAG, "Cancelled proactive worker")
        }
    }
}

@Serializable
data class ProactiveSuggestion(
    val text: String,
    val action: String? = null,
    val category: String = "general",
)
