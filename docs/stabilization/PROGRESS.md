# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
方向：D1 导航与返回规则统一
状态：in_progress
当前任务：Destination + BackPolicy 合同已建立，准备接入 Activity 返回入口
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

### 导航合同与矩阵

- 17 个 `currentScreen` 写入点已盘点，共覆盖 16 个 Screen。
- 返回行为归为系统、父页面、上下文、CardRun 任务四种合同。
- Web 历史和终端详情定义为 Activity 合同前的显示面优先消费者。
- `ScreenRouter` 现在拥有完整 Destination、BackPolicy 和 RestorePolicy 表。
- 新增 7 条纯合同测试，目标完整性、父子关系、上下文、恢复和委托全部通过。

下一步：把 MainActivity 的系统 back、顶部返回、Screen 登记和状态恢复接入同一合同。
