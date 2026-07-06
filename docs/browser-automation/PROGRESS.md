# Kite 浏览器自动化进度

最后更新：2026-07-06 A33 完成，WebView 自动浏览器已归位为设置页第二模式；A0-A32 已由本地 commit `e8bddf0` 封口，当前准备再次提交文档封口。

## 当前状态总览

| 任务 | 状态 | 备注 |
| --- | --- | --- |
| A0 建立浏览器自动化任务基线 | done | 三件套已建立；提交/合并策略已明确为先走浏览器分支 checkpoint |
| A1 调研自动浏览器技术路线 | done | 初版资料收口完成，推荐路线为受控 WebView 内核 + 后续 CDP/Playwright/Appium 兼容层 |
| A2 设计 Kite 自动浏览器协议 | done | `AUTOMATION_PROTOCOL.md` 已覆盖 session/action/result、selector、等待、URL 分流和 CardRunStore 绑定 |
| A3 实现最小自动浏览器内核 | done | 支持 session、snapshot、find/click/type/waitFor、CardRunStore 写回、默认模式回归 |
| A4 接入 AI/脚本控制入口 | done | 已有 capabilities、action、session 查询 endpoint；OnePlus 8T 端到端 demo 通过 |
| A5 扩展自动化动作和证据能力 | done | scroll/evaluate/screenshot、动作后 snapshot、截图路径和 console 查询已在 OnePlus 8T 通过 |
| A6 补网络证据和动作历史查询 | done | `/browser-automation/actions`、`/browser-automation/network` 和 session 内 actions/network 已在 OnePlus 8T 验证 |
| A7 补可访问树/语义观察 | done | `/browser-automation/session` 的 snapshot 已返回 accessibility；OnePlus 8T 验证通过且修复 password value 泄露 |
| A8 补 role/name 语义定位动作 | done | action target 已支持 role + accessible name；find/type/click/waitFor/scroll 真机通过 |
| A9 补批量动作 run 接口 | done | `/browser-automation/run` 已支持顺序执行、stopOnFailure 和完整结果汇总 |
| A10 补 open-run 一体入口 | done | `/browser-automation/open-run` 已支持打开页面、等待 session ready、执行 run |
| A11 持久化 run 结果并提供查询入口 | done | `/browser-automation/runs` 和 `/session.runs` 已在 OnePlus 8T 验证 |
| A12 补智能体 observe 观察入口 | done | `/browser-automation/observe` 已返回紧凑观察、suggestedTarget 和最近 run |
| A13 补截图 artifact 受控下载入口 | done | screenshot action 的 PNG 已可通过 `/browser-automation/artifact` 受控下载 |
| A14 observe 暴露 evaluate 安全边界 | done | `page.scope` 和 `page.trustedForEvaluate` 已接入 observe |
| A15 补 URL 等待和查找 target | done | `kind=url` 已支持 find/waitFor；不可操作动作返回 `target_not_actionable` |
| A16 补页面状态等待 target | done | `kind=state` 已支持 domReady/complete/idle；不可操作动作返回 `target_not_actionable` |
| A17 补键盘 press 动作 | done | `press` 已支持 DOM target 和 activeElement；Enter 提交、负向门禁和默认模式回归已在 OnePlus 8T 通过 |
| A18 补表单 select 动作 | done | `select` 已支持 HTML `<select>` 按 value/text/index 选择；负向、observe、门禁和真机验证已通过 |
| A19 补状态控件 check 动作 | done | `check` 已支持 checkbox true/false/toggle、radio true、ARIA switch-like；负向、observe、门禁和真机验证已通过 |
| A20 补截图 artifact URL 与 observe 恢复入口 | done | screenshot result、session/actions/runs 和 observe 均已暴露相对 `artifactUrl`；真机下载 PNG 通过 |
| A21 补 click 的 pointer/mouse 事件序列 | done | `click` 已在 `element.click()` 前派发 pointer/mouse down/up；pointer-gated 真机验证通过 |
| A22 补 hover 动作 | done | `hover` 已派发 pointer/mouse over/enter/move；hover-gated 真机验证通过 |
| A23 补受控导航动作 | done | `navigate` 已支持 back/forward/reload；拒绝任意 URL；hash history 真机验证通过 |
| A24 observe 暴露能力摘要 | done | `/observe` 已返回 actions/targets/endpoints/runs/authBoundary/evaluate；与 capabilities endpoint 共用 `BrowserAutomationCapabilities` |
| A25 补 doubleClick 动作 | done | `doubleClick` 已触发 dblclick 门控；observe/capabilities、负向、旧动作回归和默认模式门禁均通过 |
| A26 补同源 iframe 观察和动作支持 | done | 同源 iframe 内 `type -> click -> waitFor` 真机通过；observe frame 标记和 sandbox 边界验证通过 |
| A27 补 open Shadow DOM 观察和动作支持 | done | open shadow 内 `type -> click -> waitFor` 真机通过；observe/session shadow 标记和 closed shadow 边界验证通过 |
| A28 补自动浏览器 session 列表入口 | done | `/browser-automation/sessions` 已支持列表、limit、instanceId 过滤和默认模式只读查询；真机验证通过 |
| A29 补输入框 clear 动作 | done | `clear` 已支持 input/textarea/contenteditable；capabilities/observe 已同步；真机和默认模式门禁通过 |
| A30 observe 补同名元素 target index | done | observe 建议 target 已带 index；同名按钮 index=0/1 真机验证通过 |
| A31 补 disabled/readonly 动作可执行性守卫 | done | 动作层已拒绝 disabled/readonly 伪成功；observe、单测、构建、OnePlus 8T 和默认模式门禁通过 |
| A32 补 run/open-run 迟到 action 结果校准 | done | action history 校准、4 秒短等待、runs/session/observe 一致性和默认模式门禁均已验证 |
| A33 WebView 自动浏览器归位与封口 | done | 第二模式归位为 WebView 元素化 + 自动控制 + 后续自身持久化验证；完整浏览器另起后续阶段 |
| A34 WebView 登录态持久化验证 | pending | 下一步验证 cookie/localStorage/IndexedDB 和普通网站登录态复用 |
| A35 完整内置浏览器新阶段准备 | pending | A33 封口后再研究真正完整内置浏览器，不混入本次提交 |

状态取值：`pending` / `in_progress` / `blocked` / `done`

## 三问自检：A33 WebView 自动浏览器归位与封口

1. 目标是什么？引用 PLAYBOOK A33：把 A0-A32 已完成的元素化能力归位为设置页第二模式，也就是 `automation_browser` 的 WebView 自动浏览器方案；完整内置浏览器另起后续阶段。
2. 完成标准是什么？明确设置页仍只有 `webview_system_auth` 和 `automation_browser` 两个用户模式；明确 `automation_browser` 当前封口为 WebView 自动浏览器；明确元素化已测过，WebView 登录态持久化还未作为真实网站验收完成；完整浏览器不混入本次封口。
3. 前置任务是否完成？A32 已完成并验证；浏览器线本地 commit `e8bddf0 checkpoint: 浏览器登录回跳与自动浏览器底座` 已封口；当前只进入浏览器自动化方向，不进入 X11 / MEIZU 任务线。

## 2026-07-06 A33 归位记录

- Git 基线：`codex/browser-login-return` 当前最新提交为 `e8bddf0 checkpoint: 浏览器登录回跳与自动浏览器底座`，启动 A33 前工作树干净。
- 用户澄清：第二模式当前不是为强认证服务，而是 WebView 自动浏览器；重点是元素化和后续自身持久化。Google/ChatGPT 这类登录挑战不是第二模式第一验收。
- 设置归位：用户设置仍是两种模式：`webview_system_auth` 表示 WebView + 系统浏览器登录回跳；`automation_browser` 表示 WebView 自动浏览器。
- 测试状态：A0-A32 的元素化、action/run/open-run、observe、截图、iframe、open shadow、actionability 和默认模式门禁已经完成单测、构建、OnePlus 8T 真机验证。
- 未完成状态：WebView 自身登录态持久化还没有作为真实网站验收完成；A34 会单独验证 cookie、localStorage、IndexedDB 和普通网站登录态复用。
- 后续分线：完整内置浏览器作为 A35 之后的新阶段，候选可继续参考 `docs/browser-automation/REAL_BROWSER_RESEARCH.md`，但不混入本次 WebView 封口提交。

## 2026-07-06 A33 完成记录

- 调研文件：`docs/browser-automation/REAL_BROWSER_RESEARCH.md` 已降级为后续完整内置浏览器参考，不作为第二模式当前验收。
- 入口侦察：`app/src/main/java/com/kite/app/browser/BrowserRuntimeMode.kt` 当前只有 `webview_system_auth` 与 `automation_browser` 两个用户模式；`MainActivity.showSettings()` 直接枚举这两个值。
- WebView 入口：`KiteWebShell` 负责 Android WebView、`BrowserHandoffPolicy`、下载、console/network 和 `BrowserAutomationController` 绑定。
- 自动化门禁：`MainActivity.showCardRunWebView(...)` 只在 `BrowserRuntimeMode.AutomationBrowser` 下给 WebView 开启 automation；`KiteLocalServer` 的 action/run/open-run 也受同一模式门禁约束。
- Gradle 入口：`app/build.gradle` 已有 `androidx.browser` 和 `androidx.webkit`，当前没有 GeckoView 依赖；真浏览器原型需要新增依赖前先确认体积、仓库和构建影响。
- 决策回写：`DECISIONS.md` 新增 ADR-A037 和 ADR-A038；A33 验收项已在 `PLAYBOOK.md` 勾选。
- 检查：`git diff --check` 通过，仅有已有 LF/CRLF 提示。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，输出 `BUILD SUCCESSFUL`。

## 三问自检：A32 run/open-run 迟到 action 结果校准

1. 目标是什么？引用 PLAYBOOK A32：修复 run/open-run 汇总层与 action history 在迟到结果场景下互相矛盾的问题，让恢复查询看到同一份最终事实。
2. 完成标准是什么？同 actionId 的迟到成功结果能把已保存的 `request_timeout` run 查询校准为 `Succeeded`；真实 timeout 仍保持 TimedOut；open-run 响应在短暂迟到窗口内优先返回最终 action；runs/session/observe 使用同一套校准结果；单测、构建、OnePlus 8T 真机验证和默认模式门禁通过。
3. 前置任务是否完成？A31 已完成并验证；现有 `BrowserAutomationSessionStore` 已保存 action history 和 run history，可复用 actionId 关联，不需要新增平行状态。

## 2026-07-05 A32 run/open-run 迟到结果校准完成记录

- 代码：新增 `BrowserAutomationRunReconciler`，只在原 action result 为 `TimedOut/request_timeout` 时按同 `actionId` / `sessionId` 接受迟到落库的最终结果。
- Store：`BrowserAutomationSessionStore.getRun(...)` 和 `recentRuns(...)` 会用 action history 校准 run 结果；`latestResultForAction(...)` 供 run/open-run 短等待使用。
- LocalServer：`/browser-automation/action`、`/browser-automation/run` 和 `/browser-automation/open-run` 的执行链遇到 `request_timeout` 时最多 4 秒轮询同 actionId 的最终落库结果，减少响应层和 history 层刚返回即矛盾。
- 单测：`BrowserAutomationSessionStoreTest` 新增迟到成功校准和真实 timeout 保持 timeout 两个断言，并覆盖 observe 的 `recentRun` 使用校准后的结果。
- 文档：`AUTOMATION_PROTOCOL.md` 增加 run 结果校准规则；`DECISIONS.md` 新增 ADR-A036。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，输出 `BUILD SUCCESSFUL`。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过，输出 `BUILD SUCCESSFUL`。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242716518 bytes，时间 2026/7/5 15:45:54。
- 真机安装：OnePlus 8T `3f8bbaad` 安装成功，临时切到 `automation_browser` 验证。
- 真机普通一致性：`runId=a32-open-run-consistency`，session `84dd3c8c9d0a4322a133bf62d4b4a1d0`，open-run 响应、`/browser-automation/runs`、`/browser-automation/session.runs[0]` 和 `/browser-automation/observe.recentRun` 均为 `Succeeded`，页面文本包含 `Hello A32 Consistency`。
- 真机查询层校准：`runId=a32-late-evaluate-reconcile` 曾用 1.8 秒本地 evaluate 构造迟到场景；初始响应为 `TimedOut/request_timeout`，迟到 action 落库后 `/browser-automation/runs`、`/session` 和 `/observe` 均校准为 `Succeeded`，结果为 `evaluate: late-evaluate-ok`。
- 真机响应层校准：调整为 4 秒短等待后，`runId=a32-late-evaluate-short-reconcile-v3`、session `ebafc8104f714ef9955e7a17ee02e772` 使用 1.3 秒本地 evaluate 构造短迟到；open-run 响应 HTTP 200、`status=Succeeded`，`/runs`、`/session` 和 `/observe` 均为 `Succeeded`，结果为 `evaluate: late-short-ok-v3`。
- 默认模式回归：验证后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a32-mode-gate` 的 open-run 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`；默认模式下 sessions 只读查询返回 HTTP 200。

