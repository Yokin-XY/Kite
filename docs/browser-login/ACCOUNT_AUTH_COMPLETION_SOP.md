# 账号授权完成复验 SOP

本文件用于 B5 最后补证：OpenAI/Codex 和 Claude Code 的网页登录是否已经完成真实账号授权。

账号验证节点定义见 `docs/browser-login/ACCOUNT_VERIFICATION_NODES.md`。人工账号验证前的测试策略见 `docs/browser-login/LOGIN_TEST_STRATEGY.md`。本 SOP 不要求必须选择 Google 或其他容易触发验证码/MFA 的账号作为唯一完成路径；它要求把“浏览器环境与回跳机制证据”和“账号所有权完成证据”分开记录。

## 边界

- 只使用 OnePlus 8T `3f8bbaad`。
- 只运行 CLI 官方状态命令。
- 不读取、不复制、不打印 token、auth json、cookie 或 callback code。
- 不向 CLI loopback callback 端口注入假 `code`。
- 如果状态仍是未登录，记录为账号授权缺口，不把实现改成伪造成功。
- 如果 provider 进入验证码、MFA、风控或人工确认页，只记录已到达账号挑战节点，不尝试绕过。
- 如果 Google 人工验证前要提高通过概率，先按 `LOGIN_TEST_STRATEGY.md` 跑 G1-G4；只有 `disallowed_useragent` 不再出现、redirect 类型解释清楚、敏感信息边界通过后，才进入真人账号验证。

## 人工验证作战清单

这份清单用于用户准备在 OnePlus 8T 上做人真人账号验证前的最后确认。目标不是承诺 Google / OpenAI / Claude 账号一定放行，而是把 Kite 能控制的失败面先收窄到最小。

1. 先运行只读状态汇总：

```powershell
.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad
```

继续人工验证的最低条件：

- `status` 是等待真实账号授权或完成审计窗口，不是环境失败。
- `smokeStatus=passed`。
- `smokeProviderBlockingErrorCount=0`。
- `providerPreflightStatus=ready_for_manual_provider_auth` 且 `providerPreflightExitCode=2`，或账号已经进入 `readyTargets`。
- 如果小时级 long-run 正在跑，`longRunObservation=running_with_progress` 且 `smokeWatchProgressNextExpectedOverdue=False`；不要另开并发 smoke/watch。

2. 再运行人工 provider 预检：

```powershell
.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad
```

`ready_for_manual_provider_auth` 表示 Kite 可控链路已经准备好，剩余是账号所有权、验证码、MFA、组织策略或 provider 风控。`not_ready` 时先按 `failedBuckets` 修，不开始真人账号验证。

3. 人工打开账号页时只使用真实外部浏览器状态。默认使用系统浏览器或 Chrome / Custom Tabs 这类官方 external user-agent 路线；无痕、无指纹、UA 伪装或自动化特征隐藏只允许作为后续隔离实验，不作为提高通过率的默认路径。

4. 用户输入账号、验证码或 MFA 时，Codex 只陪跑 watch，不读取、不保存、不截图敏感内容。允许保存的证据只包括脱敏 UI 摘要，例如已经到达登录页、授权确认页、验证码/MFA 节点、paste code 提示或完成后官方状态命令。

5. 如果页面失败，按下面顺序归因：

- `disallowed_useragent` / `unsupported_browser`：浏览器环境问题，先查 OAuth 是否回到 WebView、外部浏览器 handler 是否漂移。
- `redirect_uri_mismatch` / `invalid_client` / `Error 400/403`：provider client type、redirect allowlist、consent screen 或第三方测试 URL 配置问题，不改 UA。
- 登录、继续、验证码、MFA、设备确认：账号挑战节点，等待用户真实完成，不当作 Kite bug。
- 浏览器完成但 CLI 未登录：callback / fallback 问题，先查 loopback listener、paste code / device code 提示，再考虑 relay。
- 页面打开慢或卡住：按 `/open-web` 接收耗时、外部浏览器前台切换耗时、授权主机 HTTPS probe、默认浏览器包名和设备负载分段定位。

6. 人工完成后立刻运行 account watch 或 runner 补证。最终只接受 `codex login status`、`codex doctor --json`、`claude auth status --json` 和 completion audit 的脱敏结果作为账号完成证据。

