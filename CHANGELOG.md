# Changelog

All notable changes to Dr. CLAW App will be documented here.

Format: [Semantic Versioning](https://semver.org/)

---

## [Unreleased] — 2026-07-19 (Transport reliability on the road)

### Fixed
- **Socket survives long turns (FIX #9):** `ChatService` now holds a partial `WakeLock` + `WifiLock` while a chat turn is active — acquired on delta/catchup/tool events, released on final/aborted/error, with an 8-minute inactivity safety-release so a missed terminal can't leak the locks — so Doze can't drop the gateway socket mid-turn. Adds the `WAKE_LOCK` permission.
- **Tick watchdog no longer kills slow-but-live connections (FIX #9):** on a stale-tick window the watchdog sends an app-level `ping` and only cancels the socket if there's no answer within 5s (a long local-model prefill legitimately delays server ticks). Previously it hard-cancelled outright.
- **Reconnect no longer storms (FIX #10):** exponential backoff now carries jitter (up to +50% of base, min 250ms spread) so simultaneous drops / rapid retries don't reconnect in lockstep and hammer the gateway. `auth.connect` remains the guaranteed first frame on every (re)open.

## [Unreleased] — 2026-07-07 (Review hardening pass)

### Fixed
- HITL approval gate for incoming gateway `cmd` frames — fail-closed, 60s timeout, background notification, per-tool Always Allow (previously executed with no user consent)
- Room history rows now replaced atomically per session instead of keyed by positional index (reloads no longer strand/overwrite rows)
- Removed destructive Room migration fallback; explicit migrations for all schema versions 1→7 (schema bumps can no longer silently wipe pins/archives/forks)
- Restart Gateway drawer button wired to the real `gateway.restart` RPC (was an empty placeholder since v0.6.0)
- Duplicate permission handling: gateway `permission.request` was answered by both the global ApprovalDialog and a chat-screen copy, risking duplicate responses — chat-screen copy removed
- `FakeGatewayClient.sendMessage` signature drift (androidTest compile broken since v1.3.0)

### Changed
- All 7 transport-injecting ViewModels routed through repositories; new thin `GatewayRepository` for connection lifecycle
- `ChatViewModel` split into `SlashCommandController` / `SpeechInputController` / `StreamPhaseController` (896 → 720 lines, behavior unchanged)
- `NtfyClient` + `PipecatClient` now fast-reconnect on network change; `PipecatClient` gets capped exponential backoff

### Removed
- Unreachable `CommandCenterScreen`, orphaned `INFRASTRUCTURE`/`EXPERIMENTS` routes, empty `ui/model`, `data/modelmanager`, `data/inference` packages
- Stale `HANDOFF.md` (self-labeled superseded)

### Added
- Unit tests for 10 previously-untested `data/` packages: brain, chronicle, device, export, plugins, preferences, schedule, security, tools, vault

## [1.3.0] — 2026-04 (Native Agentic Chat)
- `tool.start` / `tool.result` / agentic `permission.request` events rendered natively; agentic state machine; ToolUseCard, AgenticStateIndicator; collapsible tool outputs

## [1.2.0] (Cross-System Model Switching)
- `model.changed` WS event handling, switch polling with progress, role auto-refresh, quick-switch chips in chat header, auto-increment versionCode via version.properties

## [1.1.x] (Network Resilience / CC Session Phone Fix)
- NetworkMonitor instant reconnect on network change (~500ms vs 90s watchdog); Ironjaw RPC fallback for CC sessions when the bridge daemon is unreachable

## [1.0.0] (Emulator Gateway Connection)
- Debug cleartext network config, ADB gateway-URL broadcast, runtime URL switching (`reconnectWith`), editable gateway URL in Settings, `emu-*` scripts

---

## [0.9.3] — 2026-03-02 (Gateway Deadlock Prevention)
## [0.9.4] — 2026-03-03 (CC Bridge v2.0 — Session Daemon)

### Added
- **cc-bridge v2.0 server** — WebSocket server (`/cc/ws`), SessionManager with idle reaper (5 max sessions, 10min timeout), AgentSDKTransport wrapping `@anthropic-ai/claude-agent-sdk`
- **CcBridgeClient** — OkHttp WebSocket client with challenge/response auth, request/response correlation via CompletableDeferred, auto-reconnect with exponential backoff
- **Dual-source repository** — CcBridgeRepository fetches from both v1 gateway proxy (historical sessions) and v2 daemon (active sessions) simultaneously
- **CcSessionsScreen enhancements** — Active/Historical session sections, FAB for new session (only when bridge connected), "LIVE" indicator, backend badges (Cloud/Local)
- **CreateSessionDialog** — project name, working directory, backend toggle (Cloud/Local), model, permission mode (Auto/Interactive)
- **AttachedSessionScreen** — Real-time chat view with streaming display, 4 message types (user, assistant, tool use, error), auto-scroll, send/interrupt toggle
- **AttachedSessionViewModel** — Event collection from CcBridgeEvent SharedFlow, text_delta buffering, tool use tracking, raw event accumulation (capped at 500)
- **RawEventView** — Full-screen monospace event log with timestamps, color-coded by type (stream=green, status=blue, error=red, permission=yellow)
- **PermissionDialog** — Tool permission approval with JSON input preview (truncated at 500 chars), Allow/Deny buttons
- **BrainController** — Cross-session awareness via `~/.claude/brain/` shared memory files, inbox file watcher, STATUS.md auto-update (10s), delta injection between sessions, cc-chronicle integration (non-fatal)
- **NavGraph** — Added `attached_session/{sessionId}` route with navigation from session list
- **FlowCombine** — 6-flow combine utility (stdlib only supports 5)

### Changed
- `AppModule.kt` — Added CcBridgeClient Hilt provider (URL derived from gateway URL + `/cc/ws`, same auth token)
- `CcBridgeModels.kt` — Added v2 fields (backend, model, permissionMode, isActive, clients) + CcActiveSession, CcActiveSessionConfig
- `CcBridgeRepository.kt` — Rewritten for dual-source with bridge lifecycle methods (connect/disconnect/attach/detach/create/destroy)
- `CcSessionsViewModel.kt` — 6-flow combine, bridge connection management, create/attach/detach/destroy actions

### Fixed
- **Race condition on session resume** — `resumeSession()` now extracts the daemon session ID from `response.payload` and emits via `_resumedSessionId` StateFlow for auto-navigation to the attached view; uses `session.cwd` for proper working directory
- **Error messages not displayed** — `attachToSession()` and `sendMessage()` in AttachedSessionViewModel now extract errors from `response.payload.error` (string in JSON payload) instead of `response.error?.message` which was always null for cc-bridge responses
- **Double attach on active session tap** — Removed redundant `viewModel.attachSession(session.id)` call from `onAttach` lambda in CcSessionsScreen; only navigation remains (attach happens in AttachedSessionViewModel)
- **Resume error visibility** — Added SnackbarHost and LaunchedEffects to CcSessionsScreen for displaying resume errors and triggering auto-navigation on successful resume
- Removed unused `CcSessionsViewModel.attachSession()` method

### Added
- **Stall timeout reduced to 90s** — snackbar warns "No response for 90 seconds" with Abort action (was 120s)
- **Retry chip after abort** — AssistChip with replay icon appears after stall/abort, re-sends last message on tap
- **Consecutive failure tracking** — after 2+ failures, escalated snackbar "Gateway may be stuck" with Restart action
- **Post-reconnect health verification** — sends lightweight `gateway.model` RPC after reconnect to verify gateway responsiveness
- **Chat history response parsing** — `ChatHistoryResponse` and `HistoryMessage` serializable models for proper gateway history RPC parsing
- **History content extraction** — `extractTextFromContent()` handles both string and content-block-array formats from gateway

### Fixed
- **History loading completely broken** — `loadHistory()` was discarding the `ResponseFrame` payload; now properly parses the `res` frame via `ChatHistoryResponse`
- **Empty SYSTEM bubbles in history** — tool-related messages (`toolResult`, `tool` roles) now filtered out before display
- **Oversized placeholder messages** — messages containing `CHAT_HISTORY_OVERSIZED_PLACEHOLDER` now filtered from history
- **Dead code in GatewayClient** — removed unused `handleHistoryEvent()` method; history is handled via `res` frame in `ChatRepository.loadHistory()`, not as a gateway event
- **GatewayEventTest compilation** — updated `HistoryEntry` tests to use constructor directly (no longer `@Serializable`)

### Changed
- `ChatRepository` — `loadHistory()` rewritten to parse gateway `res` payload, filter tool/placeholder messages, add debug logging
- `ChatRepository` — new `extractTextFromContent()` helper for string-or-array content blocks
- `GatewayEvent.kt` — added `ChatHistoryResponse`, `HistoryMessage` models; `HistoryEntry` changed to plain data class
- `GatewayClient.kt` — removed dead `handleHistoryEvent()`, chat.history handled via res frame
- `ChatScreen.kt` — 90s stall timeout, retry chip, escalated failure warning snackbar
- `ChatViewModel.kt` — `_lastSentMessage`, `_showRetry`, `_consecutiveFailures` StateFlows; `retryLastMessage()`, `markRetryAvailable()`, health verification on reconnect
- `ChatUiState.kt` — added `showRetry`, `lastSentMessage`, `consecutiveFailures` fields
- Version bump to 0.9.3 (versionCode 13)

---

## [0.9.2] — 2026-02-27 (Phase 14: Session Search)

### Added
- **Session search in SessionDrawer** — sticky search bar between header and session list, filters conversations/sub-agents/archived by title, last message, and session key in real-time
- **Session search in CcSessionsScreen** — sticky search bar above CC session cards, filters by topic, project, machine, last message, short ID, and full session ID
- **Shared `SessionSearchBar` composable** — reusable OutlinedTextField with search/clear icons, keyboard dismiss on search action
- **Empty search state** — "No matching sessions" message when search filters everything out (both UIs)

### Changed
- `SessionViewModel` — added `sessionSearchQuery` StateFlow, `setSessionSearchQuery()`, `matchesQuery()` extension; `mainSessions`, `subAgentSessions`, `archivedSessions` flows now filter by search query
- `CcSessionsViewModel` — added `searchQuery` StateFlow, `setSearchQuery()`, `matchesQuery()` extension; `uiState` combine expanded to 4-arg with search filter
- `SessionDrawer` — accepts `sessionSearchQuery`/`onSessionSearchQueryChange` params
- `ChatScreen` — collects and wires session search query to drawer
- Version bump to 0.9.2 (versionCode 12)

### New Files
- `ui/components/SessionSearchBar.kt` — shared search bar composable

---

## [0.9.1] — 2026-02-26 (Phase 13: Model Manager + CC Session Auto-Naming)

### Added
- **Phase 13 — Model Manager** (recovered from decompiled APK v0.9.0, tasks 1-9):
  - `ModelModels.kt` — `ModelStatus`, `OllamaModel`, `OllamaStatus` data classes
  - `ModelRepository.kt` — `model.status`, `ollama/switch/session` RPC calls
  - `ModelManagerViewModel.kt` — full ViewModel with model list, status polling, switch actions
  - `ModelManagerScreen.kt` — full Compose UI for model management
  - `NavGraph.kt` — MODELS route added
  - `AppModule.kt` — `ModelRepository` Hilt provider
  - `GatewayClient.kt` — `enableFastReconnect()` for mode switching
- **CC Sessions auto-naming** (cc-bridge v1.2.0 + dr-claw-rpc topic fix):
  - Title resolution cascade: summary.md > compact summary > first message
  - Auto-naming titles now flow through to app via topic field
  - Hide "Unknown" project label when topic present, show `~` for homeless sessions
- **Context % in CC session cards** (cc-bridge v1.3.0):
  - Color-coded progress bar per session (primary <50%, amber <75%, red >=75%)
  - "Ctx: N%" label with compaction count when >0
  - cc-bridge computes context from last assistant usage tokens, cached by mtime

### Fixed
- **RoleAssignmentCard** — was iterating `modelStatus.aliases` (empty map = no roles shown). Now hardcodes 3 OpenClaw roles (Chat, Sub-agents, Heartbeat) matching the original compiled APK. Builds combined model list from cloud models + Ollama models. Dropdown highlights selected model with bold weight.

### Changed
- Version bump to 0.9.1 (versionCode 11)

### New Files
- `data/models/ModelModels.kt` — model data classes
- `data/models/ModelRepository.kt` — model RPC repository
- `ui/models/ModelManagerViewModel.kt` — model manager ViewModel
- `ui/models/ModelManagerScreen.kt` — model manager Compose screen

---

## [0.8.0] — 2026-02-24 (Phase 12: Room DB & Session Overhaul)

### Added
- **Room 2.7.1 local database** — ChatSessionEntity, MessageEntity, MessageFtsEntity (FTS4)
- **Room DAOs** — SessionDao (active/archived/pin/archive), MessageDao (FTS4 global search)
- **Type converters** — Role and MessageType enum serialization
- **DrClawDatabase** — Room database class with Hilt DI providers (Task 9)
- **Room-backed SessionRepository** — gateway state mirrored to Room on load/create/rename/delete; pin and archive (Room-only); `archivedSessions` Flow (Task 10)
- **Room-backed ChatRepository** — messages persisted on ChatFinal and history load; `currentSessionKey` synced by SessionRepository (Task 11)
- **Session pin/archive/sorting** — pin icon, Pin/Archive/Delete in long-press menu, collapsible Archived section at drawer bottom (Task 12)
- **Session auto-naming** — new sessions auto-named from first user message (40 char truncation) after first ChatFinal (Task 13)
- **Persistent status bar** — `ChatStatusBar` composable: model chip, color-coded context progress bar, connection dot, token/cost display (Task 14)
- **Per-message model badge** — assistant messages display model name below timestamp in tertiary color (Task 15)
- **Cross-conversation global search** — `SearchMode` toggle (in-chat / global), FTS4-backed `searchGlobal()` with session title in results (Task 16)
- **CC session enhancement** — topic field as primary card text, long-press copies full session ID to clipboard (Task 17)
- **Fork/branch from message** — "Fork from here" in message action sheet creates new session with messages up to that point, labeled "Fork: [title]" (Task 18)
- **Status detail bottom sheet** — `StatusDetailSheet` with model name, token breakdown, estimated cost, session duration, context capacity, "New conversation" button (Task 19)
- **v0.8.0 implementation plan** — 16 tasks across 3 parallel tracks

### Architecture
- New `data/local/` package: entities, DAOs, converters (Room persistence layer)
- ForeignKey cascade from messages to sessions
- FTS4 content table for cross-conversation full-text search
- Hilt DI for Room database, SessionDao, MessageDao
- `ChatSession` model expanded with `isPinned`, `isArchived` fields
- `SessionRepository` gains `toEntity()`/`toDomain()` mapping extensions
- `ChatRepository` gains Room write-through on ChatFinal and history events

### New Files
- `data/local/DrClawDatabase.kt` — Room database definition
- `ui/components/ChatStatusBar.kt` — persistent status bar composable
- `ui/components/StatusDetailSheet.kt` — session detail bottom sheet

---

## [0.7.1] â€” 2026-02-24

### Fixed
- **Plus button sub-agent classification:** New sessions used `agent:main:chat-<ts>` which `isSubAgentKey()` flagged as sub-agent (3 parts, last != "main"). Changed key to `agent:main:user-<ts>` and added `user-` prefix exclusion to `isSubAgentKey()`.
- **Model chip showing default model:** Model chip was empty when session had no `modelOverride` (i.e., using gateway default). Now fetches default model from gateway via `infra.status` on connect and stores in preferences as fallback.
- **Session auto-naming:** New sessions kept "New Chat" title indefinitely. Now calls `loadSessions()` after every `ChatFinal` event so the gateway's `derivedTitle` appears once the first exchange completes.
- **Gateway restart button location:** Moved from session drawer to Infrastructure screen. Drawer now shows connection status only (dot + label). Infra screen gained a Gateway control card with restart button and confirmation dialog.
- **Session title in top bar:** Replaced static "Dr. CLAW" title with the current session's title. Falls back to "New Chat" if no title. Single line with ellipsis overflow.
- **Active session sort order:** Active session now always appears first in the drawer. Main sessions sorted by: active first, then `updatedAt` descending.

### Changed
- `SessionRepository.createNewSession()` uses `agent:main:user-<ts>` key format
- `SessionRepository.isSubAgentKey()` excludes `user-` prefixed sessions
- `SessionViewModel` gained `currentSessionTitle`, `defaultModelName`, `refreshDefaultModel()` flows
- `SessionViewModel.mainSessions` now sorts active session to top via `combine(sessions, currentSessionKey)`
- `SessionViewModel.currentSessionModelName` falls back to `defaultModelName` when session model is null
- `ChatViewModel` injects `SessionRepository`; calls `loadSessions()` on `ChatFinal` events
- `InfraViewModel` injects `GatewayClient`; `InfraUiState` gained `connectionState`; added `restartGateway()`
- `InfraScreen` gained `GatewayControlCard` with restart button and confirmation dialog
- `SessionDrawer.GatewayStatusRow` simplified (restart button removed)
- `ChatScreen` shows `currentSessionTitle` and calls `refreshDefaultModel()` on connect
- `AppPreferences` gained `DEFAULT_MODEL` key, `defaultModel` flow, `setDefaultModel()` setter

### New Files
- `~/.openclaw/extensions/dr-claw-rpc/index.js` â€” Gateway plugin serving `infra.status` with `defaultModel`

---

## [0.7.0] â€” 2026-02-24

### Fixed
- **New conversation button (plus):** Was calling `resetSession()` which wiped the current session instead of creating a new one. Now generates a truly new session key (`agent:main:chat-<timestamp>`), switches to it, and optimistically adds it to the drawer. Old reset behavior preserved as `resetCurrentSession()` for future use.

### Added â€” Cost & Model Visibility
- **Model chip in chat header:** Small chip in the top app bar showing the current session's model name (e.g., `sonnet-4-6`). Only appears when model info is available from the gateway.
- **Token count + cost per session:** Third line in session drawer items showing total tokens and estimated cost (e.g., "12.4k tok Â· $0.18"). Cost calculated from hardcoded price map for Claude models; hidden for unknown/local models.
- **Idle-reset indicator:** Grey "cleared" badge next to session title in drawer when `messageCount == 0` and session is older than 5 minutes â€” warns that the conversation was wiped by idle timeout.

### Changed
- `SessionListEntry` expanded with `inputTokens`, `outputTokens`, `totalTokens`, `modelOverride`, `providerOverride` fields
- `ChatSession` expanded with `inputTokens`, `outputTokens`, `modelName` fields
- `SessionRepository.loadSessions()` maps new token/model fields from gateway response
- `SessionRepository.createNewSession()` rewritten to create truly new session with unique key
- `SessionViewModel` exposes `currentSessionModelName` flow derived from sessions + currentSessionKey
- `SessionDrawer.SessionItem` enhanced with token/cost line and cleared badge
- `ChatScreen` top bar shows model chip sourced from `SessionViewModel`

---

## [0.6.0] â€” 2026-02-23

### Added â€” OpenClaw Responsiveness (Phase 10)

**Activity Status Bar**
- New `ActivityStatusBar` composable with pulsing green dot + activity text
- Displays between message list and input bar during agent tool use
- Activities derived from gateway `ToolUse` events with friendly summaries (e.g., "Reading src/config.ts...", "Running: npm test...")
- Auto-clears on `ChatFinal`/`ChatAborted` events

**Thinking Indicator Elapsed Timer**
- `ThinkingIndicator` now shows elapsed seconds ("Xs") after 3-second delay
- Timer starts when awaiting response, resets on first streaming delta

**Stall Warning Snackbar**
- After 120 seconds of awaiting response, a snackbar warns "Agent appears stalled (2+ min)"
- Includes "Abort" action button for quick intervention

**Interrupt + Resume**
- Stop button now captures context before aborting (last activity, partial text, run duration)
- "Resume previous task" chip appears after interruption
- Resume sends context replay message: "Resume task. You were: [activity]. Last output: [text]"
- New `InterruptContext` data class for context preservation

**Auto-Continue on Truncation**
- Detects `max_tokens` stop reason and auto-sends "Continue from where you left off."
- Maximum 3 auto-continues per chain to prevent infinite loops
- Counter resets on normal completion

**Dead Sub-Agent Cleanup**
- Sub-agents older than 1 hour automatically filtered out
- Dismissed sub-agents persisted via DataStore preferences
- "Clear all" button in sub-agent section header
- Individual dismiss writes to persistent dismissed set

**Gateway Restart Button**
- Gateway status row in session drawer: color-coded dot (green/amber/red) + label
- Restart icon button with confirmation dialog
- Sends `gateway.restart` RPC via `GatewayClient`

**Effort Level Toggle**
- Three-way segmented button (Low/Medium/High) in Settings
- Sends `/think <level>` command to gateway on change
- Persisted via DataStore preferences

### Fixed
- **chat.inject param:** `ChatInjectParams` used `text` but gateway expects `message` â€” inject was silently failing
- **Sub-agent drawer filtering:** Drawer received raw session list, bypassing ViewModel's dismiss+max-age filtering â€” now accepts pre-filtered lists
- **Sub-agent clear-all:** Was calling `clearDismissedSubAgents()` (emptying the dismissed set = un-dismissing) â€” now adds all visible sub-agent keys to dismissed set
- **Sub-agent title display:** Sub-agents with no derivedTitle/title fell back to "Dr. CLAW" (displayName) â€” now shows key fragment ("Sub-agent abc12345")
- **Clipboard preview on launch:** Card showed stale clipboard on every app open due to Android clipboard access restrictions before ON_RESUME â€” now tracks ON_PAUSE and only shows card on real foreground returns

### Changed
- `GatewayEvent` expanded with `Activity` variant for tool use visibility
- `GatewayClient` expanded with `restartGateway()` method and `friendlyToolSummary()` helper
- `ChatRepository` handles auto-continue on `max_tokens` stop reason
- `ChatViewModel` expanded with interrupt/resume, activity tracking, and thinking timer
- `ChatUiState` expanded with `thinkingStartTimeMs`, `canResume`, `interruptContext`, `currentActivity`
- `SessionViewModel` filters dismissed + stale (>1hr) sub-agents via `AppPreferences`
- `SessionDrawer` enhanced with dismiss callbacks, gateway status row, clear-all button
- `SettingsViewModel` uses nested combine for 8-flow support (7-flow custom + effortLevel)
- `SettingsScreen` expanded with effort level selector section
- `AppPreferences` expanded with `dismissedSubAgents` and `effortLevel` keys/flows/methods

### New Files
- `data/model/InterruptContext.kt` â€” context preservation for interrupt+resume
- `ui/components/ActivityStatusBar.kt` â€” pulsing activity indicator composable

---

## [0.5.1] â€” 2026-02-23

### Fixed
- **ClipboardPreview:** Added `OnPrimaryClipChangedListener` so the card hides immediately when the clipboard is cleared while the app is open. Previously only re-checked on `ON_RESUME`, leaving the card stuck with stale content.

---

## [0.5.0] â€” 2026-02-23

### Added â€” Operations Dashboard (Phase 9)

**ntfy Push Notifications**
- SSE client (`NtfyClient`) subscribing to self-hosted ntfy server with auto-reconnect and exponential backoff
- Android notifications with deep-link to specific session (`drclaw://session/<key>`)
- `drclaw://` URI scheme registered in AndroidManifest for notification deep-links
- Topic auto-initialized at app startup via `DrClawApp.onCreate()`
- Priority mapping from ntfy levels to Android notification importance
- Settings UI: enable toggle, topic field with copy button, server URL field
- Auto-generated default topic (`drclaw-<8-hex>`) on first enable
- Three notification channels (service, chat, ntfy)

**Multi-Machine Claude Code Session List**
- `CcSessionsScreen` with pull-to-refresh, machine badges (docker-host=blue, workstation=green)
- Gateway proxy pattern via `sendGenericRequest()` â€” no direct LAN access needed
- `CcBridgeRepository` + `CcBridgeModels` (CcSession, Machine enum)
- Navigation drawer entry ("CC Sessions" with computer icon)

**Infrastructure Status Panel**
- `InfraScreen` with card-based layout, color-coded status dots (green/amber/red)
- Local gateway connection status + remote component health via `infra.status` proxy
- TLS certificate expiry warnings (<7 days = red, <14 days = amber)
- Pull-to-refresh with last-checked timestamp
- Navigation drawer entry ("Infrastructure" with dashboard icon)

**Sub-Agent Tracker (enhanced)**
- Enhanced `SessionDrawer` sub-agent section with badge count on drawer icon
- New `SubAgentItem` composable with pulsing status dot (active if updated within 2 min)
- Agent type label chip (extracted from agentId)
- Last message preview and elapsed time display
- Auto-clear completed sub-agents after 5 minutes
- Long-press "Dismiss" context menu

**JobHunter Trigger + Last Run Summary**
- `ProjectsScreen` with JobHunter status card, pipeline progress bar when running
- Last run stats: jobs scraped, matched, applied, errors, duration
- One-tap trigger button (sends "Run JobHunter now" via gateway)
- Navigation drawer entry ("Projects" with work icon)

**File Download**
- `FilePathDetector` with Windows path regex and `[file:]` marker detection
- `FileDownloadRepository` tracking per-path download state (downloading/complete/error)
- `FileDownloadChip` SuggestionChip with animated progress/complete/error icons
- Automatic file path detection in assistant message bubbles
- Gateway `file.get` RPC for server-side file retrieval
- `FileSaver` saves downloaded files to Downloads/DrClaw via MediaStore API

### Changed
- `GatewayClient` expanded with `sendGenericRequest()` for arbitrary gateway RPC
- `AppPreferences` expanded with ntfy settings (enabled, topic, URL)
- `SettingsViewModel` expanded with ntfy state; uses custom 7-flow `combine()`
- `ChatService` now observes ntfy preferences and forwards ntfy events as notifications
- `NotificationHelper` expanded with ntfy notification channel and `showNtfyNotification()`
- `ChatViewModel` exposes `downloadStates` and `downloadFile()` for file download
- `MessageBubble` detects file paths and renders download chips
- `SessionDrawer` enhanced with navigation items and sub-agent improvements
- `NavGraph` expanded with CC_SESSIONS, INFRASTRUCTURE, PROJECTS routes

### New Files
- `data/ntfy/NtfyClient.kt`, `data/ntfy/NtfyEvent.kt`
- `data/ccbridge/CcBridgeModels.kt`, `data/ccbridge/CcBridgeRepository.kt`
- `data/infra/InfraModels.kt`, `data/infra/InfraRepository.kt`
- `data/projects/ProjectModels.kt`, `data/projects/ProjectRepository.kt`
- `data/filedownload/FileDownloadModels.kt`, `data/filedownload/FileDownloadRepository.kt`
- `ui/ccbridge/CcSessionsScreen.kt`, `ui/ccbridge/CcSessionsViewModel.kt`
- `ui/infra/InfraScreen.kt`, `ui/infra/InfraViewModel.kt`
- `ui/projects/ProjectsScreen.kt`, `ui/projects/ProjectsViewModel.kt`
- `ui/components/FileDownloadChip.kt`
- `service/FileSaver.kt`
- `util/FlowCombine.kt`

---

## [0.4.0] â€” 2026-02-23

### Added â€” SESSIONS_BRIEFING Features
- **Session switcher with sub-agent sections**: drawer now separates main conversations from sub-agent sessions with "SA" badge
- **Verbose toggle**: eye icon in top bar sends `/verbose on|off` to gateway, controls tool event card visibility
- **Thinking level selector**: brain icon dropdown (off/low/medium/high) sends `/think <level>` to gateway
- **Abort button (session-level fallback)**: abort now falls back to `abortSession()` when no active `runId`
- **Graceful truncation handling**: history entries starting with `[chat.history omitted:` shown with "Truncated" chip and muted border
- **Partial/aborted message display**: aborted messages get amber border + "Aborted" chip
- **Sub-agent awareness**: `isSubAgentKey()` heuristic detects sub-agent session keys, `ChatSession.isSubAgent` field
- **Tool event cards**: collapsible cards for `tool_use`/`tool_result` events, shown only in verbose mode
- **Reconnect banner**: error/disconnected state shows persistent banner above message list
- **chat.inject handling**: gateway inject events displayed with blue border + "Injected" chip

### Changed
- `Message` model expanded with `MessageType` enum, `isAborted`, `isTruncated`, `isInjected`, `toolName`, `toolInput`, `toolResult`
- `ChatSession` model expanded with `isSubAgent` field
- `GatewayEvent` sealed class expanded with `ToolUse`, `ToolResult`, `ChatInject` variants
- `GatewayClient` expanded with `abortSession()`, `handleInjectEvent()`, `emitToolEvents()`
- `ChatRepository` handles new event types, detects truncated history
- `SessionRepository` classifies sessions as main/sub-agent
- `ChatViewModel` manages verbose and thinking level state
- `SessionViewModel` exposes `mainSessions` and `subAgentSessions` filtered flows
- `MessageBubble` renders border strokes and status chips for aborted/injected/truncated states
- `SessionDrawer` shows sectioned list with sub-agent badge
- `ChatScreen` includes verbose toggle, thinking menu, reconnect banner, tool event routing

### New Files
- `ui/components/ToolEventCard.kt` â€” collapsible tool call/result card

---

## [0.3.0] â€” 2026-02-23

### Fixed
- AckState lifecycle: messages now show sent (checkmark), processing (pulsing eye), done (double check)
- Reply context: swipe-to-reply now sends quoted text to gateway
- Action sheet: Share, Regenerate, and Delete buttons now functional
- Session switch protocol docs corrected (no sessions.switch method)

### Added â€” Feature Integration
- **Telegram-style input bar**: attachment clip (left), text field (center), context-sensitive right button (mic/send/stop)
- **Photo/file picker**: pick images and files via system pickers, preview thumbnails before sending
- **Camera capture**: take photos directly from attachment menu via CameraX
- **Voice-to-text**: tap mic to dictate, transcribed text fills input field
- **Voice conversation mode**: long-press mic for full-screen STT -> Gateway -> TTS pipeline
- **Conversation search**: search icon in top bar, filters messages in real-time with result count
- **Quick actions bar**: Summarize/Explain/Translate/Code review chips shown on empty conversation
- **Clipboard preview**: detects clipboard text on app resume, offers send action
- **Edit message**: delete a user message to re-edit and resend
- **Bubble overlay**: floating chat bubble (Android 11+), toggle in Settings
- **Error snackbars**: connection errors now shown as Material 3 snackbars

---

## [0.2.1] â€” 2026-02-21

### Fixed
- Fix fatal crash when session drawer loads before WebSocket handshake completes
  (`IllegalStateException: WebSocket not connected` in `GatewayClient.sendRequest()`)
- `sendRequest()` now returns an error ResponseFrame (`ok = false`) instead of throwing,
  allowing callers like `SessionRepository.loadSessions()` to handle gracefully

---

## [0.2.0] â€” 2026-02-21

### Added â€” Phases 2-8 (full feature build)

**Rich Text (Phase 2)**
- Markdown rendering via multiplatform-markdown-renderer-m3 v0.28.0
- Code blocks with copy-to-clipboard button (CodeBlockWithCopyButton)
- Relative timestamps (today / yesterday / full date)
- Thinking indicator (3 bouncing dots animation)

**Navigation & Settings (Phase 3)**
- Compose Navigation with 4 routes: CHAT, SETTINGS, VOICE, CAMERA
- Settings screen: theme toggle (system/light/dark), custom instructions, gateway URL display
- DataStore preferences persistence (AppPreferences)

**Session Management (Phase 4)**
- Session drawer (modal, long-press dropdown for rename/delete)
- Full session CRUD via gateway API (list, switch, create, rename, delete, reset)
- SessionRepository + SessionViewModel with Hilt DI

**Message Interactions (Phase 5)**
- Long-press action sheet: Copy, Share, Reply, Regenerate, Delete
- Slash command popup with filtered autocomplete (/new, /clear, /help)
- Inline suggestion chips (action buttons below assistant messages)
- Swipe-to-reply gesture with reply icon animation
- Ack state indicators (sent, processing, done) on message bubbles
- Reply preview bar in input area

**Background Service (Phase 6)**
- Foreground service (ChatService) with START_STICKY restart policy
- Two-channel notification system (low-priority service + high-priority chat)
- Share sheet integration (receive text/plain from other apps)

**Media & Voice Input (Phase 7)**
- Photo picker (PickVisualMedia) and file picker (GetContent)
- Attachment preview strip with thumbnail rendering and remove buttons
- CameraX capture screen with front/back camera toggle
- Voice record button (hold-to-record / tap-to-toggle modes)
- SpeechRecognizerHelper wrapping Android STT (works offline)
- Multimodal gateway API for base64 image + text file attachments

**Advanced Features (Phase 8)**
- Conversation search with real-time filtering and result count badge
- Edit last user message capability
- Quick actions bar (Summarize, Explain, Translate, Code review)
- Floating bubble overlay via Android 11+ Bubble API
- Full-screen voice conversation mode (STT -> Gateway -> TTS pipeline with 6-state machine)
- Clipboard content detection and send preview card

### Changed
- GatewayClient expanded with session, multimodal, and talk mode APIs
- GatewayMessage expanded with 12+ new protocol param/response types
- Message model expanded with AckState enum, suggestedActions, replyToId
- ChatSession model expanded with title, timestamps, message count
- AppModule DI expanded with SessionRepository provider
- AndroidManifest updated with service declarations, permissions, share intent filter
- All deprecation warnings resolved (hiltViewModel import, InsertDriveFile icon, LocalLifecycleOwner, annotation targets)

### Dependencies Added
- multiplatform-markdown-renderer-m3 v0.28.0
- multiplatform-markdown-renderer-coil3 v0.28.0
- navigation-compose v2.9.0
- datastore-preferences v1.1.7
- camera-core, camera-camera2, camera-lifecycle, camera-view v1.5.0

---

## [0.1.0] â€” 2026-02-21

### Added
- **Phase 1 complete:** WebSocket connection, chat UI, session history, live streaming
- Full OpenClaw Gateway Protocol v3 implementation (3-step handshake, token auth, heartbeat)
- GatewayClient with auto-reconnect (exponential backoff)
- ChatScreen with Material Design 3 message bubbles
- StreamingIndicator for live typing effect
- ChatRepository as single source of truth (MVVM + Clean Architecture)
- Hilt dependency injection
- Unit tests for all data layer classes
- DuckDNS + Caddy reverse proxy transport (auto-TLS, works from anywhere)
- DuckDNS IP updater scheduled task (queries Deco router for real WAN IP)
- Caddy auto-start on login

### Transport History
- **Tailscale** (initial): Worked but conflicts with NordVPN on Android (one-VPN limitation)
- **NordVPN Meshnet** (attempted): Unstable IPs (changed mid-session without notice), ~350ms latency, required fragile Python TCP proxy, Android cleartext traffic workarounds
- **Port forward + DuckDNS + Caddy** (final): Permanent `wss://` URL, ~5-20ms latency, Let's Encrypt TLS via DNS-01, works from any network worldwide

### Infrastructure
- Caddy v2.11.1 with DuckDNS plugin at `C:\Caddy\`
- DuckDNS subdomain: `gateway.example.com`
- Deco BE63 port forward: 443/TCP â†’ docker-host (192.0.2.51:443)
- Deco DHCP reservation for docker-host at .51
- NordVPN DNS resolver (103.86.96.100) for Caddy ACME challenges
