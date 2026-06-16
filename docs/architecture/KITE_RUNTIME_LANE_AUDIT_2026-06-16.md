# Kite 运行车道落地审查 2026-06-16

本轮只审查，不改业务代码。

目标：对照 `KITE_RUNTIME_LANES.md`、`KITE_BEHAVIOR_CONTRACT.md`、
`KITE_REGRESSION_MATRIX.md`，筛出当前代码里还没有按车道落地的地方，
作为下一轮逐个修 bug 的队列。

## 总结

当前代码不是整体架构失败。已有几块方向是对的：

- 资源主页已经有后台 `requestResourceSectionsRefresh(...)`，不是每次都同步重建。
- `CardRunStore` 是卡片运行态的中心状态源，且已有 `StateFlow`。
- terminal 预输入不是被删掉的功能，底层 `TerminalSessionController` 已有 queued input / flush 机制。
- 资源详情页已有 seed render + 后台查 catalog 的雏形。

当前不符合车道规则的主要问题集中在：

- SH progress 仍然驱动整页 `showCardRunSurface(...)` / `showConsole()`。
- 安装向导 render 过程中同步读 `resourceCatalog`、plan、registry。
- 资源安装 store 没有可消费的 revision/signal，导致页面只能靠主动重建。
- terminal 授权链接探测在 UI tick/render 中读 transcript 文件。
- 资源管理页和部分资源二级页仍然同步重查 catalog/DB 并整页重建。

## P0：必须先修

### 1. SH progress 仍然走整页重建

车道：SH 报告 + UI 绑定。

证据：

- `handleShellProgress(...)` 更新 `CardRunStore.shellReportText` 后调用 `maybeRenderShellProgress(...)`。
- `maybeRenderShellProgress(...)` 会按场景调用 `renderResourceInstallWizardFor(...)`、
  `showCardRunSurface(...)` 或 `showConsole()`。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:7519`
- `app/src/main/java/com/kite/app/MainActivity.kt:7557`
- `app/src/main/java/com/kite/app/MainActivity.kt:7562`
- `app/src/main/java/com/kite/app/MainActivity.kt:7564`
- `app/src/main/java/com/kite/app/MainActivity.kt:7565`
- `app/src/main/java/com/kite/app/MainActivity.kt:7566`

为什么不合规：

- progress 属于热数据，只应该更新当前可见 report output。
- 现在 progress 仍然可能触发整页 card surface / console 重建。
- 这会同时造成 ANR 风险和实时刷新回归风险。

正确修复形状：

- `handleShellProgress(...)` 只负责更新 `CardRunStore`。
- 如果当前报告页可见且 run instance 匹配，只更新 output TextView。
- elapsed 另走可见页面轻量 ticker。
- 不恢复每秒整页 `showCardRunSurface(...)`。

### 2. 安装向导 render 中同步读资源 catalog、plan、registry

车道：资源安装计划 + UI 绑定 + 资源静态快照。

证据：

- `resourceInstallWizardContent()` 构建 View 时直接调用：
  `resourceCatalog(forceRefresh = false)`、`planSnapshot()`、`registrySnapshot(planIds)`。
- `resourceInstallWizardStepRow()` 如果没有传入 `planSnapshot`，会直接调用
  `resourceInstallStore.planStepStatus(resourceId)`。
- `renderResourceInstallWizardFor(...)` 直接调用 `showCardRunSurface(...)`，重新构建整个向导。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:3236`
- `app/src/main/java/com/kite/app/MainActivity.kt:3242`
- `app/src/main/java/com/kite/app/MainActivity.kt:3243`
- `app/src/main/java/com/kite/app/MainActivity.kt:3250`
- `app/src/main/java/com/kite/app/MainActivity.kt:3472`
- `app/src/main/java/com/kite/app/MainActivity.kt:3482`
- `app/src/main/java/com/kite/app/MainActivity.kt:3638`
- `app/src/main/java/com/kite/app/MainActivity.kt:3641`

为什么不合规：

- plan/registry 是温数据，应该通过 store revision/snapshot 后台刷新。
- wizard row status 和 elapsed 是热 UI，只应局部更新。
- 当前向导每次重建都会把资源、DB、运行状态混到一个 render path。

正确修复形状：

- 给安装向导建立轻量 view holder / binding。
- store 变化时只更新顶部状态、按钮、受影响队列行。
- `resourceCatalog` 只提供已缓存的资源名/图标，不在向导 render 内重探测。
- 完成/失败判定不动。

### 3. 资源安装 store 当前缺少 signal/revision 出口

车道：资源安装计划。

证据：

- `KiteResourceInstallStore` 当前只是薄包装，提供 `markInstalling`、
  `markInstalled`、`markFailed`、`planSnapshot`、`registrySnapshot` 等同步方法。
- 当前文件里没有 `StateFlow`、`signals`、`revision` 或 observer 出口。
- plan/registry 写入后，UI 只能靠 `refreshResourceScreenIfVisible()` 触发整页刷新。

