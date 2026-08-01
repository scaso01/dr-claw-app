// cc-bridge/src/brain-controller.js -- BrainController
// Cross-session awareness via shared memory files and cc-chronicle integration.
// Reads/writes ~/.claude/brain/ directory for inter-session communication.
//
// NOTE: File-based memory (STATUS.md, MEMORY.md, inbox/) is DEPRECATED.
// Primary knowledge store is now ironjaw-brain HTTP API (:18794).
// File system is kept as fallback only. Will be removed in a future version.

import { EventEmitter } from 'node:events';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import os from 'node:os';

const BRAIN_DIR = path.join(os.homedir(), '.claude', 'brain');
const INBOX_DIR = path.join(BRAIN_DIR, 'inbox');
const STATUS_PATH = path.join(BRAIN_DIR, 'STATUS.md');
const MEMORY_PATH = path.join(BRAIN_DIR, 'MEMORY.md');

const BRAIN_API_BASE = 'http://localhost:18794';
const BRAIN_API_TIMEOUT_MS = 2000;
const STATUS_INTERVAL_MS = 10_000; // 10 seconds (30s when brain API active)
const STATUS_INTERVAL_DEGRADED_MS = 30_000; // 30 seconds when brain API is primary
const MAX_SYSTEM_PROMPT_TOKENS = 500; // approximate cap (~4 chars/token)
const MAX_SYSTEM_PROMPT_CHARS = MAX_SYSTEM_PROMPT_TOKENS * 4;

/**
 * BrainController -- cross-session awareness for cc-bridge.
 *
 * Provides:
 *   - System prompt enrichment from STATUS.md + MEMORY.md
 *   - Per-session delta tracking (accumulated events since last read)
 *   - Event recording to cc-chronicle (non-fatal)
 *   - File watcher on brain/inbox/ for inter-session messages
 *   - Periodic STATUS.md + MEMORY.md updates
 */
export class BrainController extends EventEmitter {
  /**
   * @param {object} opts
   * @param {import('./session-manager.js').SessionManager} opts.sessionManager
   * @param {string} [opts.chronicleUrl]
   */
  constructor({ sessionManager, chronicleUrl = 'http://localhost:18793' }) {
    super();
    this.sessionManager = sessionManager;
    this.chronicleUrl = chronicleUrl;

    /** @type {Map<string, number>} sessionId -> last-seen timestamp */
    this._lastSeen = new Map();

    /** @type {Map<string, string[]>} sessionId -> accumulated delta lines */
    this._deltas = new Map();

    /** @type {Array<{key: string, value: string, from: string, ts: number}>} */
    this._sharedMemory = [];

    this._statusTimer = null;
    this._fileWatcher = null;
    this._startedAt = Date.now();
    this._lastEventTs = null;

    /** When true, prefer brain HTTP API over file-based memory */
    this._useBrainApi = true;

    /** Cached brain API context (refreshed periodically) */
    this._brainContextCache = '';
    this._brainContextTimer = null;
  }

  /**
   * Initialize the brain: ensure dirs exist, start timers, start file watcher.
   */
  start() {
    this._ensureDirs();
    this._startStatusTimer();
    this._startFileWatcher();
    if (this._useBrainApi) {
      this._startBrainContextRefresh();
    }
    console.log('[brain] BrainController started');
  }

  /**
   * Stop all timers and watchers.
   */
  stop() {
    if (this._statusTimer) {
      clearInterval(this._statusTimer);
      this._statusTimer = null;
    }
    if (this._fileWatcher) {
      this._fileWatcher.close();
      this._fileWatcher = null;
    }
    if (this._brainContextTimer) {
      clearInterval(this._brainContextTimer);
      this._brainContextTimer = null;
    }
    console.log('[brain] BrainController stopped');
  }
  // --- Public API -----------------------------------------------------------

