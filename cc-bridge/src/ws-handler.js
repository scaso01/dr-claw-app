// cc-bridge/src/ws-handler.js — WebSocket message handler
// Routes client req frames to SessionManager methods.

const crypto = require('crypto');

function createWsHandler({ sessionManager, token }) {
  return function handleConnection(ws) {
    let authenticated = false;
    const challenge = crypto.randomBytes(16).toString('hex');

    // Send auth challenge
    ws.send(JSON.stringify({
      type: 'event',
      event: 'auth.challenge',
      data: { challenge },
    }));

    // Auto-close if not authenticated within 10s
    const authTimer = setTimeout(() => {
      if (!authenticated) {
        ws.close(4001, 'Auth timeout');
      }
    }, 10000);

    ws.on('message', async (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch (_) {
        ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }));
        return;
      }

      if (msg.type !== 'req') return;

      // Auth must be first message
      if (!authenticated) {
        if (msg.method === 'auth') {
          clearTimeout(authTimer);
          if (msg.params && msg.params.token === token) {
            authenticated = true;
            sendRes(ws, msg.id, true, { message: 'Authenticated' });
          } else {
            sendRes(ws, msg.id, false, { error: 'Invalid token' });
            ws.close(4003, 'Invalid token');
          }
        } else {
          sendRes(ws, msg.id, false, { error: 'Not authenticated' });
        }
        return;
      }

      // Route authenticated requests
      try {
        const result = await routeRequest(sessionManager, msg, ws);
        sendRes(ws, msg.id, true, result);
      } catch (err) {
        sendRes(ws, msg.id, false, { error: err.message });
      }
    });

    ws.on('close', () => {
      clearTimeout(authTimer);
      // Detach this ws from all sessions it was attached to
      for (const session of sessionManager.sessions.values()) {
        if (session.clients.has(ws)) {
          sessionManager.detach(session.id, ws);
        }
      }
    });

    ws.on('error', () => {
      clearTimeout(authTimer);
    });
  };
}

async function routeRequest(sm, msg, ws) {
  const { method, params } = msg;
  const p = params || {};

  switch (method) {
    case 'sessions.list':
      return { sessions: sm.list() };

    case 'session.create': {
      if (!p.cwd) throw new Error('cwd is required');
      const session = await sm.create(p);
      return {
        id: session.id,
        status: session.status,
        config: session.config,
        sdkSessionId: session.sdkSessionId,
      };
    }

    case 'session.resume': {
      if (!p.sessionId) throw new Error('sessionId is required');
      if (!p.cwd) throw new Error('cwd is required');
      const session = await sm.create({
        cwd: p.cwd,
        project: p.project,
        backend: p.backend,
        model: p.model,
        permissionMode: p.permissionMode,
        resume: p.sessionId,
        initialPrompt: 'Session resumed. Continue where you left off.',
      });
      return {
        id: session.id,
        status: session.status,
        config: session.config,
        sdkSessionId: session.sdkSessionId,
        resumedFrom: p.sessionId,
      };
    }

    case 'session.attach': {
      if (!p.id) throw new Error('session id required');
      sm.attach(p.id, ws);
      const session = sm.get(p.id);
      return {
        id: p.id,
        status: session ? session.transport.status : 'unknown',
        sdkSessionId: session ? session.sdkSessionId : null,
        config: session ? session.config : null,
      };
    }

    case 'session.detach': {
      if (!p.id) throw new Error('session id required');
      sm.detach(p.id, ws);
      return { id: p.id, detached: true };
    }

    case 'session.send': {
      if (!p.id) throw new Error('session id required');
      if (!p.message) throw new Error('message is required');
      await sm.send(p.id, p.message, p.deltaPrefix);
      return { id: p.id, sent: true };
    }

    case 'session.interrupt': {
      if (!p.id) throw new Error('session id required');
      await sm.interrupt(p.id);
      return { id: p.id, interrupted: true };
    }

    case 'session.destroy': {
      if (!p.id) throw new Error('session id required');
      sm.destroy(p.id, p.reason || 'client_request');
      return { id: p.id, destroyed: true };
    }

    case 'models.local.list': {
      try {
        const resp = await fetch('http://localhost:8080/v1/models');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json();
        return { models: data.data || [] };
      } catch (err) {
        return { models: [], error: err.message };
      }
    }

    default:
      throw new Error('Unknown method: ' + method);
  }
}

function sendRes(ws, id, ok, payload) {
  if (ws.readyState === 1) { // WebSocket.OPEN
    ws.send(JSON.stringify({ type: 'res', id: id, ok: ok, payload: payload }));
  }
}

module.exports = { createWsHandler };
