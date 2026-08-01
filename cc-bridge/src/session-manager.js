// cc-bridge/src/session-manager.js -- SessionManager
// Tracks all active Claude Code sessions spawned via AgentSDKTransport.

import { EventEmitter } from 'node:events';

// Lazy-loaded transport
let _TransportClass = null;
async function getTransport() {
  if (!_TransportClass) {
    const mod = await import('./transport.js');
    _TransportClass = mod.AgentSDKTransport;
  }
  return _TransportClass;
}

// Lazy-loaded BrainController
let _BrainControllerClass = null;
async function getBrainController() {
  if (!_BrainControllerClass) {
    const mod = await import('./brain-controller.js');
    _BrainControllerClass = mod.BrainController;
  }
  return _BrainControllerClass;
}

// ---- Limits ----
const MAX_SESSIONS = 5;
const MAX_CLIENTS_PER_SESSION = 3;
const IDLE_BACKGROUND_MS = 10 * 60 * 1000; // 10 min
const IDLE_ORPHAN_MS = 2 * 60 * 1000;      // 2 min
const REAPER_INTERVAL_MS = 60 * 1000;       // every 60s

let nextId = 1;

/**
 * SessionManager -- owns all active Claude Code sessions.
 *
 * Events emitted:
 *   'session:created'   (session)
 *   'session:destroyed' (id, reason)
 *   'session:attached'  (id, ws)
 *   'session:detached'  (id, ws)
 */
export class SessionManager extends EventEmitter {
  constructor() {
    super();
    /** @type {Map<string, Session>} */
    this.sessions = new Map();
    this._reaperTimer = null;
    this.brain = null;
  }

  /** Start the idle reaper. Call once at boot. */
  async start() {
    if (this._reaperTimer) return;
    this._reaperTimer = setInterval(() => this._reap(), REAPER_INTERVAL_MS);
    this._reaperTimer.unref();

    // Initialize BrainController
    try {
      const BrainCtrl = await getBrainController();
      this.brain = new BrainCtrl({ sessionManager: this });
      this.brain.start();
    } catch (err) {
      console.error("[session-manager] Failed to init BrainController:", err.message);
    }
  }

  /** Stop the idle reaper and destroy all sessions. */
  async shutdown() {
    // Stop BrainController
    if (this.brain) {
      this.brain.stop();
      this.brain = null;
    }
    if (this._reaperTimer) {
      clearInterval(this._reaperTimer);
      this._reaperTimer = null;
    }
    const ids = Array.from(this.sessions.keys());
    for (const id of ids) {
      this.destroy(id, 'shutdown');
    }
  }

  /**
   * Create a new session.
   * @param {object} params
   * @param {string} params.cwd
   * @param {string} [params.project]
   * @param {string} [params.backend] -- 'cloud' | 'local'
   * @param {string} [params.model]
   * @param {string} [params.permissionMode]
   * @param {string} [params.systemPrompt]
   * @param {string} [params.initialPrompt]
   * @returns {Promise<Session>}
   */
  async create(params) {
    if (this.sessions.size >= MAX_SESSIONS) {
      throw new Error('Max sessions (' + MAX_SESSIONS + ') reached');
    }

    const id = 's' + (nextId++);
    const Transport = await getTransport();
    const transport = new Transport();

    const session = {
      id,
      transport,
      status: 'spawning',
      clients: new Set(),
      config: {
        project: params.project || null,
        cwd: params.cwd,
        backend: params.backend || 'cloud',
        model: params.model || null,
        permissionMode: params.permissionMode || 'bypassPermissions',
        resumedFrom: params.resume || null,
      },
      createdAt: Date.now(),
      lastActivityAt: Date.now(),
      sdkSessionId: null,
    };

    this.sessions.set(id, session);

    // Wire transport events
    this._wireTransport(id, transport);

    // Build spawn options
    const spawnOpts = {
      cwd: params.cwd,
      permissionMode: session.config.permissionMode,
    };

    // Build brain-enriched system prompt
    let brainPrompt = null;
    if (this.brain) {
      try {
        brainPrompt = this.brain.buildSystemPrompt();
      } catch (_) {}
    }
    if (params.systemPrompt || brainPrompt) {
      spawnOpts.systemPrompt = [params.systemPrompt, brainPrompt].filter(Boolean).join(String.fromCharCode(10, 10));
    }
    if (params.model) spawnOpts.model = params.model;
    if (params.resume) spawnOpts.resume = params.resume;
    if (params.initialPrompt) spawnOpts.initialPrompt = params.initialPrompt;
    else if (params.resume) spawnOpts.initialPrompt = 'Session resumed.';

    // Local model support: inject env vars
    if (session.config.backend === 'local') {
      spawnOpts.env = {
        ANTHROPIC_BASE_URL: 'http://localhost:8080',
        ANTHROPIC_API_KEY: '',
        CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC: '1',
      };
    }

    await transport.spawn(spawnOpts);

    this.emit('session:created', session);

    // Record brain event
    if (this.brain) {
      this.brain.recordEvent(id, 'created', 'Session created for ' + (session.config.project || session.config.cwd)).catch(function() {});
    }
    return session;
  }

