/**
 * cc-bridge — Claude Code HTTP Bridge
 * 
 * Runs on workstation (192.0.2.10:18790)
 * Lets docker-host (192.0.2.20) send messages to Claude Code sessions via HTTP.
 */

const express = require('express');
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { WebSocketServer } = require('ws');
const { createWsHandler } = require('./ws-handler');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 18790;
// os.homedir() falls back to the password database when USERPROFILE and HOME are
// both unset, which is the case a service account hits.
const HOME = process.env.USERPROFILE || process.env.HOME || os.homedir();
const LOCAL_SESSIONS_DIR = path.join(HOME, '.claude', 'projects');
const REMOTE_SESSIONS_DIR = path.join(HOME, 'docker-host-claude', 'projects');
const DEFAULT_CWD = process.env.CC_BRIDGE_CWD || process.cwd();
const DEFAULT_TIMEOUT_MS = 120_000;
const CC_BRIDGE_TOKEN = process.env.CC_BRIDGE_TOKEN || '';
const TITLE_CACHE_PATH = path.join(__dirname, '..', 'data', 'titles.json');

// On Windows, claude is an npm .cmd shim, which needs shell:true to spawn. Run as
// a service, APPDATA is often wrong or missing, so allow an explicit override and
// fall back to the bare name for PATH resolution.
const CLAUDE_CMD = process.platform === 'win32'
  ? (process.env.CLAUDE_CMD
     || (process.env.APPDATA ? path.join(process.env.APPDATA, 'npm', 'claude.cmd') : 'claude.cmd'))
  : (process.env.CLAUDE_CMD || 'claude');
const SPAWN_OPTS_BASE = process.platform === 'win32' ? { shell: true } : {};

// ─── Title Cache ─────────────────────────────────────────────────────────────

let titleCache = { version: 1, titles: {} };

// ─── Context Cache ───────────────────────────────────────────────────────────

const contextCache = {}; // { sessionId: { mtime, contextPct, compactionCount } }

function loadTitleCache() {
  try {
    if (fs.existsSync(TITLE_CACHE_PATH)) {
      const data = JSON.parse(fs.readFileSync(TITLE_CACHE_PATH, 'utf8'));
      if (data && typeof data.titles === 'object') {
        titleCache = data;
        console.log(`[cc-bridge] loaded ${Object.keys(titleCache.titles).length} cached titles`);
      }
    }
  } catch (_) {
    console.log('[cc-bridge] title cache parse failed, starting fresh');
    titleCache = { version: 1, titles: {} };
  }
}

function saveTitleCache() {
  try {
    const dir = path.dirname(TITLE_CACHE_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const tmp = TITLE_CACHE_PATH + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(titleCache, null, 2));
    fs.renameSync(tmp, TITLE_CACHE_PATH);
  } catch (err) {
    console.error('[cc-bridge] failed to save title cache:', err.message);
  }
}

// ─── Title Extraction Helpers ────────────────────────────────────────────────

