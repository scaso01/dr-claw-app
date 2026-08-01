package com.scaso.drclawapp.data.ccbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Data models for Claude Code Bridge sessions.
 * Multi-machine session list from docker-host and workstation cc-bridges.
 *
 * No Android imports — KMP-extractable.
 */

@Serializable
data class CcSessionsResponse(
    val sessions: List<CcSession> = emptyList(),
)

@Serializable
data class CcSession(
    val sessionId: String = "",
    val project: String? = null,
    val branch: String? = null,
    val shortId: String = "",
    val cwd: String? = null,
    val machine: String = "",
    val lastActivity: Long? = null,
    val status: String = "unknown",
    val lastMessage: String? = null,
    val topic: String? = null,
    val contextPct: Float? = null,
    val compactionCount: Int = 0,
    val backend: String? = null,
    val model: String? = null,
    val permissionMode: String? = null,
    val isActive: Boolean = false,
    val clients: Int = 0,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val forkCount: Int = 0,
    val spawnMode: String? = null,
)

/** Spawn mode for CC sessions (fork, worktree, etc.). */
enum class SpawnMode {
    DEFAULT,
    WORKTREE,
    ;

    companion object {
        fun fromString(value: String?): SpawnMode = when (value?.lowercase()) {
            "worktree" -> WORKTREE
            else -> DEFAULT
        }
    }
}

/** A mailbox message for async agent communication. */
@Serializable
data class MailMessage(
    val id: String = "",
    val from: String = "",
    val subject: String = "",
    val body: String = "",
    val priority: String = "normal",
    val claimedBy: String? = null,
    val createdAt: Long = 0,
)

/** Display-friendly machine names and colors. */
enum class Machine(val displayName: String) {
    REMOTE("docker-host"),
    LOCAL("workstation"),
    PHONE("Phone"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        fun fromString(value: String): Machine = when (value.lowercase()) {
            "docker-host" -> REMOTE
            "workstation" -> LOCAL
            "phone" -> PHONE
            else -> UNKNOWN
        }
    }
}

/** Active session from cc-bridge v2 daemon. */
@Serializable
data class CcActiveSession(
    val id: String,
    val status: String = "unknown",
    val sdkSessionId: String? = null,
    val config: CcActiveSessionConfig? = null,
    val clients: Int = 0,
    val createdAt: Long = 0,
    val lastActivityAt: Long = 0,
    /** Which bridge produced this session — set client-side after fetch, not sent by the daemon. */
    @Transient val source: Machine = Machine.LOCAL,
)

@Serializable
data class CcActiveSessionConfig(
    val project: String? = null,
    val cwd: String = "",
    val backend: String = "cloud",
    val model: String? = null,
    val permissionMode: String = "bypassPermissions",
    val resumedFrom: String? = null,
)

/** History message from cc.history RPC. */
@Serializable
data class HistoryMessage(
    val role: String,
    val content: String = "",
    val timestamp: Long = 0L,
    @kotlinx.serialization.SerialName("entry_type")
    val entryType: String? = null,
    @kotlinx.serialization.SerialName("tool_name")
    val toolName: String? = null,
    /** Source: "cli" (terminal), "sdk-cli" (Dr. CLAW phone), null (unknown). */
    val entrypoint: String? = null,
)

/**
 * Session from the `cc.sessions` WS method (Ironjaw `handle_cc_sessions` →
 * active subprocesses + `HistoricalSession` JSONL scan).
 *
 * The server sends `sessionId` (not `ccBridgeId`) and a `title`; older code
 * decoded into a struct that dropped both. `@SerialName("sessionId")` maps the
 * id (also fixes blank ids / attach+destroy), and `title` is now kept.
 * `role`/`totalTokens`/`totalCostUsd` are NOT sent by this method (kept only as
 * harmless defaults for back-compat) — do not sort/display them here.
 */
@Serializable
data class ClaudeSession(
    @kotlinx.serialization.SerialName("sessionId")
    val ccBridgeId: String = "",
    val sdkSessionId: String? = null,
    val title: String? = null,
    val role: String? = null,
    val model: String? = null,
    val project: String? = null,
    val cwd: String = ".",
    val status: String = "unknown",
    val contextPct: Float? = null,
    val compactionCount: Int = 0,
    val totalTokens: Long = 0,
    val totalCostUsd: Double = 0.0,
    val createdAt: String = "",
    val lastActivity: String = "",
)
