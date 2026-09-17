# Agent 原生 Skill 与 MCP 发现矩阵

Kite 不维护第二份 Skill/MCP 事实源。适配器在进入管理页、显式刷新或配置操作完成后的
`readLive/backfill` 中读取 Agent 原生文件；界面只消费已经构造好的快照。

| Agent | Skill 原生位置 | MCP 原生位置 | Kite 重新发现边界 |
| --- | --- | --- | --- |
| OpenCode | `/root/.config/opencode/skill`、`/root/.config/opencode/skills` | `/root/.config/opencode/config.json`、`opencode.json` 或 `opencode.jsonc` 的 `mcp` | `readLive/backfill` |
| Kimi Code | `/root/.kimi-code/skills`、`/root/.agents/skills` | `/root/.kimi-code/mcp.json` | `readLive/backfill` |
| MiMo Code | `/root/.config/mimocode/skills`、`/root/.agents/skills`、`/root/.claude/skills`、`/root/.codex/skills`、`/root/.opencode/skills` | `/root/.config/mimocode/mimocode.jsonc` 的 `mcp` | `readLive/backfill` |
| OpenClaw | `/root/.openclaw/skills`、`/root/.agents/skills` | `/root/.openclaw/openclaw.json` 的 `mcp.servers` | `readLive/backfill` |
| Claude Code | `/root/.claude/skills` | `/root/.claude.json` 的 `mcpServers` | `readLive/backfill` |
| Codex | `/root/.agents/skills` | `/root/.codex/config.toml` 的 MCP 表 | `readLive/backfill` |
| Hermes | `/workspace/.kf/software/kite.hermes.core/home/skills` | 同一目录 `config.yaml` 的 `mcp_servers` | `readLive/backfill` |
| Gemini CLI | `/root/.gemini/skills`、`/root/.agents/skills` | `/root/.gemini/settings.json` 的 `mcpServers` | `readLive/backfill`；持久启停格式尚未核验，不显示启停操作 |
| Qwen Code | `/root/.qwen/skills`、`/root/.agents/skills` | `/root/.qwen/settings.json` 的 `mcpServers` 与 `mcp.excluded` | `readLive/backfill` |
| Qoder CLI | `/root/.qoder/skills`、`/root/.agents/skills` | `/root/.qoder/settings.json` 的 `mcpServers` | `readLive/backfill` |
| Cursor CLI | `/root/.cursor/skills`、`/root/.agents/skills` | `/root/.cursor/mcp.json` 的 `mcpServers` | `readLive/backfill`；启停另有本机授权事实，不伪造成配置文件能力 |
| Devin CLI | `/root/.config/devin/skills`、`/root/.agents/skills` | `/root/.config/devin/mcp_config.json` 的 `mcpServers` | `readLive/backfill` |

## 共同规则

- Skill 目录只接受含合法 `SKILL.md` 的项目；扫描深度、文件大小和路径范围均有上限，不跟随符号链接。
- 原生文件是唯一事实源。用户或 Agent 在原生目录新增内容后，下一次管理快照刷新即可在 Kite 中出现；不需要向 Kite 数据库再注册一遍。
- “Kite 已发现”不等于“运行中的 Agent 已热加载”。具体 Agent 是否需要新会话或重连，遵守其原生运行边界，Kite 不伪造热加载成功。
- MCP 的解析、启停和删除只修改对应 Agent 原生配置；连接测试只有在能调用真实原生命令时才报告结果，不能把配置可读当成服务已连接。
- 列表渲染、滚动和会话输入不会触发目录扫描。页面状态更新只替换相关快照和列表项，不整页重建。
- 共享 JSON 适配骨架只统一已经核验的 `mcpServers` 和 Skill 目录事务。模型、Provider、推理、权限、会话恢复与官方账号仍由 ACP 实时能力或专用 Adapter 声明；共享骨架会拒绝把猜测出来的模型或 Provider 写进原生文件。

## 自动证据

适配器测试对支持原生扩展的 Agent 执行同一回填流程：先读取空快照，再从适配器外部写入 Skill 目录和
MCP 原生配置，最后调用 `backfill` 并确认两者同时出现。既有测试另行覆盖受支持 Agent 的
Skill 启停/删除和 MCP 启停写回。真实外部 MCP 进程未启动时，测试只确认“未检查/不可用”，
不把它写成成功。

魅族 18 另做了真实 OpenCode 探针：在 Agent 原生 Skill 目录创建独立测试
`SKILL.md` 后，进入 Skill 管理页会在后台回填并显示该项；删除测试目录后再次进入，列表同步
恢复为空。探针与临时文件已清理。这个证据只证明 Kite 自动发现，不把它扩大成运行中
OpenCode 已热加载该 Skill。
