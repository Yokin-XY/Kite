# Kite 浏览器自动化执行手册

> 本文件是“浏览器自动化”长期目标的唯一事实来源。它服务 Kite 第二浏览器模式：设置页中的“自动浏览器”。网页登录回跳第一模式仍以 `docs/browser-login/` 为准。

## 0. 开机自检

每次继续本任务前必须先做：

1. 读 `docs/browser-automation/PLAYBOOK.md`。
2. 读 `docs/browser-automation/PROGRESS.md`。
3. 读 `docs/browser-automation/DECISIONS.md`。
4. 确认当前目录为 `D:\xm\Kite-browser-login`。
5. 确认本线设备仍是 OnePlus 8T `3f8bbaad`；不进入 X11 / MEIZU 任务线。
6. 如涉及 APK 构建、安装或真机验证，先读 `references/toolchain.md`。

每开始一个任务前，必须在 `PROGRESS.md` 写三问自检：

1. 目标是什么。
2. 完成标准是什么。
3. 前置任务是否完成。

## 1. 北极星目标

把 Kite 的第二浏览器模式做成可被 AI / 脚本稳定驱动的自动浏览器运行面：

- 能加载本地端口 Web UI 和普通网页。
- 能观察 DOM、文本、可访问树、网络、控制台和截图。
- 能执行点击、输入、滚动、等待元素、读取结果等动作。
- 能把自动化任务状态写回 Kite 的运行实例，而不是只在后台静默跑。
- 账号授权和 OAuth/SSO 仍沿用第一模式的系统浏览器 / Custom Tabs / 官方回跳边界。

## 2. 工作线绑定

- 物理目录：`D:\xm\Kite-browser-login`
- 当前分支：`codex/browser-login-return`
- 绑定设备：OnePlus 8T
- ADB serial：`3f8bbaad`
- 设置入口：`MainActivity.showSettings()` 的“浏览器模式”
- 模式状态：`kite_app_settings.browser_runtime_mode`
- 默认模式：`webview_system_auth`
- 第二模式：`automation_browser`

## 3. 红线

- 不把“自动浏览器”实现成网页登录策略绕过工具。
- 不把 OAuth/SSO 授权页拉回 WebView 承载；账号授权仍按第一模式外部浏览器边界处理。
- 不默认启用生产 WebView debugging；如果用 CDP/DevTools，必须限制在 debug、显式实验或受控设备。
- 不保存账号、密码、cookie、token、authorization code 原文到项目文档、日志或状态文件。
- 不用 Accessibility Service 做全局设备控制的默认方案；它只可作为用户明确授权的辅助/兜底研究项。
- 不硬编码某个网站的按钮路径作为通用自动化能力；要建立 selector / action / wait / report 的通用协议。
- 不与 X11 线共用设备、端口、截图、日志输出路径。

## 4. 任务梯队

### A0 建立浏览器自动化任务基线

- 问题证据：用户要求把第二模式正式开成“浏览器自动化”长期目标，先收集资料，再实现。
- 解法：建立三件套、固定第一模式/第二模式边界、写入提交与合并策略。
- 验收标准：
  - [x] `docs/browser-automation/PLAYBOOK.md` 存在。
  - [x] `docs/browser-automation/PROGRESS.md` 存在。
  - [x] `docs/browser-automation/DECISIONS.md` 存在。
  - [x] 明确第一模式已经成功到机制层，第二模式是新长期目标。
  - [x] 明确不直接提交主线的默认策略。
- 依赖：浏览器登录 B6 已完成。

### A1 调研自动浏览器技术路线

- 问题证据：用户要求先收集资料，理解现在 AI 浏览器自动化的主流做法，再决定实现路线。
- 解法：优先查官方和主流资料，覆盖 WebView 调试、Chrome DevTools Protocol、Playwright Android、WebDriver/WebDriver BiDi、Appium Hybrid、Android Accessibility 和 WebView 安全边界。
- 验收标准：
  - [x] 至少 8 个可追溯来源。
  - [x] 区分官方规范、官方工具、测试框架和社区经验。
  - [x] 输出 Kite 推荐路线、备选路线和反路线。
  - [x] 明确哪些能力适合第一阶段实现，哪些必须后置。
- 依赖：A0。

### A2 设计 Kite 自动浏览器协议

- 问题证据：Kite 需要的不是单次页面点击，而是 AI/脚本可长期调用的浏览器控制面。
- 解法：设计 browser automation session、action queue、selector 策略、等待条件、截图/DOM/日志证据、错误状态和运行实例绑定。
- 验收标准：
  - [x] 协议覆盖打开 URL、查询元素、点击、输入、滚动、执行 JS、截图、读取 DOM 文本、等待条件。
  - [x] 协议能把结果写入 `CardRunStore` 或等价状态拥有者。
  - [x] 协议不复制登录事实，不保存敏感 token 原文。
  - [x] 协议能区分本地 Web UI、普通网页、OAuth/SSO 页面和不可自动化页面。
- 依赖：A1。

### A3 实现最小自动浏览器内核

