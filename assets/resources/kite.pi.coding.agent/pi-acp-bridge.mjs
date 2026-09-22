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
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
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
const bootT0 = Date.now();
// 启动阶段耗时日志：始终输出到 stderr（Kite 采集后可在诊断中看到；量极小）。
function phase(msg) {
  process.stderr.write(`[pi-bridge +${Date.now() - bootT0}ms] ${msg}\n`);
}
phase(`boot pid=${process.pid} node=${process.version} cwd=${process.cwd()}`);
const modelRuntimeP = ModelRuntime.create({ allowModelNetwork: false }).then(
  (rt) => { phase('ModelRuntime.create ok'); return rt; },
  (err) => { phase(`ModelRuntime.create FAILED: ${err?.message ?? err}`); throw err; },
);

async function resolveModel() {
  const runtime = await modelRuntimeP;
  const key = process.env.ZHIPU_API_KEY;
  if (key) await runtime.setRuntimeApiKey('zai-coding-cn', key);
  const wanted = process.env.PI_ACP_MODEL ?? defaultModelFromConfig();
  const [providerId, ...rest] = wanted.split('/');
  const modelId = rest.join('/') || 'glm-5.3';
  const model = runtime.getModel(providerId, modelId);
  if (!model) throw new Error(`model not found: ${wanted}`);
  return { runtime, model };
}

// 默认模型解析（对齐 CC Switch 的 Pi 原生合同，不碰 auth.json）：
// 1) pi 全局 settings.json 的 defaultProvider/defaultModel；
// 2) models.json.providers 里第一个带 apiKey 且有模型的显式节点（Kite 配置适配器写入）；
// 3) 内置 zai-coding-cn/glm-5.3。
function defaultModelFromConfig() {
  const agentDir = getAgentDir();
  try {
    const settings = readJsonFileSync(join(agentDir, 'settings.json'));
    if (settings?.defaultProvider && settings?.defaultModel) {
      return `${settings.defaultProvider}/${settings.defaultModel}`;
    }
  } catch { /* 缺省文件是正常状态 */ }
  try {
    const models = readJsonFileSync(join(agentDir, 'models.json'));
    const providers = models?.providers ?? {};
    for (const [pid, node] of Object.entries(providers)) {
      const apiKey = typeof node === 'object' && node ? node.apiKey : '';
      const first = Array.isArray(node?.models) && node.models[0]?.id;
      if (apiKey && first) return `${pid}/${first}`;
    }
  } catch { /* 缺省文件是正常状态 */ }
  return 'zai-coding-cn/glm-5.3';
}

