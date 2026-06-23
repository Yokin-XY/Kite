# Kite 卡片运行容器化进度

## 当前阶段
二期：把 PRoot 事件事实源接入卡片/资源 owner 容器。

## 当前原则
所有启动、停止、查询、清理都围绕 owner id。
卡片 owner 使用 `card:<cardInstanceId>`，资源 owner 使用 `resource:<resourceId>`。
终端 owner 使用 `terminal:<sessionId>`，并用 `KF_UNIT_ID=card:<cardInstanceId>` 回指所属卡片实例。
当前不替换 Ubuntu，不重写 PRoot，不引入新容器方案；Kite 先复用 Ubuntu/PRoot，并把 PRoot telemetry 作为运行事实源。

## 目标化计划
总目标：把卡片、资源、终端从 UI 表面提升为 Kite 可登记、可查询、可停止、可复核的 owner 容器；Ubuntu/PRoot 仍是运行底座，PRoot telemetry 是运行事实源。

1. 启动身份：所有进入 Ubuntu/PRoot 的卡片、资源、终端命令都必须带 `KF_RUNTIME_ID/KF_UNIT_ID`。
2. 事实索引：ProotTelemetryStore 用 fork/clone/vfork/exit/live table 生成 owner process index。
3. 健康视图：RuntimeHealth/TaskManager/WorkloadRegistry 消费 owner index，不再把 owner tracee 当未知进程。
4. 停止收束：stop/delete/end 不按 UI 猜 pid，优先按 owner 的 pgid/tracee 收束，再用最终 remaining 复核。
5. Pool 决策：PRoot pool plan 输出 owner 容器压力，后续是否打开多 PRoot capacity executor 以这个事实为门槛。

## 已完成
- 2026-06-22：补齐 CardRun 最小运行单元的 cardInstanceId 语义；CardRunStore 仍复用现有 instanceId 主键，并对外持久化/恢复 cardInstanceId；shell、terminal、Web browser proxy 运行环境都能拿到同一个 card 实例标识。
- 2026-06-22：补齐 detached shell 的最小启动 pidfile；KiteBridgeClient 在启动时把 cardInstanceId/runId/rootPid/processGroupId/systemSessionId/logPath 写入 `/workspace/.kf/system/state/card-runs/<cardInstanceId>/<runId>.pid`，并继续通过现有 progress/report 路径回写 CardRunStore。
- 2026-06-22：收敛统一停止入口的最小实现；MainActivity.stopRecipe 先按 cardInstanceId 查询 CardRunStore 最新状态，再停止 terminal / bridge process binding，并把 stop callback 回写固定到同一个 cardInstanceId。
- 2026-06-22：补齐停止后的最小运行复核；MainActivity.handleStopResultV2 会解析 bridge stop 输出里的 `__kite_stop_remaining`，只有 stop 成功且 remaining 为空才清 binding 并写 Stopped，有残留则保留原状态并把残留 PID 写回 CardRunStore 错误。
- 2026-06-22：补齐 stop 成功后的最小 pidfile 清理；KiteBridgeClient 在 detached run binding 中保留 pidfile path，并在 stopDirectRuns 复核成功后删除对应 `<cardInstanceId>/<runId>.pid`。
- 2026-06-22：补齐 cardInstanceId 查询入口的最小展示；运行管理展开详情会显示 CardRun 标识、runId/rootPid/pgid/sid 执行绑定、terminal/web 表面绑定。
- 2026-06-22：补齐删除卡片后的最小清理；删除配置成功后只移除该卡片已关闭的 CardRun 状态/历史，并按返回的 cardInstanceId 清理残留 pidfile 目录。
- 2026-06-22：补齐异常退出的最小识别；运行管理展开详情会显示正常停止、正常完成、崩溃/异常、残留、未结束，CardRunStore 不再把进程恢复时未完成运行归成正常停止。
- 2026-06-22：完成 OnePlus 8T 真实设备启停闭环验收；通过首页真实启动/停止按钮启动临时 shell 卡片，CardRunStore 登记 `cardInstanceId/runId/rootPid/processGroupId/systemSessionId`，停止后回写 Stopped 并清空 run binding，Android 进程表确认 `rootPid/pgid` 无残留。
- 2026-06-22：修复提交前阻塞；静态验收脚本可通过 `powershell -File` 直接运行，删除活动卡片配置前会先按 cardInstanceId 请求 stop，并中止本次删除。
- 2026-06-23：补齐 PRoot owner 容器化主链路；KiteBridgeClient 在 direct shell 启动时注入 `KF_RUNTIME_ID/KF_UNIT_ID`，ProotTelemetryStore 从 live tracee 表生成 owner process index，RuntimeHealthStore 把 `card:` / `resource:` owner 变成卡片/资源运行根，TaskManagerStore 和 RuntimeWorkloadRegistry 消费同一事实源，KiteBridgeClient 停止路径追加 ProotOwnerProcessTerminator，按 owner 的 pgid/tracee 发信号并用最终 `__kite_stop_remaining` 复核残留，PRoot pool plan 输出 owner 容器数和 owner tracee 数。
- 2026-06-23：补齐终端 owner 边界；CardRun 空白终端和 terminal step 启动环境注入 `terminal:<sessionId>` owner，终端停止入口会后台调用 ProotOwnerProcessTerminator 收束 owner tracee，RuntimeHealthStore 跳过已有 terminal session root 对应的 terminal owner root，避免重复展示。
- 2026-06-23：补齐 CardRun 对 owner 事实源的消费；TaskManagerProcessItem 暴露 RuntimeHealth owner id，运行管理页按 `card:<cardInstanceId>` / `resource:<resourceId>` / `terminal:<sessionId>` 合并 owner root，不再只靠旧 pid binding 猜卡片进程归属。
- 2026-06-23：完成 Card owner 真机闭环复核；新增 debug-gated `runtime_action` 诊断/停止入口，OnePlus 8T 上启动 `kite-owner-telemetry-live` 卡片后，dump diagnostics 可看到 `card:kite-owner-telemetry-live` owner live group、workload root、pool owner 容器计数；通过同一 stop path 停止后 owner 容器数和 tracee 数归零，bridge stop 输出包含 `__kite_owner_stop_owner` 与空 `__kite_stop_remaining`。
- 2026-06-23：完成 Resource owner 真机闭环复核；新增 debug-gated `runtime_action=start_resource_owner_probe`，通过资源安装 recipe 路径启动 `resource:kite.owner.telemetry.probe`，RuntimeHealth/Workload/Authority/Pool 均能看到资源 owner 容器，停止后 owner container/tracee 归零。
- 2026-06-23：完成 Terminal owner 真机闭环复核；修复冷启动读取不到 rotated telemetry 的 owner baseline、同一 terminal step 重复 attach 导致 owner split、以及 host 终端已退出但 PRoot 未落根 tracee exit 的 tombstone 缺口；v5 实测停止后 `terminal:shell-space-main-1782193330714` 只保留历史 ledger，不再留 live owner。

