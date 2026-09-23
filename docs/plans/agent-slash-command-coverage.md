# Agent 斜杠命令覆盖核对表（确定性事实）

> 目标：每个 Agent 原生自带的斜杠命令，确定性地全部接入 Kite 命令弹层；
> 数量先对齐（Kite 显示数 = 原生可执行数），再逐条实测触发与执行。
> 本文固化截至 2026-09-23 的源码级研究结论，防止记忆丢失。

## 方法论（已验证有效的三种接入模式）

| 模式 | 适用 | 已落地 |
|---|---|---|
| **协议广告**（available_commands_update） | Agent 官方 ACP 自带清单 | Hermes、OpenCode、Claude（迟到但会到） |
| **桥端原生命令**（客户端拦截翻译成 SDK/协议调用） | TUI 命令不走协议文本路径的 | pi（抄 pi-web-ui slash-commands）、Codex（翻译成 app-server op） |
| **目录动态扫描** | 自定义命令/Skill 是文件系统事实 | Claude（~/.claude/commands）、OpenCode（skills+command 目录） |

合并语义：**协议 ∪ Adapter 静态 ∪ 目录动态，按名称去重，协议事实优先**（到达=补齐不替换）。

## 逐 Agent 事实（源码级）

### pi（桥=@earendil…pi-acp-bridge，我们自己的）
- 原生命令：`BUILTIN_SLASH_COMMANDS` = **23 条**（dist/core/slash-commands.js）：
  settings, model, tree, thinking, scoped-models, export, import, share, copy,
  name, session, changelog, hotkeys, fork, clone, trust, login, logout, new,
  compact, resume, reload, quit
- pi-web-ui（第三方最全客户端）NATIVE 只实现 11 条：new/name/model/compact/cwd/thinking/resume/reload/help/copy/quit
- Kite 桥已实现 6 条：compact/model/thinking/name/reload/help
- 缺口：export/copy/session/cwd/resume/new —— 查 pi SDK API 逐个补
- 确定性排除候选（TUI-only，无 SDK API）：settings/tree/hotkeys/scoped-models/changelog/fork/clone/trust/share/import/login/logout/new(ACP 会话归客户端)/quit

### Claude Code（ACP=官方 @agentclientprotocol/claude-agent-acp 0.79）
- 协议：supportedCommands() 全量（含自定义命令+MCP `mcp:` 前缀），迟到但会到；
  过滤 UNSUPPORTED：context/cost/login/logout/output-style:new/release-notes/todos
- Kite 静态：22 条（官方文档全集 − UNSUPPORTED）
- 目录动态：~/.claude/commands/*.md（readSlashCommands 已扫）
- 待办：官方文档逐条数一遍补漏（output-style/usage/exit 等确认）

### Codex（协议=codex-app-server，非 ACP）
- 原生命令：codex-rs/tui/src/slash_command.rs 枚举 **63 条**（kebab-case）
- app-server **不解释 / 文本**（透传=模型收到字面）→ 必须桥翻译
- app-server v2 op 资源：thread.rs（compact/start、fork、archive?、rename?）、
  turn.rs、review.rs、model.rs、permissions.rs、mcp.rs、account.rs、rollout.rs…
- 当前 Kite：仅 /compact（thread/compact/start）已实测 ✓
- 待办：63 条逐个映射 app-server op；映射上的进清单+翻译执行器；
  纯终端 UI 类（theme/statusline/pets/vim/keymap/tui/raw/terminal 类）确定性排除

### OpenCode（ACP=官方内置）
- 官方 ACP prompt 处理：detectSlashCommand → 命令注册表内走 session.command；
  /compact 特判 summarize —— **透传即执行**
- 命令注册表（command/index.ts）= 内置 init/review + 配置命令 + MCP prompts + skills
- TUI 命令（new/share/undo/redo/models/themes/usage/export…）是 tui 层，ACP 不执行 → 排除
- Kite：静态 3（compact/init/review）+ skillDirectory（skills）+ command 目录扫描 ✓
- 协议广告到达后 = 真实全集（数量以广告为准）

### Hermes（ACP=官方 hermes-acp）
- 协议广告 **9 条**：help/model/tools/context/reset/compress/steer/queue(+1)
- 即官方支持全集，无缺口 ✓

### OpenClaw
- 排除（自带 webchat 入口）

## 实测记录（真机，每 Agent 至少 1 条执行）

| Agent | 实测命令 | 结果 |
|---|---|---|
| pi | /compact | 桥拦截→SDK compact()→回显"Nothing to compact (session too small)" ✓ |
| Codex | /compact | 桥拦截→thread/compact/start→回显"已请求压缩" ✓ |
| OpenCode | /（弹层数量） | 静态 3 + 动态 skill（/AGENTS）✓ |
| Claude | /（弹层数量） | 22 条全集 ✓（协议迟到前兜底） |
| Hermes | /（弹层数量） | 协议 9 条直达 ✓ |

## Claude Bash 工具执行环境（独立问题，2026-09-24 定性）

现象：Claude /init 透传执行正常，但模型的所有 Bash/Read 工具调用失败
（ls/读 README 全 ENOENT，工作目录显示为宿主数据目录路径）。

排查结论（证据链）：
1. 宿主车道 node 的 child_process 被 kite-node-host-runtime.cjs 全量拦截
   （spawn/spawnSync/execFile/exec）：node 命令→宿主启动器；bash 等外部
   命令→prootPrefix 路由进 proot 车道执行
2. 因此 Claude 的 Bash 工具实际跑在 proot 里；它看到的 /data/user/0/...
   宿主数据目录路径在 proot 视图 = /storage/emulate/... 映射，访问语义
   不成立 → 全部失败
3. 附带加固：C 兼容层（kite-glibc-compat.c）新增 execve/execv/execvp/
   execvpe/posix_spawn/posix_spawnp 钩子 + KITE_NODE_HOST_ROOTFS 白名单
   前缀翻译（/bin /usr /etc /lib /opt /var /home /root 等；/system /data
   等Android 真实顶层不翻），env 未注入时零行为。WSL Ubuntu-24.04 用
   build-kite-node-glibc-compat.ps1 构建。

修复方向（独立任务）：Claude/外部 Agent 的工具执行车道策略——bash 类
工具应在 proot 内以容器路径（/workspace）执行，宿主路径参数需经
sessionPathMapper 双向翻译；或为宿主车道提供受控白名单的直执行通道。

## 下一步（按数量缺口排序）

1. **Codex 63→N**：逐条映射 app-server op（最大件）
2. **pi 6→11**（对齐 pi-web-ui 的 NATIVE 集）：export/copy/session/cwd/resume 逐个查 SDK API
3. **Claude 补漏**：官方文档逐条核对
4. 数量对齐后：每 Agent 实测 1 条新接入命令（截图自验）