## 复验命令

在 `D:\xm\Kite-browser-login` 运行：

```powershell
.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad
```

该脚本会对邮箱、token、API key、callback code 等字段做输出脱敏。它只用于判断状态，不读取认证文件内容。

## 人工账号验证前 smoke test

进入真人 Google / OpenAI / Claude 账号操作前，先运行：

```powershell
.\scripts\browser-login-smoke-test.ps1
```

该脚本不输入账号，不绕过验证码、MFA 或风控。它只验证：

- OnePlus 8T `3f8bbaad` 在线。
- Kite 本地 server 和 `18791 -> 8791` 转发可用。
- OnePlus 8T 设备侧能通过 HTTPS 到达 `accounts.google.com`、`auth.openai.com`、`claude.ai`；HTTP `2xx..4xx` 只证明 DNS/TLS/网络路径可达，不证明账号授权成功。
- `https://accounts.google.com/` 能通过 Android `ACTION_VIEW` 解析到 `com.kite.app` 之外的默认浏览器 Activity；Custom Tabs service 数量会记录为能力诊断，数量为 `0` 时仍允许系统浏览器 fallback，只要实际 handoff 前台匹配外部浏览器。
- 普通 `http://127.0.0.1:8791/status` 页面通过 `/open-web` 打开后仍停留在 Kite WebView，并且不新增 browser auth session。
- Google OAuthPlayground 授权 URL 被 `/open-web` 接收。
- Google OAuthPlayground URL 和 Kite App redirect URL 的本地 `/open-web` 接收耗时不高于默认 `1500ms` 阈值；该阈值只覆盖 Kite 本地 handoff 接收，不覆盖外部网页加载。
- Google、OpenAI/Codex、Claude 相关 OAuth 形态 URL 从 `/open-web` 请求到外部浏览器前台的总耗时不高于默认 `5000ms` 阈值；该阈值覆盖 Kite 发起 handoff 和系统浏览器切前台，不覆盖 provider 页面加载、验证码/MFA 或账号风控。
- 前台页面离开 Kite WebView，进入系统浏览器或 Custom Tabs。
- UI dump 没有 `disallowed_useragent` 或 Google WebView 禁止访问文案。
- 外部 provider 页面没有 `redirect_uri_mismatch`、`invalid_client`、`unsupported_browser`、`Error 400/403` 等阻塞性错误信号；登录、继续、验证码、MFA、paste code 等只记录为账号挑战提示，不代表账号已完成。
- 第三方 HTTPS redirect 不新增假的 AppRedirect session。
- OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 会从 Kite 前台切到外部浏览器 handler，且不新增假的 AppRedirect、CliLoopback 或其他 browser auth session；该检查只证明多站点外部 user-agent 分流，不证明账号授权成功。
- Kite 可接收 `kite-auth://callback` 能创建 AppRedirect pending session，并在同 `state` callback 后交付到发起运行实例。
- 本机生成的假 `code` / token / `state` 不以原文写入 `kite_browser_auth_sessions.xml`，`returnedUrl` 只保留 `present` 摘要。
- 本轮 Google `state`、OpenAI `state`、Claude `state`、AppRedirect `state`、假 `code` 和假 token 不以原文出现在 app 私有 `files` / `shared_prefs` 下的文本类状态或诊断文件中。
- 最近 logcat 没有 FATAL/ANR/Input timeout。
- JSON 输出 `schemaVersion>=10`。完成审计会拒绝没有授权主机网络探测重试 attempts、外部浏览器 handler/fallback、provider 页面阻塞错误信号、OpenAI/Claude OAuth 形态分流、普通 localhost WebView、`/open-web` 响应耗时、外部浏览器前台切换耗时、AppRedirect、脱敏字段或 app 私有文件临时值扫描的旧格式 smoke 结果，即使旧结果显示 `status=passed`。

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.json
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.md
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.png
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-ui.xml
```

预期输出分四段：

```text
---codex-version---
---codex-login-status---
---claude-version---
---claude-auth-status---
```

## 人工 provider 验证前综合预检

当 smoke / smoke watch / readiness / audit 状态已经存在，但需要判断“现在是否值得让用户开始 Google / OpenAI / Claude 真人账号验证”时，运行：

```powershell
.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad
```

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\provider-auth-preflight.json
%LOCALAPPDATA%\Kite\browser-login-continuation\provider-auth-preflight.md
```

