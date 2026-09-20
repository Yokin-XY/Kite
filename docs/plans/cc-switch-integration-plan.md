# CC Switch 供应商体系全面接入方案

> **状态标注（2026-09-21 文档整理）**：部分完成——数据管道（d2699d41 全量预置 + d331a011 apiFormat 穿透）与 P1/P2 已落地；P3 拉模型列表、P4 live sync 未实现。稳定结论已沉淀至 decisions.md 与 agent-configuration-boundary.md；剩余项转正式任务后本文件归档。

> 状态：方案已与用户对齐方向（全面以 CC Switch 为准，数据/机制/交互三层全要，仅桌面 GUI 不做）。
> 授权记录：用户明确允许直接嵌入 CC Switch 源码/数据，也允许网络下载，由我按合适程度衡量。
> 结论：**全量数据内嵌 APK（约 650KB，无网络下载）+ 机制用 Kotlin 等价实现 + 交互照 CC Switch 设计对齐**。

## 一、CC Switch 体系研究结论

### 1. 数据资产（离线目录，随应用发布）

| 来源文件 | 内容 | 转换产物体积 |
|---|---|---|
| claudeProviderPresets.ts | 90 个预设（settings.json env 形状） | 50.6 KB |
| codexProviderPresets.ts | 85 个预设（auth.json + config.toml） | 81.8 KB |
| geminiProviderPresets.ts | 26 个预设 | 14.2 KB |
| grokBuildProviderPresets.ts | 39 个预设 | 21.4 KB |
| hermesProviderPresets.ts | 79 个预设 | 56.5 KB |
| mcodeProviderPresets.ts | 39 个预设（复用 pi 目录） | 33.2 KB |
| openclawProviderPresets.ts | 78 个预设 | 92.5 KB |
| opencodeProviderPresets.ts | 78 个预设 | 62.7 KB |
| piProviderPresets.ts | 74 个预设 | 97.4 KB |
| universalProviderPresets.ts | 2 个（通用供应商模板） | 1.2 KB |
| piModelCatalog.ts | 72 个模型能力知识库（推理/输入模态/上下文窗口/最大输出） | （含于 pi 产物） |
| piThinkingProfiles.ts | 7 级思考档位映射（按 catalogKey×apiFormat 物化 thinkingLevelMap） | （含于 pi 产物） |
| **合计** | **590 个供应商预设** | **511.5 KB** |

- 每条预设 = 完整原生配置节点（`settingsConfig`）+ 分类（官方/三方/聚合商）+ 官网/取 key 链接 + 主题/图标。
- 上游 license：**MIT**（2025 Jason Young）。嵌入数据与代码合规，需在上游快照目录与关于页保留版权声明。
- 上游由社区持续维护（"每个供应商的兼容性修复都来自真实用户"），预设目录是活的。

### 2. 核心机制（运行时行为）

1. **Provider 数据模型（SSOT）**：库中 `Provider{id, name, settingsConfig(核心配置), meta(操作元数据), current}`；
   切换 = 把 `settingsConfig` **投影**到目标应用原生配置文件；`meta` 永不写入原生配置。
2. **两层应用模型**：
   - *代理层*（claude/codex/gemini/grokbuild）：流量走 CC Switch 本地代理，实现故障转移队列、熔断、用量归因、协议转换。
   - *附加层*（opencode/openclaw/hermes/pi/mcode）：读现有原生配置 → **upsert 单个供应商条目** → 写回，保留其他条目，不拦截流量。
3. **Live provider sync**：启动时从活跃配置文件自动导入外部变更（如 CLI 自己写入的供应商）；卡片"已启用"状态 = 该条目存在于配置文件。
4. **拉模型列表**（用户重点关注的"获取模型"）：`GET /models`；
   `modelsUrl` 精确覆写优先；否则由 baseURL 生成候选 URL 列表按序尝试（含剥离 `/anthropic` 等兼容子路径的兜底）；
   响应解析支持三种格式：OpenAI `data[].id`、Anthropic `data[].id`、智谱 Responses `models[].slug`。
5. **官方入口保留**：官方登录作为预置之一，随时可切回 OAuth；恢复官方 = 切官方预设 + 重启。
6. **Auth 边界**：目标应用自己管的凭据（pi `auth.json`、codex OAuth token 等）CC Switch 永不读/写/复制/刷新。
7. **备选端点 + 测速**：一个供应商可挂多个候选 baseUrl，测速择优，记录 last_used。
8. Deep Link 导入（`ccswitch://`）、配置快照 profiles、WebDAV/S3 云同步、系统托盘 —— 桌面侧能力。

