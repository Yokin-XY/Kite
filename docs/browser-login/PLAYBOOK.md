# Kite 浏览器登录回跳执行手册

> 本文件是浏览器登录任务线的唯一事实来源。后续浏览器分支会话开始前必须先读本文件、`PROGRESS.md` 和 `DECISIONS.md`。

## 0. 开机自检

每次继续本任务前必须先做：

1. 读 `docs/browser-login/PLAYBOOK.md` 全文。
2. 读 `docs/browser-login/PROGRESS.md`。
3. 读 `docs/browser-login/DECISIONS.md`。
4. 检查当前目录是否为浏览器线物理副本 `D:\xm\Kite-browser-login`。
5. 检查 ADB 目标是否为 OnePlus 8T `3f8bbaad`。

每开始一个任务前，必须在 `PROGRESS.md` 写三问自检：

1. 目标是什么。
2. 完成标准是什么。
3. 前置任务是否完成。

## 0.1 自动续跑方式

本任务线采用“完成驱动 + 外部唤醒兜底”的续跑方式：

1. 每次会话被唤起后，先执行开机自检，再读取 `PROGRESS.md` 找到第一个前置条件已满足且未完成的任务。
2. 当前回合内，如果一个任务完成验收，立即回写 `PROGRESS.md` / `DECISIONS.md`，然后继续触发下一个就绪任务，不需要用户重新描述任务。
3. 如果平台或用户侧只能按时间唤醒，推荐用每小时一次作为兜底节奏；被唤醒后仍按第 1 条恢复，不按时间猜测进度。
4. Codex 不能在会话休眠或没有外部触发时自己发起下一回合；跨回合自动继续必须依赖平台目标续跑、用户消息或本机外部调度器唤起。
5. 如果遇到需要用户决策、账号授权、真实登录凭据或外部不可用环境，必须把阻塞点写入 `PROGRESS.md`，不能假装已完成后继续。
6. 如果外部调度器支持“任务完成后立即唤醒下一回合”，优先用完成事件触发；每小时唤醒只保留为兜底和阻塞期校准。
7. 本线提供 `scripts/browser-login-continuation-gate.ps1` 作为跨回合唤醒门槛检查：退出码 `0` 表示至少一个账号已进入后置验证窗口，退出码 `2` 表示仍需真实账号授权，退出码 `1` 表示环境或输出需要检查。
8. 如果只具备本机定时能力，可用 `scripts/register-browser-login-continuation-gate.ps1` 注册 Windows 计划任务；默认每 60 分钟兜底一次，可通过 `-Minutes` 改成其他分钟数，当前下限为 5 分钟，避免高频 ADB/PRoot 轮询。
9. 计划任务实际调用 `scripts/browser-login-continuation-runner.ps1`，先跑 gate，若发现 `readyTargets` 则在同一次运行里立即接 `scripts/browser-login-post-auth-verify.ps1` 补证；这就是本机侧的“完成一个，自动触发下一阶段”。
10. runner 每次写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\runner-status.json` 汇总 `exitCode`、`readyTargets`、`waitingTargets`、`verifiedTargets` 和 `postAuthAttempted`，并在同一次运行末尾刷新 `post-auth-evidence-report.md`，后续会话优先读这些文件判断下一步。
11. 账号状态进入后置验证窗口后，`scripts/browser-login-post-auth-verify.ps1` 负责补非敏感完成证据；Codex 使用 `codex doctor --json` 的官方脱敏报告，Claude 使用 `claude auth status --json`，不启动交互会话，不调用模型生成内容。
12. `scripts/browser-login-evidence-report.ps1` 可手动重跑，用于重新生成脱敏 Markdown 摘要并更新 `PROGRESS.md` / `COMPATIBILITY_MATRIX.md`；该报告不是登录事实来源。
13. 账号验证不绑定某个具体 provider 或验证码/MFA 场景；`docs/browser-login/ACCOUNT_VERIFICATION_NODES.md` 定义 N0-N5 通用节点。没有用户真实账号授权时，只能声明到达账号挑战或回跳入口，不能伪造已登录状态。
14. 人工 Google / OpenAI / Claude 账号验证前，按 `docs/browser-login/LOGIN_TEST_STRATEGY.md` 先跑无账号和低风险测试，把失败面收窄到浏览器环境、回跳机制、OAuth client 配置或账号挑战中的具体一类。测试组合不能只依赖单一页面结果；应按 T0-T6 覆盖官方合规复核、白盒单测、真机 smoke、长期 smoke watch、人工 account watch、完成审计和人工准备度汇总。
15. 人工准备在手机浏览器里完成 Codex/Claude 账号授权时，优先运行 `scripts/browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`。它会先做 smoke/readiness 门槛，再通过现有 resource open automation 拉起真实 Codex/Claude 终端登录入口，最后可选接 account watch；自动短验证可加 `-WatchMaxAttempts 1 -WatchPollSeconds 0`。它不输入账号、不读取 token、不伪造 callback。只想陪跑已有登录流程时，可直接运行 `scripts/browser-login-account-watch.ps1`，推荐带 `-RunReadinessFirst`；如果同时带 `-RunSmokeFirst`，watch 会先刷新一次无账号 smoke，再运行 `browser-login-manual-readiness.ps1 -RefreshState -RunCompletionAudit`，确保准备度读取的是新 smoke 证据。只有准备度为 `ready_for_manual_account`、`partial_account_verified_continue_watch` 或 `account_verified_run_completion_audit` 才进入 runner/gate/post-auth 轮询；否则写入 `manual_readiness_failed` 并提示先检查准备度报告。
16. `scripts/browser-login-smoke-test.ps1` 是人工账号验证前的 OnePlus 8T 无账号 smoke test：它会启动 Kite、恢复端口转发、先用设备侧 `curl` 探测 `accounts.google.com`、`auth.openai.com`、`claude.ai` 的 HTTPS 网络路径可达，单个主机允许少量重试并在 JSON 记录 attempts，解析 `https://accounts.google.com/` 的系统 `ACTION_VIEW` 默认浏览器 handler，记录 Custom Tabs service 能力诊断，打开普通 `http://127.0.0.1:8791/status` 页面并确认仍留在 Kite WebView、打开 Google OAuthPlayground URL、确认本地 `/open-web` 接收耗时不高于默认 `1500ms` 阈值、确认 Google/OpenAI/Claude OAuth 形态从请求发出到外部浏览器前台的总耗时不高于默认 `5000ms` 阈值、确认 OAuth 前台离开 Kite WebView 且匹配外部浏览器 handler、检查 UI dump 没有 `disallowed_useragent`，并进一步确认外部 provider 页面没有 `redirect_uri_mismatch`、`invalid_client`、`unsupported_browser`、`Error 400/403` 等阻塞性错误信号；随后确认第三方 HTTPS redirect 不新增假 AppRedirect session，并用 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 验证多站点外部浏览器分流且不新增假 browser auth session；同时用本机生成的假 code/token 触发 `kite-auth://callback`，验证 AppRedirect pending、同 `state` 回跳交付、SharedPreferences 脱敏落盘，并扫描 app 私有 `files` / `shared_prefs` 文本类文件确认本轮 OAuth 临时值没有原文落盘。AppRedirect 的 `Returned` 只表示 App 已收到 redirect，smoke 必须继续等待 `Delivered` 或 `Failed`，不能把中间态当作终态。当前 smoke JSON `schemaVersion` 必须不低于 `10`，完成审计必须检查授权主机设备侧 HTTPS 可达且有重试 attempts 证据、外部浏览器 handler/fallback、provider 页面无阻塞错误、OpenAI/Claude OAuth 形态外部浏览器分流、普通 localhost WebView、响应耗时、前台切换耗时、AppRedirect/脱敏字段和 app 私有文件临时值扫描，不能只看旧格式 `status=passed`。
17. `scripts/browser-login-smoke-watch.ps1` 用于长期或多轮无账号 smoke 趋势验证。它反复调用 `browser-login-smoke-test.ps1`，汇总成功率、`/open-web` p95、外部浏览器前台切换 p95、HTTPS handler 稳定性、provider 页面阻塞错误趋势、假 auth session 泄漏和 OAuth 临时值原文落盘风险，输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-watch.json` / `.md`。运行期间还会逐轮刷新 `browser-login-smoke-watch-progress.json`，记录总轮数、已完成轮数、剩余轮数、失败数和最后一轮状态，便于长跑中途判断进度。该脚本不输入账号、不读取 token、不伪造 callback；通过只证明 Kite 可控机制稳定，不替代 Codex/Claude 真实账号完成证据。
18. `scripts/browser-login-long-run-cycle.ps1` 用于账号等待期间的单次长期巡检循环：先运行 runner 判断账号是否 ready；如果仍在等待账号，则运行 smoke watch，再刷新 manual readiness，并写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-long-run-cycle.json` / `.md`。运行期间还会刷新 `browser-login-long-run-cycle-progress.json`，标明当前处于 `runner`、`smoke_watch`、`manual_readiness`、`completion_audit` 或 `finished` 阶段。少于 3 轮的短 cycle 会把 smoke watch 写到 `browser-login-long-run-cycle-smoke\` 子目录，避免污染 completion audit 要求的主 3 轮趋势证据；3 轮及以上可刷新主 smoke watch 证据。`scripts/register-browser-login-long-run-cycle.ps1` 只提供计划任务注册入口，不默认替换现有 runner 任务。只想看当前状态时运行 `scripts/browser-login-status-summary.ps1 -Serial 3f8bbaad`，它只读计划任务、runner、latest smoke、smoke watch、manual readiness、completion audit、provider preflight 和 progress 文件，输出 `browser-login-status-summary.json` / `.md`，不启动真机测试。status summary 还会按计划任务动作里的 `SmokeIterations` / `SmokeIntervalSeconds` 估算无 progress 长跑的宽限时间，输出 `noProgressOverdueAt`、`noProgressSecondsRemaining`、`noProgressMinutesRemaining`，并输出 latest smoke 与当前长跑启动时间的关系；长跑有 progress JSON 时，会额外输出 `longRunProgress.phase/status/ageMinutes`、`smokeWatchProgress.completedIterations/remainingIterations/nextExpectedAt/nextExpectedOverdue` 和 `providerPreflight.exitCode`，便于续跑判断当前轮次和下一轮预计时间。只有运行中、没有 progress JSON 且超过宽限时，才标记 `running_without_progress_overdue` / `long_run_running_without_progress_overdue`，提示先检查长跑而不是并发启动新测试。`running_without_progress_latest_smoke_after_current_run_start` 只说明长跑窗口内出现过新 smoke 样本，不单独证明该样本一定由当前长跑写入。
19. `scripts/browser-login-provider-preflight.ps1` 用于人工 Google / OpenAI / Claude provider 验证前的综合预检：读取或可选刷新 smoke、smoke watch、runner、manual readiness 和 completion audit 状态，把官方合规、外部浏览器环境、provider 页面阻塞错误信号、redirect 类型、CLI callback/fallback、敏感信息边界、性能和后置账号状态归到 `browser_environment`、`provider_configuration`、`account_challenge`、`cli_callback_or_fallback`、`sensitive_boundary`、`performance` 或 `post_auth` 桶。退出码 `2` 表示环境 ready 但仍等真实账号，退出码 `1` 表示先修环境/配置，退出码 `0` 表示账号已进入完成审计窗口；脚本会把同一语义写入 JSON 顶层 `exitCode`、Markdown 和控制台。`smokeCheckedAt` / `smokeWatchCheckedAt` 必须以 ISO 8601 写入，避免后续续跑被本机区域时间格式干扰。该脚本不输入账号、不读取 token、不伪造 callback。
20. `scripts/browser-login-manual-readiness.ps1` 用于人工账号验证前的只读准备度汇总。它读取最新 smoke、smoke watch、runner、account watch、manual account start 和 completion audit 状态，输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\manual-account-readiness.json` / `.md`，给出 `ready_for_manual_account`、`not_ready`、`partial_account_verified_continue_watch`、`account_verified_run_completion_audit` 或 `complete`。该脚本不输入账号、不读取 token、不伪造 callback；`ready_for_manual_account` 只表示 Kite 可控链路准备好进入真人账号挑战。completion audit 默认必须在 24 小时内，否则准备度为 `not_ready` 并提示先刷新审计；ready 状态下推荐下一步为 `browser-login-manual-account-start.ps1 -StartWatch`，避免准备度通过后还要人工拼接资源启动和账号陪跑命令。manual account start 没有状态文件时，只要 runner/readiness 仍健康就视为可重新生成；最近状态为 `launch_failed` 或 `watch_needs_inspection` 时，readiness 必须提示先检查启动入口。
21. 修改 gate、runner、account watch、manual account start、post-auth、evidence report、smoke watch、long-run cycle、provider preflight 或 manual readiness 后，运行 `scripts/test-browser-login-continuation.ps1` 做 mock 分支回归，覆盖等待态、ready 态、stale post-auth 状态、人工 watch 等待/验证分支、manual account start 的 plan/launch/readiness-fail/watch 分支、manual readiness 读取 manual account start 状态、`RunSmokeFirst` 先于 readiness 的顺序、readiness 预检通过/失败分支、smoke watch 聚合分支、long-run cycle 等待账号分支、provider preflight 只剩账号缺口/仍在 WebView 风险分支和人工准备度分支。
22. 宣称浏览器线完成前，必须运行 `scripts/browser-login-completion-audit.ps1 -RefreshState`；需要刷新真机 smoke 证据时加 `-RunSmokeTest`。该审计会同时检查文档、实现文件、浏览器 handoff 单测、`assembleDebug`、OnePlus 8T 当前 ADB 在线状态、设备证据、最近 24 小时 smoke test、最近 24 小时 smoke watch 趋势、续跑自测试、runner 自动 evidence report、manual account start 最近状态、account watch 最近状态、账号 gate 计划任务、小时级 long-run cycle 计划任务和真实账号完成证据。smoke 审计项必须验证 `schemaVersion>=10`、授权主机设备侧 HTTPS 可达且有重试 attempts 证据、HTTPS `ACTION_VIEW` 外部浏览器 handler、外部 provider 页面无阻塞性错误信号、OpenAI/Codex 与 Claude OAuth 形态 URL 外部浏览器分流且不建假 auth session、普通 localhost WebView 不外跳且不建 auth session、关键 item id、本地 `/open-web` 响应耗时、真实 handoff 前台切换耗时、真实 handoff 前台匹配外部浏览器 handler、`appRedirectStatus=Delivered`、脱敏 `returnedUrl`、`rawSecretHitCount=0` 和 `appPrivateRawTemporaryValueHitCount=0`。smoke watch 审计项必须验证至少 3 轮、`failureCount=0`、`/open-web` p95 和外部浏览器前台切换 p95 不超过阈值、HTTPS handler 稳定且不是 Kite、无 provider OAuth 假 session 泄漏、无 provider 页面阻塞错误趋势、无 OAuth 临时值原文落盘。manual account start 审计项在账号未全部 verified 时要求状态缺失但 runner 可读，或最近 24 小时内为 `planned`、`launched`、`watch_waiting_for_real_account_authorization`、`watch_verified`；`launch_failed` / `watch_needs_inspection` 必须先检查。account watch 审计项在账号未全部 verified 时允许缺失但 runner 可读，或 `waiting_for_real_account_authorization` / `verified`；新鲜的 `smoke_failed` / `manual_readiness_failed` / `needs_inspection` 必须先检查，陈旧状态可由当前 runner 状态兜底。long-run cycle 计划任务必须启用并调用本副本 `browser-login-long-run-cycle.ps1`、绑定 `3f8bbaad`、使用 `SmokeIterations=6` 与 `SmokeIntervalSeconds=600`，重复间隔在 15 到 60 分钟之间并持续至少 1 天。只有该脚本输出 `status=complete` 且退出码为 `0`，才允许进入完成审计。

