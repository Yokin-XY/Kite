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
2. **阶段 2 pi 桥原型**：initialize/session new/load/prompt+updates 转发的最小实现；Docker 跑 lifecycle 9/9。
3. **阶段 3 Kite 接入**：5 条资源 manifest（新增 hermes/openclaw，openclaw 需声明网关后台运行时）；
   assets 桥拷贝安装动作；OpenClaw 不需要 Kotlin attach 适配器（官方 acp 桥即可，比预期更简）。
4. **阶段 4 真机验收**：六 Agent 模块 1；回归 6×4 矩阵。

## 已完成的前置事实

- pi 0.85.1 无官方 ACP；官方 SDK=Node（pi-web-ui 以 in-process SessionManager 嵌入为参照）。
- OpenClaw 官方 VS Code 插件即走"网关 WebSocket+JSON Node 协议"；真机 stdio 强塞=崩溃循环（写锁竞争）。
- Hermes 官方文档声明 ACP server 模式（stdio）。
- 矩阵 harness：claude/codex/opencode 模块 1 已 9/9（Docker+智谱）。
