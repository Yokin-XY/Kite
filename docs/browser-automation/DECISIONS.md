# Kite 浏览器自动化决策记录

## ADR-A001 浏览器自动化作为第二模式独立目标

- 日期：2026-07-05
- 决策：把“浏览器自动化”从“浏览器登录回跳”中拆出，建立 `docs/browser-automation/` 三件套。登录回跳第一模式继续由 `docs/browser-login/` 维护，自动浏览器第二模式由本目录维护。
- 理由：第一模式已经在机制层成功，剩余是真实账号人工证据；第二模式的核心是 AI/脚本驱动浏览器，不应把两类验收混在一起。
- 影响：后续自动浏览器实现读取 `browser_runtime_mode=automation_browser`，但不改变第一模式的 OAuth/SSO 外部浏览器边界。

## ADR-A002 默认提交策略为分支 checkpoint，不直接推主线

- 日期：2026-07-05
- 决策：浏览器线默认先在 `D:\xm\Kite-browser-login` / `codex/browser-login-return` 做本地 checkpoint commit。未经用户明确要求，不直接提交主线、不 push、不开 PR。
- 理由：浏览器线和 X11 线是物理隔离、并行开发。直接改主线会让另一条线难判断基线；本地分支 checkpoint 能先封口，再用 Git 合并工具处理交集。
- 影响：如果浏览器线提交一个功能、X11 线提交另一个功能，Git 不会自动删除彼此代码。只有两边改了同一文件同一附近内容时会产生冲突，需要人工选择或整合；两边不同文件或同文件不同区域通常能自动合并。

## ADR-A003 自动浏览器优先做受控 WebView 内核，不优先做系统浏览器自动化

- 日期：2026-07-05
- 决策：Kite 自动浏览器第一阶段优先基于 App 内受控 WebView 建立 action/selector/session 协议；系统浏览器、Custom Tabs 和 OAuth/SSO 登录继续保持第一模式。Playwright/Appium/CDP 作为兼容和调试参考，不作为第一刀直接嵌入重依赖内核。
- 理由：Kite 主要页面是本地端口 Web UI 和受控运行实例，App 内 WebView 能最低成本拿到 DOM、JS、截图和运行状态绑定。系统浏览器不属于 Kite 进程，自动化需要 ADB/CDP/Accessibility 等外部授权，边界更重，也不适合承载 OAuth 策略绕过。
- 影响：A2 设计先覆盖本地 Web UI 和普通页面的自动化；跨 App/系统浏览器自动化只作为后续研究项。

## ADR-A004 A3 第一刀先做页面快照，不先做点击输入

- 日期：2026-07-05
- 决策：`automation_browser` 第一刀只在受控 WebView 页面加载完成后建立 automation session，采集 URL、标题、DOM readyState、可见文本和可交互元素摘要，并写回 `CardRunStore`。click/type/wait 和外部 HTTP action endpoint 放到下一刀。
- 理由：自动浏览器后续能力都依赖“能稳定观察页面”。先把 session、脱敏、状态写回和真机证据跑通，可以避免一上来把 action 队列、selector、LocalServer endpoint 和 UI 状态混成一个大改动。
- 影响：第二模式已经有最小可观测内核，但还不能称为完整自动浏览器。A3 继续推进时应沿用本次模型和 store，不另建平行状态；默认第一模式仍保持 `webview_system_auth`。

## ADR-A005 自动化成功快照把运行状态校准为 Opened

- 日期：2026-07-05
- 决策：当 automation snapshot 成功后，如果当前 run 不是 `Running`、`WaitingTerminal` 或 `AlreadyRunning`，就把 `CardRunStore` 状态校准为 `Opened`，而不是继续保留临时网页创建时的 `Starting`。
- 理由：真机 smoke 发现临时网页已经加载并写入快照，但运行状态仍是 `Starting`，会让前台可能显示成一直启动中。snapshot 成功代表 Web 面已经可观察，`Opened` 更符合现有运行语义。
- 影响：临时网页和普通 Web surface 成功加载后状态更准确；失败仍写 `Failed` 和报告；终端/运行中场景会保留原运行状态，不抢占其它 runtime 生命周期。

## ADR-A006 Action endpoint 必须受模式门禁并按 session 找真实 WebView

- 日期：2026-07-05
- 决策：`/browser-automation/action` 只在 `browser_runtime_mode=automation_browser` 时执行；默认 `webview_system_auth` 模式下返回 `mode_not_enabled`。action 执行不直接使用收到 HTTP 请求的 Activity 本地 WebView，而是通过 `BrowserAutomationControllerRegistry` 按 session 找到真实承载页面的 controller。
- 理由：LocalServer 可能由主 Activity 提供，但临时网页通常显示在 CardRunActivity；只依赖本地 `activeSessionId` 会找不到 session 或误用空 WebView。模式门禁可以保证第一模式仍是“WebView + 系统浏览器登录”，不会被自动化 endpoint 意外驱动。
- 影响：A3 action smoke 能在 OnePlus 8T 上通过 endpoint 驱动真实 CardRun WebView；后续 A4 需要补 session/status 查询时继续沿用同一 registry/store 边界。