## 三问自检：A31 disabled/readonly actionability

1. 目标是什么？引用 PLAYBOOK A31：在动作层补 disabled/readonly 可执行性守卫，让自动浏览器不要对用户不可操作的控件直接 JS 改值或误报点击成功。
2. 完成标准是什么？disabled button 的 click 返回 `target_disabled`；disabled input 的 type/clear 返回 `target_disabled`；readonly input 的 type/clear 返回 `target_readonly`；observe 对 disabled button 仍只建议 find；普通 enabled 动作、iframe、shadow 和 A30 index 不回退；单测、构建、OnePlus 8T 真机验证和默认模式门禁通过。
3. 前置任务是否完成？A30 已完成并验证；现有 action script、observe enabled 字段、测试页和 role/name/index selector 可复用，不需要新增平行执行器。

## 2026-07-05 A31 disabled/readonly actionability 完成记录

- 代码：`BrowserAutomationActionScript` 新增 `disabledForAutomation`、`readonlyForAutomation` 和 `requiresEnabled`，统一检查 HTML disabled、`:disabled`、`aria-disabled=true`、HTML readonly 和 `aria-readonly=true`。
- 行为：`click`、`doubleClick`、`type`、`clear`、`select`、`check` 遇到 disabled 目标返回 `target_disabled`；目标化 `press` 遇到 disabled 目标同样返回 `target_disabled`；`type` / `clear` 遇到 readonly 返回 `target_readonly`。
- observe：`BrowserAutomationObservation` 对 `enabled=false` 的交互节点只建议 `find`；测试页 disabled button 和 disabled input 均符合该规则。
- 测试页：`/browser-automation/test-page` 新增 `Disabled Name`、`Readonly Name`、`Disabled action` 和 `disabled automation result` 探针。
- 协议文档：`AUTOMATION_PROTOCOL.md` 新增 `target_disabled` / `target_readonly` 错误码和 Actionability 边界；`RESEARCH.md` 同步 disabled/readonly 守卫。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242709398 bytes，时间 2026/7/5 15:23:43。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机打开：`runId=a31-open-actionability` 创建 session `ace1797d2ca44f4e9e4e7cc642c1d748`，页面 URL 为 `http://127.0.0.1:8791/browser-automation/test-page?a31=actionability`，session status 为 `Ready`，snapshot readyState 为 `complete`。
- 真机说明：首次 open-run HTTP 请求在 run 汇总层记录 `TimedOut/request_timeout`，但同一个 action 随后在 session store 中成功完成；恢复查询和后续动作均使用同一个 Ready session 验证。
- 真机 observe：`Disabled Name Disabled input` 返回 `enabled=false` 且 `suggestedActions=["find"]`；`Disabled action` 返回 `enabled=false` 且 `suggestedActions=["find"]`；`Readonly Name Readonly input` 仍可观察但动作层拒绝编辑。
- 真机负向：`runId=a31-actionability-negative`，5 步全部执行并返回预期失败：disabled button click -> `target_disabled`；disabled input type/clear -> `target_disabled`；readonly input type/clear -> `target_readonly`。
- 真机未改值证据：`a31-disabled-state-evaluate` 返回 `Disabled waiting||Locked disabled value|Readonly locked value`，证明 disabled click 没触发、disabled input 和 readonly input 没被改值。
- 真机回归：`runId=a31-regression-enabled-frame-shadow`，11 步普通输入/点击、`Duplicate action index=1`、同源 iframe `Frame hello A31 Frame`、open shadow `Shadow hello A31 Shadow` 全部成功。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a31-mode-gate` 的 open-run 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`；默认模式下 sessions 仍只读返回 HTTP 200。
- 检查：`git diff --check` 通过，仅出现已有 LF/CRLF 提示。

## 2026-07-05 A30 observe 同名元素 index 完成记录

- 代码：`BrowserAutomationObservation` 生成 interactive 列表时，按可观察顺序为同 `role + name` 节点计算重复序号，并写入 `suggestedTarget.index`。
- 兼容：`BrowserAutomationTarget.index` 既有默认值仍为 0；旧脚本不传 index 时继续选择第一个匹配元素。
- 测试页：`/browser-automation/test-page` 新增两个同名 `Duplicate action` 按钮和 `duplicate automation result` status。
- 协议文档：`AUTOMATION_PROTOCOL.md` 已说明 `index` 是同一 selector 结果中的序号，observe 的 `suggestedTarget.index` 可直接给 action/run 使用；`RESEARCH.md` 已同步该元素化能力。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，覆盖同名按钮 target index 为 0/1，唯一按钮仍为 0。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242707022 bytes，时间 2026/7/5 15:11:32。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机打开：`runId=a30-open-observe`，session `92f9be56dbcc44ed9bc98af437447c4e`，打开 `http://127.0.0.1:8791/browser-automation/test-page?a30=target-index` 并 `waitFor state=domReady` 成功。
- 真机 observe：`/browser-automation/observe?sessionId=92f9be56dbcc44ed9bc98af437447c4e&interactiveLimit=50` 返回两个 `button / Duplicate action`，`suggestedTarget` 分别为 `{"kind":"role","value":"button","name":"Duplicate action","index":0}` 和 `index:1`。
- 真机动作：`runId=a30-target-index-click` 在同一 session 内执行 `click Duplicate action index=0 -> waitFor Duplicate first clicked -> click Duplicate action index=1 -> waitFor Duplicate second clicked`，4 步全部成功；两个 click 的 `matchedCount=2`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a30-mode-gate` 的 open-run 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`；默认模式下 sessions 仍只读返回 HTTP 200。

## 三问自检：A30 observe 同名元素 index

1. 目标是什么？引用 PLAYBOOK A30：在 observe 的 `interactive[].suggestedTarget` 中补 `index`，让智能体遇到同 role/name 的重复元素时能直接使用返回 target 操作第 N 个元素。
2. 完成标准是什么？每个 suggestedTarget 都有非负 index；同 role/name 重复元素返回稳定 `0/1/...`；action 能直接使用 observe target 点击第二个同名按钮；本地测试页新增重复按钮探针；OnePlus 8T 上 `role=button,name=Duplicate action,index=1` 触发第二个结果；不改变未传 index 时默认第一个；单测、构建、真机验证和默认模式门禁通过。
3. 前置任务是否完成？A29 已完成并验证；现有 `BrowserAutomationTarget.index`、action selector nth 逻辑、observe interactive 列表和测试页可复用，不需要新增 selector 类型或平行状态。

## 2026-07-05 A29 clear 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Clear("clear")`；`BrowserAutomationActionScript` 新增 `clearTargetInfo` 和 `action.type === 'clear'` 分支。
- 行为：`clear` 只清空 HTML input、textarea 和 contenteditable；非可编辑目标返回 `target_not_editable`；`kind=url` / `kind=state` 继续由既有门禁返回 `target_not_actionable`。
- 事件：清空后派发 `input` 和 `change` 事件；`type` 仍保持旧的覆盖填入语义，没有改成追加或特殊清空。
- 能力声明：`BrowserAutomationCapabilities.actions` 已包含 `clear`；`/browser-automation/capabilities` 和 `/browser-automation/observe.capabilities` 共享同一来源。
- observe：textbox 建议动作更新为 `type,clear,press,find,waitFor`；OnePlus 8T 上 `Name 输入名字`、`Notes Notes`、`Editable note Editable note` 都返回该动作序列。
- 测试页：`/browser-automation/test-page` 新增 `Notes` textarea 和 `Editable note` contenteditable 探针。
- 协议文档：`AUTOMATION_PROTOCOL.md` 和 `RESEARCH.md` 已同步 `clear` 动作、错误码和 observe 示例。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242705694 bytes，时间 2026/7/5 15:01:51。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机 clear 主链路：`runId=a29-clear-success`，session `77728010ba6d4391bc31a3064dd6a80d`，12 步 `waitFor domReady -> type Name -> clear Name -> evaluate name -> type Name -> type Notes -> clear Notes -> type Editable note -> clear Editable note -> evaluate notes/editable -> click Apply greeting -> waitFor Hello A29 Final` 全部成功。
- 真机清空证据：`clear Name` 返回 `cleared input changed`；Name 的 evaluate 返回空；`clear Notes` 返回 `cleared textarea changed`；`clear Editable note` 返回 `cleared contenteditable changed`；合并 evaluate 返回 `|`。
- 真机负向：`clear role=button name=Apply greeting` 返回 `Failed/target_not_editable`；`clear kind=url` 和 `clear kind=state` 均返回 `Rejected/target_not_actionable`。
- 真机回归：`runId=a29-frame-shadow-regression`，同一 session 内同源 iframe `Frame hello A29 Frame` 和 open shadow `Shadow hello A29 Shadow` 的 `type -> click -> waitFor` 均成功。
- sessions 回归：默认最新 session 为 `77728010ba6d4391bc31a3064dd6a80d`；`/browser-automation/sessions?limit=3` 返回 HTTP 200，并能看到 A29 与 A28 sessions。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a29-mode-gate` 的 open-run 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`；默认模式下 sessions 仍只读返回 HTTP 200。

## 三问自检：A29 clear 动作

1. 目标是什么？引用 PLAYBOOK A29：新增显式 `clear` 动作，让智能体能确定性清空 input、textarea 和 contenteditable，避免用 Backspace、选择文本或把 `type` 当清空动作。
2. 完成标准是什么？action JSON 能表达 clear；clear 能清空 HTML input、textarea 和 contenteditable 并派发 input/change；非可编辑目标返回 `target_not_editable`，url/state target 返回 `target_not_actionable`；capabilities 和 observe textbox 建议动作包含 clear；OnePlus 8T 上能跑通 `type -> clear -> evaluate/waitFor -> type -> click -> waitFor`；既有 type、顶层、iframe、shadow、sessions 和默认模式门禁不回退；单测、构建、真机验证通过。
3. 前置任务是否完成？A28 已完成并验证；现有 `BrowserAutomationActionType`、`BrowserAutomationActionScript`、capabilities、observe 建议动作和测试页可在同一协议上扩展，不需要新建执行器。

## 2026-07-05 A28 session 列表入口完成记录

- 代码：`BrowserAutomationSessionStore` 新增 `recentSessions(limit, includeClosed, instanceId)`，复用 `sessions_v1`，按 `updatedAt` 最新优先排序。
- LocalServer：新增只读 `GET /browser-automation/sessions`，返回 `ok`、`count`、`latestSessionId`、`source=BrowserAutomationSessionStore`、`includeClosed` 和 `sessions` 摘要数组；不返回 snapshot/action/run 重型详情。
- 能力声明：`BrowserAutomationCapabilities.endpoints` 已包含 `/browser-automation/sessions`；`/browser-automation/capabilities` 的字符串和数组形式、`/browser-automation/observe.capabilities.endpoints` 均同步。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，覆盖 session 列表排序、过滤、limit、Closed 排除和 URL 敏感参数脱敏。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242703394 bytes，时间 2026/7/5 14:49:40。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机多 session：`runId=a28-session-one` 创建 session `61df50c573414964a7f5840dfcb457d8`，`runId=a28-session-two` 创建 session `971f0ecf403d4ae892447bc994147197`，两条 `open-run` 都完成 `waitFor domReady -> type -> click -> waitFor`。
- 真机 sessions 查询：`GET /browser-automation/sessions?limit=10` 返回 `latestSessionId=971f0ecf403d4ae892447bc994147197`，最新两个 session 为 `971f0ecf403d4ae892447bc994147197,61df50c573414964a7f5840dfcb457d8`；`limit=1` 返回 1 条；`instanceId=<第二个 instanceId>&includeClosed=true` 返回第二个 session。
- 真机恢复 observe：使用列表返回的 session `61df50c573414964a7f5840dfcb457d8` 调 `/browser-automation/observe` 成功，页面文本包含 `Hello A28 One`。
- 真机脱敏：sessions 原始 JSON 不包含 `should-not-leak-one` 或 `should-not-leak-two`，URL 中显示 `token=present` / `password=present`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/sessions?limit=1` 返回 HTTP 200 且只读，不打开页面、不执行动作；默认模式下 `runId=a28-mode-gate` 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`。
- 检查：`git diff --check` 通过，仅出现已有 LF/CRLF 提示。

