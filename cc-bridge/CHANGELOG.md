# Changelog

All notable changes to cc-bridge will be documented in this file.

## [Unreleased]

---

## [1.3.0] — 2026-02-26

### Added

- **Context usage per session** — `computeContextInfo()` parses each session's JSONL for the last `assistant` record's `message.usage` token counts, computes context percentage (`input_tokens + cache tokens / 200K * 100`), and counts `compact_boundary` records
- **In-memory context cache** — `contextCache` keyed by sessionId, invalidated by file mtime; prevents re-parsing unchanged files
- **New session fields** — `/cc/sessions` response now includes `contextPct` (0-100 or null) and `compactionCount` (int) per session

### Changed
- Version bump `1.2.0` → `1.3.0` in `/cc/health` response

### Files Changed
- `src/index.js` — context cache + `computeContextInfo()` + session fields (+51 lines)

---

## [1.2.0] — 2026-02-26

### Added

- **Title cache system** — 4-step title resolution cascade: cache → summary.md → compact summary → first user message
- **Shared title cache** — `data/titles.json` for Ollama-generated session names (written by session-namer.py hook, read by cc-bridge)
- **Streaming endpoint** — `POST /cc/chat/stream` returns Server-Sent Events for real-time claude output
- **Idle watchdog** — kills claude if no stdout for 120s (prevents hung sessions)
- **Noise filtering** — skips system-reminder, teammate-message, and other non-user content when extracting first message

### Files Changed
- `src/index.js` — title cache, streaming SSE, idle watchdog

---

## [1.1.1] — 2026-02-23

### Fixed

- **Message mangled via shell args** — Root cause of all "generic greeting" responses and single-word messages reaching claude. With `shell: true` on Windows, the message passed as a CLI positional arg was being split at spaces (only the first word reached claude). Example: `"What is 2+2?"` → claude received only `"What"` (3 tokens), responded "message got cut off". Fixed by passing the message via stdin instead of as a CLI argument. `claude -p` reads the message from stdin when no positional arg is given. Changed `stdio: ['ignore', 'pipe', 'pipe']` → `stdio: ['pipe', 'pipe', 'pipe']` and added `child.stdin.write(message); child.stdin.end()`.

### Changed
- Message is now written to `child.stdin` and closed, rather than appended to the args array
- Multi-line messages with special characters now work correctly

### Files Changed
- `src/index.js` — stdin message passing (+4 lines, -1 line)

---

## [1.1.0] — 2026-02-23

### Fixed

- **NSSM service USERPROFILE bug** — Service runs as `LocalSystem`; `USERPROFILE` resolved to the system account, causing `claude` to fail finding `~/.claude/` session files. Fixed by injecting correct user env vars via `AppEnvironmentExtra`:
  `USERPROFILE=C:\Users\<you> APPDATA=C:\Users\<you>\AppData\Roaming LOCALAPPDATA=C:\Users\<you>\AppData\Local HOME=C:\Users\<you>`

- **stdin hang** — `spawn()` did not close stdin; `claude` blocked waiting for input indefinitely. Fixed with `stdio: ['ignore', 'pipe', 'pipe']` in spawn options.

- **`--resume` on non-UUID sessionId** — `--resume` was always passed, even for new/non-existent sessions, causing immediate error: `"--resume requires a valid session ID when used with --print"`. Fixed: `--resume` is now only passed when `sessionId` matches UUID format (`/^[0-9a-f]{8}-...-[0-9a-f]{12}$/i`). Non-UUID sessionIds start a fresh session.

- **`claude.cmd` path resolution** — Used `process.env.APPDATA` to build the `claude` path, which is unreliable in NSSM service context. Fixed by hardcoding the full path: `C:\Users\<you>\AppData\Roaming\npm\claude.cmd`.

### Changed

- `spawn('claude', ...)` → `spawn('C:\\Users\\<you>\\AppData\\Roaming\\npm\\claude.cmd', ...)` with `shell: true` (required for `.cmd` files on Windows)
- Added `SPAWN_OPTS_BASE` constant for platform-aware spawn options
- Added `UUID_RE` constant and `isExistingSession` check before building args array

### Documentation

- **CLAUDE.md** — Replaced original build spec with living reference (architecture, API docs, NSSM setup, implementation notes, usage examples, monitoring)
- **CHANGELOG.md** — Created (this file)
- **HANDOFF.md** — Created with current state, open issues, and backlog
- **README.md** — Updated with correct NSSM install steps (including required env vars), PowerShell usage examples, and implementation notes

### Files Changed
- `src/index.js` — 4 bug fixes (+12 lines)
- `CLAUDE.md` — Full rewrite (build spec → living reference)
- `CHANGELOG.md` — Created
- `HANDOFF.md` — Created
- `README.md` — Updated

---

## [1.0.0] — 2026-02-23

### Added

- **Initial implementation** — Express HTTP server on `0.0.0.0:18790`
- **GET /cc/health** — Returns `{ ok: true, version: "1.0.0", host: "<hostname>" }`
- **GET /cc/sessions** — Scans `~/.claude/projects/` recursively for `*.jsonl` session files; returns metadata array sorted by `updatedAt` desc; skips subagent sessions (`agent-` prefix)
- **POST /cc/chat** — Sends message to Claude Code session via `claude -p --resume <id> --output-format json --dangerously-skip-permissions`; supports configurable `cwd` and `timeoutMs`; returns `{ ok, result, sessionId, raw }` on success
- **NSSM service** — Install script at `install_service.ps1`; service name `cc-bridge`
- **README.md** — Initial API documentation

### Tech Stack
- Node.js + Express 4
- No external dependencies beyond express
- Built-in: child_process, fs, path, os
