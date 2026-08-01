# Phase 5 — LLM switching + llama backup (chat vs. the coding loop)

Two *different* surfaces can run on Claude or on the local workstation llama-server. Don't conflate them.

## 1. In-app CHAT backend — one-tap, in the chat header  ✅ shipped

The chat header has a **`Cloud | Local`** toggle (next to the model chip). The highlighted segment
is the badge — it shows which backend is serving chat *right now* (derived from the active model,
`chatBackendOf()`); tapping the other segment switches.

- **Local** → `role.preset.apply local` → every role (incl. chat) uses llama-server. Free.
- **Cloud** → cost-confirm dialog → `role.preset.apply cloud` → Anthropic Claude. Paid per token.

This is the "llama is a real backup for chat" path: if Claude is down or quota'd, tap **Local** and
keep chatting on llama. It is **manual** — there is no automatic failover (see note below).

### No automatic failover (by design, for now)
Ironjaw's `FailoverProvider` exists but is **not wired** into the live router, and the router returns
an error (rather than falling back) when a prefixed cloud model is unhealthy. So a mid-chat Claude
outage does **not** auto-switch you to llama — you flip to Local yourself. Building real auto-failover
is an Ironjaw (Rust) change touching the shared workstation gateway; deferred until wanted.

## 2. The on-phone CODING loop (`claude` in the proot) — base-URL override  ⚠️ DEGRADED, optional

The cc-bridge daemon spawns the **`claude` CLI** in the proot for agentic coding. To run *that* loop
on llama when there's no Anthropic quota / no internet:

**Gotcha:** the `claude` CLI speaks the **Anthropic Messages API**. llama-server speaks the **OpenAI**
API. So `ANTHROPIC_BASE_URL` **cannot point straight at llama-server** (`:8080`) — the protocols
differ. You need an Anthropic-compatible shim in front of llama. Free options:

- **claude-code-router** (`@musistudio/claude-code-router`, MIT) — purpose-built to point Claude Code
  at OpenAI-compatible backends.
- **LiteLLM proxy** (`litellm[proxy]`, MIT) — exposes an `/anthropic` passthrough that translates
  Anthropic ↔ OpenAI; point it at `http://192.0.2.10:8080/v1`.

Then, for the spawned `claude` process only (env, not a global):

```sh
export ANTHROPIC_BASE_URL="http://<shim-host>:<port>"   # the shim, NOT llama-server directly
export ANTHROPIC_AUTH_TOKEN="dummy"                      # shim ignores it; CLI requires it set
claude ...
```

**Flagged DEGRADED:** a local llama model is *not* Claude for agentic coding — expect weaker tool-use,
planning, and instruction-following. Use only as an offline/no-quota stopgap, not the default.
Not built here (optional); this is the recipe if/when it's needed.