判定：

- 先看 JSON/Markdown/控制台里的语义 `exitCode`，不要只看工具面板对非零退出码的概括；`smokeCheckedAt` / `smokeWatchCheckedAt` 应为 ISO 8601，便于和 long-run/status summary 对账。
- 退出码 `2` 且 `status=ready_for_manual_provider_auth`：浏览器环境、provider 页面阻塞错误信号、redirect 类型、CLI callback/fallback、敏感边界、性能和审计形态均已通过，剩余是 Codex/Claude 真实账号授权。
- 退出码 `1` 且 `status=not_ready`：先看 `failedBuckets`。`browser_environment` 通常对应 OAuth 仍在 WebView、默认浏览器 handler 异常、`disallowed_useragent` 或 `unsupported_browser`；`provider_configuration` 通常对应 `redirect_uri_mismatch`、`invalid_client`、client type / redirect allowlist；`cli_callback_or_fallback` 对应 loopback / paste code / device code；`sensitive_boundary` 和 `performance` 分别对应落盘与卡顿风险。
- 退出码 `0`：账号已进入后置完成审计窗口，运行 `browser-login-completion-audit.ps1 -RefreshState`。

该脚本不输入账号、不读取 token、不伪造 callback；它只是把人工验证前的多个证据源汇总成一份可读状态。

## 判定

Codex：

- `codex login status` 输出 `Not logged in`：OpenAI/Codex 完整账号授权未完成。
- 输出已登录账号或等价 authenticated 状态：再补一条真实 Codex CLI 可用性检查，作为完成证据。

Claude：

- `claude auth status` 输出 `"loggedIn": false`：Claude Code 完整账号授权未完成。
- 输出 `"loggedIn": true`：再补一条 Claude Code CLI 可用性检查，作为完成证据。

## 后续补证方式

1. 通过 Kite 发起 Codex 或 Claude 登录。
2. 在系统浏览器完成真实账号授权，或按 CLI 官方提示使用 device code / paste code fallback。
3. 回到 Kite 终端，确认 CLI 自己进入已登录状态。
4. 运行本 SOP 的复验命令。
5. 将输出摘要写入 `PROGRESS.md` 和 `COMPATIBILITY_MATRIX.md`。

## 人工账号启动入口

当用户准备真正在手机上完成 Codex/Claude 账号挑战时，优先运行：

```powershell
.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified
```

默认行为：

- 先运行无账号 smoke test。
- 再运行 manual readiness 和 completion audit。
- 准备度通过后，通过 Kite 现有 `runtime_action=start_resource_open` 分别启动 `kite.codex.cli` 和 `kite.claude.code` 的真实终端入口。
- 如果传入 `-StartWatch`，资源启动后接 `browser-login-account-watch.ps1`，等待用户在系统浏览器里完成真实账号授权。
- 不输入账号、不读取 token、不伪造 callback。

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\manual-account-start-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\manual-account-start-report.md
```

`browser-login-manual-readiness.ps1` 会读取这份状态。`planned`、`launched`、`watch_waiting_for_real_account_authorization` 和 `watch_verified` 只说明启动入口已知或正在等待账号授权，不等于账号已登录；`launch_failed` 或 `watch_needs_inspection` 会让准备度变成 `not_ready`，先检查启动入口。

如果只想看计划、不启动手机资源：

```powershell
.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -PlanOnly
```

如果只是自动化短验证启动入口和 watch 接续，不想等待完整人工登录窗口：

```powershell
.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex -StartWatch -WatchMaxAttempts 1 -WatchPollSeconds 0
```

## 人工授权 watch

当 Codex/Claude 登录入口已经由用户或 `manual-account-start` 拉起，只想让本机同时陪跑收证时，可以直接运行 watch：

```powershell
.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -RunSmokeFirst -RunReadinessFirst -TimeoutMinutes 60 -PollSeconds 30
```

如果希望用户完成真实账号授权后，同一轮自动接完成审计，使用：

```powershell
.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -RunSmokeFirst -RunReadinessFirst -RunCompletionAuditOnVerified -TimeoutMinutes 60 -PollSeconds 30
```

默认行为：

- 可选先运行一次无账号 smoke test，确认浏览器环境、回跳和脱敏边界仍可用；如果同时传入 `-RunSmokeFirst -RunReadinessFirst`，smoke 会先运行，readiness 随后读取刷新后的 smoke 证据。
- 如果传入 `-RunReadinessFirst`，watch 会刷新 manual readiness 和 completion audit；只有准备度为 `ready_for_manual_account`、`partial_account_verified_continue_watch` 或 `account_verified_run_completion_audit` 才进入账号轮询。
- 每轮调用 `browser-login-continuation-runner.ps1`。
- 如果账号仍未登录，写入等待态，不运行交互操作。
- 如果任一目标账号进入 ready 状态，runner 会在同一轮接 `browser-login-post-auth-verify.ps1`，用官方状态命令补证。
- 如果指定 targets 都已 verified 且传入 `-RunCompletionAuditOnVerified`，watch 会继续运行 `browser-login-completion-audit.ps1 -RefreshState`，并把 `completionAuditExit`、`completionAuditStatus`、`completionAuditJsonPath` 和 `completionAuditReportPath` 写入 watch 状态。
- watch 自身只整理 `runner-status.json` 和脱敏 evidence report，不读取 token，不保存邮箱、API key 或 callback code 原文。

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\account-watch-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\account-watch-report.md
%LOCALAPPDATA%\Kite\browser-login-continuation\runner-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md
%LOCALAPPDATA%\Kite\browser-login-continuation\completion-audit.json
%LOCALAPPDATA%\Kite\browser-login-continuation\completion-audit.md
```