## 正在进行
- 暂无

## 阻塞项
- 暂无

## 下次推荐领取
1. 多 PRoot 容量执行复核：在 capacity executor policy 打开后，确认 owner 容器计数进入 pool plan，再决定是否启动绑定的第二 PRoot runtime。
2. PRoot telemetry 基线清理：处理旧 jsonl skipped-bytes/history 污染和历史 stale owner，让后续验收可以区分历史账本与当前 live owner。
3. Owner stop 审计泛化：把 terminal tombstone 的“host 已确认退出后写回同源 telemetry”策略，评估是否需要扩展到更多非终端 owner stop 场景。

## 会话记录

### 2026-06-22 14:29
- 领取任务：一期任务 1，定义/补齐最小 CardRun 运行单元字段里的 cardInstanceId 语义。
- 领取原因：真实代码已经以 CardRunStore.instanceId 承担卡片实例所有权，但总纲和运行环境需要明确的 cardInstanceId 名称，先补兼容面比新建状态模型更小。
- 涉及入口或状态源：CardRunStore；MainActivity 的 startRecipe / executeShellRecipeStep / executeTerminalRecipeStep / openBrowserRequestInCardRun；KiteBridgeClient 的 extraEnv handoff；KiteBrowserProxyInstaller / KiteLocalServer 的 Web surface 绑定。
- 验收标准：CardRunStore 可查询同一实例的 runId、rootPid、processGroupId、terminalSessionId、status、createdAt、updatedAt，并持久化 cardInstanceId；shell/terminal/Web 启动环境可通过 KITE_CARD_INSTANCE_ID 找回同一实例。
- 明确不做：不重写 stop(cardInstanceId)，不改 Ubuntu/PRoot，不引入新容器方案，不做二期运行目录制度，不改 terminal/runtime surface 内部行为。
- 修改范围：app/src/main/java/com/kite/app/run/CardRunModels.kt；app/src/main/java/com/kite/app/run/CardRunStore.kt；app/src/main/java/com/kite/app/bridge/KiteBrowserProxy.kt；app/src/main/java/com/kite/app/bridge/KiteLocalServer.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`。
- 验证结果：静态检查通过；debug APK 编译通过。
- 完成状态：已完成。
- 剩余问题：stop(cardInstanceId)、pidfile 创建、停止复核仍未统一。
- 下次建议：领取“统一启动入口”的最小 pidfile 创建和 CardRunStore 回写。

### 2026-06-22 14:36
- 领取任务：一期任务 2，统一启动入口里的 detached shell 最小 pidfile 创建和 CardRunStore 回写。
- 领取原因：CardRunStore 已有 cardInstanceId/runId/rootPid/processGroupId 字段和回写路径，下一步最小缺口是 launcher 启动时落一个可查询 pidfile。
- 涉及入口或状态源：MainActivity.executeShellRecipeStep；KiteBridgeClient.runRecipe / executeDetachedShellStep；CardRunStore.update；KiteBrowserProxyInstaller.environment。
- 验收标准：detached shell 启动经 KiteBridgeClient launcher 写入 KITE_CARD_INSTANCE_ID、生成 runId、创建 pidfile、记录 rootPid/processGroupId，并继续回写 CardRunStore。
- 明确不做：不重写 stop(cardInstanceId)，不做停止复核，不引入 `/run/kite/cards/<cardInstanceId>/` 二期目录制度，不改 PRoot 或 kf-runner 二进制。
- 修改范围：app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`jar tf D:\xm\Kite\app\build\outputs\apk\debug\app-debug.apk | Select-String -Pattern 'assets/system/kf-runner-arm64|assets/system/kf-procps-arm64'`；`git diff --check -- ...`。
- 验证结果：静态检查通过；debug APK 编译通过；APK 包含 kf-runner/kf-procps 资产；diff whitespace 检查通过。
- 完成状态：已完成。
- 剩余问题：stop(cardInstanceId)、停止后残留复核、pidfile 清理仍未统一。
- 下次建议：领取“统一停止入口”，用 cardInstanceId 查询 CardRunStore 后停止 terminal / bridge run / process group。

### 2026-06-22 14:44
- 领取任务：一期任务 3，统一停止入口的最小 stop(cardInstanceId) 收口。
- 领取原因：启动侧已经能登记 cardInstanceId/runId/rootPid/processGroupId，下一步必须让 UI 停止动作不再依赖页面局部 previousState 猜测，而是回到 CardRunStore 的实例事实。
- 涉及入口或状态源：MainActivity.stopRecipe / handleStopResultV2；CardRunStore.get(cardInstanceId)；KiteBridgeClient.stopProcessBinding；TerminalRuntimeHost.endSession。
- 验收标准：stopRecipe 先按 cardInstanceId 查询 CardRunStore；停止结果回写同一个 cardInstanceId；terminal 场景如果保留 pid/rootPid/processGroupId/systemSessionId，必须同时请求 bridge 停止对应 process binding。
- 明确不做：不做二期 `/run/kite/cards/<cardInstanceId>/` 目录；不改 Ubuntu/PRoot；不新增容器方案；不做 pidfile 清理；不把完整残留复核制度并入本次。
- 修改范围：app/src/main/java/com/kite/app/MainActivity.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`；代码审核确认 stopProcessBinding 会走已有 process group kill 和 remaining 检查。
- 验证结果：静态检查通过；debug APK 编译通过；diff whitespace 检查通过（仅 Git CRLF 提示）；bridge direct stop 路径包含 kill process group/targets 和 `__kite_stop_remaining` 检查。
- 完成状态：已完成本次最小统一停止入口。
- 剩余问题：停止后复核结果还没有作为 CardRunStore 的明确四条件门槛；pidfile 停止/删除清理仍未做；还缺围绕 cardInstanceId 的查询验收入口。
- 下次建议：领取“运行复核”，把 stop 请求成功、进程组不存在、CardRunStore 清 binding、UI 状态 Stopped 四个条件收成同一个复核流程。

