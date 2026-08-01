package com.scaso.drclawapp.data.roles

/** Which LLM backend the chat is currently routed to. */
enum class ChatBackend { CLOUD, LOCAL }

// Ironjaw's router dispatches cloud models by a "provider/" prefix; bare names are local
// llama-server models. ponytail: prefix heuristic — widen this list if a new cloud provider
// is added (e.g. "mistral/"). Bare "claude"/"gpt"/"gemini" are included as a safety net for
// the rare case the gateway returns an un-prefixed cloud model id.
private val CLOUD_PREFIXES = listOf(
    "anthropic/", "openrouter/", "openai/", "gemini/", "google/",
    "claude", "gpt-", "gemini-",
)

/**
 * Resolve a (already provider-resolved) model name to its backend.
 * Null/blank → LOCAL (safe default: never imply a paid cloud backend without evidence).
 */
fun chatBackendOf(model: String?): ChatBackend {
    val m = model?.trim()?.lowercase().orEmpty()
    if (m.isEmpty()) return ChatBackend.LOCAL
    return if (CLOUD_PREFIXES.any { m.startsWith(it) }) ChatBackend.CLOUD else ChatBackend.LOCAL
}