const NOISE_PATTERNS = [
  /^<teammate-message\b/, /^<task-notification\b/, /^<system-reminder\b/,
  /^<command-name\b/, /^<local-command/, /^\{"type":\s*"idle_notification"/,
  /^\{"type":\s*"shutdown/, /^\{"type":\s*"task_completed"/,
  /^This session is being continued/, /^Stop hook feedback:/,
  /^\[Request interrupted by user/,
];

function isNoise(text) {
  const trimmed = text.trim();
  return NOISE_PATTERNS.some(p => p.test(trimmed));
}

function extractText(content) {
  if (typeof content === 'string') return content.trim();
  if (Array.isArray(content)) {
    return content
      .map(b => (typeof b === 'string' ? b : (b && b.type === 'text' ? b.text || '' : '')))
      .join(' ').trim();
  }
  return '';
}

function readFirstLines(filePath, maxLines) {
  const fd = fs.openSync(filePath, 'r');
  try {
    const buf = Buffer.alloc(128 * 1024);
    const bytesRead = fs.readSync(fd, buf, 0, buf.length, 0);
    const text = buf.toString('utf8', 0, bytesRead);
    return text.split('\n').filter(l => l.trim()).slice(0, maxLines);
  } finally {
    fs.closeSync(fd);
  }
}

function extractTitleFromSummaryMd(sessionDir) {
  const summaryPath = path.join(sessionDir, 'session-memory', 'summary.md');
  try {
    if (!fs.existsSync(summaryPath)) return null;
    const lines = fs.readFileSync(summaryPath, 'utf8').split('\n');
    // Line 5 (index 4) is the title per Claude Code's summary.md format
    const titleLine = lines[4];
    if (titleLine && titleLine.trim().length > 3) return titleLine.trim();
  } catch (_) {}
  return null;
}

function extractTitleFromCompactSummary(filePath) {
  try {
    const lines = readFirstLines(filePath, 200);
    for (const line of lines) {
      try {
        const parsed = JSON.parse(line);
        if (!parsed.isCompactSummary) continue;
        const content = parsed.message?.content;
        const text = typeof content === 'string' ? content : '';
        // Look for "# Session Title" section (some compaction formats include it)
        const match = text.match(/# Session Title[^\n]*\n[^\n]*\n\s*(.+)/);
        if (match) return match[1].trim();
      } catch (_) {}
    }
  } catch (_) {}
  return null;
}

function resolveTitle(sessionId, fullPath, firstUserMessage) {
  // 1. Cache hit (skip first_message — those are eligible for upgrade)
  const cached = titleCache.titles[sessionId];
  if (cached && cached.source !== 'first_message') return cached.title;

  // 2. summary.md
  const sessionDir = path.join(path.dirname(fullPath), sessionId);
  const summaryTitle = extractTitleFromSummaryMd(sessionDir);
  if (summaryTitle) {
    titleCache.titles[sessionId] = { title: summaryTitle, source: 'summary', generatedAt: Date.now() };
    return summaryTitle;
  }

  // 3. Compact summary
  const compactTitle = extractTitleFromCompactSummary(fullPath);
  if (compactTitle) {
    titleCache.titles[sessionId] = { title: compactTitle, source: 'compact', generatedAt: Date.now() };
    return compactTitle;
  }

  // 4. First user message (fallback — eligible for LLM title upgrade later)
  if (cached && cached.source === 'first_message') return cached.title;
  if (firstUserMessage) {
    titleCache.titles[sessionId] = { title: firstUserMessage, source: 'first_message', generatedAt: Date.now() };
    return firstUserMessage;
  }

  return null;
}

// ─── Context Info Extraction ─────────────────────────────────────────────────

function computeContextInfo(filePath, sessionId, mtimeMs) {
  const cached = contextCache[sessionId];
  if (cached && cached.mtime === mtimeMs) {
    return { contextPct: cached.contextPct, compactionCount: cached.compactionCount };
  }

  let lastUsage = null;
  let compactionCount = 0;

  try {
    const data = fs.readFileSync(filePath, 'utf8');
    for (const line of data.split('\n')) {
      if (!line.trim()) continue;
      try {
        const parsed = JSON.parse(line);
        if (parsed.type === 'system' && parsed.subtype === 'compact_boundary') {
          compactionCount++;
        }
        if (parsed.type === 'assistant' && parsed.message && parsed.message.usage) {
          lastUsage = parsed.message.usage;
        }
      } catch (_) {}
    }
  } catch (_) {
    contextCache[sessionId] = { mtime: mtimeMs, contextPct: null, compactionCount: 0 };
    return { contextPct: null, compactionCount: 0 };
  }

  let contextPct = null;
  if (lastUsage) {
    const input = (lastUsage.input_tokens || 0)
      + (lastUsage.cache_creation_input_tokens || 0)
      + (lastUsage.cache_read_input_tokens || 0);
    contextPct = Math.round(input / 200000 * 100);
  }

  contextCache[sessionId] = { mtime: mtimeMs, contextPct, compactionCount };
  return { contextPct, compactionCount };
}

// Load title cache on startup
loadTitleCache();

// ─── Health ──────────────────────────────────────────────────────────────────

// Public unauthenticated liveness endpoint (no /cc prefix) — for watchdog scripts.
app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'cc-bridge', uptime_s: Math.round(process.uptime()) });
});

