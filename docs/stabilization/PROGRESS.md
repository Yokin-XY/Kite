# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：D1 导航与返回规则统一
状态：in_progress
当前任务：统一系统 back、顶部返回、Screen 登记和恢复入口
代码分支：main
代码策略：会话分支，Git 单主线，阶段性本地提交
```

## 方向总览

| 方向 | 状态 | 当前结论 |
| --- | --- | --- |
| P0 公共行为安全网 | done | 静态检查、动作路由测试、全量单测和 Debug 构建通过 |
| D1 导航与返回 | in_progress | 本会话专属，正在建立统一目标与返回合同 |
| D2 动作编排 | pending | 由其他会话领取 |
| D3 状态投影 | pending | 由其他会话领取 |
| D4 生命周期和资源预算 | pending | 由其他会话领取 |
| D5 功能模块与扩展点 | pending | 由其他会话领取 |

## P0 公共安全网记录

目标：让后续每个结构调整都有可信的自动回归基础，而不是依赖源码字符串或人工记忆。

完成标准：修复验证误报、补共享动作路由测试，并通过全量单测和 Debug 构建。

依赖检查：正式版本 `v0.0.1` 和本地 `main@cc70520` 已存在；P0 无前置方向任务。

已知证据：

- `MainActivity.kt` 约 2.1 万行，导航、动作、状态投影和生命周期仍高度集中。
- `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1` 当前报告 11 项失败。
- 核对代码后，资源安装 Store 的失败项实际都已调用 `emitSignal`。
- `stopRecipe` 已委托 `stopRecipeByCardInstanceId`，失败来自脚本要求旧的精确调用文本。

## P0 完成记录

实现：

- 新增稳定化三件套，固定会话分支、代码单主线和阶段提交边界。
- 静态检查新增成员函数体提取，资源信号检查不再锁死调用参数排版。
- 停止委托检查改为验证入口和 `cardInstanceId`，允许合法增加调用参数。
- 新增 5 条 `KiteActionRouter` 合同测试，覆盖标准停止兜底、显式停止动作、
  命名动作归一化、缺失动作和空动作。

提交：

- `e8c6b7e [S1] establish stabilization handoff baseline`
- `174321b [S1] make runtime lane checks semantic`
- `bcff4b8 [S1] cover shared action routing contracts`

验证：

- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- 动作路由、资源安装信号、资源投影、MainActivity 路由目标测试：通过。
- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 本阶段没有用户可见行为改动，因此未安装真机；S2 恢复 OnePlus 8T 真机验收。

## D1 当前动作

1. 审计所有 `currentScreen = Screen.*`、`show*()`、顶部返回、系统 back 和恢复入口。
2. 建立导航目标、父子关系和返回策略矩阵。
3. 先写纯合同与测试，再让现有入口渐进接入。
4. 不改页面视觉，不改动作执行、资源 Store、浏览器认证和运行生命周期。
5. 每形成一个可构建、可回退的小段立即本地提交。

## D1 执行记录

### 当前节点三问

- 目标是什么：让 `MainActivity` 的系统 back、顶部返回、Screen 登记和状态恢复统一经过 D1 导航合同。
- 完成标准是什么：父页面、上下文、系统、CardRun 和恢复边界有自动化证据；全量单测、Debug 构建和 OnePlus 8T 验收通过。
- 依赖是否满足：P0 公共安全网已完成，`Destination + BackPolicy + RestorePolicy` 合同及纯测试已提交，依赖满足。

边界：不改页面视觉，不修改资源或运行状态事实，不调整浏览器认证协议，不改变任务生命周期。

### 导航合同与矩阵

- 17 个 `currentScreen` 写入点已盘点，共覆盖 16 个 Screen。
- 返回行为归为系统、父页面、上下文、CardRun 任务四种合同。
- Web 历史和终端详情定义为 Activity 合同前的显示面优先消费者。
- `ScreenRouter` 现在拥有完整 Destination、BackPolicy 和 RestorePolicy 表。
- 新增 7 条纯合同测试，目标完整性、父子关系、上下文、恢复和委托全部通过。

下一步：把 MainActivity 的系统 back、顶部返回、Screen 登记和状态恢复接入同一合同。

### Activity 与显示面返回入口接入

- `MainActivity` 已改用生命周期感知的 `OnBackPressedDispatcher`，统一解析 Web、CardRun、上下文、父页面和系统返回。
- 17 个 Screen 写入点全部经过 `enterScreen`，顶部返回和底部主导航不再各自维护返回目标。
- Recipe 原始 JSON、资源更多/原始 JSON、历史详情和编辑草稿保留上下文返回动作。
- 终端详情顶部返回改为提交统一 back 请求，由 Fragment 自己的回调优先消费。
- 状态恢复由 `RestorePolicy` 驱动，保留原有白名单和缺少参数时不恢复的边界。
- `MainActivityScreenRoutingTest` 新增资源搜索、资源详情、进程管理和恢复边界覆盖。
- 静态检查禁止直接写 `currentScreen = Screen.*` 和恢复旧 `onBackPressed()` 分支。

验证：

- `ScreenRouterContractTest + MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。
- `git diff --check`：通过，仅有既存换行符提示。

下一步：补 Web 历史和终端详情优先消费的自动化证据，再做全量构建与 OnePlus 8T 验收。

### 显示面优先消费证据

- 新增 WebView back 跟踪测试：工作台存在网页历史时只调用 `goBack()`，Screen 保持 Workbench。
- 终端详情回调与顶部按钮加入静态合同护栏，分别锁定“详情回列表”和“提交统一 dispatcher”。
- 曾尝试用 Robolectric 挂载完整 `TerminalFragment`；断言通过，但会启动真实终端运行时并留下临时文件占用，因此撤销该重型测试，改由 OnePlus 8T 完成行为验收。

验证：

- `MainActivityScreenRoutingTest`：`BUILD SUCCESSFUL`。
- `scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：通过。

下一步：运行全量单测和 Debug 构建，随后在 OnePlus 8T 验收返回矩阵。
