# Kite 运行车道执行编排 2026-06-16

本文件用于把 `KITE_RUNTIME_LANES.md` 落到后续实际修 bug 的执行顺序。

核心判断：

```text
实时刷新和不卡顿不冲突。
冲突来自热事件进入整页重建入口，或 UI render 顺手做冷数据探测。
```

后续每个 runtime bug 都必须先归车道，再改对应最小模块。不能用删功能、
恢复整页重绘、改安装脚本、改 SH 流式机制来绕开真正问题。

## 一、模块职责

### 1. Resource Static Snapshot

负责：

- 资源 catalog、manifest、workspace/toolchain 探测。
- 资源卡名称、图标、来源、静态展示信息。
- installed / failed / busy 的轻量展示快照。

正确编排：

```text
install / uninstall / onResume / 手动刷新
-> mark snapshot dirty
-> 后台构建 snapshot
-> UI 只读最新 snapshot
```

不能做：

- 在资源卡 row render 中 `File.exists` / `listFiles` / health / DB 全量查询。
- 在安装向导每一帧重新跑 `resourceCatalog(forceRefresh = true)`。
- 为了让资源标签新鲜，每秒刷新资源页。

### 2. Resource Install Plan

负责：

- `KiteResourceInstallStore`
- registry 状态：installing / installed / failed / uninstalling。
- plan step：pending / running / done / failed / blocked。
- 安装向导顶部状态、队列行状态的数据事实。

正确编排：

```text
markInstalling / markInstalled / markFailed / plan step change
-> store revision/signal
-> 当前向导可见：更新顶部 + 受影响队列行
-> 资源列表可见：标记 snapshot dirty，后台刷新
```

不能做：

- 队列行变化后重建整个资源页。
- 向导 render 时同步 `planSnapshot()` / `registrySnapshot()` 作为 live 机制。
- 修 UI 实时性时改安装完成判定。

### 3. Card Run State

负责：

- `CardRunStore`
- run instance identity。
- status、surface、startedAt、endedAt。
- terminalSessionId、runId、nextActionUrl。
- shellReportText。

正确编排：

```text
bridge / terminal / web / install event
-> CardRunStore.update(...)
-> visible consumer 根据 instanceId 局部更新
```

不能做：

- 新建第二套 SH 报告数据源。
- 把 runtime 状态写回 card JSON / resource manifest。
- 为一个页面复制一份本地 run 状态。

### 4. SH Report Binding

负责：

- 当前报告页 output TextView。
- 当前报告页 elapsed TextView。
- report page 与 run instance 的绑定。

正确编排：

```text
handleShellProgress
-> CardRunStore.update(shellReportText)
-> 如果当前报告页 instanceId 匹配，只更新 output TextView

visible report ticker
-> 只更新 elapsed TextView
```

不能做：

- progress 调 `showCardRunSurface(...)`。
- elapsed 每秒重建报告页。
- 还没证明 progress 不到，就改 SH 执行/流式机制。

### 5. Install Wizard Binding

负责：

- 当前安装向导顶部文案/进度。
- 队列行状态 badge。
- 队列行 elapsed。
- 队列行打开报告/终端/Web 的按钮可见性。

正确编排：

```text
install store signal
-> 当前 wizard context 匹配
-> 读取轻量 plan/registry snapshot
-> diff/apply 顶部和行

visible wizard ticker
-> running 行 elapsed 文本
```

不能做：

- 每秒或每个 progress 重新 `resourceInstallWizardContent()`。
- 通过 `renderResourceInstallWizardFor(...)` 重建整页来维持 live。
- 让队列行状态依赖用户返回/重新进入。

### 6. Terminal Runtime

负责：

- terminal session 创建、绑定、关闭。
- terminal 预输入/预设 command 注入。
- `PendingTerminalFlow`。
- terminal transcript / auth link 观察。

正确编排：