- 问题证据：设置页已有“自动浏览器”模式，但目前只是入口和持久化状态。
- 解法：在自动模式下建立一个受控 WebView 运行面，先支持本地 URL 的打开、DOM 读取、点击/输入/等待和截图证据。
- 验收标准：
  - [x] 切换到 `automation_browser` 后，打开本地测试页能创建 automation session。
  - [x] 能用通用 action 协议完成至少一个本地页面交互。
  - [x] 能输出 DOM/截图/动作结果证据。
  - [x] 默认模式 `webview_system_auth` 行为不回退。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A2。

### A4 接入 AI/脚本控制入口

- 问题证据：用户目标是 AI 很火的“自动浏览器”模式，不只是 App 内部按钮。
- 解法：提供本地 HTTP/bridge/CLI 控制接口，让 AI agent 或脚本能提交自动化动作并读取结果。
- 验收标准：
  - [x] 有受控接口提交 action。
  - [x] 有状态查询和错误报告。
  - [x] 有权限/来源边界。
  - [x] 能在 OnePlus 8T 跑一条端到端 demo。
- 依赖：A3。

### A5 扩展自动化动作和证据能力

- 问题证据：A3/A4 已支持基本 DOM 观察和 find/click/type/waitFor，但北极星目标还包括滚动、截图、执行 JS、控制台/网络证据和更完整的状态读取。
- 解法：在现有 session/action/result 协议上继续扩展，不重建平行状态；优先补 `scroll`、`snapshot action` 后的最新 DOM、截图证据文件、受限 `evaluate` 和 action 历史查询。
- 验收标准：
  - [x] `scroll` 动作在本地测试页真机通过。
  - [x] action 后可主动刷新 snapshot，并在 `/browser-automation/session` 读到最新 DOM。
  - [x] 有截图证据 API 或文件路径，不只依赖 ADB screencap。
  - [x] 受限 `evaluate` 只对本地/可信页面开放，并有拒绝错误码。
  - [x] 控制台或网络证据至少有一个可查询入口。
- 依赖：A4。

### A6 补网络证据和动作历史查询

- 问题证据：A5 已补 console、截图和最新 snapshot，但 AI/脚本仍只能读最近一个 action result；北极星目标中的“网络”证据还没有可查询入口。
- 解法：继续复用 `BrowserAutomationSessionStore`，由 WebViewClient 记录当前 session 的资源请求和 HTTP 错误，只保存脱敏 URL、方法、主框架标记、状态码和时间；LocalServer 暴露 action history 和 network 查询。
- 验收标准：
  - [x] `/browser-automation/session` 能返回最近多条 action result，不只最新一条。
  - [x] 有 `/browser-automation/actions` 或等价 endpoint 查询 action 历史。
  - [x] 有 `/browser-automation/network` 或等价 endpoint 查询网络证据。
  - [x] 网络证据不保存请求头、cookie、authorization 或 token 原文。
  - [x] 本地测试页真机触发 `fetch` 后能查询到对应网络记录。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A5。

### A7 补可访问树/语义观察

- 问题证据：北极星目标要求观察 DOM、文本、可访问树、网络、控制台和截图；A6 后 DOM/文本/网络/控制台/截图已具备，但 snapshot 还没有 role/name/state 层面的语义树。
- 解法：在现有 snapshot 里增加由 Web DOM 派生的 accessibility 节点摘要，不启用 Android Accessibility Service，也不申请全局辅助权限。节点包含 role、name、tag、level、visible、enabled、checked/selected/expanded、rect 等安全字段，并通过 `/browser-automation/session` 暴露。
- 验收标准：
  - [x] snapshot parser 能解析 accessibility 节点并持久化。
  - [x] `/browser-automation/session` 的 snapshot JSON 返回 `accessibility` 数组。
  - [x] 本地测试页真机 snapshot 能看到 textbox、button、status、section 等语义节点。
  - [x] 可访问树不保存 cookie、token、输入密码值或隐藏文本。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A6。

### A8 补 role/name 语义定位动作

- 问题证据：A7 已能观察可访问树，但 action 仍主要依赖 CSS/text，AI/脚本还不能稳定用“button + 名称”“textbox + 名称”这类语义方式操作页面。
- 解法：在现有 `BrowserAutomationTarget` 上增加可选 `name` 字段；`kind=role` 时优先按 DOM 派生 role + accessible name 匹配元素，继续兼容旧的 role 文本匹配。
- 验收标准：
  - [x] action JSON 可表达 `{"target":{"kind":"role","value":"button","name":"Apply greeting"}}`。
  - [x] `find/click/type/waitFor/scroll` 能使用 role/name target。
  - [x] role/name 匹配不读取 password input value。
  - [x] 本地测试页真机能用 role/name 完成 `type -> click -> waitFor`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A7。

### A9 补批量动作 run 接口

