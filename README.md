# Dr. CLAW App

Native Android chat client for the Ironjaw Gateway — a private, self-hosted alternative to the Telegram bot interface.

**Current version:** v1.3.0

## What It Does

Connects directly to the Ironjaw Gateway WebSocket over NordVPN Meshnet + Caddy reverse proxy with auto-TLS (DuckDNS), replicating the full Telegram bot UX without routing through any third-party servers. Meshnet-only by design: the phone must be on Meshnet, and nothing is exposed to the WAN. Beyond chat it is a phone-side control panel for the whole workstation/docker-host setup — Claude Code sessions, model switching, infrastructure status, and remote phone commands (HITL-gated).

## Features

| Feature | Status |
|---|---|
| Live streaming (message edits as text arrives) | ✅ |
| Rich Markdown rendering + code copy | ✅ |
| Session management (create/switch/rename/delete) | ✅ |
| Inline action buttons (suggestion chips) | ✅ |
| Slash command menu (/new, /clear, /help) | ✅ |
| Long-press actions (copy/share/reply/regenerate/delete) | ✅ |
| Swipe-to-reply threading | ✅ |
| Background WebSocket service (foreground service) | ✅ |
| Notification channels (service + chat messages) | ✅ |
| Share sheet integration (send text from other apps) | ✅ |
| Photo/file attachments + camera capture | ✅ |
| Voice input (Android STT, works offline) | ✅ |
| Full-screen voice conversation mode | ✅ |
| Conversation search | ✅ |
| Quick actions bar (Summarize/Explain/Translate/Code) | ✅ |
| Floating bubble overlay (Android 11+) | ✅ |
| Clipboard detection + send preview | ✅ |
| Settings (theme/instructions/gateway URL) | ✅ |
| Persistent WebSocket (auto-reconnect, network-change fast reconnect) | ✅ |
| Native agentic tool execution (tool cards, permission approvals) | ✅ |
| Room local DB (instant history, pin/archive/fork, encrypted, FTS global search) | ✅ |
| Claude Code session control (multi-machine list, attach, create, split view) | ✅ |
| Model Manager (status, cross-system switching, role assignment) | ✅ |
| System dashboard (infra, schedules, devices, metrics, vault, export, plugins) | ✅ |
| Brain + Chronicle screens (Ironjaw memory, session archive) | ✅ |
| ntfy push notifications (SSE, self-hosted) | ✅ |
| Incoming phone-command approval gate (location/clipboard/calendar HITL) | ✅ |

## Tech Stack

- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose + Material Design 3 (BOM 2025.05.01)
- **Networking:** OkHttp 4.12.0 WebSocket
- **DI:** Hilt 2.57.2
- **Persistence:** Room 2.7.1 (FTS4, encrypted)
- **Architecture:** MVVM + Clean Architecture
- **Markdown:** multiplatform-markdown-renderer-m3 v0.28.0
- **Navigation:** Compose Navigation v2.9.0
- **Camera:** CameraX v1.5.0
- **Transport:** Ironjaw Gateway WebSocket via Caddy + DuckDNS

## Gateway Protocol

Connects to `wss://gateway.example.com` using the Ironjaw Gateway WebSocket API:

- `chat.send` / `chat.send.multimodal` — send text or multimodal messages
- `chat.history` — load session history
- `chat.abort` — abort in-flight run
- `chat.inject` — inject assistant note
- `sessions.list` / `sessions.patch` / `sessions.delete` / `sessions.reset` — list, rename, delete, reset sessions (session context is per-request via `sessionKey`; there is no `sessions.switch` method)
- `talk.mode` / `tts.convert` — voice conversation pipeline
- Streaming via `chat` events (live typing effect)
- Feature RPCs (`brain.*`, `vault.*`, `model.*`, `gateway.restart`, …) via generic request — full registry in the Ironjaw repo

## Project Structure

Single-activity Compose app: `ui/` (one package per screen + shared `components/`), `data/` (transport clients, Room, one repository per feature), `service/` (foreground service, bubble, network monitor, accessibility), `cmd/` (HITL-gated incoming phone commands). Full package map: [CLAUDE.md → Project Structure](CLAUDE.md).

## Setup

```bash
# Clone with submodules (from re-lab root)
git submodule update --init android/dr-claw-app

# Copy secrets template and fill in values
cp secrets.properties.example secrets.properties
# GATEWAY_URL=wss://gateway.example.com
# GATEWAY_TOKEN=<your-token>

# Build
./gradlew assembleDebug

# Run on connected device
./gradlew installDebug
```

## Related

- [re-lab](../) — parent RE workspace
