// cc-bridge/src/transport.js — AgentSDKTransport
// Wraps @anthropic-ai/claude-agent-sdk query() as an EventEmitter for
// persistent, multi-turn Claude Code sessions with real-time streaming.

import { EventEmitter } from 'node:events';
import path from 'node:path';

// Lazy-loaded SDK reference (ESM dynamic import)
let _query = null;
async function getQuery() {
  if (!_query) {
    const sdk = await import('@anthropic-ai/claude-agent-sdk');
    _query = sdk.query;
  }
  return _query;
}

/**
 * Push-based async iterable for feeding messages to the SDK query.
 * The query() generator stays alive as long as this iterable hasn't ended.
 */
function createMessageChannel() {
  const queue = [];
  let waiting = null;
  let closed = false;

  return {
    async *[Symbol.asyncIterator]() {
      while (!closed) {
        if (queue.length > 0) {
          yield queue.shift();
        } else {
          await new Promise((resolve) => { waiting = resolve; });
          waiting = null;
        }
      }
      // Drain remaining
      while (queue.length > 0) yield queue.shift();
    },
    push(msg) {
      queue.push(msg);
      if (waiting) waiting();
    },
    close() {
      closed = true;
      if (waiting) waiting();
    },
  };
}

/**
 * AgentSDKTransport — wraps a single Claude Code session.
 *
 * Lifecycle:
 *   const t = new AgentSDKTransport();
 *   await t.spawn({ cwd, systemPrompt, permissionMode, ... });
 *   // events start flowing
 *   await t.send("do something");
 *   await t.interrupt();
 *   t.close();
 *
 * Events emitted:
 *   'message'  (SDKMessage)      — every SDK message (for WS forwarding)
 *   'init'     ({ session_id, model, tools, permissionMode })
 *   'stream'   (BetaRawMessageStreamEvent, { parent_tool_use_id, session_id })
 *   'result'   (SDKResultMessage)
 *   'status'   ({ status, permissionMode })
 *   'error'    (Error)
 *   'close'    ()
 *   'assistant' (SDKAssistantMessage)
 *   'user'     (SDKUserMessage)
 */
export class AgentSDKTransport extends EventEmitter {
  constructor() {
    super();
    /** @type {import('@anthropic-ai/claude-agent-sdk').Query | null} */
    this._query = null;
    this.sessionId = null;
    this.status = 'closed'; // closed | spawning | idle | streaming
    this._abortController = null;
    this._consumePromise = null;
    this._streaming = false;
    this._channel = null;
  }

  /**
   * Spawn a new Claude Code session.
   * @param {object} options
   * @param {string} options.cwd — working directory for the session
   * @param {string} [options.systemPrompt] — appended to Claude Code's system prompt
   * @param {string} [options.permissionMode] — default: bypassPermissions
   * @param {string} [options.model] — model ID override
   * @param {object} [options.env] — environment variables for subprocess
   * @param {string[]} [options.allowedTools] — tool allow-list
   * @param {string[]} [options.disallowedTools] — tool deny-list
   * @param {string} [options.resume] — session ID to resume
   * @param {string} [options.initialPrompt] — first message (default: ack prompt)
   */
  async spawn(options) {
    if (this.status !== 'closed') {
      throw new Error(`Cannot spawn: transport is ${this.status}`);
    }

    this.status = 'spawning';
    const queryFn = await getQuery();

    this._abortController = new AbortController();

    // Claude CLI path: env override first (e.g. phone/proot via CLAUDE_CLI_PATH), else the
    // npm global location on Windows, else undefined → SDK uses its own bundled cli.js.
    // ponytail: env-driven, no install-specific path baked in.
    const claudeCliPath = process.env.CLAUDE_CLI_PATH
      || (process.platform === 'win32' && process.env.APPDATA
          ? path.join(process.env.APPDATA, 'npm', 'node_modules',
                      '@anthropic-ai', 'claude-code', 'cli.js')
          : undefined);

    const sdkOptions = {
      cwd: options.cwd,
      includePartialMessages: true,
      persistSession: true,
      permissionMode: options.permissionMode || 'bypassPermissions',
      allowDangerouslySkipPermissions: true,
      abortController: this._abortController,
    };
    if (claudeCliPath) sdkOptions.pathToClaudeCodeExecutable = claudeCliPath;

    if (options.systemPrompt) {
      sdkOptions.systemPrompt = {
        type: 'preset',
        preset: 'claude_code',
        append: options.systemPrompt,
      };
    }
    if (options.model) sdkOptions.model = options.model;
    if (options.allowedTools) sdkOptions.allowedTools = options.allowedTools;
    if (options.disallowedTools) sdkOptions.disallowedTools = options.disallowedTools;
    if (options.resume) {
      sdkOptions.resume = options.resume;
      // Fork session: create a new session ID so the daemon doesn't write
      // to the same JSONL file as a terminal instance using this session.
      sdkOptions.forkSession = true;
    }

    // Build child env: inherit process.env, strip CLAUDECODE to prevent
    // nested-session detection, then merge any caller-provided env overrides
    const childEnv = { ...process.env };
    delete childEnv.CLAUDECODE;
    if (options.env) Object.assign(childEnv, options.env);
    sdkOptions.env = childEnv;

    // Log stderr from Claude subprocess
    sdkOptions.stderr = (data) => {
      const s = data.toString().trim();
      if (s) console.error('[transport:stderr]', s);
    };

    // Create push-based message channel for multi-turn support.
    // Passing an AsyncIterable (not a string) to query() keeps stdin
    // open so the Claude process stays alive between turns.
    this._channel = createMessageChannel();

    const initialText = options.initialPrompt
      || 'You are ready. Acknowledge briefly and wait for instructions.';

    this._channel.push({
      type: 'user',
      message: { role: 'user', content: initialText },
      parent_tool_use_id: null,
      session_id: '',
    });

    this._query = queryFn({ prompt: this._channel, options: sdkOptions });

    // Start consuming messages in the background
    this._consumePromise = this._consumeMessages();
  }

