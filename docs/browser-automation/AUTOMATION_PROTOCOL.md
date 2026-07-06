# Kite 自动浏览器协议设计

最后更新：2026-07-05

## 1. 目标

自动浏览器协议用于把 Kite 第二浏览器模式从“一个设置项”变成可被 AI / 脚本稳定驱动的浏览器控制面。

它必须同时满足四件事：

1. 保留现有第一模式的登录边界：OAuth/SSO 继续走 `BrowserHandoffPolicy` 和系统浏览器。
2. 绑定现有运行实例：自动化状态写回 `CardRunStore`，不新建平行运行事实。
3. 支持可验证动作：每个动作都有输入、结果、耗时、页面状态和错误。
4. 支持长期扩展：第一阶段用 App 内 WebView，后续可兼容 CDP / Playwright / Appium / WebDriver/BiDi 思路。

## 2. 当前真实入口

代码入口来自当前工作区真实文件：

- `KiteWebShell`
  - 当前负责 WebView 设置、页面加载、导航拦截、console/error 诊断。
  - 当前没有 DOM snapshot、selector、click、type、wait action 接口。
- `MainActivity.showCardRunWebView(...)`
  - 当前负责把 CardRun Web surface 绑定到共享 `webView`。
  - 当前已经在加载前调用 `BrowserHandoffPolicy.classify(...)`，OAuth/SSO 会外部打开或进入 handoff 等待页。
- `KiteLocalServer`
  - 当前已有 `/open-web`，只负责打开 URL。
  - 当前没有自动化动作提交或 session 查询 endpoint。
- `CardRunStore`
  - 当前是运行实例状态拥有者。
  - 可写 `surface`、`lastMeaningfulOutput`、`lastError`、`shellReportText`、`nextActionUrl` 等运行状态。
- `BrowserRuntimeMode`
  - 当前设置持久化 `webview_system_auth` / `automation_browser`。
  - 当前只作为设置状态，尚未影响打开网页行为。

## 3. 核心模型

### 3.1 AutomationSession

自动浏览器每个受控页面对应一个 session。

```json
{
  "sessionId": "auto-run_temp_xxx-1783219000000",
  "instanceId": "run_temp_xxx",
  "recipeId": "temp_browser_xxx",
  "url": "http://127.0.0.1:8648",
  "mode": "webview",
  "status": "Opening|Ready|RunningAction|Waiting|Failed|Closed",
  "createdAt": 1783219000000,
  "updatedAt": 1783219001000,
  "lastActionId": "act_001",
  "lastError": "",
  "lastSnapshotId": "snap_001"
}
```

状态语义：

- `Opening`：WebView 正在加载目标页面。
- `Ready`：主框架已加载，可接收动作。
- `RunningAction`：正在执行一个动作。
- `Waiting`：正在等待 selector、text、url 或 idle 条件。
- `Failed`：最近动作或页面进入不可继续错误。
- `Closed`：运行实例关闭或 WebView 被释放。

### 3.2 AutomationAction

动作是自动浏览器的最小执行单位。

```json
{
  "actionId": "act_001",
  "sessionId": "auto-run_temp_xxx-1783219000000",
  "type": "open|snapshot|find|click|doubleClick|hover|navigate|type|clear|press|select|check|scroll|waitFor|evaluate|screenshot",
  "target": {
    "kind": "css|text|xpath|role|point|url|state|none",
    "value": "#submit"
  },
  "value": "hello",
  "timeoutMs": 8000,
  "trusted": false
}
```

第一阶段支持动作：