退出码：

- `0`：指定 targets 都已由 post-auth 证明确认；若传入 `-RunCompletionAuditOnVerified`，同轮会写入完成审计结果。审计 `status=complete` 才能宣称浏览器线完成。
- `2`：到达轮询次数或超时时仍等待真实账号授权。
- `1`：smoke、环境或 post-auth 输出需要检查。

## 后置补证命令

当 `browser-login-continuation-gate.ps1` 的 `readyTargets` 不为空时，运行：

```powershell
.\scripts\browser-login-post-auth-verify.ps1 -Serial 3f8bbaad -WriteState
```

默认行为：

- 先刷新 `browser-login-continuation-gate.ps1` 的只读账号门槛。
- 若没有任何 `readyTargets`，退出码为 `2`，不运行后置 probe。
- 若存在 ready 的 Codex 账号，运行 `codex login status` 和 `codex doctor --json`；`doctor --json` 是 Codex 官方脱敏健康报告。
- 若存在 ready 的 Claude 账号，运行 `claude auth status --json`。
- 不启动交互会话，不发送 prompt，不调用模型生成内容。
- 输出和状态文件会脱敏账号邮箱、token、API key 和 callback code。

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-raw.txt
```

判定：

- 退出码 `0`：选中的 ready 账号完成后置验证，可以把对应站点补到兼容矩阵。
- 退出码 `2`：当前没有 ready 账号，继续等待真实账号授权。
- 退出码 `1`：选中的账号后置验证失败，需要检查 raw 输出，但不能伪造成功。

## 后置证据摘要

runner 每次运行都会自动刷新一份只含脱敏摘要的 Markdown 报告。需要手动重建时运行：

```powershell
.\scripts\browser-login-evidence-report.ps1
```

默认输出：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md
```

该报告只读取 `last-status.json`、`runner-status.json`、`post-auth-status.json` 和已经脱敏的 `post-auth-raw.txt`。它不是登录事实来源，只是把官方状态命令和 post-auth 验证结果整理成后续更新 `PROGRESS.md` / `COMPATIBILITY_MATRIX.md` 时可复查的摘要。

## 续跑链路自测试

修改 gate、runner、post-auth 或 evidence report 后，先运行：

```powershell
.\scripts\test-browser-login-continuation.ps1
```

该脚本只使用临时目录和 mock 状态，覆盖：