### 2026-06-22 14:49
- 领取任务：一期任务 4，运行复核的最小 remaining 门槛。
- 领取原因：统一停止入口已经把 stop 交给 CardRunStore 实例和 bridge process binding，下一步需要避免只凭 stop 返回就把 UI/CardRunStore 标成 Stopped。
- 涉及入口或状态源：MainActivity.handleStopResultV2；KiteBridgeClient.stopDirectRuns / stopDirectProcesses 已有 `__kite_stop_remaining` 输出；CardRunStore.update。
- 验收标准：stop 请求成功且 `__kite_stop_remaining` 为空时才清 run binding 并写 Stopped；如果 remaining 有 PID，CardRunStore 保留原 run binding 并写入“停止后仍有进程残留”错误；UI 不显示 Stopped。
- 明确不做：不新增 CardRunStore 字段；不做 pidfile 删除；不做卡片删除清理；不引入新的 runtime 探针；不改 Ubuntu/PRoot。
- 修改范围：app/src/main/java/com/kite/app/MainActivity.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`；代码审核确认 bridge stop 已输出并判断 `__kite_stop_remaining`。
- 验证结果：静态检查通过；debug APK 编译通过；diff whitespace 检查通过（仅 Git CRLF 提示）；复核逻辑复用现有 bridge remaining marker。
- 完成状态：已完成本次最小运行复核。
- 剩余问题：pidfile 停止/删除清理仍未做；还缺围绕 cardInstanceId 的查询验收入口；异常退出和残留状态还没有独立展示。
- 下次建议：领取“删除/清理”，停止成功或删除卡片时清理 `/workspace/.kf/system/state/card-runs/<cardInstanceId>/<runId>.pid`。

### 2026-06-22 14:56
- 领取任务：一期删除/清理里的 stop 成功后 pidfile 最小清理。
- 领取原因：detached shell 启动已经写入 pidfile，停止和复核也已经围绕 cardInstanceId；如果 stop 成功后 pidfile 还留着，运行所有权的“可清理”验收仍不完整。
- 涉及入口或状态源：KiteBridgeClient.executeDetachedShellStep / stopDirectRuns；DirectRunBinding；MainActivity.stopRecipeByCardInstanceId / retryStopRequestAfterStableBridge。
- 验收标准：detached run binding 保留 pidfile path；stopDirectRuns 在 `__kite_stop_remaining` 为空后删除该 pidfile；MainActivity 停止调用把 cardInstanceId 传给 bridge，便于进程重启后的派生清理路径。
- 明确不做：不做查询入口；不做删除卡片时清整个目录；不新增 CardRunStore 字段；不引入 `/run/kite/cards/<cardInstanceId>/` 二期目录；不改 Ubuntu/PRoot。
- 修改范围：app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt；app/src/main/java/com/kite/app/MainActivity.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`。
- 验证结果：静态检查通过；debug APK 编译通过；diff whitespace 检查通过（仅 Git CRLF 提示）。
- 完成状态：已完成 stop 成功后的最小 pidfile 清理。
- 剩余问题：删除卡片时清已关闭运行状态/残留 pidfile 目录仍未做；还缺围绕 cardInstanceId 的查询验收入口；异常退出和残留状态还没有独立展示。
- 下次建议：领取“查询入口”，补一个围绕 cardInstanceId 的运行资源查看/调试入口，先让 rootPid/pgid/terminal/Web 归属可验收。