- 问题证据：A8 后 AI/脚本可以稳定提交单步语义 action，但真实智能体任务通常是 `type -> click -> waitFor -> snapshot` 这样的动作序列；逐条调用 `/browser-automation/action` 容易丢失中间失败原因和完整结果。
- 解法：新增 `POST /browser-automation/run`，请求体包含 `actions` 数组、公共 `sessionId`/`instanceId` 和 `stopOnFailure`；LocalServer 按顺序复用现有单步 action handler 执行，不新建平行执行器。
- 验收标准：
  - [x] run JSON 可表达一组 role/name actions，并把公共 session 信息继承到每个 action。
  - [x] run 响应返回 `runId`、`status`、`requestedCount`、`completedCount`、`stoppedOnFailure` 和每步 result。
  - [x] 任一步失败且 `stopOnFailure=true` 时停止后续步骤，并保留失败 result。
  - [x] `/browser-automation/capabilities` 暴露 `/browser-automation/run`。
  - [x] 本地测试页真机能用一次 run 完成 `type -> click -> waitFor`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A8。

### A10 补 open-run 一体入口

- 问题证据：A9 后外部智能体仍需要自己编排 `open-web -> 等 session -> run`，这会让简单任务也变成多次 HTTP 调用，并且容易在等待 session 时选错最新页面。
- 解法：新增 `POST /browser-automation/open-run`，请求体包含 `url` 和 `actions`。LocalServer 在自动浏览器模式下先调用现有 `openWeb` 回调，等待目标页面创建并 ready 的 automation session，再把 session 继承到 run 请求并复用 A9 的执行链。
- 验收标准：
  - [x] open-run JSON 可表达 `url + actions + stopOnFailure + openTimeoutMs`。
  - [x] 自动模式下 open-run 能等待新 session ready，并一次完成 `type -> click -> waitFor`。
  - [x] open-run 响应包含 `open` 摘要、run 汇总和每步 result。
  - [x] 默认 `webview_system_auth` 模式下 open-run 返回 `mode_not_enabled`，不启动页面动作。
  - [x] `/browser-automation/capabilities` 暴露 `/browser-automation/open-run`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A9。

### A11 持久化 run 结果并提供查询入口

- 问题证据：A9/A10 的 run 汇总只在 HTTP 响应中返回；如果外部智能体断线、重启或需要复盘，只能从 action history 间接还原，缺少按 `runId` 查询的恢复入口。
- 解法：把 `BrowserAutomationRunResult` 保存到 `BrowserAutomationSessionStore`，新增 `/browser-automation/runs` 查询 endpoint，并在 `/browser-automation/session` 中返回最近 run 摘要。
- 验收标准：
  - [x] run result 保存到同一份 automation store，不新建平行状态。
  - [x] `/browser-automation/runs?sessionId=...` 能返回最近 run 列表。
  - [x] `/browser-automation/runs?runId=...` 能返回指定 run。
  - [x] `/browser-automation/session` 包含最近 run 摘要。
  - [x] open-run 和 run 的成功/失败汇总都能被查询。
  - [x] run 结果不保存 cookie、token、password、authorization 原文。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A10。

### A12 补智能体 observe 观察入口

- 问题证据：A11 后外部智能体已经能执行和恢复 run，但每次做下一步决策仍要解析完整 `/browser-automation/session`，里面混有 snapshot、actions、runs、console、network 等较重信息；缺少一个面向“观察-决策-动作”循环的紧凑观察结果。
- 解法：新增 `GET /browser-automation/observe`，复用同一份 `BrowserAutomationSessionStore`，返回当前 session 摘要、页面文本、可交互语义节点、建议 action target、最近 action/run 状态和安全边界提示，不新建平行状态。
- 验收标准：
  - [x] `/browser-automation/observe?sessionId=...` 返回紧凑 JSON，包含 session、page、interactive、recentAction、recentRun、limits。
  - [x] interactive 来自现有 accessibility 节点，并生成可直接用于 action 的 `suggestedTarget`。
  - [x] 默认 latest session 查询仍可用，但显式 sessionId 优先。
  - [x] observe 不执行动作、不打开页面、不绕过默认模式门禁或登录边界。
  - [x] observe 输出不包含 password value、隐藏文本、cookie、token、authorization 原文。
  - [x] `/browser-automation/capabilities` 暴露 `/browser-automation/observe`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A11。

### A13 补截图 artifact 受控下载入口

- 问题证据：A5 的 `screenshot` action 已能把 WebView 画面保存到应用私有目录，并在 result 中返回 `artifactPath`；但外部智能体只能看到设备内部路径，无法通过本地 HTTP 直接读取 PNG 证据，截图能力还没有形成完整观察链路。
- 解法：新增 `GET /browser-automation/artifact?path=...`，只允许读取应用私有 `files/browser-automation/screenshots` 下的 PNG 文件；同时在 capabilities 中声明 artifact endpoint，不开放任意文件读取。
- 验收标准：
  - [x] screenshot action 生成的 `artifactPath` 可通过 `/browser-automation/artifact?path=...` 下载。
  - [x] artifact endpoint 返回 `image/png`，PNG 文件头正确，内容大小非零。
  - [x] 非 screenshots 目录、相对路径穿越、非 PNG 后缀会被拒绝。
  - [x] 缺失文件返回明确错误。
  - [x] `/browser-automation/capabilities` 暴露 `/browser-automation/artifact`。
  - [x] 不保存或暴露 cookie、token、authorization 原文。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A12。

### A14 在 observe 中显式暴露 evaluate 安全边界

