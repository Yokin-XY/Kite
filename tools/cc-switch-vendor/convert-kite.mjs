/**
 * CC Switch 预设 → Kite 供应商目录转换器（v2，产出 Kite 形状）。
 *
 * 输入：out/{app}-presets.json（convert-all.mjs 的 CC Switch 原生形状产物）
 * 输出：
 *   - out/kite-catalog.json                 （Kite 形状，供检查与测试夹具）
 *   - app/src/main/java/.../CcSwitchCatalogBundle.kt（内嵌同一份 JSON 的 Kotlin bundle）
 *
 * 上游：farion1231/cc-switch (MIT)。重新拉取上游后先跑 convert-all.mjs 再跑本脚本。
 */
import { writeFileSync, readFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(here, '..', '..');

// CC Switch app → Kite adapterId（Kite 无对应 Agent 的 app 不在此表，数据仍在 JSON 里）
const ADAPTER_BY_APP = {
  claude: 'claude-code',
  codex: 'codex',
  gemini: 'gemini-cli',
  opencode: 'opencode',
  openclaw: 'openclaw',
  hermes: 'hermes',
  pi: 'pi-coding-agent',
};

const CATEGORY_MAP = {
  official: 'Official',
  cn_official: 'ChinaOfficial',
  cloud_provider: 'CloudProvider',
  aggregator: 'Aggregator',
  third_party: 'ThirdParty',
  custom: 'Custom',
};

const MARKET_BY_CATEGORY = {
  official: 'Global',
  cn_official: 'China',
};

const slugify = (name) =>
  name.trim().toLowerCase()
    .replace(/[（）()【】\[\]{}]/g, ' ')
    .replace(/[^a-z0-9\u4e00-\u9fff]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/-{2,}/g, '-');

const accessChannelOf = (name, isOfficial) => {
  const n = name.toLowerCase();
  if (/coding plan|for coding/.test(n)) return 'CodingPlan';
  if (/token plan/.test(n)) return 'TokenPlan';
  if (isOfficial) return 'OfficialLogin';
  return 'Api';
};

// codex config.toml 中的 base_url 提取（model_providers.*.base_url）
// 各 app 的协议格式字段提取；无对应字段时返回 null（写入侧用 Adapter 默认值）
const apiFormatOf = (app, sc, configToml) => {
  switch (app) {
    case 'pi':
    case 'openclaw':
      return typeof sc.api === 'string' && sc.api ? sc.api : null;
    case 'hermes':
      return typeof sc.api_mode === 'string' && sc.api_mode ? sc.api_mode : null;
    case 'claude':
      return 'anthropic-messages';
    case 'codex': {
      const m = /wire_api\s*=\s*"([\w-]+)"/.exec(configToml || '');
      return m ? `codex-${m[1]}` : null;
    }
    default:
      return null;
  }
};

const codexBaseUrl = (configToml) => {
  const m = /base_url\s*=\s*"([^"]+)"/.exec(configToml || '');
  return m ? m[1] : null;
};

const capabilityOf = (raw) => {
  if (!raw || typeof raw !== 'object') return null;
  const cap = {};
  if (typeof raw.reasoning === 'boolean') cap.reasoning = raw.reasoning;
  if (Array.isArray(raw.input)) cap.input = raw.input.filter((x) => typeof x === 'string');
  if (Number.isFinite(raw.contextWindow)) cap.contextWindow = raw.contextWindow;
  if (Number.isFinite(raw.maxTokens)) cap.maxTokens = raw.maxTokens;
  if (raw.reasoningLevels && Array.isArray(raw.reasoningLevels)) {
    cap.reasoningLevels = raw.reasoningLevels.filter((x) => typeof x === 'string');
  }
  if (raw.thinkingLevelMap && typeof raw.thinkingLevelMap === 'object') {
    const map = {};
    for (const [k, v] of Object.entries(raw.thinkingLevelMap)) {
      if (v === null || typeof v === 'string') map[k] = v;
    }
    if (Object.keys(map).length > 0) cap.thinkingLevelMap = map;
  }
  return Object.keys(cap).length > 0 ? cap : null;
};

const modelList = (models) => {
  const out = [];
  const seen = new Set();
  const push = (id, displayName, raw) => {
    id = String(id || '').trim();
    if (!id || seen.has(id)) return;
    seen.add(id);
    const entry = { id, displayName: String(displayName || id).trim() || id };
    const cap = capabilityOf(raw);
    if (cap) entry.capability = cap;
    out.push(entry);
  };
  if (Array.isArray(models)) {
    for (const m of models) {
      if (!m || typeof m !== 'object') continue;
      push(m.id ?? m.model, m.name ?? m.displayName, m);
    }
  } else if (models && typeof models === 'object') {
    for (const [id, m] of Object.entries(models)) {
      push(id, m && typeof m === 'object' ? m.name : id, m);
    }
  }
  return out;
};

const load = (app) => JSON.parse(readFileSync(resolve(here, 'out', `${app}-presets.json`), 'utf-8'));

