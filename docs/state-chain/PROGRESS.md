# Kite 动态状态链路进度

最后更新：2026-07-11，T005-T008 全部完成，等待优化提交封口。

## 当前恢复指针

- 当前任务：已完成
- 当前阶段：差异复核与优化提交
- 稳定基线：`bc7ef77 [T004] seal stable delivery baseline`
- 目标设备：OnePlus 8T `3f8bbaad`

## 状态总览

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| T005 审核动态状态拓扑 | completed | 资源、CardRun、终端、Web/认证均已对账，只保留两个通用低风险缺口 |
| T006 补全可见资源状态收敛 | completed | 目标并集与连续信号共享快照测试已通过 |
| T007 安装向导即时状态快路径 | completed | 真机诊断中 beginPlan 至 markInstalled 均为 `path=memory` |
| T008 回归与真机验收 | completed | 完整单测、构建、安装/卸载闭环和异常扫描均通过 |

## T005 三问自检

1. 目标是什么：按 `PLAYBOOK.md` T005 对账动态状态链路，不把某个页面的二次点击问题修成页面特判。
2. 完成标准是什么：覆盖资源、CardRun、终端、Web/认证，并只选通用低风险缺口。
3. 依赖是否满足：已满足；稳定基线 `bc7ef77` 已提交，完整单测、APK 构建、OnePlus 8T 冷启动和浏览器 smoke 已通过。

## 当前证据

- `KiteResourceInstallStore` 是资源登记和计划状态拥有者，写入 SQLite 后同步更新共享快照并发布 `StateFlow` 信号。
- `CardRunStore` 发布完整 runs 快照，报告、控制台和资源打开状态从同一列表收敛。
- 可见资源投影目前在有 preferred IDs 时只选择该子集；`StateFlow` 合并连续事件时，其他已变化的可见卡片可能漏过本轮即时校准。
- 安装向导每个 store 信号都进入 `root.post -> 后台线程 -> runOnUiThread`，即便 `cachedResourceCatalog` 已可用。

## 待验证

- [x] 浏览器回调由 loopback/app redirect 原生入口落地，后台持久化、恢复前台校准符合既定边界。
- [x] 终端刷新保持 single-flight/coalescing，资源信号不触发终端重建。
- [x] T006/T007 的纯函数和状态测试。
- [x] OnePlus 8T 资源操作可见状态与 logcat。

## 最终验证证据

- `:app:testDebugUnitTest :app:assembleDebug`：成功。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，构建后大小 `244791034` 字节。
- OnePlus 8T `3f8bbaad`：覆盖安装成功，冷启动进入主界面，无启动诊断拦截。
- OpenCode 获取：资源首页点击后首帧即为“准备中”；进入向导后为“待获取”；点击开始后首帧为“获取中”；15 秒后自动为“已完成”；返回资源首页首帧为“打开”。
- OpenCode 卸载：详情页点击后立即为“卸载中”；完成后同页自动为“获取”；返回资源首页首帧为“获取”。
- `recipe-events.jsonl`：`beginPlan`、`markPlanStepRunning`、`show_wizard`、`hosted`、`markInstalled` 均记录 `path=memory`。
- logcat：无 `FATAL EXCEPTION`、`ANR in com.kite.app` 或 `Input dispatching timed out`。
- 失败态：共享快照与目标并集由 `KiteResourceInstallStoreSignalTest` 和 `MainActivityResourceStateTargetTest` 锁定；本轮未通过断网人为制造真机失败。