- 问题证据：A5 已把 `evaluate` 限制在本地/可信页面；A12 的 `observe` 已成为智能体的轻量观察入口，但目前没有告诉外部智能体当前页面是否允许 evaluate。智能体可能在普通 HTTPS 页面上误发 evaluate，再靠失败响应纠错。
- 解法：在 `BrowserAutomationObservation.page` 中增加 `scope` 和 `trustedForEvaluate` 字段，复用现有 URL 信任判断语义，让智能体在决策前就能知道当前页面是否可执行 JS。
- 验收标准：
  - [x] 本地 `127.0.0.1`/`localhost` 页面 observe 返回 `scope=local`、`trustedForEvaluate=true`。
  - [x] 普通 `https://example.com` 页面 observe 返回 `scope=remote`、`trustedForEvaluate=false`。
  - [x] 空白/未知 URL 返回 `scope=unknown`、`trustedForEvaluate=false`。
  - [x] observe 仍不执行动作、不改变浏览器模式。
  - [x] 单测、构建通过；如涉及真机，仍只用 OnePlus 8T。
- 依赖：A13。

### A15 补 URL 等待和查找 target

- 问题证据：`AUTOMATION_PROTOCOL.md` 已把 `urlContains` / `urlEquals` 列为第一阶段等待条件，但当前 `BrowserAutomationTargetKind` 只有 `css`、`text`、`role`、`none`，`waitFor` 只能等 DOM 元素或文本，不能可靠等待页面跳转、query 或 hash 变化。
- 解法：在现有 action target 协议中增加 `kind=url`，只允许 `find` / `waitFor` 读取当前 `location.href` 做 contains/exact 匹配；点击、输入、滚动等动作对 URL target 返回明确不可操作错误。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"waitFor","target":{"kind":"url","value":"#ready"}}`。
  - [x] `find` / `waitFor` 对 `kind=url` 支持 `contains` 和 `exact` 匹配。
  - [x] `click` / `type` / `scroll` 对 `kind=url` 不尝试 DOM 操作，返回 `target_not_actionable`。
  - [x] `/browser-automation/capabilities` 的 targets 声明包含 `url`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A14。

### A16 补页面状态等待 target

- 问题证据：`AUTOMATION_PROTOCOL.md` 已把 `domReady` 和 `idleMs` 列为第一阶段等待条件，但当前 action 协议没有页面状态 target；外部智能体只能等 DOM 文本、role 或 URL，打开页面后仍可能需要写固定 sleep。
- 解法：在现有 action target 协议中增加 `kind=state`，只允许 `find` / `waitFor` 读取页面状态；支持 `domReady`、`complete` 和 `idle`，其中 `idle` 用 WebView 内页面 mutation 时间判断短暂稳定。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"waitFor","target":{"kind":"state","value":"domReady"}}`。
  - [x] `find` / `waitFor` 对 `kind=state` 支持 `domReady` 和 `complete`。
  - [x] `waitFor state=idle` 支持通过 action `value` 或 `idle:<ms>` 指定稳定毫秒数。
  - [x] `click` / `type` / `scroll` 对 `kind=state` 返回 `target_not_actionable`。
  - [x] `/browser-automation/capabilities` 的 targets 声明包含 `state`。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A15。

### A17 补键盘 press 动作

- 问题证据：A16 后自动浏览器已能观察页面、语义定位、点击、输入和等待，但真实智能体常需要按 Enter 提交输入、按 Escape 关闭浮层、按 Tab 或方向键推进焦点；当前 action 协议没有键盘按键动作，只能通过点击按钮绕开。
- 解法：在现有 action 协议中新增 `press`，对受控 WebView 内目标元素或当前 activeElement 派发合成 KeyboardEvent；第一阶段支持 Enter、Escape、Tab、Space、Backspace、Delete、方向键和单字符，不做 Android 全局按键注入。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"press","target":{"kind":"role","value":"textbox","name":"Name"},"value":"Enter"}`。
  - [x] `press` 能使用现有 css/text/role/role+name DOM target；`target.kind=none` 时使用当前 activeElement 或页面 body。
  - [x] `press` 对 `kind=url` / `kind=state` 返回 `target_not_actionable`，不尝试 DOM 操作。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `press`。
  - [x] 本地测试页真机能用 `type -> press Enter -> waitFor` 完成提交。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A16。

### A18 补表单 select 动作

- 问题证据：A17 后自动浏览器已能输入、按键和点击，但本地 Web UI/设置页常见的 `<select>` 下拉选择还只能靠 `evaluate` 或点击浏览器原生菜单绕开，不适合作为通用智能体动作。
- 解法：在现有 action 协议中新增 `select`，只操作受控 WebView 内 HTML `<select>` 元素；可按 option 的 `value`、可见文本或 index 选择，并派发 `input` / `change` 事件。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"select","target":{"kind":"role","value":"combobox","name":"Tone"},"value":"formal"}`。
  - [x] `select` 能使用现有 css/text/role/role+name DOM target 定位 `<select>`。
  - [x] `select` 支持按 option value、option 文本和 `index:<n>` 选择。
  - [x] 非 `<select>` 目标返回 `target_not_selectable`；`kind=url` / `kind=state` 返回 `target_not_actionable`。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `select`；observe 对 combobox 建议 `select`。
  - [x] 本地测试页真机能用 `type -> select -> click/press -> waitFor` 完成提交。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A17。