const routes = {};
const skipped = {};

for (const [app, adapterId] of Object.entries(ADAPTER_BY_APP)) {
  const list = [];
  for (const preset of load(app)) {
    const name = String(preset.name || '').trim();
    const isOfficial = Boolean(preset.isOfficial);
    const providerType = String(preset.providerType || '');
    if (!name) { (skipped[app] ||= []).push('<unnamed>'); continue; }
    // 官方登录条目：Kite 已有自己的官方登录入口；OAuth/官方 env 为空，不进第三方目录。
    if (isOfficial || providerType === 'codex_oauth') { (skipped[app] ||= []).push(`${name}(official)`); continue; }

    const sc = preset.settingsConfig || {};
    let baseUrl = null;
    let models = [];
    if (app === 'claude') {
      const env = sc.env || {};
      baseUrl = typeof env.ANTHROPIC_BASE_URL === 'string' ? env.ANTHROPIC_BASE_URL : null;
      models = modelList([{ id: env.ANTHROPIC_MODEL }]);
    } else if (app === 'codex') {
      baseUrl = codexBaseUrl(preset.config);
      models = modelList(preset.modelCatalog);
    } else if (app === 'opencode') {
      baseUrl = sc.options && typeof sc.options.baseURL === 'string' ? sc.options.baseURL : null;
      models = modelList(sc.models);
    } else if (app === 'pi' || app === 'openclaw') {
      baseUrl = typeof sc.baseUrl === 'string' ? sc.baseUrl : null;
      models = modelList(sc.models);
    } else if (app === 'hermes') {
      baseUrl = typeof sc.base_url === 'string' ? sc.base_url : null;
      models = modelList(sc.models);
    } else if (app === 'gemini') {
      baseUrl = typeof sc.baseUrl === 'string' ? sc.baseUrl : (sc.apiKey ? null : null);
      models = modelList(sc.models);
    }
    if (!baseUrl) { (skipped[app] ||= []).push(`${name}(no-baseUrl)`); continue; }
    if (baseUrl.includes('${')) { (skipped[app] ||= []).push(`${name}(placeholder-url)`); continue; }
    if (models.length === 0) { (skipped[app] ||= []).push(`${name}(no-models)`); continue; }

    const category = CATEGORY_MAP[preset.category] || 'ThirdParty';
    list.push({
      id: slugify(name),
      displayName: name,
      baseUrl: baseUrl.replace(/\/+$/, ''),
      models,
      vendorDisplayName: name,
      category,
      accessChannel: accessChannelOf(name, isOfficial),
      market: MARKET_BY_CATEGORY[preset.category] || 'Unspecified',
      documentationUrl: preset.apiKeyUrl || preset.websiteUrl || null,
      apiFormat: apiFormatOf(app, sc, preset.config),
    });
  }
  routes[adapterId] = list;
}

const bundle = {
  schemaVersion: 1,
  source: 'farion1231/cc-switch@master (MIT)',
  routes,
};

const jsonPretty = JSON.stringify(bundle, null, 1);
if (jsonPretty.includes('"""') || jsonPretty.includes('$')) {
  throw new Error('产物包含 Kotlin raw string 保留字符（""" 或 $），请改用资产文件分发');
}
const json = JSON.stringify(bundle);

mkdirSync(resolve(here, 'out'), { recursive: true });
writeFileSync(resolve(here, 'out', 'kite-catalog.json'), jsonPretty);

const kotlin = `package com.kite.app.agent.config

/**
 * CC Switch 供应商预置目录（构建期生成的 vendor 数据，请勿手改）。
 *
 * 上游：farion1231/cc-switch (MIT, Copyright (c) 2025 Jason Young)。
 * 再生成：tools/cc-switch-vendor/convert-all.mjs → convert-kite.mjs。
 * 解析方：[CcSwitchBundledCatalogParser]。
 */
internal object CcSwitchCatalogBundle {
    internal val CATALOG_JSON: String = """
${jsonPretty}
    """.trimIndent()
}
`;

const kotlinPath = resolve(ROOT, 'app', 'src', 'main', 'java', 'com', 'kite', 'app', 'agent', 'config', 'CcSwitchCatalogBundle.kt');
mkdirSync(dirname(kotlinPath), { recursive: true });
writeFileSync(kotlinPath, kotlin);

let total = 0;
for (const [adapterId, list] of Object.entries(routes)) {
  total += list.length;
  console.log(`${adapterId}: ${list.length} presets, ${list.reduce((n, p) => n + p.models.length, 0)} models`);
}
console.log(`TOTAL: ${total} presets → out/kite-catalog.json + ${kotlinPath.endsWith('CcSwitchCatalogBundle.kt') ? 'CcSwitchCatalogBundle.kt' : kotlinPath}`);
for (const [app, list] of Object.entries(skipped)) {
  if (list.length) console.log(`skipped[${app}]: ${list.length}`);
}