## ADR-A007 本地自动化测试页作为 action 验证基准

- 日期：2026-07-05
- 决策：新增 `GET /browser-automation/test-page`，用于验证 `type -> click -> waitFor` 的 DOM 交互闭环。
- 理由：不应把 `/toolchain` 或真实业务页面当 action 验证基准，否则点击按钮可能触发工具链任务或外部副作用。测试页只改变 DOM，能证明自动浏览器能力而不污染业务状态。
- 影响：A3 真机验收使用 `Kite Automation Test` 页面；后续新增 action 类型时优先扩展这个无副作用页面做 smoke，再接真实本地 Web UI。

## ADR-A008 Session 查询 endpoint 默认返回最新未关闭 session

- 日期：2026-07-05
- 决策：`GET /browser-automation/session` 支持显式 `sessionId` / `instanceId` 查询；未传参数时返回最新未关闭 session，并附带最近 snapshot 和最近 action result。
- 理由：AI/脚本从 `/open-web` 打开临时网页时，调用方未必立刻知道 CardRun 生成的 instanceId/sessionId。默认返回最新 session 能让端到端 demo 和轻量 agent 流程先跑通；明确参数仍保留给多 session 场景。
- 影响：A4 已具备提交 action 和读取状态的 HTTP 闭环。后续多窗口/并发任务需要优先传 `sessionId` 或 `instanceId`，避免默认 latest 选错页面。

## ADR-A009 A5 扩展动作继续复用 session/action/result 协议

- 日期：2026-07-05
- 决策：`scroll`、`evaluate`、`screenshot`、console 证据都接入现有 `BrowserAutomationController` 和 `BrowserAutomationSessionStore`，不新建平行状态。成功 action 后由 controller 主动补采 snapshot；截图文件只保存到应用私有目录，并通过 result 的 `artifactPath` 暴露路径；console 由 WebView `WebChromeClient` 写入当前 automation session。
- 理由：自动浏览器需要让 AI/脚本从同一接口读到页面、动作、证据和失败原因。把证据分散到页面本地状态或临时日志里，会让长期任务难恢复，也不符合“状态拥有者负责改状态和发信号”的项目规则。
- 安全边界：`evaluate` 第一版只允许本地/可信 URL；非本地普通网页返回 `untrusted_evaluate_blocked`。OAuth/SSO 页面仍保持外部浏览器边界，不被自动浏览器拉回 WebView 执行。
- 影响：A5 后自动浏览器具备基本 AI 驱动闭环；后续可在同一协议上继续补 action 历史列表、网络证据和更强的多 session 调度。

## ADR-A010 网络证据只记录脱敏元数据

- 日期：2026-07-05
- 决策：A6 的网络证据由 WebViewClient 旁路记录请求和 HTTP 错误，保存到 `BrowserAutomationSessionStore`，并通过 `/browser-automation/network` 查询。记录字段限定为 kind、method、脱敏 URL、是否主框架、状态码、reason 和时间；不保存请求头、响应头、cookie、authorization、body 或 token 原文。
- 理由：AI/脚本需要知道页面是否发起了网络请求、请求是否 404/失败、请求和 action 的相对顺序；这些信息足以解释多数本地 Web UI 行为。保存 headers/body 会明显扩大敏感信息面，不符合自动浏览器的账号边界。
- 影响：A6 可以证明本地测试页点击后触发了 `/status?automationNetwork=...`；后续如果需要更完整网络诊断，应继续保持脱敏和显式调试开关，不把本功能变成默认抓包器。

## ADR-A011 可访问树先采用 Web DOM 派生语义树

- 日期：2026-07-05
- 决策：A7 的可访问树能力先在 WebView snapshot JS 中从 DOM 派生 role/name/state/rect，并存入 `BrowserAutomationSnapshot.accessibility`。不启用 Android Accessibility Service，不控制系统浏览器，不申请全局辅助权限。
- 理由：Kite 自动浏览器第一阶段的主要对象是 App 内受控 WebView 和本地 Web UI。DOM 派生语义树足以让 AI/脚本按 textbox、button、status、region、heading 等语义理解页面，同时不会扩大到跨 App 全局控制。
- 安全边界：password input 的 value 不进入 accessibility name，也不进入旧的 elements 摘要或 action label；不可见节点不进入 accessibility 数组。真机用 `a7-password-should-not-leak` 和 `a7-hidden-text-should-not-leak` 做了负向验证。
- 影响：`/browser-automation/session` 的 snapshot 现在同时包含文本、元素摘要和语义节点。后续如需更接近浏览器 DevTools Accessibility Tree，可在 debug/受控设备下研究 CDP，不把系统 Accessibility Service 作为默认路线。

## ADR-A012 role/name selector 采用 Playwright 式语义定位，但执行仍在受控 WebView 内