## 三问自检：A28 session 列表入口

1. 目标是什么？引用 PLAYBOOK A28：新增只读 `/browser-automation/sessions`，让外部智能体在多页面、断线恢复或跨回合继续时能发现当前自动浏览器 sessions，而不是依赖 latest 或对话记忆。
2. 完成标准是什么？sessions endpoint 返回 `ok/count/sessions/latestSessionId/source`；默认排除 Closed 并按 `updatedAt` 最新优先；支持 `limit`、`includeClosed=true` 和 `instanceId` 过滤；输出不泄露 token/password/authorization 原文；capabilities/observe capabilities 暴露 endpoint；OnePlus 8T 上两个页面可列出并用 sessionId observe；默认模式下只读可查且 open-run 门禁不回退；单测、构建、真机验证通过。
3. 前置任务是否完成？A27 已完成并验证；现有 `BrowserAutomationSessionStore.allSessions()`、`toPublicJson()`、capabilities 和 observe 能力摘要可复用，不需要新建平行状态。

## 2026-07-05 A27 open Shadow DOM 完成记录

- 代码：`BrowserAutomationActionScript` 的 selector 现在会递归进入同源 document / iframe document 内的 open `shadowRoot`，`css/text/role/role+name` target 可匹配 open shadow 内元素。
- 语义定位：`accessibleNameOf` 的 `label[for]` 和 `aria-labelledby` 查询改为优先使用元素所属 root，避免 open shadow 内 label 被顶层 document 查询漏掉。
- Snapshot：`BrowserAutomationController` 的 snapshot JS 采集 open shadow root 内的可见文本、元素摘要和 accessibility 节点；节点增加 `shadowPath` / `shadowHost`。
- 模型：`BrowserAutomationElementSummary` / `BrowserAutomationAccessibilityNode` 增加可选 `shadowPath`、`shadowHost`；parser、store、session public JSON 和 observe 均已接入。
- 测试页：`/browser-automation/test-page` 新增 `open-shadow-widget` 验证组件，以及 `closed-shadow-widget` 边界探针，closed shadow 内部包含 `closed-shadow-secret-should-not-leak`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242700662 bytes，时间 2026/7/5 14:41:46。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机 shadow 动作：`runId=a27-open-shadow-success`，session `5f18219c27944130b28089a063d786c7`，6 步 `waitFor domReady -> waitFor role=textbox name=Shadow Name -> type -> click role=button name=Apply shadow greeting -> waitFor role=status name=Shadow hello A27 Shadow -> evaluate` 全部成功；evaluate 返回 `A27 Shadow`。
- 真机 observe/session：observe 返回 open shadow 内 `textbox`、`button`、`status`，均带 `shadow.path=/shadow[45]` 和 `shadow.host=open-shadow-widget #open-shadow-widget Open shadow automation host`；session snapshot accessibility 同样返回 `shadowPath` / `shadowHost`。
- 真机 closed shadow 边界：observe 和 session 原始 JSON 均不包含 `closed-shadow-secret-should-not-leak`；closed host 仅作为普通 host region 暴露，不读取 closed shadow 内部 DOM。
- 真机回归：`runId=a27-top-frame-regression`，session `31013ee7e25b4a96bad45c3aed40c894`，8 步顶层 `type/click/waitFor` 和同源 iframe `type/click/waitFor` 全部成功。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a27-mode-gate` 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`。
- 检查：`git diff --check` 通过，仅出现已有 LF/CRLF 提示。

## 三问自检：A27 open Shadow DOM

1. 目标是什么？引用 PLAYBOOK A27：让自动浏览器能观察并操作 open shadow root 内的 DOM/语义节点，同时保持 closed shadow root 的浏览器边界。
2. 完成标准是什么？snapshot/observe 能看到 open shadow 内 textbox/button/status 并带 shadow 标记；现有 DOM target 能操作 open shadow 内元素；action 后 snapshot 能反映 shadow 内状态变化；closed shadow 内部敏感文本不进入 snapshot/observe；本地测试页和 OnePlus 8T 真机跑通 shadow 内 `type -> click -> waitFor`；既有顶层、iframe、observe、capabilities 和默认模式门禁不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A26 已完成并验证；现有 document/iframe traversal、snapshot/action selector、role/name selector、observe 和测试页可继续扩展到 open shadow root，不需要新建执行器。

## 2026-07-05 A26 同源 iframe 完成记录

- 代码：`BrowserAutomationActionScript` 的 selector 现在递归进入同源 `iframe/frame` 的 `contentDocument`，`css/text/role/role+name` target 可匹配同源 iframe 内元素。
- 事件：action 派发 pointer/mouse 事件时改用元素所属 `ownerDocument.defaultView`，避免 iframe 内元素使用顶层 window 事件构造器导致坐标/事件上下文错误。
- Snapshot：`BrowserAutomationController` 的 snapshot JS 递归采集同源 iframe 的可见文本、元素和 accessibility；跨源或 sandbox 不可访问 iframe 只输出 `role=iframe` 摘要和 `frameAccessible=false`，不读取内部 DOM。
- 模型：`BrowserAutomationElementSummary` / `BrowserAutomationAccessibilityNode` 增加可选 `framePath`、`frameUrl`、`frameName`、`frameAccessible`；parser、store、session public JSON 和 observe 均已接入并对 frameUrl 脱敏。
- 测试页：`/browser-automation/test-page` 新增同源 iframe `/browser-automation/test-frame`，以及 sandboxed `srcdoc` iframe 作为不可访问边界探针，内部包含 `sandbox-secret-should-not-leak`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242691838 bytes，时间 2026/7/5 14:29:57。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机 iframe 动作：`runId=a26-same-origin-iframe-success`，session `8e232333fef3400d8317a876512464bf`，6 步 `waitFor domReady -> waitFor role=textbox name=Frame Name -> type -> click role=button name=Apply frame greeting -> waitFor role=status name=Frame hello A26 Frame -> evaluate` 全部成功；evaluate 返回 `A26 Frame`。
- 真机 observe：同源 frame button 返回 `frame.path=top/frame[0]`、`frame.accessible=true`；iframe 内 status 返回 `role=status`；sandbox frame 返回 `role=iframe`、`frame.accessible=false`；observe 不包含 `sandbox-secret-should-not-leak`。
- 真机顶层回归：`runId=a26-top-level-regression`，8 步顶层 `type/click/waitFor/doubleClick/hover` 全部成功。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a26-mode-gate` 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`。
- 检查：`git diff --check` 通过，仅出现已有 LF/CRLF 提示。

## 三问自检：A26 同源 iframe

1. 目标是什么？引用 PLAYBOOK A26：让自动浏览器能观察并操作同源 iframe 内的 DOM/语义节点，同时对跨源或不可访问 iframe 明确保持只读边界。
2. 完成标准是什么？snapshot/observe 能看到同源 iframe 内 button/textbox/status 并带 frame 标记；现有 DOM target 能操作同源 iframe 内元素；action 后 snapshot 能反映 iframe 内状态变化；跨源/不可访问 iframe 不读取内部敏感内容；本地测试页和 OnePlus 8T 真机跑通 iframe 内 `type -> click -> waitFor`；既有顶层动作和默认模式门禁不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A25 已完成并验证；现有 snapshot JS、action queryElements、role/name selector、observe 和测试页都可在同一协议上扩展，不需要新建平行执行器或全局浏览器控制。

## 2026-07-05 A25 doubleClick 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `DoubleClick("doubleClick")`；`BrowserAutomationActionScript` 新增 `dispatchDoubleClick`，执行两轮 click prelude + `element.click()`，最后派发 `dblclick`。
- 能力声明：`BrowserAutomationCapabilities.actions` 已包含 `doubleClick`；`/browser-automation/capabilities` 的字符串和数组形式均同步；`/browser-automation/observe.capabilities.actions` 也包含 `doubleClick`。
- observe：可点击语义节点建议动作更新为 `click,hover,doubleClick,find,waitFor`；真机 observe 对 `Double click open` 返回 `role=button`、`suggestedActions=click,hover,doubleClick,find,waitFor`。
- 测试页：`/browser-automation/test-page` 新增 `Double click open` 按钮和 `double-result` status；只有 `dblclick` 监听器会把状态改成 `Double click opened`，普通 click 只记录 `data-double-clicks`。
- 协议文档：`AUTOMATION_PROTOCOL.md` 和 `RESEARCH.md` 已同步 `doubleClick` 动作和 observe capabilities 示例。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242678306 bytes，时间 2026/7/5 14:16:06。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，`/browser-automation/capabilities` 返回 `doubleClick`，actionCount 为 14。
- 真机 doubleClick：`runId=a25-double-click-success`，session `d23ab62898b140f78678afbf61f6cc9b`，4 步 `waitFor domReady -> doubleClick role=button name=Double click open -> waitFor text=Double click opened -> evaluate` 全部成功；evaluate 返回 `opened:2`。
- 真机旧动作回归：`runId=a25-existing-actions-regression`，14 步 `type/select/check/press/waitFor/hover/click/navigate/url wait` 全部成功。
- 真机负向：`doubleClick` 携带 `kind=url` 和 `kind=state` target 均返回 `Rejected/target_not_actionable`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `runId=a25-mode-gate` 返回 HTTP 409、`mode_not_enabled`、`open.requested=false`。
- 检查：`git diff --check` 通过，仅出现已有 LF/CRLF 提示。

## 三问自检：A25 doubleClick 动作

1. 目标是什么？引用 PLAYBOOK A25：新增 `doubleClick`，让智能体能触发真实 Web UI 常见的 `dblclick` 交互，而不是用两次 click 或 evaluate 绕开。
2. 完成标准是什么？action JSON 能表达 doubleClick；复用现有 DOM target；url/state 返回 `target_not_actionable`；capabilities 和 observe 建议动作包含 doubleClick；本地测试页有 dblclick 门控；OnePlus 8T 真机能触发 `Double click opened`；既有动作不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A24 已完成并验证；现有 `BrowserAutomationActionType`、`BrowserAutomationActionScript`、`BrowserAutomationCapabilities`、observe 建议动作和测试页可在同一协议上扩展，不需要新建平行执行器。

## 三问自检：A0/A1

1. 目标是什么？建立“浏览器自动化”长期任务，先收集资料和路线，不直接把未验证内核写进第二模式。
2. 完成标准是什么？三件套存在；资料来源可追溯；提交策略和合并风险解释清楚；输出 Kite 推荐路线。
3. 前置任务是否完成？浏览器登录 B6 已完成，设置页已有第二模式入口；B5 真实账号证据仍等待人工授权，但不阻塞浏览器自动化调研。

## 三问自检：A2/A3

1. 目标是什么？A2 收口 Kite 自动浏览器协议；A3 在 `automation_browser` 模式下建立最小 WebView 自动化 session，并采集页面快照。
2. 完成标准是什么？A2 以 `AUTOMATION_PROTOCOL.md` 覆盖协议验收；A3 第一小段至少要创建 session、采集 DOM/文本摘要、写回 `CardRunStore`，并保持第一模式行为不回退。
3. 前置任务是否完成？A0/A1 已完成；设置页第二模式入口已存在；OAuth/SSO 仍由 `BrowserHandoffPolicy` 外部分流，不阻塞 A3。

## 三问自检：A3 action 协议

1. 目标是什么？补齐 A3 未完成项：用通用 action 协议在本地页面上完成至少一次真实交互，不写死某个业务网站。
2. 完成标准是什么？至少支持 `find`、`click`、`type`、`waitFor` 的基础模型和执行结果；有本地 HTTP endpoint 可提交 action；OnePlus 8T 上能通过 endpoint 对本地测试页完成一条动作链并写回状态。
3. 前置任务是否完成？A3 第一刀已完成 session、snapshot、CardRunStore 写回和真机 smoke；LocalServer 已有 127.0.0.1 绑定，可扩展受控 endpoint。