- 等待态不会误触发 post-auth。
- ready 态会在同一次 runner 运行里触发 post-auth。
- runner 会在等待态和 ready 态自动写出 evidence report。
- evidence report 会脱敏账号和 API key。
- runner 显示 `postAuthAttempted=false` 时，旧 post-auth 失败状态不会污染当前等待态。
- account watch 在等待后转 verified 时退出 `0` 并写报告。
- account watch 在仍等待账号授权时退出 `2` 并写等待态。
- manual account start 会先通过 readiness，再启动 Codex/Claude 资源；准备度失败时退出 `1`，且不调用 ADB resource open。
- account watch 带 `-RunReadinessFirst` 时，准备度通过后才调用 runner；准备度失败时退出 `1`、写 `manual_readiness_failed`，且不调用 runner。
- account watch 同时带 `-RunSmokeFirst -RunReadinessFirst` 时，先运行 smoke，再运行 readiness，避免旧 smoke 证据导致人工账号陪跑入口误拒绝。
- manual readiness 会读取 `manual-account-start-status.json`；已启动并等待账号授权的状态应通过，最近 `launch_failed` 应阻止进入人工账号验证。

## 完成状态审计

宣称浏览器线完成前运行：

```powershell
.\scripts\browser-login-completion-audit.ps1 -RefreshState
```

需要让审计同轮刷新真机 smoke 证据时运行：

```powershell
.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest
```

判定：

- 退出码 `0` 且 `status=complete`：当前证据足以进入目标完成审计。
- 退出码 `2` 且 `status=incomplete`：仍缺目标要求中的强证据，通常是 Codex/Claude 真实账号授权完成证据。
- 退出码 `1`：审计脚本或环境异常。

输出文件：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\completion-audit.json
%LOCALAPPDATA%\Kite\browser-login-continuation\completion-audit.md
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-unit-test-output.txt
%LOCALAPPDATA%\Kite\browser-login-continuation\assemble-debug-output.txt
%LOCALAPPDATA%\Kite\browser-login-continuation\adb-devices-output.txt
```

该审计会在 `-RefreshState` 下重新运行 runner、续跑自测试、浏览器 handoff 单测和 `assembleDebug`，并检查 OnePlus 8T `3f8bbaad` 当前 ADB 在线、最近 24 小时 smoke test、runner 自动生成的 `post-auth-evidence-report.md`、Windows 计划任务是否绑定到浏览器线 runner、以及 Codex/Claude 真实账号完成证据。smoke test 覆盖授权主机设备侧 HTTPS 可达和重试 attempts、HTTPS `ACTION_VIEW` 外部浏览器 handler、Custom Tabs service 能力诊断、provider 页面阻塞错误信号、OpenAI/Codex 与 Claude OAuth 形态 URL 外部浏览器分流、普通 localhost WebView、本地 `/open-web` 响应耗时、OAuth handoff 前台切换耗时、实测前台匹配外部浏览器 handler、第三方 HTTPS redirect、Kite App redirect 回跳、auth session 脱敏和 app 私有文本文件临时值扫描；审计要求 `schemaVersion>=10`、关键 item id 完整、普通 localhost 前台为 `com.kite.app`、Google/OpenAI/Claude OAuth 形态 handoff 前台包名匹配默认 HTTPS handler、`providerPageBlockingErrorCount=0`、provider smoke watch 趋势中 `providerPageBlockingErrorRunCount=0`、providerOAuthNewSessionCount 为 `0`、前台切换耗时不超过阈值、`appRedirectStatus=Delivered`、`returnedUrl` 是 `present` 摘要、`rawSecretHitCount=0` 且 `appPrivateRawTemporaryValueHitCount=0`。这些仍不能替代真实账号授权；它只防止把已有实现、mock 测试或账号门槛前截图误判为整条浏览器线完成。
审计还会读取 `manual-account-start-status.json`。账号未全部 verified 时，`planned`、`launched`、`watch_waiting_for_real_account_authorization`、`watch_verified` 或状态缺失但 runner 可读都表示启动入口可继续或可重新生成；`launch_failed` / `watch_needs_inspection` 会成为审计失败项。账号都已 verified 后，旧启动入口状态不再阻塞完成。

审计还会读取 `account-watch-status.json`。账号未全部 verified 时，`waiting_for_real_account_authorization` / `verified` 或状态缺失但 runner 可读都表示 watch 可继续或可重新生成；新鲜的 `smoke_failed`、`manual_readiness_failed`、`needs_inspection` 会成为审计失败项。陈旧 watch 状态可由当前 runner 状态兜底，避免旧陪跑结果误伤后续审计。

## 自动续跑门槛

跨回合时，Codex 不能在没有外部触发的情况下自己发起下一回合。因此浏览器线使用一个只读门槛脚本，让外部调度器或人工唤醒前先判断是否值得继续：

```powershell
.\scripts\browser-login-continuation-gate.ps1 -Serial 3f8bbaad
```

退出码含义：

- `0`：至少一个账号已经进入后置验证窗口，下一回合应先补该账号的 CLI 可用性证据；其他账号若仍未登录，继续保留账号门槛。
- `2`：当前没有任何账号进入后置验证窗口，继续等待真实账号授权或官方 fallback。
- `1`：设备、PRoot、CLI 或状态输出异常，需要先检查环境。

如需本机定时兜底复验，可注册 Windows 计划任务。`60` 是默认兜底间隔，不是硬限制；可以用 `-Minutes 15` 或 `-Minutes 5` 调整，当前脚本下限为 5 分钟，避免高频 ADB/PRoot 轮询：

```powershell
.\scripts\register-browser-login-continuation-gate.ps1 -Serial 3f8bbaad -Minutes 60 -Days 7
```

注册后，计划任务动作调用：

```powershell
.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad
```

runner 会先运行 `browser-login-continuation-gate.ps1`。如果 gate 退出码为 `0`，说明存在 `readyTargets`，runner 会立即运行 `browser-login-post-auth-verify.ps1 -UseExistingGateState -WriteState`，把后置补证状态写入 `post-auth-status.json`。如果 gate 退出码为 `2`，runner 不运行 post-auth，只继续等待真实账号授权。无论是否 ready，runner 都会在写入 `runner-status.json` 后刷新 `post-auth-evidence-report.md`，让后续会话和人工检查能直接看到当前等待态或后置验证结果。

因此，本机侧不是“只能一小时后再做下一步”：一次计划任务运行内部已经是完成驱动。定时间隔只负责在账号尚未授权、Codex 会话没有外部唤醒时做兜底检查。

计划任务只运行官方状态命令，并把最近一次结果写到：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\last-status-raw.txt
%LOCALAPPDATA%\Kite\browser-login-continuation\runner-status.json
%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md
```

