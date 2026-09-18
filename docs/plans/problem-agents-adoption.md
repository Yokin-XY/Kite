# 三问题 Agent 落地方案（pi / OpenClaw / Hermes）

> 2026-09-19 与用户定稿。约束背景：国内网络（不依赖 GitHub/npm 托管我们自己的包）、
> 不新增独立仓库、安装交给官方机制、Kite 资源化路线不变。

## 最终形态：我们的代码全跟 APK 走，Agent 本体走现有资源通道

| Agent | 我们的代码（随 APK） | 安装（资源页，官方包+镜像） | 运行 |
|---|---|---|---|
| pi | 薄桥 JS 脚本（APK assets，安装时静默拷贝；官方 SDK→ACP stdio，几百行） | 官方 pi 包 | managed：桥被拉起，Kite 当普通 ACP Agent |
| OpenClaw | 无需 Kotlin 适配器（阶段 1 实测修正） | 官方 openclaw 包 | 网关单例常驻（Kite 后台运行时）+ 官方 `openclaw acp` 桥按会话拉起（managed） |
| Hermes | 无 | 官方 Hermes 包（pip，需 PRoot 内 Python） | managed：`hermes acp`（官方 ACP server），与 Claude/Codex 同构 |

边界一句话：**Kite 的代码跟 Kite 走（APK），Agent 跟资源仓库走（manifest JSON，不发版可改）。**

- 托管：零。桥不发 npm、不建仓库；GitHub 仅为我们自己的开发仓库（既有事实）。
- 安装形式：桥拷贝=静默（资源安装动作一部分）；Agent 本体=正常可见资源流程。
- 更新：桥/适配器随 APK 发版；Agent 本体官方机制；接法变化改 manifest。

## 执行计划

1. **阶段 1 Docker 实测 ✅ 完成（2026-09-19）**：
   - Hermes：pip 装 + 官方 `hermes acp` + 智谱 anthropic_messages 路由 → **9/9**
   - OpenClaw：网关常驻（gateway.mode=local, port 18789）+ 官方 `openclaw acp` 桥 → **9/9**。
     崩溃循环根因同款复现：网关多实例写锁打架；单例网关+官方桥即干净。
   - 前置 3 个（claude/codex/opencode）此前已 9/9。**5/6 全绿。**
2. **阶段 2 pi 桥原型 ✅ 完成（2026-09-19）**：
   - `pi-bridge/pi-acp-bridge.mjs`（~400 行）：官方 SDK（createAgentSession/SessionManager/DefaultResourceLoader）
     → ACP stdio；智谱用 pi 内置供应商 zai-coding-cn（glm-5.3 及全档位在目录，零自定义模型配置）。
   - 覆盖：消息流/思考流/工具调用映射、跨进程恢复（SessionManager.list→open→重放）、
     命令面板（getPrompts→available_commands_update，Kite 斜杠命令面的输入源）。
   - Docker lifecycle **9/9**。踩坑记录：npm 无 package.json 安装报 idealTree、ESM 不认
     NODE_PATH（桥须与 node_modules 同目录）、listAll 首参是会话目录非 cwd（用 list(cwd)）。
3. **阶段 3 Kite 接入（进行中）**：
   - ✅ OpenClaw 真机闭环（2026-09-19，提交 9b77c231）：崩溃循环三层根因全破
     （宿主通道无 /tmp→服务型 FULL_LINUX；准入活锁→常驻豁免；ACP 进程→清单
     requirements 新合同）。真机：网关 /readyz 200、会话启动、GLM-5.3 对话完整渲染。
     斜杠命令事件（AvailableCommandsUpdate）全链路已通。
   - 遗留：网关冷启动 >30s（无代理时模型目录刷新拖慢几分钟）——需调
     startupTimeoutMs / 网关代理 env；智谱 provider 当前为容器内手写配置，
     产品化走 Kite 的 openclaw 配置适配器（模块 2）。
   - ⏳ pi 清单改桥（argv 指向 APK assets 桥，静默拷贝）。
   - ⏳ Hermes 真机安装验证（Python 前置已就绪：kite.python/kite.uv 已装）。
4. **阶段 4 真机验收**：六 Agent 模块 1；回归 6×4 矩阵。

## 已完成的前置事实

- pi 0.85.1 无官方 ACP；官方 SDK=Node（pi-web-ui 以 in-process SessionManager 嵌入为参照）。
- OpenClaw 官方 VS Code 插件即走"网关 WebSocket+JSON Node 协议"；真机 stdio 强塞=崩溃循环（写锁竞争）。
- Hermes 官方文档声明 ACP server 模式（stdio）。
- 矩阵 harness：claude/codex/opencode 模块 1 已 9/9（Docker+智谱）。