```text
terminal step
-> create session
-> CardRunStore 记录 terminalSessionId
-> enqueue/send preset input
-> terminal runtime/log 证明 command sent
```

auth link 应该是：

```text
terminal transcript change
-> 后台读取尾部或 runtime 缓存 auth url
-> UI topbar 只读 cached auth url
```

不能做：

- 因为 UI 卡顿删掉 terminal 预输入。
- 让预输入依赖 `showCardRunSurface(...)` 是否及时执行。
- UI tick 中 `File.readText()` 读整个 transcript。

### 7. Web Surface

负责：

- WebView 生命周期。
- open_web route。
- auth/browser route。
- `nextActionUrl`。

正确编排：

```text
explicit web route/action
-> CardRunStore.nextActionUrl / surface Web
-> WebView only loads when url/source changes
```

不能做：

- 资源状态、报告输出、安装队列变化导致 WebView reload。
- 修资源/报告实时性时清掉 `nextActionUrl`。

### 8. UI Binding

负责：

- 当前可见控件。
- 局部 updater handle。
- scroll position。
- visible-only elapsed ticker。

正确编排：

```text
store is truth
view binding is projection
hidden page does not tick
visible page only updates changed labels/rows/buttons
```

不能做：

- UI binding 做 DB / File / health。
- store 已更新但靠整页重建解决可见同步。

## 二、当前不符合点和归属

### P0-A：SH progress 仍然可能整页重建

位置：

- `MainActivity.handleShellProgress(...)`
- `MainActivity.maybeRenderShellProgress(...)`

当前链路：

```text
progress
-> CardRunStore.update(shellReportText)
-> maybeRenderShellProgress
-> showCardRunSurface / showConsole / renderResourceInstallWizardFor
```

违反车道：

- SH Report Binding。
- UI Binding。

最小修复：

- 保留 progress -> `CardRunStore.update(...)`。
- 删除 progress 到整页重建的 live 依赖。
- 建立当前报告页 `instanceId` 绑定，匹配才更新 output TextView。
- elapsed 单独可见 ticker，只改 label。

禁止误修：

- 不改 `bridgeClient.runRecipe`。
- 不改 SH 流式读取。
- 不恢复每秒 `showCardRunSurface(...)`。

### P0-B：安装向导 render 同步读取 catalog / plan / registry

位置：

- `MainActivity.resourceInstallWizardContent(...)`
- `MainActivity.resourceInstallWizardStepRow(...)`
- `MainActivity.renderResourceInstallWizardFor(...)`

当前链路：

```text
render wizard
-> resourceCatalog(forceRefresh = false)
-> planSnapshot()
-> registrySnapshot(planIds)
-> build all rows
```

违反车道：

- Resource Static Snapshot。
- Resource Install Plan。
- Install Wizard Binding。

最小修复：

- `KiteResourceInstallStore` 先补 revision/signal。
- 向导打开时 seed render 一次。
- signal 到达时只更新顶部/按钮/队列行。
- catalog 只用已缓存的资源元数据，不在 live render 中重探测。

禁止误修：

- 不改安装完成判定。
- 不改 Hermes WebUI 安装脚本/manifest。
- 不把向导队列状态复制到独立本地数据源。

### P0-C：Resource install store 没有 live signal 出口

位置：

- `KiteResourceInstallStore`

当前问题：

```text
registry/plan 已变化
-> 没有统一 revision/signal
-> UI 只能主动重建或轮询
```

违反车道：

- Resource Install Plan。

最小修复：

- 增加 `KiteResourceInstallSignal(revision, reason, resourceId, targetResourceId)`。
- 写操作后递增 revision 并 emit。
- UI 收 signal 后按当前 screen 决定局部更新或 snapshot dirty。

signal 必须覆盖：

- `markInstalling`
- `markInstalled`
- `markFailed`
- `markUninstalling`
- `clear`
- `beginPlan`
- `markPlanStepRunning`
- `advancePlanAfter`
- `failPlanAt`
- `clearPlan`

