// pi-acp-bridge —— pi 官方 SDK → ACP stdio 桥（Kite 矩阵用）
// 设计边界：只做协议翻译，不做策略。pi 侧全部走官方 API（createAgentSession/SessionManager）。
// ACP 侧按 Agent Client Protocol（Kite 消费 com.agentclientprotocol:acp:0.26.0 形状）。
//
// 环境变量：
//   ZHIPU_API_KEY   —— zai-coding-cn 供应商 key（后备；优先读 /root/.pi/agent/auth.json）
//   PI_ACP_MODEL    —— 默认 zai-coding-cn/glm-5.3
//   PI_ACP_PROVIDERS —— 模型目录只列这些供应商（逗号分隔，默认 zai-coding-cn）
//   PI_ACP_DEBUG    —— 1 时向 stderr 打调试行

import { randomUUID } from 'node:crypto';
import {
  createAgentSession,
  DefaultResourceLoader,
  getAgentDir,
  ModelRuntime,
  SessionManager,
} from '@earendil-works/pi-coding-agent';

const DEBUG = process.env.PI_ACP_DEBUG === '1';
const debug = (...a) => { if (DEBUG) process.stderr.write('[pi-acp] ' + a.join(' ') + '\n'); };

// ---------- stdio JSON-RPC ----------
let buf = '';
const pending = new Map(); // id -> resolve/reject
const serverRequests = new Map(); // id -> handler（来自客户端的请求）
let nextServerId = 1;

function sendMessage(msg) {
  process.stdout.write(JSON.stringify(msg) + '\n');
}
function reply(id, result) { sendMessage({ jsonrpc: '2.0', id, result }); }
function replyError(id, code, message) { sendMessage({ jsonrpc: '2.0', id, error: { code, message } }); }
function notify(method, params) { sendMessage({ jsonrpc: '2.0', method, params }); }
function request(method, params) {
  const id = nextServerId++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    sendMessage({ jsonrpc: '2.0', id, method, params });
  });
}

// ---------- ACP update 辅助 ----------
function sessionUpdate(sessionId, update) {
  notify('session/update', { sessionId, update });
}

// ---------- pi 会话管理 ----------
const sessions = new Map(); // sessionId -> { pi, unsubscribe, file, cwd }
const modelRuntimeP = ModelRuntime.create({ allowModelNetwork: false });

async function resolveModel() {
  const runtime = await modelRuntimeP;
  const key = process.env.ZHIPU_API_KEY;
  if (key) await runtime.setRuntimeApiKey('zai-coding-cn', key);
  const wanted = process.env.PI_ACP_MODEL ?? 'zai-coding-cn/glm-5.3';
  const [providerId, ...rest] = wanted.split('/');
  const modelId = rest.join('/') || 'glm-5.3';
  const model = runtime.getModel(providerId, modelId);
  if (!model) throw new Error(`model not found: ${wanted}`);
  return { runtime, model };
}

async function newPiSession(cwd, sessionManager) {
  const { runtime, model } = await resolveModel();
  const loader = new DefaultResourceLoader({ cwd, agentDir: getAgentDir() });
  await loader.reload();
  const result = await createAgentSession({
    cwd,
    model,
    modelRuntime: runtime,
    sessionManager,
    resourceLoader: loader,
  });
  return { result, loader };
}

/** 把 pi 流事件转成 ACP update 并推送。 */
function wireSession(sessionId, pi) {
  let assistantMessageId = null;
  const nextMessageId = () => `pi_${randomUUID()}`;
  const unsubscribe = pi.subscribe((event) => {
    try {
      switch (event.type) {
        case 'message_start':
          assistantMessageId = nextMessageId();
          break;
        case 'message_update': {
          const e = event.assistantMessageEvent;
          if (e.type === 'text_delta' && e.delta) {
            sessionUpdate(sessionId, {
              sessionUpdate: 'agent_message_chunk',
              content: { type: 'text', text: e.delta },
              messageId: assistantMessageId ?? undefined,
            });
          } else if (e.type === 'thinking_delta' && e.delta) {
            sessionUpdate(sessionId, {
              sessionUpdate: 'agent_thought_chunk',
              content: { type: 'text', text: e.delta },
              messageId: assistantMessageId ?? undefined,
            });
          }
          break;
        }
        case 'tool_execution_start': {
          debug('tool_start keys:', Object.keys(event).join(','));
          sessionUpdate(sessionId, {
            sessionUpdate: 'tool_call',
            toolCallId: event.toolCallId ?? `tc_${randomUUID()}`,
            title: event.toolName ?? 'tool',
            kind: { _tag: 'execute' },
            content: [],
          });
          break;
        }
        case 'tool_execution_end': {
          const raw = safeText(event.result);
          sessionUpdate(sessionId, {
            sessionUpdate: 'tool_call_update',
            toolCallId: event.toolCallId ?? '',
            content: raw ? [{ type: 'text', text: raw.slice(0, 2000) }] : [],
            status: { _tag: event.isError ? 'failed' : 'completed' },
          });
          break;
        }
        default:
          debug('event', event.type);
      }
    } catch (err) {
      debug('event map error:', err?.message);
    }
  });
  return unsubscribe;
}