app.get('/cc/health', (req, res) => {
  res.json({ ok: true, version: '2.0.0', host: require('os').hostname() });
});

// ─── Sessions ────────────────────────────────────────────────────────────────

app.get('/cc/sessions', (req, res) => {
  try {
    const sessions = [];
    scanSessions(LOCAL_SESSIONS_DIR, sessions, 'workstation');
    scanSessions(REMOTE_SESSIONS_DIR, sessions, 'docker-host');
    sessions.sort((a, b) => b.updatedAt - a.updatedAt);
    res.json({ ok: true, sessions });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

function scanSessions(dir, results, machine) {
  if (!fs.existsSync(dir)) return;
  let cacheChanged = false;

  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      scanSessions(fullPath, results, machine);
    } else if (entry.name.endsWith('.jsonl')) {
      try {
        const sessionId = entry.name.replace('.jsonl', '');

        // Skip subagent sessions early (before any I/O)
        if (sessionId.startsWith('agent-')) continue;

        const stat = fs.statSync(fullPath);
        const slug = path.basename(path.dirname(fullPath));

        // Single-pass: read first 50 lines for cwd + first user message
        let cwd = null;
        let firstUserMessage = null;
        try {
          const lines = readFirstLines(fullPath, 50);
          for (const line of lines) {
            try {
              const parsed = JSON.parse(line);
              if (!cwd && parsed.cwd) cwd = parsed.cwd;
              if (!firstUserMessage) {
                const msg = parsed.message || {};
                if (msg.role === 'user') {
                  const text = extractText(msg.content);
                  if (text && !isNoise(text)) {
                    firstUserMessage = text.replace(/\n/g, ' ').trim().slice(0, 120);
                  }
                }
              }
              if (cwd && firstUserMessage) break;
            } catch (_) {}
          }
        } catch (_) {}

        // Resolve title via 4-step cascade
        const prevCacheSize = Object.keys(titleCache.titles).length;
        const title = resolveTitle(sessionId, fullPath, firstUserMessage);
        if (Object.keys(titleCache.titles).length > prevCacheSize) cacheChanged = true;

        // Derive project name from cwd
        const projectsRoot = path.join(HOME, 'Projects');
        let project = null;
        if (cwd) {
          const rel = path.relative(projectsRoot, cwd);
          if (!rel.startsWith('..') && rel !== '') {
            project = rel.split(path.sep)[0] || null;
          }
        }

        const ctxInfo = computeContextInfo(fullPath, sessionId, stat.mtimeMs);

        results.push({
          sessionId, slug, project, cwd, title, machine, topic: title,
          updatedAt: Math.round(stat.mtimeMs), lastActivity: Math.round(stat.mtimeMs),
          filePath: fullPath,
          contextPct: ctxInfo.contextPct,
          compactionCount: ctxInfo.compactionCount,
        });
      } catch (_) {}
    }
  }

  // Persist cache if new titles were resolved (only at top-level call)
  if (cacheChanged && (dir === LOCAL_SESSIONS_DIR || dir === REMOTE_SESSIONS_DIR)) saveTitleCache();
}

// ─── Chat ─────────────────────────────────────────────────────────────────────

app.post('/cc/chat', (req, res) => {
  const { sessionId, message, cwd, timeoutMs } = req.body;

  if (!sessionId || typeof sessionId !== 'string') {
    return res.status(400).json({ ok: false, error: 'sessionId is required' });
  }
  if (!message || typeof message !== 'string') {
    return res.status(400).json({ ok: false, error: 'message is required' });
  }

  const workDir = cwd || DEFAULT_CWD;
  const timeout = timeoutMs || DEFAULT_TIMEOUT_MS;

  // Only use --resume if sessionId is a valid UUID (existing session)
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const isExistingSession = UUID_RE.test(sessionId);

  // Build args — message is passed via stdin (not as CLI arg) to avoid shell quoting issues
  // on Windows with shell:true. Claude -p reads from stdin when no positional message arg is given.
  const args = [
    '-p',
    ...(isExistingSession ? ['--resume', sessionId] : []),
    '--output-format', 'json',
    '--dangerously-skip-permissions',
  ];

  console.log(`[cc-bridge] chat session=${sessionId} cwd=${workDir}`);
  console.log(`[cc-bridge] message: ${message.slice(0, 80)}...`);

  let stdout = '';
  let stderr = '';
  let timedOut = false;

  const child = spawn(CLAUDE_CMD, args, {
    ...SPAWN_OPTS_BASE,
    cwd: workDir,
    env: { ...process.env },
    windowsHide: true,
    stdio: ['pipe', 'pipe', 'pipe'],  // pipe stdin so we can write the message, then close it
  });

  // Write message to stdin and close — claude -p reads message from stdin when no positional arg given.
  // This avoids shell quoting/escaping issues with multi-line or special-character messages on Windows.
  child.stdin.write(message, 'utf8');
  child.stdin.end();

  const timer = setTimeout(() => {
    timedOut = true;
    child.kill('SIGTERM');
    res.status(504).json({ ok: false, error: 'claude timed out', timeoutMs: timeout });
  }, timeout);

  // Idle watchdog � kill if no stdout for 120s
  const IDLE_TIMEOUT_MS = 120_000;
  let idleTimer = null;

  const resetIdleTimer = () => {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      console.log('[cc-bridge] idle timeout � killing child');
      child.kill('SIGTERM');
      if (!timedOut) {
        timedOut = true;
        clearTimeout(timer);
        res.status(504).json({
          ok: false,
          error: 'claude idle timeout � no output for 120s',
          partialOutput: stdout || null,
        });
      }
    }, IDLE_TIMEOUT_MS);
  };

  resetIdleTimer();

  child.stdout.on('data', (chunk) => { stdout += chunk.toString(); resetIdleTimer(); });
  child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });

  child.on('close', (code) => {
    clearTimeout(timer);
    if (idleTimer) clearTimeout(idleTimer);
    if (timedOut) return;

    console.log(`[cc-bridge] claude exited code=${code}`);

    if (code !== 0) {
      return res.status(500).json({
        ok: false,
        error: stderr || `claude exited with code ${code}`,
        exitCode: code,
        stdout,
      });
    }

    // Parse JSON output from claude
    try {
      const raw = JSON.parse(stdout.trim());
      return res.json({
        ok: true,
        result: raw.result || raw.content || stdout,
        sessionId: raw.session_id || sessionId,
        raw,
      });
    } catch (_) {
      // Not JSON — return raw text
      return res.json({
        ok: true,
        result: stdout,
        sessionId,
        raw: null,
      });
    }
  });

  child.on('error', (err) => {
    clearTimeout(timer);
    if (idleTimer) clearTimeout(idleTimer);
    if (!timedOut) {
      res.status(500).json({ ok: false, error: `spawn error: ${err.message}` });
    }
  });
});