  /**
   * Build a ~500-token system prompt from cached brain API context or STATUS.md/MEMORY.md.
   * Returns empty string if no context is available.
   * Synchronous — brain API context is refreshed in the background.
   * @returns {string}
   */
  buildSystemPrompt() {
    // Use cached brain API context when available
    if (this._useBrainApi && this._brainContextCache) {
      let prompt = '<brain-context>\n' + this._brainContextCache + '\n</brain-context>';
      if (prompt.length > MAX_SYSTEM_PROMPT_CHARS) {
        prompt = prompt.slice(0, MAX_SYSTEM_PROMPT_CHARS) + '\n...(truncated)';
      }
      return prompt;
    }

    // Fallback to file-based system
    const parts = [];

    const status = this._readFileSafe(STATUS_PATH);
    if (status) {
      parts.push('<brain-status>\n' + status + '\n</brain-status>');
    }

    const memory = this._readFileSafe(MEMORY_PATH);
    if (memory) {
      parts.push('<brain-memory>\n' + memory + '\n</brain-memory>');
    }

    if (parts.length === 0) return '';

    let prompt = parts.join('\n\n');

    // Truncate to approximate token budget
    if (prompt.length > MAX_SYSTEM_PROMPT_CHARS) {
      prompt = prompt.slice(0, MAX_SYSTEM_PROMPT_CHARS) + '\n...(truncated)';
    }

    return prompt;
  }

  /**
   * Get accumulated deltas for a session since its last message.
   * Returns empty string if no new events.
   * Clears the delta buffer for this session after reading.
   * @param {string} sessionId
   * @returns {string}
   */
  getDeltaForSession(sessionId) {
    const deltas = this._deltas.get(sessionId);
    if (!deltas || deltas.length === 0) return '';

    const result = deltas.join('\n');
    this._deltas.set(sessionId, []);
    this._lastSeen.set(sessionId, Date.now());
    return result;
  }