## 三问自检：A5 扩展能力

1. 目标是什么？在现有自动浏览器协议上补齐滚动、action 后最新 snapshot、截图证据、受限 evaluate 和控制台证据入口。
2. 完成标准是什么？`scroll`、`evaluate`、`screenshot` 能通过 `/browser-automation/action` 返回结构化结果；成功 action 后能刷新最新 snapshot；`/browser-automation/session` 能读到最新证据；非自动模式仍拒绝 action。
3. 前置任务是否完成？A3/A4 已完成并在 OnePlus 8T 通过；LocalServer、ControllerRegistry、SessionStore 已建立，可继续扩展而不另建平行状态。

## 三问自检：A6 网络证据与动作历史

1. 目标是什么？引用 PLAYBOOK A6：补齐网络证据和动作历史查询，让自动浏览器能解释页面变化来自哪些 action 和网络请求。
2. 完成标准是什么？`/browser-automation/session` 能返回多条 action result；有 action history 与 network 查询 endpoint；网络证据脱敏且不保存头/cookie/token；本地测试页真机 fetch 后可查询到网络记录；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A5 已完成并验证；现有 `BrowserAutomationSessionStore`、`BrowserAutomationController`、`KiteWebShell` 和 LocalServer endpoint 可继续扩展。

## 三问自检：A7 可访问树/语义观察

1. 目标是什么？引用 PLAYBOOK A7：在自动浏览器 snapshot 中补由 Web DOM 派生的可访问树摘要，让 AI/脚本能用 role/name/state 理解页面。
2. 完成标准是什么？snapshot parser 能解析和持久化 accessibility 节点；`/browser-automation/session` 返回 `accessibility`；本地测试页真机能看到 textbox、button、status、section 等语义节点；不保存 cookie/token/密码值/隐藏文本；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A6 已完成并验证；snapshot parser、session store 和 LocalServer public JSON 已存在，可在现有 snapshot 协议上扩展。

## 三问自检：A8 role/name 语义定位

1. 目标是什么？引用 PLAYBOOK A8：让 action target 支持 `kind=role` + `name`，把 A7 已观察到的 role/name 语义真正用于页面操作。
2. 完成标准是什么？action JSON 可表达 role/name；find/click/type/waitFor/scroll 均可复用 role/name target；匹配时不读取 password value；本地测试页真机用 role/name 跑通 `type -> click -> waitFor`；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A7 已完成并验证；现有 action 模型、LocalServer action endpoint 和 `BrowserAutomationActionScript` 可在兼容旧 target 的基础上扩展。

## 三问自检：A9 批量动作 run 接口

1. 目标是什么？引用 PLAYBOOK A9：新增 `POST /browser-automation/run`，让 AI/脚本一次提交多步 action，并拿到完整结构化结果。
2. 完成标准是什么？run JSON 能包含 actions 数组并继承公共 session；响应包含 runId/status/计数/stoppedOnFailure/results；失败可按 `stopOnFailure` 停止；capabilities 暴露 endpoint；本地测试页真机一次 run 跑通 `type -> click -> waitFor`；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A8 已完成并验证；现有 `/browser-automation/action`、action handler、session store 和 result 持久化可复用，不需要另建执行器。

## 三问自检：A10 open-run 一体入口

1. 目标是什么？引用 PLAYBOOK A10：新增 `POST /browser-automation/open-run`，让 AI/脚本一次请求完成打开 URL、等待 automation session ready、执行批量 run。
2. 完成标准是什么？open-run JSON 能表达 url/actions/stopOnFailure/openTimeoutMs；自动模式下一次请求跑通 `type -> click -> waitFor`；响应包含 open 摘要和 run 结果；默认模式返回 `mode_not_enabled`；capabilities 暴露 endpoint；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A9 已完成并验证；现有 `openWeb` 回调、`BrowserAutomationSessionStore`、A9 run 执行链和模式门禁可复用。

## 三问自检：A11 run 结果恢复查询

1. 目标是什么？引用 PLAYBOOK A11：持久化 run/open-run 汇总结果，并提供 `/browser-automation/runs` 让外部智能体按 `runId` 或 `sessionId` 恢复查询。
2. 完成标准是什么？run result 进入同一份 automation store；`/runs?sessionId=...` 返回最近 run；`/runs?runId=...` 返回指定 run；`/session` 包含最近 run 摘要；成功和失败 run 都可查；不保存敏感原文；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A10 已完成并验证；`BrowserAutomationSessionStore` 已持久化 session/snapshot/action/console/network，可继续扩展 run history。

## 三问自检：A12 observe 观察入口

1. 目标是什么？引用 PLAYBOOK A12：新增 `/browser-automation/observe`，让外部智能体用一次轻量查询获得页面状态、可交互语义节点、建议 target 和最近执行状态。
2. 完成标准是什么？observe 返回 session/page/interactive/recentAction/recentRun/limits；interactive 来自现有 accessibility；建议 target 可直接用于 action；不执行动作、不打开页面、不绕过登录边界；不泄露敏感原文；capabilities 暴露 endpoint；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A11 已完成并验证；session、snapshot、action、run 都已在同一 store 内，可在 LocalServer 上增加只读观察入口。

## 三问自检：A13 screenshot artifact 下载

1. 目标是什么？引用 PLAYBOOK A13：新增 `/browser-automation/artifact?path=...`，让 screenshot action 生成的 PNG 证据可以被外部智能体通过受控 HTTP 读取。
2. 完成标准是什么？artifact endpoint 能下载 screenshot PNG；返回 `image/png` 和正确 PNG 文件头；非 screenshots 目录、路径穿越、非 PNG、缺失文件都被明确拒绝；capabilities 暴露 endpoint；不泄露敏感原文；单测、构建、OnePlus 8T 验证通过。
3. 前置任务是否完成？A12 已完成并验证；A5 已有 screenshot action 和应用私有截图目录，可在 LocalServer 上增加只读文件出口。

## 三问自检：A14 evaluate 安全边界可观察

1. 目标是什么？引用 PLAYBOOK A14：在 `/browser-automation/observe` 的 page 摘要中增加 `scope` 和 `trustedForEvaluate`，让智能体决策前知道当前页面是否允许 evaluate。
2. 完成标准是什么？本地页面返回 `scope=local`、`trustedForEvaluate=true`；普通 HTTPS 页面返回 `scope=remote`、`trustedForEvaluate=false`；空白/未知 URL 返回 `scope=unknown`、`trustedForEvaluate=false`；observe 仍只读；单测和构建通过。
3. 前置任务是否完成？A13 已完成并验证；A5 的 evaluate 信任边界和 A12 的 observe 摘要都已存在，可在观察层复用。

## 三问自检：A15 URL 等待和查找 target

1. 目标是什么？引用 PLAYBOOK A15：补 `kind=url` target，让 `find` / `waitFor` 能可靠等待当前页面 URL 的 query、path 或 hash 变化。
2. 完成标准是什么？action JSON 可表达 URL target；`find` / `waitFor` 支持 contains/exact；不可操作动作返回 `target_not_actionable`；capabilities 声明包含 `url`；单测、构建和 OnePlus 8T 真机验证通过。
3. 前置任务是否完成？A14 已完成并验证；现有 action/run/open-run 执行链、模式门禁和脱敏结果都可复用，不需要新建平行状态。

## 三问自检：A16 页面状态等待 target

1. 目标是什么？引用 PLAYBOOK A16：补 `kind=state` target，让 `find` / `waitFor` 能等待 `domReady`、`complete` 和短暂 `idle`。
2. 完成标准是什么？action JSON 可表达 state target；`find` / `waitFor` 支持 domReady/complete/idle；不可操作动作返回 `target_not_actionable`；capabilities 声明包含 `state`；单测、构建和 OnePlus 8T 真机验证通过。
3. 前置任务是否完成？A15 已完成并验证；现有 action/run/open-run 执行链、模式门禁和 action result 持久化可复用，不需要新建平行状态。

## 三问自检：A17 键盘 press 动作

1. 目标是什么？引用 PLAYBOOK A17：新增 `press` action，让智能体可以在受控 WebView 内对目标元素或当前 activeElement 派发 Enter、Escape、Tab、Space、Backspace、Delete、方向键和单字符。
2. 完成标准是什么？action JSON 可表达 press；press 复用现有 DOM target；`target.kind=none` 时使用当前 activeElement 或 body；URL/state target 返回 `target_not_actionable`；capabilities 声明包含 `press`；本地测试页真机用 `type -> press Enter -> waitFor` 通过；单测、构建和 OnePlus 8T 验证通过。
3. 前置任务是否完成？A16 已完成并验证；现有 action/run/open-run 执行链、模式门禁、role/name selector 和测试页可直接复用，不需要新建平行状态。

## 三问自检：A18 表单 select 动作

1. 目标是什么？引用 PLAYBOOK A18：新增 `select` action，让智能体可以在受控 WebView 内对 HTML `<select>` 元素按 option value、可见文本或 index 做表单选择。
2. 完成标准是什么？action JSON 可表达 select；select 复用现有 DOM target；支持 value/text/index 选择并派发 input/change；非 select 返回 `target_not_selectable`；URL/state target 返回 `target_not_actionable`；capabilities 和 observe 声明包含 select；本地测试页真机跑通 `type -> select -> click/press -> waitFor`；单测、构建和 OnePlus 8T 验证通过。
3. 前置任务是否完成？A17 已完成并验证；role/name 已能定位 combobox，run/open-run、模式门禁、测试页和 action result 持久化均可复用，不需要新建平行状态。

## 三问自检：A19 状态控件 check 动作

1. 目标是什么？引用 PLAYBOOK A19：新增 `check` action，让智能体可以在受控 WebView 内把 checkbox、radio 和 switch-like 控件设置到明确状态，而不是只能 click 翻转。
2. 完成标准是什么？action JSON 可表达 check；check 复用现有 DOM target；checkbox 支持 true/false/toggle；radio 支持 true 且 false 返回明确错误；非状态控件返回 `target_not_checkable`；URL/state target 返回 `target_not_actionable`；capabilities 和 observe 声明包含 check；本地测试页真机跑通 `type -> check -> click/press -> waitFor`；单测、构建和 OnePlus 8T 验证通过。
3. 前置任务是否完成？A18 已完成并验证；role/name 已能定位 checkbox/radio 等 DOM 控件，run/open-run、模式门禁、测试页和 action result 持久化均可复用，不需要新建平行状态。

## 三问自检：A20 截图 artifact URL 与 observe 恢复入口

1. 目标是什么？引用 PLAYBOOK A20：让 screenshot action 生成的截图证据在 action result、session/actions/runs 和 observe 恢复入口里直接带可下载 artifact URL，减少外部智能体拼接路径的脆弱性。
2. 完成标准是什么？screenshot action result 同时包含 `artifactPath` 和相对 `artifactUrl`；session/actions/runs 继承该表达；observe 的 `recentAction` 和 `recentRun` 能暴露 artifact 摘要；artifact URL 仍只走 `/browser-automation/artifact` 白名单；OnePlus 8T 可用返回 URL 下载 PNG；单测、构建和真机验证通过。
3. 前置任务是否完成？A19 已完成并验证；A13 artifact resolver 和 endpoint 已存在，A5 screenshot action、A11 run history、A12 observe 均可复用，不需要新建平行文件出口。

## 三问自检：A21 click pointer/mouse 事件序列

1. 目标是什么？引用 PLAYBOOK A21：增强 `click` action，让它在 `element.click()` 前派发受控 pointer/mouse down/up 序列，覆盖依赖 pointerdown/mousedown 的真实 Web UI。
2. 完成标准是什么？click 派发 pointerdown/mousedown/pointerup/mouseup 后再触发 activation；坐标取目标中心；不支持 PointerEvent 时仍派发 mouse 事件；测试页新增 pointer-gated 按钮；OnePlus 8T 真机能点击成功；既有普通 click、check/select/type 不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A20 已完成并验证；现有 action 脚本和测试页可继续扩展，不需要新建执行器，不改变 OAuth/SSO 外部分流或系统输入边界。

## 三问自检：A22 hover 动作