### A19 补状态控件 check 动作

- 问题证据：A18 后自动浏览器已能处理文本、按键和 `<select>`，但 checkbox、radio、switch 这类状态控件仍只能靠 `click` 翻转。对智能体来说，“点击一次”不是“确保已选中”，会导致重复执行、恢复执行或页面初始状态变化时结果不稳定。
- 解法：在现有 action 协议中新增 `check`，复用 css/text/role/role+name DOM target，把 checkbox、radio 和 switch-like 控件设置到明确状态；支持 `true` / `false` / `toggle`，并派发 `input` / `change` 事件。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"check","target":{"kind":"role","value":"checkbox","name":"Subscribe updates"},"value":"true"}`。
  - [x] `check` 能使用现有 css/text/role/role+name DOM target 定位状态控件。
  - [x] checkbox 支持 true/false/toggle；radio 支持 true，false 返回明确不可操作错误。
  - [x] 非状态控件返回 `target_not_checkable`；`kind=url` / `kind=state` 返回 `target_not_actionable`。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `check`；observe 对 checkbox/radio/switch 建议 `check`。
  - [x] 本地测试页真机能用 `type -> check -> click/press -> waitFor` 完成提交。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A18。

### A20 补截图 artifact URL 与 observe 恢复入口

- 问题证据：A13 已有 `/browser-automation/artifact?path=...` 下载入口，A5/A13 的 screenshot action 会返回 `artifactPath`；但外部智能体需要自己拼 URL，`observe.recentAction` 也没有 artifact 字段。断线或跨回合恢复时，智能体难从轻量观察入口直接找到上一张截图证据。
- 解法：在现有 `BrowserAutomationActionResult.toJson()` 中为 artifact 结果增加相对 `artifactUrl`；在 `BrowserAutomationObservation` 的 `recentAction` 和 `recentRun` 摘要中暴露 artifact 路径/URL，不新建文件出口，也不放宽 artifact resolver。
- 验收标准：
  - [x] screenshot action result JSON 同时包含 `artifactPath` 和可直接请求的相对 `artifactUrl`。
  - [x] `/browser-automation/session`、`/browser-automation/actions`、`/browser-automation/runs` 中的 action/run result 继承同一 artifact URL 表达。
  - [x] `/browser-automation/observe` 的 `recentAction` 对 screenshot 返回 `artifactPath` / `artifactUrl` / `snapshotId`；`recentRun` 能给出最近 artifact 摘要。
  - [x] artifact URL 仍只能走 `/browser-automation/artifact` 白名单，不开放任意文件读取。
  - [x] OnePlus 8T 上执行 screenshot 后，使用返回的 `artifactUrl` 可下载 PNG，文件头为 PNG。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A19。

### A21 补 click 的 pointer/mouse 事件序列

- 问题证据：当前 `click` action 只调用 `element.click()`。很多现代 Web UI 会在 `pointerdown`、`mousedown` 或 `pointerup` 阶段打开菜单、建立 pressed 状态或准备后续 click；只发 click 容易让真实页面交互不完整。
- 解法：在现有 `click` action 中，滚动并聚焦目标后先派发受控的 pointer/mouse down/up 事件序列，再调用 `element.click()` 保留浏览器默认 activation 行为；不做 Android 全局触摸注入。
- 验收标准：
  - [x] `click` action 对 DOM target 派发 pointerdown/mousedown/pointerup/mouseup 后再触发 click activation。
  - [x] 事件坐标来自目标元素中心点，并保留 bubbles/cancelable/composed。
  - [x] 浏览器不支持 `PointerEvent` 时仍至少派发 mouse down/up，不报错。
  - [x] 本地测试页新增 pointer-gated 按钮，只有收到 down 事件后 click 才成功。
  - [x] OnePlus 8T 上 `click role=button name="Pointer gated click"` 能触发 `Pointer sequence clicked`。
  - [x] 既有普通 click、check/select/type 等动作不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A20。

### A22 补 hover 动作

- 问题证据：A21 后 `click` 已能补齐 pointer/mouse down/up，但真实 Web UI 和智能体浏览器任务里常见的菜单、tooltip、popover 会在 hover、`pointerover`、`mouseenter` 或 `mousemove` 阶段展开。当前 action 协议没有 `hover`，外部智能体只能用 click 或 evaluate 绕开，不利于通用自动浏览器能力。
- 解法：在现有 action 协议中新增 `hover`，定位受控 WebView 内 DOM target 后滚动到目标中心，派发 `pointerover` / `pointerenter` / `mouseover` / `mouseenter` / `pointermove` / `mousemove`。不做 Android 全局鼠标、触摸或 Accessibility 注入。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"hover","target":{"kind":"role","value":"button","name":"Hover reveal menu"}}`。
  - [x] `hover` 复用现有 css/text/role/role+name DOM target；未找到或不可见时返回现有结构化错误。
  - [x] `hover` 对 `kind=url` / `kind=state` 返回 `target_not_actionable`，不尝试 DOM 操作。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `hover`；observe 对可点击语义节点建议 `hover`。
  - [x] 本地测试页新增 hover-gated 控件，只有收到 hover/pointer/mouse over/move 后才显示状态。
  - [x] OnePlus 8T 上 `hover role=button name="Hover reveal menu"` 能触发 `Hover menu revealed`。
  - [x] 既有 click、check/select/type/press 等动作不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A21。

