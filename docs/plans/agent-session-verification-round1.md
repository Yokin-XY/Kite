# Agent 会话链路验证（第一轮：Claude Code + 智谱 Coding Max）

> 目标：验证"新建会话 / 加载恢复 / 会话间交互"的通畅性与稳定性——5 Agent 匹配的地基层。
> 供应商：智谱 GLM Coding Plan（key 由用户提供，仅存设备端，不入库）。

## 验证环境

- 设备：OnePlus KB2000（3f8bbaad），Kite debug @ a4f2c029
- Agent：Claude Code（npm 官方命令安装）
- Provider：智谱 GLM Coding Plan，Anthropic 路由 `https://open.bigmodel.cn/api/anthropic`
- 模型：GLM-5.3（models.dev 目录另提供 5.3-Flash / 5.3 Highspeed / 4.6V——印证"档位=模型侧"）

## 通过项 ✅

| 步骤 | 结果 |
|------|------|
| 供应商配置链路 | 预设（models.dev 目录）→ 智谱 Coding Plan → key → 保存 → 模型库识别（4 模型）✓ |
| 应用到 Agent | 模型库点选 GLM-5.3 设为默认 → ClaudeCodeAgentConfigAdapter 写入原生 env（ANTHROPIC_BASE_URL/AUTH_TOKEN）✓ |
| 新建会话 + 对话 | "hi" → GLM-5.3 回复（8 秒），会话自动命名 "Greeting" ✓ |
| 杀进程重启 | force-stop → 会话列表持久（Greeting / reply… 两条）✓ |
| 恢复会话 | 打开 Greeting → 历史消息完整（hi + 回复）✓ |
| 模型选择持久 | 重启后 GLM-5.3 仍为当前模型，"模型"控制条正常出现 ✓ |

## 发现的问题 ❌

### 问题 1（核心）：特定消息触发无声失败——回复永不到达且无失败提示

- 稳定复现：`what was my first word` 两次发送均失败（用时 3/5 秒后 turn 结束，无回复、无错误提示）。
- **已排除**的假设：
  - ✗ 息屏/后台打断（息屏 40 秒回复照常到达）；
  - ✗ 冷启后恢复会话第一条（复现失败，第一条正常）；
  - ✗ 回复文本与历史重复（`say exactly: hi` 回复 "hi" 正常渲染）；
  - ✗ thinking/推理路径（17×23 分步推理正常回复）。
- 剩余候选假设：该消息要求检索会话早期上下文，可能触发 claude-agent-acp 的特殊行为
  （microcompact / 权限请求 / 特殊 update 类型），需 ACP 层日志取证。
- **已买到的教训**：ACP 层零日志，无法取证——修复前置：加可观测性。

### 问题 1b（伴生）：失败无声

- 对比：认证失败时会显示"本轮未完成/失败： Authentication required"；
  但流中断时只有固定的"用时 N秒"，无任何失败/重试提示——违反"点击后无提示恢复原状"的交互规约。

### 通过项（追加）：消息链路比预期稳

| 场景 | 结果 |
|------|------|
| 息屏 40 秒中长回复（数数到 10） | ✓ 解锁后完整到达 |
| 冷启后恢复会话第一条消息 | ✓ 正常回复 |
| 强制推理消息（分步算术） | ✓ 正常回复 |
| 回复文本与历史完全相同 | ✓ 正常渲染 |

### 问题 2：OpenClaw 后台网关循环崩溃 + OpenCode 登记无操作掉落

- 日志：`background-space-main-openclaw-gateway` 每 ~4 秒 "自动恢复已排队: process-exit:1"，
  `supervisorctl status failed: admission_shared_write_waiting_for_exclusive`（写锁竞争）。
- 伴随现象：OpenCode 资源在无人工操作下从"已获取（打开/卸载）"变为"未获取（获取）"。
- 关联待查：网关崩溃与登记掉落是否同源（probe 失败 → invalidateMissingInstallations）。
- 需要判断是否与本次修复退场改动有关（改动本身只动标记路径，但探测时机可能被暴露）。

### 观察（不构成问题）

- "模型"控制条仅在 provider 应用后出现（catalog.model 非 null），认证未完成时隐藏——符合设计。
- 档位选择在模型库里以模型 ID 呈现，会话内模型条与之一致——印证用户判断：档位跟模型走。
- Agent 自有模式（工作模式面板）：Manual / Accept edits / Plan / Auto——第二层验证的清单项。

## 下一步

1. 攻问题 1：恢复会话的响应流（代码定位 + 修复 + 重测杀进程恢复对话）。
2. 查问题 2：OpenClaw 网关崩溃日志（background-runtimes 日志区）与 OpenCode 登记掉落链路。
3. 以上修完再铺 5 Agent × 4 层矩阵（会话生命周期 / 供应商+档位 / 自有模式 / MCP+Skill）。