- `open(url)`：打开 URL；会先经过 `BrowserHandoffPolicy`。
- `snapshot()`：采集当前 URL、标题、可见文本摘要、元素摘要、截图 hash/path。
- `find(target)`：查询元素存在性和可见性。
- `click(target)`：点击元素；先对目标中心派发受控 `pointerdown` / `mousedown` / `pointerup` / `mouseup`，再调用 `element.click()` 保留浏览器默认 activation。第一阶段不做 Android 全局触摸注入。
- `doubleClick(target)`：双击元素；执行两轮受控 pointer/mouse/click activation，并派发 `dblclick`。第一阶段不做 Android 全局触摸注入。
- `hover(target)`：悬停元素；对目标中心派发受控 `pointerover` / `pointerenter` / `mouseover` / `mouseenter` / `pointermove` / `mousemove`。第一阶段不做 Android 全局鼠标、触摸或 Accessibility 注入。
- `navigate(back|forward|reload)`：控制当前 WebView session 的历史和刷新；第一阶段只支持 `back`、`forward`、`reload`，不接受任意 URL。
- `type(target, value)`：聚焦元素并输入文本。
- `clear(target)`：清空 HTML input、textarea 或 contenteditable，并派发 `input` / `change` 事件。
- `press(target, key)`：在受控 WebView 内对目标元素或当前 activeElement 派发键盘事件；第一阶段支持 Enter、Escape、Tab、Space、Backspace、Delete、方向键和单字符。
- `select(target, value)`：对 HTML `<select>` 选择 option；第一阶段支持 option value、可见文本和 `index:<n>`。
- `check(target, value)`：把 checkbox、radio 或 switch-like 控件设置为明确状态；第一阶段支持 `true`、`false` 和 `toggle`。
- `scroll(direction|target)`：页面滚动或滚到元素。
- `waitFor(target|url|idle)`：等待元素、文本、URL 变化或页面 idle。
- `evaluate(script)`：执行 JS；第一阶段默认只允许本地/可信页面。
- `screenshot()`：采集当前 WebView 画面并返回受控 `artifactPath` / `artifactUrl`。

### 3.3 AutomationResult

每个动作必须有结果，不能只有后台副作用。

```json
{
  "actionId": "act_001",
  "sessionId": "auto-run_temp_xxx-1783219000000",
  "status": "Succeeded|Failed|TimedOut|Rejected",
  "durationMs": 124,
  "url": "http://127.0.0.1:8648/home",
  "title": "Hermes",
  "message": "clicked css=#submit",
  "snapshotId": "snap_002",
  "artifactPath": "",
  "artifactUrl": "",
  "errorCode": "",
  "errorDetail": ""
}
```

错误码第一批：

- `not_ready`：页面未 ready。
- `target_not_found`：selector/text 没找到。
- `target_not_visible`：元素存在但不可见或不可点。
- `target_disabled`：目标元素处于 HTML disabled、`:disabled` 或 `aria-disabled=true` 状态。
- `target_readonly`：编辑动作遇到 HTML readonly 或 `aria-readonly=true` 目标。
- `target_not_editable`：目标不是 input、textarea 或 contenteditable。
- `target_not_checkable`：目标不是 checkbox、radio 或 switch-like 状态控件，或请求了不支持的 radio 取消操作。
- `target_not_actionable`：当前 action 不接受该 target 类型，例如 `navigate` 携带 DOM/URL/state target。
- `unsupported_navigation_value`：`navigate` 的 value 不是 `back`、`forward` 或 `reload`。
- `action_timeout`：等待或动作超时。
- `navigation_blocked_for_auth`：目标 URL 是 OAuth/SSO，已转交第一模式。
- `untrusted_evaluate_blocked`：非可信页面拒绝执行任意 JS。
- `webview_unavailable`：运行实例没有可用 WebView。
- `session_not_found`：session 不存在或已关闭。

## 4. Selector 策略

第一阶段 selector 必须够通用，但不追求完整 Playwright 语法。

优先级：

1. `css`：标准 CSS selector。
2. `text`：可见文本包含或精确匹配。
3. `role`：从 DOM attribute 近似读取 `role`、`aria-label`、`placeholder`、按钮文本。
4. `xpath`：后置支持，不作为第一阶段默认。
5. `point`：坐标点击只用于测试或视觉兜底。