function safeText(value) {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  try { return JSON.stringify(value); } catch { return String(value); }
}

/** 回放 pi 消息历史 → ACP chunks。 */
function replayMessages(sessionId, messages) {
  for (const message of messages ?? []) {
    const blocks = Array.isArray(message?.content) ? message.content : [];
    const role = message?.role;
    for (const block of blocks) {
      if (block?.type === 'text' && block.text) {
        sessionUpdate(sessionId, {
          sessionUpdate: role === 'user' ? 'user_message_chunk' : 'agent_message_chunk',
          content: { type: 'text', text: block.text },
          messageId: undefined,
        });
      } else if (block?.type === 'thinking' && block.thinking) {
        sessionUpdate(sessionId, {
          sessionUpdate: 'agent_thought_chunk',
          content: { type: 'text', text: block.thinking },
          messageId: undefined,
        });
      }
    }
  }
}

function emitCommands(sessionId, loader) {
  const prompts = loader?.getPrompts?.() ?? [];
  if (!prompts.length) return;
  sessionUpdate(sessionId, {
    sessionUpdate: 'available_commands_update',
    availableCommands: prompts.map((p) => ({
      name: p.name,
      description: p.description ?? '',
      input: null,
    })),
  });
}

// ---------- 模型选择（session/set_model，UNSTABLE 但广泛实现） ----------
// 只列配置了 key 的供应商（PI_ACP_PROVIDERS，默认 zai-coding-cn），避免全量 1300+ 模型涌入 UI。
const configuredProviders = () =>
  (process.env.PI_ACP_PROVIDERS ?? 'zai-coding-cn').split(',').map((s) => s.trim()).filter(Boolean);

async function modelState(entry) {
  const runtime = await modelRuntimeP;
  const models = [];
  for (const pid of configuredProviders()) {
    for (const m of runtime.getModels(pid)) {
      models.push({
        modelId: `${pid}/${m.id}`,
        name: m.name ?? m.id,
        // 推理强度是模型参数：thinkingLevelMap 声明该模型支持的档位（low/high/max…，null=不支持）
        _meta: m.reasoning && m.thinkingLevelMap
          ? { thinkingLevels: Object.entries(m.thinkingLevelMap).filter(([, v]) => v).map(([k]) => k) }
          : undefined,
      });
    }
  }
  const current = entry?.pi?.model;
  const currentId = current ? modelIdOf(current) : (process.env.PI_ACP_MODEL ?? 'zai-coding-cn/glm-5.3');
  return { currentModelId: currentId, availableModels: models };
}

function modelIdOf(model) {
  const pid = typeof model?.provider === 'string' ? model.provider : model?.provider?.id;
  return pid ? `${pid}/${model.id}` : String(model?.id ?? model);
}

/** 在 pi 会话目录里按 sessionId 找会话文件（跨进程恢复）。 */
async function findSessionManager(sessionId, cwd) {
  try {
    // 注意：listAll() 的首参是会话目录而非 cwd；按项目列用 list(cwd)。SessionInfo: {id,path,...}
    const list = await SessionManager.list(cwd);
    debug('list count:', list?.length);
    for (const item of list ?? []) {
      const file = item?.path ?? item?.file ?? '';
      if (item?.id === sessionId || String(file).includes(sessionId)) {
        debug('matched session file:', file);
        return SessionManager.open(file);
      }
    }
  } catch (err) {
    debug('findSession error:', err?.message);
  }
  return null;
}