### 2026-06-22 14:59
- 领取任务：一期查询入口，补围绕 cardInstanceId 的运行资源查看入口。
- 领取原因：登记、停止、复核、pidfile 清理都已有最小路径，但用户侧还缺一个能看到 cardInstanceId 名下 rootPid/pgid/terminal/Web 归属的验收入口。
- 涉及入口或状态源：MainActivity.showKiteProcessOverview；runManagementDetails；CardRunStore.runs；TerminalSessionStore；TaskManagerStore。
- 验收标准：运行管理展开卡片后能看到 cardInstanceId、runId、rootPid、pgid、sid、terminalSessionId、web URL 这些归属信息。
- 明确不做：不新建页面；不新增 runtime 查询服务；不新增 CardRunStore 字段；不做删除卡片清理；不做异常退出识别；不改 Ubuntu/PRoot。
- 修改范围：app/src/main/java/com/kite/app/MainActivity.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`。
- 验证结果：静态检查通过；debug APK 编译通过；diff whitespace 检查通过（仅 Git CRLF 提示）。
- 完成状态：已完成最小查询入口。
- 剩余问题：删除卡片时清已关闭运行状态/残留 pidfile 目录仍未做；异常退出和残留状态还没有独立展示；还未做真实设备验收。
- 下次建议：领取“删除卡片清理”，删除卡片时清理已关闭运行状态和残留 pidfile 目录。

### 2026-06-22 15:07
- 领取任务：一期删除卡片清理，删除卡片时清理已关闭运行状态和残留 pidfile 目录。
- 领取原因：查询入口已能看到 cardInstanceId 归属，下一块一期验收是删除卡片后状态和 pidfile 残留可清理。
- 涉及入口或状态源：MainActivity.showDeleteRecipeConfirmSheet；CardRunStore.removeClosedRunStatesForRecipes；KiteBridgeClient.cleanCardRunPidDirs；`/workspace/.kf/system/state/card-runs/<cardInstanceId>/`。
- 验收标准：删除配置成功后只清理该 recipe 下已关闭的 CardRun 当前状态/历史；不删除仍处于运行/停止中等未结束状态；按被移除的 cardInstanceId 派生并清理 pidfile 目录。
- 明确不做：不停止仍在运行的进程；不做异常退出识别；不新建查询服务；不引入 `/run/kite/cards/<cardInstanceId>/` 二期目录；不改 Ubuntu/PRoot。
- 修改范围：app/src/main/java/com/kite/app/MainActivity.kt；app/src/main/java/com/kite/app/run/CardRunStore.kt；app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`；代码审核确认删除入口不会调用 stop，也不会用宽删除清 active run。
- 验证结果：静态检查通过；debug APK 编译通过；diff whitespace 检查通过（仅 Git CRLF 提示）。
- 完成状态：已完成删除卡片后的最小已关闭状态和 pidfile 目录清理。
- 剩余问题：异常退出识别还没有独立区分正常停止、崩溃、残留；还未做真实设备验收。
- 下次建议：领取“异常退出识别”，先复用 CardRunStore/history 和 stop remaining 文案，把正常停止、崩溃、残留的状态边界补清楚。