位置：

- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:5`
- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:31`
- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:37`
- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:40`
- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:48`
- `app/src/main/java/com/kite/app/resources/KiteResourceInstallStore.kt:52`

为什么不合规：

- 安装向导 live 更新需要 signal/dirty，而不是页面轮询或整页重建。
- 资源页缓存失效也应该来自明确 revision，而不是到处手动 invalidate。

正确修复形状：

- 在 store 包装层增加轻量 `StateFlow`/revision signal。
- `markInstalling`、`markInstalled`、`markFailed`、`beginPlan`、
  `markPlanStepRunning`、`advancePlanAfter`、`failPlanAt`、`clearPlan` 后发 signal。
- UI 根据 signal 判断是否更新资源页、安装向导或管理页。

### 4. terminal 授权链接探测在 UI 线程读 transcript 文件

车道：终端 Runtime + UI 绑定。

证据：

- `cardRunTopBar(...)` render 时调用 `terminalAuthorizationUrl(...)`。
- `startTerminalAuthorizationLinkWatcher(...)` 用 `root.postDelayed` 在 UI 线程 tick。
- `terminalAuthorizationUrl(...)` 直接 `File(entry.transcriptPath).readText()`。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:5218`
- `app/src/main/java/com/kite/app/MainActivity.kt:5219`
- `app/src/main/java/com/kite/app/MainActivity.kt:7819`
- `app/src/main/java/com/kite/app/MainActivity.kt:7848`
- `app/src/main/java/com/kite/app/MainActivity.kt:7853`
- `app/src/main/java/com/kite/app/MainActivity.kt:7856`

为什么不合规：

- transcript 文件可能增长，`readText()` 不应该出现在 UI render/tick。
- 这条链和 terminal 预输入无关，不能通过延迟/删除预输入来修。

正确修复形状：

- terminal watcher 在后台读取 transcript 尾部或由 terminal runtime 暴露 auth-url snapshot。
- UI 只读缓存的 auth-url 状态。
- topbar 只根据已缓存状态显示“授权”按钮。

## P1：第二批修

### 5. 资源管理页同步重查 catalog、plan、registry

车道：资源静态快照 + 资源安装计划 + UI 绑定。

证据：

- `showResourceManage()` render 中直接 `resourceCatalog(forceRefresh = true)`。
- 同一 render 中直接 `planSnapshot()` 和 `registrySnapshot(planIds)`。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:1430`
- `app/src/main/java/com/kite/app/MainActivity.kt:1439`
- `app/src/main/java/com/kite/app/MainActivity.kt:1441`
- `app/src/main/java/com/kite/app/MainActivity.kt:1443`

正确修复形状：

- 像资源主页一样改成后台 payload。
- plan/registry 变化时只刷新安装列表块。
- 已安装资源列表从 cached catalog snapshot 读取。

### 6. `resourceCatalog(...)` 混合了缓存、DB、stale normalize、workspace probe

车道：资源静态快照。

证据：

- `resourceCatalog(...)` 内部读取 registry snapshot。
- 内部调用 `normalizeStaleResourceState(...)`，可能写 registry / plan。
- 后台路径会 `ToolchainPackInstaller.refreshState(...)` 和
  `toolchainWorkspaceSnapshot(allowProbe = true)`。
- `toolchainWorkspaceSnapshot(...)` 最终做 `File.exists()` 探测。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:4438`
- `app/src/main/java/com/kite/app/MainActivity.kt:4445`
- `app/src/main/java/com/kite/app/MainActivity.kt:4447`
- `app/src/main/java/com/kite/app/MainActivity.kt:4459`
- `app/src/main/java/com/kite/app/MainActivity.kt:4460`
- `app/src/main/java/com/kite/app/MainActivity.kt:4475`
- `app/src/main/java/com/kite/app/MainActivity.kt:4889`
- `app/src/main/java/com/kite/app/MainActivity.kt:4896`
- `app/src/main/java/com/kite/app/MainActivity.kt:4897`
- `app/src/main/kotlin/com/kftest/app/foundation/toolchain/ToolchainPackInstaller.kt:64`
- `app/src/main/kotlin/com/kftest/app/foundation/toolchain/ToolchainPackInstaller.kt:75`

正确修复形状：

- 拆出纯 UI snapshot：只读缓存，不写状态。
- stale normalize 变成后台 reconcile，不在 catalog 构建中顺手写。
- workspace probe 独立 TTL 后台刷新。

### 7. `refreshResourceScreenIfVisible()` 对二级页仍然整页重建

车道：UI 绑定。

证据：

