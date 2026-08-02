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

/**
 * One completed run of a batch pipeline, as read from its `run_history.json`.
 *
 * The three counts describe the usual collect, filter, act shape: how many
 * records a run took in, how many survived its criteria, and how many it acted
 * on. A pipeline that does not act on anything simply leaves [itemsSubmitted] at
 * zero and the UI omits that row. These property names are the JSON keys, since
 * the gateway forwards the record unchanged.
 */
@Serializable
data class LastRunSummary(
    val timestamp: Long? = null,
    val durationMs: Long? = null,
    val itemsScraped: Int = 0,
    val itemsMatched: Int = 0,
    val itemsSubmitted: Int = 0,
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

// The Projects tab is deployment specific: replace these entries with your own
// services. `dashboardUrl` renders a link out to a web UI, and
// `statusRpcSupported` makes the tile poll the gateway for live pipeline status.
//
// Dashboard URLs go through the same reverse proxy as the gateway itself, so one
// certificate covers everything and the tiles work off the local network. The port
// scheme below is 20000 plus the service's own port, which is a convention rather
// than a requirement.
private const val GW = "gateway.example.com"

val ALL_PROJECTS = listOf(
    ProjectInfo(
        name = "Ironjaw",
        description = "The gateway this app connects to",
        // The gateway's own dashboard is the web equivalent of this app, so there
        // is nothing useful to link to from inside it.
    ),
    // The one entry wired to live status. ProjectsViewModel matches on this exact
    // name, so rename it in both places or the tile stops updating.
    ProjectInfo(
        name = "ExamplePipeline",
        description = "A scheduled job reporting live status over RPC",
        dashboardUrl = "https://$GW:28502",
        statusRpcSupported = true,
    ),
    ProjectInfo(
        name = "example-dashboard",
        description = "A web service reachable through the reverse proxy",
        dashboardUrl = "https://$GW:28503",
    ),
    ProjectInfo(
        name = "llama-server",
        description = "Local LLM inference, no dashboard of its own",
    ),
)