function readJsonFileSync(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

async function newPiSession(cwd, sessionManager) {
  const t0 = Date.now();
  const { runtime, model } = await resolveModel();
  phase(`resolveModel ok (${Date.now() - t0}ms): ${model ? `${model.providerId}/${model.id}` : 'null'}`);
  const t1 = Date.now();
  const loader = new DefaultResourceLoader({ cwd, agentDir: getAgentDir() });
  await loader.reload();
  phase(`resourceLoader.reload ok (${Date.now() - t1}ms)`);
  const t2 = Date.now();
  const result = await createAgentSession({
    cwd,
    model,
    modelRuntime: runtime,
    sessionManager,
    resourceLoader: loader,
  });
  phase(`createAgentSession ok (${Date.now() - t2}ms)`);
  return { result, loader };
}

/** 把 pi 流事件转成 ACP update 并推送。 */
function wireSession(sessionId, pi) {
  let assistantMessageId = null;
  const nextMessageId = () => `pi_${randomUUID()}`;
  const unsubscribe = pi.subscribe((event) => {
    try {
      if (event?.type === 'error') {
        phase(`pi error event: ${JSON.stringify(event).slice(0, 300)}`);
      }
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

// ---------- 斜杠命令（模式抄自 pi-web-ui server/slash-commands.ts）----------
// NATIVE_COMMANDS：桥端原生实现的命令（pi CLI 的交互式内置命令不经 SDK prompt()，
// 不拦截会被当普通文本发给模型）。exec() 与此清单保持同步。
const NATIVE_COMMANDS = [
  { name: 'compact', description: '压缩上下文', argumentHint: '[说明]' },
  { name: 'model', description: '查看或切换模型', argumentHint: '[provider/model 或 model]' },
  { name: 'thinking', description: '设置思考强度', argumentHint: '<off|minimal|low|medium|high|xhigh|max>' },
  { name: 'name', description: '重命名当前会话', argumentHint: '<名称>' },
  { name: 'reload', description: '重新加载扩展、技能与模板' },
  { name: 'help', description: '显示全部命令' },
];

function collectSlashCommands(pi) {
  // 目录 = 桥端原生 + SDK 会话可触发的命令（extension/prompt/skill），与
  // pi-web-ui SlashCommandsService.push() 同源：AgentSession.prompt() 的展开路径。
  const commands = [];
  const seen = new Set();
  for (const c of NATIVE_COMMANDS) {
    commands.push({ name: c.name, description: c.description, input: c.argumentHint ? { hint: c.argumentHint } : null });
    seen.add(c.name);
  }
  try {
    const runner = pi?.extensionRunnerRef?.runner ?? pi?.extensionRunner;
    for (const cmd of runner?.getRegisteredCommands?.() ?? []) {
      const name = cmd.invocationName ?? cmd.name;
      if (!name || seen.has(name)) continue;
      commands.push({ name, description: cmd.description ?? '', input: null });
      seen.add(name);
    }
  } catch { /* extension runner 未就绪：目录仍可用 */ }
  try {
    for (const t of pi?.promptTemplates ?? []) {
      if (seen.has(t.name)) continue;
      commands.push({ name: t.name, description: t.description ?? '', input: null });
      seen.add(t.name);
    }
  } catch { /* 同上 */ }
  try {
    for (const s of pi?.resourceLoader?.getSkills?.()?.skills ?? []) {
      if (s.disableModelInvocation) continue;
      const name = `skill:${s.name}`;
      if (seen.has(name)) continue;
      commands.push({ name, description: s.description ?? '', input: null });
      seen.add(name);
    }
  } catch { /* 同上 */ }
  return commands;
}

function emitCommands(sessionId, pi) {
  const commands = collectSlashCommands(pi);
  if (!commands.length) return;
  sessionUpdate(sessionId, {
    sessionUpdate: 'available_commands_update',
    availableCommands: commands,
  });
}

function parseSlash(text) {
  const trimmed = (text ?? '').trim();
  if (!trimmed.startsWith('/')) return null;
  const m = trimmed.match(/^\/([^\s]+)\s*([\s\S]*)$/);
  if (!m || !m[1]) return null;
  return { name: m[1], args: m[2].trim() };
}

/** 桥端原生命令执行：返回 true 表示已拦截（不再透传 SDK）。结果以 agent 消息回显。 */
async function execNativeCommand(sessionId, entry, name, args) {
  const { pi } = entry;
  const reply = (text) => {
    sessionUpdate(sessionId, { sessionUpdate: 'agent_message_chunk', content: { type: 'text', text } });
  };
  if (name === 'compact') {
    reply(args ? `正在压缩上下文（说明：${args}）…` : '正在压缩上下文…');
    try {
      await pi.compact(args || undefined);
      reply('上下文压缩完成。');
    } catch (err) {
      reply(`压缩失败：${err?.message ?? err}`);
    }
    return true;
  }
  if (name === 'model') {
    try {
      const runtime = await modelRuntimeP;
      const all = [];
      for (const pid of configuredProviders()) {
        for (const m of runtime.getModels(pid)) all.push({ pid, m, modelId: `${pid}/${m.id}` });
      }
      if (!args) {
        const current = pi.model;
        reply(`当前模型：${current ? `${current.providerId}/${current.id}` : '未知'}\n可用：\n` +
          all.map((x) => `- ${x.modelId}${x.m.name ? `（${x.m.name}）` : ''}`).join('\n'));
        return true;
      }
      const hit = all.find((x) => x.modelId === args) ?? all.find((x) => x.m.id === args);
      if (!hit) { reply(`未找到模型 ${args}。用 /model 查看可用列表。`); return true; }
      await pi.setModel(hit.m);
      reply(`已切换模型：${hit.modelId}`);
    } catch (err) {
      reply(`切换模型失败：${err?.message ?? err}`);
    }
    return true;
  }
  if (name === 'thinking') {
    const levels = ['off', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'];
    if (!levels.includes(args)) {
      reply(`用法：/thinking <${levels.join('|')}>`);
      return true;
    }
    pi.setThinkingLevel(args);
    reply(`思考强度已设为 ${args}。`);
    return true;
  }
  if (name === 'name') {
    if (!args) { reply('用法：/name <名称>'); return true; }
    pi.setSessionName(args);
    reply(`会话已重命名为：${args}`);
    return true;
  }
  if (name === 'reload') {
    reply('正在重新加载扩展、技能与模板…');
    try {
      await pi.reload?.();
      emitCommands(sessionId, pi);
      reply('重新加载完成，命令清单已更新。');
    } catch (err) {
      reply(`重新加载失败：${err?.message ?? err}`);
    }
    return true;
  }
  if (name === 'help') {
    reply('可用命令：\n' + collectSlashCommands(pi).map((c) => `/${c.name}${c.input?.hint ? ' ' + c.input.hint : ''} — ${c.description}`).join('\n'));
    return true;
  }
  return false;
}

// ---------- 模型选择（session/set_model，UNSTABLE 但广泛实现） ----------
// 列出：PI_ACP_PROVIDERS 指定的内置供应商 + models.json 里带 key 的自定义供应商（Kite 配置写入）。
// 否则 Kite 模型库默认模型（自定义供应商的）无法映射到会话模型选择，发送前会被拦下。
const configuredProviders = () => {
  const env = (process.env.PI_ACP_PROVIDERS ?? 'zai-coding-cn')
    .split(',').map((s) => s.trim()).filter(Boolean);
  try {
    const models = readJsonFileSync(join(getAgentDir(), 'models.json'));
    for (const [pid, node] of Object.entries(models?.providers ?? {})) {
      const hasKey = typeof node === 'object' && node ? Boolean(node.apiKey) : false;
      if (hasKey && !env.includes(pid)) env.push(pid);
    }
  } catch { /* 缺省文件是正常状态 */ }
  return env;
};

async function modelState(entry) {
  const t0 = Date.now();
  const runtime = await modelRuntimeP;
  const models = [];
  for (const pid of configuredProviders()) {
    const tp = Date.now();
    let list;
    try {
      list = runtime.getModels(pid);
    } catch (err) {
      phase(`modelState getModels(${pid}) THREW after ${Date.now() - tp}ms: ${err?.message ?? err}`);
      throw err;
    }
    phase(`modelState getModels(${pid}) -> ${list.length} models (${Date.now() - tp}ms)`);
    for (const m of list) {
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
  phase(`modelState done (${Date.now() - t0}ms): ${models.length} models, current=${currentId}`);
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
    phase('initialize requested');
    return {
      protocolVersion: 1,
      agentCapabilities: {
        loadSession: true,
        promptCapabilities: { image: false },
        changeMode: false,
        sessionCapabilities: { list: {}, resume: {} },
      },
      agentInfo: { name: 'pi-acp-bridge', title: 'Pi (Kite bridge)', version: '0.2.4' },
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
    emitCommands(sessionId, pi);
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
    emitCommands(resolvedId, pi);
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
    phase(`prompt received: ${text.slice(0, 40)}`);
    if (text) {
      sessionUpdate(params.sessionId, {
        sessionUpdate: 'user_message_chunk',
        content: { type: 'text', text },
        messageId: undefined,
      });
    }
    // 桥端原生命令拦截（与 NATIVE_COMMANDS 同步）：命中则本地执行，不透传 SDK。
    const slash = parseSlash(text);
    if (slash && NATIVE_COMMANDS.some((c) => c.name === slash.name)) {
      phase(`native command: /${slash.name}`);
      try {
        await execNativeCommand(params.sessionId, entry, slash.name, slash.args);
      } catch (err) {
        phase(`native command FAILED: ${err?.message ?? err}`);
        throw err;
      }
      return { stopReason: 'end_turn' };
    }
    try {
      await pi.prompt(text);
      phase('prompt completed');
    } catch (err) {
      phase(`prompt FAILED: ${err?.message ?? err}`);
      throw err;
    }
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
let sawFirstLine = false;
process.stdin.on('data', (chunk) => {
  if (!sawFirstLine) { sawFirstLine = true; phase('first stdin line received'); }
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