### A23 补受控导航动作

- 问题证据：A22 后自动浏览器已能观察、点击、输入、表单、hover、截图和等待，但真实智能体循环还需要在同一 WebView session 内执行 `reload`、`back`、`forward`。当前只能通过打开新页面或 evaluate 操作 history，既不清晰，也不利于 run 恢复。
- 解法：在现有 action 协议中新增 `navigate`，第一阶段只支持 `value=reload|back|forward`，并且要求 `target.kind=none`。不支持任意 URL 跳转；打开 URL 继续走 `open-run` / `open-web` 和 `BrowserHandoffPolicy`。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"navigate","value":"back"}`、`{"type":"navigate","value":"forward"}`、`{"type":"navigate","value":"reload"}`。
  - [x] `navigate` 只接受 `target.kind=none`；对 css/text/role/url/state target 返回 `target_not_actionable`。
  - [x] 不支持任意 URL 值，未知 value 返回明确错误，不改变页面。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `navigate`。
  - [x] 本地测试页新增 hash navigation 证据，能构造 back/forward 历史。
  - [x] OnePlus 8T 上可用 `click link -> click link -> navigate back -> waitFor url -> navigate forward -> waitFor url -> navigate reload` 跑通。
  - [x] 既有 click、hover、check/select/type/press 等动作不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A22。

### A24 observe 暴露能力摘要

- 问题证据：A23 后自动浏览器 action 数量已经较多，`/browser-automation/capabilities` 有完整能力清单，而 `/browser-automation/observe` 只返回页面、可交互元素和最近结果。智能体如果只按“观察 -> 决策 -> 动作”循环调用 observe，需要额外请求 capabilities 或记忆协议，容易在新增 action 后漂移。
- 解法：新增共享 `BrowserAutomationCapabilities`，由 capabilities endpoint 和 observe 共同使用。observe 返回紧凑 `capabilities` 摘要，包含 actions、targets、runs、endpoints、authBoundary 和 evaluate 边界；不执行动作、不刷新页面、不暴露敏感数据。
- 验收标准：
  - [x] `/browser-automation/observe` 返回 `capabilities.actions`，包含 `click`、`hover`、`navigate`、`screenshot`。
  - [x] `/browser-automation/observe` 返回 `capabilities.targets`，包含 `role+name`、`url`、`state`。
  - [x] `/browser-automation/observe` 返回 `capabilities.endpoints`，包含 `/browser-automation/run`、`/browser-automation/open-run`、`/browser-automation/artifact`。
  - [x] `/browser-automation/capabilities` 与 observe 共享同一份 capabilities 来源，不再各自硬编码 action/target/endpoint 列表。
  - [x] observe 仍只读，不改变 session、页面、运行状态或默认模式门禁。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A23。

### A25 补 doubleClick 动作

- 问题证据：A24 后智能体可以从 observe 获得能力清单并执行常见单击、输入、表单、hover 和导航动作，但真实 Web UI 仍会把打开、编辑、选中、进入详情等交互绑定在 `dblclick` 上。当前协议没有双击动作，外部智能体只能用两次 click 或 evaluate 绕开，行为不可审计且不一定触发 `dblclick` 监听器。
- 解法：在现有 action 协议中新增 `doubleClick`，定位受控 WebView 内 DOM target 后滚动到目标中心，执行两轮 pointer/mouse/click activation，并派发 `dblclick` 事件。不做 Android 全局触摸、坐标注入或系统浏览器控制。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"doubleClick","target":{"kind":"role","value":"button","name":"Double click open"}}`。
  - [x] `doubleClick` 复用现有 css/text/role/role+name DOM target；未找到或不可见时返回现有结构化错误。
  - [x] `doubleClick` 对 `kind=url` / `kind=state` 返回 `target_not_actionable`，不尝试 DOM 操作。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `doubleClick`；observe 对可点击语义节点建议 `doubleClick`。
  - [x] 本地测试页新增 double-click-gated 控件，只有收到 `dblclick` 后才显示状态。
  - [x] OnePlus 8T 上 `doubleClick role=button name="Double click open"` 能触发 `Double click opened`。
  - [x] 既有 click、hover、check/select/type/press/navigate 等动作不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A24。

### A26 补同源 iframe 观察和动作支持