其中 `runner-status.json` 是总摘要，包含 `exitCode`、`postAuthAttempted`、`readyTargets`、`waitingTargets`、`verifiedTargets` 和 `failedTargets`。`post-auth-evidence-report.md` 是自动整理出的脱敏阅读版。它们只用于判断是否该唤醒下一回合；它们不是登录事实来源，也不包含 token、cookie 或 callback code。

## 账号等待期 long-run cycle

如果账号长时间没有完成授权，只跑 runner 只能证明“账号仍未 ready”，不能持续证明浏览器环境仍健康。此时可以运行一次 long-run cycle：

```powershell
.\scripts\browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 3 -SmokeIntervalSeconds 600
```

cycle 会按顺序做：

1. 运行 `browser-login-continuation-runner.ps1`，发现账号 ready 时走后置验证。
2. 如果账号仍在等待，运行 `browser-login-smoke-watch.ps1` 检查多轮无账号浏览器链路、前台切换耗时、默认浏览器和泄漏风险。
3. 刷新 `browser-login-manual-readiness.ps1`，给出下一步账号验证状态。
4. 写入 `browser-login-long-run-cycle.json` / `.md`。

输出位置：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-long-run-cycle.json
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-long-run-cycle.md
```

如果只是当前会话里的短验证，例如 `-SmokeIterations 1 -SmokeIntervalSeconds 0`，cycle 会把 smoke watch 写到：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-long-run-cycle-smoke\
```

这样不会覆盖完成审计使用的主 `browser-login-smoke-watch.json`。主 smoke watch 仍必须至少 3 轮通过，才能满足 completion audit。

如需独立的小时级长期巡检任务，可显式注册：

```powershell
.\scripts\register-browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -Minutes 60 -Days 1 -SmokeIterations 6 -SmokeIntervalSeconds 600
```

该任务不替换 `KiteBrowserLoginContinuationGate`。前者偏向“等待账号期间持续巡检浏览器链路”，后者偏向“账号一 ready 就立即补 post-auth 证据”。
