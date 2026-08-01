package com.scaso.drclawapp.data.projects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Project status models. Gateway proxies project data from docker-host.
 *
 * No Android imports -- KMP-extractable.
 */

@Serializable
data class ProjectStatusResponse(
    val project: String = "",
    val status: ProjectStatus = ProjectStatus.UNKNOWN,
    val lastRun: LastRunSummary? = null,
    val pipeline: PipelineStatus? = null,
)

@Serializable
data class LastRunSummary(
    val timestamp: Long? = null,
    val durationMs: Long? = null,
    val jobsScraped: Int = 0,
    val jobsMatched: Int = 0,
    val jobsApplied: Int = 0,
    val errors: Int = 0,
)

@Serializable
data class PipelineStatus(
    val stage: String = "",
    val progress: Float = 0f,
    val message: String? = null,
)

@Serializable
enum class ProjectStatus {
    @SerialName("idle") IDLE,
    @SerialName("running") RUNNING,
    @SerialName("failed") FAILED,
    @SerialName("unknown") UNKNOWN,
}

data class ProjectInfo(
    val name: String,
    val description: String,
    val dashboardUrl: String? = null,
    val statusRpcSupported: Boolean = false,
)

// Dashboard URLs use gateway.example.com through Caddy HTTPS proxy.
// Proxy port scheme: 20000 + original_port (e.g. 8502 → 28502).
// Works on both internal WiFi and external 5G — all traffic goes through Caddy TLS.
private const val GW = "gateway.example.com"

val ALL_PROJECTS = listOf(
    ProjectInfo(
        name = "Ironjaw",
        description = "Rust WebSocket gateway (16 crates, axum)",
        // Dashboard is the web version of Dr. CLAW — no need to link from within the app
    ),
    ProjectInfo(
        name = "ExamplePipeline",
        description = "Job scraper pipeline (3153+ tests)",
        dashboardUrl = "https://$GW:28502",
        statusRpcSupported = true,
    ),
    ProjectInfo(
        name = "herby-strout",
        description = "Social media automation for Matt Strout",
        dashboardUrl = "https://dashboard.example.com",
    ),
    ProjectInfo(
        name = "ColorCatalog",
        description = "Paint color cataloging app (Android + Web)",
        dashboardUrl = "https://$GW:28085",
    ),
    ProjectInfo(
        name = "cc-chronicle",
        description = "CC session transcript indexer & dashboard",
        dashboardUrl = "https://$GW:38793",
    ),
    ProjectInfo(
        name = "quant-tools",
        description = "Multi-asset quantitative finance toolkit (1018+ tests)",
        dashboardUrl = "https://$GW:28503",
    ),
    ProjectInfo(
        name = "osint-toolkit",
        description = "OSINT investigation workbench (97 tools, 211 tests)",
        dashboardUrl = "https://$GW:28088",
    ),
    ProjectInfo(
        name = "link-monitor",
        description = "Docker ISP monitoring stack (8 containers)",
        dashboardUrl = "https://$GW:23003",
    ),
    ProjectInfo(
        name = "llama-server",
        description = "Local LLM inference (Qwen3.5 35B MoE)",
        // No proxy — port 28080 is SearXNG on docker-host, not workstation's llama-server
    ),
    ProjectInfo(
        name = "mesh-monitor",
        description = "TUI network dashboard for Deco mesh",
    ),
    ProjectInfo(
        name = "awesome-aggregator",
        description = "Content dashboard (22 tests)",
    ),
    ProjectInfo(
        name = "re-lab",
        description = "Multi-platform reverse engineering environment",
    ),
    ProjectInfo(
        name = "monster-search",
        description = "Python client + CLI for search engines (8 engines)",
    ),
)