### 2026-06-22 15:12
- 领取任务：一期异常退出识别，区分正常停止、崩溃/异常、残留。
- 领取原因：启动、停止、查询、删除清理都已有最小链路，剩余一期验收缺口是异常退出不能被看成正常停止。
- 涉及入口或状态源：CardRunStore.normalizedAfterProcessRestore / normalizedHistoryAfterProcessRestore；MainActivity.runManagementOwnershipRows / runManagementExitSummary；handleStopResultV2 已有 `__kite_stop_remaining` 文案。
- 验收标准：运行管理展开详情能看到退出判断；Stopped 显示正常停止，Completed 显示正常完成，Failed/BridgeUnavailable 显示崩溃/异常，`停止后仍有进程残留` 显示残留；进程恢复时未完成运行写入历史为 Failed 并保留异常文案。
- 明确不做：不新增 exitReason 字段；不新增后台扫描/监控服务；不扫全系统残留；不做二期 `/run/kite/cards/<cardInstanceId>/`；不改 Ubuntu/PRoot。
- 修改范围：app/src/main/java/com/kite/app/MainActivity.kt；app/src/main/java/com/kite/app/run/CardRunStore.kt；scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1；docs/card-runtime-ownership-progress.md。
- 验证方式：`& D:\xm\Kite\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- ...`。
- 验证结果：静态检查通过；debug APK 编译通过；最终 diff whitespace 检查通过（仅 Git CRLF 提示）。
- 完成状态：已完成异常退出的最小识别。
- 剩余问题：还未做真实设备验收；异常退出目前是现有状态/文案派生，不支持结构化筛选统计。
- 下次建议：领取“真实设备验收”，启动一张 detached 卡片，运行管理查看 cardInstanceId/runId/rootPid/pgid/退出判断，停止后确认无残留并清 pidfile。

### 2026-06-22 15:24
- 领取任务：真实设备验收，确认卡片启动、登记、停止、残留复核、CardRunStore 回写闭环。
- 领取原因：一期制度能力已经完成静态检查和构建，下一步必须在默认真实设备 OnePlus 8T 上验证真实 UI 路径，不继续凭代码推断。
- 涉及入口或状态源：`/storage/emulated/0/Download/KF/cards` 外部卡片入口；首页卡片启动/停止按钮；MainActivity.stopRecipeByCardInstanceId；KiteBridgeClient/kf-runner shell 执行路径；CardRunStore 的 `kite_card_run_store.xml`；Android 进程表。
- 验收标准：能安装并启动 APK；能通过真实卡片按钮启动 shell 运行；CardRunStore 能看到 `cardInstanceId/runId/rootPid/processGroupId/systemSessionId`；停止后 CardRunStore 写 Stopped 并清 run binding；`ps` 查不到原 `rootPid/pgid` 相关进程；`/workspace/.kf/system/state/card-runs` 无残留 pidfile。
- 明确不做：不修改 Ubuntu/PRoot；不引入新容器方案；不启动二期 `/run/kite/cards/<cardInstanceId>/`；不把资源页/任务页 UI 做新改造；不保留临时测试卡。
- 修改范围：docs/card-runtime-ownership-progress.md；设备侧临时创建并删除 `/storage/emulated/0/Download/KF/cards/kite-process-container-test.json`。
- 验证方式：`adb -s 3f8bbaad install -r app/build/outputs/apk/debug/app-debug.apk`；`adb -s 3f8bbaad shell am start -n com.kite.app/.MainActivity`；真实 UI 点击测试卡 `启动`/`停止`；`adb -s 3f8bbaad shell run-as com.kite.app cat shared_prefs/kite_card_run_store.xml`；`adb -s 3f8bbaad shell ps -A -o USER,PID,PPID,PGID,NAME,ARGS`；`adb -s 3f8bbaad shell run-as com.kite.app find files/runtime/shared/ubuntu-main/.kf -maxdepth 5 -name '*run_db04e6a4fc0e4d88b188283ff1f6cf98*' -o -name '*.pid'`。
- 验证结果：APK 安装成功并启动；首页显示测试卡运行中和停止按钮；启动时 CardRunStore 写入 `cardInstanceId=kite-process-container-test`、`runId=run_db04e6a4fc0e4d88b188283ff1f6cf98`、`rootPid=10815`、`processGroupId=10815`、`systemSessionId=10815`、`lastMeaningfulOutput=KITE_PROCESS_TEST_START`；停止后首页变为 `已停止 1`，CardRunStore 当前运行写 `status=Stopped/surface=Summary` 且 `runId/pid/rootPid/processGroupId/systemSessionId` 清空，`lastMeaningfulOutput=已停止，未发现进程残留`；`ps` 对 `10815/10812/10809/sleep 300/KITE_PROCESS_TEST_START` 无命中；`.kf/system/state` 下未发现该 runId 的 pidfile 或 `card-runs` 残留；临时测试卡 JSON 已删除。
- 完成状态：已完成真实设备启停闭环验收。
- 剩余问题：本次临时 shell 卡实测走 kf-runner shell 绑定路径，未产生 detached pidfile；detached pidfile 的创建/清理仍以代码审核和静态检查覆盖，如需实物证据，下次单独构造 detached launch 卡片复核。底部“资源”tab 在当前实际 app 中是资源页，不是任务管理器入口，本次不继续扩展 UI 查询路径。
- 下次建议：领取“一期收口复核”，按最终验收逐项整理仍缺的真实设备证据；如优先补证据，则领取“detached pidfile 专项实机复核”。