1. 目标是什么？引用 PLAYBOOK A22：新增 `hover` action，让智能体可以在受控 WebView 内触发依赖 hover、pointerover、mouseenter 或 mousemove 的菜单、tooltip 和 popover。
2. 完成标准是什么？action JSON 可表达 hover；hover 复用 css/text/role/role+name DOM target；URL/state target 返回 `target_not_actionable`；capabilities 和 observe 声明包含 hover；测试页有 hover-gated 控件；OnePlus 8T 真机能触发 `Hover menu revealed`；既有动作不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A21 已完成并验证；现有 action 脚本、pointer/mouse 事件 helper、observe 和测试页可继续扩展，不需要新建执行器，不改变 OAuth/SSO 外部分流或系统输入边界。

## 三问自检：A23 受控导航动作

1. 目标是什么？引用 PLAYBOOK A23：新增 `navigate` action，让智能体可以在同一受控 WebView session 内执行 `reload`、`back`、`forward`。
2. 完成标准是什么？action JSON 可表达 navigate back/forward/reload；navigate 只接受 `target.kind=none`；任意 URL 或 DOM target 不被当成跳转目标；capabilities 声明包含 navigate；测试页有 hash navigation 证据；OnePlus 8T 真机能跑通 back/forward/reload；既有动作不回退；单测、构建和真机验证通过。
3. 前置任务是否完成？A22 已完成并验证；现有 action/run/open-run 执行链、URL/state wait、模式门禁和测试页可直接复用。任意 URL 打开仍走 `open-run` / `open-web` 与 `BrowserHandoffPolicy`，A23 不改变 OAuth/SSO 外部分流。

## 三问自检：A24 observe 能力摘要

1. 目标是什么？引用 PLAYBOOK A24：让 `/browser-automation/observe` 返回智能体决策所需的 capabilities 摘要，并让 observe 与 `/browser-automation/capabilities` 共享同一份能力来源。
2. 完成标准是什么？observe 返回 actions/targets/endpoints/runs/authBoundary/evaluate 边界；actions 包含 click/hover/navigate/screenshot；targets 包含 role+name/url/state；endpoints 包含 run/open-run/artifact；capabilities endpoint 与 observe 不再各自硬编码列表；observe 仍只读；单测、构建和 OnePlus 8T 真机验证通过。
3. 前置任务是否完成？A23 已完成并验证；现有 `BrowserAutomationObservation`、LocalServer capabilities endpoint 和协议文档可直接扩展，不改变 action 执行、OAuth/SSO 外部分流或默认模式门禁。

## 当前判断

- 第一模式“WebView + 系统浏览器登录”在机制层已经成功：用户已人工确认系统浏览器方案有效，B4/B6 均完成；B5 剩余的是 Codex/Claude 真实账号授权完成证据。
- 第二模式“自动浏览器”是新目标，不应继续塞进 `docs/browser-login` 的完成定义里。
- 默认 Git 策略：先在 `codex/browser-login-return` 做本地 checkpoint commit；不要直接提交主线。等 X11 分支也准备好后，再分别 merge/rebase 到主线并解决冲突。

## 待验证清单

- [x] 已建立 Codex 目标：浏览器自动化。
- [x] `docs/browser-automation/PLAYBOOK.md` 已写入。
- [x] `docs/browser-automation/PROGRESS.md` 已写入。
- [x] `docs/browser-automation/DECISIONS.md` 已写入。
- [x] `docs/browser-automation/RESEARCH.md` 已写入。
- [x] A1 资料来源不少于 8 个。
- [x] A1 输出推荐路线、备选路线、反路线。
- [x] A2 协议文档已写入。
- [x] A3 自动模式能创建 automation session。
- [x] A3 自动模式能采集页面快照并写回运行报告。
- [x] A3 单测和 debug 构建通过。
- [x] A3 OnePlus 8T 真机最小验证通过。
- [x] A3 通用 action 协议完成至少一个本地页面交互。
- [x] A3 默认模式 `webview_system_auth` 做回归 smoke。
- [x] A4 已有受控 action endpoint。
- [x] A4 补 session/status 查询 endpoint 和更完整错误查询。
- [x] A5 scroll/evaluate/screenshot 等扩展能力。
- [x] A6 网络证据和 action 历史查询。
- [x] A7 可访问树/语义观察。
- [x] A8 role/name 语义定位动作。
- [x] A9 批量动作 run 接口。
- [x] A10 open-run 一体入口。
- [x] A11 run 结果恢复查询。
- [x] A12 observe 观察入口。
- [x] A13 screenshot artifact 下载。
- [x] A14 observe evaluate 安全边界。
- [x] A15 URL target 的 find/waitFor。
- [x] A16 页面状态 target 的 find/waitFor。
- [x] A17 键盘 press action。
- [x] A18 表单 select action。
- [x] A19 状态控件 check action。
- [x] A20 截图 artifact URL 与 observe 恢复入口。
- [x] A21 click pointer/mouse 事件序列。
- [x] A22 hover 动作。
- [x] A23 受控导航动作。
- [x] A24 observe 能力摘要。
- [ ] 浏览器线 checkpoint commit 边界明确；提交时必须显式 add 文件，不用 `git add .`。

## 2026-07-05 A24 observe 能力摘要完成记录

- 代码：新增 `BrowserAutomationCapabilities`，集中维护 actions、targets、runs、endpoints、authBoundary 和 evaluate 边界；`KiteLocalServer` 的 `/browser-automation/capabilities` 与 `BrowserAutomationObservation` 共同读取该对象。
- observe：`/browser-automation/observe` 新增 `capabilities` 对象，返回数组形态的 `actions`、`targets`、`runs`、`endpoints`，以及 `authBoundary=oauth_and_sso_stay_external`、`evaluate=local_trusted_only`、`source=BrowserAutomationCapabilities`。
- 兼容：`/browser-automation/capabilities` 继续保留旧的逗号分隔字符串字段 `actions` / `targets` / `runs` / `endpoints`，同时新增 `actionList` / `targetList` / `runList` / `endpointList` 数组。
- 协议：`AUTOMATION_PROTOCOL.md` 的 observe 示例已补 `capabilities`，说明 observe 数组字段与 capabilities endpoint 共享来源。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，覆盖 observe capabilities 和 endpoint/list 同步。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242677266 bytes；已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机 observe：OnePlus 8T `3f8bbaad` 临时切到 `automation_browser`，`runId=a24-observe-capabilities-session` 打开测试页并等待 `domReady` 成功；`GET /browser-automation/observe?sessionId=...` 返回 `actionCount=13`，`actions` 包含 `click`、`hover`、`navigate`、`screenshot`，`targets` 包含 `role+name`、`url`、`state`，`endpoints` 包含 `/browser-automation/run`、`/browser-automation/open-run`、`/browser-automation/artifact`。
- 真机 capabilities：`GET /browser-automation/capabilities` 返回旧字符串字段和 `actionList`/`targetList`/`endpointList` 数组字段，`authBoundary=oauth_and_sso_stay_external`、`evaluate=local_trusted_only`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a24-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 无 whitespace error；仅输出既有 LF/CRLF 转换警告。

## 2026-07-05 A23 受控导航动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Navigate("navigate")`；`BrowserAutomationActionScript` 在 DOM 查询前处理 `navigate`，支持 `back`、`forward`、`reload` 和别名 `goBack`、`goForward`、`refresh`。
- 行为边界：`navigate` 只接受 `target.kind=none`；携带 DOM/URL/state target 返回 `Rejected/target_not_actionable`；`value` 为 URL 或其它字符串返回 `Failed/unsupported_navigation_value`，不会设置 `location.href`。
- 安全边界：A23 不打开任意 URL；打开 URL 仍走 `open-run` / `open-web` 和 `BrowserHandoffPolicy`，OAuth/SSO 外部分流不变。
- 能力声明：`/browser-automation/capabilities` 的 `actions` 已更新为 `snapshot,find,click,hover,navigate,type,press,select,check,waitFor,scroll,evaluate,screenshot`。
- 测试页：`/browser-automation/test-page` 新增 `Go first hash`、`Go second hash` 两个 link 和 `nav-result` 状态，用 hash 历史构造 back/forward 验证。
- 协议和调研：`AUTOMATION_PROTOCOL.md` 已写明 navigate 动作、错误码和 target 边界；`RESEARCH.md` 的第一阶段动作清单已补 `navigate(back/forward/reload)`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242673718 bytes；已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机正向：OnePlus 8T `3f8bbaad` 临时切到 `automation_browser`，`runId=a23-navigate-history-success` 执行 `waitFor state=domReady`、点击 `Go first hash`、等待 `#a23-first`、点击 `Go second hash`、等待 `#a23-second`、`navigate back`、等待 `#a23-first`、`navigate forward`、等待 `#a23-second`、`navigate reload`、等待 `domReady`、`evaluate data-nav-hash`，`status=Succeeded`、`completedCount=12`、evaluate 返回 `#a23-second`。
- 真机负向：`runId=a23-navigate-url-rejected` 执行 `navigate value=https://example.com/`，返回 `status=Failed`、`errorCode=unsupported_navigation_value`，当前 URL 仍停在测试页 `#a23-second`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a23-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 无 whitespace error；仅输出既有 LF/CRLF 转换警告。

## 2026-07-05 A22 hover 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Hover("hover")`；`BrowserAutomationActionScript` 新增 `dispatchHoverPrelude(...)`，对目标中心派发 `pointerover`、`pointerenter`、`mouseover`、`mouseenter`、`pointermove`、`mousemove`。
- 行为：`hover` 复用 css/text/role/role+name DOM target；目标缺失、不可见沿用现有结构化错误；`kind=url` / `kind=state` 仍由通用门禁返回 `target_not_actionable`；不调用 `element.click()`，不做 Android 全局鼠标、触摸或 Accessibility 注入。
- 能力声明：`/browser-automation/capabilities` 的 `actions` 已更新为 `snapshot,find,click,hover,type,press,select,check,waitFor,scroll,evaluate,screenshot`。
- observe：按钮、链接等可点击语义节点的 `suggestedActions` 更新为 `click,hover,find,waitFor`；checkbox/radio/switch 仍优先 `check`。
- 测试页：`/browser-automation/test-page` 新增 `Hover reveal menu` 按钮和 `hover-result` 状态；只有收到 hover/pointer/mouse over 或 move 后才显示 `Hover menu revealed`。
- 协议和调研：`AUTOMATION_PROTOCOL.md` 已写明 hover 动作和 target 边界；`RESEARCH.md` 的第一阶段动作清单已补 `hover`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242672062 bytes；已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机 hover 正向：OnePlus 8T `3f8bbaad` 临时切到 `automation_browser`，`runId=a22-hover-success` 执行 `waitFor state=domReady`、`hover role=button name="Hover reveal menu"`、`waitFor role=status name="Hover menu revealed"`、`evaluate` 成功；evaluate 返回 `revealed:pointer:mouse`。
- 真机旧动作回归：`runId=a22-existing-actions-regression` 串联 `type`、`select`、`check`、`press`、`waitFor`、`type`、`click`、`waitFor` 共 8 个动作，`status=Succeeded`、`completedCount=8`。
- 真机 observe：`GET /browser-automation/observe?...` 返回 `Hover reveal menu` 的 `suggestedActions=click,hover,find,waitFor`，页面 `scope=local`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a22-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 无 whitespace error；仅输出既有 LF/CRLF 转换警告。

## 2026-07-05 A21 click pointer/mouse 事件序列完成记录

- 代码：`BrowserAutomationActionScript` 新增 `eventPointOf(...)`、`dispatchPointerMouse(...)`、`dispatchClickPrelude(...)`；`click` action 现在先派发 `pointerdown`、`mousedown`、`pointerup`、`mouseup`，再调用 `element.click()`。
- 行为：事件坐标取目标元素中心点；事件带 `bubbles=true`、`cancelable=true`、`composed=true`；浏览器不支持 `PointerEvent` 时忽略 pointer 分支并保留 mouse down/up。
- 测试页：`/browser-automation/test-page` 新增 `Pointer gated click` 按钮和 `pointer-result` 状态；只有收到 pointer/mouse down 后 click 才显示 `Pointer sequence clicked`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242670898 bytes，已安装到 OnePlus 8T `3f8bbaad`。
- 真机 pointer 正向：`runId=a21-pointer-click-success` 执行 `click role=button name="Pointer gated click"` 后 `waitFor role=status name="Pointer sequence clicked"` 成功，`evaluate` 返回 `clicked:pointer:mouse`，说明 pointerdown 和 mousedown 都到达。
- 真机普通 click 回归：`runId=a21-ordinary-click-regression` 执行 `type role=textbox name=Name value="A21 Click"`、`click role=button name="Apply greeting"`、`waitFor role=status name="Hello A21 Click"` 成功。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a21-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A20 截图 artifact URL 与 observe 恢复入口完成记录