  /**
   * Record an event to brain API + cc-chronicle. Non-fatal if either is down.
   * @param {string} sessionId
   * @param {string} eventType
   * @param {string} summary
   */
  async recordEvent(sessionId, eventType, summary) {
    this._lastEventTs = Date.now();

    // Add to delta buffers for all OTHER sessions
    const deltaLine = '[' + new Date().toISOString() + '] ' + eventType + ': ' + summary + ' (session: ' + sessionId + ')';
    for (const [id] of this.sessionManager.sessions) {
      if (id === sessionId) continue;
      if (!this._deltas.has(id)) this._deltas.set(id, []);
      this._deltas.get(id).push(deltaLine);
    }

    // POST to brain API (fire-and-forget, non-blocking)
    if (this._useBrainApi) {
      this._postToBrainApi('/api/brain/remember', {
        content: eventType + ': ' + summary,
        memory_type: 'event',
        source_type: 'cc_bridge',
        importance: 0.5,
        source: sessionId,
      });
    }

    // POST to cc-chronicle (non-fatal)
    try {
      await fetch(this.chronicleUrl + '/api/brain/events', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId,
          eventType,
          summary,
          timestamp: Date.now(),
        }),
        signal: AbortSignal.timeout(5000),
      });
    } catch (_) {
      // cc-chronicle may not be running - silently ignore
    }
  }
  // --- File Watcher ---------------------------------------------------------

  /**
   * Start watching ~/.claude/brain/inbox/ for new files.
   * When a file appears: read, store in shared memory, add to deltas, delete.
   */
  _startFileWatcher() {
    if (!fs.existsSync(INBOX_DIR)) return;

    try {
      this._fileWatcher = fs.watch(INBOX_DIR, (eventType, filename) => {
        if (eventType !== 'rename' || !filename) return;

        const filePath = path.join(INBOX_DIR, filename);

        // Small delay to ensure file is fully written
        setTimeout(() => {
          this._processInboxFile(filePath, filename);
        }, 100);
      });

      this._fileWatcher.on('error', (err) => {
        console.error('[brain] file watcher error:', err.message);
      });
    } catch (err) {
      console.error('[brain] failed to start file watcher:', err.message);
    }
  }

  /**
   * Process a single inbox file.
   * @param {string} filePath
   * @param {string} filename
   */
  _processInboxFile(filePath, filename) {
    try {
      if (!fs.existsSync(filePath)) return;

      const content = fs.readFileSync(filePath, 'utf8').trim();
      if (!content) {
        fs.unlinkSync(filePath);
        return;
      }

      // Store in shared memory
      const entry = {
        key: filename.replace(/\.[^.]+$/, ''),
        value: content,
        from: 'inbox',
        ts: Date.now(),
      };
      this._sharedMemory.push(entry);

      // Cap shared memory to 50 entries
      if (this._sharedMemory.length > 50) {
        this._sharedMemory = this._sharedMemory.slice(-50);
      }

      // Add to delta buffers for ALL sessions
      const deltaLine = '[' + new Date().toISOString() + '] inbox: ' + filename + ' -> ' + content.slice(0, 200);
      for (const [id] of this.sessionManager.sessions) {
        if (!this._deltas.has(id)) this._deltas.set(id, []);
        this._deltas.get(id).push(deltaLine);
      }

      // POST inbox content to brain API (fire-and-forget)
      if (this._useBrainApi) {
        this._postToBrainApi('/api/brain/remember', {
          content: filename + ': ' + content,
          memory_type: 'fact',
          source_type: 'cc_bridge',
          importance: 0.6,
        });
      }

      // Delete processed file
      fs.unlinkSync(filePath);

      // Update MEMORY.md with new shared memory
      this.updateMemoryFile();

      console.log('[brain] processed inbox file:', filename);
    } catch (err) {
      console.error('[brain] failed to process inbox file:', filename, err.message);
    }
  }
  // --- Status + Memory File Updates -----------------------------------------

  /**
   * Start periodic STATUS.md updates.
   * When brain API is active, interval is 30s (degraded); otherwise 10s.
   */
  _startStatusTimer() {
    this.updateStatusFile();
    const interval = this._useBrainApi ? STATUS_INTERVAL_DEGRADED_MS : STATUS_INTERVAL_MS;
    this._statusTimer = setInterval(() => {
      this.updateStatusFile();
    }, interval);
    this._statusTimer.unref();
  }

  /**
   * Start periodic brain API context refresh (every 30s).
   * Fetches context immediately on start, then refreshes in the background.
   */
  _startBrainContextRefresh() {
    // Fetch immediately
    this._refreshBrainContext();
    this._brainContextTimer = setInterval(() => {
      this._refreshBrainContext();
    }, STATUS_INTERVAL_DEGRADED_MS);
    this._brainContextTimer.unref();
  }

  /**
   * Refresh the cached brain API context. Non-blocking.
   */
  _refreshBrainContext() {
    this._fetchBrainContext('general').then((ctx) => {
      if (ctx) {
        this._brainContextCache = ctx;
      }
    });
  }

  /**
   * Write STATUS.md with active sessions, uptime, and last event timestamp.
   */
  updateStatusFile() {
    try {
      const lines = ['# CC Bridge Brain Status', ''];

      // Uptime
      const uptimeSec = Math.floor((Date.now() - this._startedAt) / 1000);
      const hours = Math.floor(uptimeSec / 3600);
      const mins = Math.floor((uptimeSec % 3600) / 60);
      lines.push('**Uptime:** ' + hours + 'h ' + mins + 'm');

      // Last event
      if (this._lastEventTs) {
        lines.push('**Last event:** ' + new Date(this._lastEventTs).toISOString());
      } else {
        lines.push('**Last event:** none');
      }

      lines.push('');

      // Active sessions
      const sessions = this.sessionManager.list();
      if (sessions.length === 0) {
        lines.push('No active sessions.');
      } else {
        lines.push('## Active Sessions (' + sessions.length + ')');
        lines.push('');
        for (const s of sessions) {
          const status = s.status || 'unknown';
          const project = (s.config && s.config.project) || 'n/a';
          lines.push('- **' + s.id + '** - status: ' + status + ', project: ' + project);
        }
      }

      lines.push('');

      this._writeFileSafe(STATUS_PATH, lines.join('\n'));
    } catch (err) {
      console.error('[brain] failed to update STATUS.md:', err.message);
    }
  }

  /**
   * Write MEMORY.md from in-memory shared memory entries.
   */
  updateMemoryFile() {
    try {
      const lines = ['# Shared Memory', ''];

      if (this._sharedMemory.length === 0) {
        lines.push('No shared memories yet.');
      } else {
        lines.push(this._sharedMemory.length + ' entries:');
        lines.push('');
        for (const entry of this._sharedMemory) {
          const ts = new Date(entry.ts).toISOString();
          lines.push('### ' + entry.key);
          lines.push('*From:* ' + entry.from + ' | *At:* ' + ts);
          lines.push('');
          lines.push(entry.value);
          lines.push('');
        }
      }

      this._writeFileSafe(MEMORY_PATH, lines.join('\n'));
    } catch (err) {
      console.error('[brain] failed to update MEMORY.md:', err.message);
    }
  }
  // --- Brain HTTP API -------------------------------------------------------

  /**
   * Fetch context from the brain HTTP API.
   * Returns the response body as a string, or empty string on failure.
   * @param {string} [project]
   * @returns {Promise<string>}
   */
  _fetchBrainContext(project) {
    return new Promise((resolve) => {
      const query = encodeURIComponent(project || 'general');
      const url = BRAIN_API_BASE + '/api/brain/context?project=' + query;
      const req = http.get(url, { timeout: BRAIN_API_TIMEOUT_MS }, (res) => {
        if (res.statusCode !== 200) {
          res.resume(); // drain response
          resolve('');
          return;
        }
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => resolve(data.trim()));
      });
      req.on('error', () => resolve(''));
      req.on('timeout', () => { req.destroy(); resolve(''); });
    });
  }

  /**
   * POST data to a brain API endpoint. Fire-and-forget — never blocks,
   * never throws. Logs errors at debug level only.
   * @param {string} apiPath - e.g. '/api/brain/remember'
   * @param {object} body - JSON-serializable payload
   */
  _postToBrainApi(apiPath, body) {
    try {
      const payload = JSON.stringify(body);
      const url = new URL(apiPath, BRAIN_API_BASE);
      const req = http.request(
        {
          hostname: url.hostname,
          port: url.port,
          path: url.pathname,
          method: 'POST',
          timeout: BRAIN_API_TIMEOUT_MS,
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload),
          },
        },
        (res) => { res.resume(); } // drain response, we don't need it
      );
      req.on('error', () => {}); // silently ignore
      req.on('timeout', () => { req.destroy(); });
      req.write(payload);
      req.end();
    } catch (_) {
      // Never block or throw on brain API failures
    }
  }

  // --- Helpers --------------------------------------------------------------

  /**
   * Ensure brain directories exist.
   */
  _ensureDirs() {
    try {
      fs.mkdirSync(BRAIN_DIR, { recursive: true });
      fs.mkdirSync(INBOX_DIR, { recursive: true });
    } catch (err) {
      console.error('[brain] failed to create brain dirs:', err.message);
    }
  }

  /**
   * Read a file safely. Returns null if file does not exist or is empty.
   * @param {string} filePath
   * @returns {string|null}
   */
  _readFileSafe(filePath) {
    try {
      if (!fs.existsSync(filePath)) return null;
      const content = fs.readFileSync(filePath, 'utf8').trim();
      return content || null;
    } catch (_) {
      return null;
    }
  }

  /**
   * Write a file atomically (temp + rename).
   * @param {string} filePath
   * @param {string} content
   */
  _writeFileSafe(filePath, content) {
    const tmp = filePath + '.tmp';
    fs.writeFileSync(tmp, content, 'utf8');
    fs.renameSync(tmp, filePath);
  }
}