- 问题证据：A25 后自动浏览器已经能执行较完整的单页 DOM 动作，但真实 Web UI、嵌入式工具、支付/账号组件和文档预览经常把交互放进 iframe。当前 action 和 snapshot 只扫描顶层 document，同源 iframe 内的按钮/输入/status 不会进入 observe，也不能被 role/name target 操作；外部智能体只能用 evaluate 绕开或误判页面没有目标。
- 解法：在现有 WebView 内部 JS 中递归进入同源 iframe 的 `contentDocument`，把同源 frame 内的可见文本、元素和 accessibility 节点纳入 snapshot / queryElements；节点增加轻量 frame 标记。跨源或不可访问 iframe 只记录 frame 摘要和 `inaccessible` 边界，不尝试读取 DOM、不绕过浏览器同源策略。
- 验收标准：
  - [x] snapshot / observe 能看到同源 iframe 内的 `button`、`textbox`、`status` 等语义节点，并带有 frame 来源标记。
  - [x] `find/click/type/waitFor/scroll/doubleClick/hover` 等 DOM target 能复用现有 css/text/role/role+name 选择器操作同源 iframe 内元素。
  - [x] 同源 iframe 内 action 成功后，snapshot 更新能反映 iframe 内 DOM 状态变化。
  - [x] 跨源或不可访问 iframe 不读取内部文本、密码、cookie、token，也不把内部目标伪装成可操作元素。
  - [x] 本地测试页新增同源 iframe 验证页，OnePlus 8T 上能执行 `type -> click -> waitFor` 操作 iframe 内控件。
  - [x] 既有顶层页面动作、observe、capabilities 和默认模式门禁不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A25。

### A27 补 open Shadow DOM 观察和动作支持

- 问题证据：A26 后自动浏览器已能进入同源 iframe，但现代 Web Components 常把真实控件放进 open shadow root。当前 snapshot / queryElements 只走 document/iframe document，open shadow 内的按钮、输入框和 status 不会进入 observe，也不能被 role/name target 操作；外部智能体只能用 evaluate 绕开。
- 解法：在现有 WebView JS 中递归遍历同源 document 和 open `shadowRoot`，把 open shadow 内的元素、可见文本和 accessibility 节点纳入 snapshot / action selector；节点增加轻量 `shadowPath` / `shadowHost` 标记。closed shadow root 保持浏览器边界，不读取内部 DOM。
- 验收标准：
  - [x] snapshot / observe 能看到 open shadow root 内的 `textbox`、`button`、`status` 等语义节点，并带有 shadow 来源标记。
  - [x] `find/click/type/waitFor/scroll/doubleClick/hover` 等 DOM target 能复用现有 css/text/role/role+name 选择器操作 open shadow 内元素。
  - [x] open shadow 内 action 成功后，snapshot 更新能反映 shadow 内 DOM 状态变化。
  - [x] closed shadow root 内部文本、密码、cookie、token 不进入 snapshot / observe，也不被伪装成可操作元素。
  - [x] 本地测试页新增 open shadow 验证组件，OnePlus 8T 上能执行 `type -> click -> waitFor` 操作 shadow 内控件。
  - [x] 既有顶层页面、同源 iframe、observe、capabilities 和默认模式门禁不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A26。

### A28 补自动浏览器 session 列表入口

- 问题证据：A10/A12 后外部智能体可以通过 `open-run` 打开页面并用 `observe` 查询单个 session，但多页面、断线恢复或跨回合继续时仍要依赖 latest session 或记住 sessionId。真实智能体浏览器需要先发现当前有哪些自动浏览器 session，再选择目标页面。
- 解法：复用现有 `BrowserAutomationSessionStore`，新增只读 `GET /browser-automation/sessions`，按 `updatedAt` 最新优先返回 session 摘要；支持 `limit`、`includeClosed` 和 `instanceId` 过滤；默认不返回 snapshot/action/run 重型详情，也不新建平行状态。
- 验收标准：
  - [x] `/browser-automation/sessions` 返回 `ok`、`count`、`sessions`、`latestSessionId` 和 `source`。
  - [x] session 列表默认排除 `Closed`，并按 `updatedAt` 最新优先排序。
  - [x] 支持 `limit` 上限和 `includeClosed=true`；支持 `instanceId=<id>` 过滤。
  - [x] 列表中的 URL、错误和文本字段不包含 token/password/authorization 原文。
  - [x] `/browser-automation/capabilities` 和 `/browser-automation/observe.capabilities` 暴露 `/browser-automation/sessions`。
  - [x] OnePlus 8T 上打开至少两个自动浏览器页面后，sessions endpoint 能列出它们，并能用返回的 sessionId 调用 observe。
  - [x] 默认 `webview_system_auth` 模式下 sessions endpoint 仍只读可查，不打开页面、不执行动作；open-run 门禁不回退。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A27。

### A29 补输入框 clear 动作