- ResourceDetail 直接 `showResourceDetail(...)`。
- ResourceMore 里 force catalog 后 `showResourceMoreActions(...)`。
- ResourceManage 直接 `showResourceManage()`。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:4310`
- `app/src/main/java/com/kite/app/MainActivity.kt:4321`
- `app/src/main/java/com/kite/app/MainActivity.kt:4322`
- `app/src/main/java/com/kite/app/MainActivity.kt:4323`
- `app/src/main/java/com/kite/app/MainActivity.kt:4327`

正确修复形状：

- ResourceDetail：只更新状态/action 区域。
- ResourceManage：只更新安装列表和已安装列表块。
- ResourceMore：只更新可见 action 状态，不 force catalog on UI。

### 8. `showConsole()` 是首页卡运行态刷新的主要手段

车道：卡片运行状态 + UI 绑定。

证据：

- `setRuntimeState(...)` 后只调用 `maybeRefreshConsoleAfterRuntimeState(...)`。
- `maybeRefreshConsoleAfterRuntimeState(...)` 对 Console 调 `showConsole()`。
- `showConsole()` 会重新 `prepareDropZone()`、`loadAllRecipes()`、`recipeGrid()`。
- 没有看到 MainActivity 对 `CardRunStore.runs` 的局部 UI collect。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:965`
- `app/src/main/java/com/kite/app/MainActivity.kt:967`
- `app/src/main/java/com/kite/app/MainActivity.kt:968`
- `app/src/main/java/com/kite/app/MainActivity.kt:983`
- `app/src/main/java/com/kite/app/MainActivity.kt:8416`
- `app/src/main/java/com/kite/app/MainActivity.kt:8466`
- `app/src/main/java/com/kite/app/MainActivity.kt:8469`
- `app/src/main/java/com/kite/app/MainActivity.kt:8481`
- `app/src/main/java/com/kite/app/run/CardRunStore.kt:24`

正确修复形状：

- 首页卡列表建立可见 card binding。
- `CardRunStore.runs` 变化时只更新对应卡片状态/计时。
- `showConsole()` 保留为页面进入/手动刷新，不作为运行态热刷新手段。

## P2：可以排后，但要记录

### 9. Web surface 依赖 `showCardRunSurface(...)` 重建进入

车道：Web Surface + UI 绑定。

证据：

- `showCardRunSurface(...)` 在 Web surface 时直接 `showCardRunWebView(...)`。
- 只要错误路径触发 `showCardRunSurface(...)`，WebView 有被 remove/add 和 reload 的风险。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:5131`
- `app/src/main/java/com/kite/app/MainActivity.kt:5283`
- `app/src/main/java/com/kite/app/MainActivity.kt:5294`
- `app/src/main/java/com/kite/app/MainActivity.kt:5297`

正确修复形状：

- Web route 只在 URL/source 变化时 load。
- 非 Web 状态变化不能导致 WebView reload。

### 10. manifest loader 有缓存，但仍可能被 catalog render 链间接触发

车道：资源静态快照。

证据：

- manifest loader 内部有 asset cache。
- 但 `resourceCatalog(...)` 会 `map { applyResourceManifest(it) }`。
- 如果 catalog 被 UI render 强制刷新，manifest 读取也会被拖进热路径。

位置：

- `app/src/main/java/com/kite/app/MainActivity.kt:4882`
- `app/src/main/java/com/kite/app/MainActivity.kt:4904`
- `app/src/main/java/com/kite/app/resources/KiteResourceManifestLoader.kt:167`
- `app/src/main/java/com/kite/app/resources/KiteResourceManifestLoader.kt:287`
- `app/src/main/java/com/kite/app/resources/KiteResourceManifestLoader.kt:322`

正确修复形状：

- manifest 读取只属于后台 resource snapshot 构建。
- UI render 使用 already-built manifest fields。

## 明天建议修复顺序

1. 先加最小诊断日志，不改行为：
   `handleShellProgress`、`CardRunStore.update`、report UI bind、terminal command send、
   install plan state change、wizard row update。

2. 修 SH 报告 live binding：
   progress -> store -> 当前 report output TextView；elapsed -> 当前 elapsed TextView。
   不恢复整页 `showCardRunSurface(...)`。

3. 给 `KiteResourceInstallStore` 增加 revision/signal：
   plan/registry 写操作发 signal，安装向导和资源页消费。

4. 修安装向导局部绑定：
   顶部状态、按钮、队列行、elapsed 分开更新。

5. 把 terminal auth transcript 读取移出 UI：
   后台读尾部 / cached auth-url snapshot，topbar 只读缓存。

6. 修资源管理页后台 payload：
   管理页不在 render 里 force catalog / DB snapshot。

7. 最后处理首页卡局部更新：
   `CardRunStore.runs` -> visible card binding，减少 `showConsole()` 热刷新。

## 本轮明确不动的东西

- 不改 Hermes WebUI 安装脚本。
- 不改 Hermes WebUI manifest。
- 不改安装完成判定。
- 不改 SH 执行/流式机制。
- 不改 CardRunStore 大结构。
- 不改 cards loader / JSON / schema。
- 不删除 terminal 预输入。