## 1. 北极星目标

解决 Kite 内置浏览器触发网页登录后，认证流程无法稳定回到 App 或被 Google、ChatGPT、Claude Code 等站点判定为不合规浏览环境的问题。

本任务先走官方推荐路径：系统浏览器、Chrome Custom Tabs、App Links、OAuth 2.0 for Native Apps、PKCE、AppAuth 或等价标准能力。无指纹浏览器、UA/环境伪装、自动化浏览器特征等内容只能作为兼容性研究和风险记录，不作为默认实现路线。

## 2. 工作线绑定

- 物理目录：`D:\xm\Kite-browser-login`
- 建议分支：`codex/browser-login-return`
- 绑定设备：OnePlus 8T
- ADB serial：`3f8bbaad`
- 本机调试端口：`18791 -> 8791`
- 主要真实入口候选：`KiteBrowserProxy`、`KiteLocalServer`、浏览器相关 Activity/bridge、登录回跳 Intent 处理。

## 3. 红线

- 不把 Google 当前报错当成单一 provider 特判处理。
- 不靠改 User-Agent、隐藏 WebView 特征或硬编码成功结果作为正式修复。
- 不在未确认回跳协议前保存、转发或打印敏感 token。
- 不把账号邮箱、token、API key、callback code 原文写入续跑状态文件；状态文件只保存脱敏摘要。
- 不新增平行登录状态来源；登录事实必须由已有认证/会话拥有者确认。
- 不把验证码、MFA、风控挑战或账号所有权证明当成 Kite 可以绕过的技术问题；只能记录通用节点和等待用户真实授权。
- 不把“到了 Google 账号挑战页”写成“Google 账号已验证通过”；人工验证前必须区分浏览器环境成功、redirect 配置成功和账号所有权成功。
- 不用无设备 serial 的 ADB 命令。
- 不与 X11 线共用同一个物理目录、同一个 host 转发端口或同一个截图/日志输出路径。