// ─── Chat Stream (SSE) ──────────────────────────────────────────────────────

app.post('/cc/chat/stream', (req, res) => {
  const { sessionId, message, cwd, timeoutMs } = req.body;

  if (!sessionId || typeof sessionId !== 'string') {
    return res.status(400).json({ ok: false, error: 'sessionId is required' });
  }
  if (!message || typeof message !== 'string') {
    return res.status(400).json({ ok: false, error: 'message is required' });
  }

  const workDir = cwd || DEFAULT_CWD;
  const timeout = timeoutMs || DEFAULT_TIMEOUT_MS;

  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const isExistingSession = UUID_RE.test(sessionId);

  const args = [
    '-p',
    ...(isExistingSession ? ['--resume', sessionId] : []),
    '--output-format', 'stream-json',
    '--dangerously-skip-permissions',
  ];

  console.log(`[cc-bridge] stream session=${sessionId} cwd=${workDir}`);

  // SSE headers
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });

  let timedOut = false;
  let stderr = '';

  const child = spawn(CLAUDE_CMD, args, {
    ...SPAWN_OPTS_BASE,
    cwd: workDir,
    env: { ...process.env },
    windowsHide: true,
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  child.stdin.write(message, 'utf8');
  child.stdin.end();

  const timer = setTimeout(() => {
    timedOut = true;
    child.kill('SIGTERM');
    res.write(`data: ${JSON.stringify({ type: 'error', error: 'timeout' })}\n\n`);
    res.end();
  }, timeout);

  // Idle watchdog for streaming too
  const IDLE_TIMEOUT_MS = 120_000;
  let idleTimer = setTimeout(() => {
    child.kill('SIGTERM');
    if (!timedOut) {
      timedOut = true;
      clearTimeout(timer);
      res.write(`data: ${JSON.stringify({ type: 'error', error: 'idle timeout' })}\n\n`);
      res.end();
    }
  }, IDLE_TIMEOUT_MS);

  // Forward each line of stream-json as an SSE event
  let buffer = '';
  child.stdout.on('data', (chunk) => {
    // Reset idle timer
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      child.kill('SIGTERM');
      if (!timedOut) {
        timedOut = true;
        clearTimeout(timer);
        res.write(`data: ${JSON.stringify({ type: 'error', error: 'idle timeout' })}\n\n`);
        res.end();
      }
    }, IDLE_TIMEOUT_MS);

    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop(); // Keep incomplete last line in buffer

    for (const line of lines) {
      if (line.trim()) {
        res.write(`data: ${line}\n\n`);
      }
    }
  });

  child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });

  child.on('close', (code) => {
    clearTimeout(timer);
    clearTimeout(idleTimer);
    if (timedOut) return;

    // Flush remaining buffer
    if (buffer.trim()) {
      res.write(`data: ${buffer}\n\n`);
    }

    // Send completion event
    res.write(`data: ${JSON.stringify({ type: 'done', exitCode: code, sessionId })}\n\n`);
    res.end();
  });

  child.on('error', (err) => {
    clearTimeout(timer);
    clearTimeout(idleTimer);
    if (!timedOut) {
      res.write(`data: ${JSON.stringify({ type: 'error', error: err.message })}\n\n`);
      res.end();
    }
  });

  // Handle client disconnect
  req.on('close', () => {
    clearTimeout(timer);
    clearTimeout(idleTimer);
    child.kill('SIGTERM');
  });
});
// ─── WebSocket + Start ──────────────────────────────────────────────────────