### 3. Kite 的定位与范围决策

- **Kite 对应"附加层"语义**：全部 6 Agent 都走"写原生配置、CLI 直连上游"，不建本地代理。
  理由：Android 容器内 CLI 直连最短路径；故障转移/用量归因属代理层增强，Kite 远期再议。
- **不嵌入的部分**：Tauri/React GUI（用户明确不要桌面形态，但**交互设计照学**）、本地 HTTP 代理引擎、
  WebDAV/S3 同步、系统托盘、npm/GitHub 网络依赖（数据全部随 APK 走，符合"中国网络现实"）。
- **嵌入的部分**：
  - 数据：全部 590 预设 + 模型知识库 + 档位映射（内嵌 JSON，约 650KB）。
  - 机制：预设驱动的添加流程、upsert 写入、live sync、拉模型、官方入口保留、auth 边界——Kotlin 等价实现。
  - 交互：供应商卡片列表（当前高亮）、预设下拉 → 填 key → 添加、编辑回读、启用/禁用（写/删节点库留底）。

## 二、数据管道（已 PoC 验证）

```
上游 farion1231/cc-switch (MIT, 社区维护)
   │ ① vendor 快照：tools/cc-switch-vendor/src/**（TS 源文件，git 提交，可 diff）
   │ ② 转换脚本：tools/cc-switch-vendor/convert-all.mjs（esbuild bundle → node 执行 → JSON）
   ▼
tools/cc-switch-vendor/out/{app}-presets.json ×10（590 预设，511.5KB，含 pi 知识库）
   │ ③ 拷入 app assets（构建期/提交期，走 git）
   ▼
Kite 运行时读取 → 替换手维护的 CcSwitchProviderPresetDefinitions 数据部分
```

- 更新上游 = ①curl 重拉 → ②一条命令转换 → ③提交。无托管依赖、无运行时网络。
- 预设 TS 源里少量函数（如 openclaw rebase、pi thinkingLevelMap 物化）在转换期已执行，产物是纯数据。
- smol-toml 等 npm 依赖仅转换期需要（stub 隔离），不进 APK。

## 三、分阶段实施

| 阶段 | 内容 | 验收 |
|---|---|---|
| **P1 pi 数据接入 + 端到端** | pi-presets.json 接入 Kite 数据源；供应商编辑页预设下拉改吃 JSON；真机：选"智谱 GLM Coding Plan"预置 → 填 key → 保存 → pi 会话对话成功（零注入） | 真机对话成功；无预设时提示"当前没有供应商，请配置供应商" |
| **P2 模型能力落地** | 模型条目扩字段（reasoning/input/contextWindow/maxTokens/thinkingLevelMap）；pi adapter 写完整能力；桥/会话 UI 消费档位 | 档位 UI 显示真实映射而非缺省假值 |
| **P3 拉模型列表** | 供应商编辑页"获取模型"按钮：候选 URL 生成 + 三格式解析（对齐 CC Switch fetch_models_for_config 语义） | 用智谱/任意 OpenAI 兼容端点拉出模型列表 |
| **P4 live sync** | 打开供应商列表时读原生配置，外部变更 upsert 进 Kite（对齐"已启用=在配置文件中"） | 容器内手改 models.json → Kite 列表出现对应卡片 |
| **P5 铺满 9 家** | 其余 agent 的 presets.json → 各家 config adapter 消费（claude/codex/gemini/grokbuild/opencode/openclaw/hermes/mcode） | 各家选预置 → 填 key → 原生配置正确生成 |
| 远期 | 备选端点+测速、通用供应商（一份 key 扇出多 app）、Deep Link 导入 | — |

## 四、边界与原则（不变）

- API key 绝不入库不入日志（Kite 现有原则继续有效；预设数据只含端点与模型，不含 key）。
- auth 边界照 CC Switch：目标应用自管凭据不碰。
- 上游数据更新是我们仓库内的普通提交（可 review 可回滚），不引入任何运行时 GitHub 依赖。
- 本方案只动供应商体系；模型切换与 Agent 模式匹配是下一主题（供应商体系稳定后专做）。