## 4. 任务梯队

### B0 建立浏览器任务基线

- 问题证据：用户要求浏览器登录线独立于 X11 线，绑定 OnePlus 8T，并先明确目录、设备和方向。
- 解法：建立浏览器登录三件套和双线隔离说明。
- 验收标准：
  - [x] `docs/browser-login/PLAYBOOK.md` 存在。
  - [x] `docs/browser-login/PROGRESS.md` 存在。
  - [x] `docs/browser-login/DECISIONS.md` 存在。
  - [x] `docs/parallel-workstreams/README.md` 写明浏览器线目录、分支、设备和端口。
- 依赖：无。

### B1 确认当前内置浏览器和回跳真实链路

- 问题证据：用户描述“用内置浏览器选择登录，自动跳转后点击登录，Google 显示不符合要求”。
- 解法：只读检查当前浏览器代理、LocalServer、Intent、deep link、回调桥接和登录状态写入点，记录真实入口和当前报错可复现路径。
- 验收标准：
  - [x] 列出当前登录入口、跳转入口、回跳入口和状态拥有者。
  - [x] 记录 Google 报错原文、URL 参数和触发页面环境。
  - [x] 明确当前失败属于 WebView/embedded user-agent、redirect URI、client 配置、cookie/session、TLS/UA 还是其他原因。
  - [x] 不修改代码。