### 2026-06-22 15:38
- 领取任务：提交前阻塞修复：静态验收脚本编码和删除活动卡片的 stop 边界。
- 领取原因：静态验收门必须能用 `powershell -File` 直接运行；删除配置不能绕过仍在运行的 cardInstanceId 运行单元。
- 涉及入口或状态源：`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`MainActivity.showDeleteRecipeConfirmSheet`；`CardRunStore.currentForRecipe`；`stopRecipeByCardInstanceId`；`CardRunStore.removeClosedRunStatesForRecipes`；`KiteBridgeClient.cleanCardRunPidDirs`。
- 验收标准：静态脚本在 Windows PowerShell 下不因中文断言字符串解析失败；删除活动卡片时先按 cardInstanceId 调用 stop，并不在同一次点击继续删除 recipe；已关闭卡片删除仍只清 closed run state 和 pidfile 目录。
- 明确不做：不做 RuntimeHealthStore 级完整运行态复核；不做一键异步 stop 后自动删除队列；不改 Ubuntu/PRoot；不引入 `/run/kite/cards/<cardInstanceId>/`。
- 修改范围：`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`app/src/main/java/com/kite/app/MainActivity.kt`；`docs/card-runtime-ownership-progress.md`。
- 验证方式：`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat :app:assembleDebug --console=plain`；`git diff --check -- app/src/main/java/com/kite/app/MainActivity.kt scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`。
- 验证结果：静态验收脚本通过；debug APK 编译通过；diff whitespace 检查通过，仅有 Git CRLF 提示。
- 完成状态：已完成提交前两个硬阻塞修复。
- 剩余问题：停止复核仍是 bridge stop 脚本里的 `/proc`/`kill -0` 即时检查，不是 RuntimeHealthStore 级运行态复核；删除活动卡片采用“先停并中止本次删除”，停止完成后需要再次删除。
- 下次建议：领取“一期收口复核”；如果要补强完整统一管理，再领取 RuntimeHealthStore 级运行态复核方案。