- 日期：2026-07-05
- 决策：A8 在 `BrowserAutomationTarget` 增加可选 `name` 字段；`kind=role` 时用 DOM 派生 role + accessible name 定位元素，支持 `button + Apply greeting`、`textbox + Name`、`status + Hello A8 Role` 这类智能体常用语义操作方式。
- 理由：现代浏览器智能体和测试框架更偏向语义 selector，而不是脆弱 CSS 路径。Kite 的主要自动化对象是本地 Web UI 和受控网页，直接在 WebView 内派生 role/name 能覆盖第一阶段需求，不需要先引入全局 Accessibility Service、系统浏览器控制或重型 CDP 依赖。
- 安全边界：role/name 匹配不读取 password input 的真实 value；OAuth/SSO 和账号授权页仍由第一模式外部浏览器处理，不进入自动浏览器策略绕过。
- 影响：AI/脚本现在可以通过 `/browser-automation/action` 提交 role/name target；旧的 css/text/role target 继续兼容。后续如要对接 Playwright/CDP/Appium，可把这套协议作为 Android 侧适配层。

## ADR-A013 批量 run 是单步 action 的轻量编排层

- 日期：2026-07-05
- 决策：A9 新增 `POST /browser-automation/run`，但 run 不直接操作 WebView，也不复制 session/action 状态；它只按顺序调用现有单步 `browserAutomationAction` handler，并汇总每步 result。
- 理由：AI/脚本通常需要一次提交多步任务，但真正的动作执行、超时、模式门禁、snapshot 刷新、CardRunStore 写回和证据持久化已经由单步 action 链验证过。复用单步链可以避免新建平行执行器导致行为分叉。
- 安全边界：run 继承单步 action 的 `mode_not_enabled`、`untrusted_evaluate_blocked`、OAuth/SSO 外部分流和敏感字段脱敏；默认 `stopOnFailure=true`，避免失败后继续误操作页面。
- 影响：外部智能体可以用一次 HTTP 请求完成 `type -> click -> waitFor` 等序列，并拿到 `requestedCount`、`completedCount`、`stoppedOnFailure` 和完整 result 列表。后续如果需要更复杂的任务编排，应继续在 run 层增加轻量元数据，而不是绕过 action controller。

## ADR-A014 open-run 只组合打开和 run，不改变登录边界

- 日期：2026-07-05
- 决策：A10 新增 `POST /browser-automation/open-run`，用于把外部智能体常见的 `open-web -> 等 session -> run` 合并为一次请求。实现上先检查自动浏览器模式，再调用现有 `openWeb` 回调，等待目标 session ready，最后复用 A9 run 链。
- 理由：外部智能体不应自己猜最新 session 或手写等待循环；Kite 内部更清楚 session store 和运行实例状态。把等待逻辑放在 LocalServer 层可以减少误选页面，同时保持 action 执行路径一致。
- 安全边界：默认 `webview_system_auth` 模式下 open-run 返回 `mode_not_enabled`，且 `open.requested=false`；OAuth/SSO URL 仍会被 `BrowserHandoffPolicy` 分流，open-run 不把账号授权页拉回 WebView。
- 影响：AI/脚本现在可以用单次 HTTP 请求打开本地 Web UI 并执行语义动作序列。后续多 session/并发任务仍应优先使用显式 `sessionId` 或 `instanceId`，避免依赖 latest。

## ADR-A015 run history 进入 BrowserAutomationSessionStore

- 日期：2026-07-05
- 决策：A11 把 `BrowserAutomationRunResult` 持久化到 `BrowserAutomationSessionStore` 的 `runs_v1`，并通过 `/browser-automation/runs` 和 `/browser-automation/session.runs` 暴露恢复查询。
- 理由：长期智能体任务会断线、重启或跨回合复盘；只把 run 汇总放在 HTTP 响应里，会让外部调用方丢失失败原因。run history 与 session/action/snapshot/console/network 属于同一份自动浏览器事实，应由同一个 store 管理。
- 安全边界：run history 只保存脱敏 JSON；URL 里的 token/password/code 等 query 值变成 `present`，普通文本里的敏感赋值和 Bearer 片段也会脱敏。仍不保存 cookie、请求头、Authorization 原文或账号凭据。
- 影响：外部智能体可以按 `runId` 恢复单次任务结果，也可以按 `sessionId` 查看最近任务序列。后续如增加更复杂任务编排，应继续把可恢复摘要接入该 store，而不是另建平行任务日志。

## ADR-A016 observe 是只读智能体摘要层

- 日期：2026-07-05
- 决策：A12 新增 `GET /browser-automation/observe`，只从 `BrowserAutomationSessionStore` 读取 session、snapshot、action 和 run，生成面向智能体决策的紧凑 JSON；不执行 action、不打开页面、不刷新 WebView。
- 理由：智能体的循环通常是“观察页面 -> 决策 -> 执行动作”。完整 `/browser-automation/session` 适合复盘和调试，但信息较重，外部智能体每一步都解析完整 session 会增加错误面。observe 提供稳定的 page、interactive、suggestedTarget、recentAction、recentRun，可以直接接到下一步 action/run。
- 安全边界：observe 继续使用 Web DOM 派生 accessibility，不启用 Android Accessibility Service，不控制系统浏览器，不改变 OAuth/SSO 外部分流边界。输出只读且脱敏，不保存或暴露 cookie、token、password、authorization 原文。
- 影响：外部智能体现在可以用 `/observe` 作为 Playwright/Computer Use 风格的轻量观察面。后续如果做任务队列、循环执行器或 Playwright 风格 DSL，应优先消费 observe，而不是自行解析完整 session。