Iframe 边界：

- `css` / `text` / `role` / `role+name` target 会递归进入同源 `iframe/frame`。
- 同源 frame 内的元素和可访问树节点会带 `framePath` / `frameUrl` / `frameName`。
- 跨源、sandbox 或不可访问 iframe 只作为 `role=iframe` 摘要暴露，`frameAccessible=false`，不会读取内部 DOM 或文本。
- 不把跨源 iframe 作为网页登录、验证码或第三方组件的自动化绕过路径。

Shadow DOM 边界：

- `css` / `text` / `role` / `role+name` target 会递归进入 open `shadowRoot`。
- open shadow 内的元素和可访问树节点会带 `shadowPath` / `shadowHost`。
- open shadow 内的 label / `aria-labelledby` 语义定位按元素所属 root 查询，不依赖顶层 document。
- closed shadow root 只暴露 host 元素本身，不读取内部 DOM、文本、密码、cookie 或 token。
- 不把 closed shadow root 作为网页登录、验证码或第三方组件的自动化绕过路径。

Actionability 边界：

- `click` / `doubleClick` / `type` / `clear` / `select` / `check` 遇到 disabled 目标返回 `target_disabled`，不派发 DOM 事件、不改值、不报告成功。
- 目标化 `press` 遇到 disabled 目标返回 `target_disabled`；`target.kind=none` 时仍只作用于当前页面 activeElement 或 body。
- `type` / `clear` 遇到 readonly 目标返回 `target_readonly`，不改值。
- `hover` / `find` / `waitFor` / `observe` 是观察或悬停语义，不因为 disabled/readonly 本身失败；但 observe 对 `enabled=false` 的节点只建议 `find`。
- disabled/readonly 判断使用通用 DOM 状态：HTML 属性/属性反射、`:disabled`、`aria-disabled=true`、`aria-readonly=true`，不是针对某个测试页或按钮名特判。

URL target 是等待条件，不是 DOM selector：

- `kind=url` 只读取当前 `location.href`。
- `find` / `waitFor` 支持 `contains` 和 `exact` 匹配。
- `click` / `doubleClick` / `hover` / `type` / `clear` / `press` / `check` / `scroll` 等需要 DOM 元素或 activeElement 的动作会返回 `target_not_actionable`。

State target 是页面状态等待条件，也不是 DOM selector：

- `kind=state,value=domReady`：`document.readyState` 为 `interactive` 或 `complete`。
- `kind=state,value=complete`：`document.readyState` 为 `complete`。
- `kind=state,value=idle`：页面 ready 后，DOM mutation 停止一小段时间；默认 500ms，可用 action `value` 指定毫秒数，也可写 `idle:<ms>`。
- `find` / `waitFor` 支持 state target；`click` / `doubleClick` / `hover` / `type` / `clear` / `press` / `check` / `scroll` 返回 `target_not_actionable`。

Navigate 是受控 WebView 历史动作，不是 URL 打开入口：

- `navigate` 只接受 `target.kind=none`。
- `value=back|forward|reload` 分别调用当前 session 的 `history.back()`、`history.forward()`、`location.reload()`。
- `value` 为 URL 或其它字符串时返回 `unsupported_navigation_value`，不设置 `location.href`。
- 打开 URL 继续使用 `open-run` / `open-web`，并继续经过 `BrowserHandoffPolicy` 做 OAuth/SSO 外部分流。

Press target 是 WebView 内部键盘动作，不是 Android 全局按键注入：

- `target.kind=css|text|role` 时先定位、滚动并聚焦目标元素，再派发 `keydown` / `keypress` / `keyup`。
- `target.kind=none` 时使用当前 `document.activeElement`，没有 activeElement 时退到 `body`。
- `target.kind=url|state` 返回 `target_not_actionable`。
- 第一阶段不模拟系统输入法、硬件键盘或系统浏览器里的按键；如需更接近真实浏览器输入，后续单独评估 CDP、WebDriver 或系统级输入授权。