- 依赖：B0。

### B2 调研官方推荐和通用网站登录回跳模式

- 问题证据：用户要求先看别人怎么完成，再看网页登录后返回 App/软件需要什么环境和要求。
- 解法：优先查官方资料和成熟库文档，覆盖 Google、OAuth Native Apps、Chrome Custom Tabs、AppAuth、App Links，以及 ChatGPT/Claude Code 等常见网页登录回跳约束。
- 验收标准：
  - [x] 至少 5 个可追溯来源。
  - [x] 区分官方要求、社区经验和推断。
  - [x] 明确 embedded WebView、Custom Tabs、系统浏览器、无指纹/伪装环境各自风险。
  - [x] 输出适配 Kite 的实现路线和反路线。
- 依赖：B1。

### B3 设计 Kite 登录回跳协议

- 问题证据：Kite 需要让网页认证完成后回到 App 或对应软件，而不是只解决一个 Google 页面。
- 解法：设计 provider-agnostic 的浏览器 handoff 协议，明确启动、回跳、状态确认、失败展示和重试路径。
- 验收标准：
  - [x] 设计包含 external browser / Custom Tabs 路线。
  - [x] 设计包含 App Links 或可验证 redirect 入口。
  - [x] 设计包含 PKCE/code exchange 的安全边界。
  - [x] 设计不要求伪造浏览器环境作为主路径。
  - [x] 设计说明如何兼容内置浏览器里的非 OAuth 普通登录。