### P0-D：terminal auth link watcher 在 UI tick 读 transcript

位置：

- `MainActivity.startTerminalAuthorizationLinkWatcher(...)`
- `MainActivity.terminalAuthorizationUrl(...)`
- `MainActivity.cardRunTopBar(...)`

当前链路：

```text
root.postDelayed UI tick
-> TerminalRuntimeRegistry.snapshot()
-> File(transcriptPath).readText()
-> showCardRunSurface(...)
```

违反车道：

- Terminal Runtime。
- UI Binding。

最小修复：

- transcript 读取放后台，只读尾部或缓存 auth-url snapshot。
- UI topbar 只读 cached auth-url。
- 发现 auth-url 后只更新 topbar/action 文案，不整页重建。

禁止误修：

- 不碰 terminal 预输入。
- 不把“打开慢”和“命令没发送”混成同一修法。

## 三、第二批不符合点

### P1-A：资源管理页 render 强制 catalog / DB snapshot

位置：

- `MainActivity.showResourceManage(...)`

当前问题：

```text
open/manage refresh
-> resourceCatalog(forceRefresh = true)
-> planSnapshot()
-> registrySnapshot()
-> build whole ScrollView
```

最小修复：

- 管理页 payload 后台构建。
- UI 初始显示 cached/empty state。
- plan signal 只刷新安装任务块。
- catalog dirty 后只刷新已安装资源块。

### P1-B：resourceCatalog 混合 UI snapshot、DB、normalize、workspace probe

位置：

- `MainActivity.resourceCatalog(...)`
- `ToolchainPackInstaller.refreshState(...)`
- `toolchainWorkspaceSnapshot(...)`

当前问题：

```text
resourceCatalog
-> registrySnapshot
-> normalizeStaleResourceState
-> ToolchainPackInstaller.refreshState on background
-> workspace File.exists probe
```

最小修复：

- 拆出 `resourceCatalogForUiRender()`：只返回已构建 cache。
- stale normalize 进入后台 reconcile。
- workspace probe 用 TTL 后台刷新，不随 render 触发。

### P1-C：首页卡运行态靠 showConsole 热刷新

位置：

- `MainActivity.setRuntimeState(...)`
- `MainActivity.maybeRefreshConsoleAfterRuntimeState(...)`
- `MainActivity.showConsole(...)`

当前链路：

```text
CardRunStore.update
-> maybeRefreshConsoleAfterRuntimeState
-> showConsole
-> prepareDropZone + loadAllRecipes + recipeGrid
```

最小修复：

- 首页卡建立 card view binding。
- `CardRunStore.runs` 变化只更新对应卡片状态/计时。
- `showConsole()` 只用于进入页面、手动刷新、导入完成。

### P1-D：资源二级页 refresh 仍然整页重建

位置：

- `MainActivity.refreshResourceScreenIfVisible(...)`

当前问题：

```text
resource dirty
-> ResourceDetail showResourceDetail
-> ResourceMore force catalog + showResourceMoreActions
-> ResourceManage showResourceManage
```

最小修复：

- detail 只更新状态/action 区域。
- more 只更新当前 action 状态。
- manage 走后台 payload + block-level refresh。

## 四、修复顺序

### 第 0 步：只加诊断，不改行为

目的：不要靠截图猜，不要一上来改 SH 或安装脚本。

需要日志：

- shell progress received：recipeId、instanceId、runId、text length、signature。
- `CardRunStore.update`：instanceId、status、surface、text length、session/url。
- report UI bind：visible instanceId、TextView text length、elapsed。
- install store write：resourceId、old/new、reason、revision。
- wizard row update：resourceId、row status、elapsed。
- terminal command send：sessionId、command length、queued/sent。
- terminal auth cache update：sessionId、url found yes/no、tail length。

### 第 1 步：SH 报告局部 live

输入：

- `BridgeProgress`

状态源：

- `CardRunStore`