- 代码：`BrowserAutomationActionResult.toJson()` 对带 `artifactPath` 的结果新增相对 `artifactUrl=/browser-automation/artifact?path=...`；`BrowserAutomationObservation` 的 `recentAction` 新增 `snapshotId`、`artifactPath`、`artifactUrl`，`recentRun` 新增 `artifactCount`、`latestArtifactPath`、`latestArtifactUrl`。
- 行为：`/browser-automation/action`、`/browser-automation/session`、`/browser-automation/actions`、`/browser-automation/runs` 复用同一 action result JSON，screenshot 结果都能直接带可下载 artifact URL。
- 安全：没有新增文件出口；`artifactUrl` 仍只指向既有 `/browser-automation/artifact`，由 `BrowserAutomationArtifactResolver` 白名单限制在应用私有 screenshots PNG 目录。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242669026 bytes，已安装到 OnePlus 8T `3f8bbaad`。
- 真机正向：`runId=a20-artifact-url-success` 执行 `waitFor state=domReady`、`type role=textbox name=Name value="A20 Artifact"`、`click Apply greeting`、`waitFor Hello A20 Artifact`、`screenshot`，`status=Succeeded`、`completedCount=5`。
- 真机下载：screenshot result 返回 `artifactPath=/data/user/0/com.kite.app/files/browser-automation/screenshots/shot_f3c2842e9cfa44cd88bbb3b3997a7797_1783229782370.png` 和相对 `artifactUrl`；使用 `http://127.0.0.1:18791{artifactUrl}` 下载 PNG 成功，大小 76311 bytes，文件头 `89 50 4E 47 0D 0A 1A 0A`。
- 真机恢复查询：`/browser-automation/observe` 的 `recentAction` 返回 screenshot `artifactPath/artifactUrl`，`recentRun` 返回 `artifactCount=1` 和 `latestArtifactUrl`；`/browser-automation/session` 与 `/browser-automation/runs?runId=a20-artifact-url-success` 的 screenshot result 均包含 `artifactUrl`。
- 真机安全负向：请求 `/browser-automation/artifact?path=/data/user/0/com.kite.app/files/shared_prefs/kite_app_settings.xml` 返回 `ok=false`、`error=artifact_path_not_allowed`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a20-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A19 状态控件 check 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Check("check")`；`BrowserAutomationActionScript` 新增 `normalizeCheckValue(...)`、`checkTargetInfo(...)` 和 `check` action 分支。
- 行为：`check` 可对 HTML `<input type=checkbox>`、`<input type=radio>`，以及 `role=checkbox|radio|switch` 的 ARIA 状态控件设置状态；`value` 为空时默认 true，也支持 `true`、`false`、`toggle` 及 `checked/on/1`、`unchecked/off/0`。
- 负向：非状态控件返回 `Failed`、`errorCode=target_not_checkable`；radio 请求 false 返回 `target_not_checkable` 和 `radio cannot be unchecked directly`；`kind=state` / `kind=url` 仍返回 `Rejected`、`errorCode=target_not_actionable`。
- 测试页：`/browser-automation/test-page` 新增 `Subscribe updates` checkbox 和 `Plan` radio 组；默认不改变旧 `Hello <name>` 文案，勾选后显示 `Hello <name> +Subscribed [pro]` 等结果。
- observe：checkbox、radio 和 switch 的 `suggestedActions` 更新为 `check,click,find,waitFor`。
- 能力声明：`/browser-automation/capabilities` 的 `actions` 已更新为 `snapshot,find,click,type,press,select,check,waitFor,scroll,evaluate,screenshot`。
- 协议和调研：`AUTOMATION_PROTOCOL.md` 已写明 check target 边界；`RESEARCH.md` 的第一阶段动作清单已补 `check`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242667690 bytes，已安装到 OnePlus 8T `3f8bbaad`；已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机正向：`runId=a19-check-checkbox-radio-success-2`，执行 `waitFor state=domReady`、`type role=textbox name=Name value="A19 Check"`、`check role=checkbox name="Subscribe updates" value=true`、`check role=radio name="Pro plan" value=true`、`click role=button name="Apply greeting"`、`waitFor role=status name="Hello A19 Check +Subscribed [pro]"`，`status=Succeeded`、`completedCount=6`。
- 真机 false：`runId=a19-check-false-success`，先 check true 再 check false，`evaluate document.getElementById("subscribe").checked` 返回 `evaluate: false`，最终 `status=Succeeded`、`completedCount=7`。
- 真机负向：`actionId=a19-check-textbox-rejected` 对 textbox 提交 check，返回 `errorCode=target_not_checkable`；`actionId=a19-check-radio-false-rejected` 对 radio 提交 false，返回 `target_not_checkable`；`actionId=a19-check-state-rejected` 对 state target 提交 check，返回 `Rejected/target_not_actionable`。
- 真机观察和截图：observe 返回 checkbox/radio `suggestedActions=check,click,find,waitFor`；screenshot action `a19-screenshot-2` 返回 `Succeeded`，artifactPath 为 `/data/user/0/com.kite.app/files/browser-automation/screenshots/shot_fcab7e9f891941a3a24433667c540815_1783229436397.png`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a19-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 通过，仅有既有 LF/CRLF 提示。

## 2026-07-05 A18 表单 select 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Select("select")`；`BrowserAutomationActionScript` 新增 option 匹配和 `select` action 分支。
- 行为：`select` 只操作 HTML `<select>`；支持按 option `value`、可见文本和 `index:<n>` 选择；选中后派发 `input` 和 `change` 事件。
- 负向：非 `<select>` 目标返回 `Failed`、`errorCode=target_not_selectable`；`kind=state` / `kind=url` 仍返回 `Rejected`、`errorCode=target_not_actionable`。
- 测试页：`/browser-automation/test-page` 新增 `Tone` 下拉，默认 Plain 不改变旧 `Hello <name>` 文案；选中 tone 后显示 `Hello <name> (Formal)` 等结果。
- observe：combobox 的 `suggestedActions` 更新为 `select,click,find,waitFor`。
- 能力声明：`/browser-automation/capabilities` 的 `actions` 已更新为 `snapshot,find,click,type,press,select,waitFor,scroll,evaluate,screenshot`。
- 协议和调研：`AUTOMATION_PROTOCOL.md` 已写明 select target 边界；`RESEARCH.md` 的第一阶段动作清单已补 `press` / `select`。
- 单测和构建：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242662830 bytes，已安装到 OnePlus 8T `3f8bbaad`；已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机 value 成功：`runId=a18-select-value-success`，执行 `type role=textbox name=Name value="A18 Select"`、`select role=combobox name=Tone value=formal`、`click role=button name=Apply greeting`、`waitFor role=status name="Hello A18 Select (Formal)"`，`status=Succeeded`、`completedCount=5`。
- 真机文本成功：`runId=a18-select-text-success`，执行 `select role=combobox name=Tone value=Focused` 后用 `press Enter` 提交，最终 `waitFor role=status name="Hello A18 Text (Focused)"` 成功，`completedCount=5`。
- 真机 index 成功：`runId=a18-select-index-success`，执行 `select role=combobox name=Tone value=index:3` 后提交，最终 `waitFor role=status name="Hello A18 Index (Formal)"` 成功，`completedCount=5`。
- 真机负向：`actionId=a18-select-textbox-rejected` 对 textbox 提交 select，返回 `errorCode=target_not_selectable`；`actionId=a18-select-state-rejected` 对 state target 提交 select，返回 HTTP 409、`Rejected/target_not_actionable`。
- 真机能力查询：capabilities 返回 actions 包含 `select`；observe 的 combobox `suggestedActions=select,click,find,waitFor`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a18-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 通过，仅有既有 LF/CRLF 提示。

## 2026-07-05 A17 键盘 press 动作完成记录

- 代码：`BrowserAutomationActionType` 新增 `Press("press")`；`BrowserAutomationActionScript` 新增 `normalizePressKey(...)`、KeyboardEvent 派发和 `press` action 分支。
- 行为：`press` 支持 Enter、Escape、Tab、Space、Backspace、Delete、方向键和单字符；DOM target 会先定位、滚动并聚焦；`target.kind=none` 使用当前 `document.activeElement`，没有 activeElement 时退到 `body`。
- 负向：`kind=url` / `kind=state` 仍在脚本前段返回 `Rejected`、`errorCode=target_not_actionable`，不尝试 DOM 操作。
- 测试页：`/browser-automation/test-page` 把 greeting 逻辑抽成 `applyGreeting()`，并让 `#name` 的 Enter keydown 触发同一逻辑，作为无副作用真机验证页。
- observe：textbox 的 `suggestedActions` 更新为 `type,press,find,waitFor`，让智能体能自然组成 `type -> press Enter -> waitFor`。
- 能力声明：`/browser-automation/capabilities` 的 `actions` 已更新为 `snapshot,find,click,type,press,waitFor,scroll,evaluate,screenshot`。
- 单测和构建：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242658242 bytes，已安装到 OnePlus 8T `3f8bbaad`；已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。
- 真机成功 open-run：`runId=a17-press-enter-success`，URL `http://127.0.0.1:8791/browser-automation/test-page?a17=press#ready`；四步 `waitFor state=domReady`、`type role=textbox name=Name value="A17 Press"`、`press role=textbox name=Name value=Enter`、`waitFor role=status name="Hello A17 Press"` 均返回 `Succeeded`，`completedCount=4`。
- 真机 activeElement 成功：`runId=a17-press-active-element-success`，输入 `A17 Active` 后执行 `press kind=none value=Enter`，随后 `waitFor role=status name="Hello A17 Active"` 成功，`completedCount=4`。
- 真机负向：`actionId=a17-press-state-rejected` 对同一 session 提交 `press kind=state`，返回 HTTP 409、`status=Rejected`、`errorCode=target_not_actionable`。
- 真机能力查询：capabilities 返回 actions 包含 `press`；observe 的 textbox `suggestedActions=type,press,find,waitFor`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a17-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。
- 静态检查：`git diff --check` 通过，仅有既有 LF/CRLF 提示。

## 2026-07-05 A16 页面状态等待 target 完成记录

- 代码：`BrowserAutomationTargetKind` 新增 `State("state")`；`BrowserAutomationActionScript` 在 DOM 查询前处理页面状态 target。
- 行为：`find` / `waitFor` 支持 `state=domReady`、`state=complete` 和 `state=idle`。`idle` 使用页面内 `MutationObserver` 记录最近 DOM 变化，配合现有 `waitFor` 轮询等待稳定。
- 参数：`idle` 默认 500ms；可用 action `value` 指定毫秒数，也可写成 `target.value=idle:<ms>`。
- 负向：`click` / `type` / `scroll` 等非状态读取动作对 `kind=state` 返回 `Rejected`、`errorCode=target_not_actionable`，不尝试 DOM 操作。
- 能力声明：`/browser-automation/capabilities` 的 `targets` 已更新为 `css,text,role,role+name,url,state`。
- 协议：`AUTOMATION_PROTOCOL.md` 已写明 state target 只用于 `find` / `waitFor`。
- 单测和构建：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242652182 bytes，已安装到 OnePlus 8T `3f8bbaad`。
- 真机成功 open-run：`runId=a16-state-wait-success`，URL `http://127.0.0.1:8791/browser-automation/test-page?a16=state-wait#ready`；三步 `waitFor state=domReady`、`waitFor state=complete`、`waitFor state=idle value=300` 均返回 `Succeeded`，`completedCount=3`。
- 真机负向：`actionId=a16-click-state-rejected` 对同一 session 提交 `click kind=state`，返回 HTTP 409、`status=Rejected`、`errorCode=target_not_actionable`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a16-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A15 URL 等待和查找 target 完成记录