- 依赖：B2。

### B4 实现最小通用登录回跳

- 问题证据：当前内置浏览器登录无法稳定通过 provider 要求并回到 Kite。
- 解法：按 B3 设计实现最小可验证链路，优先复用现有 bridge、LocalServer 和 Activity/Intent 模式。
- 验收标准：
  - [x] OnePlus 8T 上能从 Kite 发起登录并回到正确运行实例或浏览器上下文。
  - [x] 失败时有可解释状态，不静默回到初始可点状态。
  - [x] 不新增 provider 单点特判。
  - [x] 有相关单测或集成测试保护回跳解析。
  - [x] 构建、安装、截图和 logcat 检查完成。
- 依赖：B3。

### B5 扩展多站点兼容矩阵

- 问题证据：用户明确说问题不止 Google，ChatGPT、Claude Code 或其他网页登录也可能遇到类似限制。
- 解法：建立兼容矩阵，按站点类型验证官方回跳、Custom Tabs、系统浏览器、普通网页登录和失败兜底。
- 验收标准：
  - [ ] 至少覆盖 Google、OpenAI/ChatGPT、Anthropic/Claude 相关网页登录场景。
  - [ ] 每个场景有设备、截图、日志或错误证据。
  - [x] 不把策略绕过当作默认成功路径。
  - [x] 记录仍需用户账号或外部权限的验证缺口。
  - [x] `LOGIN_TEST_STRATEGY.md` 写明人工账号验证前的高置信度测试方法、Google 验证边界和体验/性能回归项。
  - [x] `ACCOUNT_AUTH_COMPLETION_SOP.md` 写明人工 provider 验证作战清单，覆盖状态汇总、provider preflight、外部浏览器选择、脱敏证据、失败归因和完成证据。
  - [x] `browser-login-smoke-test.ps1` 能在 OnePlus 8T 上通过，并由完成审计读取最近一次结果。
  - [x] `browser-login-smoke-watch.ps1` 能对多轮 smoke 输出趋势报告，用于长时间自主跑测试时定位偶发失败、卡顿、默认浏览器变化和 session 泄漏。
  - [x] `browser-login-provider-preflight.ps1` 能把人工 provider 验证前的环境、配置、账号和性能状态汇总成可复查 JSON/Markdown。
