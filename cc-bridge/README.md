# cc-bridge v2.0 — Session Daemon

WebSocket session daemon that spawns, owns, and multiplexes Claude Code sessions via the Agent SDK (`@anthropic-ai/claude-agent-sdk`).

## Architecture

Express HTTP + `ws` WebSocket server on `:18790`.

```
Dr. CLAW App → wss://gateway.example.com/cc/ws → Caddy → cc-bridge :18790
```

## Key Files

| File | Purpose |
|------|---------|
| `src/index.js` | Server entry point, Express + WS upgrade handler |
| `src/transport.js` | AgentSDKTransport — wraps Claude Agent SDK as EventEmitter |
| `src/session-manager.js` | SessionManager — lifecycle, idle reaper, resource limits |
| `src/ws-handler.js` | WebSocket message routing + challenge/response auth |
| `src/brain-controller.js` | BrainController — cross-session awareness via shared memory |

## Auth

Challenge/response with the `CC_BRIDGE_TOKEN` env var (the same token the gateway is configured with).

1. Client connects to `ws://host:18790/cc/ws`
2. Server sends `auth.challenge` event
3. Client responds with `auth` request containing token
4. Server verifies and responds ok/fail

## WebSocket Protocol

**Request:** `{ "type": "req", "id": "uuid", "method": "...", "params": {...} }`
**Response:** `{ "type": "res", "id": "uuid", "ok": true/false, "payload": {...} }`
**Event:** `{ "type": "event", "event": "session.stream", "sessionId": "...", "data": {...} }`

### Methods

| Method | Description |
|--------|-------------|
| `sessions.list` | List active sessions |
| `sessions.create` | Create new session (cwd, backend, model, permissionMode) |
| `sessions.attach` | Subscribe to session events |
| `sessions.detach` | Unsubscribe from session events |
| `sessions.send` | Send message to session |
| `sessions.interrupt` | Interrupt active generation |
| `sessions.destroy` | Kill session |
| `models.list` | List local models from llama-server |

## Resource Limits

| Setting | Value |
|---------|-------|
| Max active sessions | 5 |
| Idle timeout (background) | 10 min |
| Idle timeout (orphan) | 2 min |
| Max concurrent messages | 1 per session |
| WebSocket clients per session | 3 |

## Running

```bash
# Via Task Scheduler (production)
schtasks /Run /TN "CC Bridge"

# Foreground (development)
cd cc-bridge && CC_BRIDGE_TOKEN=<token> node src/index.js
```