- 代码：`BrowserAutomationTargetKind` 新增 `Url("url")`；`BrowserAutomationActionScript` 在 DOM 查询前处理 URL target，直接读取当前 `location.href`。
- 行为：`find` / `waitFor` 支持 `kind=url` 的 `contains` 和 `exact`；匹配成功返回 `message=matched url`、`matchedCount=1`。
- 负向：`click` / `type` / `scroll` 等非 URL 动作对 `kind=url` 返回 `Rejected`、`errorCode=target_not_actionable`，不尝试 DOM 操作。
- 能力声明：`/browser-automation/capabilities` 的 `targets` 已更新为 `css,text,role,role+name,url`。
- 协议：`AUTOMATION_PROTOCOL.md` 已写明 URL target 不是 DOM selector，只用于 `find` / `waitFor`。
- 单测和构建：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：debug APK 产物 `app\build\outputs\apk\debug\app-debug.apk`，大小 242648386 bytes，已安装到 OnePlus 8T `3f8bbaad`。
- 真机成功 open-run：`runId=a15-url-wait-success`，URL `http://127.0.0.1:8791/browser-automation/test-page?a15=url-wait#ready`；三步 `waitFor url contains a15=url-wait`、`waitFor url contains #ready`、`find url exact ...#ready` 均返回 `Succeeded`，`completedCount=3`。
- 真机负向：`actionId=a15-click-url-rejected` 对同一 session 提交 `click kind=url`，返回 HTTP 409、`status=Rejected`、`errorCode=target_not_actionable`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a15-mode-gate` 返回 HTTP 409、`Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A14 evaluate 安全边界可观察完成记录

- 代码：新增 `BrowserAutomationPageTrust`，把 evaluate 的页面信任判断抽成共享策略；`BrowserAutomationController` 和 `BrowserAutomationObservation` 共用同一份规则。
- observe：`page` 摘要新增 `scope` 和 `trustedForEvaluate`。本地/可信页面返回 `scope=local`、`trustedForEvaluate=true`；普通远程 HTTP/HTTPS 页面返回 `scope=remote`、`trustedForEvaluate=false`；未知 URL 返回 `scope=unknown`、`trustedForEvaluate=false`。
- 协议：`AUTOMATION_PROTOCOL.md` 已在 observe 响应示例中加入 `scope` / `trustedForEvaluate`，并写明普通远程页面不建议提交 `evaluate`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 local、remote、unknown 三类 URL 和 observe 脱敏。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机验证：`runId=a14-observe-trust` 打开 `http://127.0.0.1:8791/browser-automation/test-page?a14=trust&token=should-not-leak`；`/browser-automation/observe` 返回 `page.scope=local`、`page.trustedForEvaluate=true`、URL 中 `token=present`，完整 JSON 不包含 `should-not-leak`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a14-mode-gate` 返回 `Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A13 screenshot artifact 下载完成记录

- 代码：新增 `BrowserAutomationArtifactResolver`，只允许解析 `files/browser-automation/screenshots` 下的 `.png` 文件；路径穿越、非 PNG、截图目录外路径均拒绝。
- LocalServer：新增 `GET /browser-automation/artifact?path=...`，成功时返回 `Content-Type: image/png` 的二进制响应；`/browser-automation/capabilities` endpoint 列表包含 `/browser-automation/artifact`。
- 协议：`AUTOMATION_PROTOCOL.md` 已新增 screenshot artifact 下载说明和安全约束。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 resolver 成功、缺失、路径穿越和非 PNG 拒绝。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机截图 run：`runId=a13-screenshot-artifact`，URL `http://127.0.0.1:8791/browser-automation/test-page?a13=artifact&token=should-not-leak`，动作 `waitFor role=button name=Apply greeting` + `screenshot`；响应 `status=Succeeded`，截图 action `status=Succeeded`。
- 下载证据：artifactPath 为 `/data/user/0/com.kite.app/files/browser-automation/screenshots/shot_480dca23a0bd498b91850670b0648493_1783226355678.png`；通过 `/browser-automation/artifact?path=...` 下载到 `build\a13-artifact.png`，大小 65697 bytes，PNG 头 `89 50 4E 47 0D 0A 1A 0A`。
- 负向验证：`missing.png` 返回 `artifact_not_found`；`shot.txt` 返回 `artifact_type_not_allowed`；`../shared_prefs/kite_app_settings.xml` 和 `/data/user/0/com.kite.app/shared_prefs/kite_app_settings.xml` 返回 `artifact_path_not_allowed`。
- 泄露检查：screenshot run JSON 不包含 `should-not-leak`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a13-mode-gate` 返回 `Rejected/mode_not_enabled`、`open.requested=false`。

## 2026-07-05 A12 observe 观察入口完成记录

- 代码：新增 `BrowserAutomationObservation`，把 session、page、interactive、recentAction、recentRun 和 limits 合成为智能体可读的紧凑观察 JSON。
- LocalServer：新增 `GET /browser-automation/observe`；`/browser-automation/capabilities` endpoint 列表包含 `/browser-automation/observe`。
- 语义 target：`interactive[].suggestedTarget` 由现有 accessibility 节点生成，格式为 `{"kind":"role","value":"...","name":"..."}`，可直接用于 action/run。
- 稳定性修正：Web DOM 派生 `accessibleNameOf(...)` 不再把 input 当前 value 拼进 role/name；真机 observe 中 textbox target 稳定为 `textbox/Name 输入名字`，不含当前输入值 `A12 Stable`。
- 安全：observe 只读，不执行动作、不打开页面；输出沿用 URL 和文本脱敏，不包含 token/password/authorization/Bearer 原文。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 observe target、隐藏节点过滤和敏感文本脱敏。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机 open-run：`runId=a12-open-run-observe-final`，session `7d236b7430074a73bf20793bfa571302`，一次执行 `type role=textbox name=Name value="A12 Stable"`、`click role=button name=Apply greeting`、`waitFor role=status name=Hello A12 Stable`；响应 `status=Succeeded`、`completedCount=3`。
- 真机 observe：`GET /browser-automation/observe?sessionId=7d236b7430074a73bf20793bfa571302&interactiveLimit=10&textLimit=800` 返回 `page.title=Kite Automation Test`、`page.text` 包含 `Hello A12 Stable`、`recentRun=a12-open-run-observe-final/Succeeded/3`，`interactive` 包含 `textbox/Name 输入名字`、`button/Apply greeting`、`status/automation result Hello A12 Stable` 等 suggestedTarget。
- latest 查询：不传 `sessionId` 的 `/browser-automation/observe?interactiveLimit=5&textLimit=300` 返回同一最新 ready session。
- 泄露检查：observe JSON 不包含 `should-not-leak`、`a7-password-should-not-leak`、`a7-hidden-text-should-not-leak`，也不包含不稳定的 `Name 输入名字 A12 Stable` textbox target。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a12-mode-gate` 返回 `Rejected/mode_not_enabled`、`open.requested=false`，observe 仍只读查询已有 session。

## 2026-07-05 A11 run 结果恢复查询完成记录

- 代码：`BrowserAutomationRunResult` 新增 `completedAt`；`BrowserAutomationSessionStore` 新增 `runs_v1`，提供 `saveRunResult(...)`、`getRun(...)`、`recentRuns(...)`。
- LocalServer：新增 `GET /browser-automation/runs`；`GET /browser-automation/session` 返回最近 `runs`；`POST /browser-automation/run` 和 `POST /browser-automation/open-run` 会保存 run 汇总。
- 安全：`BrowserAutomationRedactor.safeText(...)` 增加通用敏感赋值和 Bearer 片段脱敏；run history 不保存 token/password/authorization 原文。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242631546 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机成功 open-run：`runId=a11-open-run-success`，URL `http://127.0.0.1:8791/browser-automation/test-page?a11=success&token=should-not-leak`，一次执行 `type role=textbox name=Name value="A11 Runs"`、`click role=button name=Apply greeting`、`waitFor role=status name=Hello A11 Runs`；响应 `status=Succeeded`、`completedCount=3`，URL 中 token 脱敏为 `token=present`。
- 真机失败 run：`runId=a11-run-failure`，点击不存在的 `button name=Missing A11 Button`；响应 `status=Failed`、`completedCount=1`、`stoppedOnFailure=true`、`errorCode=target_not_found`。
- 恢复查询证据：`/browser-automation/runs?runId=a11-open-run-success` 返回成功 run；`/browser-automation/runs?runId=a11-run-failure` 返回失败 run；`/browser-automation/runs?sessionId=75d97b1c275a45878cb9a732a0477973&limit=5` 和 `/browser-automation/session?...&runLimit=5` 均返回 `a11-run-failure,a11-open-run-success`。
- 泄露检查：上述 run/list/session JSON 中不包含 `should-not-leak`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 open-run `runId=a11-mode-gate` 返回 `Rejected/mode_not_enabled`、`open.requested=false`，并可通过 `/browser-automation/runs?runId=a11-mode-gate` 查询。

## 2026-07-05 A10 open-run 一体入口完成记录