Clear target 是 WebView 内部确定性清空动作：

- 支持 HTML `<input>`、`<textarea>` 和 `contenteditable`。
- 推荐用 `kind=role,value=textbox,name=<label>` 定位。
- 清空后总是派发 `input` 和 `change` 事件，便于前端状态同步。
- disabled 目标返回 `target_disabled`；readonly 目标返回 `target_readonly`。
- 非可编辑目标返回 `target_not_editable`；`target.kind=url|state` 返回 `target_not_actionable`。
- `type(target, value)` 仍保持覆盖填入语义，`clear` 只是让“先清空”成为可审计动作。

Select target 是 WebView 内部 HTML 表单动作：

- 只支持 HTML `<select>` 元素，推荐用 `kind=role,value=combobox,name=<label>` 定位。
- `value` 可写 option 的 `value`、用户可见文本，或 `index:<n>`。
- 选中后派发 `input` 和 `change` 事件。
- 非 `<select>` 目标返回 `target_not_selectable`；`target.kind=url|state` 返回 `target_not_actionable`。

Check target 是 WebView 内部状态控件动作：

- 支持 HTML `<input type=checkbox>`、`<input type=radio>`，以及 `role=checkbox|radio|switch` 的 ARIA 状态控件。
- 推荐用 `kind=role,value=checkbox,name=<label>` 或 `kind=role,value=radio,name=<label>` 定位。
- `value` 为空时默认为 `true`；也可写 `true` / `false` / `toggle`，并兼容 `checked`、`unchecked`、`on`、`off`、`1`、`0`。
- checkbox 支持 true/false/toggle；radio 支持 true，false 或把已选中 radio toggle 成 false 会返回 `target_not_checkable`。
- 状态变化后派发 `input` 和 `change` 事件；状态已满足时返回成功但不重复派发事件。
- 非状态控件返回 `target_not_checkable`；`target.kind=url|state` 返回 `target_not_actionable`。

推荐 target 结构：

```json
{
  "kind": "text",
  "value": "登录",
  "match": "contains|exact",
  "index": 0
}
```

- `index` 是同一 selector 结果中的序号，首个为 0；不传时兼容旧行为，默认选择第一个匹配元素。
- observe 会在 `suggestedTarget.index` 中写入同 role/name 元素的去歧义序号，智能体可以直接把该 target 传给 action/run。

元素摘要不保存输入框真实敏感值，只保存：

- tag
- type
- clear
- visible text 截断
- placeholder 截断
- aria-label 截断
- bounding rect
- enabled/visible/focused

## 5. 等待条件

自动化不能靠固定 sleep 作为主机制。

第一阶段 wait 条件：

- `domReady`：`document.readyState` 至少为 `interactive` 或 `complete`。
- `networkQuiet`：先作为后置研究；WebView 内部无法低成本可靠统计全网络。
- `selectorVisible`：selector 匹配且可见。
- `textVisible`：文本出现。
- `urlContains` / `urlEquals`：用 `target.kind=url` 和 `match=contains|exact` 表达 URL 条件。
- `domReady` / `complete` / `idleMs`：用 `target.kind=state` 表达页面状态等待；`idleMs` 只作短暂稳定兜底。

默认超时：

- 单动作：`8000ms`
- 页面打开：`15000ms`
- snapshot：`3000ms`

## 6. 与 CardRunStore 的绑定

自动化不能新建平行运行事实。

写入规则：

- session 创建：保持或切到 `CardRunSurface.Web`。
- action 开始：`lastMeaningfulOutput = "自动浏览器正在执行：<type> <target摘要>"`。
- action 成功：`lastMeaningfulOutput = "自动浏览器完成：<type>，<message>"`。
- action 失败：`lastError = "自动浏览器失败：<errorCode> <errorDetail摘要>"`。
- snapshot 证据：可写入 `shellReportText` 或后续专用 automation report 字段；第一阶段先用报告文本承载。

