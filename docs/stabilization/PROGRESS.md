# Kite 主线稳定化进度

最后更新：2026-07-12

## 当前恢复指针

```text
阶段：S1 基线与行为合同
状态：done
当前任务：S1 已封口，等待新的会话领取 S2 导航与返回
代码分支：main
代码策略：会话分支，Git 单主线，阶段性本地提交
```

## 阶段总览

| 阶段 | 状态 | 当前结论 |
| --- | --- | --- |
| S1 基线与行为合同 | done | 静态检查、动作路由测试、全量单测和 Debug 构建通过 |
| S2 导航与返回 | ready | 从统一导航入口和返回合同审计开始 |
| S3 动作编排 | pending | 等待 S2 |
| S4 状态投影 | pending | 等待 S3 |
| S5 显示面与生命周期 | pending | 等待 S4 |
| S6 模块化与扩展 | pending | 等待 S5 |

## S1 启动记录

目标：让后续每个结构调整都有可信的自动回归基础，而不是依赖源码字符串或人工记忆。

完成标准：引用 `PLAYBOOK.md` 的 S1 验收清单，不降低标准。

依赖检查：正式版本 `v0.0.1` 和本地 `main@cc70520` 已存在；S1 无前置代码阶段。

已知证据：

- `MainActivity.kt` 约 2.1 万行，导航、动作、状态投影和生命周期仍高度集中。
- `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1` 当前报告 11 项失败。
- 核对代码后，资源安装 Store 的失败项实际都已调用 `emitSignal`。
- `stopRecipe` 已委托 `stopRecipeByCardInstanceId`，失败来自脚本要求旧的精确调用文本。

## S1 完成记录

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

## S2 接手入口

1. 读取 `PLAYBOOK.md` 的固定原则和 S2 定义。
2. 审计所有 `currentScreen = Screen.*`、`show*()`、顶部返回按钮和系统 back 入口。
3. 先形成导航目标与返回合同测试，再建立统一导航入口。
4. 不迁移资源状态机，不改浏览器认证协议，不修改运行事实 Store。
5. 每形成一个可构建、可回退的小段立即本地提交。
