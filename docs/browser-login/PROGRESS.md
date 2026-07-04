# Kite 浏览器登录回跳进度

最后更新：2026-07-04 基线准备

## 当前状态总览

| 任务 | 状态 | 备注 |
| --- | --- | --- |
| B0 建立浏览器任务基线 | done | 三件套和双线隔离说明已建立 |
| B1 确认当前内置浏览器和回跳真实链路 | pending | 后续浏览器副本中执行 |
| B2 调研官方推荐和通用网站登录回跳模式 | pending | 后续浏览器副本中执行 |
| B3 设计 Kite 登录回跳协议 | pending | 依赖 B2 |
| B4 实现最小通用登录回跳 | pending | 依赖 B3 |
| B5 扩展多站点兼容矩阵 | pending | 依赖 B4 |

状态取值：`pending` / `in_progress` / `blocked` / `done`

## 待验证清单

- [ ] 浏览器线物理目录为 `D:\xm\Kite-browser-login`。
- [ ] 浏览器线分支为 `codex/browser-login-return` 或用户确认的等价分支。
- [ ] OnePlus 8T `3f8bbaad` 在线。
- [ ] 本机转发端口使用 `18791`，不与 X11 线冲突。
- [ ] 后续代码实现完成后运行相关单测、构建、安装、截图和 logcat 检查。

## 任务日志

### B0 [done] 建立浏览器任务基线

三问自检：

1. 目标：把浏览器登录线从 X11 线中分离出来，固定物理目录、分支、设备、端口和后续研究方向。
2. 完成标准：浏览器三件套存在；双线隔离说明写明浏览器线绑定 OnePlus 8T `3f8bbaad`；不开始代码改动和资料调研实现。
3. 前置任务：无。

已完成：

- 新增 `docs/browser-login/PLAYBOOK.md`。
- 新增 `docs/browser-login/PROGRESS.md`。
- 新增 `docs/browser-login/DECISIONS.md`。
- 新增 `docs/parallel-workstreams/README.md`，记录浏览器线和 X11 线的目录、设备、端口和隔离规则。

本次不做：

- 不实现登录回跳。
- 不继续替用户搜资料。
- 不复制物理目录。
- 不创建分支或会话。