  /**
   * Attach a WebSocket client to a session.
   */
  attach(id, ws) {
    const session = this._get(id);
    if (session.clients.size >= MAX_CLIENTS_PER_SESSION) {
      throw new Error('Max clients (' + MAX_CLIENTS_PER_SESSION + ') for session ' + id);
    }
    session.clients.add(ws);
    session.lastActivityAt = Date.now();
    this.emit('session:attached', id, ws);
  }

  /**
   * Detach a WebSocket client. Session stays alive in background.
   */
  detach(id, ws) {
    const session = this.sessions.get(id);
    if (!session) return;
    session.clients.delete(ws);
    this.emit('session:detached', id, ws);
  }

  /**
   * Send a message to a session.
   */
  async send(id, message, deltaPrefix) {
    const session = this._get(id);
    if (session.transport._streaming) {
      throw new Error('Session ' + id + ' is currently streaming');
    }
    session.lastActivityAt = Date.now();

    // Enrich with brain delta if no explicit deltaPrefix provided
    let prefix = deltaPrefix;
    if (!prefix && this.brain) {
      try {
        prefix = this.brain.getDeltaForSession(id);
      } catch (_) {}
    }
    await session.transport.send(message, prefix || undefined);
  }

  /**
   * Interrupt the current generation.
   */
  async interrupt(id) {
    const session = this._get(id);
    await session.transport.interrupt();
  }

  /**
   * Destroy a session and clean up.
   */
  destroy(id, reason) {
    const session = this.sessions.get(id);
    if (!session) return;

    // Record brain event
    if (this.brain) {
      this.brain.recordEvent(id, 'destroyed', 'Session destroyed: ' + (reason || 'unknown')).catch(function() {});
    }

    // Close all attached clients
    for (const ws of session.clients) {
      try {
        ws.send(JSON.stringify({
          type: 'event',
          event: 'session.closed',
          sessionId: id,
          data: { reason: reason || 'destroyed' },
        }));
      } catch (_) {}
    }
    session.clients.clear();

    // Kill transport
    session.transport.close();

    this.sessions.delete(id);
    this.emit('session:destroyed', id, reason || 'destroyed');
  }

  /**
   * List all sessions with summary info.
   */
  list() {
    const result = [];
    for (const [id, s] of this.sessions) {
      result.push({
        id,
        status: s.transport.status,
        sdkSessionId: s.sdkSessionId,
        config: s.config,
        clients: s.clients.size,
        createdAt: s.createdAt,
        lastActivityAt: s.lastActivityAt,
      });
    }
    return result;
  }

  /**
   * Get a single session by ID.
   */
  get(id) {
    return this.sessions.get(id) || null;
  }

  // ---- Internal ----

  _get(id) {
    const session = this.sessions.get(id);
    if (!session) throw new Error('Session ' + id + ' not found');
    return session;
  }

  /**
   * Broadcast a message to all attached WebSocket clients.
   */
  _broadcast(id, msg) {
    const session = this.sessions.get(id);
    if (!session) return;
    const payload = JSON.stringify(msg);
    for (const ws of session.clients) {
      try {
        if (ws.readyState === 1) { // WebSocket.OPEN
          ws.send(payload);
        }
      } catch (_) {}
    }
  }

  /**
   * Wire transport events to broadcast to attached clients.
   */
  _wireTransport(id, transport) {
    transport.on('init', (data) => {
      const session = this.sessions.get(id);
      if (session) {
        session.sdkSessionId = data.session_id;
        session.status = 'idle';
      }
      this._broadcast(id, {
        type: 'event',
        event: 'session.init',
        sessionId: id,
        data,
      });
    });

    transport.on('stream', (streamEvent, meta) => {
      this._broadcast(id, {
        type: 'event',
        event: 'session.stream',
        sessionId: id,
        data: { streamEvent, meta },
      });
    });

    transport.on('result', (resultMsg) => {
      const session = this.sessions.get(id);
      if (session) {
        session.lastActivityAt = Date.now();
      }
      this._broadcast(id, {
        type: 'event',
        event: 'session.result',
        sessionId: id,
        data: resultMsg,
      });
    });

    transport.on('status', (statusData) => {
      this._broadcast(id, {
        type: 'event',
        event: 'session.status',
        sessionId: id,
        data: statusData,
      });
    });

    transport.on('assistant', (assistantMsg) => {
      this._broadcast(id, {
        type: 'event',
        event: 'session.assistant'
,
        sessionId: id,
        data: assistantMsg,
      });
    });

    transport.on('error', (err) => {
      this._broadcast(id, {
        type: 'event',
        event: 'session.error',
        sessionId: id,
        data: { message: err.message },
      });
    });

    transport.on('close', () => {
      const session = this.sessions.get(id);
      if (session) {
        this.destroy(id, 'transport_closed');
      }
    });
  }

  /**
   * Reap idle sessions.
   * - Orphan (0 clients): destroy after IDLE_ORPHAN_MS
   * - Background (has clients but idle): destroy after IDLE_BACKGROUND_MS
   */
  _reap() {
    const now = Date.now();
    for (const [id, session] of this.sessions) {
      const idle = now - session.lastActivityAt;
      const isOrphan = session.clients.size === 0;

      if (isOrphan && idle > IDLE_ORPHAN_MS) {
        this.destroy(id, 'idle_orphan');
      } else if (!isOrphan && session.transport.status === 'idle' && idle > IDLE_BACKGROUND_MS) {
        this.destroy(id, 'idle_background');
      }
    }
  }
}