- 代码：新增 `BrowserAutomationOpenRunRequest`；`BrowserAutomationRunRequest.withSession(...)` 可把 ready session 继承给未指定 session 的 actions。
- LocalServer：新增 `POST /browser-automation/open-run`，先检查 `browser_runtime_mode=automation_browser`，再调用现有 `openWeb` 回调，等待目标 session ready，最后复用 A9 run 执行链。
- 能力声明：`/browser-automation/capabilities` endpoint 列表包含 `/browser-automation/open-run`。
- 响应结构：沿用 run 汇总字段，并增加 `open` 摘要，包含 `requested`、脱敏 `url`、`source`、`sessionId`、`instanceId`、`status`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 open-run request 解析和 session 继承。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242623130 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`。
- 真机成功 open-run：`runId=a10-open-run-final`，请求 `url=http://127.0.0.1:8791/browser-automation/test-page?a10=final`，一次执行 `type role=textbox name=Name value="A10 Final"`、`click role=button name=Apply greeting`、`waitFor role=status name=Hello A10 Final`；响应 `status=Succeeded`、`requestedCount=3`、`completedCount=3`、`open.status=Ready`。
- 真机状态证据：open-run 返回 session `877558707bf24d45a5a013002d367207`，session 文本包含 `Hello A10 Final`；完整 session JSON 中 `a7-password-should-not-leak=False`，`a7-hidden-text-should-not-leak=False`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/open-run` 返回 HTTP 409，错误码 `mode_not_enabled`，且 `open.requested=false`。最终门禁请求 `runId=a10-mode-gate-final`。

## 2026-07-05 A9 批量动作 run 接口完成记录

- 代码：新增 `BrowserAutomationRunRequest` 和 `BrowserAutomationRunResult`；run 请求限制最多 20 个 actions，并把公共 `sessionId` / `instanceId` 继承到每个 action。
- LocalServer：新增 `POST /browser-automation/run`；按顺序复用现有单步 `browserAutomationAction` handler，不新建 WebView 执行器，不绕过模式门禁。
- 能力声明：`/browser-automation/capabilities` 新增 `runs=sequential,stopOnFailure`，endpoint 列表包含 `/browser-automation/run`。
- 响应结构：返回 `runId`、`sessionId`、`status`、`durationMs`、`requestedCount`、`completedCount`、`stoppedOnFailure`、`errorCode`、`errorDetail` 和每步 `results`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 run session 继承和失败汇总。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242617450 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，打开 `http://127.0.0.1:8791/browser-automation/test-page?a9=batch-run`。
- 真机成功 run：`runId=a9-role-name-success`，一次请求执行 `type role=textbox name=Name value="A9 Run"`、`click role=button name=Apply greeting`、`waitFor role=status name=Hello A9 Run`；响应 `status=Succeeded`、`requestedCount=3`、`completedCount=3`、`stoppedOnFailure=false`。
- 真机状态证据：session 文本包含 `Hello A9 Run`；完整 session JSON 中 `a7-password-should-not-leak=False`，`a7-hidden-text-should-not-leak=False`。
- 真机失败停止：`runId=a9-stop-on-failure` 请求 2 步，第一步点击不存在的 `button name=Missing A9 Button`，响应 `status=Failed`、`completedCount=1`、`stoppedOnFailure=true`、`errorCode=target_not_found`，第二步未执行。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/run` 返回 HTTP 409，错误码 `mode_not_enabled`。

## 2026-07-05 A8 role/name 语义定位完成记录

- 代码：`BrowserAutomationTarget` 新增可选 `name` 字段；`BrowserAutomationAction.fromJson()` 和 LocalServer flat query fallback 均支持 `name` / `targetName`。
- Action 脚本：`kind=role` 时先派生 DOM role，再用 accessible name 匹配 `target.name`；未传 `name` 时保留旧 role 文本匹配兼容。
- 能力声明：`/browser-automation/capabilities` 的 targets 更新为 `css,text,role,role+name`。
- 安全修正：role/name 匹配和 label 摘要不读取 password input 的真实 value；保留 A7 的 password/hidden 探针做回归。
- 单测和构建：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242605958 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，打开 `http://127.0.0.1:8791/browser-automation/test-page?a8=role-name`。
- 真机 session：`9ef5ac005d794f0fa7afbe2eaeeb23ea`，snapshot 初始可见 `textbox:Name 输入名字`、`button:Apply greeting`、`status:automation result Waiting`。
- role/name 动作链：`type role=textbox name=Name value="A8 Role"`、`click role=button name=Apply greeting`、`waitFor role=status name=Hello A8 Role` 均返回 `Succeeded`。
- 动作覆盖：`find role=button name=Apply greeting`、`scroll role=region name=deep automation target`、`click role=button name=Mark deep target`、`waitFor role=status name=Deep target clicked` 均返回 `Succeeded`。
- 真机证据：最终 session 文本包含 `Hello A8 Role` 和 `Deep target clicked`，console 包含 `automation:greeting:A8 Role`，network 包含 `/status?automationNetwork=A8%20Role`。
- 泄露检查：完整 session JSON 中 `a7-password-should-not-leak=False`，`a7-hidden-text-should-not-leak=False`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/action` 返回 HTTP 409，错误码 `mode_not_enabled`。

## 2026-07-05 A7 可访问树/语义观察完成记录

- 代码：`BrowserAutomationSnapshot` 新增 `accessibility: List<BrowserAutomationAccessibilityNode>`；snapshot parser、session store、LocalServer public JSON 和运行报告均已接入。
- 语义来源：WebView snapshot JS 从 DOM 派生 role/name/state/rect，不启用 Android Accessibility Service，不申请系统级辅助权限。
- 节点字段：`role`、`name`、`tag`、`type`、`level`、`visible`、`enabled`、`checked`、`selected`、`expanded`、rect。
- 安全修正：真机验证时发现旧 `elements` 摘要会读取 password input 的 `value`，已同步修复 snapshot 脚本和 action 脚本，password input 不再进入元素 label/name。
- 测试页：加入 visible password input 和 hidden text，作为泄露回归探针。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过，覆盖 accessibility parser/store 和 action password label 防护。
- 构建：同一命令内 `:app:assembleDebug` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242601242 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，打开 `http://127.0.0.1:8791/browser-automation/test-page`。
- 真机语义证据：`/browser-automation/session` 的 `snapshot.accessibility` 返回 `heading:Kite Automation Test`、`textbox:Name 输入名字`、`textbox:Secret Password`、`button:Apply greeting`、`status:automation result Waiting`、`region:deep automation target...`、`button:Mark deep target`、`status:Deep waiting`。
- 真机安全证据：完整 session JSON 中 `a7-password-should-not-leak` 为 `False`，`a7-hidden-text-should-not-leak` 为 `False`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/action` 返回 `mode_not_enabled`。

## 2026-07-05 A6 网络证据与动作历史完成记录

- 代码：新增 `BrowserAutomationNetworkEntry`；`BrowserAutomationSessionStore` 增加 `recentResults(...)`、`saveNetworkEntry(...)`、`recentNetworkEntries(...)`。
- WebView：`KiteWebShell` 在 `shouldInterceptRequest` 记录资源请求，在 `onReceivedHttpError` 记录 HTTP 错误；记录只包含脱敏 URL、method、主框架标记、状态码、reason 和时间。
- LocalServer：`/browser-automation/session` 返回 `actions` 和 `network` 数组；新增 `GET /browser-automation/actions` 与 `GET /browser-automation/network`。
- 测试页：点击 Apply greeting 时触发 `fetch('/status?automationNetwork=...')`；点击 deep target 时触发 capabilities fetch，作为无副作用网络证据。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过，覆盖 action history 顺序和 network URL 脱敏。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242585490 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，打开 `http://127.0.0.1:8791/browser-automation/test-page`。
- 真机动作链：执行 `type #name = A6 Net`、`click #apply`、`waitFor text=Hello A6 Net`，三条 action 均成功。
- Action history 证据：`GET /browser-automation/actions?sessionId=ddd5f1613494429eb3322e4818f371e8&limit=10` 返回 `waitFor`、`click`、`type` 三条结果，按最新优先排序。
- Network 证据：`GET /browser-automation/network?sessionId=ddd5f1613494429eb3322e4818f371e8&limit=20` 返回主框架请求、`favicon.ico` 请求和 `favicon.ico` 404；点击后还能看到 `http://127.0.0.1:8791/status?automationNetwork=A6%20Net`。
- Session 证据：`GET /browser-automation/session?...` 同时返回最新 snapshot、latest result、`actions`、`console`、`network`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/action` 返回 `mode_not_enabled`。

## 2026-07-05 A5 扩展能力完成记录

- 代码：`BrowserAutomationActionType` 新增 `scroll`、`evaluate`、`screenshot`；`BrowserAutomationActionResult` 新增 `artifactPath`；`BrowserAutomationConsoleEntry` 进入同一份 `BrowserAutomationSessionStore`。
- Controller：成功的非截图动作会自动补采 snapshot，并把 `snapshotId` 写回 action result 和 session；`screenshot` 将 WebView 当前画面保存到应用私有目录；`evaluate` 只允许本地/可信 URL，否则返回 `untrusted_evaluate_blocked`。
- LocalServer：`/browser-automation/capabilities` 已公开新动作；`/browser-automation/session` 返回最新 snapshot、result 和 console；新增 `GET /browser-automation/console`。
- 测试页：`/browser-automation/test-page` 增加深层滚动区域、deep target 和 `console.log` 证据点。
- 单测：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" --console=plain --no-parallel` 通过。
- 构建：`.\gradlew.bat :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，大小 242572390 bytes。
- 真机：OnePlus 8T `3f8bbaad` 安装新 APK，临时切到 `automation_browser`，打开 `http://127.0.0.1:8791/browser-automation/test-page`。
- 真机动作链：`type #name = A5 Kite`、`click #apply`、`waitFor text=Hello A5 Kite`、`scroll css=#deep-target`、`click #deep-apply`、`evaluate`、`screenshot` 全部返回结构化结果。
- Snapshot 证据：`/browser-automation/session` 最新 snapshot 文本包含 `Hello A5 Kite` 和 `Deep target clicked`，说明 action 后 DOM 已刷新。
- Screenshot 证据：action result 返回 `artifactPath=/data/user/0/com.kite.app/files/browser-automation/screenshots/shot_5cafc12cfe9c4e6692e3d77ce2e9a1f8_1783222210409.png`；`run-as` 下 `ls -l` 看到该 PNG 文件，大小 14622 bytes。
- Console 证据：`/browser-automation/console` 返回 `automation:greeting:A5 Kite` 和 `automation:deep-clicked` 两条 LOG。
- 负向验证：`https://example.com/` 页面执行 `evaluate document.title` 返回 `Rejected`，错误码 `untrusted_evaluate_blocked`。
- 默认模式回归：验证结束后已恢复 `browser_runtime_mode=webview_system_auth`；默认模式下 `/browser-automation/action` 返回 `mode_not_enabled`。

## 2026-07-05 A3 第一刀完成记录

- 代码：新增 `com.kite.app.browser.automation`，包含 `BrowserAutomationSessionStore`、`BrowserAutomationController`、snapshot parser 和模型。
- 接入：`KiteWebShell.loadInWebView(..., automationEnabled)` 在第二模式下创建 session；`onPageFinished` 采集 DOM/文本/元素摘要；结果由 `MainActivity.handleBrowserAutomationEvent(...)` 写回 `CardRunStore`。
- 设备：OnePlus 8T `3f8bbaad`，未使用 MEIZU/X11 设备。
- 真机 smoke：临时切到 `automation_browser`，通过 `GET /open-web?url=http://127.0.0.1:8791/toolchain` 打开本地页面。
- 真机证据：`kite_browser_automation.xml` 出现 `status=Ready`、`title=KF Tool Environment`、`elementCount=27`、两个 button 元素摘要；`kite_card_run_store.xml` 出现 `status=Opened` 和 `自动浏览器已采集页面快照：KF Tool Environment`。
- 截图证据：`docs/browser-automation/evidence/automation-toolchain-webview.png`。
- 验证命令：`.\gradlew.bat :app:clean :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已复制到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`，原始产物为 `app\build\outputs\apk\debug\app-debug.apk`。
- 收尾：验证后已把 1+8T 的 `browser_runtime_mode` 恢复为 `webview_system_auth`。
- 剩余：第一刀记录时尚未实现 action endpoint；该项已在后续 A3 action 协议记录中完成。

## 2026-07-05 A3 action 协议完成记录

- 代码：新增 action/result/target 模型、`BrowserAutomationActionScript`、`BrowserAutomationControllerRegistry`。
- Endpoint：`GET /browser-automation/capabilities`、`GET /browser-automation/test-page`、`POST /browser-automation/action`。
- 支持动作：`find`、`click`、`type`、`waitFor`、`snapshot`；第一版 target 支持 `css`、`text`、`role`。
- 关键修正：LocalServer 可能在主 Activity，而 WebView 页面在 CardRunActivity；因此用 controller registry 按 session 找真实 WebView controller，不再只依赖本地 `activeSessionId`。
- 真机动作链：OnePlus 8T `3f8bbaad` 打开 `http://127.0.0.1:8791/browser-automation/test-page` 后，通过 `/browser-automation/action` 执行：
  - `type css=#name value="Kite Action"` -> `Succeeded`，message=`typed 11 chars`
  - `click css=#apply` -> `Succeeded`，message=`clicked css`
  - `waitFor text="Hello Kite Action"` -> `Succeeded`，message=`found text`
- 证据：`kite_browser_automation.xml` 写入 `results_v1` 三条 action result；截图 `docs/browser-automation/evidence/automation-action-test-page.png` 显示输入框为 `Kite Action`、结果为 `Hello Kite Action`。
- 默认模式回归：恢复 `browser_runtime_mode=webview_system_auth` 后，本地服务 `/status` 正常，`/browser-automation/action` 返回 `mode_not_enabled`，不驱动默认浏览器模式。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.automation.*" :app:assembleDebug --console=plain --no-parallel` 通过。
- APK：已覆盖到 `C:\Users\19437\Desktop\kite-browser-automation-debug.apk`。

## 2026-07-05 A4 session 查询完成记录

- Endpoint：`GET /browser-automation/session` 支持按 `sessionId`、`instanceId` 查询，未传参数时返回最新未关闭 session。
- 响应内容：`session`、最近 `snapshot`、最近 `result`；错误时返回 `session_not_found`。
- 真机验证：安装新 APK 后，`GET /browser-automation/capabilities` 返回 action 列表和 endpoint 列表；`GET /browser-automation/session` 返回 session `f31a4c15...`，`lastActionId=act_6f51...`，latest result 为 `waitFor/Succeeded/found text`。
- 收尾：验证后 1+8T 仍为 `browser_runtime_mode=webview_system_auth`。

## 本轮资料来源初稿

- Chrome DevTools：Remote debugging WebViews
- Android Developers：Debug web apps / WebView
- Chrome DevTools Protocol
- Playwright Android experimental automation
- W3C WebDriver / WebDriver BiDi
- Selenium WebDriver
- Appium Hybrid Apps
- Android Accessibility Service
- OWASP MAS：WebView debugging production security

## A0/A1 完成证据

- 三件套：`docs/browser-automation/PLAYBOOK.md`、`PROGRESS.md`、`DECISIONS.md` 已建立。
- 调研文档：`docs/browser-automation/RESEARCH.md` 已写入。
- 推荐路线：第一阶段做 App 内受控 WebView 自动化；第二阶段研究 CDP/DevTools；第三阶段外部兼容 Playwright/Appium/WebDriver/BiDi。
- 反路线：不把自动浏览器当作网页登录策略绕过工具；不默认生产开启 WebView debugging；不默认用 Accessibility Service 控制系统浏览器。