- 问题证据：当前 `type` action 已经把目标输入控件的值覆盖为 `value`，但协议没有显式“清空当前输入”的动作。真实智能体任务经常需要先清空搜索框、地址栏式输入、textarea 或 contenteditable，再决定是否填新值；靠多次 Backspace、选择文本或把 `type` 当清空动作都不够清晰。
- 解法：在现有 action 协议中新增 `clear`，复用 css/text/role/role+name DOM target，只清空受控 WebView 内 HTML input、textarea 和 contenteditable；派发 `input` / `change` 事件并返回结构化结果。`type` 保持现有覆盖填入行为，不改旧脚本语义。
- 验收标准：
  - [x] action JSON 可表达 `{"type":"clear","target":{"kind":"role","value":"textbox","name":"Name"}}`。
  - [x] `clear` 能清空 HTML input、textarea 和 contenteditable，并派发 `input` / `change` 事件。
  - [x] 非可编辑目标返回 `target_not_editable`；`kind=url` / `kind=state` 返回 `target_not_actionable`。
  - [x] `/browser-automation/capabilities` 的 actions 声明包含 `clear`；observe 对 textbox 建议 `clear`。
  - [x] 本地测试页在 OnePlus 8T 上能执行 `type -> clear -> evaluate/waitFor -> type -> click -> waitFor`，证明清空和后续填入均可恢复。
  - [x] 既有 `type` 覆盖填入、顶层页面、同源 iframe、open shadow、sessions 和默认模式门禁不回退；单测、构建和 OnePlus 8T 真机验证通过。
- 依赖：A28。

### A30 observe 补同名元素 target index

- 问题证据：A12/A24 后 observe 已返回 `interactive[].suggestedTarget`，A8 的 action target 也已有 `index` 字段；但 observe 目前只给出 `kind=role,value=<role>,name=<name>`，没有把同名同 role 元素的去歧义 index 写进 target。真实智能体遇到列表、重复按钮、重复表单行时，只靠 role/name 容易误点第一个元素。
- 解法：在 `BrowserAutomationObservation` 生成 interactive 列表时，按 `role + name` 为可交互节点计算重复序号，并写入 `suggestedTarget.index`；首个仍为 0，后续为 1、2。复用现有 `BrowserAutomationTarget.index` 和 action selector 的 nth 逻辑，不新增平行 selector。
- 验收标准：
  - [x] observe 的每个 `suggestedTarget` 都包含非负 `index` 字段。
  - [x] 同 role/name 的重复元素在 observe 中返回稳定的 `index=0/1/...`。
  - [x] action JSON 可直接使用 observe 返回的 `suggestedTarget` 点击第二个同名按钮。
  - [x] 本地测试页新增重复按钮探针；OnePlus 8T 上能用 `role=button,name=Duplicate action,index=1` 触发第二个按钮结果。
  - [x] 不改变现有 role/name 未传 index 时默认选择第一个的兼容行为。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过；默认模式门禁不回退。
- 依赖：A29。

### A31 补 disabled/readonly 动作可执行性守卫

- 问题证据：A7/A12 的 observe 已能暴露节点 `enabled` 状态，并且 disabled 可点击节点只建议 `find`；但动作层仍可能对 disabled button/input 或 readonly input 直接执行 JS、改值或返回成功。这会让智能体误以为真实用户操作成功，违背自动浏览器的 actionability 语义。
- 解法：在 `BrowserAutomationActionScript` 中增加 DOM actionability 守卫：`click`、`doubleClick`、`type`、`clear`、`select`、`check`、目标化 `press` 遇到 HTML disabled 或 `aria-disabled=true` 返回 `target_disabled`；`type` / `clear` 遇到 readonly 或 `aria-readonly=true` 返回 `target_readonly`。`hover`、`find`、`waitFor` 和只读 observe 不受影响。
- 验收标准：
  - [x] disabled HTML button 的 `click` 返回 `target_disabled`，不报告成功。
  - [x] disabled input 的 `type` / `clear` 返回 `target_disabled`。
  - [x] readonly input 的 `type` / `clear` 返回 `target_readonly`。
  - [x] observe 对 disabled button 仍只建议 `find`。
  - [x] 普通 enabled 按钮、输入框、同源 iframe、open shadow 和 A30 `index` 选择不回退。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过；默认模式门禁不回退。
- 依赖：A30。

### A32 补 run/open-run 迟到 action 结果校准

- 问题证据：A31 真机验证中 `runId=a31-open-actionability` 的 open-run 汇总层先记录 `TimedOut/request_timeout`，但同一个 action `act_40f2affee282460f9e5235b740818f39` 随后在 `BrowserAutomationSessionStore` 中写入 `Succeeded/state domReady readyState=complete`。这会让 `/browser-automation/runs`、`/browser-automation/session.runs` 和 observe 的 `recentRun` 与 action history 互相矛盾。
- 解法：继续复用 `BrowserAutomationSessionStore`，不新建平行任务状态。run/open-run 执行链遇到 `request_timeout` action 时短暂等待同 actionId 的最终落库结果；run 查询和 session/observe 摘要读取时也按 actionId 用已落库 action result 校准旧 run 汇总。
- 验收标准：
  - [x] 同 actionId 的迟到成功结果能把已保存的 `request_timeout` run 查询校准为 `Succeeded`。
  - [x] 没有迟到落库结果的真实 timeout 仍保持 `TimedOut/request_timeout`。
  - [x] open-run 响应在短暂迟到窗口内优先返回最终 action 结果，不先制造矛盾 run。
  - [x] `/browser-automation/runs`、`/browser-automation/session` 和 `/browser-automation/observe.recentRun` 使用同一套校准后的 run 结果。
  - [x] 单测、构建和 OnePlus 8T 真机验证通过；默认模式门禁不回退。
- 依赖：A31。