## ADR-A017 artifact endpoint 只服务 screenshot PNG

- 日期：2026-07-05
- 决策：A13 新增 `GET /browser-automation/artifact?path=...`，但只允许读取应用私有 `files/browser-automation/screenshots` 目录下的 `.png` 文件。实现使用 canonical path 校验，拒绝路径穿越、截图目录外路径和非 PNG 后缀。
- 理由：A5 的 screenshot action 已能生成 PNG，但只返回设备内部 `artifactPath`；外部智能体无法直接读取图像证据。受控 artifact endpoint 补齐“截图动作 -> 证据文件 -> 外部读取”的链路，同时避免把 LocalServer 变成任意文件下载器。
- 安全边界：artifact endpoint 不读取 shared_prefs、数据库、缓存或任意应用私有文件；不暴露 cookie、token、authorization、请求头或 body。当前只返回 `image/png`，缺失和拒绝都返回结构化 JSON 错误。
- 影响：外部智能体可以在执行 screenshot action 后直接下载 PNG 做视觉检查。后续如需要其它 artifact 类型，必须逐类增加白名单和验收，而不是放宽目录校验。

## ADR-A018 evaluate 信任边界由共享策略公开给 observe

- 日期：2026-07-05
- 决策：A14 新增 `BrowserAutomationPageTrust`，由执行层和 observe 共同使用。observe 的 `page.scope` / `page.trustedForEvaluate` 明确告诉外部智能体当前页面是否允许 `evaluate`。
- 理由：`evaluate` 已在执行层限制为本地/可信页面；如果 observe 不公开该边界，智能体只能通过失败响应试错。共享策略能避免观察层和执行层对“可信页面”的定义漂移。
- 安全边界：本地/可信范围限定为 `file`、`localhost`、`127.*`、`::1` 和 `appassets.androidplatform.net`；普通远程 HTTP/HTTPS 页面为 `remote`，`trustedForEvaluate=false`。这不是新增授权，也不改变 OAuth/SSO 外部分流。
- 影响：智能体可在观察阶段避免向普通网页提交任意 JS。后续如果扩大可信范围，必须修改同一策略并补测试和决策记录。

## ADR-A019 URL target 只作为页面状态等待条件

- 日期：2026-07-05
- 决策：A15 新增 `kind=url` target，但它只服务 `find` / `waitFor`，读取当前 WebView 的 `location.href` 并按 `contains` / `exact` 匹配。`click`、`type`、`scroll` 等需要 DOM 元素的动作对 URL target 返回 `target_not_actionable`。
- 理由：智能体经常需要等待 query、path 或 hash 跳转完成；如果只能等 DOM 文本，就容易写固定 sleep 或误选页面。但 URL 不是可交互元素，不能被点击或输入。把 URL 等待并入同一 target 协议，可以补齐 Playwright 风格的等待能力，同时不新建平行执行器。
- 安全边界：URL 匹配结果只返回 `matched url` 和脱敏后的当前 URL；失败 detail 不回显目标 URL 原文。OAuth/SSO 分流和默认模式门禁不变。
- 影响：open-run / run / action 都可以使用 `{"target":{"kind":"url","value":"#ready"}}` 等待导航状态。后续如果增加更复杂 URL 条件，应继续放在等待语义里，而不是把 URL target 当普通 DOM selector。

## ADR-A020 State target 只作为页面状态等待条件

- 日期：2026-07-05
- 决策：A16 新增 `kind=state` target，只服务 `find` / `waitFor`，用于读取 `document.readyState` 和短暂 DOM idle。当前支持 `domReady`、`complete`、`idle`，其中 `idle` 默认 500ms，也可通过 action `value` 或 `idle:<ms>` 指定。
- 理由：智能体打开页面后需要一个比固定 sleep 更可靠的“页面已可读/短暂稳定”信号；但 WebView 内部无法低成本精确统计完整网络静默，所以第一阶段用 readyState + MutationObserver 作为轻量等待条件。
- 安全边界：state target 不执行页面业务 JS，不读取 cookie/token/password，不控制系统浏览器，也不替代 OAuth/SSO 外部分流。`click`、`type`、`scroll` 等需要 DOM 元素的动作对 state target 返回 `target_not_actionable`。
- 影响：open-run / run / action 可以在真实动作前插入 `{"target":{"kind":"state","value":"domReady"}}` 或 `{"target":{"kind":"state","value":"idle"},"value":"300"}`，减少固定 sleep。后续如实现 CDP 网络静默，应作为受控调试能力单独扩展，不把当前 idle 误称为完整 network quiet。

## ADR-A021 press 第一阶段采用 WebView 内部键盘事件