  /**
   * Send a follow-up message to the active session.
   * @param {string} message — user message text
   * @param {string} [deltaPrefix] — brain delta to prepend
   */
  async send(message, deltaPrefix) {
    if (!this._query) throw new Error('Transport not spawned');
    if (this._streaming) throw new Error('Already streaming — wait for idle');

    let text = message;
    if (deltaPrefix) {
      text = `<brain-delta>\n${deltaPrefix}\n</brain-delta>\n\n${message}`;
    }

    this._channel.push({
      type: 'user',
      message: { role: 'user', content: text },
      parent_tool_use_id: null,
      session_id: this.sessionId || '',
    });
  }

  /**
   * Interrupt the current generation.
   */
  async interrupt() {
    if (!this._query) return;
    try {
      await this._query.interrupt();
    } catch (_) { /* ignore if already idle */ }
  }

  /**
   * Kill the subprocess and clean up.
   */
  close() {
    if (this.status === 'closed') return;
    this.status = 'closed';
    this._streaming = false;

    if (this._channel) {
      this._channel.close();
      this._channel = null;
    }
    if (this._query) {
      try { this._query.close(); } catch (_) {}
      this._query = null;
    }
    if (this._abortController) {
      try { this._abortController.abort(); } catch (_) {}
      this._abortController = null;
    }
    this.emit('close');
  }

  /**
   * Background consumer — reads all messages from the Query async generator,
   * classifies them, and emits typed events.
   * @private
   */
  async _consumeMessages() {
    try {
      for await (const msg of this._query) {
        if (this.status === 'closed') break;

        // Raw passthrough for WebSocket forwarding
        this.emit('message', msg);

        switch (msg.type) {
          case 'system':
            if (msg.subtype === 'init') {
              this.sessionId = msg.session_id;
              this.status = 'idle';
              this._streaming = false;
              this.emit('init', {
                session_id: msg.session_id,
                model: msg.model,
                tools: msg.tools,
                permissionMode: msg.permissionMode,
                cwd: msg.cwd,
                claude_code_version: msg.claude_code_version,
              });
            } else if (msg.subtype === 'status') {
              this.emit('status', {
                status: msg.status,
                permissionMode: msg.permissionMode,
              });
              if (msg.status === 'compacting') {
                this.status = 'streaming';
                this._streaming = true;
              }
            }
            break;

          case 'stream_event':
            if (!this._streaming) {
              this._streaming = true;
              this.status = 'streaming';
            }
            this.emit('stream', msg.event, {
              parent_tool_use_id: msg.parent_tool_use_id,
              session_id: msg.session_id,
              uuid: msg.uuid,
            });
            break;

          case 'assistant':
            this.emit('assistant', msg);
            break;

          case 'result':
            this._streaming = false;
            this.status = 'idle';
            this.emit('result', msg);
            break;

          case 'user':
            this.emit('user', msg);
            break;

          default:
            this.emit('sdk_event', msg);
            break;
        }
      }
    } catch (err) {
      if (this.status !== 'closed') {
        this.emit('error', err);
      }
    } finally {
      if (this.status !== 'closed') {
        this.status = 'closed';
        this._streaming = false;
        this.emit('close');
      }
    }
  }
}