禁止：

- 不把 session 状态长期复制到 Activity 本地变量作为唯一事实。
- 不在页面绘制时扫描 DOM 或跑重型自动化。
- 不因为某个 action 完成重建整个页面。

## 7. URL 分流边界

所有 `open(url)` 和页面内导航必须继续调用 `BrowserHandoffPolicy.classify(...)`。

分类处理：

- `StayInWebView`：可进入自动浏览器。
- `StartAuthHandoff`：拒绝自动化承载，转交第一模式，结果为 `navigation_blocked_for_auth` 或 `handoff_started`。
- `StartCliCallbackHandoff`：保留 CLI/终端回跳边界，转交第一模式。
- `OpenExternalBrowser`：外部浏览器打开，不创建 automation session。
- `ShowUnsupportedFallback`：返回失败或外部打开。

这条规则不因 `automation_browser` 模式改变。

## 8. LocalServer / Bridge 草案

当前外部入口由 `KiteLocalServer` 承载，所有自动化接口都只在 `browser_runtime_mode=automation_browser` 时执行动作；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。

当前 endpoints：

- `POST /browser-automation/action`
  - 提交单个动作，返回 action result。
- `POST /browser-automation/run`
  - 提交动作序列，顺序复用单步 action handler，返回 run 汇总和每步 result。
- `POST /browser-automation/open-run`
  - 打开 URL、等待 automation session ready、执行动作序列，返回 open 摘要和 run 汇总。
- `GET /browser-automation/observe?sessionId=...`
  - 查询面向智能体决策的紧凑页面观察结果。
- `GET /browser-automation/artifact?path=...`
  - 读取受控 screenshot artifact，当前只允许应用私有截图目录下的 PNG。
- `GET /browser-automation/runs?runId=...`
  - 按 runId 恢复查询单个 run 汇总和每步 result。
- `GET /browser-automation/runs?sessionId=...`
  - 按 sessionId 查询最近 run 列表，默认最新优先。
- `GET /browser-automation/sessions?limit=...`
  - 查询自动浏览器 session 摘要列表，默认排除 Closed，按最新优先排序；支持 `includeClosed=true` 和 `instanceId` 过滤。
- `GET /browser-automation/session?sessionId=...`
  - 查询 session、最近 snapshot、最近 result、action history、run history、console 和 network 摘要。
- `GET /browser-automation/actions?sessionId=...`
  - 查询 action history。
- `GET /browser-automation/console?sessionId=...`
  - 查询 console 摘要。
- `GET /browser-automation/network?sessionId=...`
  - 查询脱敏网络证据。
- `GET /browser-automation/capabilities`
  - 查询可用动作和安全策略。
- `GET /browser-automation/test-page`
  - 本地无副作用验证页。

所有响应都必须 JSON 化，且不输出敏感字段原文。

### 8.1 批量 run 请求

```json
{
  "runId": "optional-client-run-id",
  "sessionId": "session-id",
  "stopOnFailure": true,
  "actions": [
    {
      "type": "type",
      "target": { "kind": "role", "value": "textbox", "name": "Name" },
      "value": "Kite"
    },
    {
      "type": "click",
      "target": { "kind": "role", "value": "button", "name": "Apply greeting" }
    },
    {
      "type": "waitFor",
      "target": { "kind": "role", "value": "status", "name": "Hello Kite" }
    }
  ]
}
```

响应：

```json
{
  "ok": true,
  "runId": "optional-client-run-id",
  "sessionId": "session-id",
  "status": "Succeeded",
  "durationMs": 1386,
  "requestedCount": 3,
  "completedCount": 3,
  "stoppedOnFailure": false,
  "errorCode": "",
  "errorDetail": "",
  "completedAt": 1783225382605,
  "results": []
}
```

约束：