- 日期：2026-07-05
- 决策：A17 新增 `press` action，但第一阶段只在受控 WebView 页面内对目标元素或当前 activeElement 派发 KeyboardEvent；支持 Enter、Escape、Tab、Space、Backspace、Delete、方向键和单字符。不使用 Android 全局按键注入，不控制系统浏览器或输入法。
- 理由：智能体常见任务需要 `type -> press Enter -> waitFor`、Escape 关闭浮层、Tab/方向键推进焦点等键盘动作。Kite 第一阶段的自动浏览器对象是 App 内受控 WebView，页面内 KeyboardEvent 已能覆盖本地 Web UI 的主要交互，同时不扩大到跨 App 或账号授权页控制。
- 安全边界：`press` 复用现有 action 模式门禁、role/name selector、敏感字段脱敏和 OAuth/SSO 外部分流。`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式返回 `mode_not_enabled`，不会打开或驱动自动浏览器。
- 影响：外部智能体可通过 run/open-run 提交 `press`，observe 的 textbox 建议动作也包含 `press`。后续如果需要更接近真实浏览器输入、表单默认提交或系统级按键，应作为 CDP/WebDriver/受控系统输入能力单独设计和验证。

## ADR-A022 select 第一阶段只支持 HTML 表单下拉

- 日期：2026-07-05
- 决策：A18 新增 `select` action，但第一阶段只操作受控 WebView 内的 HTML `<select>` 元素；支持按 option `value`、可见文本和 `index:<n>` 选择，并派发 `input` / `change` 事件。不控制 Android 原生 Spinner、系统选择弹窗或系统浏览器下拉。
- 理由：本地 Web UI 和设置页常见表单会使用 `<select>`；让智能体通过专门 action 选择 option，比用 `evaluate` 或点击原生菜单更可恢复、可审计，也能复用现有 role/name selector 和 run/open-run 执行链。
- 安全边界：`select` 复用现有模式门禁、target 脱敏和 OAuth/SSO 外部分流。非 `<select>` 目标返回 `target_not_selectable`；`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。
- 影响：外部智能体可对 `role=combobox` 的 HTML 下拉提交 `select`；observe 对 combobox 建议 `select`。如果后续需要处理 ARIA 自绘 combobox、系统弹窗或多选复杂语义，应单独扩展，不把当前 HTML select 实现泛化成全局下拉控制。

## ADR-A023 check 第一阶段采用确定性状态设置

- 日期：2026-07-05
- 决策：A19 新增 `check` action，用于把 checkbox、radio 和 switch-like 状态控件设置为明确状态，而不是复用 `click` 翻转。第一阶段支持 HTML checkbox/radio 和 `role=checkbox|radio|switch` 的 ARIA 控件；checkbox 支持 true/false/toggle，radio 只支持设置 true。
- 理由：智能体任务需要表达“确保已选中/未选中”，不是“点一下”。只靠 click 会在页面初始状态、重复执行或恢复执行时变成不确定行为。把状态控件独立成 action，能保持 run/open-run 可恢复，也更接近 Playwright/WebDriver 的确定性表单动作。
- 安全边界：`check` 仍只在受控 WebView 内执行，复用现有模式门禁、role/name selector、敏感字段脱敏和 OAuth/SSO 外部分流。非状态控件返回 `target_not_checkable`；`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。
- 影响：外部智能体可对 checkbox/radio/switch 提交 `check`，observe 对这些 role 优先建议 `check`。后续如需处理自绘复杂控件、Android 原生控件或系统浏览器状态控件，应单独扩展，不把当前 DOM 状态设置泛化成跨 App 控制。

## ADR-A024 artifactUrl 是截图证据的恢复链接，不是新文件权限

- 日期：2026-07-05
- 决策：A20 在 `BrowserAutomationActionResult.toJson()` 中为带 `artifactPath` 的结果生成相对 `artifactUrl`，并在 observe 的 `recentAction` / `recentRun` 摘要中暴露最近 artifact 线索。`artifactUrl` 指向既有 `/browser-automation/artifact` endpoint。
- 理由：智能体的观察循环和断线恢复需要从轻量结果直接拿到截图证据。只返回设备内部路径会迫使调用方拼接 endpoint，容易跨端口、URL 编码和恢复场景出错。相对 URL 可以让 host 侧和设备侧各自拼当前 origin，同时保留原有 artifactPath 方便复盘。
- 安全边界：`artifactUrl` 不新增目录权限，不开放任意文件读取；所有下载仍经过 `BrowserAutomationArtifactResolver` 的 canonical path 校验，只允许应用私有 screenshots 目录下的 PNG。默认模式门禁和 OAuth/SSO 外部分流不变。
- 影响：外部智能体可以从 action/session/actions/runs/observe 直接取到 screenshot 下载入口。后续如扩展非截图 artifact，必须逐类白名单并新增验收，不可复用该 URL 机制绕过 resolver。

## ADR-A025 click prelude 采用 WebView 内部 pointer/mouse 事件，不做系统触摸注入

- 日期：2026-07-05
- 决策：A21 增强 `click` action，在 `element.click()` 前派发目标中心点的 `pointerdown`、`mousedown`、`pointerup`、`mouseup`。保留 `element.click()` 作为最终 activation，不改用 Android 全局触摸或坐标注入。
- 理由：许多真实 Web UI 在 pointerdown/mousedown 阶段准备菜单、pressed 状态或拖拽/选择上下文；只调用 `element.click()` 会漏掉这些前置状态。先派发 down/up 序列可以提升与现代 Web 框架的兼容性，同时保留 click 默认行为。
- 安全边界：事件只在受控 WebView DOM 内派发，不控制系统浏览器、不模拟 Android 触摸屏、不绕过 OAuth/SSO 外部分流。`PointerEvent` 不可用时只派发 mouse 事件；失败不会扩大到系统输入权限。
- 影响：普通 button click 继续可用，依赖 pointerdown/mousedown 的本地 Web UI 能被自动浏览器驱动。后续如需要更真实的触摸、拖拽、长按或多点手势，应单独设计，不把当前 click prelude 泛化成系统级输入。

## ADR-A026 hover 第一阶段采用 WebView 内部 pointer/mouse over/move 事件

- 日期：2026-07-05
- 决策：A22 新增 `hover` action，在目标中心点派发 `pointerover`、`pointerenter`、`mouseover`、`mouseenter`、`pointermove`、`mousemove`。不调用 `element.click()`，不使用 Android 全局鼠标、触摸或 Accessibility Service。
- 理由：智能体浏览器经常需要打开 hover 菜单、tooltip 或 popover；这些 UI 依赖 over/enter/move，而不是 click。把 hover 作为独立 action，比让外部智能体用 click 或 evaluate 绕开更可恢复、更可审计，也更接近 Playwright/WebDriver 的动作模型。
- 安全边界：`hover` 仍只在受控 WebView DOM 内执行，复用现有模式门禁、target 脱敏、role/name selector 和 OAuth/SSO 外部分流。`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。
- 影响：外部智能体可以通过 run/open-run 对按钮、链接等语义节点提交 `hover`，observe 对可点击语义节点建议 `click,hover,find,waitFor`。后续如果需要拖拽、真实触摸轨迹、长按或多点手势，应继续作为单独动作设计，不把 hover 泛化成跨 App 控制。