// ---------- ACP 方法实现 ----------
const methods = {
  async initialize() {
    return {
      protocolVersion: 1,
      agentCapabilities: {
        loadSession: true,
        promptCapabilities: { image: false },
        changeMode: false,
        sessionCapabilities: { list: {}, resume: {} },
      },
      agentInfo: { name: 'pi-acp-bridge', title: 'Pi (Kite bridge)', version: '0.2.0' },
      authMethods: [],
    };
  },

  async 'session/new'(params) {
    const cwd = params?.cwd ?? process.cwd();
    const sm = SessionManager.create(cwd);
    const { result, loader } = await newPiSession(cwd, sm);
    const pi = result.session;
    const sessionId = pi.sessionId ?? `pi_${randomUUID()}`;
    sessions.set(sessionId, { pi, unsubscribe: wireSession(sessionId, pi), file: pi.sessionFile ?? null, cwd });
    emitCommands(sessionId, loader);
    debug('session/new', sessionId, 'file:', pi.sessionFile);
    return { sessionId, models: await modelState(sessions.get(sessionId)) };
  },

  async 'session/load'(params) {
    const sessionId = params?.sessionId;
    const cwd = params?.cwd ?? process.cwd();
    const known = sessions.get(sessionId);
    const sm = known?.file
      ? SessionManager.open(known.file)
      : await findSessionManager(sessionId, cwd);
    if (!sm) throw new Error(`session not found: ${sessionId}`);
    const { result, loader } = await newPiSession(cwd, sm);
    const pi = result.session;
    const resolvedId = pi.sessionId ?? sessionId;
    sessions.set(resolvedId, { pi, unsubscribe: wireSession(resolvedId, pi), file: pi.sessionFile ?? null, cwd });
    emitCommands(resolvedId, loader);
    replayMessages(resolvedId, pi.agent?.state?.messages);
    return { sessionId: resolvedId, models: await modelState(sessions.get(resolvedId)) };
  },

  async 'session/list'(params) {
    const cwd = params?.cwd ?? process.cwd();
    let list = [];
    try { list = await SessionManager.list(cwd); } catch (err) { debug('list error:', err?.message); }
    const items = (list ?? []).map((it) => ({
      sessionId: it.id,
      cwd: it.cwd ?? cwd,
      title: it.title ?? it.firstMessage?.slice(0, 60) ?? undefined,
      updatedAt: it.modified ?? it.lastModified ?? undefined,
    }));
    // Kotlin SDK（com.agentclientprotocol:acp）字段为 sessions/nextCursor；新版 TS schema 用 itemsBatch。
    return { sessions: items };
  },

  async 'session/resume'(params) {
    return methods['session/load'](params);
  },

  async 'session/set_model'(params) {
    const entry = sessions.get(params?.sessionId);
    if (!entry) throw new Error('unknown session');
    const runtime = await modelRuntimeP;
    const wanted = String(params?.modelId ?? '');
    const idx = wanted.indexOf('/');
    const providerId = idx > 0 ? wanted.slice(0, idx) : 'zai-coding-cn';
    const modelId = idx > 0 ? wanted.slice(idx + 1) : wanted;
    const model = runtime.getModel(providerId, modelId);
    if (!model) throw new Error(`model not found: ${wanted}`);
    await entry.pi.setModel(model);
    debug('set_model', wanted);
    return {};
  },

  async 'session/prompt'(params) {
    const entry = sessions.get(params?.sessionId);
    if (!entry) throw new Error('unknown session');
    const { pi } = entry;
    const text = (params?.prompt ?? []).filter((b) => b?.type === 'text').map((b) => b.text).join('');
    if (text) {
      sessionUpdate(params.sessionId, {
        sessionUpdate: 'user_message_chunk',
        content: { type: 'text', text },
        messageId: undefined,
      });
    }
    await pi.prompt(text);
    return { stopReason: 'end_turn' };
  },

  async 'session/cancel'(params) {
    const entry = sessions.get(params?.sessionId);
    if (entry) await entry.pi.abort();
    return {};
  },
};

// ---------- 主循环 ----------
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  buf += chunk;
  let idx;
  while ((idx = buf.indexOf('\n')) >= 0) {
    const line = buf.slice(0, idx).trim();
    buf = buf.slice(idx + 1);
    if (!line) continue;
    let msg;
    try { msg = JSON.parse(line); } catch { debug('badline:', line.slice(0, 120)); continue; }
    handle(msg).catch((err) => debug('handler error:', err?.stack ?? err?.message));
  }
});

async function handle(msg) {
  if (msg.method && msg.id !== undefined) {
    const handler = methods[msg.method];
    if (!handler) {
      replyError(msg.id, -32601, `method not found: ${msg.method}`);
      return;
    }
    try {
      reply(msg.id, await handler(msg.params ?? {}));
    } catch (err) {
      replyError(msg.id, -32000, String(err?.message ?? err));
    }
    return;
  }
  if (msg.method) {
    // 通知（暂不处理）
    debug('notify:', msg.method);
    return;
  }
  if (msg.id !== undefined && pending.has(msg.id)) {
    const p = pending.get(msg.id);
    pending.delete(msg.id);
    if (msg.error) p.reject(new Error(JSON.stringify(msg.error)));
    else p.resolve(msg.result);
  }
}

process.stdin.on('end', () => {
  debug('stdin end, exiting');
  process.exit(0);
});