- 依赖：B4。

### B6 浏览器运行模式切换与自动浏览器地基

- 问题证据：用户确认“系统浏览器登录”方案有效，同时要求在设置中提供两种模式切换：一种是当前 WebView 加系统浏览器登录跳转，另一种是后续自动浏览器模式。
- 解法：先建立可持久化、可测试的浏览器运行模式单一事实来源，并在设置页提供显式切换入口；自动浏览器作为实验模式先完成入口、状态和边界，不把未实现的自动化内核伪装成已可用能力。
- 验收标准：
  - [x] 设置页显示当前浏览器模式，并能在“WebView + 系统浏览器登录”和“自动浏览器”之间切换。
  - [x] 模式选择持久化到 `kite_app_settings`，重启后仍能读取。
  - [x] 默认模式保持现有 WebView + 系统浏览器登录行为，不影响本地 localhost Web UI 和 OAuth 外部浏览器 handoff。
  - [x] 自动浏览器模式不得绕回 WebView 承载 OAuth，也不得通过 UA/指纹伪装作为默认登录方案。
  - [x] 有最小单测或构建验证；用户可见入口尽量在 OnePlus 8T 上安装检查。
- 依赖：B4；B5 可以继续等待真实账号，不阻塞 B6 的设置地基。

### B7 稳定交付版认证事务收口

- 问题证据：稳定版回归曾把 Codex“打开”改成强制 `codex login`，跳过原生三选一首屏；人工账号授权后浏览器回调长时间转圈并失败。另有 `previous_process_incomplete` 历史诊断错误拦截正常启动。
- 解法：恢复 CLI 原生启动流程和已验证的透明双向 loopback relay；删除额外登录守卫与确认接口。历史进程中断只保留诊断记录，真实未捕获异常仍进入启动失败页。
- 验收标准：
  - [x] Codex open/home recipe 只启动 `codex`，真机显示官方三选一首屏。
  - [x] loopback relay 完整转发浏览器请求和 CLI 响应，不解析、不改写 OAuth 字段。
  - [x] 删除 `kite-auth-run`、owner-confirmed 接口和相关平行状态。
  - [x] `previous_process_incomplete` 不再设置阻塞失败，并能清理旧版本留下的同类待处理记录。
  - [x] 相关单测、构建、OnePlus 8T 安装、冷启动、中断恢复、截图和崩溃日志检查完成。
  - [ ] 用户在 OnePlus 8T 从原生首屏完成一次真实 Codex 账号授权，确认浏览器收到 CLI 响应并回到终端。
- 依赖：B4、B5 真实链路证据。
