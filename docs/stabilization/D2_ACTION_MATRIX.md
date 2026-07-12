# D2 动作编排验收矩阵

## 固定动作链

```text
页面提交意图
-> KiteRecipeActionCoordinator 生成轻量计划
-> MainActivity 动作入口分发到既有执行者
-> CardRunStore / KiteResourceInstallStore 写入事实
-> 页面消费状态投影
```

动作入口不得直接执行 PRoot、终端或网络重活，也不得自行复制运行事实。

## 卡片动作

| 来源 | 用户动作 | 统一意图 | 计划结果 |
| --- | --- | --- | --- |
| 首页卡片 | 启动/重试/停止 | Primary | LaunchTask / Execute / Stop |
| 编辑页 | 启动/重新启动 | Start | Execute；已有实例时 OpenRun |
| 编辑页 | 打开 | Open | OpenRun |
| 编辑页 | 停止 | Stop | Stop |
| 任意来源 | 重复启动中动作 | Primary / Start | Ignored(busy) |
| 任意来源 | Ubuntu 未就绪 | Primary / Start | RuntimeRequired |

状态拥有者仍是 `CardRunStore`；协调器只返回计划，不创建实例、不写状态、不执行命令。

## 资源动作

| 动作 | 当前状态 | 目标统一入口 | 状态 |
| --- | --- | --- | --- |
| 获取/安装 | 待获取、失败 | ResourceAction Intake | done |
| 打开 | 已安装、未运行 | ResourceAction Intake | done |
| 停止 | 运行中 | ResourceAction Intake | done |
| 卸载 | 已安装、失败残留 | ResourceAction Intake | done |
| 取消 | 获取中、失败残留 | ResourceAction Intake | done |
| 继续计划/完成 | 安装计划中 | InstallPlan Action Intake | done |

## 自动化证据

- `KiteRecipeActionCoordinatorTest`：忙碌去重、运行中主动作、已有实例、运行环境阻塞、独立任务和路由委托。
- `KiteActionRouterTest`：命名动作、停止兜底、缺失与空动作。
- `KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`：首页和编辑页不得绕过共享动作入口。
- `KiteResourceActionCoordinatorTest`：资源投影标签与恢复安装向导统一为稳定意图。
- `KiteInstallPlanActionCoordinatorTest`：运行、卸载、失败、待获取和完成五类向导动作。