## ADR-A027 navigate 只控制当前 WebView 历史，不打开任意 URL

- 日期：2026-07-05
- 决策：A23 新增 `navigate` action，但第一阶段只支持 `back`、`forward`、`reload`，并要求 `target.kind=none`。`navigate` 不接受 URL，不设置 `location.href`，未知 value 返回 `unsupported_navigation_value`。
- 理由：智能体循环需要在同一 session 内执行后退、前进和刷新；让它用 `evaluate` 操作 history 不够清晰，也难以审计。与此同时，任意 URL 打开已经由 `open-run` / `open-web` 承担，并且那里有 `BrowserHandoffPolicy` 负责 OAuth/SSO 分流。
- 安全边界：`navigate` 复用现有自动模式门禁；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。它不新增网页登录策略，不把账号授权页拉回 WebView，也不绕过系统浏览器登录方案。
- 影响：外部智能体可以在 run/open-run 中组合 `click link -> navigate back -> waitFor url -> navigate forward -> reload`。后续如果需要 `goto(url)`，必须单独接入 `BrowserHandoffPolicy` 和账号授权边界，不应复用当前 `navigate` 绕开。

## ADR-A028 capabilities 由共享对象同时供 endpoint 和 observe 使用

- 日期：2026-07-05
- 决策：A24 新增 `BrowserAutomationCapabilities`，把自动浏览器 actions、targets、runs、endpoints、authBoundary 和 evaluate 边界收口为同一份事实来源。`/browser-automation/capabilities` 和 `/browser-automation/observe.capabilities` 都从这里生成。
- 理由：智能体通常按“observe -> 决策 -> action/run”循环工作。如果 observe 不带能力摘要，调用方要额外请求 capabilities 或记忆协议，新增 action 后容易漂移。共享对象避免 endpoint 和 observe 各自硬编码列表。
- 安全边界：capabilities 是静态能力摘要，不读取页面数据、不暴露 cookie/token/password、不执行 action、不打开 URL。observe 仍然只读，默认模式门禁和 OAuth/SSO 外部分流不变。
- 影响：外部智能体可以只靠 observe 得到当前页面、建议 target、最近结果和可用动作集合。旧脚本继续读取 capabilities endpoint 的逗号分隔字符串字段；新脚本可读取 `actionList` / `targetList` / `endpointList` 或 observe 的数组字段。

## ADR-A029 doubleClick 采用 WebView 内部双 click prelude 加 dblclick