- run 最多包含 20 个 actions。
- `stopOnFailure` 默认 `true`。
- 公共 `sessionId` / `instanceId` 会继承到未显式设置的 action。
- run 不绕过 OAuth/SSO 外部分流，不绕过默认模式门禁，不保存敏感字段原文。
- run 汇总会保存到 `BrowserAutomationSessionStore`，供 `/browser-automation/runs` 和 `/browser-automation/session` 恢复查询。

### 8.2 open-run 请求

```json
{
  "runId": "optional-client-run-id",
  "url": "http://127.0.0.1:8791/browser-automation/test-page",
  "source": "browser_automation_open_run",
  "openTimeoutMs": 15000,
  "stopOnFailure": true,
  "actions": [
    {
      "type": "type",
      "target": { "kind": "role", "value": "textbox", "name": "Name" },
      "value": "Kite"
    },
    {
      "type": "click",
      "target": { "kind": "role", "value": "button", "name": "Apply greeting" }
    },
    {
      "type": "waitFor",
      "target": { "kind": "role", "value": "status", "name": "Hello Kite" }
    }
  ]
}
```

响应：

```json
{
  "ok": true,
  "runId": "optional-client-run-id",
  "sessionId": "session-id",
  "status": "Succeeded",
  "requestedCount": 3,
  "completedCount": 3,
  "open": {
    "requested": true,
    "url": "http://127.0.0.1:8791/browser-automation/test-page",
    "source": "browser_automation_open_run",
    "sessionId": "session-id",
    "instanceId": "run_temp_web_xxx",
    "status": "Ready"
  },
  "results": []
}
```

约束：

- 只在 `automation_browser` 模式下打开页面；默认模式返回 `mode_not_enabled` 且 `open.requested=false`。
- `openTimeoutMs` 默认 15000，范围 1000 到 30000。
- open-run 只负责组合已有 `openWeb` 与 run 链路，不新建第二套 WebView 执行器。

### 8.3 run 恢复查询

按 runId 查询：

```text
GET /browser-automation/runs?runId=a11-open-run-success
```

响应直接返回 run 汇总：

```json
{
  "ok": true,
  "runId": "a11-open-run-success",
  "sessionId": "session-id",
  "status": "Succeeded",
  "requestedCount": 3,
  "completedCount": 3,
  "completedAt": 1783225382605,
  "results": []
}
```

按 sessionId 查询：

```text
GET /browser-automation/runs?sessionId=session-id&limit=5
```

响应返回最新优先的 `runs` 数组。`GET /browser-automation/session?sessionId=...&runLimit=5` 也会带同一份最近 run 摘要。

约束：

- run history 与 session、snapshot、action、console、network 共用 `BrowserAutomationSessionStore`。
- 不保存 cookie、token、password、authorization code 或 Authorization/Bearer 原文。
- 默认模式门禁、open timeout、普通 action 失败都会保存为可查询的失败 run，只要请求里有合法 run/actions。
- 如果 run 汇总中的某个 action 是 `TimedOut/request_timeout`，但同一 `actionId` / `sessionId`
  稍后在 action history 中落库为非 `request_timeout` 的最终结果，`/browser-automation/runs`、
  `/browser-automation/session` 和 `/browser-automation/observe.recentRun` 必须返回校准后的 run。
- run/open-run 执行链遇到 `request_timeout` action 时会最多等待 4 秒同 actionId 的最终落库结果，避免刚返回就制造 run history 与 action history 的矛盾。
- 没有同 actionId 迟到结果的真实 `request_timeout` 必须保持 `TimedOut/request_timeout`，不能被猜测成成功。

### 8.4 observe 观察请求

observe 是只读入口，用于智能体的“观察-决策-动作”循环。它不执行动作、不打开页面、不刷新 WebView，只从 `BrowserAutomationSessionStore` 读取最近 session、snapshot、action 和 run。