const wss = new WebSocketServer({ noServer: true });

// Lazy-load SessionManager (ESM module)
let sessionManagerPromise = null;
function getSessionManager() {
  if (!sessionManagerPromise) {
    sessionManagerPromise = import('./session-manager.js').then(mod => {
      const sm = new mod.SessionManager();
      sm.start();
      console.log('[cc-bridge] SessionManager started');
      return sm;
    });
  }
  return sessionManagerPromise;
}

// Boot: start HTTP server, wire WebSocket upgrade
const server = app.listen(PORT, '0.0.0.0', async () => {
  console.log(`cc-bridge listening on 0.0.0.0:${PORT}`);
  console.log(`workstation sessions: ${LOCAL_SESSIONS_DIR}`);
  console.log(`docker-host sessions: ${REMOTE_SESSIONS_DIR}`);
  console.log(`Default cwd:  ${DEFAULT_CWD}`);

  // Pre-warm SessionManager
  const sm = await getSessionManager();
  const handler = createWsHandler({ sessionManager: sm, token: CC_BRIDGE_TOKEN });
  wss.on('connection', handler);

  console.log('[cc-bridge] WebSocket server ready on /cc/ws');
});

server.on('upgrade', (req, socket, head) => {
  const { pathname } = new URL(req.url, `http://${req.headers.host}`);
  if (pathname === '/cc/ws') {
    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req);
    });
  } else {
    socket.destroy();
  }
});

// Graceful shutdown
process.on('SIGTERM', async () => {
  console.log('[cc-bridge] SIGTERM received, shutting down...');
  try {
    const sm = await getSessionManager();
    await sm.shutdown();
  } catch (_) {}
  wss.close();
  server.close();
  process.exit(0);
});