- 日期：2026-07-05
- 决策：A25 新增 `doubleClick` action，在受控 WebView DOM 内对目标元素执行两轮 `pointerdown/mousedown/pointerup/mouseup + element.click()`，再派发 `dblclick` MouseEvent。该动作复用现有 css/text/role/role+name target、模式门禁、action/run/open-run 链路和 observe 建议动作。
- 理由：真实 Web UI 和智能体浏览器任务里常见双击打开、编辑、选中等交互。用两次独立 `click` 无法保证触发 `dblclick` 监听器；用 `evaluate` 绕开又不可审计。把它作为独立 action 能让外部智能体清楚表达意图，并复用既有 run 恢复和错误结构。
- 安全边界：`doubleClick` 只在 Kite 受控 WebView 内派发 DOM 事件，不做 Android 全局触摸、不控制系统浏览器、不改变 OAuth/SSO 外部分流。`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式返回 `mode_not_enabled`。
- 影响：外部智能体可通过 action/run/open-run 提交 `doubleClick`；observe 对可点击语义节点建议 `click,hover,doubleClick,find,waitFor`。后续如需拖拽、文件上传、iframe 或真实触摸轨迹，应继续作为独立动作/能力扩展。

## ADR-A030 iframe 第一阶段只进入同源文档

- 日期：2026-07-05
- 决策：A26 让 snapshot 和 action selector 递归进入同源 `iframe/frame` 的 `contentDocument`；同源 frame 内节点带 `framePath`、`frameUrl`、`frameName` 标记。跨源、sandbox 或其它不可访问 iframe 只输出 `role=iframe` 摘要与 `frameAccessible=false`，不读取内部 DOM。
- 理由：智能体浏览器需要处理嵌入式本地工具和组件页；同源 iframe 在浏览器安全模型内可读可操作，适合作为第一阶段通用能力。跨源 iframe 往往承载第三方登录、支付、验证码或外部组件，绕过同源策略既不稳定也越过账号/平台边界。
- 安全边界：不尝试绕过浏览器同源策略，不使用 Accessibility Service 控制系统浏览器，不保存 iframe 内 cookie/token/password。`frameUrl` 进入 Kotlin parser/store/observe 前走同一套 URL 脱敏；sandbox frame 内文本不会进入 snapshot/observe。
- 影响：外部智能体可用原有 `css/text/role/role+name` target 操作同源 iframe 内元素，无需新增 target kind。observe 可以显示节点来自哪个 frame；后续如果要做跨源 frame、CDP frame tree 或文件上传，应作为显式授权的单独能力设计。

## ADR-A031 Shadow DOM 第一阶段只进入 open shadowRoot

- 日期：2026-07-05
- 决策：A27 让 snapshot 和 action selector 递归进入同源 document / iframe document 内的 open `shadowRoot`；open shadow 内节点带 `shadowPath`、`shadowHost` 标记。closed shadow root 只保留 host 元素本身，不读取内部 DOM。
- 理由：现代 Web Components 常把真实按钮、输入框和状态文本放在 open shadow root 内；这是页面脚本可访问的标准 DOM 边界，适合作为第一阶段通用自动浏览器能力。closed shadow root 明确表示组件作者不暴露内部结构，强行读取既不稳定也越过浏览器封装边界。
- 安全边界：不尝试绕过 closed shadow root，不使用 Accessibility Service 或系统浏览器控制读取内部内容，不保存 closed shadow 内 cookie/token/password/隐藏文本。`shadowHost` 进入 store/observe 前走文本截断和脱敏；closed shadow 探针文本不会进入 snapshot/observe。
- 影响：外部智能体可用原有 `css/text/role/role+name` target 操作 open shadow 内元素，无需新增 target kind。observe 可以显示节点来自哪个 shadow host；后续如果要处理 closed shadow、第三方组件专用 API 或 CDP DOM domain，应作为显式授权的单独能力设计。

## ADR-A032 sessions endpoint 是只读发现入口

- 日期：2026-07-05
- 决策：A28 新增 `GET /browser-automation/sessions`，只从 `BrowserAutomationSessionStore` 读取 session 摘要，按 `updatedAt` 最新优先返回；支持 `limit`、`includeClosed` 和 `instanceId` 过滤。它不打开页面、不执行 action、不刷新 snapshot。
- 理由：长期智能体任务会出现多个 Web surface、跨回合恢复和断线重连。如果只能依赖 latest session 或对话记忆里的 sessionId，外部智能体容易选错页面。sessions endpoint 让调用方先发现现有页面，再用显式 `sessionId` 调 observe/action。
- 安全边界：sessions 只暴露已有 session 的脱敏摘要，不返回 snapshot 文本、action 详情、run 详情、cookie、token、password、authorization 或请求头。默认 `webview_system_auth` 模式下它仍只读可查，但动作型接口继续由 `mode_not_enabled` 门禁拦住。
- 影响：外部智能体可先调用 `/browser-automation/sessions` 选择目标 session，再调用 `/browser-automation/observe?sessionId=...` 或 `/browser-automation/run`。后续如要增加 session 关闭、命名或多窗口调度，应单独设计写操作和权限边界，不复用只读发现入口偷改状态。

## ADR-A033 clear 是确定性编辑动作，不改变 type 覆盖语义

- 日期：2026-07-05
- 决策：A29 新增 `clear` action，用于清空受控 WebView 内的 HTML input、textarea 和 contenteditable，并派发 `input` / `change` 事件；`type` 继续保持已验证的覆盖填入语义。
- 理由：智能体任务经常需要表达“先清空再输入”，这应该是可审计的动作，而不是靠多次 Backspace、选择文本、空字符串 `type` 或页面专用 JS。把 `clear` 独立出来可以提升恢复执行和表单状态同步的确定性，同时避免改变旧脚本对 `type` 的预期。
- 安全边界：`clear` 只操作当前 App 受控 WebView DOM，不控制系统浏览器、不使用 Android Accessibility Service、不绕过 OAuth/SSO 外部分流。非可编辑目标返回 `target_not_editable`；`kind=url` / `kind=state` 返回 `target_not_actionable`；默认 `webview_system_auth` 模式继续返回 `mode_not_enabled`。
- 影响：observe 对 textbox 建议 `type,clear,press,find,waitFor`；外部智能体可用 `clear` 明确表达输入框清空。后续如需文件上传、复杂富文本编辑或系统级输入，应作为单独动作设计，不把 `clear` 泛化成跨 App 控制。

## ADR-A034 observe target index 复用现有 selector 序号

- 日期：2026-07-05
- 决策：A30 在 observe 的 `interactive[].suggestedTarget` 中增加 `index`，用于同 role/name 元素去歧义。该 index 复用现有 `BrowserAutomationTarget.index` 语义：同一 selector 结果中第 N 个匹配项，首个为 0。
- 理由：智能体浏览器需要把页面“元素化”为可直接操作的 target。只给 role/name 在重复按钮、列表项、表格行和表单行里会误选第一个元素；另建临时元素句柄会引入生命周期和跨 snapshot 失效问题。先复用已有 index 能低成本提升可操作性，并保持 action 协议稳定。
- 安全边界：`index` 只是 selector 去歧义元数据，不读取 cookie、token、password，不提供跨域 iframe 或 closed shadow 访问能力，不改变 OAuth/SSO 外部分流。默认 `webview_system_auth` 模式下动作接口仍由 `mode_not_enabled` 拦截。
- 影响：外部智能体可以直接把 observe 返回的 `suggestedTarget` 传给 action/run/open-run；旧脚本不传 index 时仍默认第一个匹配项。后续如需要更强恢复能力，可再设计受控元素引用，但不把本次 index 当作永久 DOM 句柄。

## ADR-A035 disabled/readonly 按真实 actionability 拒绝伪成功

- 日期：2026-07-05
- 决策：A31 在动作层加入通用 actionability 守卫。`click`、`doubleClick`、`type`、`clear`、`select`、`check` 和目标化 `press` 遇到 disabled 目标返回 `target_disabled`；`type` / `clear` 遇到 readonly 目标返回 `target_readonly`。observe 对 `enabled=false` 的交互节点只建议 `find`。
- 理由：智能体浏览器的动作结果要接近真实用户可执行行为。直接用 JS 改 disabled/readonly 控件的值或报告点击成功，会让外部智能体误以为页面接受了操作，后续决策会建立在假状态上。
- 安全边界：守卫基于通用 DOM 状态：HTML disabled、`:disabled`、`aria-disabled=true`、HTML readonly 和 `aria-readonly=true`，不是针对测试页或某个站点按钮的特判。`hover`、`find`、`waitFor` 和 observe 保持只读/悬停语义，不因为 disabled/readonly 本身失败。
- 影响：自动浏览器会更早返回结构化失败，让调用方改走可用控件、等待状态变化或请求人工介入。普通 enabled 控件、同源 iframe、open shadow 和 observe target index 的既有能力保持不变。

## ADR-A036 run/open-run 用 action history 校准迟到结果

- 日期：2026-07-05
- 决策：A32 继续以 `BrowserAutomationSessionStore` 为唯一事实来源。run/open-run 在单步 action 返回 `TimedOut/request_timeout` 时最多等待 4 秒同 `actionId` 的最终 action history；run 查询、session 摘要和 observe 的 `recentRun` 也按同一 `actionId` / `sessionId` 用已落库 action result 校准旧 run 汇总。
- 理由：真机 A31 发现 run 汇总层可能先因 HTTP 等待窗口超时写入 `request_timeout`，但 WebView action 随后完成并写入成功结果。如果恢复查询继续显示旧 timeout，外部智能体会看到互相矛盾的事实，难以判断是否重试。
- 安全边界：校准只接受同 `actionId`、同 `sessionId`、完成时间不早于原 timeout、且自身不是 `request_timeout` 的 action result；没有迟到落库结果时，真实 timeout 保持 `TimedOut/request_timeout`。这不是按站点、页面或测试 runId 的特判，也不改变登录/OAuth 外部分流边界。
- 影响：`/browser-automation/runs`、`/browser-automation/session` 和 `/browser-automation/observe.recentRun` 会看到同一份校准后的 run 结果。open-run 在短暂迟到窗口内优先返回最终 action 结果，减少刚返回即矛盾的恢复状态。