可见消费者：

- 当前报告页 output TextView。
- 当前报告页 elapsed TextView。

验收：

```text
LIVE-1 到 LIVE-10 逐秒出现
elapsed 每秒增长
showCardRunSurface 未因 progress 调用
```

### 第 2 步：安装 store signal

输入：

- registry / plan 写操作。

状态源：

- `KiteResourceInstallStore`

可见消费者：

- 当前安装向导。
- 当前资源列表/资源管理页。

验收：

```text
markInstalling/markInstalled/markFailed/plan step 都发 revision
UI 不再靠整页轮询才知道 plan 变化
```

### 第 3 步：安装向导局部 live

输入：

- install store signal。
- visible wizard elapsed ticker。

状态源：

- `KiteResourceInstallStore`
- `CardRunStore`

可见消费者：

- 顶部状态。
- 队列行 status badge。
- running 行 elapsed。
- row action buttons。

验收：

```text
排队 -> 安装中 -> 已完成/失败 自动变化
顶部和队列同步
完成后 elapsed 固定
不需要返回/重新进入
```

### 第 4 步：terminal auth 文件读取下沉

输入：

- terminal transcript。

状态源：

- terminal runtime cached auth-url。

可见消费者：

- topbar 授权按钮/提示。

验收：

```text
UI thread 不 readText 大 transcript
terminal 预输入保持
auth link 能出现
```

### 第 5 步：资源 snapshot 纯化

输入：

- install signal。
- onResume。
- 手动刷新。

状态源：

- resource static snapshot cache。

可见消费者：

- 资源首页。
- 资源详情。
- 资源管理页。

验收：

```text
render path 不做 File/DB/health/probe
资源列表滚动不被探测拖住
```

### 第 6 步：首页卡局部 runtime binding

输入：

- `CardRunStore.runs`

状态源：

- `CardRunStore`

可见消费者：

- 首页可见卡片状态/计时。

验收：

```text
运行态变化不触发 loadAllRecipes / prepareDropZone
终端预输入不受首页重建时序影响
```

## 五、以后每个 bug 的默认归类

| 现象 | 第一车道 | 先查什么 | 禁止第一反应 |
| --- | --- | --- | --- |
| 全局 Kite 没有响应 | Diagnostics | logcat/main-thread stack | 直接砍功能 |
| 资源页卡 | Resource Static Snapshot | render 是否 File/DB/probe | 每秒刷新资源页 |
| 安装向导卡 | Resource Install Plan + Binding | plan signal、row update | 重建整个 wizard |
| 安装向导不实时 | Install Wizard Binding | store 是否发 signal | 改安装脚本 |
| SH 输出不实时 | SH Report Binding | progress -> store -> UI | 改 SH 流式 |
| elapsed 停住 | UI Binding | visible ticker 是否绑定 | 整页 tick |
| 首页卡状态旧 | Card Run State + Binding | CardRunStore 是否更新 | showConsole 热刷新 |
| 终端预输入丢 | Terminal Runtime | session/command send 日志 | 延迟/删除预输入 |
| Web 没打开 | Web Surface | nextActionUrl/route event | 重建 WebView |

## 六、明天开修前的固定问题

每个改动前必须写出：

```text
Symptom:
Visible surface:
Lane:
State owner:
Event source:
Visible consumer:
Forbidden cross-lane changes:
Regression checks:
```

如果写不出来，先做第 0 步诊断，不直接改刷新架构。

## 七、验收底线

每轮修完都要回答：

```text
Whole-page redraw restored? no
Render-time heavy probe introduced? no
Terminal pre-input preserved? yes
SH report single source preserved? yes
Web route preserved? yes
Resource install completion judgement unchanged? yes
```

这就是之后修 bug 的“车道闸机”：功能可以继续加，bug 可以继续修，但不能再把
热状态、冷探测、终端输入、Web 生命周期和安装计划塞进同一个整页刷新链里。