```text
GET /browser-automation/observe?sessionId=session-id&interactiveLimit=30&textLimit=1200
```

响应：

```json
{
  "ok": true,
  "session": {
    "sessionId": "session-id",
    "instanceId": "run_temp_web_xxx",
    "url": "http://127.0.0.1:8791/browser-automation/test-page",
    "mode": "webview",
    "status": "Ready",
    "lastActionId": "act_xxx",
    "lastSnapshotId": "snap_xxx"
  },
  "page": {
    "snapshotReady": true,
    "snapshotId": "snap_xxx",
    "url": "http://127.0.0.1:8791/browser-automation/test-page",
    "scope": "local",
    "trustedForEvaluate": true,
    "title": "Kite Automation Test",
    "readyState": "complete",
    "text": "Kite Automation Test Name Apply greeting...",
    "elementCount": 8,
    "accessibilityCount": 7,
    "capturedAt": 1783226000000
  },
  "interactive": [
    {
      "role": "textbox",
      "name": "Name",
      "tag": "input",
      "type": "text",
      "enabled": true,
      "suggestedTarget": {
        "kind": "role",
        "value": "textbox",
        "name": "Name",
        "index": 0
      },
      "suggestedActions": ["type", "clear", "press", "find", "waitFor"]
    },
    {
      "role": "button",
      "name": "Apply greeting",
      "suggestedTarget": {
        "kind": "role",
        "value": "button",
        "name": "Apply greeting",
        "index": 0
      },
      "suggestedActions": ["click", "hover", "doubleClick", "find", "waitFor"]
    },
    {
      "role": "checkbox",
      "name": "Subscribe updates",
      "checked": "false",
      "suggestedTarget": {
        "kind": "role",
        "value": "checkbox",
        "name": "Subscribe updates",
        "index": 0
      },
      "suggestedActions": ["check", "click", "find", "waitFor"]
    }
  ],
  "recentAction": {},
  "recentRun": {},
  "capabilities": {
    "actions": ["snapshot", "find", "click", "doubleClick", "hover", "navigate", "type", "clear", "press", "select", "check", "waitFor", "scroll", "evaluate", "screenshot"],
    "targets": ["css", "text", "role", "role+name", "url", "state"],
    "runs": ["sequential", "stopOnFailure"],
    "endpoints": ["/browser-automation/action", "/browser-automation/run", "/browser-automation/open-run", "/browser-automation/sessions", "/browser-automation/artifact"],
    "authBoundary": "oauth_and_sso_stay_external",
    "evaluate": "local_trusted_only",
    "source": "BrowserAutomationCapabilities"
  },
  "limits": {
    "textLimit": 1200,
    "interactiveLimit": 30,
    "source": "BrowserAutomationSessionStore"
  },
  "authBoundary": "oauth_and_sso_stay_external"
}
```

`capabilities` 与 `GET /browser-automation/capabilities` 共用同一份来源。observe 中的 `capabilities.actions` / `targets` / `endpoints` 是数组，便于智能体直接决策；capabilities endpoint 为兼容早期脚本继续保留逗号分隔字符串字段，并额外提供 `actionList` / `targetList` / `endpointList`。

约束：

- 未传 `sessionId` 时沿用最新未关闭 session；多页面任务应显式传 `sessionId`。
- `interactive` 来自现有 Web DOM 派生 accessibility 节点；不会启用 Android Accessibility Service。
- `suggestedTarget` 可直接作为 action/run/open-run 的 target；同 role/name 有多个元素时，`suggestedTarget.index` 用于选择第 N 个匹配项。
- `scope=local` 且 `trustedForEvaluate=true` 时才建议提交 `evaluate`；普通远程 HTTPS 页面为 `scope=remote`、`trustedForEvaluate=false`。
- 输出会脱敏 URL 和文本里的 token/password/authorization/Bearer 片段。
- observe 不改变 `browser_runtime_mode`，也不把 OAuth/SSO 页面拉回 WebView。