### 2026-06-23
- 领取任务：目标化实施“卡片/资源作为 PRoot owner 容器”的完整主链路。
- 领取原因：单纯围绕 `cardInstanceId` 和 pidfile 的一期方案仍偏 UI/进程绑定，无法证明 Ubuntu 内 fork/clone/exec 后的子进程都属于同一个 owner；必须从 PRoot telemetry 事实源接入。
- 涉及入口或状态源：KiteBridgeClient direct shell env；ProotTelemetryStore live table / owner process index；RuntimeHealthStore roots；RuntimeWorkloadRegistry；TaskManagerStore；RuntimeProotPoolPlanDryRun；ProotOwnerProcessTerminator；MainActivity stop residue parsing；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`。
- 验收标准：启动时带 `KF_RUNTIME_ID/KF_UNIT_ID`；PRoot telemetry snapshot 暴露 owner process index；RuntimeHealth 生成 CARD/RESOURCE owner roots 并排除 owner tracee 进入 unattributed；TaskManager 显示卡片/资源容器；终端启动生成 `terminal:<sessionId>` owner，结束终端会按 owner 收束；workload/pool 计划能看到 owner 容器压力；停止时先走旧 binding，再按 owner pgid/tracee 收束，并以最终 `__kite_stop_remaining` 判断是否 Stopped。
- 明确不做：不替换 Ubuntu 24.04；不重写 PRoot；不自动打开多 PRoot capacity executor policy；不把卡片/资源 owner 混入泛化压力回收自动杀；不做真实设备验收。
- 修改范围：`app/src/main/java/com/kite/app/bridge/KiteBridgeClient.kt`；`app/src/main/java/com/kite/app/MainActivity.kt`；`app/src/main/kotlin/com/kftest/app/foundation/terminal/TerminalSessionController.kt`；`app/src/main/kotlin/com/kftest/app/foundation/runtime/ProotTelemetryStore.kt`；`ProotOwnerProcessTerminator.kt`；`RuntimeHealthStore.kt`；`RuntimeWorkloadRegistry.kt`；`TaskManagerStore.kt`；`RuntimeProotPoolPlanDryRun.kt`；`RuntimeReclaimer.kt`；`RuntimeStateReconciler.kt`；`RuntimeLifecycleAuthorityMatrix.kt`；`RuntimeProcessStopReconciliation.kt`；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；本文件。
- 验证方式：`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat assembleDebug`；`git diff --check`。
- 验证结果：静态验收脚本通过；debug APK 编译通过；diff whitespace 检查通过，仅有 Git CRLF 提示。
- 完成状态：已完成代码主链路，未做真实设备 owner telemetry 验收。
- 剩余问题：Web 表面本身不是独立 Ubuntu 长驻进程，当前 owner 只覆盖 Ubuntu 侧命令/终端/open-url helper；多 PRoot capacity executor 是否实际启动仍受现有 policy/binding gate 控制。
- 下次建议：先做卡片/资源/终端 owner 实机复核，再决定是否打开 capacity executor policy 做第二 PRoot runtime 实验。

### 2026-06-23 CardRun owner 消费补强
- 领取任务：让 CardRun 运行管理页消费 TaskManager/RuntimeHealth 的 owner 事实源。
- 领取原因：上一轮 RuntimeHealth/TaskManager 已能看到 PRoot owner root，但运行管理分组仍主要按旧 pid/rootPid binding 匹配，CardRun 层没有直接吃同一事实源。
- 修改范围：`app/src/main/java/com/kite/app/MainActivity.kt`；`TaskManagerStore.kt`；`TaskManagerFragment.kt`；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；本文件。
- 验收标准：TaskManagerProcessItem 携带 raw `runtimeOwnerId`；运行管理页按 owner id 把卡片/资源/终端 owner root 合并进对应 CardRun；静态脚本阻止回退到只按 pid 匹配。
- 明确不做：不新增 CardRunStore 字段；不复制 RuntimeHealth 快照进 CardRunStore；不新增扫描。

### 2026-06-23 Card owner 真机复核与自动化入口
- 领取任务：把 PRoot owner 容器方案整理成可执行目标，并完成 Card owner 实机闭环验证。
- 领取原因：最高效完整路线不是继续补 UI pid 绑定，而是让 Kite 通过 PRoot telemetry owner index 管理 Ubuntu 内部 tracee；这条路线必须用真实卡片启动、dump、stop、再 dump 证明。
- 修改范围：`app/src/main/java/com/kite/app/MainActivity.kt`；`RuntimeAutomationActions.kt`；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；本文件。
- 验收标准：`runtime_action=dump_diagnostics` 必须走真实 MainActivity launcher path，并在导出前刷新 RuntimeHealth；`runtime_action=stop_card_run` 必须复用 `stopRecipeByCardInstanceId` 产品停止路径；实机启动卡片后能看到 `card:<cardInstanceId>` owner group，停止后 owner container/tracee 计数归零。
- 验证方式：`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`git diff --check`；`.\gradlew.bat assembleDebug --console=plain`；`adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`；OnePlus 8T 上通过 CardRunActivity 启动临时 shell 卡片，再用 MainActivity `runtime_action` dump/stop/dump。
- 验证结果：静态检查通过；diff whitespace 检查通过，仅有 Git CRLF 提示；debug APK 编译通过并安装成功；启动 `kite-owner-telemetry-live` 后，`runtime-pressure.env` 出现 `proot_pool_plan_owner_container_count=1`、`proot_pool_plan_owner_container_tracee_count=2`、`workload_2_id=CARD:card:kite-owner-telemetry-live`；停止后 owner container/tracee 均为 `0`，bridge 输出 `__kite_owner_stop_owner:card:kite-owner-telemetry-live` 且 `__kite_stop_remaining:,`。
- 完成状态：Card owner 真机闭环已完成；后续已继续补资源 owner 和终端 owner 真机复核，多 PRoot capacity executor 仍需单独验证。
- 剩余问题：设备侧 telemetry 仍存在旧 jsonl skipped-bytes/history 污染，需要单独清理基线；这不影响本次 live owner group 启停归零证据。