### 8.5 sessions 会话发现

sessions 是只读恢复入口，用于多页面、断线恢复和跨回合继续。它不执行动作、不打开页面、不刷新 snapshot。

```text
GET /browser-automation/sessions?limit=20
GET /browser-automation/sessions?includeClosed=true
GET /browser-automation/sessions?instanceId=run_temp_web_xxx
```

响应：

```json
{
  "ok": true,
  "count": 2,
  "latestSessionId": "session-newest",
  "source": "BrowserAutomationSessionStore",
  "includeClosed": false,
  "sessions": [
    {
      "sessionId": "session-newest",
      "instanceId": "run_temp_web_xxx",
      "source": "card_run_surface",
      "url": "http://127.0.0.1:8791/browser-automation/test-page?token=present",
      "mode": "webview",
      "status": "Ready",
      "updatedAt": 1783234257761,
      "lastActionId": "act_xxx",
      "lastSnapshotId": "snap_xxx",
      "lastError": ""
    }
  ]
}
```

约束：

- 默认排除 `Closed` session；`includeClosed=true` 才返回已关闭 session。
- `limit` 会被限制在 store 的最大 session 数内。
- `instanceId` 过滤只返回该运行实例的 session。
- URL、错误和其它摘要字段必须脱敏，不返回 cookie、token、password、authorization、snapshot 文本、action history 或 run history。
- 默认 `webview_system_auth` 模式下该入口仍可只读查询；动作型 endpoint 继续被模式门禁拦截。

### 8.6 screenshot artifact 下载

`screenshot` action 会把 WebView 当前画面保存到应用私有目录，并在 action result 的 `artifactPath` 返回路径。result 同时返回相对 `artifactUrl`，外部智能体可以把它拼到当前本地服务 origin 后直接下载，也可以继续用 artifact endpoint 读取该 PNG：

```text
GET /browser-automation/artifact?path=/data/user/0/com.kite.app/files/browser-automation/screenshots/shot_xxx.png
/browser-automation/artifact?path=%2Fdata%2Fuser%2F0%2Fcom.kite.app%2Ffiles%2Fbrowser-automation%2Fscreenshots%2Fshot_xxx.png
```

成功响应：

```text
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: ...
```

约束：

- 只允许读取 `files/browser-automation/screenshots` 目录下的 `.png` 文件。
- 支持 action result 返回的绝对 `artifactPath`，也支持相对截图文件名。
- `artifactUrl` 是相对 URL，不固定 host；`/browser-automation/observe` 的 `recentAction` 会暴露 `artifactPath` / `artifactUrl`，`recentRun` 会暴露 `artifactCount` 和最近 artifact URL。
- 路径穿越、非截图目录、非 PNG 后缀返回 JSON 错误。
- 缺失文件返回 `artifact_not_found`。
- 不提供任意文件下载，不暴露 cookie/header/body/token。

## 9. 第一阶段实现切片

A3 不一次性实现完整浏览器。

第一刀建议：

1. 新增 `BrowserAutomationSessionStore`，只保存 session/action/result 的非敏感摘要。
2. 新增 `BrowserAutomationController`，挂在 `KiteWebShell` 旁边，持有 WebView 弱边界和 action 执行队列。
3. `automation_browser` 模式下，`showCardRunWebView(...)` 创建/绑定 automation session。
4. 支持 `snapshot()` 和 `find(text/css)`，先不做点击输入。
5. 单测覆盖：
   - OAuth URL 不进入自动 session。
   - 本地 URL 能创建 session。
   - unknown session/action 返回明确错误。
6. 真机验证：
   - 切到自动浏览器。
   - 打开本地测试页。
   - 采集 snapshot 并写入运行报告或诊断文件。

## 10. 完成判定

A2 完成只表示协议设计完成，不表示自动浏览器已可用。

A3 完成才允许声明“第二模式有最小可用自动化内核”。