### 2026-06-23 Resource owner 真机复核
- 领取任务：验证资源卡片是否能作为独立 PRoot owner 容器进入 RuntimeHealth/Workload/Pool 事实链。
- 领取原因：Card owner 已证明卡片实例路径可行，但资源安装 recipe 必须保留 `ownerKind=resource`，否则 startRecipe 会把资源 probe 覆盖回 card owner。
- 修改范围：`app/src/main/java/com/kite/app/MainActivity.kt`；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；本文件。
- 验收标准：`runtime_action=start_resource_owner_probe` 能启动 `resource:<resourceId>` owner；PRoot telemetry owner index、RuntimeHealth、workload、authority matrix、pool plan 均能看到资源容器；停止后 owner container/tracee 清空。
- 验证方式：`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；`.\gradlew.bat assembleDebug --console=plain`；`adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`；OnePlus 8T 上用 `runtime_action=start_resource_owner_probe` 启动 `kite.owner.telemetry.probe`，dump/stop/dump。
- 验证结果：启动后 `runtime-pressure.env` 出现 `proot_live_table_entry_*_kf_runtime_id=resource:kite.owner.telemetry.probe`、`workload_2_id=RESOURCE:resource:kite.owner.telemetry.probe`、`lifecycle_authority_matrix_entry_1_root_key=RESOURCE:resource:kite.owner.telemetry.probe`、`proot_pool_plan_owner_container_count=1`、`proot_pool_plan_owner_container_tracee_count=2`；停止后 owner container/tracee 均为 `0`，日志输出 `已停止，未发现进程残留`。
- 完成状态：Resource owner 真机闭环已完成。
- 剩余问题：资源 probe 是 debug-gated 自动化入口，不代表完整 install/uninstall 产品交互已覆盖；多 PRoot capacity executor 仍未打开。

### 2026-06-23 Terminal owner 真机复核与 stop 缺口修复
- 领取任务：验证 terminal step 下 Ubuntu bash/sleep 是否全归属同一 terminal owner，并在结束终端后从 live owner index 清空。
- 领取原因：终端不是 direct shell 卡片路径，真实执行要经过 terminal session attach；这里最容易出现 owner split、rotated telemetry 读不到、host 关闭但 PRoot 根 tracee 无 exit 事件三个缺口。
- 修改范围：`app/src/main/kotlin/com/kftest/app/foundation/runtime/ProotTelemetryStore.kt`；`app/src/main/kotlin/com/kftest/app/foundation/runtime/ProotOwnerProcessTerminator.kt`；`app/src/main/kotlin/com/kftest/app/foundation/terminal/TerminalSessionController.kt`；`scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1`；本文件。
- 验收标准：terminal step 只 attach 一个 PRoot session；`sleep 600` 继承 `terminal:<sessionId>` 和 `card:<cardInstanceId>`；停止后 `__kite_stop_remaining` 最终为空；若 host 已确认退出但 native PRoot 没写根 tracee exit，必须向同一 telemetry JSONL 写入 owner tombstone，而不是靠扫描或 UI 状态遮盖。
- 验证方式：安装 debug APK 后在 OnePlus 8T 通过 CardRunActivity 启动临时 terminal 卡 `kite-terminal-owner-telemetry-live-v5`，等待 `sleep 600`，dump/stop/dump，并检查 `terminal-actions.log`、`kf-proot-telemetry.jsonl`、`runtime-pressure.env`。
- 验证结果：v5 session 为 `shell-space-main-1782193330714`；启动后 raw telemetry 中 `/usr/bin/bash` 与 `sleep 600` 均带 `kfRuntimeId=terminal:shell-space-main-1782193330714`、`kfUnitId=card:kite-terminal-owner-telemetry-live-v5`；停止后 terminal log 出现 `retire-proot-owner-tracees`，`retiredTraceePids=29389`，`previousLiveTracees=2`、`observedLiveTracees=1`，随后 owner stop 输出 `__kite_stop_remaining:` 为空；raw telemetry 写入 `sourceHook=kite_owner_retire...` 的 `TraceeExited` tombstone；runtime-pressure 对 v5 只剩历史 `runtime_resource_event_ledger`，不再有 `proot_live_table_entry`。
- 完成状态：Terminal owner 真机闭环已完成。
- 剩余问题：设备上仍有旧 v4 验证留下的 stale 历史 owner，需要单独 telemetry 基线清理；owner tombstone 当前只在 terminal host 已确认退出后启用，是否扩展到其他 owner stop 场景待下一阶段审计。
