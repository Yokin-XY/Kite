# Kite 浏览器登录回跳进度

最后更新：2026-07-11 B7 已恢复原生 CLI 流程和透明双向回调桥，等待真实账号最终验收

## 当前状态总览

| 任务 | 状态 | 备注 |
| --- | --- | --- |
| B0 建立浏览器任务基线 | done | 三件套和双线隔离说明已建立 |
| B1 确认当前内置浏览器和回跳真实链路 | done | 已整理代码链路，并在 OnePlus 8T 复现 Google WebView `disallowed_useragent` |
| B2 调研官方推荐和通用网站登录回跳模式 | done | 官方资料、风险分层和 Kite 路线判断已写入 |
| B3 设计 Kite 登录回跳协议 | done | `LOGIN_HANDOFF_DESIGN.md` 已写入，B4 实现切片明确 |
| B4 实现最小通用登录回跳 | done | 已实现 URL 分类、Custom Tabs handoff、session/redirect 骨架，并完成 OnePlus 验证 |
| B5 扩展多站点兼容矩阵 | in_progress | OpenAI/Codex 与 Claude Code 均已验证到真实账号页、CliLoopback pending 和终端保留；完整账号授权 callback 仍需用户账号补证 |
| B6 浏览器运行模式切换与自动浏览器地基 | done | 设置页模式入口、持久化、默认回退和自动浏览器边界已完成；自动浏览器内核仍是后续任务 |
| B7 稳定交付版认证事务收口 | in_progress | 原生 Codex 三选一首屏、透明双向 relay、非阻塞历史诊断和真机自动验证已完成；等待用户真实账号最终验收 |

状态取值：`pending` / `in_progress` / `blocked` / `done`

### B7 [in_progress] 稳定交付版认证事务收口

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B7，恢复 WebView + 系统浏览器稳定版的原生 Codex 交互，并让 browser callback 与 CLI HTTP listener 形成透明双向通信。
2. 完成标准：Codex 打开后显示官方三选一首屏；浏览器请求和 CLI 响应完整往返；不新增 provider 特判或第二套认证状态；历史进程中断不拦截正常启动；完成单测、构建和 OnePlus 8T 真机验证。
3. 前置任务：B4/B5 已有外部浏览器、loopback、终端保留和真机证据；`4eb78f4` 是已人工验证成功的回调桥基线；资源安装相关未提交改动必须保留并共同验证。

当前证据：

- 回归 APK 中 Codex open recipe 被改成 `kite-auth-run codex`，真机打开资源后直接执行 `codex login`，与用户要求的官方三选一流程不符。
- 失败现场中 `127.0.0.1:1455` 的 Codex listener 和 `[::1]:1455` 的 Kite relay 同时存在，但 session 只有 `RelayReady`、没有 `CallbackForwarded`；浏览器最终显示网页不可用。
- `previous_process_incomplete` 来自上一进程未到首帧的历史状态，不是当前进程真实异常，不应设置 `failure_pending`。

已实现：

- Codex open/home recipe 恢复为直接启动 `codex`；NPM 安装、依赖和卸载改造保持不变。
- 删除 `kite-auth-run`、`/browser-auth/owner-confirmed` 以及相关 owner 状态，避免绕过 CLI 原生交互。
- `BrowserLoopbackCallbackBridge` 恢复已验证的全双工代理：请求头交给 CLI 后继续转发请求体，同时把 CLI 响应完整复制回浏览器。
- `BrowserAuthSessionStore` 恢复单一 `CallbackForwarded -> Delivered` 传输状态，不把 Kite 自己变成第二个认证 owner。
- `StartupTraceStore` 将上次中断记入 `last_incomplete_*`，不再生成阻塞失败；升级时自动清理旧 `previous_process_incomplete` 待处理记录。

验证证据：

- 目标单测覆盖 loopback 请求/响应往返、session 状态、启动中断语义、Codex manifest 和资源安装协议；结果 `BUILD SUCCESSFUL`。
- `assembleDebug`：`BUILD SUCCESSFUL`；OnePlus 8T `adb install -r`：`Success`。
- 冷启动直接进入 `MainActivity`，到达 `main.first_frame_ready`；logcat 无 `AndroidRuntime` 或 `ActivityTaskManager` 错误。
- 真机构造上一状态为 `running` 后再次冷启动，仍直接进入 `MainActivity`；旧阶段写入 `last_incomplete_*`，新进程重新到 `ready`。
- 真机打开 Codex 资源后显示官方三选一首屏：ChatGPT、Device Code、API Key；未自动打开系统浏览器。
- `browser-login-smoke-test.ps1 -Serial 3f8bbaad`：`status=passed`，外部浏览器 handoff、AppRedirect 回跳、敏感临时值脱敏和无崩溃/ANR 检查全部通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，大小 `231.67 MiB`，SHA-256 `1A596DC4550D6602B5B981D8DF7ADC8DF69CED2D1AD06127BC6F1F8182787A7E`。

剩余验收：

- 用户从当前官方三选一首屏选择 ChatGPT，完成一次真实账号授权；确认系统浏览器收到 CLI HTTP 响应，并自动回到终端完成登录。
- 通过后再检查 CLI 原生登录状态与冷启动持久化，B7 才改为 done 并形成稳定版提交边界。

压力分诊：

- 主要通道：Terminal Runtime + Web Surface；状态拥有者为 CLI 进程、`BrowserAuthSessionStore` 与 `CardRunStore`。
- 事件来源：CLI 发出的授权 URL、系统浏览器 loopback callback、CLI HTTP response。
- 禁止路径：不自动替用户选择登录方式，不读取或转存 token/code，不增加 provider 单点协议，不轮询整页，不重建终端/WebView。
- 验证边界：单测保护透明传输和状态语义；真机验证安装、启动恢复和原生首屏；真实账号授权由用户人工验证。

## 待验证清单

- [x] 浏览器线物理目录为 `D:\xm\Kite-browser-login`。
- [x] 浏览器线分支为 `codex/browser-login-return` 或用户确认的等价分支。
- [x] OnePlus 8T `3f8bbaad` 在线。
- [x] 本机转发端口使用 `18791`，不与 X11 线冲突。
- [x] 当前浏览器链路文档已写入 `docs/browser-login/CURRENT_CHAIN.md`。
- [x] 官方/通用网页登录回跳调研初版已写入 `docs/browser-login/WEB_LOGIN_RESEARCH.md`。
- [x] B3 登录 handoff 方案写入 `docs/browser-login/LOGIN_HANDOFF_DESIGN.md`。
- [x] B4 相关单测、构建、安装、截图和 logcat 检查完成。
- [x] B5 兼容矩阵初版写入 `docs/browser-login/COMPATIBILITY_MATRIX.md`。
- [x] B5 OpenAI/Codex CLI 在 Kite 容器内真实发起登录，外部浏览器打开 OpenAI 登录页，并保留终端显示面。
- [x] B5 OpenAI/Codex CLI loopback listener 在 OnePlus 8T 设备侧可达，`http://127.0.0.1:1455/` 返回 `tiny-http` 的 `404 Not Found`。
- [x] B5 Claude Code 官方安装脚本在 OnePlus 8T 完成，`claude` 已暴露到 `.kf/bin`。
- [x] B5 Claude Code CLI 在 Kite 容器内真实发起登录，外部浏览器打开 Claude 登录页，并保留终端显示面。
- [x] B5 多站点兼容矩阵已补齐 Google / OpenAI / Claude 账号门槛前的真实设备证据。
- [x] B5 Pending/Expired 会话校准已加单测，并在 OnePlus 8T 验证会话层过期落盘与无 active run 时同步收敛。
- [x] B5 App redirect 与 CLI loopback 的同 state 误匹配边界已加单测保护。
- [x] B5 App redirect callback 的 `code` / token 类参数不再以原文写入 `returnedUrl`。
- [x] B5 OAuth handoff 诊断事件、Web 状态文件和 open-web 失败/尝试日志不再保存授权 URL 临时值原文。
- [x] B5 browser auth session 不再通过 `originalUrl` / `state` 原文持久化 OAuth 临时值，改用脱敏摘要与 `requestKey` / `stateKey` 指纹匹配。
- [x] B5 Codex / Claude 账号授权状态可通过 `scripts/browser-login-auth-status.ps1` 非敏感复验；当前真机输出 Codex `Not logged in`，Claude `"loggedIn": false`。
- [x] B5 跨回合续跑门槛可通过 `scripts/browser-login-continuation-gate.ps1` 只读判断；支持按账号粒度触发后置验证；可选 Windows 计划任务注册入口已提供。
- [x] B5 后置补证入口 `scripts/browser-login-post-auth-verify.ps1` 已提供；账号 ready 后可运行非交互、脱敏的 CLI 健康/状态命令补证。
- [x] B5 Windows 计划任务已改为调用 `scripts/browser-login-continuation-runner.ps1`，同一次唤醒内可从 gate 自动接 post-auth。
- [x] B5 人工账号授权期间可通过 `scripts/browser-login-account-watch.ps1` 陪跑；它会轮询 runner，账号 ready 后同轮接 post-auth，并写脱敏 watch 状态/报告。
- [x] B5 account watch 支持 `-RunCompletionAuditOnVerified`，指定账号 verified 后可同轮接完成审计并写入审计状态。
- [x] B5 runner 已写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\runner-status.json` 总摘要，供外部调度和后续会话判断下一步。
- [x] B5 后置账号证据可通过 `scripts/browser-login-evidence-report.ps1` 生成脱敏 Markdown 摘要；当前等待态和 mock 成功态已验证。
- [x] B5 runner 每次运行后会自动刷新 `%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md`；等待态和 ready 态已纳入 mock 自测试。
- [x] B5 续跑链路可通过 `scripts/test-browser-login-continuation.ps1` 做 mock 分支回归。
- [x] B5 续跑自测试已覆盖 account watch：等待后转 verified 退出 `0` 并写报告；仍等待账号授权退出 `2` 并写等待态。
- [x] B5 完成判定可通过 `scripts/browser-login-completion-audit.ps1 -RefreshState` 审计；审计已覆盖浏览器 handoff 单测、`assembleDebug`、续跑自测试、runner 自动 evidence report 和 Windows 计划任务绑定，当前只缺 Codex/Claude 真实账号完成证据。
- [x] B5 完成审计会运行当前浏览器 handoff 单测和 `:app:assembleDebug`；最近一次审计单测退出码 `0`、构建退出码 `0`、debug APK 存在。
- [x] B5 完成审计会检查 OnePlus 8T `3f8bbaad` 当前 ADB 在线；最近一次审计确认 `product:OnePlus8T_CH model:KB2000 device:OnePlus8T`。
- [x] B5 账号验证已抽象为 `ACCOUNT_VERIFICATION_NODES.md` 的 N0-N5 通用节点，不把 Google/验证码/MFA 当作唯一完成路径。
- [x] B5 人工 Google / OpenAI / Claude 账号验证前的高置信度测试策略已写入 `LOGIN_TEST_STRATEGY.md`，区分官方合规、本地确定性、真机无账号、账号门槛和人工账号完成。
- [x] B5 人工账号验证前 OnePlus 8T 无账号 smoke test 已自动化；`browser-login-smoke-test.ps1` 最近一次通过，完成审计已读取该证据。
- [x] B5 无账号 smoke test 已覆盖授权主机设备侧 HTTPS 可达：`accounts.google.com`、`auth.openai.com`、`claude.ai` 均能从 OnePlus 8T 通过 HTTPS 得到 HTTP 响应。
- [x] B5 无账号 smoke test 已覆盖 Kite App redirect：`kite-auth://callback` pending session 可按同 `state` 回跳到 `Delivered`。
- [x] B5 无账号 smoke test 已覆盖 callback 脱敏：`returnedUrl` 为 `code/access_token/state=present`，browser auth session prefs 不含本次假 `code` / token / `state` 原文。
- [x] B5 无账号 smoke test 已覆盖 app 私有文本文件临时值扫描：最近一次扫描 `files` / `shared_prefs` 文本类文件 `117` 个，本轮 OAuth 临时值原文命中 `0`。
- [x] B5 完成审计已强制检查新版 smoke schema：`schemaVersion>=10`、授权主机设备侧 HTTPS 可达、HTTPS `ACTION_VIEW` 外部浏览器 handler、provider 页面无阻塞性错误信号、OpenAI/Codex 与 Claude OAuth 形态 URL 分流、普通 localhost WebView 回归、关键 smoke item、本地 `/open-web` 响应耗时、真实 handoff 前台切换耗时、真实 handoff 前台匹配外部浏览器 handler、`appRedirectStatus=Delivered`、脱敏 `returnedUrl`、`appRedirectRawSecretHitCount=0` 和 `appPrivateRawTemporaryValueHitCount=0`。
- [x] B5 无账号 smoke test 已覆盖本地 `/open-web` 响应耗时：默认阈值 `1500ms`，最近一次普通 localhost `17ms`、Google OAuth handoff `12ms`、App redirect handoff `46ms`。
- [x] B5 无账号 smoke test 已覆盖普通 localhost Web UI 回归：非 OAuth `http://127.0.0.1:8791/status` 留在 Kite WebView 且不新增 browser auth session。
- [x] B5 OAuth URL 持久化边界已扩到 `CardRunStore`：运行状态 `nextActionUrl` 和 history detail 持久化时会对 OAuth URL 写脱敏摘要，redirect 已交付/失败时会清理 `nextActionUrl`。
- [x] B5 无账号 smoke test 增加外部浏览器 HTTPS handler / Custom Tabs fallback 能力检查，并由完成审计读取 schema 6 证据。
- [x] B5 无账号 smoke test 增加 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 多站点外部浏览器分流检查，并由完成审计读取 schema 7 证据。
- [x] B5 OpenAI/Codex 与 Claude provider URL 形态已补分类器回归单测，覆盖 loopback CLI callback 和第三方 HTTPS redirect 外部打开。
- [x] B5 无账号 smoke test 增加 Google/OpenAI/Claude OAuth 形态从 `/open-web` 到外部浏览器前台的总耗时检查，并由完成审计读取 schema 8 证据。
- [x] B5 长期 smoke watch 稳定性测试已提供并在 mock / 真机各验证一轮，输出趋势报告。
- [x] B5 完成审计已强制检查最近 24 小时 smoke watch 趋势证据，至少 3 轮、无失败、p95 不超阈值、handler 稳定且无 session/secret 泄漏。
- [x] B5 人工账号验证前 T0-T6 多方法置信度测试组合与下一轮高置信度自动跑法已写入 `LOGIN_TEST_STRATEGY.md`，区分官方合规、白盒单测、真机 smoke、多轮趋势、人工 watch、完成审计、准备度汇总和真实账号边界。
- [x] B5 人工账号验证前准备度汇总脚本已提供，可输出 `ready_for_manual_account` / `not_ready` 和下一步命令。
- [x] B5 人工账号验证前组合预检已通过 `browser-login-manual-readiness.ps1 -RefreshState -RunSmokeWatch -SmokeIterations 3 -SmokeIntervalSeconds 0 -RunCompletionAudit` 实跑；最近一次输出 `ready_for_manual_account`，3 轮 smoke watch 均通过。
- [x] B5 account watch 支持 `-RunReadinessFirst`，能在账号轮询前刷新 manual readiness / completion audit；准备度通过才进入 runner，准备度失败不会误轮询账号。
- [x] B5 smoke schema 9 已为授权主机设备侧 HTTPS 探测加入重试 attempts 证据；schema 10 在此基础上新增 provider 页面阻塞错误信号，并已由完成审计读取。
- [x] B5 account watch 同时使用 `-RunSmokeFirst -RunReadinessFirst` 时，已固定先刷新无账号 smoke，再执行 manual readiness / completion audit，避免旧 smoke 证据导致人工账号陪跑入口误拒绝。
- [x] B5 人工账号授权启动入口已提供：`browser-login-manual-account-start.ps1` 能先做 smoke/readiness，再用现有 resource open 拉起 Codex/Claude 真实终端登录入口，并可选接 account watch。
- [x] B5 人工账号授权启动入口支持 bounded watch：自动短验证可传 `-WatchMaxAttempts 1 -WatchPollSeconds 0`，account-watch 单目标输出会过滤到本次 targets。
- [x] B5 manual readiness 已读取 manual account start 状态：真实 readiness 可显示 `watch_waiting_for_real_account_authorization`、`launchedTargets=claude`、`watchMaxAttempts=1`，但仍不把等待态当成账号已登录。
- [x] B5 completion audit 已读取 manual account start 状态：真实审计中 `manual-account-start-state` 通过，失败项仍只有 `codex-account`、`claude-account`。
- [x] B5 completion audit 已读取 account watch 状态：真实审计中 `account-watch-state` 通过，失败项仍只有 `codex-account`、`claude-account`。
- [x] B5 人工账号验证前高置信度组合已刷新：3 轮 smoke watch 通过，manual readiness 为 `ready_for_manual_account`，completion audit 仍只缺真实账号授权证据。
- [x] B5 smoke test 已修正 AppRedirect 等待口径：`Returned` 只作为中间态，必须等到 `Delivered` 或 `Failed` 才结束该检查；最新真机 smoke 通过，`appRedirectStatus=Delivered`。
- [x] B5 人工账号启动入口已刷新双目标证据：`manual-account-start-status.json` 显示 `targets=codex,claude`、`launchedTargets=codex,claude`、`watch_waiting_for_real_account_authorization`。
- [x] B5 人工账号验证前六轮短周期 smoke watch 已刷新：`status=passed`、`failureCount=0`、`openWebP95Ms=82/1500`、`foregroundP95Ms=1431/5000`、`handlerPackages=com.heytap.browser`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`。
- [x] B5 六轮 smoke watch 后已刷新 runner、completion audit 和 manual readiness：runner 仍为 `wait_for_real_account_authorization`；completion audit 仍只缺 `codex-account,claude-account`；manual readiness 为 `ready_for_manual_account`。
- [x] B5 账号等待期 long-run cycle 已提供：`browser-login-long-run-cycle.ps1` 会串联 runner、smoke watch、manual readiness 和可选 completion audit；短 cycle 的 smoke watch 写入独立子目录，不污染主 3 轮趋势证据。
- [x] B5 long-run cycle 已纳入续跑自测试、manual readiness 脚本清单和 completion audit 脚本清单；真机 1 轮 cycle 输出 `waiting_account_browser_stable`，主 smoke watch 仍保持 3 轮通过。
- [x] B5 小时级 long-run cycle 计划任务已注册并纳入 completion audit：`KiteBrowserLoginLongRunCycle` 当前为 `Running`，触发间隔 `PT1H`、持续 `P1D`，参数为 `SmokeIterations=6` / `SmokeIntervalSeconds=600`；05:18 审计中 `scheduled-long-run-cycle-task` 通过，失败项仍只有 `codex-account,claude-account`。
- [x] B5 smoke watch / long-run cycle 已增加运行中 progress JSON：`browser-login-smoke-watch-progress.json` 逐轮记录已完成/剩余轮数，`browser-login-long-run-cycle-progress.json` 记录当前阶段；续跑自测试已覆盖这两个 progress 文件。
- [x] B5 当前状态只读汇总已提供：`browser-login-status-summary.ps1` 会读取计划任务、runner、long-run、smoke watch、manual readiness、completion audit 和 progress 文件；真实运行 `2026-07-05T05:34:19+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_without_progress_from_pre_progress_script`、`waitingTargets=codex,claude`。
- [x] B5 人工 provider 验证前综合预检已提供：`browser-login-provider-preflight.ps1` 可汇总官方合规、外部浏览器环境、redirect 类型、CLI fallback、敏感边界、性能和账号状态；真实运行输出 `ready_for_manual_provider_auth`，阻塞项为空。
- [x] B5 provider 页面阻塞错误信号已纳入 smoke / smoke watch / provider preflight / completion audit：最新 smoke `schemaVersion=10`、`providerPageSignalState=challenge_or_login_visible`、`providerPageBlockingErrorCount=0`、`providerPageChallengeHintCount=2`；最新 3 轮 smoke watch `providerPageBlockingErrorRunCount=0`。
- [x] B5 人工验证通过率置信模型已写入 `LOGIN_TEST_STRATEGY.md`：区分浏览器合规、provider 配置、网络性能、AppRedirect/CLI callback、敏感边界和账号所有权；明确哪些可自动接近确定、哪些必须由真人账号和官方状态命令收证。
- [x] B5 人工 provider 验证作战清单已写入 `ACCOUNT_AUTH_COMPLETION_SOP.md`：人工开始前先看 status summary / provider preflight，默认使用真实外部浏览器状态，保存脱敏证据，失败按浏览器环境、provider 配置、账号挑战、callback/fallback 和性能分层归因。
- [x] B6 设置页提供浏览器运行模式切换入口。
- [x] B6 模式选择持久化，默认保持当前 WebView + 系统浏览器登录行为。
- [x] B6 自动浏览器模式边界写入决策记录，不把未完成内核伪装成可用能力。

### B6 [done] 浏览器运行模式切换与自动浏览器地基

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B6，在设置中提供两种浏览器运行模式切换，并建立自动浏览器后续接入的状态地基。
2. 完成标准：设置页能显示和切换“WebView + 系统浏览器登录 / 自动浏览器”；选择持久化且默认不改变现有 handoff；自动浏览器模式不绕过官方登录边界；完成最小构建或单测验证。
3. 前置任务：B4 已完成，当前 WebView + 系统浏览器登录链路已由用户人工确认可用；B5 仍等待真实账号授权证据，但不阻塞 B6 的设置地基。

本轮计划：

- 新增浏览器运行模式枚举和读取/保存接口。
- 在设置页新增“浏览器模式”入口，显示当前模式并允许二选一。
- 更新决策记录，固定“自动浏览器”和“官方外部登录 handoff”是两条边界清晰的能力。

已完成：

- 新增 `BrowserRuntimeMode`，提供 `webview_system_auth` 和 `automation_browser` 两个持久化值，未知值回落到默认 `WebViewWithSystemAuth`。
- `MainActivity.showSettings()` 新增“浏览器模式”入口，当前默认显示“WebView + 系统浏览器登录”。
- 点击入口会弹出二选一模式面板；选择后写入 `kite_app_settings.browser_runtime_mode` 并刷新设置页。
- 自动浏览器文案明确为实验入口，账号授权仍保持官方外部浏览器边界；运行时 handoff 分类未改动。
- `DECISIONS.md` 新增 ADR-B074，固定浏览器运行模式切换不改变认证安全边界。

验证证据：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.BrowserRuntimeModeTest" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `git diff --check -- app docs`：无 diff 格式错误，仅提示既有 LF/CRLF 换行转换提醒。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- OnePlus 8T 设置页 UI dump `docs/browser-login/evidence/browser-mode-settings.xml` 包含“浏览器模式 / WebView + 系统浏览器登录”。
- OnePlus 8T 模式弹层 UI dump `docs/browser-login/evidence/browser-mode-dialog.xml` 包含“WebView + 系统浏览器登录”和“自动浏览器”两项。
- 切到自动浏览器后，`run-as com.kite.app cat shared_prefs/kite_app_settings.xml` 显示 `<string name="browser_runtime_mode">automation_browser</string>`。
- 验证结束后已切回默认模式，最终 prefs 显示 `<string name="browser_runtime_mode">webview_system_auth</string>`。
- 截图证据：`docs/browser-login/evidence/browser-mode-settings-default.png`。
- [x] B5 05:40 续跑状态已复核：账号 gate 仍等待 `codex,claude`；小时级 long-run 仍运行中；运行中单轮 smoke 在 `2026-07-05T05:38:56+08:00` 通过，provider 阻塞错误和敏感值原文命中均为 `0`。
- [x] B5 05:42 provider preflight 已复核：`ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`smokeCheckedAt=2026-07-05T05:38:56+08:00`，仍只等待真实账号。
- [x] B5 当前状态汇总已增强：`browser-login-status-summary.ps1` 现在直接输出 latest smoke 的 `checkedAt/status/schemaVersion/providerPageBlockingErrorCount/appPrivateRawTemporaryValueHitCount`，真实运行 `2026-07-05T05:46:31+08:00` 已读到 05:38 smoke 样本。
- [x] B5 05:50 续跑状态已复核：账号 gate 仍等待 `codex,claude`；小时级 long-run 仍运行中；运行中单轮 smoke 在 `2026-07-05T05:49:25+08:00` 通过，provider 阻塞错误和敏感值原文命中均为 `0`。
- [x] B5 状态汇总时间戳已统一为 ISO 8601：控制台 `smokeCheckedAt` 不再受本机区域格式影响，便于长跑对账和日志复制。
- [x] B5 状态汇总已增加长跑无 progress 超时判断：按计划任务 `SmokeIterations=6` / `SmokeIntervalSeconds=600` 估算宽限时间，当前 05:18 长跑已运行约 41 分钟，`longRunNoProgressOverdue=False`。
- [x] B5 状态汇总已增加 latest smoke 与当前 long-run 启动时间关联：真实运行 `2026-07-05T06:05:11+08:00` 输出 `longRunObservation=running_without_progress_latest_smoke_after_current_run_start`、`latestSmokeAfterLongRunStart=True`、`latestSmokeAgeMinutes=5`、`smokeCheckedAt=2026-07-05T05:59:51+08:00`，仍等待 `codex,claude` 真实账号授权。
- [x] B5 状态汇总已输出 long-run 无 progress 告警时间和剩余时间：真实运行 `2026-07-05T06:10:15+08:00` 输出 `longRunNoProgressOverdueAt=2026-07-05T06:38:01+08:00`、`longRunNoProgressMinutesRemaining=28`、`longRunNoProgressOverdue=False`，仍不并发启动新 smoke/watch。
- [x] B5 05:18 小时级 long-run 已自然完成：`browser-login-long-run-cycle.json` 在 `2026-07-05T06:10:44+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`，6 轮 smoke watch 全通过，`openWebP95Ms=88/1500`、`foregroundP95Ms=1942/5000`、`providerPageBlockingErrorRunCount=0`、`secretLeakRunCount=0`。
- [x] B5 provider preflight 已输出语义 `exitCode` 并固定证据时间戳：真实运行 `2026-07-05T06:18:54+08:00` 输出 `ready_for_manual_provider_auth`、`exitCode=2`、`blockingFailureIds=(none)`、`smokeCheckedAt=2026-07-05T06:18:04+08:00`、`smokeWatchCheckedAt=2026-07-05T06:10:43+08:00`。
- [x] B5 06:18 小时级 long-run 已按计划再次启动：状态汇总 `2026-07-05T06:19:24+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`latestSmokeCheckedAt=2026-07-05T06:18:04+08:00`、`providerPreflight.checkedAt=2026-07-05T06:18:54+08:00`。
- [x] B5 状态汇总已输出运行中 progress 细节：真实跨轮运行 `2026-07-05T06:29:13+08:00` 输出 `longRunProgressPhase=smoke_watch_started`、`smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:39:00+08:00`、`smokeWatchProgressNextExpectedOverdue=False`、`providerPreflightExitCode=2`。
- [x] B5 06:39 长跑第三轮已自然落地：只读状态汇总 `2026-07-05T06:39:51+08:00` 输出 `smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T06:39:00+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:49:28+08:00`、`providerPreflightExitCode=2`。
- [x] B5 06:49 长跑第四轮已自然落地：只读状态汇总 `2026-07-05T06:50:10+08:00` 输出 `smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T06:49:28+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:59:55+08:00`、`providerPreflightExitCode=2`。
- [x] B5 06:59 长跑第五轮已自然落地：只读状态汇总 `2026-07-05T07:01:01+08:00` 输出 `smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T06:59:55+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T07:10:21+08:00`、`providerPreflightExitCode=2`。
- [x] B5 06:18 小时级 long-run 6/6 已自然完成：`browser-login-long-run-cycle.json` 在 `2026-07-05T07:10:49+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`smokeWatchExit=0`、`manualReadinessExit=0`、`smokeWatchStatus=passed`、`smokeWatchFailureCount=0`、`smokeWatchOpenWebP95Ms=98/1500`、`smokeWatchForegroundP95Ms=2103/5000`、`smokeWatchProviderPageBlockingErrorRunCount=0`、`smokeWatchSecretLeakRunCount=0`、`manualReadinessStatus=ready_for_manual_account`。
- [x] B5 07:12 完成审计复核：`browser-login-completion-audit.ps1 -RefreshState` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`，说明长跑后仍只缺真实账号完成证据。
- [x] B5 07:18 下一轮小时级 long-run 已自然启动并完成第 1/6 轮：只读状态汇总 `2026-07-05T07:19:29+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`smokeWatchProgressCompleted=1`、`smokeWatchProgressRemaining=5`、`smokeCheckedAt=2026-07-05T07:18:05+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T07:28:33+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 07:28 新一轮小时级 long-run 第二轮已自然落地：只读状态汇总 `2026-07-05T07:29:32+08:00` 输出 `smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeCheckedAt=2026-07-05T07:28:33+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T07:39:02+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 07:39 新一轮小时级 long-run 第三轮已自然落地：只读状态汇总 `2026-07-05T07:39:37+08:00` 输出 `smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T07:39:02+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T07:49:30+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 07:49 新一轮小时级 long-run 第四轮已自然落地：只读状态汇总 `2026-07-05T07:50:38+08:00` 输出 `smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T07:49:31+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T07:59:59+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 07:59 新一轮小时级 long-run 第五轮已自然落地：只读状态汇总 `2026-07-05T08:02:07+08:00` 输出 `smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T07:59:59+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T08:10:26+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 07:18 新一轮小时级 long-run 6/6 已自然完成：`browser-login-long-run-cycle.json` 在 `2026-07-05T08:10:52+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`；`browser-login-smoke-watch.json` 在 `2026-07-05T08:10:51+08:00` 输出 `status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=78/1500`、`foregroundP95Ms=3054/5000`、`providerPageBlockingErrorRunCount=0`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`；manual readiness 为 `ready_for_manual_account`。
- [x] B5 08:11 完成审计复核：`browser-login-completion-audit.ps1 -RefreshState` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`，说明本轮 6/6 长跑后仍只缺真实账号完成证据。
- [x] B5 08:18 下一轮小时级 long-run 已自然启动并完成第 1/6 轮：只读状态汇总 `2026-07-05T08:18:41+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`smokeWatchProgressCompleted=1`、`smokeWatchProgressRemaining=5`、`smokeCheckedAt=2026-07-05T08:18:05+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T08:28:31+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 08:28 新一轮小时级 long-run 第二轮已自然落地：只读状态汇总 `2026-07-05T08:29:16+08:00` 输出 `smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeCheckedAt=2026-07-05T08:28:31+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T08:38:56+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 08:38 新一轮小时级 long-run 第三轮已自然落地：只读状态汇总 `2026-07-05T08:40:06+08:00` 输出 `smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T08:38:56+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T08:49:21+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 08:49 新一轮小时级 long-run 第四轮已自然落地：只读状态汇总 `2026-07-05T08:50:49+08:00` 输出 `smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T08:49:22+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T08:59:49+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 08:59 新一轮小时级 long-run 第五轮已自然落地：只读状态汇总 `2026-07-05T09:01:14+08:00` 输出 `smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T08:59:49+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`、`smokeWatchProgressNextExpectedAt=2026-07-05T09:10:14+08:00`、`waitingTargets=codex,claude`、`providerPreflightExitCode=2`。
- [x] B5 08:18 新一轮小时级 long-run 6/6 已自然完成：`browser-login-long-run-cycle.json` 在 `2026-07-05T09:10:42+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`；`browser-login-smoke-watch.json` 在 `2026-07-05T09:10:40+08:00` 输出 `status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=95/1500`、`foregroundP95Ms=1780/5000`、`providerPageBlockingErrorRunCount=0`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`；manual readiness 为 `ready_for_manual_account`。
- [x] B5 09:12 完成审计复核：`browser-login-completion-audit.ps1 -RefreshState` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`，说明本轮 6/6 长跑后仍只缺真实账号完成证据。
- [ ] B5 OpenAI/Codex 完整账号授权后的 localhost callback relay 或官方 fallback 完成证据。
- [ ] B5 Claude Code 完整账号授权后的 localhost callback 或 paste code 完成证据。

## 任务日志

### B5 [done] 复核 08:18 新一轮 long-run 6/6 完成与审计缺口

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和第 18/22 条，确认 08:18 小时级 long-run 6/6 自然完成后，浏览器稳定性证据是否满足趋势要求，并用 completion audit 复核最终缺口。
2. 完成标准：不输入账号、不读取 token、不伪造 callback；读取 `browser-login-long-run-cycle.json`、`browser-login-smoke-watch.json` 和 `manual-account-readiness.json`；运行 `browser-login-completion-audit.ps1 -RefreshState`；回写 6/6 证据和仍缺账号项。
3. 前置任务：B0-B4 已完成；B5 已有 5/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T09:12:00+08:00` 状态汇总输出 `status=waiting_for_real_account_authorization`、`longRunProgressPhase=finished`、`longRunProgressStatus=waiting_account_browser_stable`、`smokeWatchProgressCompleted=6`、`smokeWatchProgressRemaining=0`、`smokeCheckedAt=2026-07-05T09:10:15+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- `browser-login-long-run-cycle.json` 在 `2026-07-05T09:10:42+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`。
- `browser-login-smoke-watch.json` 在 `2026-07-05T09:10:40+08:00` 输出 `status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=95/1500`、`foregroundP95Ms=1780/5000`、`providerPageBlockingErrorRunCount=0`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`、`handlerPackages=com.heytap.browser`。
- `manual-account-readiness.json` 在 `2026-07-05T09:10:42+08:00` 输出 `status=ready_for_manual_account`、`waitingTargets=codex,claude`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 在 `2026-07-05T09:12:28+08:00` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`。

结论：

- 08:18 小时级 long-run 已完整跑完 6/6，浏览器可控链路在本轮继续稳定；provider 页面阻塞错误、假 session 泄漏和敏感值泄漏均为 `0`。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据；不能把 `ready_for_manual_account` 写成账号已登录。

### B5 [done] 复核 08:18 新一轮 long-run 第 5/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 08:18 小时级 long-run 是否按 progress 预计推进到第 5/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 5 轮 smoke 通过、provider 阻塞错误为 `0`、最后一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 08:18 新一轮第 4/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:56:56+08:00` 中途只读复核显示当前长跑正常运行，4/6 轮已通过，下一轮预计 `2026-07-05T08:59:49+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T09:01:14+08:00` 状态汇总输出第 5 轮已通过：`smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T08:59:49+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 最后一轮预计时间更新为 `2026-07-05T09:10:14+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 08:18 小时级 long-run 继续自然推进，当前第 5/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 08:18 新一轮 long-run 第 4/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 08:18 小时级 long-run 是否按 progress 预计推进到第 4/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 4 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 08:18 新一轮第 3/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:45:11+08:00` 中途只读复核显示当前长跑正常运行，3/6 轮已通过，下一轮预计 `2026-07-05T08:49:21+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T08:50:49+08:00` 状态汇总输出第 4 轮已通过：`smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T08:49:22+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T08:59:49+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 08:18 小时级 long-run 继续自然推进，当前第 4/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 08:18 新一轮 long-run 第 3/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 08:18 小时级 long-run 是否按 progress 预计推进到第 3/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 3 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 08:18 新一轮第 2/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:37:30+08:00` 中途只读复核显示当前长跑正常运行，2/6 轮已通过，下一轮预计 `2026-07-05T08:38:56+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T08:40:06+08:00` 状态汇总输出第 3 轮已通过：`smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T08:38:56+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T08:49:21+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 08:18 小时级 long-run 继续自然推进，当前第 3/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 08:18 新一轮 long-run 第 2/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 08:18 小时级 long-run 是否按 progress 预计推进到第 2/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 2 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 08:18 新一轮第 1/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:22:00+08:00` 中途只读复核显示当前长跑正常运行，1/6 轮已通过，下一轮预计 `2026-07-05T08:28:31+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T08:29:16+08:00` 状态汇总输出第 2 轮已通过：`smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeCheckedAt=2026-07-05T08:28:31+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T08:38:56+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 08:18 小时级 long-run 继续自然推进，当前第 2/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 08:18 新一轮 long-run 启动和第 1/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，确认 07:18 long-run 完成后，08:18 下一轮小时级 long-run 是否按计划自然启动，并完成第 1/6 轮 smoke。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 1 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有上一轮 6/6 完成和 completion audit 只缺账号证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:14:12+08:00` 状态汇总仍为 `waiting_for_real_account_authorization`，上一轮 long-run 已 finished，下一次计划任务为 `2026/7/5 8:18:01`。
- `KiteBrowserLoginContinuationGate` 在 `2026/7/5 8:15:13` 运行，`LastTaskResult=2`；账号仍等待 `codex,claude`，未触发 post-auth。
- 等待计划任务自然触发后，`2026-07-05T08:18:41+08:00` 状态汇总输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`smokeWatchProgressCompleted=1`、`smokeWatchProgressRemaining=5`。
- 第 1 轮 smoke `smokeCheckedAt=2026-07-05T08:18:05+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间为 `2026-07-05T08:28:31+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 08:18 新一轮小时级 long-run 已按计划自然启动，当前第 1/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 07:18 新一轮 long-run 6/6 完成与审计缺口

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和第 18/22 条，确认 07:18 小时级 long-run 6/6 自然完成后，浏览器稳定性证据是否满足趋势要求，并用 completion audit 复核最终缺口。
2. 完成标准：不输入账号、不读取 token、不伪造 callback；读取 `browser-login-long-run-cycle.json`、`browser-login-smoke-watch.json` 和 `manual-account-readiness.json`；运行 `browser-login-completion-audit.ps1 -RefreshState`；回写 6/6 证据和仍缺账号项。
3. 前置任务：B0-B4 已完成；B5 已有 5/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T08:10:58+08:00` 状态汇总输出 `status=waiting_for_real_account_authorization`、`longRunProgressPhase=finished`、`longRunProgressStatus=waiting_account_browser_stable`、`smokeWatchProgressCompleted=6`、`smokeWatchProgressRemaining=0`、`smokeCheckedAt=2026-07-05T08:10:26+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- `browser-login-long-run-cycle.json` 在 `2026-07-05T08:10:52+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`。
- `browser-login-smoke-watch.json` 在 `2026-07-05T08:10:51+08:00` 输出 `status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=78/1500`、`foregroundP95Ms=3054/5000`、`providerPageBlockingErrorRunCount=0`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`、`handlerPackages=com.heytap.browser`。
- `manual-account-readiness.json` 在 `2026-07-05T08:10:52+08:00` 输出 `status=ready_for_manual_account`、`waitingTargets=codex,claude`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 在 `2026-07-05T08:11:31+08:00` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`。

结论：

- 07:18 小时级 long-run 已完整跑完 6/6，浏览器可控链路在本轮继续稳定；provider 页面阻塞错误、假 session 泄漏和敏感值泄漏均为 `0`。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据；不能把 `ready_for_manual_account` 写成账号已登录。

### B5 [done] 复核 07:18 新一轮 long-run 第 5/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 07:18 小时级 long-run 是否按 progress 预计推进到第 5/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 5 轮 smoke 通过、provider 阻塞错误为 `0`、最后一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 07:18 新一轮第 4/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T07:58:36+08:00` 中途只读复核显示当前长跑正常运行，4/6 轮已通过，下一轮预计 `2026-07-05T07:59:59+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T08:00:27+08:00` 第一次过预计时间复核仍显示 4/6，但 `smokeWatchProgressNextExpectedOverdue=False`，处于合理执行缓冲内，未并发启动新测试。
- `2026-07-05T08:02:07+08:00` 状态汇总输出第 5 轮已通过：`smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T07:59:59+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 最后一轮预计时间更新为 `2026-07-05T08:10:26+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 07:18 小时级 long-run 继续自然推进，当前第 5/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 07:18 新一轮 long-run 第 4/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 07:18 小时级 long-run 是否按 progress 预计推进到第 4/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 4 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 07:18 新一轮第 3/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T07:47:33+08:00` 中途只读复核显示当前长跑正常运行，3/6 轮已通过，下一轮预计 `2026-07-05T07:49:30+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T07:50:38+08:00` 状态汇总输出第 4 轮已通过：`smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T07:49:31+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T07:59:59+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 07:18 小时级 long-run 继续自然推进，当前第 4/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 07:18 新一轮 long-run 第 3/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 07:18 小时级 long-run 是否按 progress 预计推进到第 3/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 3 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 07:18 新一轮第 2/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T07:37:40+08:00` 中途只读复核显示当前长跑正常运行，2/6 轮已通过，下一轮预计 `2026-07-05T07:39:02+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T07:39:37+08:00` 状态汇总输出第 3 轮已通过：`smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T07:39:02+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T07:49:30+08:00`，账号仍等待 `codex,claude`，`providerPreflightExitCode=2`。

结论：

- 07:18 小时级 long-run 继续自然推进，当前第 3/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 07:18 新一轮 long-run 第 2/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 07:18 小时级 long-run 是否按 progress 预计推进到第 2/6 轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认第 2 轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；账号仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有 07:18 新一轮第 1/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T07:24:52+08:00` 中途只读复核显示当前长跑正常运行，1/6 轮已通过，下一轮预计 `2026-07-05T07:28:33+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。
- `2026-07-05T07:29:32+08:00` 状态汇总输出第 2 轮已通过：`smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeCheckedAt=2026-07-05T07:28:33+08:00`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T07:39:02+08:00`，账号仍等待 `codex,claude`。

结论：

- 07:18 小时级 long-run 继续自然推进，当前第 2/6 轮通过；当前不需要并发启动新的 smoke/watch。
- B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核 07:18 新一轮 long-run 启动和第 1/6 轮

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064 和 ADR-B072，确认 06:18 long-run 完成后，07:18 下一轮小时级 long-run 是否按计划自然启动，并完成第 1/6 轮 smoke。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认计划任务 `KiteBrowserLoginLongRunCycle` 为 Running、`LastRunTime=2026/7/5 7:18:02`，第 1 轮 smoke 通过且 provider 阻塞错误为 `0`；账号 gate 仍没有 ready target 时保持等待，不触发 post-auth。
3. 前置任务：B0-B4 已完成；B5 已有上一轮 6/6 完成和 completion audit 只缺账号证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T07:14:54+08:00` 状态汇总仍为 `waiting_for_real_account_authorization`，上一轮 long-run 已 finished，下一次计划任务为 `2026/7/5 7:18:01`。
- 等待计划任务自然触发后，`2026-07-05T07:19:29+08:00` 状态汇总输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`smokeWatchProgressCompleted=1`、`smokeWatchProgressRemaining=5`。
- 第 1 轮 smoke `smokeCheckedAt=2026-07-05T07:18:05+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- `KiteBrowserLoginContinuationGate` 在 `2026/7/5 7:15:13` 运行，`LastTaskResult=2`；账号仍等待 `codex,claude`，未触发 post-auth。

结论：

- 07:18 新一轮小时级 long-run 已按计划自然启动，当前第 1/6 轮通过，下一轮预计 `2026-07-05T07:28:33+08:00`。
- 当前不需要并发启动新的 smoke/watch；B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核小时级 long-run 六轮完成与完成审计缺口

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和第 18/22 条，确认 06:18 小时级 long-run 自然完成后，浏览器稳定性证据是否满足 6 轮趋势要求，并用 completion audit 复核最终缺口。
2. 完成标准：不输入账号、不读取 token、不伪造 callback；读取 `browser-login-long-run-cycle.json`、`browser-login-smoke-watch.json` 和 `manual-account-readiness.json`；运行 `browser-login-completion-audit.ps1 -RefreshState`；回写 6/6 证据和仍缺账号项。
3. 前置任务：B0-B4 已完成；B5 已有 5/6 轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T07:11:46+08:00` 输出 `longRunProgressPhase=finished`、`longRunProgressStatus=waiting_account_browser_stable`、`smokeWatchProgressCompleted=6`、`smokeWatchProgressRemaining=0`、`smokeCheckedAt=2026-07-05T07:10:21+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- `browser-login-long-run-cycle.json` 在 `2026-07-05T07:10:49+08:00` 输出 `status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`、`waitingTargets=codex,claude`。
- `browser-login-smoke-watch.json` 在 `2026-07-05T07:10:48+08:00` 输出 `status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=98/1500`、`foregroundP95Ms=2103/5000`、`handlerPackages=com.heytap.browser`、`handlerStable=True`、`providerSessionLeakRunCount=0`、`providerPageBlockingErrorRunCount=0`、`secretLeakRunCount=0`。
- `manual-account-readiness.json` 在 `2026-07-05T07:10:49+08:00` 输出 `status=ready_for_manual_account`、`waitingTargets=codex,claude`、`failedItemIds=[]`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 在 `2026-07-05T07:12:44+08:00` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`。

结论：

- 06:18 小时级 long-run 已自然完成 6/6，浏览器环境、前台切换、provider 页面阻塞错误、session 泄漏和敏感值落盘趋势均通过。
- completion audit 没有新增环境或实现失败项；B5 最终缺口仍只是真实 Codex/OpenAI 与 Claude/Anthropic 账号授权完成证据。

### B5 [done] 复核小时级 long-run 第五轮自然落地

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064、ADR-B065 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 06:18 小时级 long-run 是否按 progress 预计自然推进到第五轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认 long-run 仍为 `running_with_progress`、第五轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；回写 `PROGRESS.md` / `COMPATIBILITY_MATRIX.md`。
3. 前置任务：B0-B4 已完成；B5 已有 06:49 第四轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T06:56:14+08:00` 只读状态显示当前长跑正常运行，4/6 轮已通过，下一轮预计 `2026-07-05T06:59:55+08:00`。
- 等待 280 秒后再次只读运行，`2026-07-05T07:01:01+08:00` 输出第五轮已通过：`smokeWatchProgressCompleted=5`、`smokeWatchProgressRemaining=1`、`smokeCheckedAt=2026-07-05T06:59:55+08:00`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T07:10:21+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。

结论：

- 06:18 小时级 long-run 继续按 6 轮 / 600 秒节奏推进；当前不需要并发启动新的 smoke/watch。
- 账号 gate 仍等待 `codex,claude`，B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核小时级 long-run 第四轮自然落地

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064、ADR-B065 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 06:18 小时级 long-run 是否按 progress 预计自然推进到第四轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认 long-run 仍为 `running_with_progress`、第四轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；回写 `PROGRESS.md` / `COMPATIBILITY_MATRIX.md`。
3. 前置任务：B0-B4 已完成；B5 已有 06:39 第三轮通过证据；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T06:44:23+08:00` 只读状态显示当前长跑正常运行，3/6 轮已通过，下一轮预计 `2026-07-05T06:49:28+08:00`。
- 等待 340 秒后再次只读运行，`2026-07-05T06:50:10+08:00` 输出第四轮已通过：`smokeWatchProgressCompleted=4`、`smokeWatchProgressRemaining=2`、`smokeCheckedAt=2026-07-05T06:49:28+08:00`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T06:59:55+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。

结论：

- 06:18 小时级 long-run 继续按 6 轮 / 600 秒节奏推进；当前不需要并发启动新的 smoke/watch。
- 账号 gate 仍等待 `codex,claude`，B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 复核小时级 long-run 第三轮自然落地

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B063、ADR-B064、ADR-B065 和 ADR-B072，在不并发启动新 smoke/watch 的前提下，复核 06:18 小时级 long-run 是否按 progress 预计自然推进到第三轮。
2. 完成标准：只读运行 `browser-login-status-summary.ps1 -Serial 3f8bbaad`；确认 OnePlus 8T `3f8bbaad` 在线、long-run 仍为 `running_with_progress`、第三轮 smoke 通过、provider 阻塞错误为 `0`、下一轮预计时间可见；回写 `PROGRESS.md` / `COMPATIBILITY_MATRIX.md`。
3. 前置任务：B0-B4 已完成；B5 已有 long-run cycle、progress JSON、status summary 和 provider preflight；账号授权仍等待真实 Codex/Claude 账号。

已完成：

- `2026-07-05T06:38:08+08:00` 只读状态显示当前长跑正常运行，2/6 轮已通过，下一轮预计 `2026-07-05T06:39:00+08:00`。
- 等待 95 秒后再次只读运行，`2026-07-05T06:39:51+08:00` 输出第三轮已通过：`smokeWatchProgressCompleted=3`、`smokeWatchProgressRemaining=3`、`smokeCheckedAt=2026-07-05T06:39:00+08:00`、`smokeProviderBlockingErrorCount=0`。
- 下一轮预计时间更新为 `2026-07-05T06:49:28+08:00`，`smokeWatchProgressNextExpectedOverdue=False`。

结论：

- 06:18 小时级 long-run 没有卡住，正在按 6 轮 / 600 秒节奏自然推进；当前不需要并发启动新的 smoke/watch。
- 账号 gate 仍等待 `codex,claude`，B5 最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号完成证据。

### B5 [done] 补人工 provider 验证作战清单

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、`LOGIN_TEST_STRATEGY.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 ADR-B066，把用户提出的“人工 Google / OpenAI / Claude 验证前，Codex 自己如何提高通过概率、如何判断浏览器不卡顿且完整、失败时如何归因”整理成可执行清单。
2. 完成标准：不修改登录实现、不输入账号、不读取 token、不伪造 provider callback；清单必须按官方 external user-agent / Custom Tabs / App Links / CLI fallback 路线表达，明确哪些可自动高置信验证，哪些只能由真人账号和官方状态命令确认；回写 `PROGRESS.md` / 必要 ADR，并做文档格式检查和当前状态只读复核。
3. 前置任务：B0-B4 已完成；B5 已有 smoke、smoke watch、provider preflight、manual readiness、long-run cycle 和 status summary；当前 06:18 小时级 long-run 正在运行且 `running_with_progress`。

已完成：

- `ACCOUNT_AUTH_COMPLETION_SOP.md` 新增“人工验证作战清单”：先读 status summary / provider preflight，再进入 manual account start 或 account watch；默认走真实系统浏览器 / Custom Tabs / Chrome Auth Tab 候选能力，不把无痕、无指纹、UA 伪装或自动化隐藏作为默认路径。
- `LOGIN_TEST_STRATEGY.md` 的长期测试组合新增 T9，把作战清单纳入高置信度测试入口。
- `PLAYBOOK.md` 的 B5 验收补充该作战清单。
- `DECISIONS.md` 新增 ADR-B073，固定人工 provider 验证按分层证据和脱敏证据收口。

验证：

- `git diff --check -- docs/browser-login/ACCOUNT_AUTH_COMPLETION_SOP.md docs/browser-login/LOGIN_TEST_STRATEGY.md docs/browser-login/DECISIONS.md docs/browser-login/PLAYBOOK.md docs/browser-login/PROGRESS.md`：无格式错误；仅提示 Git 后续触碰部分文件时会做 LF/CRLF 换行转换。
- `rg -n "[ \t]+$" docs/browser-login/ACCOUNT_AUTH_COMPLETION_SOP.md docs/browser-login/LOGIN_TEST_STRATEGY.md docs/browser-login/DECISIONS.md docs/browser-login/PLAYBOOK.md docs/browser-login/PROGRESS.md`：无行尾空白匹配。
- `.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:35:58+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:39:00+08:00`、`smokeWatchProgressNextExpectedOverdue=False`、`providerPreflightStatus=ready_for_manual_provider_auth`、`providerPreflightExitCode=2`。

结论：

- 人工验证前的执行入口已经收口：先看 status summary / provider preflight，若 ready 再由 manual account start / account watch 陪跑；失败时按浏览器环境、provider 配置、账号挑战、callback/fallback、性能分层归因。
- 当前 long-run 正常运行，不需要并发启动新的 smoke/watch；B5 最终仍缺 Codex/OpenAI 与 Claude/Anthropic 真人账号完成证据。

### B5 [done] 输出长跑 progress 轮次和下一轮预计时间

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B064、ADR-B065、ADR-B070 和 ADR-B071，让账号等待期 status summary 在 `running_with_progress` 时直接说明当前长跑阶段、smoke watch 已完成轮次、剩余轮次、下一轮预计时间和 provider preflight 语义退出码。
2. 完成标准：`browser-login-status-summary.ps1` 继续只读，不启动真机测试、不输入账号、不读取 token、不伪造 provider callback；JSON、Markdown 和控制台能显示 `longRunProgress`、`smokeWatchProgress` 细节以及 `providerPreflight.exitCode`；当前 06:18 长跑仍保持 `exitCode=0` 的等待账号状态。
3. 前置任务：B0-B4 已完成；B5 已有 long-run cycle、progress JSON、provider preflight `exitCode` 和 06:18 新一轮长跑。

已完成：

- `scripts/browser-login-status-summary.ps1` 在 `longRunProgress` 中增加 `startedAt`、`smokeIterations`、`smokeIntervalSeconds`、阶段 exit、`ageSeconds/Minutes`。
- `smokeWatchProgress` 增加 `iterations`、`intervalSeconds`、`lastIteration`、`lastStatus`、`lastExitCode`、`nextAction`、`ageSeconds/Minutes`、`nextExpectedAt`、`nextSeconds/MinutesRemaining`、`nextExpectedOverdue`。
- `providerPreflight` 汇总增加 `exitCode`。
- Markdown 和控制台新增运行中 progress 摘要，后续续跑不需要手动打开多个 JSON 才知道长跑是否正常睡到下一轮。

验证：

- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查 `STATUS_SUMMARY_PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:24:13+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_with_progress`、`longRunProgressPhase=smoke_watch_started`、`smokeWatchProgressStatus=running`、`smokeWatchProgressCompleted=1`、`smokeWatchProgressRemaining=5`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:28:31+08:00`、`smokeWatchProgressNextExpectedOverdue=False`、`providerPreflightExitCode=2`。
- 跨轮只读复核：`2026-07-05T06:29:13+08:00` 输出 `smokeWatchProgressCompleted=2`、`smokeWatchProgressRemaining=4`、`smokeWatchProgressNextExpectedAt=2026-07-05T06:39:00+08:00`、`smokeCheckedAt=2026-07-05T06:28:32+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 原始 `browser-login-status-summary.json` 已写入 `smokeWatchProgress.nextExpectedAt=2026-07-05T06:39:00+08:00`、`smokeWatchProgress.nextMinutesRemaining=10`、`providerPreflight.exitCode=2`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，确认 account watch、manual account start、smoke watch、long-run cycle、manual readiness 和 provider preflight 既有分支未回归。

当前结论：

- 06:18 小时级 long-run 正在正常运行，当前第 2/6 轮 smoke 已通过，下一轮预计在 06:39 左右；当前不需要并发启动新的 smoke/watch。
- B5 仍未整体完成；最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 统一 provider preflight 退出码和时间戳

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B060、ADR-B067 和 ADR-B070，让人工 provider 验证前的总预检文件既能被人读，也能被后续自动续跑稳定读取。
2. 完成标准：`browser-login-provider-preflight.ps1` 在 JSON、Markdown 和控制台输出语义 `exitCode`；`smokeCheckedAt` / `smokeWatchCheckedAt` 以 ISO 8601 落盘；等待真实账号时仍退出 `2`，不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 已有 provider preflight、status summary、long-run cycle 和六轮 smoke watch 证据；当前缺口仍是 Codex/Claude 真实账号授权。

已完成：

- `scripts/browser-login-provider-preflight.ps1` 新增 `Get-JsonPropertyValue` / `Format-Timestamp`，避免 PowerShell 读 JSON 后把时间落成区域格式。
- `provider-auth-preflight.json` 新增顶层 `exitCode`，Markdown 和控制台同步输出。
- 脚本末尾统一 `exit $semanticExitCode`，保持 `not_ready=1`、`ready_for_manual_provider_auth/account_ready_run_post_auth=2`、完成窗口 `0`。

验证：

- PowerShell PSParser：`scripts/browser-login-provider-preflight.ps1` 语法检查 `PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:18:54+08:00` 输出 `status=ready_for_manual_provider_auth`、`exitCode=2`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex,claude`，捕获退出码 `2`。
- 原始 `provider-auth-preflight.json` 显示 `exitCode=2`、`smokeCheckedAt=2026-07-05T06:18:04+08:00`、`smokeWatchCheckedAt=2026-07-05T06:10:43+08:00`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，覆盖 provider preflight “只剩账号缺口退出 2”和“OAuth 仍在 Kite 风险退出 1”两个分支。
- `.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:19:24+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_with_progress`、`longRunNoProgressOverdueAt=2026-07-05T07:38:02+08:00`、`longRunNoProgressMinutesRemaining=79`、`smokeCheckedAt=2026-07-05T06:18:04+08:00`。

当前结论：

- 浏览器可控链路仍处于可人工验证状态；05:18 的小时级 long-run 已完成六轮，06:18 的下一轮已按计划启动并写出 progress。
- B5 仍未整体完成；最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 输出 long-run 无 progress 告警时间

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、0.1 自动续跑方式、ADR-B068 和 ADR-B069，让账号等待期 status summary 不只说“未超时”，还直接给出什么时候才应检查无 progress 长跑，减少后续续跑时手动心算。
2. 完成标准：`browser-login-status-summary.ps1` 只读输出 `noProgressOverdueAt`、`noProgressSecondsRemaining`、`noProgressMinutesRemaining`；当前 05:18 长跑未超过宽限时仍返回 `0` 并保持等待账号状态；不停止计划任务、不启动并发 smoke、不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 已有长跑计划任务、status summary、latest smoke 关联和超时判断；当前 05:18 长跑仍在运行且 latest smoke 健康。

已完成：

- `scripts/browser-login-status-summary.ps1` 根据 `currentRunStartedAt + noProgressGraceSeconds` 计算 `noProgressOverdueAt`。
- Summary JSON 新增 `longRunRuntime.noProgressOverdueAt`、`noProgressSecondsRemaining`、`noProgressMinutesRemaining`。
- Markdown/控制台新增 `longRunNoProgressOverdueAt` 和 `longRunNoProgressMinutesRemaining`。

验证：

- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查 `PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:10:15+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_without_progress_latest_smoke_after_current_run_start`、`longRunElapsedMinutes=52`、`longRunNoProgressOverdue=False`、`longRunNoProgressOverdueAt=2026-07-05T06:38:01+08:00`、`longRunNoProgressMinutesRemaining=28`、`latestSmokeAfterLongRunStart=True`、`waitingTargets=codex,claude`。
- 同轮 `browser-login-status-summary.json` 显示 `continuationTask.state=Running`、`longRunTask.state=Running`、`latestSmokeCheckedAt=2026-07-05T05:59:51+08:00`、`completionAudit.failedItemIds=codex-account,claude-account`。

当前结论：

- 当前不需要并发启动新 smoke/watch；06:38 前若仍无 progress JSON，status summary 才会把它提升为只读检查项。
- B5 仍未整体完成；最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 关联 latest smoke 与当前 long-run 启动时间

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、0.1 自动续跑方式、ADR-B065 和 ADR-B068，让账号等待期 status summary 能解释“当前长跑没有 progress JSON，但 latest smoke 已在本轮长跑窗口内刷新”的状态，帮助后续几小时自主测试判断是不是空等。
2. 完成标准：`browser-login-status-summary.ps1` 只读解析最新 smoke 的 `checkedAt`，输出 `latestSmokeAgeSeconds/Minutes`、`latestSmokeAfterCurrentRunStart` 和 `latestSmokeSecondsAfterCurrentRunStart`；当长跑运行中、无 progress、latest smoke 晚于本轮启动且未超时时，使用独立 observation；不停止计划任务、不启动并发 smoke、不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 已有 long-run cycle、status summary 和 schema 10 smoke；当前 05:18 的小时级 long-run 仍在运行，不用新的手动 smoke 干扰它。

已完成：

- `scripts/browser-login-status-summary.ps1` 新增 latest smoke 时间解析和年龄计算，并把字段写入 `longRunRuntime`。
- `longRunObservation` 新增 `running_without_progress_latest_smoke_after_current_run_start`，用于区别“旧进程无 progress 且已有新 smoke 样本”和“完全没有新样本”。
- Markdown/控制台新增 `latestSmokeAgeMinutes` 与 `latestSmokeAfterLongRunStart`。
- 同轮按官方资料再复核测试口径：Google OAuth 政策仍要求合适 client、redirect、secure browser，禁止开发者可控 embedded user-agent；Google WebView remediation 仍指向 Chrome Custom Tabs；RFC 8252 仍要求 native app 使用外部 user-agent、state/PKCE/loopback 或 claimed link；Codex/Claude CLI 官方路线仍保留浏览器登录失败时的 device code、localhost forwarding 或 paste code fallback。结论不变：测试目标是把 Kite 可控层压到高置信，不承诺账号风控 100% 放行。

验证：

- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查 `PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T06:05:11+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_without_progress_latest_smoke_after_current_run_start`、`longRunElapsedMinutes=47`、`longRunNoProgressOverdue=False`、`latestSmokeAgeMinutes=5`、`latestSmokeAfterLongRunStart=True`、`waitingTargets=codex,claude`、`smokeCheckedAt=2026-07-05T05:59:51+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 同轮 `browser-login-status-summary.json` 显示 `latestSmokeSecondsAfterCurrentRunStart=2510`、`providerPreflight.status=ready_for_manual_provider_auth`、`completionAudit.failedItemIds=codex-account,claude-account`。
- 文档回写后复验：`2026-07-05T06:07:39+08:00` 再次运行 status summary，仍输出 `longRunObservation=running_without_progress_latest_smoke_after_current_run_start`、`latestSmokeAfterLongRunStart=True`、`longRunNoProgressOverdue=False`、`waitingTargets=codex,claude`。
- `git diff --check -- docs/browser-login/... scripts/browser-login-status-summary.ps1` 无格式错误，仅提示部分 Markdown 下次触碰会 LF/CRLF 转换；`rg -n "[ \t]+$"` 扫描本轮文件无尾随空白命中。

当前结论：

- 长跑仍在合理窗口内并且已经有 05:59 的新 smoke 样本；目前不并发启动新的 long-run/smoke。
- latest smoke 只能证明长跑窗口内有浏览器健康样本，不单独证明该样本一定由当前长跑写入，更不能替代 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 增强状态汇总识别长跑无 progress 超时

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、0.1 自动续跑方式、ADR-B064 和 ADR-B065，让账号等待期 status summary 不仅能解释“旧进程无 progress”，也能在长跑超过合理时间后提示检查，避免未来 24 小时巡检卡死却仍被误判为正常运行。
2. 完成标准：`browser-login-status-summary.ps1` 只读解析 `KiteBrowserLoginLongRunCycle` 计划任务动作中的 `SmokeIterations` / `SmokeIntervalSeconds`，输出当前长跑开始时间、运行分钟数、宽限秒数和 `noProgressOverdue`；当前未超时不得误报；超过宽限时状态进入 `long_run_running_without_progress_overdue` 并退出 `1`；不停止计划任务、不启动并发 smoke、不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 已有 long-run cycle 和 status summary；当前 05:18 的小时级 long-run 正在运行，不能用新的手动 long-run 干扰它。

已完成：

- `scripts/browser-login-status-summary.ps1` 新增 `Convert-ToDateTimeOffsetOrNull` 与 `Get-ActionIntArgument`。
- Summary JSON 新增 `longRunRuntime` 字段：`running`、`currentRunStartedAt`、`currentRunElapsedSeconds`、`currentRunElapsedMinutes`、`scheduledSmokeIterations`、`scheduledSmokeIntervalSeconds`、`noProgressGraceSeconds`、`noProgressOverdue`。
- Markdown/控制台新增 `longRunElapsedMinutes` 和 `longRunNoProgressOverdue`，便于续跑第一眼判断“运行中正常”还是“运行中疑似卡住”。
- 超时判断使用保守宽限：`(SmokeIterations - 1) * SmokeIntervalSeconds + 1800`。当前计划任务为 `6` 轮、`600` 秒间隔，因此无 progress 宽限为 `4800` 秒；这只用于只读告警，不会杀进程或重启任务。

验证：

- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查 `PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:59:18+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`longRunObservation=running_without_progress_from_pre_progress_script`、`longRunElapsedMinutes=41`、`longRunNoProgressOverdue=False`、`waitingTargets=codex,claude`、`smokeCheckedAt=2026-07-05T05:49:25+08:00`。
- 同轮 `browser-login-status-summary.json` 显示 `longRunRuntime.currentRunStartedAt=2026-07-05T05:18:01+08:00`、`scheduledSmokeIterations=6`、`scheduledSmokeIntervalSeconds=600`、`noProgressGraceSeconds=4800`、`noProgressOverdue=false`。

当前结论：

- 05:18 开始的小时级长跑仍在合理窗口内，不能并发启动新的 smoke/watch；继续等待它自然完成或下一轮计划任务刷新 progress。
- B5 仍未整体完成；最终缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 记录 05:50 续跑状态并统一状态汇总时间戳

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B065 和 ADR-B066，在不干扰小时级 long-run 的前提下，继续对账最新 runner/status summary/smoke 样本，并修正状态汇总控制台时间格式，降低后续长跑误读。
2. 完成标准：只读读取当前计划任务、runner 和 latest smoke；如果有新样本则回写进度和兼容矩阵；`browser-login-status-summary.ps1` 的任务时间和状态文件 `checkedAt` 输出统一为 ISO 8601；不启动并发 smoke、不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 处于账号等待期；`KiteBrowserLoginLongRunCycle` 正在运行，不能用新的手动 smoke 干扰当前长跑。

已完成：

- 当前本机时间：`2026-07-05T05:50:50+08:00`。
- `browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:50:52+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_without_progress_from_pre_progress_script`、`waitingTargets=codex,claude`。
- `KiteBrowserLoginContinuationGate` 05:50 轮已完成：`runner-status.json checkedAt=2026-07-05T05:50:15+08:00`、`exitCode=2`、`nextAction=wait_for_real_account_authorization`、`readyTargets=[]`、`waitingTargets=codex,claude`。
- `KiteBrowserLoginLongRunCycle` 仍为 `Running`，`LastRunTime=2026-07-05T05:18:01+08:00`、`LastTaskResult=267009`、`NextRunTime=2026-07-05T06:18:01+08:00`；当前长跑进程仍按旧进程缺少 progress JSON 解释。
- 长跑中最新单轮 smoke 已刷新：`browser-login-smoke.json checkedAt=2026-07-05T05:49:25+08:00`、`status=passed`、`schemaVersion=10`、`openWebElapsedMs=19`、`foregroundHandoffElapsedMs=1738`、`providerPageSignalState=challenge_or_login_visible`、`providerPageBlockingErrorCount=0`、`providerPageChallengeHintCount=2`、`appRedirectStatus=Delivered`、`appRedirectRawSecretHitCount=0`、`appPrivateRawTemporaryValueHitCount=0`。
- 同轮 OpenAI/Codex 与 Claude OAuth 形态均通过外部浏览器分流：OpenAI `handoffForegroundElapsedMs=1919`、Claude `handoffForegroundElapsedMs=1700`，前台均为 `com.heytap.browser/com.android.browser.BrowserActivity`，均未新增假 browser auth session。
- `scripts/browser-login-status-summary.ps1` 新增 `Format-Timestamp`，任务快照、runner/long-run/smoke/smoke watch/manual readiness/completion audit/provider preflight 的 `checkedAt` 字段和控制台 `smokeCheckedAt` 均统一输出为 ISO 8601。

验证：

- 本轮状态复核只读取现有状态文件和计划任务；未启动新的 smoke 或 long-run。
- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查通过。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:54:06+08:00` 输出 `smokeCheckedAt=2026-07-05T05:49:25+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`，确认控制台时间戳已固定为 ISO。

当前结论：

- 05:49 新 smoke 样本继续证明 Kite 可控浏览器链路健康：外部浏览器分流正常、provider 页面无阻塞错误、AppRedirect 已交付、敏感临时值原文命中为 0。
- 这仍不是账号完成证据；B5 继续等待 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 增强状态汇总读取最新单轮 smoke

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、ADR-B064 和 ADR-B065，减少长跑运行中判断状态时需要手动拼多个 JSON 的问题，让 `browser-login-status-summary.ps1` 直接暴露最新单轮 smoke 的关键健康字段。
2. 完成标准：状态汇总只读读取 `browser-login-smoke.json`，在 JSON/Markdown/控制台输出 latest smoke 的时间、状态、schema、provider 阻塞错误数和敏感临时值命中数；不启动真机测试、不输入账号、不读取 token、不伪造 callback；保留原有 runner/long-run/progress 判断。
3. 前置任务：B0-B4 已完成；B5 已有 smoke、smoke watch、long-run 和 provider preflight；当前小时级 long-run 仍在运行旧进程，不能并发启动新测试。

已完成：

- `scripts/browser-login-status-summary.ps1` 新增读取 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.json`，并在 summary JSON 的 `smoke` 字段写入 `checkedAt`、`status`、`schemaVersion`、`openWebElapsedMs`、`foregroundHandoffElapsedMs`、`providerPageSignalState`、`providerPageBlockingErrorCount`、`providerPageChallengeHintCount`、`appRedirectStatus`、`appRedirectRawSecretHitCount`、`appPrivateRawTemporaryValueHitCount` 和文件路径/写入时间。
- Markdown 报告 `browser-login-status-summary.md` 的关键文件区新增 `smoke：... providerBlocking=... secretHits=...`。
- 控制台输出新增 `smokeCheckedAt`、`smokeStatus`、`smokeProviderBlockingErrorCount`，方便外部唤醒后第一眼判断长跑中最新浏览器样本。

验证：

- PowerShell PSParser：`scripts/browser-login-status-summary.ps1` 语法检查 `PARSER_OK`。
- 真实只读运行：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:46:31+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`waitingTargets=codex,claude`、`smokeCheckedAt=2026-07-05T05:38:56+08:00`、`smokeStatus=passed`、`smokeProviderBlockingErrorCount=0`。
- 同轮 summary JSON 的 `smoke` 字段显示 `schemaVersion=10`、`providerPageSignalState=challenge_or_login_visible`、`appRedirectStatus=Delivered`、`appRedirectRawSecretHitCount=0`、`appPrivateRawTemporaryValueHitCount=0`。

当前结论：

- 后续续跑时可以只先看 status summary，就能同时知道账号 gate、long-run 运行状态和最近一次无账号浏览器健康样本。
- 这仍不是账号完成证据；B5 继续等待 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 记录 05:40 续跑状态和长跑中 smoke 样本

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、0.1 自动续跑方式和 ADR-B064/ADR-B065，在不干扰小时级 long-run 的前提下，读取当前计划任务、runner、status summary 和正在刷新的 smoke 证据，判断是否出现账号 ready、环境错误或新的浏览器稳定性样本。
2. 完成标准：只读检查 `KiteBrowserLoginContinuationGate`、`KiteBrowserLoginLongRunCycle`、`runner-status.json`、`browser-login-status-summary.json` 和最新 `browser-login-smoke.json`；有新证据就回写文档；不启动并发 smoke、不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 当前进入账号等待期；小时级 long-run 由计划任务运行，5 分钟账号 gate 负责 ready 后接 post-auth。

已完成：

- 当前本机时间：`2026-07-05T05:40:51+08:00`。
- `KiteBrowserLoginContinuationGate` 05:40 轮已完成：`runner-status.json checkedAt=2026-07-05T05:40:16+08:00`、`exitCode=2`、`nextAction=wait_for_real_account_authorization`、`waitingTargets=codex,claude`、`readyTargets=[]`。
- `browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:40:53+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_without_progress_from_pre_progress_script`、`waitingTargets=codex,claude`。
- `KiteBrowserLoginLongRunCycle` 仍为 `Running`，`LastRunTime=2026/7/5 5:18:01`、`LastTaskResult=267009`、`NextRunTime=2026/7/5 6:18:01`；当前进程早于 progress JSON 改动启动，所以 progress 文件缺失仍按旧进程运行解释。
- 长跑中最新单轮 smoke 已刷新：`browser-login-smoke.json checkedAt=2026-07-05T05:38:56+08:00`、`status=passed`、`schemaVersion=10`、`openWebElapsedMs=19`、`foregroundHandoffElapsedMs=1654`、`providerPageSignalState=challenge_or_login_visible`、`providerPageBlockingErrorCount=0`、`providerPageChallengeHintCount=2`、`appRedirectStatus=Delivered`、`appRedirectRawSecretHitCount=0`、`appPrivateRawTemporaryValueHitCount=0`。
- 同轮 OpenAI/Codex 与 Claude OAuth 形态均通过外部浏览器分流：OpenAI `handoffForegroundElapsedMs=1812`、Claude `handoffForegroundElapsedMs=1749`，前台均为 `com.heytap.browser/com.android.browser.BrowserActivity`，均未新增假完成证据。
- 只读 provider preflight 已读取最新 smoke 重新归桶：`checkedAt=2026-07-05T05:42:35+08:00`、`status=ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex,claude`、`smokeCheckedAt=2026-07-05T05:38:56+08:00`、`providerPageBlockingErrorCount=0`、`completionAuditFailedItemIds=codex-account,claude-account`。

验证：

- 本轮未启动新的真机 smoke 或 long-run，只读取计划任务和状态文件。
- Provider preflight 未带 `-RunSmokeTest`、`-RunSmokeWatch`、`-RefreshReadiness` 或 `-RunCompletionAudit`，只读取现有状态并写出 `provider-auth-preflight.json` / `.md`。
- OnePlus 8T `3f8bbaad` 在线；MEIZU 同时在线但未用于浏览器线。

当前结论：

- 浏览器可控链路在 05:38 的长跑中样本仍健康；没有 provider 页面阻塞错误、没有 AppRedirect 交付失败、没有敏感临时值原文落盘。
- B5 仍未整体完成；继续等待 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 补充人工验证通过率置信模型

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和 `LOGIN_TEST_STRATEGY.md`，把用户提出的“我也要主动想怎么测、怎么提高人工 Google/OpenAI/Claude 验证通过概率、浏览器如何更快更完整”落成可恢复的测试策略。
2. 完成标准：区分 Kite 能自动接近确定的机制、只能高置信趋势判断的环境和必须由真人账号完成的外部变量；给出多种测试方法而不是纠结单一 smoke；不输入账号、不读取 token、不伪造 provider callback；不干扰正在运行的小时级 long-run。
3. 前置任务：B0-B4 已完成；B5 smoke、smoke watch、provider preflight、manual readiness、completion audit 和 status summary 均已存在；当前 `KiteBrowserLoginLongRunCycle` 正在运行，文档更新不并发启动新真机测试。

已完成：

- 通过网络复核 Google OAuth policy、WebView remediation、RFC 8252、Google installed apps、loopback migration、AppAuth-Android、Chrome Auth Tab / warmup、OpenAI Codex auth 和 Claude Code auth 文档，结论仍是外部 user-agent、正确 client/redirect、PKCE/state、官方 CLI fallback 和真人账号交互。
- `LOGIN_TEST_STRATEGY.md` 新增“通过率置信模型”：把浏览器合规、provider 配置早期错误、网络和性能、回跳/CLI callback、敏感边界、账号所有权拆开判断。
- `LOGIN_TEST_STRATEGY.md` 新增体验/性能检查点：冷/热启动、浏览器能力完整性、人工验证陪跑截图边界。
- `LOGIN_TEST_STRATEGY.md` 新增“后续自主测试实验池”：长跑状态对账、冷/热启动性能采样、默认浏览器漂移、Auth Tab / warmup 可行性、CLI fallback 明确性和人工失败归因复盘。
- `DECISIONS.md` 新增 ADR-B066，固定“人工验证通过率按分层置信度管理”，避免把浏览器链路健康误写成账号已通过。

验证：

- 本轮只读状态汇总：`.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 在 `2026-07-05T05:36:34+08:00` 输出 `status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`waitingTargets=codex,claude`。
- 没有启动新的 smoke 或 long-run；不干扰当前小时级计划任务。

当前结论：

- 后续几个小时的自主测试方向不再只是一条 smoke watch，而是先看状态对账和长跑进度，再按分层指标补性能、handler、provider 页面信号、callback/fallback 和失败归因。
- B5 仍未整体完成；最终强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 新增浏览器线当前状态只读汇总

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和自动续跑方式，在不干扰正在运行的小时级 long-run cycle 的前提下，提供一个只读汇总入口，把计划任务、runner、long-run、smoke watch、manual readiness、completion audit 和 progress 文件状态归到一个可复查状态文件。
2. 完成标准：新增脚本能读取 `KiteBrowserLoginContinuationGate`、`KiteBrowserLoginLongRunCycle` 和 `%LOCALAPPDATA%\Kite\browser-login-continuation` 里的状态文件；在账号仍等待时输出 `waiting_for_real_account_authorization` 或 `long_run_running` 等明确状态；能识别当前这种“旧 long-run 进程运行中但 progress 文件缺失”的情况；写出 JSON/Markdown；不启动真机测试、不输入账号、不读取 token、不伪造 callback。
3. 前置任务：B0-B4 已完成；B5 runner、long-run、completion audit 和计划任务均已存在；当前 05:18 启动的小时级 long-run 仍在运行，不能并发抢设备跑新 smoke。

已完成：

- 新增 `scripts/browser-login-status-summary.ps1`。它读取 `KiteBrowserLoginContinuationGate`、`KiteBrowserLoginLongRunCycle`、`runner-status.json`、`browser-login-long-run-cycle.json`、`browser-login-long-run-cycle-progress.json`、`browser-login-smoke-watch.json`、`browser-login-smoke-watch-progress.json`、`manual-account-readiness.json`、`completion-audit.json` 和 `provider-auth-preflight.json`，写出 `browser-login-status-summary.json` / `.md`。
- 真实运行 `.\scripts\browser-login-status-summary.ps1 -Serial 3f8bbaad` 输出 `checkedAt=2026-07-05T05:34:19+08:00`、`status=long_run_running_waiting_for_real_account_authorization`、`exitCode=0`、`longRunObservation=running_without_progress_from_pre_progress_script`、`waitingTargets=codex,claude`。
- 同轮计划任务状态：`KiteBrowserLoginContinuationGate` 为 `Ready`，`LastRunTime=2026-07-05T05:30:13+08:00`，`LastTaskResult=2`，`NextRunTime=2026-07-05T05:35:12+08:00`；`KiteBrowserLoginLongRunCycle` 为 `Running`，`LastRunTime=2026-07-05T05:18:01+08:00`，`LastTaskResult=267009`，`NextRunTime=2026-07-05T06:18:01+08:00`。
- 当前 long-run 观测结论为 `running_without_progress_from_pre_progress_script`，因为 05:18 启动的长跑进程早于 progress JSON 改动；这不是失败。该进程仍通过最新 smoke 文件可见：`browser-login-smoke.json` 在 `2026-07-05T05:28:56+08:00` 被刷新。

验证：

- PSParser：`scripts/browser-login-status-summary.ps1` 为 `PARSER_OK`。
- 状态汇总脚本退出码 `0`；账号等待不被当作环境错误。

当前结论：

- 后续会话可以先读 `browser-login-status-summary.json` 判断是否值得继续补证、等待长跑结束，或进入账号人工验证。
- B5 仍未整体完成；剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 增强 long-run 运行中可观测状态

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5、0.1 自动续跑方式和 ADR-B063，在不改变账号边界的前提下，让小时级 long-run cycle 和多轮 smoke watch 在运行过程中也能留下只读进度状态，便于后续会话判断当前是正在跑、卡在第几轮，还是已经结束。
2. 完成标准：`browser-login-smoke-watch.ps1` 每轮写出 progress JSON，包含总轮数、已完成轮数、剩余轮数、失败数和最后一轮状态；`browser-login-long-run-cycle.ps1` 写出 progress JSON，标明 runner、smoke watch、manual readiness、completion audit 或 finished 阶段；mock 自测试覆盖两个 progress 文件；不输入账号、不读取 token、不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 long-run cycle、smoke watch、计划任务和完成审计均已存在；当前小时级计划任务正在运行，脚本修改只影响后续调用，不替换正在运行的进程；真实账号 N4/N5 仍需 Codex/OpenAI 与 Claude/Anthropic 官方状态证据。

已完成：

- `scripts/browser-login-smoke-watch.ps1` 新增 `browser-login-smoke-watch-progress.json`：脚本启动时写入 `status=running`，每轮 smoke 后刷新 `completedIterations`、`remainingIterations`、`failureCount`、最后一轮状态和下一步动作；最终写入 `status=passed` 或 `failed`。
- `scripts/browser-login-long-run-cycle.ps1` 新增 `browser-login-long-run-cycle-progress.json`：阶段切换时刷新 `phase=runner_started/runner_completed/smoke_watch_started/smoke_watch_completed/manual_readiness_started/manual_readiness_completed/completion_audit_started/completion_audit_completed/finished`，并记录各阶段 exit code 和输出文件路径。
- `scripts/test-browser-login-continuation.ps1` 已增加 mock 断言：smoke watch 必须写 progress 且最终 `completedIterations=2`、`remainingIterations=0`；long-run cycle 必须写 progress 且最终 `phase=finished`、`status=waiting_account_browser_stable`、`smokeWatchExit=0`。
- 当前 05:18 启动的小时级 `KiteBrowserLoginLongRunCycle` 仍在运行，已经启动的进程使用的是启动时读取的脚本；新增 progress 文件会在后续调用或下一轮计划任务中产生，不伪造当前运行进度。

验证：

- PowerShell PSParser：`browser-login-smoke-watch.ps1`、`browser-login-long-run-cycle.ps1`、`test-browser-login-continuation.ps1` 均 `PARSER_OK`。
- `git diff --check -- scripts/browser-login-smoke-watch.ps1 scripts/browser-login-long-run-cycle.ps1 scripts/test-browser-login-continuation.ps1 docs/browser-login/PROGRESS.md`：无格式错误。
- 尾随空白检查：无命中。
- `.\scripts\test-browser-login-continuation.ps1`：`browser-login-continuation-self-test passed`，新增 progress 断言全部通过。

当前结论：

- 小时级长跑后续不再只能等最终 JSON；中途也能通过 progress JSON 判断运行阶段和轮次进度。
- 该改动只增强可观测性，不改变账号验证边界；B5 仍未整体完成，剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 注册小时级 long-run cycle 外部调度

三问自检：

1. 目标：引用 `PLAYBOOK.md` B5 和 ADR-B059，在账号等待期把浏览器稳定性 long-run cycle 从手动运行补成小时级外部调度；现有 `KiteBrowserLoginContinuationGate` 继续负责 5 分钟账号 ready/post-auth 门槛，不被替换。
2. 完成标准：注册 `KiteBrowserLoginLongRunCycle` Windows 计划任务；动作指向本副本 `scripts/browser-login-long-run-cycle.ps1`，serial 为 `3f8bbaad`，stateDir 为浏览器线独立状态目录；间隔不低于 15 分钟且本轮采用 60 分钟；长巡检参数为 `SmokeIterations=6`、`SmokeIntervalSeconds=600`，用于小时级稳定性观察；注册后能通过 `Get-ScheduledTask` 读取任务。
3. 前置任务：B0-B4 已完成；B5 long-run cycle 和注册脚本已存在并通过 PSParser；本轮状态检查确认 `KiteBrowserLoginContinuationGate` 已存在但 `KiteBrowserLoginLongRunCycle` 尚未注册；真实账号 N4/N5 仍需 Codex/OpenAI 与 Claude/Anthropic 官方状态证据。

已完成：

- 已运行 `.\scripts\register-browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -Minutes 60 -Days 1 -SmokeIterations 6 -SmokeIntervalSeconds 600`，输出 `registeredTask=KiteBrowserLoginLongRunCycle`、`intervalMinutes=60`、`durationDays=1`、`serial=3f8bbaad`、`smokeIterations=6`、`smokeIntervalSeconds=600`。
- `Get-ScheduledTask` / `Get-ScheduledTaskInfo` 复验：`KiteBrowserLoginLongRunCycle` 动作调用 `D:\xm\Kite-browser-login\scripts\browser-login-long-run-cycle.ps1`，绑定 `Serial "3f8bbaad"` 和独立状态目录 `%LOCALAPPDATA%\Kite\browser-login-continuation`；触发器 `PT1H` / `P1D`；`LastRunTime=2026/7/5 5:18:01`，`LastTaskResult=267009` 表示正在运行，`NextRunTime=2026/7/5 6:18:01`。
- 已修改 `scripts/browser-login-completion-audit.ps1`，新增 `scheduled-long-run-cycle-task` 审计项：检查任务启用、动作指向本副本 long-run cycle、包含 OnePlus 8T serial、使用 `SmokeIterations=6` / `SmokeIntervalSeconds=600`、重复间隔在 15 到 60 分钟之间且持续至少 1 天。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T05:18:21+08:00`、`status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`；新增 `scheduled-long-run-cycle-task` 通过，证据为 `state=Running`、`lastResult=267009`、`nextRun=07/05/2026 06:18:01`、`actionOk=True`、`intervalOk=True`、`durationOk=True`。

当前结论：

- 5 分钟 `KiteBrowserLoginContinuationGate` 继续负责账号 ready 后立即接 post-auth；小时级 `KiteBrowserLoginLongRunCycle` 单独负责账号等待期间的浏览器稳定性、性能和趋势巡检。
- 这两条计划任务不共享目录之外的测试状态、不触碰 X11/MEIZU、不输入账号、不读取 token、不伪造 provider callback。
- B5 仍未整体完成；完成审计目前只剩 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 刷新账号等待期巡检证据

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5、账号等待期 long-run cycle 和 completion audit SOP，在真实账号授权尚未完成时，继续刷新 OnePlus 8T 上的浏览器可控链路、长期趋势、人工准备度和完成审计证据。
2. 完成标准：当前目录仍为 `D:\xm\Kite-browser-login`；OnePlus 8T `3f8bbaad` 在线；runner 没有环境错误；3 轮 smoke watch 无失败、p95 不超阈值、provider 页面阻塞错误趋势为 0；manual readiness 为可人工验证窗口；completion audit 只剩 `codex-account` / `claude-account` 或转为 complete；provider preflight 不出现账号以外阻塞桶。
3. 前置任务：B0-B4 已完成；B5 smoke schema 10、smoke watch、long-run cycle、manual readiness、provider preflight 和 completion audit 均已存在；真实账号 N4/N5 仍需 Codex/OpenAI 与 Claude/Anthropic 官方状态证据，本任务不能替代该缺口。

已验证：

- 当前目录仍为 `D:\xm\Kite-browser-login`，分支 `codex/browser-login-return`；OnePlus 8T `3f8bbaad` 在线，MEIZU/X11 设备未使用。
- 进入本轮前 runner 状态为 `checkedAt=2026-07-05T05:05:15+08:00`、`exitCode=2`、`nextAction=wait_for_real_account_authorization`、`waitingTargets=codex,claude`，没有 ready/error targets。
- `.\scripts\browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 3 -SmokeIntervalSeconds 0` 输出 `checkedAt=2026-07-05T05:11:49+08:00`、`status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`、`waitingTargets=codex,claude`。
- 同轮 smoke watch：`checkedAt=2026-07-05T05:11:47+08:00`、`status=passed`、`iterations=3`、`failureCount=0`、`openWebP95Ms=46/1500`、`foregroundP95Ms=1801/5000`、`handlerPackages=com.heytap.browser`、`handlerStable=True`、`providerSessionLeakRunCount=0`、`providerPageBlockingErrorRunCount=0`、`secretLeakRunCount=0`。
- 同轮 smoke 最近样本：`checkedAt=2026-07-05T05:11:21+08:00`、`schemaVersion=10`、`providerPageSignalState=challenge_or_login_visible`、`providerPageBlockingErrorCount=0`、`providerPageChallengeHintCount=2`、`providerOAuthForegroundMaxElapsedMs=1544`、普通 localhost `17ms`、Google `/open-web` `12ms`、App redirect `/open-web` `46ms`、`appRedirectStatus=Delivered`、`appPrivateRawTemporaryValueHitCount=0`。
- 同轮 manual readiness：`checkedAt=2026-07-05T05:11:49+08:00`、`status=ready_for_manual_account`、`failedItemIds=[]`、`waitingTargets=codex,claude`。
- `.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` 输出 `checkedAt=2026-07-05T05:11:58+08:00`、`status=ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex,claude`、`providerPageBlockingErrorCount=0`、语义退出码 `2`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T05:12:05+08:00`、`status=incomplete`、`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`、`failedItemIds=codex-account,claude-account`。

当前结论：

- 账号等待期间浏览器可控链路继续健康，完成审计没有出现账号以外的新缺口。
- B5 仍未整体完成；剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 复核官方要求与人工验证前测试设计

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md`，在真实账号授权仍未完成时，复核官方网页登录要求，并把人工验证前可自动提高置信度的测试方法、性能观察项和剩余缺口写清楚。
2. 完成标准：官方资料复核不改变当前主路线；测试策略明确哪些部分可接近确定性验证、哪些仍必须人工账号完成；补充浏览器效率、完整性和卡顿风险的观测方法；不触碰 X11，不输入账号，不读取 token，不伪造 provider callback。
3. 前置任务：B0-B4 已完成；B5 已有 schema 10 smoke、smoke watch、provider preflight、manual readiness、completion audit 和 long-run cycle；真实账号授权仍缺 Codex/OpenAI 与 Claude/Anthropic 官方状态证据。

已完成：

- 复核官方资料后，当前主路线不变：Google OAuth / 原生 App 授权继续以外部 user-agent、PKCE、匹配 redirect 和真实账号交互为主；Codex/Claude CLI 继续把 localhost callback 与 device-code / paste-code fallback 当作一等路径。
- `docs/browser-login/LOGIN_TEST_STRATEGY.md` 已补充浏览器能力盘点、端到端分段计时、交互完整性、会话策略，以及长期自测观测矩阵。
- `docs/browser-login/DECISIONS.md` 新增 ADR-B062，固定人工验证前用分段指标判断浏览器效率和完整性，不把单一“页面打开成功”当作账号通过。
- 校验过程中确认 `scripts/browser-login-long-run-cycle.ps1` 的结构化状态仍以 JSON `exitCode=2` / runner 语义码为准；当前 shell 工具会把任意非零进程退出显示为 `1`，不表示脚本语义状态丢失。

当前结论：

- 能自动接近确定性验证的范围是 Kite 可控链路：URL 分类、WebView 分流、外部浏览器前台、同 state 回跳、普通 localhost 不误跳、敏感值不落盘、runner/watch/audit 状态流和分段性能指标。
- 不能自动承诺 100% 的范围仍是 provider 账号层：验证码、MFA、风控、OAuth client 配置、真实 Codex/Claude 账号授权和未来策略变化。

### B5 [done] 刷新账号等待期 long-run cycle 与完成审计

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和账号等待期 SOP，在真实账号授权尚未完成时，继续验证浏览器线可控链路没有退化，并刷新完成审计的单测、构建、runner 和设备状态证据。
2. 完成标准：OnePlus 8T `3f8bbaad` 在线；`browser-login-long-run-cycle.ps1` 的 runner/smoke watch/manual readiness 均健康；3 轮 smoke watch 无失败、p95 不超阈值、provider 页面阻塞错误趋势为 0；`browser-login-completion-audit.ps1 -RefreshState` 只剩 `codex-account` / `claude-account`；provider preflight 仍为 `ready_for_manual_provider_auth`。
3. 前置任务：B0-B4 已完成；B5 schema 10 smoke、provider preflight、completion audit 和 long-run cycle 已存在；真实账号授权仍缺 Codex/OpenAI 与 Claude/Anthropic 官方状态证据，本任务不能替代该缺口。

已验证：

- 当前目录仍为 `D:\xm\Kite-browser-login`，分支 `codex/browser-login-return`；OnePlus 8T `3f8bbaad` 在线，MEIZU/X11 设备未使用。
- `.\scripts\browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 3 -SmokeIntervalSeconds 0` 写出结构化状态 `checkedAt=2026-07-05T05:00:38+08:00`、`status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`、`waitingTargets=codex,claude`。
- 同轮 smoke watch：`checkedAt=2026-07-05T05:00:37+08:00`、`status=passed`、`iterations=3`、`failureCount=0`、`openWebP95Ms=85/1500`、`foregroundP95Ms=1758/5000`、`handlerPackages=com.heytap.browser`、`providerSessionLeakRunCount=0`、`providerPageBlockingErrorRunCount=0`、`secretLeakRunCount=0`。
- 同轮 manual readiness：`checkedAt=2026-07-05T05:00:38+08:00`、`status=ready_for_manual_account`、`failedItemIds=[]`、`waitingTargets=codex,claude`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T05:01:10+08:00`、`status=incomplete`、`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`、`failedItemIds=codex-account,claude-account`。
- `.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` 输出 `checkedAt=2026-07-05T05:01:24+08:00`、`status=ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex,claude`、语义退出码 `2`。

当前结论：

- 账号等待期间浏览器可控链路仍健康，完成审计没有出现账号以外的新缺口。
- 目标 B5 仍未整体完成，剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [done] 补齐 provider 页面阻塞错误信号

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的人工账号验证前测试要求，在不输入账号、不伪造 callback 的前提下，让真机 smoke 不只确认“离开 WebView”，还确认外部浏览器页面没有出现 `disallowed_useragent`、`redirect_uri_mismatch`、`invalid_client` 等阻塞性错误信号。
2. 完成标准：`browser-login-smoke-test.ps1` 提升 schema，新增 provider 页面错误信号 item 和结构化字段；completion audit / provider preflight 读取该证据；mock 自测试按新版 schema 通过；OnePlus 8T 真机短验证或 smoke 运行通过；三件套和测试策略回写完成。
3. 前置任务：B0-B4 已完成；B5 已有 smoke schema 9、provider preflight、completion audit 和长期 watch；真实账号授权仍缺 Codex/OpenAI 与 Claude/Anthropic 官方状态证据，本任务不能替代该缺口。

当前状态：

- 已完成。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=10`，新增 `provider-page-no-blocking-error` 检查项和 `providerPageSignalState`、`providerPageBlockingErrorCount`、`providerPageBlockingErrorMatches`、`providerPageChallengeHintCount`、`providerPageChallengeHintMatches` 字段。
- `scripts/browser-login-smoke-watch.ps1` 汇总每轮 provider 页面信号，并输出 `providerPageBlockingErrorRunCount`。
- `scripts/browser-login-provider-preflight.ps1` 和 `scripts/browser-login-completion-audit.ps1` 已读取 schema 10 provider 页面信号；preflight 把阻塞页面归入 `browser_environment` 或 `provider_configuration` 的下一步排查入口。
- `scripts/browser-login-long-run-cycle.ps1`、`scripts/test-browser-login-continuation.ps1` 已同步新版 smoke watch 字段；mock 自测试覆盖 schema 10 等待账号路径。
- 修正 `browser-login-provider-preflight.ps1` 证据字符串中 PowerShell `$name:` 插值导致 host/id 丢失的小问题。

已验证：

- PowerShell PSParser 通过：`browser-login-smoke-test.ps1`、`browser-login-smoke-watch.ps1`、`browser-login-provider-preflight.ps1`、`browser-login-completion-audit.ps1`、`browser-login-long-run-cycle.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过。
- OnePlus 8T 单轮 smoke：`.\scripts\browser-login-smoke-test.ps1 -Serial 3f8bbaad` 输出 `checkedAt=2026-07-05T04:49:02+08:00`、`status=passed`、`schemaVersion=10`、`providerPageSignalState=challenge_or_login_visible`、`providerPageBlockingErrorCount=0`、`providerPageChallengeHintCount=2`、`providerOAuthNewSessionCount=0`、`appRedirectStatus=Delivered`、`appPrivateRawTemporaryValueHitCount=0`。
- OnePlus 8T 三轮 smoke watch：`.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 3 -IntervalSeconds 0` 输出 `checkedAt=2026-07-05T04:49:34+08:00`、`status=passed`、`iterations=3`、`failureCount=0`、`openWebP95Ms=79/1500`、`foregroundP95Ms=1815/5000`、`handlerPackages=com.heytap.browser`、`providerPageBlockingErrorRunCount=0`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`。
- provider preflight：`.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` 输出 `checkedAt=2026-07-05T04:50:43+08:00`、`status=ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex,claude`、语义退出码 `2`。
- completion audit：`.\scripts\browser-login-completion-audit.ps1` 输出 `checkedAt=2026-07-05T04:57:34+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`、语义退出码 `2`。

当前结论：

- 人工 Google / OpenAI / Claude 验证前，Kite 可自动排除的浏览器环境、provider 页面阻塞错误、redirect 类型、敏感边界和性能问题均已纳入门槛。
- 目标 B5 仍未整体完成，剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [in_progress] 补齐人工 provider 验证前综合预检

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的人工账号验证前测试组合，把 Google / OpenAI / Claude 人工验证前的官方合规、浏览器环境、redirect 类型、CLI fallback、性能和泄漏边界汇总成一份只读预检状态。
2. 完成标准：新增预检不输入账号、不读取 token、不伪造 callback；能读取或可选刷新 smoke、smoke watch、runner、manual readiness 和 completion audit；能把失败归到 `browser_environment`、`provider_configuration`、`account_challenge`、`cli_callback_or_fallback`、`sensitive_boundary`、`performance` 或 `post_auth` 等桶；输出 JSON/Markdown；文档和三件套回写；至少通过 PowerShell 解析与一次 OnePlus 8T 真实状态运行。
3. 前置任务：B0-B4 已完成；B5 已有 smoke、smoke watch、runner、account watch、manual readiness、completion audit 和 long-run cycle；真实账号授权仍缺 Codex/OpenAI 与 Claude/Anthropic 官方状态证据，本任务不能替代该缺口。

当前状态：

- 已实现 `scripts/browser-login-provider-preflight.ps1`：读取当前 state dir，输出 `provider-auth-preflight.json` / `.md`；可选刷新 runner、smoke、smoke watch、manual readiness 和 completion audit；不输入账号、不读取 token、不伪造 callback。
- 预检会检查：smoke schema 10 与失败项、授权主机 HTTPS attempts、外部浏览器 handler、provider 页面阻塞错误信号、Google/OpenAI/Claude OAuth 形态分流、第三方 HTTPS redirect 与 `kite-auth://callback` 边界、callback/临时值脱敏、多轮 smoke watch p95、manual readiness、CLI 登录入口/watch 状态、runner 状态和 completion audit 是否只剩账号失败。
- `scripts/test-browser-login-continuation.ps1` 已纳入 provider preflight mock 分支：只剩账号缺口时输出 `ready_for_manual_provider_auth` / 退出 `2`；OAuth 仍停在 Kite 时输出 `not_ready` 并归因 `browser_environment`。

已验证：

- PowerShell PSParser 通过：`browser-login-provider-preflight.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增断言包含 `provider preflight exits 2 when only accounts wait`、`provider preflight records manual provider auth readiness`、`provider preflight exits 1 when OAuth remains in Kite` 和 `provider preflight classifies embedded browser risk`。
- 真实状态运行：`.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` 输出 `status=ready_for_manual_provider_auth`、`blockingFailureIds=(none)`、`failedBuckets=(none)`、`waitingTargets=codex, claude`、`readyTargets=(none)`、`verifiedTargets=(none)`、语义退出码 `2`。

当前结论：

- 人工 Google / OpenAI / Claude 验证前现在有一个总预检入口，可以把“浏览器/redirect/性能/敏感边界/账号状态”一次性归因。
- 目标仍未完成，剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 的真实账号授权完成证据。

### B5 [in_progress] 补齐账号等待期 long-run cycle

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的长期自主测试要求，在真实账号授权尚未完成时，把“账号门槛检查 + 无账号 smoke watch + readiness 汇总”封装成可被外部调度器反复调用的一次性 cycle。
2. 完成标准：新增脚本不输入账号、不读取 token、不伪造 callback；等待账号时能运行 runner、smoke watch 和 manual readiness，并写入结构化 long-run 状态；账号 ready 时能触发 completion audit 入口；提供计划任务注册脚本但不强迫替换现有 runner 计划任务；PowerShell 解析、自测或真机短验证、文档回写完成。
3. 前置任务：B0-B4 已完成；B5 runner、smoke watch、manual readiness 和 completion audit 均已存在；当前真实状态为 `waitingTargets=codex,claude`，没有环境错误。

已实现：

- 新增 `scripts/browser-login-long-run-cycle.ps1`：单次执行 runner；若账号仍未 ready，则执行 smoke watch、manual readiness，并写 `browser-login-long-run-cycle.json` / `.md`；若账号 ready，则接 completion audit。
- 新增 `scripts/register-browser-login-long-run-cycle.ps1`：提供 Windows 计划任务注册入口，默认可按 60 分钟节奏运行 6 轮、600 秒间隔的长巡检 cycle；本轮未实际注册或替换现有任务。
- `scripts/browser-login-completion-audit.ps1` 和 `scripts/browser-login-manual-readiness.ps1` 的脚本存在性检查已纳入 long-run cycle 与注册脚本。
- `scripts/test-browser-login-continuation.ps1` 新增 long-run cycle mock 分支，覆盖 runner 等待账号、smoke watch 通过、readiness 通过时输出 `waiting_account_browser_stable`。
- 修正短 cycle 证据隔离：`SmokeIterations<3` 时，long-run 内部 smoke watch 写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-long-run-cycle-smoke\`，不会覆盖 completion audit 使用的主 `browser-login-smoke-watch.json`。

已验证：

- PowerShell PSParser 通过：`browser-login-long-run-cycle.ps1`、`register-browser-login-long-run-cycle.ps1`、`browser-login-completion-audit.ps1`、`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增断言包含 `long-run cycle exits 2 while accounts wait`、`long-run cycle records browser-stable waiting status`、`long-run cycle preserves waiting targets`。
- 真机短 cycle：`.\scripts\browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 1 -SmokeIntervalSeconds 0` 输出 `checkedAt=2026-07-05T04:22:28+08:00`、`status=waiting_account_browser_stable`、`exitCode=2`、`runnerExit=2`、`smokeWatchExit=0`、`manualReadinessExit=0`。
- long-run 短 cycle JSON 显示 `waitingTargets=codex,claude`、`smokeWatchStatus=passed`、`smokeWatchOpenWebP95Ms=38`、`smokeWatchForegroundP95Ms=1544`、`smokeWatchHandlerPackages=com.heytap.browser`、`manualReadinessStatus=ready_for_manual_account`、`completionAuditFailedItemIds=codex-account,claude-account`。
- 主 smoke watch 已恢复为 3 轮 canonical 证据：`checkedAt=2026-07-05T04:21:42+08:00`、`status=passed`、`iterations=3`、`failureCount=0`、`openWebP95Ms=67`、`foregroundP95Ms=1546`、`handlerPackages=com.heytap.browser`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T04:21:54+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`，说明新增 long-run 脚本和主 smoke watch 审计均已通过。
- 最终刷新：`.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad -RefreshState` 输出 `checkedAt=2026-07-05T04:26:12+08:00`、`status=ready_for_manual_account`；`.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T04:26:24+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`。
- 同轮 logcat 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

当前结论：

- 账号等待期现在有两层自动化：轻量 runner 继续负责发现账号 ready 并触发 post-auth；long-run cycle 负责在等待期间顺带刷新浏览器稳定性、性能和泄漏风险证据。
- 目标仍未完成，剩余强缺口仍是 Codex/OpenAI 与 Claude/Anthropic 真实账号授权后的官方状态命令证据。

### B5 [in_progress] 刷新六轮 smoke watch 与账号等待态审计

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5、`LOGIN_TEST_STRATEGY.md` 的 T3/T5/T6，在真实账号授权前继续提高 Kite 可控链路的置信度，验证多轮无账号 handoff、默认浏览器、性能、session 泄漏和临时值落盘风险。
2. 完成标准：OnePlus 8T `3f8bbaad` 六轮 `browser-login-smoke-watch.ps1` 必须全部通过；runner 刷新后不能出现环境错误；completion audit 的失败项仍只能是 `codex-account,claude-account`；manual readiness 仍为 `ready_for_manual_account`；logcat 无崩溃、ANR 或输入超时；文档回写完成。
3. 前置任务：B0-B4 已完成；B5 smoke、smoke watch、manual account start、account watch、runner、manual readiness 和 completion audit 均已建立；上一轮双目标启动已进入 `watch_waiting_for_real_account_authorization`。

已验证：

- `.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 6 -IntervalSeconds 0` 退出码 `0`。
- `browser-login-smoke-watch.json` 输出 `checkedAt=2026-07-05T04:07:33+08:00`、`status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=82`、`openWebP95ThresholdMs=1500`、`foregroundP95Ms=1431`、`foregroundP95ThresholdMs=5000`。
- 六轮趋势中 `handlerPackages=com.heytap.browser`、`handlerStable=True`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`，各轮 `providerOAuthNewSessionCount=0`、`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad` 刷新后语义退出码为等待账号授权；`codex=account_required`、`claude=account_required`、`waitingTargets=codex,claude`、`errorTargets=`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T04:07:59+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`。
- `.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad -RefreshState` 退出码 `0`，输出 `checkedAt=2026-07-05T04:08:05+08:00`、`status=ready_for_manual_account`、`waitingTargets=codex,claude`、`failedItemIds=`。
- 同轮 logcat 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

当前结论：

- 这轮补强的是 Kite 可控链路的稳定性证据，不代表账号已登录。
- 当前最小剩余缺口仍是用户在系统浏览器内完成 Codex/OpenAI 与 Claude/Anthropic 真实账号授权，然后由 runner/post-auth/completion audit 收证。

### B5 [in_progress] 修正 AppRedirect smoke 中间态并刷新双目标人工启动

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 smoke/人工账号验证要求，修正真机 smoke 对 AppRedirect 回跳状态的误判，并把人工账号启动入口刷新为 Codex/Claude 双目标证据。
2. 完成标准：`browser-login-smoke-test.ps1` 不再把 `Returned` 当作终态；真机 smoke 必须通过且 `appRedirectStatus=Delivered`；`browser-login-manual-account-start.ps1 -Targets codex,claude -StartWatch -WatchMaxAttempts 1 -WatchPollSeconds 0 -RunCompletionAuditOnVerified` 能启动两个资源并进入等待真实账号授权；completion audit 仍只缺真实账号授权；logcat 无崩溃、ANR 或输入超时；文档回写完成。
3. 前置任务：B0-B4 已完成；B5 smoke、manual readiness、manual account start、account watch 和 completion audit 均已建立；上一轮 readiness 为 `ready_for_manual_account`，但 manual/account watch 状态文件仍主要来自 Claude 单目标短验证。

已发现：

- 第一次双目标 manual account start 被 smoke 门槛挡住：`smokeExit=1`、失败项 `appredirect-callback-delivered`。
- 设备状态随后显示同一 AppRedirect session 已变为 `Delivered`，说明业务链路已交付，失败来自 smoke 在 `Returned` 中间态过早结束等待。
- 曾验证“同步落盘”假设，但重跑后证明根因不是 `SharedPreferences.apply()`；该 Kotlin 改动已撤回，最终只修 smoke 等待条件。

已实现：

- `scripts/browser-login-smoke-test.ps1` 的 AppRedirect callback 等待状态从 `Delivered/Failed/Returned` 改为只等待 `Delivered/Failed`。
- 添加短注释说明 `Returned` 是中间态，不能作为 smoke 结束条件。
- `BrowserAuthSessionStore.kt` 保持原有 `apply()` 写法，没有新增同步落盘或 UI 压力风险。

已验证：

- PowerShell PSParser 通过：`scripts/browser-login-smoke-test.ps1`。
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain` 通过。
- `.\gradlew.bat :app:assembleDebug --console=plain` 通过。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk` 返回 `Success`。
- 修复后 `.\scripts\browser-login-smoke-test.ps1 -Serial 3f8bbaad` 通过，`checkedAt=2026-07-05T04:00:22+08:00`、`appRedirectStatus=Delivered`、`foregroundHandoffElapsedMs=1229`、`providerOAuthForegroundMaxElapsedMs=1258`、`providerOAuthNewSessionCount=0`、`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -WatchMaxAttempts 1 -WatchPollSeconds 0 -RunCompletionAuditOnVerified` 成功越过 smoke/readiness 门槛；`launchExit=0` for Codex/Claude；状态文件显示 `status=watch_waiting_for_real_account_authorization`、`targets=codex,claude`、`launchedTargets=codex,claude`、`watchExit=2`、`watchMaxAttempts=1`。
- `account-watch-status.json` 显示 `status=waiting_for_real_account_authorization`、`targets=codex,claude`、`waitingTargets=codex,claude`、`attempts=1`、`maxAttempts=1`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T04:01:20+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`；`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`manualStartLaunchedTargets=codex,claude`、`accountWatchWaitingTargets=codex,claude`。
- 同轮近 1000 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。

### B5 [in_progress] 刷新人工账号验证前高置信度组合

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 第 8 节，在没有真实账号授权前，继续刷新 Kite 可控链路的无账号高置信度证据，确认最后人工验证尽量只剩账号/平台挑战。
2. 完成标准：运行 `browser-login-manual-readiness.ps1 -RefreshState -RunSmokeWatch -SmokeIterations 3 -SmokeIntervalSeconds 0 -RunCompletionAudit`；3 轮 smoke watch 无失败；manual readiness 输出 `ready_for_manual_account`；completion audit 若仍未 complete，失败项只能是 Codex/Claude 真实账号证据；logcat 无崩溃、ANR 或输入超时；结果写回本文件。
3. 前置任务：B0-B4 已完成；B5 smoke、smoke watch、manual readiness、manual account start、account watch 和 completion audit 均已建立；上一轮审计已证明 manual account start 与 account watch 状态纳入完成审计。

已验证：

- `.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad -RefreshState -RunSmokeWatch -SmokeIterations 3 -SmokeIntervalSeconds 0 -RunCompletionAudit` 退出码 `0`。
- `manual-account-readiness.json` 输出 `checkedAt=2026-07-05T03:50:21+08:00`、`status=ready_for_manual_account`、`waitingTargets=codex,claude`、`failedItemIds=`；下一步仍为 `browser-login-manual-account-start.ps1 -StartWatch -RunCompletionAuditOnVerified`。
- `browser-login-smoke-watch.json` 输出 `status=passed`、`iterations=3`、`failureCount=0`、`openWebP95Ms=64`、`openWebP95ThresholdMs=1500`、`foregroundP95Ms=1925`、`foregroundP95ThresholdMs=5000`、`handlerStable=True`、`handlerPackages=com.heytap.browser`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`。
- `completion-audit.json` 输出 `checkedAt=2026-07-05T03:50:21+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`；`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`refreshRunnerExit=2`。
- 同轮审计显示 `accountWatchStatus=waiting_for_real_account_authorization`、`manualStartStatus=watch_waiting_for_real_account_authorization`，说明等待态来自真实账号授权缺口，不是启动入口或 watch 链路故障。
- 同轮近 800 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。

### B5 [in_progress] completion audit 纳入 account watch 状态

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 完成审计要求，继续增强最终审计对人工账号陪跑链路的覆盖，让 `browser-login-completion-audit.ps1` 能直接暴露最近 account watch 的等待、验证或失败状态。
2. 完成标准：completion audit 新增 account watch 状态审计项；账号未全部 verified 时，新鲜的 `smoke_failed`、`manual_readiness_failed`、`needs_inspection` 必须成为审计失败项；状态缺失或陈旧但 runner 当前可读时不阻塞；账号已经 verified 时，不因旧 watch 状态阻塞最终完成；脚本解析、自测、真实审计和文档回写完成。
3. 前置任务：B0-B4 已完成；B5 account watch、runner、manual readiness 和 completion audit 已存在；上一轮 completion audit 已能读取 manual account start 状态但尚未直接审计 account watch 状态。

已实现：

- `scripts/browser-login-completion-audit.ps1` 新增 `account-watch-state` 审计项。
- 账号未全部 verified 时，状态缺失但 runner 可读，或状态为 `waiting_for_real_account_authorization` / `verified` 才通过；新鲜的 `smoke_failed`、`manual_readiness_failed`、`needs_inspection` 会失败。
- 陈旧 watch 状态可由当前 runner 状态兜底，避免旧短验证或旧陪跑结果误伤当前审计。
- 审计 JSON 新增 `accountWatchStatus`、`accountWatchTargets`、`accountWatchWaitingTargets`、`accountWatchVerifiedTargets`、`accountWatchAttempts`、`accountWatchMaxAttempts`。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md` 和 `DECISIONS.md` 已同步；新增 ADR-B056。

已验证：

- PowerShell PSParser 通过：`browser-login-completion-audit.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T03:45:08+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`；`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`。
- 同轮审计 JSON 写入 `accountWatchStatus=waiting_for_real_account_authorization`、`accountWatchWaitingTargets=claude`、`accountWatchAttempts=1`、`accountWatchMaxAttempts=1`，审计项 `account-watch-state` 通过。
- 同轮近 500 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。
- `git diff --check -- scripts\browser-login-completion-audit.ps1 docs\browser-login\PLAYBOOK.md docs\browser-login\ACCOUNT_AUTH_COMPLETION_SOP.md docs\browser-login\LOGIN_TEST_STRATEGY.md docs\browser-login\DECISIONS.md docs\browser-login\PROGRESS.md` 无格式错误；仅有既有 LF/CRLF 提示。

### B5 [in_progress] completion audit 纳入人工账号启动状态

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 完成审计要求，继续增强最终审计对人工账号验证入口的覆盖，让 `browser-login-completion-audit.ps1` 不只确认 manual account start 脚本存在，也能检查最近启动状态是否处于可继续人工账号挑战的窗口。
2. 完成标准：completion audit 新增 manual account start 状态审计项；账号未 verified 时，`launch_failed` / `watch_needs_inspection` 必须成为审计失败项；账号已经 verified 时，不因旧启动入口状态阻塞最终完成；脚本解析、自测、真实审计和文档回写完成。
3. 前置任务：B0-B4 已完成；B5 manual account start、manual readiness 和 completion audit 已存在；上一轮 readiness 已能读取 manual account start 状态但 completion audit 尚未直接审计该状态。

已实现：

- `scripts/browser-login-completion-audit.ps1` 新增 `manual-account-start-state` 审计项。
- 账号未全部 verified 时，状态缺失但 runner 可读，或最近 24 小时内为 `planned`、`launched`、`watch_waiting_for_real_account_authorization`、`watch_verified` 才通过；`launch_failed` / `watch_needs_inspection` 会失败。
- 账号都已 verified 后，旧 manual start 状态不阻塞最终完成，避免复制登录事实来源。
- 审计 JSON 新增 `manualStartStatus`、`manualStartTargets`、`manualStartLaunchedTargets`、`manualStartWatchExit`、`manualStartWatchMaxAttempts`。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md` 和 `DECISIONS.md` 已同步；新增 ADR-B055。

已验证：

- PowerShell PSParser 通过：`browser-login-completion-audit.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T03:41:10+08:00`、`status=incomplete`、`failedItemIds=codex-account,claude-account`；`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`。
- 同轮审计 JSON 写入 `manualStartStatus=watch_waiting_for_real_account_authorization`、`manualStartLaunchedTargets=claude`、`manualStartWatchExit=2`、`manualStartWatchMaxAttempts=1`，审计项 `manual-account-start-state` 通过。
- 同轮近 500 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。
- `git diff --check -- scripts\browser-login-completion-audit.ps1 docs\browser-login\PLAYBOOK.md docs\browser-login\ACCOUNT_AUTH_COMPLETION_SOP.md docs\browser-login\LOGIN_TEST_STRATEGY.md docs\browser-login\DECISIONS.md docs\browser-login\PROGRESS.md` 无格式错误；仅有既有 LF/CRLF 提示。

### B5 [in_progress] manual readiness 读取人工账号启动状态

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，继续提高人工账号验证前的汇总可信度，让 `browser-login-manual-readiness.ps1` 不只看到 smoke/watch/runner/audit，也能直接展示 `browser-login-manual-account-start.ps1` 最近是否已经拉起真实登录入口并接上 watch。
2. 完成标准：manual readiness 读取 `manual-account-start-status.json` 并输出独立检查项；通过状态仅表示入口已知或可重新生成，不把账号等待态写成已登录；mock 自测覆盖该检查项；PLAYBOOK/SOP/测试策略/ADR/PROGRESS 回写；真实验证仍不输入账号、不读取 token、不伪造 callback。
3. 前置任务：B0-B4 已完成；B5 manual account start、account watch、manual readiness 和 completion audit 已存在；上一轮 bounded watch 已证明单目标等待态会过滤到本次 targets。

已实现：

- `scripts/browser-login-manual-readiness.ps1` 新增读取 `manual-account-start-status.json`，输出 `manualStartStatus`、`manualStartTargets`、`manualStartLaunchedTargets`、`manualStartWatchExit` 和 `manualStartWatchMaxAttempts`。
- manual readiness 新增检查项 `t4-manual-account-start-known`。`planned`、`launched`、`watch_waiting_for_real_account_authorization`、`watch_verified` 通过；状态缺失但 runner 健康时视为可重新生成；`launch_failed` 和 `watch_needs_inspection` 会阻止 `ready_for_manual_account`。
- `scripts\test-browser-login-continuation.ps1` 新增 mock 覆盖：readiness 能读取已启动并等待账号授权的 manual start 状态；最近 `launch_failed` 会让 readiness 退出 `1` 并记录失败项。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md` 和 `DECISIONS.md` 已同步；新增 ADR-B054。

已验证：

- PowerShell PSParser 通过：`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增断言包含 `manual readiness records manual account start status`、`manual readiness accepts waiting manual account start status`、`manual readiness exits 1 when manual account start launch failed`。
- 真实 readiness：`.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad` 输出 `status=ready_for_manual_account`、`waitingTargets=codex,claude`、`failedItemIds=`，下一步仍指向 `browser-login-manual-account-start.ps1 -StartWatch`。
- 同轮 `manual-account-readiness.json` 写入 `manualStartStatus=watch_waiting_for_real_account_authorization`、`manualStartLaunchedTargets=claude`、`manualStartWatchExit=2`、`manualStartWatchMaxAttempts=1`，检查项 `t4-manual-account-start-known` 通过。
- `git diff --check -- scripts\browser-login-manual-readiness.ps1 scripts\test-browser-login-continuation.ps1 docs\browser-login\PLAYBOOK.md docs\browser-login\ACCOUNT_AUTH_COMPLETION_SOP.md docs\browser-login\LOGIN_TEST_STRATEGY.md docs\browser-login\DECISIONS.md docs\browser-login\PROGRESS.md` 无格式错误；仅有既有 LF/CRLF 提示。

### B5 [in_progress] manual account start 支持 bounded watch

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，继续增强人工账号授权启动入口，让 `-StartWatch` 既能用于真实人工长时间陪跑，也能用 `WatchMaxAttempts` 做自动化短验证，避免后续会话或计划任务为了确认入口健康而阻塞 60 分钟。
2. 完成标准：`browser-login-manual-account-start.ps1` 支持向 account watch 传递最大尝试次数；默认行为保持适合人工陪跑；mock 自测覆盖 bounded watch；SOP/测试策略/ADR/PROGRESS 回写；真机轻量验证仍不输入账号、不读取 token、不伪造 callback。
3. 前置任务：B0-B4 已完成；B5 manual account start、account watch、manual readiness 和 completion audit 已存在；真实账号完成证据仍缺 Codex/Claude 两项。

已实现：

- `scripts/browser-login-manual-account-start.ps1` 新增 `WatchMaxAttempts` 参数；默认 `0` 保持人工长时间陪跑，传入大于 `0` 时转发给 account watch。
- `scripts/browser-login-account-watch.ps1` 写自身状态前会把 `readyTargets`、`waitingTargets`、`verifiedTargets`、`failedTargets` 和 `errorTargets` 过滤到本次 `Targets`，避免单目标 watch 混入未选择账号。
- `scripts/test-browser-login-continuation.ps1` 新增 bounded watch 断言和单目标 target filter 断言。
- `ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`PLAYBOOK.md` 和 `DECISIONS.md` 已同步短验证命令与边界说明。

已验证：

- PowerShell PSParser 通过：`browser-login-account-watch.ps1`、`browser-login-manual-account-start.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增断言包含 `manual account start records watch max attempts`、`manual account start forwards bounded watch max attempts`、`account watch target filter keeps selected waiting target`、`account watch target filter omits unselected waiting target`。
- 真机 bounded watch：`.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets claude -StartWatch -WatchMaxAttempts 1 -WatchPollSeconds 0 -LaunchDelaySeconds 0`。状态文件写入 `checkedAt=2026-07-05T03:29:47+08:00`、`status=watch_waiting_for_real_account_authorization`、`exitCode=2`、`smokeExit=0`、`readinessExit=0`、`readinessStatus=ready_for_manual_account`、`watchExit=2`、`watchMaxAttempts=1`、`launchedTargets=claude`。
- 同轮 `account-watch-status.json` 写入 `targets=claude`、`waitingTargets=claude`、`maxAttempts=1`；未再混入未选择的 Codex。
- 同轮近 500 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T03:30:09+08:00`，`status=incomplete`，`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`，失败项仍只有 `codex-account`、`claude-account`。

### B5 [in_progress] 人工账号授权启动入口

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，继续推进 Codex/Claude 真实账号授权缺口，把“准备度通过后如何拉起两个 CLI 登录入口并接 watch”做成可复查脚本，避免人工验证时走错设备、漏跑 readiness 或漏接后置补证。
2. 完成标准：新增脚本只使用 OnePlus 8T、现有 resource open automation、manual readiness 和 account watch；默认先 smoke/readiness，再启动指定资源的真实终端登录入口；可选接 account watch；不输入账号、不读取 token、不伪造 callback；mock 自测覆盖计划、启动、readiness 失败跳过启动和可选 watch；PLAYBOOK、SOP、测试策略、兼容矩阵和 ADR 回写。
3. 前置任务：B0-B4 已完成；B5 smoke/readiness/account watch/post-auth/completion audit 已存在；当前真实缺口仍是 Codex/Claude 账号授权完成证据，不能由自动脚本伪造。

已实现：

- 新增 `scripts/browser-login-manual-account-start.ps1`。
- 默认流程：无账号 smoke -> manual readiness / completion audit -> ADB `runtime_action=start_resource_open` 启动 `kite.codex.cli` / `kite.claude.code` -> 可选 `-StartWatch` 接 account watch。
- 状态输出：`%LOCALAPPDATA%\Kite\browser-login-continuation\manual-account-start-status.json` 和 `manual-account-start-report.md`。
- `scripts/browser-login-manual-readiness.ps1` 的 `ready_for_manual_account` 下一步已改为 `browser-login-manual-account-start.ps1 -StartWatch -RunCompletionAuditOnVerified`。
- `scripts/browser-login-completion-audit.ps1` 已把 manual account start 纳入必备续跑脚本。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md` 已同步；新增 ADR-B053。

已验证：

- PowerShell PSParser 通过：`browser-login-manual-account-start.ps1`、`browser-login-manual-readiness.ps1`、`browser-login-completion-audit.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增覆盖：plan-only 不启动资源、ready 后 mock ADB 启动 Codex/Claude、readiness 失败跳过 ADB resource open、`-StartWatch` 传播等待态。
- `.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -PlanOnly` 退出 `0`，写入 `status=planned`。
- 真机轻量启动：`.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex -LaunchDelaySeconds 0` 退出 `0`；状态文件 `checkedAt=2026-07-05T03:18:56+08:00`、`status=launched`、`smokeExit=0`、`readinessExit=0`、`readinessStatus=ready_for_manual_account`、`launchedTargets=codex`、`launchExit=0`。
- 同轮前台检查：`adb -s 3f8bbaad shell dumpsys window` 显示 `com.kite.app/com.kite.app.CardRunActivity` 为当前前台；近 500 行 logcat 未发现 `AndroidRuntime`、`FATAL EXCEPTION`、ANR 或 input timeout。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 输出 `checkedAt=2026-07-05T03:20:15+08:00`，`status=incomplete`，`continuation-scripts` PASS 且 requiredEvidence 包含 `manual account start`；失败项仍只有 `codex-account`、`claude-account`。
- `.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad -RefreshState -RunCompletionAudit` 输出 `checkedAt=2026-07-05T03:20:41+08:00`，`status=ready_for_manual_account`，下一步为 `.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`。

### B5 [in_progress] account watch 固定 smoke 先于 readiness

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，继续提高人工账号验证前的高置信度测试入口，确保推荐的 account watch 命令会先刷新真机无账号 smoke，再用 manual readiness 判断是否可以进入真人账号挑战。
2. 完成标准：`browser-login-account-watch.ps1` 同时传入 `-RunSmokeFirst -RunReadinessFirst` 时先运行 smoke；readiness 读取的是刷新后的 smoke / audit 状态；mock 自测覆盖该顺序；PLAYBOOK、SOP、测试策略和 ADR 回写；真实轻量 watch 仍只剩 Codex/Claude 账号授权完成缺口。
3. 前置任务：B0-B4 已完成；B5 schema 9 smoke、smoke watch、manual readiness 和 account watch 已存在；当前真实缺口仍是 Codex/Claude 账号授权完成证据。

已实现：

- `scripts/browser-login-account-watch.ps1` 调整为先处理 `-RunSmokeFirst`，再处理 `-RunReadinessFirst`；如果 smoke 失败仍写 `smoke_failed`，如果 smoke 通过但 readiness 失败则写 `manual_readiness_failed` 并保留 `smokeExit`。
- `scripts/test-browser-login-continuation.ps1` 新增 mock 分支：manual readiness 要求先看到 `mock-smoke-count.txt`，验证 account watch 同时传入两个开关时确实先跑 smoke、再跑 readiness、最后才进入 runner。
- `scripts/browser-login-manual-readiness.ps1` 的 `nextAction` 命令统一展示为 `-RunSmokeFirst -RunReadinessFirst`。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md` 已同步；新增 ADR-B052。

已验证：

- PowerShell PSParser 通过：`browser-login-account-watch.ps1`、`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，新增断言包含 `account watch smoke-first readiness order permits waiting poll`、`account watch records smoke exit before readiness`、`account watch readiness sees refreshed smoke evidence`。
- 真实轻量陪跑入口：`.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -RunSmokeFirst -RunReadinessFirst -MaxAttempts 1 -PollSeconds 0`。状态文件写入 `checkedAt=2026-07-05T03:09:44+08:00`、`status=waiting_for_real_account_authorization`、`exitCode=2`、`smokeExit=0`、`readinessExit=0`、`readinessStatus=ready_for_manual_account`、`waitingTargets=codex,claude`。
- 同轮 `manual-account-readiness.json` 写入 `checkedAt=2026-07-05T03:09:43+08:00`、`status=ready_for_manual_account`，`nextAction` 已为 `-RunSmokeFirst -RunReadinessFirst -RunCompletionAuditOnVerified`。
- 同轮 completion audit：`checkedAt=2026-07-05T03:09:43+08:00`，`status=incomplete`，失败项仍只有 `codex-account`、`claude-account`；`browser-login-smoke` 读取最新 `schemaVersion=9`，`checkedAt=2026-07-05T03:09:09+08:00`。
- `git diff --check -- scripts\browser-login-account-watch.ps1 scripts\browser-login-manual-readiness.ps1 scripts\test-browser-login-continuation.ps1 docs\browser-login\PLAYBOOK.md docs\browser-login\ACCOUNT_AUTH_COMPLETION_SOP.md docs\browser-login\LOGIN_TEST_STRATEGY.md docs\browser-login\COMPATIBILITY_MATRIX.md docs\browser-login\DECISIONS.md docs\browser-login\PROGRESS.md` 无 diff 格式错误；仅提示部分 Markdown 下次 Git 触碰时会 LF/CRLF 转换。

### B5 [in_progress] smoke schema 9 增加授权主机重试证据

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，继续提高人工账号验证前的高置信度测试，区分授权主机瞬时网络毛刺和真实不可达，避免把单次 `curl` 超时直接等同于 Kite 浏览器机制失败。
2. 完成标准：`browser-login-smoke-test.ps1` 对 `accounts.google.com`、`auth.openai.com`、`claude.ai` 的设备侧 HTTPS 探测支持少量重试并记录 attempts；schema 提升到 9；completion audit 和 manual readiness 要求 schema 9；mock 自测和真机 smoke watch 通过；PROGRESS、兼容矩阵和 ADR 回写。
3. 前置任务：B0-B4 已完成；B5 schema 8 smoke、smoke watch、manual readiness 和 completion audit 已存在；当前真实缺口仍是 Codex/Claude 账号授权完成证据。

触发证据：

- 先运行 `.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 6 -IntervalSeconds 10`。
- 结果：`checkedAt=2026-07-05T02:52:34+08:00`，`status=failed`，`iterations=6`，`failureCount=1`。
- 失败项只在第 2 轮：`auth-hosts-network-reachable`；其中 `accounts.google.com` 为 `exit=28`、`http=0`、5 秒连接超时；`auth.openai.com` 和 `claude.ai` 同轮可达，后续轮次 Google 又恢复为 `200`。
- 同组趋势里 `openWebP95Ms=53`、`foregroundP95Ms=1495`、`handlerPackages=com.heytap.browser`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`，说明失败不是 Kite handoff 性能、handler 漂移、session 泄漏或敏感值落盘。

已实现：

- `scripts/browser-login-smoke-test.ps1` 新增 `AuthHostProbeAttempts` 和 `AuthHostProbeRetryDelaySeconds` 参数，默认每个授权主机最多探测 2 次、间隔 2 秒。
- `authHostNetworkResults[]` 增加 `attemptCount` 和 `attempts[]`，记录每次 `curl` 的退出码、HTTP 状态、是否可达和输出摘要。
- `browser-login-smoke-test.ps1` 提升到 `schemaVersion=9`。
- `scripts/browser-login-completion-audit.ps1` 的 `browser-login-smoke` 项要求 `schemaVersion>=9`，并在证据中输出 attempts。
- `scripts/browser-login-manual-readiness.ps1` 的 T2 smoke 门槛要求 schema 9。
- `scripts/test-browser-login-continuation.ps1` mock smoke 状态更新为 schema 9。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md` 已同步；新增 ADR-B051。

已验证：

- PowerShell PSParser 通过：`browser-login-smoke-test.ps1`、`browser-login-completion-audit.ps1`、`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，输出 `browser-login-continuation-self-test passed`。
- 新版真机 6 轮：`.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 6 -IntervalSeconds 10` 输出 `checkedAt=2026-07-05T02:59:54+08:00`、`status=passed`、`iterations=6`、`failureCount=0`、`openWebP95Ms=61`、`foregroundP95Ms=1868`、`handlerPackages=com.heytap.browser`、`providerSessionLeakRunCount=0`、`secretLeakRunCount=0`。
- 最新 `browser-login-smoke.json`：`checkedAt=2026-07-05T02:59:29+08:00`，`schemaVersion=9`，`status=passed`，`authHostProbeAttempts=2`，三个授权主机均 `ok=True` 且 `attemptCount=1`。
- `.\scripts\browser-login-manual-readiness.ps1 -Serial 3f8bbaad -RefreshState -RunCompletionAudit` 输出 `checkedAt=2026-07-05T03:00:22+08:00`，`status=ready_for_manual_account`，`waitingTargets=codex,claude`，`failedItemIds=[]`。
- 同轮 completion audit：`checkedAt=2026-07-05T03:00:22+08:00`，`status=incomplete`，`failedItemIds=codex-account,claude-account`；`browser-login-smoke` 和 `browser-login-smoke-watch` 均 PASS。

### B5 [in_progress] account watch 增加 readiness 预检

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，把人工账号验证阶段的 `account-watch` 接到 manual readiness 门槛，避免环境、smoke、completion audit 还没 ready 时直接轮询账号。
2. 完成标准：`browser-login-account-watch.ps1` 支持 `-RunReadinessFirst`；准备度为 `ready_for_manual_account`、`partial_account_verified_continue_watch` 或 `account_verified_run_completion_audit` 才进入 runner；准备度失败写 `manual_readiness_failed` 且不调用 runner；续跑自测试覆盖通过/失败分支；PLAYBOOK、SOP、测试策略和 ADR 回写。
3. 前置任务：B0-B4 已完成；B5 manual readiness、completion audit、smoke watch 和 account watch 已存在；当前仍没有用户真实账号授权完成证据。

已实现：

- `scripts/browser-login-account-watch.ps1` 新增 `-RunReadinessFirst` 前置检查，记录 `readinessExit`、`readinessStatus` 和 manual readiness 报告路径。
- account watch 只接受 `ready_for_manual_account`、`partial_account_verified_continue_watch`、`account_verified_run_completion_audit` 三种健康准备度状态继续轮询。
- `scripts/test-browser-login-continuation.ps1` 新增 mock manual readiness，并覆盖 readiness 通过后继续等待、readiness 失败后不调用 runner 两个分支。
- `scripts/browser-login-manual-readiness.ps1` 推荐的 account watch 命令已带 `-RunReadinessFirst`。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md` 已同步推荐命令和边界；`DECISIONS.md` 新增 ADR-B050。

已验证：

- PowerShell PSParser 通过：`browser-login-account-watch.ps1`、`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，输出 `browser-login-continuation-self-test passed`；新增 readiness pass/fail 分支均通过。
- 真实轻量预检：`.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -RunReadinessFirst -MaxAttempts 1 -PollSeconds 0` 写入 `account-watch-status.json`：`status=waiting_for_real_account_authorization`，`exitCode=2`，`readinessExit=0`，`readinessStatus=ready_for_manual_account`，`waitingTargets=codex,claude`。
- 同轮 `manual-account-readiness.json`：`status=ready_for_manual_account`，`failedItemIds=[]`，下一步命令包含 `-RunReadinessFirst`。
- 同轮 `completion-audit.json`：`status=incomplete`，`failedItemIds=codex-account,claude-account`，说明当前真实缺口仍只是账号授权完成证据。
- 最终复跑 `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 生成 `checkedAt=2026-07-05T02:47:14+08:00`；审计 `status=incomplete`，失败项仍只有 `codex-account`、`claude-account`。

### B5 [in_progress] completion audit 纳入 smoke watch 趋势强审计

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，把“长期 smoke watch 用于发现偶发失败、卡顿、默认浏览器变化和 session 泄漏”从策略和手动报告推进成 completion audit 的强审计项。
2. 完成标准：`scripts/browser-login-completion-audit.ps1` 读取 `browser-login-smoke-watch.json` / `.md`；要求最近 24 小时、至少 3 轮、`failureCount=0`、`/open-web` p95 和外部浏览器前台切换 p95 不超过阈值、HTTPS handler 稳定且不是 Kite、无 provider OAuth 假 session、无 OAuth 临时值原文落盘；`PLAYBOOK.md` 和 ADR 回写；真实完成审计仍只允许账号证据作为最终剩余缺口。
3. 前置任务：B0-B4 已完成；B5 smoke watch 已存在并在上一轮组合预检中跑出 3 轮通过证据；当前无用户真实账号授权输入。

已实现：

- `scripts/browser-login-completion-audit.ps1` 新增 `browser-login-smoke-watch` 审计项。
- 审计项读取 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-watch.json` 和 `.md`。
- 审计项检查 `status`、`checkedAt` 新鲜度、迭代数、失败数、两个 p95 阈值、handler 稳定性、provider session 泄漏和 OAuth 临时值落盘风险。
- `PLAYBOOK.md` 的完成审计规则补充 smoke watch 趋势强门槛。
- `DECISIONS.md` 新增 ADR-B049。

已验证：

- PowerShell PSParser 通过：`browser-login-completion-audit.ps1`、`test-browser-login-continuation.ps1`。
- `.\scripts\test-browser-login-continuation.ps1` 通过，输出 `browser-login-continuation-self-test passed`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 真实复验：`checkedAt=2026-07-05T02:35:52+08:00`，`status=incomplete`，`failedItemIds=codex-account,claude-account`。
- completion audit 中新增的 `browser-login-smoke-watch` 项已通过：`iterations=3`，`failureCount=0`，`openWebP95Ms=65`，`foregroundP95Ms=1515`，`handlerPackages=com.heytap.browser`，`providerSessionLeakRunCount=0`，`secretLeakRunCount=0`。
- `git diff --check -- scripts/browser-login-completion-audit.ps1 docs/browser-login/PLAYBOOK.md docs/browser-login/DECISIONS.md docs/browser-login/PROGRESS.md` 无格式错误；仅提示部分文档下次 Git 触碰时会进行 LF/CRLF 换行转换。

### B5 [in_progress] 人工账号验证前组合预检实跑

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和 `LOGIN_TEST_STRATEGY.md` 第 8 节，把人工账号验证前的高置信度自动跑法从文档策略推进成一次真实组合预检。
2. 完成标准：使用既有 `browser-login-manual-readiness.ps1` 编排 runner、smoke watch 和 completion audit；真机 smoke watch 通过；manual readiness 输出 `ready_for_manual_account`；完成审计若仍未完成，失败项只能是真实账号完成证据。
3. 前置任务：B0-B4 已完成；B5 manual readiness、smoke watch、completion audit 和 account watch 已存在；当前没有用户真实账号授权输入。

已执行：

- `.\scripts\browser-login-manual-readiness.ps1 -RefreshState -RunSmokeWatch -SmokeIterations 3 -SmokeIntervalSeconds 0 -RunCompletionAudit`

已验证：

- `manual-account-readiness.json`：`checkedAt=2026-07-05T02:31:35+08:00`，`status=ready_for_manual_account`，`failedItemIds=[]`，`waitingTargets=codex,claude`。
- `browser-login-smoke-watch.json`：`checkedAt=2026-07-05T02:31:28+08:00`，`status=passed`，`iterations=3`，`failureCount=0`。
- 本轮 smoke watch 趋势：`openWebP95Ms=65`，`foregroundP95Ms=1515`，`handlerPackages=com.heytap.browser`，`providerSessionLeakRunCount=0`，`secretLeakRunCount=0`。
- `completion-audit.json`：`checkedAt=2026-07-05T02:31:35+08:00`，`status=incomplete`，`failedItemIds=codex-account,claude-account`。
- `runner-status.json`：`exitCode=2`，`nextAction=wait_for_real_account_authorization`，`errorTargets=[]`，说明当前不是环境错误，而是继续等待真实账号授权。

### B5 [in_progress] 人工验证前高置信度自动跑法补充

三问自检：

1. 目标：响应用户要求，在人工 Google / OpenAI / Claude 账号验证前，由我自己先思考并固定多种测试方式，尽量把失败面收窄到账号挑战、provider 配置或真实外部策略，而不是笼统等人工试错。
2. 完成标准：`LOGIN_TEST_STRATEGY.md` 写清几小时自主续跑时的执行顺序；明确哪些项目能自动测到接近确定，哪些不能承诺 100%；把卡顿、反应慢、默认浏览器变化、session 泄漏和敏感值落盘纳入归因；不把验证码、MFA 或账号风控写成 Kite 可绕过问题。
3. 前置任务：B0-B4 已完成；B5 smoke schema 8、smoke watch、account watch、completion audit 和 manual readiness 已存在；当前真实状态仍只缺 Codex/Claude 真实账号授权完成证据。

已实现：

- `LOGIN_TEST_STRATEGY.md` 新增“下一轮高置信度自动跑法”。
- 固定顺序为：manual readiness 刷新、长时间 smoke watch、completion audit 刷新、人工 account watch 陪跑。
- 写明 `disallowed_useragent`、handler 变化、p95 超阈值、session 泄漏、临时值落盘、provider 配置错误和账号挑战的优先归因。
- 写明自动可确定范围与必须人工确认范围，避免把“浏览器链路健康”误写成“账号已经通过”。

已验证：

- 文档复读确认新章节已落入 `LOGIN_TEST_STRATEGY.md`。
- 后续仍以 `browser-login-manual-readiness.ps1 -RefreshState` 和 `browser-login-completion-audit.ps1 -RefreshState` 的真实状态作为是否进入人工账号挑战的门槛。

### B5 [in_progress] manual readiness 增加 completion audit 新鲜度保护

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，收紧人工账号验证准备度汇总，避免 `ready_for_manual_account` 基于过期 completion audit 状态文件作出判断。
2. 完成标准：`scripts/browser-login-manual-readiness.ps1` 默认要求 completion audit 在 24 小时新鲜窗口内；旧审计输出 `not_ready` 并提示运行 `browser-login-completion-audit.ps1 -RefreshState`；续跑自测试覆盖 stale audit 分支；文档和 ADR 回写；真实只读准备度仍能在当前新鲜审计下输出正确状态。
3. 前置任务：B0-B4 已完成；B5 manual readiness 已存在并通过 mock / 真实只读验证；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `scripts/browser-login-manual-readiness.ps1` 新增参数 `AuditFreshHours`，默认 `24`。
- `t5-completion-audit-shape` 现在同时检查 completion audit 是否存在、是否在新鲜窗口内、是否没有账号以外的新缺口。
- `complete` 状态也要求 completion audit 新鲜，避免旧完成状态误报。
- `scripts/test-browser-login-continuation.ps1` 新增 stale audit mock 分支。
- 更新 `PLAYBOOK.md` 和 ADR-B048。

已验证：

- PowerShell PSParser 通过：`browser-login-manual-readiness.ps1`、`test-browser-login-continuation.ps1`。
- 续跑自测试通过，新增 stale audit 分支确认：过期 completion audit 会让 manual readiness 退出 `not_ready`，并记录 `t5-completion-audit-shape`。
- 当前真实 `manual-readiness -RefreshState` 通过，状态仍为 `ready_for_manual_account`；当时推荐下一步为 account watch，现已升级为 `browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`。
- 完成审计复验仍为预期 `incomplete`，唯一失败项仍是 `codex-account`、`claude-account`。

### B5 [in_progress] 人工账号验证准备度汇总脚本

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，把 T0-T6 人工账号验证前的多方法测试组合从文档策略推进成只读准备度报告，减少长时间续跑后靠口头判断是否该进入真人账号挑战。
2. 完成标准：新增 `scripts/browser-login-manual-readiness.ps1`；读取最新 smoke、smoke watch、runner、account watch 和 completion audit 状态；输出 `manual-account-readiness.json` / `.md`；能区分 `ready_for_manual_account`、`not_ready`、`partial_account_verified_continue_watch`、`account_verified_run_completion_audit` 和 `complete`；续跑自测试覆盖 ready 与 not_ready 分支；完成审计把脚本列为必备脚本；文档和 ADR 回写。
3. 前置任务：B0-B4 已完成；B5 smoke schema 8、smoke watch、runner、account watch 和 completion audit 已存在；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

已实现：

- 新增 `scripts/browser-login-manual-readiness.ps1`。
- `manual-readiness` 默认只读状态文件；可选 `-RefreshState` 刷新 runner，`-RunSmokeWatch` 跑多轮 smoke watch，`-RunCompletionAudit` 刷新完成审计。
- 输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\manual-account-readiness.json` 和 `.md`。
- 只要 smoke / smoke-watch / runner / completion audit 形态健康且 completion audit 只剩账号缺口，输出 `ready_for_manual_account` 和 account watch 下一步命令。
- 如果 smoke 等前置失败，输出 `not_ready` 并列出失败 item，不把不健康环境误报为可人工登录。
- `scripts/test-browser-login-continuation.ps1` 新增 manual readiness mock 分支。
- `scripts/browser-login-completion-audit.ps1` 已把 `browser-login-manual-readiness.ps1` 纳入必备续跑脚本。
- 更新 `PLAYBOOK.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B048。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-manual-readiness.ps1`：语法检查通过。
  - `scripts/test-browser-login-continuation.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\test-browser-login-continuation.ps1`：`browser-login-continuation-self-test passed`，新增 manual readiness 分支通过：
  - `manual readiness exits 0 when only account gaps remain`
  - `manual readiness records ready status`
  - `manual readiness records waiting codex`
  - `manual readiness points to account watch`
  - `manual readiness writes report`
  - `manual readiness exits 1 when smoke fails`
  - `manual readiness records not ready status`
  - `manual readiness records smoke failure`
- `.\scripts\browser-login-manual-readiness.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T02:21:10+08:00`，退出码 `0`。
  - `status=ready_for_manual_account`。
  - `waitingTargets=codex,claude`，`verifiedTargets=`。
  - `failedItemIds=`，说明当前人工前置项已通过，剩余是账号挑战。
  - 当时推荐 `nextAction=.\scripts\browser-login-account-watch.ps1 ...`；现已升级为 `.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`。
  - 输出 `C:\Users\19437\AppData\Local\Kite\browser-login-continuation\manual-account-readiness.json` 和 `.md`。
- `git diff --check -- scripts docs/browser-login`：无 diff 格式错误；仅提示若 Git 触碰部分文档会进行 LF/CRLF 换行转换。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T02:21:22+08:00`；最终复验 `checkedAt=2026-07-05T02:22:28+08:00`。
  - `status=incomplete`；`waitingTargets=codex,claude`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] 人工账号验证前多方法置信度测试组合

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和用户关于“自己思考测试方法、提高人工 Google/账号验证通过概率、浏览器更高效完整不卡顿”的要求，把人工验证前的测试方式从单一 smoke 扩展为可恢复、可归因的多方法组合。
2. 完成标准：`LOGIN_TEST_STRATEGY.md` 明确 T0-T5 测试组合；官方资料复核覆盖 Google secure browser / client type / OOB / loopback 边界，以及 Codex/Claude 官方 fallback；失败现象能分类到 WebView、OAuth client 配置、账号挑战、fallback、callback 或性能问题；`DECISIONS.md` 写入新的 ADR；运行最小文档/脚本验证和一组真机 smoke watch。
3. 前置任务：B0-B4 已完成；B5 smoke schema 8、smoke watch、account watch、completion audit 已存在；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `LOGIN_TEST_STRATEGY.md` 官方依据补充 Google OAuth Policies、OOB / loopback migration、OpenAI Codex auth 和 Claude Code auth / CLI reference。
- `LOGIN_TEST_STRATEGY.md` 新增 T0-T5 长期测试组合：官方合规复核、白盒协议测试、真机单轮 smoke、真机多轮趋势、人工账号陪跑、完成审计。
- 新增失败归因表：`disallowed_useragent`、`redirect_uri_mismatch` / `invalid_client`、账号挑战、paste/device code fallback、loopback callback 未交付、p95 超阈值分别对应不同下一步。
- `PLAYBOOK.md` 的人工验证前测试规则补充 T0-T5 组合要求。
- `DECISIONS.md` 新增 ADR-B047。

验证：

- `git diff --check -- docs/browser-login/LOGIN_TEST_STRATEGY.md docs/browser-login/DECISIONS.md docs/browser-login/PLAYBOOK.md docs/browser-login/PROGRESS.md`：无 diff 格式错误；仅提示若 Git 触碰部分文档会进行 LF/CRLF 换行转换。
- `.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 3 -IntervalSeconds 0`：
  - `checkedAt=2026-07-05T02:13:57+08:00`，退出码 `0`。
  - `status=passed`，`iterations=3`，`failureCount=0`。
  - `openWebP95Ms=47`，默认阈值 `1500`。
  - `foregroundP95Ms=1338`，默认阈值 `5000`。
  - `handlerPackages=com.heytap.browser`，handler 稳定。
  - 输出 `C:\Users\19437\AppData\Local\Kite\browser-login-continuation\browser-login-smoke-watch.json` 和 `.md`。
- 再次 `git diff --check -- docs/browser-login/LOGIN_TEST_STRATEGY.md docs/browser-login/DECISIONS.md docs/browser-login/PLAYBOOK.md docs/browser-login/PROGRESS.md docs/browser-login/COMPATIBILITY_MATRIX.md`：无 diff 格式错误；仅提示若 Git 触碰部分文档会进行 LF/CRLF 换行转换。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T02:14:46+08:00`。
  - `status=incomplete`；`waitingTargets=codex,claude`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] account watch verified 后显式自动接完成审计

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和账号完成缺口，把人工账号授权后的收证闭环再缩短一步：账号 verified 后可同轮运行完成审计，直接判断是否已经满足浏览器线完整目标。
2. 完成标准：`scripts/browser-login-account-watch.ps1` 新增显式开关 `-RunCompletionAuditOnVerified`；等待态不触发完成审计；verified 后调用 `browser-login-completion-audit.ps1 -RefreshState` 并写入 `completionAuditExit`、`completionAuditStatus`、`completionAuditJsonPath`、`completionAuditReportPath`；续跑自测试覆盖 verified 后 audit incomplete 分支；真实等待态验证 JSON 不含 `[null]` 空数组。
3. 前置任务：B0-B4 已完成；B5 runner、post-auth、completion audit、account watch 和 smoke watch 已存在；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `scripts/browser-login-account-watch.ps1` 新增参数 `CompletionAuditScript` 和 `RunCompletionAuditOnVerified`。
- 指定 targets 全部 verified 后，如传入 `-RunCompletionAuditOnVerified`，watch 会同轮运行 `browser-login-completion-audit.ps1 -RefreshState`。
- watch 状态和报告新增 `completionAuditExit`、`completionAuditStatus`、`completionAuditJsonPath`、`completionAuditReportPath`。
- 未传 `-RunCompletionAuditOnVerified` 时，watch 保持原行为，只提示下一步运行完成审计。
- 等待态、smoke 失败态、post-auth/error 态不会触发完成审计。
- `Write-WatchState` 清洗空数组，避免机器读 JSON 时出现 `[null]`。
- `scripts/test-browser-login-continuation.ps1` 新增 mock completion audit 分支和空数组回归断言。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B046。

验证：

- `.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad`：
  - Codex：`Not logged in`。
  - Claude：`"loggedIn": false`。
- `.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad`：
  - `codex=account_required`，`claude=account_required`。
  - `waitingTargets=codex,claude`，`nextAction=wait_for_real_account_authorization`。
- PowerShell PSParser：
  - `scripts/browser-login-account-watch.ps1`：语法检查通过。
  - `scripts/test-browser-login-continuation.ps1`：语法检查通过。
- `.\scripts\test-browser-login-continuation.ps1`：`browser-login-continuation-self-test passed`，新增分支通过：
  - `account watch exits 0 when verified audit is incomplete for other targets`
  - `account watch records completion audit exit`
  - `account watch records completion audit status`
  - `account watch records audit next action`
  - `account watch omits null ready targets`
  - `account watch omits null verified targets`
- `.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -MaxAttempts 1 -PollSeconds 0 -RunCompletionAuditOnVerified`：
  - 当前仍为等待态，脚本内部 `exitCode=2`，符合无真实账号授权时的预期。
  - `completionAuditExit=null`、`completionAuditStatus=""`，证明等待态未误触发完成审计。
  - `readyTargets=[]`、`verifiedTargets=[]`、`waitingTargets=["codex","claude"]`，空数组不再写成 `[null]`。
- `git diff --check -- scripts docs`：无 diff 格式错误；仅提示若 Git 触碰部分文档会进行 LF/CRLF 换行转换。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T02:06:53+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] 人工账号验证前多轮 smoke watch 稳定性测试

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和用户关于“自己思考测试方法、提高人工 Google/账号验证通过概率、浏览器更高效更完整不卡顿”的要求，在一次 smoke test 之外提供多轮趋势测试，发现偶发失败、卡顿、默认浏览器 handler 变化、session 泄漏和 OAuth 临时值落盘回归。
2. 完成标准：新增 `scripts/browser-login-smoke-watch.ps1`；支持 `Iterations`、`IntervalSeconds`、阈值和 mock `SmokeTestScript`；输出 `browser-login-smoke-watch.json` / `.md`；续跑自测试覆盖稳定 smoke 聚合；完成审计把脚本列为必备；文档明确它不输入账号、不读取 token、不伪造 provider callback，不替代 Codex/Claude N4/N5。
3. 前置任务：B0-B4 已完成；B5 schema 8 smoke 已通过，account watch 已覆盖人工账号授权收证入口；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

实施计划：

- 新增 `browser-login-smoke-watch.ps1` 聚合多轮 smoke。
- 为 mock smoke 增加自测试，保证无真机时也能验证趋势聚合逻辑。
- 更新 `PLAYBOOK.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`。
- 运行 PSParser、续跑自测试和至少一轮 OnePlus 8T 真机 smoke watch。

已实现：

- 新增 `scripts/browser-login-smoke-watch.ps1`。
- smoke watch 支持 `Iterations`、`IntervalSeconds`、`OpenWebP95ThresholdMs`、`ForegroundP95ThresholdMs`、`MaxFailureCount`、`SmokeTestScript` 和 `LeaveBrowserOpen`。
- 每轮复用 `browser-login-smoke-test.ps1`，并把每次 `browser-login-smoke.json` 复制到 `browser-login-smoke-watch\smoke-iteration-NNN.json`。
- 输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-watch.json` 和 `browser-login-smoke-watch.md`。
- 聚合检查包括失败次数、`/open-web` p95、外部浏览器前台切换 p95、HTTPS handler 稳定性、provider OAuth 形态误建 session 和 OAuth 临时值原文落盘。
- `scripts/test-browser-login-continuation.ps1` 新增 mock smoke watch 分支。
- `scripts/browser-login-completion-audit.ps1` 已把 `browser-login-smoke-watch.ps1` 纳入必备脚本。
- 更新 `PLAYBOOK.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B045。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-smoke-watch.ps1`：语法检查通过。
  - `scripts/test-browser-login-continuation.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\test-browser-login-continuation.ps1`：`browser-login-continuation-self-test passed`，新增 smoke watch 分支通过：
  - `smoke watch exits 0 for stable smoke`
  - `smoke watch records passed status`
  - `smoke watch records iteration count`
  - `smoke watch records zero failures`
  - `smoke watch records stable handler`
  - `smoke watch writes report`
- `.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 1 -IntervalSeconds 0`：
  - `checkedAt=2026-07-05T01:59:19+08:00`，退出码 `0`。
  - `status=passed`，`iterations=1`，`failureCount=0`。
  - `openWebP95Ms=34`，阈值 `1500`。
  - `foregroundP95Ms=1202`，阈值 `5000`。
  - `handlerPackages=com.heytap.browser`，`handlerStable=True`。
  - `providerSessionLeakRunCount=0`，`secretLeakRunCount=0`。
  - 本轮底层 smoke `schemaVersion=8`、`status=passed`、`authHostNetworkResults=accounts.google.com:200/auth.openai.com:403/claude.ai:403 ok=True`。
  - OpenAI/Codex handoff `handoffForegroundElapsedMs=1202`，Claude handoff `517`，均进入 `com.heytap.browser`。
  - `providerOAuthNewSessionCount=0`，`appPrivateRawTemporaryValueHitCount=0`，`appRedirectStatus=Delivered`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T02:00:20+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `continuation-scripts` PASS，已包含 `browser-login-smoke-watch.ps1`。
  - `browser-login-smoke` PASS，读取了本轮 `schemaVersion=8` 真机 smoke 证据。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。
- `& .\scripts\browser-login-completion-audit.ps1 -SkipImplementationChecks | Out-Null; "LASTEXITCODE=$LASTEXITCODE"`：
  - 输出 `LASTEXITCODE=2`，确认完成审计等待态的脚本退出码语义正确；当前工具面板把该非零状态显示为 exit code 1，不影响脚本内部语义和审计 JSON。

### B5 [in_progress] 人工账号授权 account watch 收证入口

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和账号完成缺口，在用户手动完成 Codex/Claude 真实账号授权时，提供一个不读取敏感凭据、不伪造 callback 的本机 watch 入口，自动等待 ready 状态并接 runner/post-auth 收证。
2. 完成标准：新增 `scripts/browser-login-account-watch.ps1`；支持 `Targets`、`RunSmokeFirst`、`TimeoutMinutes`、`PollSeconds`、`MaxAttempts`；等待态写 `account-watch-status.json` / `account-watch-report.md` 并退出 `2`；指定账号全部 verified 时退出 `0` 并提示运行完成审计；续跑自测试覆盖等待后 verified 和仍等待两个分支；完成审计把 watch 脚本列为必备脚本。
3. 前置任务：B0-B4 已完成；B5 runner、gate、post-auth、evidence report 已完成并通过 mock 自测试；当前真实状态仍为 `codex=account_required`、`claude=account_required`。

已实现：

- 新增 `scripts/browser-login-account-watch.ps1`。
- watch 可选先运行 `browser-login-smoke-test.ps1`，确认人工账号验证前的浏览器环境、回跳、脱敏和体验门禁。
- watch 每轮调用 `browser-login-continuation-runner.ps1`；runner 仍负责 gate、post-auth 和脱敏 evidence report。
- watch 输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\account-watch-status.json` 和 `account-watch-report.md`。
- watch 不输入账号、不读取 token、不保存账号邮箱/API key/callback code 原文、不伪造 callback。
- `scripts/browser-login-completion-audit.ps1` 已把 `browser-login-account-watch.ps1` 纳入必备续跑脚本。
- `scripts/test-browser-login-continuation.ps1` 新增 account watch mock 分支。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 `DECISIONS.md`；新增 ADR-B044。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-account-watch.ps1`：语法检查通过。
  - `scripts/test-browser-login-continuation.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\test-browser-login-continuation.ps1`：`browser-login-continuation-self-test passed`，新增 watch 分支通过：
  - `account watch exits 0 after verified target`
  - `account watch records verified status`
  - `account watch exits 2 while waiting`
  - `account watch records waiting status`
- `.\scripts\browser-login-account-watch.ps1 -Serial 3f8bbaad -Targets codex,claude -MaxAttempts 1 -PollSeconds 0`：
  - 输出 `runnerExit=2`。
  - `account-watch-status.json` 显示 `status=waiting_for_real_account_authorization`、`exitCode=2`、`attempts=1`、`waitingTargets=codex,claude`、`verifiedTargets=`。
  - 这是当前无真实账号授权时的预期等待态。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T01:48:55+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `continuation-scripts` PASS，已包含 `browser-login-account-watch.ps1`。
  - `continuation-self-test` PASS。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] smoke schema 8 增加外部浏览器前台切换耗时

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和用户关于“更高效、更完整、不卡顿、反应快”的要求，把 smoke test 从只测 `/open-web` 接收耗时，扩展到真实前台从 Kite 切到外部浏览器的耗时门禁。
2. 完成标准：`browser-login-smoke-test.ps1` 输出 `schemaVersion=8`；新增 `external-foreground-responsive` 检查项，要求 Google、OpenAI/Codex、Claude 相关 OAuth 形态 URL 在被 `/open-web` 接收后，真实前台切到外部浏览器 handler 的总耗时不高于默认阈值；完成审计读取 schema 8、新字段和新 item；文档明确该耗时不覆盖 provider 页面加载、验证码/MFA 或账号风控。
3. 前置任务：B0-B4 已完成；B5 schema 7 smoke 已通过并覆盖多站点 OAuth 形态 URL 外部浏览器分流；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=8`。
- 新增参数 `ForegroundResponsiveThresholdMs`，默认 `5000ms`，用于衡量 OAuth handoff 从 `/open-web` 请求发出到外部浏览器成为前台的总耗时。
- `Wait-ExternalForeground` 记录前台等待耗时；Google 主路径输出 `foregroundWaitElapsedMs` 和 `foregroundHandoffElapsedMs`。
- OpenAI/Codex 与 Claude provider OAuth 形态结果输出 `foregroundWaitMs`、`handoffForegroundElapsedMs`，并汇总 `providerOAuthForegroundMaxElapsedMs`。
- 新增 smoke item `external-foreground-responsive`，要求 Google、OpenAI/Codex、Claude 三类 OAuth 形态前台切换均不超过阈值。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=8`，并把 `external-foreground-responsive`、`foregroundHandoffElapsedMs` 和 `providerOAuthForegroundMaxElapsedMs` 纳入完成审计。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B043。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-smoke-test.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T01:37:44+08:00`，退出码 `0`。
  - `schemaVersion=8`，`status=passed`。
  - `openWebElapsedMs=28`，`foregroundHandoffElapsedMs=390`，`foregroundResponsiveThresholdMs=5000`。
  - `providerOAuthResults=openai:openWeb=19;handoff=397;foreground=com.heytap.browser;passed=True; claude:openWeb=36;handoff=599;foreground=com.heytap.browser;passed=True`。
  - `providerOAuthForegroundMaxElapsedMs=599`，`providerOAuthNewSessionCount=0`。
  - `localWebOpenWebElapsedMs=45`，`appRedirectOpenWebElapsedMs=32`，`appRedirectStatus=Delivered`。
  - `appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T01:38:29+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=8`、`foregroundHandoffElapsedMs=390`、`providerOAuthForegroundMaxElapsedMs=599` 和阈值 `5000ms`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] OpenAI/Claude provider URL 分类器回归单测

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求，把 schema 7 真机 smoke 已覆盖的 OpenAI/Codex 与 Claude URL 形态补到 `BrowserHandoffPolicyTest`，防止后续分类器改动只保住 Google 样例、却破坏真实 CLI loopback 或第三方 HTTPS redirect 分流。
2. 完成标准：新增单测覆盖 `auth.openai.com/oauth/authorize` + loopback redirect、Claude host 登录/OAuth 形态 + loopback redirect、OpenAI/Codex 第三方 HTTPS redirect、Claude 第三方 HTTPS redirect；测试必须断言 loopback 进入 `StartCliCallbackHandoff` 并保留 redirect/state，第三方 HTTPS redirect 只 `OpenExternalBrowser` 不创建 AppRedirect；运行 `:app:testDebugUnitTest --tests com.kite.app.browser.*` 通过。
3. 前置任务：B0-B4 已完成；B5 schema 7 smoke 已通过，OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 在 OnePlus 8T 上均 `/open-web` accepted、前台进入外部浏览器、未新增 browser auth session；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `BrowserHandoffPolicyTest` 新增 `openAiCodexLoopbackRedirectStartsCliCallbackHandoff`，覆盖 `auth.openai.com/oauth/authorize` + `http://localhost:1455/auth/callback`，断言进入 `StartCliCallbackHandoff` 并保留 redirect/state。
- `BrowserHandoffPolicyTest` 新增 `claudeLoopbackRedirectStartsCliCallbackHandoff`，覆盖 Claude host 登录/OAuth 形态 + `http://localhost:43299/callback`，断言进入 `StartCliCallbackHandoff` 并保留 redirect/state。
- `BrowserHandoffPolicyTest` 新增 `openAiExternalHttpsRedirectOpensExternalBrowser`，覆盖 OpenAI/Codex 第三方 HTTPS redirect，断言只走 `OpenExternalBrowser`。
- `BrowserHandoffPolicyTest` 新增 `claudeExternalHttpsRedirectOpensExternalBrowser`，覆盖 Claude 第三方 HTTPS redirect，断言只走 `OpenExternalBrowser`。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T01:31:20+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] smoke schema 7 增加 OpenAI/Claude OAuth 形态 URL 分流检查

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 多站点兼容矩阵要求和 `LOGIN_TEST_STRATEGY.md` 的 C2/C3，把无账号 smoke 从“Google OAuth 正控”扩展到 OpenAI/Codex 与 Claude 相关 host 的 OAuth 形态 URL，证明 Kite 的外部浏览器 handoff 是 provider-agnostic，不是只对 Google 样例有效。
2. 完成标准：`browser-login-smoke-test.ps1` 输出 `schemaVersion=7`；在不输入账号、不伪造 callback 的前提下，分别用 OpenAI/Codex 已观察到的 `auth.openai.com/oauth/authorize` 形态 URL 和 Claude host 的登录/OAuth 形态 URL 通过 `/open-web` 触发 handoff；每个 URL 都必须被 `/open-web` 接收、从 Kite 前台切到外部浏览器 handler 包名，并且不新增 AppRedirect/CliLoopback/browser auth session。完成审计要求 schema 7、新检查项和 provider 结果字段。
3. 前置任务：B0-B4 已完成；B5 schema 6 smoke 已通过，确认授权主机网络、默认 HTTPS handler、系统浏览器 fallback、普通 localhost WebView、Google OAuth 外部浏览器、App redirect、脱敏和 app 私有文件临时值扫描；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

实施计划：

- `browser-login-smoke-test.ps1` 提升到 `schemaVersion=7`，新增 OpenAI/Codex 与 Claude host 的 OAuth 形态 URL 外部浏览器分流检查。
- `browser-login-completion-audit.ps1` 要求 `schemaVersion>=7`，并把多站点 provider handoff 检查纳入 required smoke item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=7`。
- smoke 新增 `provider-oauth-openai-external-browser`，使用 OpenAI/Codex 已观察到的 `auth.openai.com/oauth/authorize` 形态 URL，验证 `/open-web` 接收后从 Kite 前台切到外部浏览器 handler。
- smoke 新增 `provider-oauth-claude-external-browser`，使用 Claude host 登录/OAuth 形态 URL，验证同一外部浏览器 handoff 机制。
- smoke 新增 `provider-oauth-no-auth-session`，要求 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 不新增 AppRedirect、CliLoopback 或其他 browser auth session。
- smoke JSON / Markdown 新增 `providerOAuthResults` 和 `providerOAuthNewSessionCount`。
- app 私有文本扫描范围加入 OpenAI 与 Claude 本轮生成的 `state`，继续要求原文命中 `0`。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=7`，并把 provider OAuth 形态分流检查纳入 `browser-login-smoke` required item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B042。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-smoke-test.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T01:24:49+08:00`，退出码 `0`。
  - `schemaVersion=7`，`status=passed`。
  - `providerOAuthResults=openai:accepted=True;elapsedMs=20;foreground=com.heytap.browser/com.android.browser.BrowserActivity;external=True;matches=True;passed=True; claude:accepted=True;elapsedMs=14;foreground=com.heytap.browser/com.android.browser.BrowserActivity;external=True;matches=True;passed=True`。
  - `providerOAuthNewSessionCount=0`，说明 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 没有创建假的 AppRedirect/CliLoopback/browser auth session。
  - `httpsBrowserResolvePackage=com.heytap.browser`，`httpsBrowserResolveActivity=com.android.browser.RealBrowserActivity`，`httpsBrowserResolveExported=true`。
  - `customTabsServiceCount=0`，系统 `ACTION_VIEW` fallback 已由实测前台证明可用。
  - 授权主机网络探测：`accounts.google.com:exit=0;http=200;ok=True`、`auth.openai.com:exit=0;http=403;ok=True`、`claude.ai:exit=0;http=403;ok=True`。
  - 普通 localhost Web UI 前台为 `com.kite.app/com.kite.app.CardRunActivity`，`localWebOpenWebElapsedMs=38`。
  - `openWebElapsedMs=20`，`appRedirectOpenWebElapsedMs=63`，默认阈值 `1500ms`。
  - `appRedirectStatus=Delivered`，`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`。
  - `appRedirectRawSecretHitCount=0`，`appPrivateTextScannedFileCount=112`，`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T01:25:36+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=7`、OpenAI/Claude 多站点 OAuth 形态分流、外部浏览器 handler/fallback、授权主机网络、普通 localhost WebView、AppRedirect、脱敏和 app 私有文件扫描。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] smoke schema 6 增加外部浏览器 handler/fallback 能力检查

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的 C2/C3 测试策略，把“官方推荐的外部 user-agent 路线”继续拆成可自动验证的设备能力：OnePlus 8T 必须能把 HTTPS 授权 URL 解析到真实外部浏览器，并且 Kite 的 Custom Tabs / 系统浏览器 fallback 路径能被实际前台观测到。
2. 完成标准：`browser-login-smoke-test.ps1` 输出 `schemaVersion=6`；记录 `https://accounts.google.com/` 的 `ACTION_VIEW` 默认 handler 包名/Activity、Custom Tabs service 数量和包名；新增检查项要求 HTTPS handler 存在、不是 `com.kite.app`，且真实 Google OAuth handoff 后前台包名离开 Kite 并与默认 HTTPS handler 一致。完成审计要求 schema 6 和新检查项。Custom Tabs service 数量可以为 `0`，只要系统浏览器 fallback handler 存在且实测前台外跳成功。
3. 前置任务：B0-B4 已完成；B5 schema 5 smoke 已通过，覆盖授权主机设备侧 HTTPS 可达、普通 localhost WebView、`/open-web` 响应耗时、App redirect、脱敏和 app 私有文件临时值扫描；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

实施计划：

- `browser-login-smoke-test.ps1` 提升到 `schemaVersion=6`，新增 HTTPS `ACTION_VIEW` handler 和 Custom Tabs service 探测。
- `browser-login-completion-audit.ps1` 要求 `schemaVersion>=6`，并把新 handler/fallback 检查项纳入 required smoke item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=6`。
- smoke 新增 `external-browser-handler-resolved`：通过 `cmd package resolve-activity -a android.intent.action.VIEW -d https://accounts.google.com/` 记录默认 HTTPS 浏览器 handler。
- smoke 新增 `external-browser-handler-observed`：Google OAuth handoff 后要求前台包名离开 Kite，并与默认 HTTPS handler 包名一致。
- smoke JSON / Markdown 新增 `httpsBrowserResolvePackage`、`httpsBrowserResolveActivity`、`httpsBrowserResolveExported`、`customTabsServiceCount` 和 `customTabsServicePackages`。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=6`，并把 `external-browser-handler-resolved`、`external-browser-handler-observed` 纳入 required smoke item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B041。

验证：

- PowerShell PSParser：
  - `scripts/browser-login-smoke-test.ps1`：语法检查通过。
  - `scripts/browser-login-completion-audit.ps1`：语法检查通过。
- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T01:15:34+08:00`，退出码 `0`。
  - `schemaVersion=6`，`status=passed`。
  - `httpsBrowserResolvePackage=com.heytap.browser`，`httpsBrowserResolveActivity=com.android.browser.RealBrowserActivity`，`httpsBrowserResolveExported=true`。
  - `customTabsServiceCount=0`，说明当前设备没有可查询的 Custom Tabs service；系统 `ACTION_VIEW` fallback 已由实测前台证明可用。
  - Google OAuth handoff 前台为 `com.heytap.browser/com.android.browser.BrowserActivity`，与默认 HTTPS handler 包名一致。
  - 授权主机网络探测：`accounts.google.com:exit=0;http=200;ok=True`、`auth.openai.com:exit=0;http=403;ok=True`、`claude.ai:exit=0;http=403;ok=True`。
  - 普通 localhost Web UI 前台为 `com.kite.app/com.kite.app.CardRunActivity`，`localWebOpenWebElapsedMs=31`。
  - `openWebElapsedMs=35`，`appRedirectOpenWebElapsedMs=30`，默认阈值 `1500ms`。
  - `appRedirectStatus=Delivered`，`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`。
  - `appRedirectRawSecretHitCount=0`，`appPrivateTextScannedFileCount=112`，`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - `checkedAt=2026-07-05T01:16:04+08:00`。
  - `status=incomplete`；`refreshRunnerExit=2`、`selfTestExit=0`、`unitTestExit=0`、`assembleExit=0`、`adbDevicesExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=6`、外部浏览器 handler/fallback、授权主机网络、普通 localhost WebView、AppRedirect、脱敏和 app 私有文件扫描。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] smoke schema 5 增加授权主机网络可达性

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的人工账号验证前高置信度测试，把“浏览器环境可用”继续细分到 OnePlus 8T 设备侧能通过 HTTPS 到达 Google/OpenAI/Claude 授权主机。
2. 完成标准：`browser-login-smoke-test.ps1` 在不输入账号、不读取凭据的前提下，对 `accounts.google.com`、`auth.openai.com`、`claude.ai` 做设备侧 HTTPS reachability probe；每个 host 的 `curl` 退出码为 `0` 且 HTTP 状态码在 `200..499` 范围内即可视为 DNS/TLS/网络路径可达；完成审计要求新版 smoke schema 和新 item；文档说明该检查只证明设备网络可达，不证明账号挑战或 provider 授权通过。
3. 前置任务：B0-B4 已完成；B5 已有 schema 4 smoke，覆盖普通 localhost WebView、OAuth 外部浏览器、`/open-web` 响应耗时、App redirect 回跳、auth session 脱敏和 app 私有文本文件临时值扫描；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

实施计划：

- `browser-login-smoke-test.ps1` 提升到 `schemaVersion=5`，新增授权主机设备侧 HTTPS reachability 检查。
- `browser-login-completion-audit.ps1` 要求 `schemaVersion>=5`，并把新检查项纳入 required smoke item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=5`。
- smoke 新增 `auth-hosts-network-reachable`，在 OnePlus 8T 设备侧用 HTTPS probe 检查 `accounts.google.com`、`auth.openai.com`、`claude.ai`。
- smoke JSON / Markdown 新增 `authHostNetworkResults`，只记录 host、`curl` 退出码、HTTP 状态码和是否可达，不保存账号、cookie 或 token。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=5`，并把 `auth-hosts-network-reachable` 纳入 `browser-login-smoke` required item。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md` 和 `DECISIONS.md`；新增 ADR-B040。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T01:04:44+08:00`，退出码 `0`。
  - `schemaVersion=5`，`status=passed`。
  - `authHostNetworkResults=accounts.google.com:exit=0;http=200;ok=True; auth.openai.com:exit=0;http=403;ok=True; claude.ai:exit=0;http=403;ok=True`。
  - 普通 localhost Web UI 前台为 `com.kite.app/com.kite.app.CardRunActivity`，`localWebOpenWebElapsedMs=52`。
  - Google OAuth 前台为 `com.heytap.browser/com.android.browser.BrowserActivity`，`openWebElapsedMs=40`。
  - `appRedirectOpenWebElapsedMs=26`，`appRedirectStatus=Delivered`。
  - `appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`。
  - `appRedirectRawSecretHitCount=0`。
  - `appPrivateTextScannedFileCount=112`，`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 当前最新审计 `checkedAt=2026-07-05T01:05:30+08:00`。
  - `status=incomplete`；`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`、`adbDevicesExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=5`、授权主机网络探测、普通 localhost WebView、外部浏览器、AppRedirect、脱敏和 app 私有文件扫描。
  - `failedItemIds=codex-account,claude-account`，runner 状态仍为 `wait_for_real_account_authorization`，`waitingTargets=codex,claude`。

### B5 [in_progress] smoke schema 4 扩展 app 私有文件临时值扫描

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的 G4，把“人工账号验证前不泄漏 OAuth 临时值”的自动化范围从 `kite_browser_auth_sessions.xml` 扩展到 app 私有文本状态/诊断文件。
2. 完成标准：`browser-login-smoke-test.ps1` 生成本轮唯一 `state` / 假 `code` / 假 token 后，扫描 app 私有 `files` 与 `shared_prefs` 下的文本类文件，确认这些临时值没有原文落盘；完成审计要求新版 smoke schema 和新 item；文档说明该检查只证明 Kite 持久状态边界，不替代真实账号 N4/N5。
3. 前置任务：B0-B4 已完成；B5 已有 schema 3 smoke，覆盖普通 localhost WebView、OAuth 外部浏览器、`/open-web` 响应耗时、App redirect 回跳和 auth session prefs 脱敏；当前仍缺 Codex/Claude 真实账号完成证据。

实施计划：

- `browser-login-smoke-test.ps1` 提升到 `schemaVersion=4`，新增 app 私有文本文件扫描和检查项。
- `browser-login-completion-audit.ps1` 要求 `schemaVersion>=4`，并把新检查项和 `appPrivateRawTemporaryValueHitCount=0` 纳入完成审计。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=4`。
- smoke 新增 `no-oauth-temporary-values-in-app-files`，扫描 app 私有 `files` / `shared_prefs` 下文本类文件，只报告命中文件路径和计数，不输出本轮生成的临时值。
- smoke JSON / Markdown 新增 `appPrivateTextScannedFileCount`、`appPrivateRawTemporaryValueHitCount` 和 `appPrivateRawTemporaryValueHitPaths`。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=4`，并把 `no-oauth-temporary-values-in-app-files` 与 `appPrivateRawTemporaryValueHitCount=0` 纳入 `browser-login-smoke` 审计项。
- 首次 schema 4 smoke 发现真实漏点：本轮 OAuth 临时值命中 `shared_prefs/kite_card_run_store.xml`。根因是运行状态/历史步骤持久化了 OAuth 授权 URL。
- `CardRunStore` 持久化 `nextActionUrl` 和 history detail 前会对 OAuth URL 写脱敏摘要；`MainActivity` 在 App redirect 已交付或失败时会清理 `nextActionUrl`，避免短期授权 URL继续留在运行状态。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B039。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:53:02+08:00`，退出码 `0`。
  - `schemaVersion=4`，`status=passed`。
  - 普通 localhost Web UI 前台为 `com.kite.app/com.kite.app.CardRunActivity`。
  - Google OAuth 前台为 `com.heytap.browser/com.android.browser.BrowserActivity`。
  - `appRedirectStatus=Delivered`。
  - `appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`。
  - `appRedirectRawSecretHitCount=0`。
  - `appPrivateTextScannedFileCount=112`，`appPrivateRawTemporaryValueHitCount=0`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - 最终复验 `checkedAt=2026-07-05T00:53:55+08:00`。
  - `status=incomplete`；`smokeExit=0`、`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=4`、`localWebOpenWebElapsedMs=17`、`openWebElapsedMs=20`、`appRedirectOpenWebElapsedMs=27`、`appRedirectStatus=Delivered`、脱敏 `returnedUrl`、`appRedirectRawSecretHitCount=0`、`appPrivateTextScannedFileCount=112`、`appPrivateRawTemporaryValueHitCount=0`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 当前最新审计 `checkedAt=2026-07-05T00:58:29+08:00`。
  - `status=incomplete`；`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`、`adbDevicesExit=0`。
  - `browser-login-smoke` 继续读取最近 24 小时 schema 4 证据并 PASS。
  - `failedItemIds=codex-account,claude-account`，runner 状态为 `wait_for_real_account_authorization`，`waitingTargets=codex,claude`。
- `git diff --check`：无空白错误；仅有 Git 的 LF/CRLF 换行提示。

### B5 [in_progress] smoke 增加普通 localhost WebView 回归

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的普通 localhost Web UI 场景，补充真机 smoke 证明非 OAuth 的 `http://127.0.0.1:<port>` 页面仍留在 Kite WebView，不被 OAuth handoff 逻辑误分流到系统浏览器。
2. 完成标准：`browser-login-smoke-test.ps1` 先通过 `/open-web` 打开 `http://127.0.0.1:8791/status`，确认前台仍为 `com.kite.app`，且不新增 browser auth session；完成审计把该本地 WebView 回归 item 纳入 required smoke item；文档记录该测试保护普通 Web UI，不证明账号 N4/N5。
3. 前置任务：B0-B4 已完成；B5 已有 OAuth 外部浏览器、App redirect 回跳、脱敏落盘、`/open-web` 响应耗时和完成审计 schema 检查；当前 runner 仍为 `codex=account_required`、`claude=account_required`。

已实现：

- `scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=3`。
- smoke 新增 `local-web-open-accepted`、`local-webview-stays-in-kite`、`local-webview-no-auth-session`。
- smoke JSON / Markdown 输出 `localWebUrl`、`localWebOpenWebElapsedMs` 和 `localWebForeground`。
- `scripts/browser-login-completion-audit.ps1` 要求 `schemaVersion>=3`，并把普通 localhost WebView 三个 item 纳入 required smoke item；审计还直接检查 `localWebForegroundPackage=com.kite.app`。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`LOGIN_TEST_STRATEGY.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md`；新增 ADR-B038。

验证计划（已完成）：

- 运行新版 smoke，确认普通 localhost 页面前台仍为 `com.kite.app` 且无新增 auth session。
- 运行完成审计，确认 `browser-login-smoke` 通过且失败项仍只剩真实账号完成证据。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:30:03+08:00`，退出码 `0`。
  - `schemaVersion=3`。
  - `local-web-open-accepted` PASS，`elapsedMs=21`，URL 为 `http://127.0.0.1:8791/status`。
  - `local-webview-stays-in-kite` PASS，前台为 `com.kite.app/com.kite.app.CardRunActivity`。
  - `local-webview-no-auth-session` PASS，`beforeCount=9`、`afterCount=9`、`newSessionIds=`。
  - `open-web-responsive` PASS，`googleElapsedMs=38`、`appRedirectElapsedMs=47`、`thresholdMs=1500`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - 最终复验 `checkedAt=2026-07-05T00:30:55+08:00`。
  - `status=incomplete`；`smokeExit=0`、`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`。
  - `browser-login-smoke` PASS，证据包含 `schemaVersion=3`、`localWebOpenWebElapsedMs=33`、`localWebForeground=com.kite.app/com.kite.app.CardRunActivity`、`openWebElapsedMs=13`、`appRedirectOpenWebElapsedMs=43`、`appRedirectStatus=Delivered`、脱敏 `returnedUrl`、`appRedirectRawSecretHitCount=0`、`missingSmokeItemIds=(none)`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] smoke 增加本地 handoff 响应耗时阈值

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的体验/性能测试要求，把网页登录 handoff 的本地 `/open-web` 接收速度纳入真机 smoke，验证“点了之后 Kite 迅速承诺并交给浏览器”，而不是只验证最终页面是否打开。
2. 完成标准：`browser-login-smoke-test.ps1` 对 Google 外部浏览器 URL 和 Kite App redirect URL 的 `/open-web` 接收耗时设置明确阈值；完成审计把该 item 纳入 required smoke item；文档说明该阈值只覆盖本地 handoff 接收，不承诺 provider 页面加载或账号挑战速度。
3. 前置任务：B0-B4 已完成；B5 已有 schema 2 smoke、App redirect 回跳和脱敏断言；当前账号仍未授权，性能 smoke 不能替代 Codex/Claude N4/N5。

已实现：

- `scripts/browser-login-smoke-test.ps1` 新增参数 `OpenWebResponsiveThresholdMs`，默认 `1500`。
- 新增 smoke item `open-web-responsive`：要求 Google OAuth URL 和 Kite App redirect URL 两次本地 `/open-web` 接收耗时都不高于阈值。
- smoke JSON / Markdown 输出 `openWebResponsiveThresholdMs`。
- `scripts/browser-login-completion-audit.ps1` 把 `open-web-responsive` 纳入 required smoke item。
- 更新 `LOGIN_TEST_STRATEGY.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`PLAYBOOK.md`、`DECISIONS.md`；新增 ADR-B037。

验证计划：

- 运行新版 smoke，确认 `open-web-responsive` 通过并记录两次耗时。
- 运行完成审计，确认 `browser-login-smoke` 仍通过，且真实账号缺口仍只落在 Codex/Claude 账号项。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:22:27+08:00`，退出码 `0`。
  - `openWebResponsiveThresholdMs=1500`。
  - `open-web-responsive` PASS，证据为 `googleElapsedMs=22; appRedirectElapsedMs=40; thresholdMs=1500`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - 最终复验 `checkedAt=2026-07-05T00:24:21+08:00`。
  - `status=incomplete`；`smokeExit=0`、`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`。
  - `browser-login-smoke` PASS，证据包含 `openWebElapsedMs=15`、`appRedirectOpenWebElapsedMs=26`、`openWebResponsiveThresholdMs=1500`、`appRedirectStatus=Delivered`、脱敏 `returnedUrl`、`appRedirectRawSecretHitCount=0`、`missingSmokeItemIds=(none)`。
  - `failedItemIds=codex-account,claude-account`，仍只缺真实账号授权完成证据。

### B5 [in_progress] 完成审计加固 smoke schema

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 完成审计要求，把 `browser-login-completion-audit.ps1` 对 smoke test 的判断从“最近 24 小时 status=passed”收紧为“确实包含 App redirect 回跳和脱敏边界证据”，避免旧格式 smoke 结果被误当作覆盖 G3/G4。
2. 完成标准：`browser-login-smoke-test.ps1` 输出可识别的 schema；完成审计要求 smoke schema、关键 item id、`appRedirectStatus=Delivered`、`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`、`appRedirectRawSecretHitCount=0` 全部满足；真实账号缺口仍只保留在 Codex/Claude 账号项。
3. 前置任务：B0-B4 已完成；B5 已有 App redirect + 脱敏 smoke 证据；当前 runner 仍显示 `codex=account_required`、`claude=account_required`，因此不能转入 post-auth 完成声明。

已实现：

- `scripts/browser-login-smoke-test.ps1` 输出 `schemaVersion=2`。
- `scripts/browser-login-completion-audit.ps1` 的 `browser-login-smoke` 项现在要求：
  - smoke schema 不低于 2。
  - 关键 item id 包含外部浏览器、无 `disallowed_useragent`、第三方 HTTPS redirect 不建假 AppRedirect、AppRedirect pending、AppRedirect delivered、AppRedirect redacted 和无崩溃/ANR。
  - `appRedirectStatus=Delivered`。
  - `appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`。
  - `appRedirectRawSecretHitCount=0`。
- 更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 `DECISIONS.md`；新增 ADR-B036。

验证计划（已完成）：

- 运行新版 smoke，确认 `schemaVersion=2` 和 App redirect 字段写入。
- 运行完成审计，确认 `browser-login-smoke` 项通过且失败项仍只剩真实账号完成证据。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:18:53+08:00` 和审计同轮刷新 `checkedAt=2026-07-05T00:19:11+08:00`。
  - `status=passed`，退出码 `0`。
  - JSON 字段：`schemaVersion=2`、`appRedirectStatus=Delivered`、`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`、`appRedirectRawSecretHitCount=0`。
  - 关键 item id 包含 `foreground-external-browser`、`ui-no-disallowed-useragent`、`no-third-party-appredirect-session`、`appredirect-pending-session`、`appredirect-callback-delivered`、`appredirect-callback-redacted`、`no-crash-or-anr`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - `checkedAt=2026-07-05T00:19:26+08:00`。
  - `status=incomplete`；`smokeExit=0`、`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`。
  - `browser-login-smoke` PASS，证据为 `schemaVersion=2`、`appRedirectStatus=Delivered`、`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`、`appRedirectRawSecretHitCount=0`、`missingSmokeItemIds=(none)`、`failedItemIds=(none)`。
  - `failedItemIds=codex-account,claude-account`，即仍只缺真实账号授权完成证据。

### B5 [in_progress] App redirect 与脱敏 smoke 扩展

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md` 的 G3/G4，在无账号 smoke test 中补齐 Kite 可接收 `kite-auth://callback` 的真机回跳机制验证，以及授权 `code` / token / `state` 的持久化脱敏验证。
2. 完成标准：`browser-login-smoke-test.ps1` 能在 OnePlus 8T 上创建唯一 AppRedirect pending session、用同 `state` 模拟 callback、确认 session 到达 `Delivered` 或等价已交付状态、确认 `returnedUrl` 只含 `present` 摘要且 raw prefs 不含本次假 `code` / token / `state`；文档明确这不是账号 N4/N5 完成证据。
3. 前置任务：B0-B4 已完成；B5 已有 Google 外部浏览器 smoke、第三方 HTTPS redirect 不建假 session、账号验证节点和完成审计。

已实现：

- 扩展 `scripts/browser-login-smoke-test.ps1`，新增 `Read-BrowserAuthSessionPrefs`、新 session 等待、session 状态等待和脱敏断言。
- smoke 会用 `redirect_uri=kite-auth://callback` 的 OAuth 形态 URL 创建 `AppRedirect` pending session，再用 Android `ACTION_VIEW` 触发同 `state` callback。
- smoke JSON / Markdown 只写入 redacted URL、redacted callback、session 状态和 `rawSecretHitCount`，不写入本次假 `code` / token / `state` 原文。
- 更新 `LOGIN_TEST_STRATEGY.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`COMPATIBILITY_MATRIX.md`、`WEB_LOGIN_RESEARCH.md`、`PLAYBOOK.md`、`DECISIONS.md` 和完成审计 smoke 要求。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:12:43+08:00`。
  - `status=passed`，退出码 `0`。
  - `foreground=com.heytap.browser/com.android.browser.BrowserActivity`，UI dump 未出现 `disallowed_useragent`。
  - `no-third-party-appredirect-session` PASS，`beforeCount=2`、`afterCount=2`、`newThirdPartySessionIds=`。
  - `appredirect-pending-session` PASS，`kind=AppRedirect`、`redirectUri=kite-auth://callback`、`status=Pending`。
  - `appredirect-callback-delivered` PASS，session `141a587a6d5c414ab0f029810025b7c5` 到 `Delivered`。
  - `appredirect-callback-redacted` PASS，`returnedUrl=kite-auth://callback?code=present&access_token=present&state=present`，`rawSecretHitCount=0`。
  - `no-crash-or-anr` PASS。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - `checkedAt=2026-07-05T00:12:57+08:00`。
  - `status=incomplete`；`smokeExit=0`、`unitTestExit=0`、`assembleExit=0`、`selfTestExit=0`、`adbDevicesExit=0`。
  - PASS：`browser-login-smoke`、`browser-unit-tests`、`debug-apk-build`、`oneplus-device-online`、`continuation-self-test`、`runner-refresh`、`runner-evidence-report`、`scheduled-continuation-task`。
  - MISS：`codex-account`、`claude-account`，仍等待真实账号授权。

### B5 [in_progress] 真机无账号 smoke test 自动化

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和 `LOGIN_TEST_STRATEGY.md`，把人工账号验证前的 C2/G1-G4 可控部分变成 OnePlus 8T 可重复运行的无账号 smoke test，不等待用户输入账号。
2. 完成标准：新增脚本能启动 Kite、恢复 `18791 -> 8791` 转发、触发 Google OAuthPlayground 授权 URL、确认前台离开 Kite WebView 到系统浏览器/Custom Tabs、确认第三方 HTTPS redirect 不新增可疑 AppRedirect session、采集截图/UI dump/崩溃过滤结果；完成审计能读取最近一次 smoke 结果。
3. 前置任务：B0-B4 已完成；B5 已有 Google WebView 负控、Custom Tabs 正控、测试策略、完成审计和 OnePlus 8T 绑定。

已实现：

- 新增 `scripts/browser-login-smoke-test.ps1`。
- 更新 `LOGIN_TEST_STRATEGY.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`COMPATIBILITY_MATRIX.md`、`PLAYBOOK.md` 和 `browser-login-completion-audit.ps1`。
- 新增 ADR-B034：真机 smoke test 作为人工账号验证前置证据。
- 完成审计新增 `browser-login-smoke` 审计项：默认读取最近 24 小时 smoke 结果；传入 `-RunSmokeTest` 时同轮刷新真机 smoke。

验证：

- `.\scripts\browser-login-smoke-test.ps1`：
  - `checkedAt=2026-07-05T00:00:18+08:00`。
  - `status=passed`，退出码 `0`。
  - 前台为 `com.heytap.browser/com.android.browser.BrowserActivity`，证明 Google OAuth 已离开 Kite WebView。
  - `ui-no-disallowed-useragent` PASS，UI dump 未出现 `disallowed_useragent` 或 Google WebView 禁止访问文案。
  - `no-third-party-appredirect-session` PASS，`beforeCount=2`、`afterCount=2`、`newThirdPartySessionIds=`。
  - `no-crash-or-anr` PASS，最近日志无 FATAL/ANR/Input timeout。
  - 输出文件：`%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.json`、`browser-login-smoke.md`、`browser-login-smoke.png`、`browser-login-smoke-ui.xml`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState -RunSmokeTest`：
  - `checkedAt=2026-07-05T00:02:58+08:00`。
  - PASS：`browser-login-smoke`，证据为 `status=passed`、`fresh24h=True`、`smokeExit=0`、前台 `com.heytap.browser/com.android.browser.BrowserActivity`。
  - PASS：`browser-unit-tests`、`debug-apk-build`、`oneplus-device-online`、`continuation-self-test`、`runner-refresh`、`scheduled-continuation-task`。
  - MISS：`codex-account`、`claude-account`，证据为 `verifiedTargets=(none)`、`waitingTargets=codex, claude`。
  - 审计退出码为 `2`，符合当前只缺真实账号完成证据。
- `git diff --check -- docs scripts` 退出码为 `0`；仅提示部分 Markdown 后续被 Git 触碰时会做 LF/CRLF 换行转换。

### B5 [in_progress] 账号验证节点和人工验证前测试策略

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，补齐多站点账号验证的通用判断和人工 Google / OpenAI / Claude 验证前的测试方法，避免把 Google、验证码、MFA 或账号风控当成唯一验收口径。
2. 完成标准：新增账号验证节点文档和测试策略文档；PLAYBOOK、SOP、兼容矩阵、完成审计和 ADR 同步；测试策略说明哪些可以测到机制确定性，哪些必须等待用户真实账号完成。
3. 前置任务：B0-B4 已完成；B5 已有 Google WebView 负控、Custom Tabs 正控、App redirect、Codex/Claude CLI 账号门槛前证据和完成审计脚本。

已实现：

- 新增 `docs/browser-login/ACCOUNT_VERIFICATION_NODES.md`，把账号验证拆成 N0 环境拒绝、N1 合规浏览器打开、N2 账号挑战到达、N3 回跳入口可达、N4 状态拥有者确认、N5 后置健康检查。
- 新增 `docs/browser-login/LOGIN_TEST_STRATEGY.md`，把人工账号验证前的测试拆成 C0-C4，并为 Google 写入 G1-G4：WebView 负控、Custom Tabs 正控、浏览器会话可用性、redirect 类型分离和敏感信息边界。
- 复核官方资料：Google remediation 仍建议用 Chrome Custom Tabs 替换 WebView OAuth；Google embedded webview 政策仍禁止 WebView OAuth；Chrome Auth Tab 是后续体验增强候选。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`COMPATIBILITY_MATRIX.md`、`WEB_LOGIN_RESEARCH.md` 和 `browser-login-completion-audit.ps1` 已同步。
- `DECISIONS.md` 新增 ADR-B032 和 ADR-B033。

验证：

- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 输出 `checkedAt=2026-07-04T23:53:03+08:00`、`status=incomplete`。
  - PASS：`docs`，新增 `ACCOUNT_VERIFICATION_NODES.md` 与 `LOGIN_TEST_STRATEGY.md` 已纳入必备文档链路。
  - PASS：`browser-unit-tests`，退出码 `0`。
  - PASS：`debug-apk-build`，退出码 `0`，debug APK 存在。
  - PASS：`oneplus-device-online`，`3f8bbaad device product:OnePlus8T_CH model:KB2000 device:OnePlus8T`。
  - PASS：`runner-refresh`、`runner-evidence-report`、`scheduled-continuation-task`。
  - MISS：`codex-account`、`claude-account`，证据为 `verifiedTargets=(none)`、`waitingTargets=codex, claude`。
  - 审计退出码为 `2`，符合当前只缺真实账号完成证据。
- `git diff --check -- docs scripts` 退出码为 `0`；仅提示部分 Markdown 后续被 Git 触碰时会做 LF/CRLF 换行转换。
- Windows 计划任务 `KiteBrowserLoginContinuationGate`：`LastRunTime=2026/7/4 23:50:13`、`LastTaskResult=2`、`NextRunTime=2026/7/4 23:55:12`、`NumberOfMissedRuns=0`。

### B5 [in_progress] 完成审计覆盖当前 OnePlus 在线状态

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 与浏览器线设备绑定要求，把 OnePlus 8T `3f8bbaad` 当前 ADB 在线状态纳入完成审计，避免最终验收时只依赖历史截图或误用 X11/MEIZU 设备。
2. 完成标准：`browser-login-completion-audit.ps1` 运行 `adb devices -l`，把输出写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\adb-devices-output.txt`；审计项 `oneplus-device-online` 要求 `3f8bbaad` 为 `device` 状态；当前账号未授权时，审计仍只把 Codex/Claude 账号完成项列为目标未完成缺口。
3. 前置任务：B0-B4 已完成；浏览器线绑定设备为 OnePlus 8T `3f8bbaad`；`references/toolchain.md` 已确认设备目标和 ADB 命令。

已实现：

- `scripts/browser-login-completion-audit.ps1` 新增 `adb devices -l` 检查，并把输出写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\adb-devices-output.txt`。
- 新增审计项 `oneplus-device-online`：只接受 `3f8bbaad` 为 `device` 状态，避免误把 MEIZU/X11 设备当作浏览器线目标。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 `DECISIONS.md` 已同步；新增 ADR-B031。

验证：

- `adb devices -l` 当前输出包含：
  - `181QGEYH222B9 device product:meizu_18_CN model:MEIZU_18 device:meizu18`
  - `3f8bbaad device product:OnePlus8T_CH model:KB2000 device:OnePlus8T`
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - PASS：`oneplus-device-online`，证据为 `exit=0`，设备行为 `3f8bbaad device product:OnePlus8T_CH model:KB2000 device:OnePlus8T`。
  - 整体仍为 `status=incomplete`，失败项只剩 `codex-account,claude-account`，退出码为 `2`。

### B5 [in_progress] 完成审计覆盖当前实现验证

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 与完成审计要求，把当前浏览器 handoff 单测和 `assembleDebug` 纳入 `browser-login-completion-audit.ps1 -RefreshState`，避免最终完成声明只依赖文件存在或历史构建记录。
2. 完成标准：审计在 `-RefreshState` 下运行 `:app:testDebugUnitTest --tests com.kite.app.browser.*` 和 `:app:assembleDebug`；审计报告记录退出码、日志路径和 APK 是否存在；当前账号未授权时，审计仍只把 Codex/Claude 账号完成项列为目标未完成缺口。
3. 前置任务：B0-B4 已完成；B5 已有通用 handoff 实现、浏览器包单测、完成审计和续跑链路；工具链说明已读取 `references/toolchain.md`。

已实现：

- `scripts/browser-login-completion-audit.ps1` 新增当前实现验证：
  - `browser-unit-tests`：`-RefreshState` 时运行 `:app:testDebugUnitTest --tests com.kite.app.browser.* --console=plain`。
  - `debug-apk-build`：`-RefreshState` 时运行 `:app:assembleDebug --console=plain`，并检查 `app/build/outputs/apk/debug/app-debug.apk` 存在。
  - 单测和构建输出分别写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-unit-test-output.txt` 与 `assemble-debug-output.txt`。
- `ACCOUNT_AUTH_COMPLETION_SOP.md` 已补充审计输出文件和当前验证范围。
- `PLAYBOOK.md` 和 `DECISIONS.md` 已同步；新增 ADR-B030。

验证：

- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - PASS：`browser-unit-tests`，证据为 `exit=0`，日志为 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-unit-test-output.txt`。
  - PASS：`debug-apk-build`，证据为 `exit=0`、`apkExists=True`，APK 为 `D:\xm\Kite-browser-login\app\build\outputs\apk\debug\app-debug.apk`。
  - `browser-unit-test-output.txt` 末尾显示 `BUILD SUCCESSFUL in 1s`，`41 actionable tasks: 1 executed, 40 up-to-date`。
  - `assemble-debug-output.txt` 末尾显示 `BUILD SUCCESSFUL in 1s`，`53 actionable tasks: 1 executed, 52 up-to-date`。
  - `2026-07-04 23:40:16` 复验整体仍为 `status=incomplete`，失败项只剩 `codex-account,claude-account`，退出码为 `2`。
- `git diff --check -- docs scripts` 退出码为 `0`；仅提示 Git 触碰部分 Markdown 时会做 LF/CRLF 换行转换。
- Windows 计划任务在 `2026/7/4 23:40:13` 自然触发，`LastTaskResult=2`，下一次计划触发为 `2026/7/4 23:45:12`，`NumberOfMissedRuns=0`。

### B5 [in_progress] 完成审计覆盖自动续跑链路

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 与完成审计要求，把 runner 自动生成 evidence report 和 Windows 计划任务指向浏览器线 runner 这两个续跑证据纳入完成审计，避免最终宣称完成时只检查账号状态而漏掉跨回合自动推进链路。
2. 完成标准：`browser-login-completion-audit.ps1 -RefreshState` 会刷新 runner 状态，并检查 `post-auth-evidence-report.md` 已由 runner 链路产生；审计会检查计划任务 `KiteBrowserLoginContinuationGate` 启用、动作指向 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`、包含 OnePlus 8T serial 且重复间隔不高于 5 分钟；当前真实账号未授权时，审计仍只把账号完成项列为目标未完成的强缺口。
3. 前置任务：B0-B4 已完成；B5 续跑 runner、evidence report、自测试、计划任务注册和完成审计脚本均已存在；真实账号授权缺口不能用审计增强替代。

已实现：

- `scripts/browser-login-completion-audit.ps1` 的 `-RefreshState` 现在会运行 runner 和续跑自测试；不再用审计脚本额外手动补跑 evidence report 来掩盖 runner 行为。
- 新增审计项 `continuation-self-test`：要求自测试脚本在 `-RefreshState` 下退出码为 `0`。
- 新增审计项 `runner-refresh`：要求 runner 退出码为 `0` 或 `2`，把退出码 `1` 视作环境或后置验证错误。
- 新增审计项 `runner-evidence-report`：要求 `post-auth-evidence-report.md` 存在、包含状态摘要，且在 `-RefreshState` 下更新时间不早于 `runner-status.json`。
- 新增审计项 `scheduled-continuation-task`：检查计划任务 `KiteBrowserLoginContinuationGate` 已启用，动作调用浏览器线物理副本的 runner，包含 `3f8bbaad`，重复间隔不高于 5 分钟。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 `DECISIONS.md` 已同步；新增 ADR-B029。

验证：

- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 输出 `status=incomplete`、`waitingTargets=codex,claude`。
  - PASS：`continuation-self-test`，证据为自测试退出码 `0`。
  - PASS：`runner-refresh`，证据为 `refreshRunnerExit=2`，即当前等待账号授权而非环境错误。
  - PASS：`runner-evidence-report`，证据为 report 与 runner 状态同轮更新，且包含 `状态：waiting_for_real_account_authorization`。
  - PASS：`scheduled-continuation-task`，证据为计划任务 `state=Ready`、动作调用 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`、包含 `3f8bbaad`、`interval=PT5M`。
  - MISS：`codex-account`、`claude-account`，证据为 `verifiedTargets=(none)`、`waitingTargets=codex, claude`。
  - 退出码为 `2`，符合当前只缺真实账号完成证据。
- 复验输出：
  - `.\scripts\test-browser-login-continuation.ps1` 输出 `browser-login-continuation-self-test passed`，退出码为 `0`。
  - `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 在 `2026-07-04 23:34:56` 输出 `status=incomplete`、`failedItemIds=codex-account,claude-account`，退出码为 `2`。
  - Windows 计划任务在 `2026/7/4 23:35:13` 自然触发，`LastTaskResult=2`，下一次计划触发为 `2026/7/4 23:40:12`，`NumberOfMissedRuns=0`。
  - 同次自然触发写入 `runner-status.json`：`postAuthAttempted=false`、`readyTargets=[]`、`waitingTargets=["codex","claude"]`、`nextAction=wait_for_real_account_authorization`。

### B5 [in_progress] runner 自动刷新 evidence report

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 与自动续跑方式，让本机计划任务每次跑完都留下可读的脱敏证据摘要，不再要求等下一轮或手动补跑 report 才能判断下一步。
2. 完成标准：runner 在等待态、ready/post-auth 成功态和异常态写入 `runner-status.json` 后自动刷新 `post-auth-evidence-report.md`；mock 自测试覆盖自动报告；真实 OnePlus 8T 当前未登录状态仍输出等待账号授权；完成审计仍只缺 Codex/Claude 真实账号证据。
3. 前置任务：B0-B4 已完成；B5 已有 gate、post-auth、evidence report、completion audit 和计划任务注册脚本；真实账号授权缺口仍不能绕过。

已实现：

- `scripts/browser-login-continuation-runner.ps1` 增加 evidence report 串联步骤，默认调用 `scripts/browser-login-evidence-report.ps1`。
- runner 在 gate 等待、gate 异常、post-auth 成功、post-auth 等待和 post-auth 失败分支都会先写 `runner-status.json`，再刷新 `post-auth-evidence-report.md`。
- `scripts/test-browser-login-continuation.ps1` 已增加断言：等待态和 ready 态 runner 都会自动写出 evidence report，并继续验证账号/API key 脱敏。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`COMPATIBILITY_MATRIX.md` 和 `DECISIONS.md` 已同步：`60` 分钟只是默认兜底，`-Minutes` 可调整；本机侧“完成一个，自动触发下一阶段”由 runner 在同一次运行内完成。

验证：

- `.\scripts\test-browser-login-continuation.ps1`：
  - 等待态：runner 退出 `2`，不触发 post-auth，自动写出 evidence report，报告保持 `waiting_for_real_account_authorization`。
  - ready 态：runner 退出 `0`，触发 post-auth，自动写出 evidence report，报告为 `post_auth_verified`，账号和 API key 已脱敏。
  - stale 态：旧 post-auth 失败状态不会污染当前等待态。
  - 输出 `browser-login-continuation-self-test passed`，退出码为 `0`。
- `.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad`：
  - 输出 `codex=account_required`、`claude=account_required`、`waitingTargets=codex,claude`。
  - 写入 `runner-status.json`：`postAuthAttempted=false`、`readyTargets=[]`、`waitingTargets=["codex","claude"]`。
  - 输出 `evidenceReportExit=2`、runner 退出码为 `2`，符合当前仍需真实账号授权。
- `%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md`：
  - `状态：waiting_for_real_account_authorization`。
  - `readyTargets：(none)`、`waitingTargets：codex, claude`。
  - Codex 为 `Not logged in`，Claude 为 `"loggedIn": false`。
- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 输出 `status=incomplete`。
  - `failedItemIds=codex-account,claude-account`。
  - 退出码为 `2`，符合当前只缺真实账号完成证据。
- `.\scripts\register-browser-login-continuation-gate.ps1 -Serial 3f8bbaad -Minutes 5 -Days 7`：
  - 已重新注册 Windows 计划任务 `KiteBrowserLoginContinuationGate`。
  - 触发器为 `RepetitionInterval=PT5M`、`RepetitionDuration=P7D`。
  - 动作为 PowerShell 7 调用 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`。
  - 手动触发后 `LastRunTime=2026/7/4 23:29:35`、`LastTaskResult=2`、`NumberOfMissedRuns=0`，`runner-status.json` 同步为继续等待 Codex/Claude 真实账号授权。
  - 首次自然触发已在 `2026/7/4 23:30:12` 运行，`LastTaskResult=2`，下一次计划触发为 `2026/7/4 23:35:12`，`NumberOfMissedRuns=0`。

### B5 [in_progress] 完成状态审计脚本

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5 和当前目标，把浏览器线完成判定固化成脚本，逐项检查调研、方案、实现、OnePlus 8T 证据、多站点矩阵和真实账号补证缺口。
2. 完成标准：新增只读审计脚本；能够输出 `complete` / `incomplete` / `error`；当前账号未授权时明确列出 Codex 与 Claude 账号证据缺失；可选刷新 runner/report；文档记录该审计不能替代真实账号授权。
3. 前置任务：B0-B4 已完成；B5 已有兼容矩阵、续跑 gate、post-auth、evidence report 和自测试脚本。

已实现：

- 新增 `scripts/browser-login-completion-audit.ps1`：
  - 检查浏览器线文档、实现文件、关键 OnePlus 8T 截图、续跑脚本和 runner 状态。
  - 检查 `verifiedTargets` 是否包含 `codex` 和 `claude`，作为账号完成证据。
  - 默认输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\completion-audit.json` 和 `completion-audit.md`。
  - 支持 `-RefreshState`，先运行 runner 和 evidence report，再审计。
  - 退出码 `0` 表示完成，退出码 `2` 表示证据不足，退出码 `1` 保留给环境/脚本错误。
- `PLAYBOOK.md` 已补充：宣称浏览器线完成前必须运行该审计并得到 `status=complete`。

验证：

- `.\scripts\browser-login-completion-audit.ps1 -RefreshState`：
  - 输出 `status=incomplete`。
  - `PASS docs`、`PASS implementation`、`PASS device-evidence`、`PASS continuation-scripts`、`PASS runner-state`。
  - `MISS codex-account`、`MISS claude-account`。
  - `failedItemIds=codex-account,claude-account`，退出码为 `2`，符合当前真实账号未授权状态。
- 最终复验：
  - `.\scripts\browser-login-completion-audit.ps1 -RefreshState` 在 `2026-07-04 23:20` 输出 `status=incomplete`、`waitingTargets=codex,claude`、`failedItemIds=codex-account,claude-account`，退出码为 `2`。
  - `.\scripts\test-browser-login-continuation.ps1` 输出 `browser-login-continuation-self-test passed`，退出码为 `0`。
  - `.\scripts\browser-login-evidence-report.ps1` 输出 `status=waiting_for_real_account_authorization`、`nextAction=wait_for_real_account_authorization`，退出码为 `2`。
  - `git diff --check -- docs scripts` 退出码为 `0`；仅提示 Git 触碰部分 Markdown 时会做 LF/CRLF 换行转换。
  - Windows 计划任务 `KiteBrowserLoginContinuationGate` 当前 `LastTaskResult=2`、`NextRunTime=2026-07-04 23:54:54`、`NumberOfMissedRuns=0`。

### B5 [in_progress] 续跑链路自测试脚本

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，把 gate、runner、post-auth、evidence report 的关键续跑分支固化成可重复自测试，避免后续账号 ready 时才发现自动链路退化。
2. 完成标准：新增自测试脚本；覆盖等待态不触发 post-auth、ready 态触发 post-auth 并生成 verified 报告、以及 stale post-auth 状态不会污染等待态；脚本通过后回写验证证据。
3. 前置任务：B5 的 gate、runner、post-auth 和 evidence report 脚本均已完成；ADR-B021/B022/B023/B024/B025 已固定只读、脱敏和完成驱动边界。

已实现：

- 新增 `scripts/test-browser-login-continuation.ps1`：
  - `wait` 场景：mock gate 返回 `2`，runner 不应触发 post-auth，evidence report 保持 `waiting_for_real_account_authorization`。
  - `ready` 场景：mock gate 返回 `0`，runner 应触发 mock post-auth，evidence report 生成 `post_auth_verified`，并脱敏邮箱与 API key。
  - `stale` 场景：runner 显示 `postAuthAttempted=false` 时，evidence report 应忽略旧的 `post-auth-status.json` 失败状态。
- `PLAYBOOK.md` 已补充：修改 gate、runner、post-auth 或 evidence report 后，先跑该自测试脚本。

验证：

- `.\scripts\test-browser-login-continuation.ps1`：
  - `PASS wait runner exits 2`
  - `PASS wait runner does not attempt post-auth`
  - `PASS ready runner exits 0`
  - `PASS ready evidence report redacts account`
  - `PASS ready evidence report omits api key`
  - `PASS stale evidence report ignores stale post-auth failed target`
  - 输出 `browser-login-continuation-self-test passed`，退出码为 `0`。
- 最终复验：
  - `.\scripts\test-browser-login-continuation.ps1` 在 `2026-07-04 23:05` 再次输出 `browser-login-continuation-self-test passed`，退出码为 `0`。
  - `.\scripts\browser-login-evidence-report.ps1` 输出 `status=waiting_for_real_account_authorization`、`nextAction=wait_for_real_account_authorization`，退出码为 `2`。
  - `git diff --check -- docs scripts` 退出码为 `0`；仅提示 Git 触碰部分 Markdown 时会做 LF/CRLF 换行转换。
  - Windows 计划任务 `KiteBrowserLoginContinuationGate` 当前 `LastTaskResult=2`、`NextRunTime=2026-07-04 23:54:54`、`NumberOfMissedRuns=0`。

### B5 [in_progress] 后置账号证据报告脚本

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，在不依赖真实账号授权、不读取 token 的前提下，补齐账号 ready 后的证据整理入口，让后续 Codex/Claude 完成登录时可以稳定生成脱敏摘要。
2. 完成标准：新增脚本从 `last-status.json`、`runner-status.json`、`post-auth-status.json` 和脱敏 raw 输出生成 Markdown 摘要；当前未登录状态能生成“继续等待账号授权”的报告；mock 已验证状态能生成 `verifiedTargets` 摘要并返回成功；文档和 ADR 写清楚该报告不是登录事实来源。
3. 前置任务：B5 gate、post-auth、runner 已完成；ADR-B020/B023/B024 已固定官方状态命令、非交互补证和 runner 汇总边界。

已实现：

- 新增 `scripts/browser-login-evidence-report.ps1`：
  - 默认读取 `%LOCALAPPDATA%\Kite\browser-login-continuation` 下的 `last-status.json`、`runner-status.json`、`post-auth-status.json` 和 `post-auth-raw.txt`。
  - 默认输出 `%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md`。
  - 邮箱、token、API key、Bearer token 和 callback code 类字段会再次脱敏。
  - 如果 `runner-status.json` 显示 `postAuthAttempted=false`，报告会忽略旧的 `post-auth-status.json` / `post-auth-raw.txt`，避免历史强制 probe 的失败状态污染当前等待结论。
- `ACCOUNT_AUTH_COMPLETION_SOP.md`、`COMPATIBILITY_MATRIX.md`、`PLAYBOOK.md` 已补充报告脚本用法。
- 新增 ADR-B025，固定报告只整理脱敏状态，不作为登录事实来源。

验证：

- 真实 OnePlus 8T 当前等待态：
  - `.\scripts\browser-login-evidence-report.ps1`
  - 输出 `status=waiting_for_real_account_authorization`、`verifiedTargets=`、`failedTargets=`、`nextAction=wait_for_real_account_authorization`。
  - 报告中 `readyTargets=(none)`、`waitingTargets=codex, claude`、Codex 为 `Not logged in`、Claude 为 `"loggedIn": false`。
  - 退出码为 `2`，符合“未完成账号授权”的门槛状态。
- 临时 mock 已验证状态：
  - mock `readyTargets=["codex"]`、`verifiedTargets=["codex"]`、`postAuthAttempted=true`。
  - 报告输出 `status=post_auth_verified`、`verifiedTargets=codex`，退出码为 `0`。
  - mock raw 中的 `user@example.com` 被写成 `<account>`；`sk-SECRET...` 未进入摘要。
- 最终复验：
  - `git diff --check -- docs scripts` 退出码为 `0`；仅提示 Git 触碰部分 Markdown 时会做 LF/CRLF 换行转换。
  - `.\scripts\browser-login-evidence-report.ps1` 在 `2026-07-04 23:02` 输出 `status=waiting_for_real_account_authorization`、`nextAction=wait_for_real_account_authorization`，退出码为 `2`。
  - Windows 计划任务 `KiteBrowserLoginContinuationGate` 仍为 `LastTaskResult=2`，下一次触发时间 `2026-07-04 23:54:54`，`NumberOfMissedRuns=0`。

### 调度规则 [done] runner 状态摘要与完成驱动复验

三问自检：

1. 目标：把“定时是否只能一小时”和“完成一个能否自动触发下一阶段”固定为可执行、可复验的调度规则。
2. 完成标准：文档说明 `-Minutes` 可配置且下限为 5 分钟；runner 在 ready 时同一次运行自动接 post-auth；runner 每次写 `runner-status.json`；真实未登录状态、mock ready 和 mock wait 分支都通过复验。
3. 前置任务：B5 gate、post-auth、runner 已存在；ADR-B021/B022/B023/B024 已固定只读、脱敏、按账号粒度和非交互边界。

已实现：

- `PLAYBOOK.md` 与 `ACCOUNT_AUTH_COMPLETION_SOP.md` 明确 `60` 分钟只是默认兜底间隔，注册脚本可通过 `-Minutes` 调整，当前下限为 5 分钟。
- `ACCOUNT_AUTH_COMPLETION_SOP.md` 记录 `runner-status.json`，作为跨回合总摘要。
- ADR-B024 补充 `runner-status.json` 的影响范围。

验证：

- 真实 OnePlus 8T 未登录状态：
  - `.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad`
  - 输出 `codex=account_required`、`claude=account_required`、`waitingTargets=codex,claude`。
  - runner 退出码 `2`，`runner-status.json` 写入 `postAuthAttempted=false`、`readyTargets=[]`、`waitingTargets=["codex","claude"]`、`verifiedTargets=[]`、`failedTargets=[]`。
- 临时 mock ready 状态：
  - mock gate 退出 `0` 并写 `readyTargets=["codex"]`。
  - runner 同一次运行调用 mock post-auth，写入 `verifiedTargets=["codex"]`。
  - `runner-status.json` 写入 `exitCode=0`、`postAuthAttempted=true`、`readyTargets=["codex"]`、`verifiedTargets=["codex"]`。
- 临时 mock wait 状态：
  - mock gate 退出 `2` 并写 `waitingTargets=["codex","claude"]`。
  - runner 未调用 post-auth，退出码 `2`，临时目录没有生成 `post-auth-status.json`。
- 计划任务复验：
  - `.\scripts\register-browser-login-continuation-gate.ps1 -Serial 3f8bbaad -Minutes 60 -Days 7` 重新注册后，任务动作确认为 `pwsh.exe` 调用 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`。
  - 手动触发计划任务后，`LastRunTime=2026/7/4 22:53:54`、`LastTaskResult=2`、`NumberOfMissedRuns=0`。
  - 首次自然触发后，`LastRunTime=2026/7/4 22:54:54`、`LastTaskResult=2`、`NextRunTime=2026/7/4 23:54:54`、`NumberOfMissedRuns=0`。
  - `%LOCALAPPDATA%\Kite\browser-login-continuation\runner-status.json` 写入 `postAuthAttempted=false`、`readyTargets=[]`、`waitingTargets=["codex","claude"]`、`verifiedTargets=[]`、`failedTargets=[]`。

### 调度规则 [done] runner 串联 gate 与 post-auth

三问自检：

1. 目标：把计划任务从“只写 gate 状态”推进为“同一次唤醒内 gate 发现 ready 后立即接 post-auth 补证”。
2. 完成标准：新增 runner 脚本；gate 返回 `2` 时不运行 post-auth；gate 返回 `0` 时运行 post-auth 并传递 `-UseExistingGateState`；计划任务注册脚本改为调用 runner；真实未登录状态仍返回 `2`；mock ready 状态能自动接 post-auth。
3. 前置任务：B5 gate 脚本和 post-auth 脚本已完成；ADR-B021/B022/B023 已固定只读、脱敏、按账号粒度和非交互边界。

已实现：

- 新增 `scripts/browser-login-continuation-runner.ps1`：
  - 先运行 `browser-login-continuation-gate.ps1 -WriteState`。
  - gate 退出 `2` 时输出 `nextAction=wait_for_real_account_authorization`，直接退出 `2`。
  - gate 退出 `0` 时运行 `browser-login-post-auth-verify.ps1 -UseExistingGateState -WriteState`。
  - post-auth 退出 `0` 时 runner 退出 `0`；post-auth 退出 `1` 时 runner 退出 `1`；post-auth 退出 `2` 时 runner 退出 `2`。
- `scripts/register-browser-login-continuation-gate.ps1` 改为注册 runner 作为计划任务动作，仍使用 PowerShell 7。
- 新增 ADR-B024，固定计划任务应串联 gate 与 post-auth。

验证：

- 真实 OnePlus 8T 未登录状态：
  - `.\scripts\browser-login-continuation-runner.ps1 -Serial 3f8bbaad`
  - gate 输出 `codex=account_required`、`claude=account_required`、`waitingTargets=codex,claude`。
  - runner 输出 `gateExit=2`、`nextAction=wait_for_real_account_authorization`，退出码 `2`。
- 临时 mock ready 状态：
  - mock gate 退出 `0` 并写 `readyTargets=["codex"]`。
  - runner 自动调用 mock post-auth，post-auth 写 `verifiedTargets=["codex"]`。
  - runner 输出 `postAuthExit=0`、`nextAction=record_post_auth_completion_evidence`，退出码 `0`。
- 临时 mock wait 状态：
  - mock gate 退出 `2` 并写 `waitingTargets=["codex","claude"]`。
  - runner 没有调用 mock post-auth，退出码 `2`。
- 计划任务复验：
  - `.\scripts\register-browser-login-continuation-gate.ps1 -Serial 3f8bbaad -Minutes 60 -Days 7` 重新注册后，任务动作已切到 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`。
  - 手动触发计划任务后，`Get-ScheduledTaskInfo -TaskName KiteBrowserLoginContinuationGate` 显示 `LastRunTime=2026/7/4 22:46:46`、`LastTaskResult=2`、`NumberOfMissedRuns=0`。
  - 注册后的首次自动触发在 `2026/7/4 22:47:36` 自然运行，收敛后 `LastTaskResult=2`、`NextRunTime=2026/7/4 23:47:36`、`NumberOfMissedRuns=0`。
  - `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 写入 `readyTargets=[]`、`waitingTargets=["codex","claude"]`、`nextAction=wait_for_real_account_authorization`。

### B5 [in_progress] 后置账号补证脚本

三问自检：

1. 目标：在 B5 的真实账号授权门槛之后，提供一个可重复、非敏感、非交互的补证入口，避免下次账号 ready 时还要临时判断该跑什么。
2. 完成标准：脚本默认只处理 `readyTargets`；没有 ready 账号时退出 `2` 且不运行后置 probe；Codex ready 后使用官方 `codex login status` 与 `codex doctor --json`；Claude ready 后使用官方 `claude auth status --json`；状态文件和 raw 输出脱敏账号邮箱、token、API key、callback code；文档和 ADR 写清楚边界。
3. 前置任务：B5 账号状态门槛脚本已建立；ADR-B020 要求账号完成证据采用官方状态命令；ADR-B012 要求不伪造 provider callback。

命令确认：

- `codex doctor --help`：官方说明为“Diagnose local Codex installation, config, auth, and runtime health”，支持 `--json`，说明会输出 redacted machine-readable report。
- `claude auth status --help`：官方说明为“Show authentication status”，支持 `--json`。
- `claude doctor --help`：说明会检查自动更新、跳过工作区 trust dialog，并可能启动 `.mcp.json` 里的 stdio servers；因此不作为账号补证默认命令。

已实现：

- 新增 `scripts/browser-login-post-auth-verify.ps1`：
  - 默认先刷新 `scripts/browser-login-continuation-gate.ps1` 并读取 `readyTargets`。
  - 没有 `readyTargets` 时写入 `post-auth-status.json`，退出 `2`，不运行后置 probe。
  - Codex ready 时运行 `codex --version`、`codex login status`、`codex doctor --json`。
  - Claude ready 时运行 `claude --version`、`claude auth status --json`。
  - 支持 `-PlanOnly` 用于只验证选择逻辑，不运行 probe。
  - 支持 `-Targets` 强制验证指定目标；用于诊断时若未登录会失败，不会误判成功。
- `scripts/browser-login-auth-status.ps1` 增加输出脱敏，避免账号邮箱、token、API key、callback code 原文进入日志。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`DECISIONS.md`、`COMPATIBILITY_MATRIX.md` 已同步；新增 ADR-B023。

验证：

- 当前真实未登录状态：
  - `.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad` 输出 Codex `Not logged in`、Claude `"loggedIn": false`。
  - `.\scripts\browser-login-post-auth-verify.ps1 -Serial 3f8bbaad -WriteState` 输出 `readyTargets=`、`selectedTargets=`、`nextAction=wait_for_real_account_authorization`，退出码 `2`。
  - `%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-status.json` 写入 `readyTargets=[]`、`verifiedTargets=[]`、`skippedTargets=["codex","claude"]`。
- 临时 mock plan-only 验证：
  - `readyTargets=["codex"]` 时，`-PlanOnly` 输出 `selectedTargets=codex`，退出码 `0`，JSON 中 `readyTargets` 与 `selectedTargets` 保持数组。
  - `readyTargets=["claude"]` 时，`-PlanOnly` 输出 `selectedTargets=claude`，退出码 `0`，JSON 中 `readyTargets` 与 `selectedTargets` 保持数组。
- 强制 probe 失败路径：
  - 当前未登录状态运行 `.\scripts\browser-login-post-auth-verify.ps1 -Serial 3f8bbaad -Targets codex,claude -WriteState`。
  - 输出 `failedTargets=codex,claude`、`nextAction=inspect_post_auth_probe_output`，退出码 `1`，说明未登录不会被误判为完成。
- 最终复验：
  - `.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad` 在 `2026-07-04 22:42` 仍输出 Codex `Not logged in`、Claude `"loggedIn": false`。
  - `.\scripts\browser-login-post-auth-verify.ps1 -Serial 3f8bbaad -WriteState` 在同一轮输出 `nextAction=wait_for_real_account_authorization`，退出码 `2`。
  - `git diff --check -- docs scripts` 无格式错误；仅提示若 Git 触碰部分文档会进行 LF/CRLF 换行转换。

### 调度规则 [done] 按账号粒度续跑门槛

三问自检：

1. 目标：在 B5 账号授权门槛下，把续跑唤醒从“Codex 和 Claude 都完成才触发”改成“任一账号完成就触发对应后置验证”。
2. 完成标准：门槛脚本输出 `readyTargets`、`waitingTargets`、`errorTargets`；任一账号进入已登录候选时退出码为 `0`；当前两个账号均未登录时仍退出码为 `2`；计划任务使用新脚本语义并能写入状态文件。
3. 前置任务：跨回合续跑门槛脚本已建立；B5 账号完成证据以官方状态命令为准；不读取 token、不伪造 callback 的红线继续适用。

已实现：

- `scripts/browser-login-continuation-gate.ps1` 改为按账号分别归类：
  - `readyTargets`：已进入后置 CLI 可用性验证窗口的账号。
  - `waitingTargets`：仍需真实账号授权的账号。
  - `errorTargets`：状态输出缺失或环境异常的账号。
- 退出码语义调整：
  - `0`：至少一个账号可做后置验证。
  - `2`：当前没有任何账号可做后置验证，且仍在等待真实授权。
  - `1`：没有账号可做后置验证，且存在环境或状态输出异常。
- `scripts/register-browser-login-continuation-gate.ps1` 的计划任务描述已同步为“至少一个账号 ready 即返回 0”。
- `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md`、`DECISIONS.md` 已同步新语义；新增 ADR-B022。

验证：

- 当前 OnePlus 8T 真实状态：
  - `.\scripts\browser-login-continuation-gate.ps1 -Serial 3f8bbaad`
  - 输出 `codex=account_required`、`claude=account_required`、`readyTargets=`、`waitingTargets=codex,claude`、`errorTargets=`、`readyForPostAuthVerification=False`。
  - 退出码为 `2`，符合当前两个账号均未登录的真实状态。
- 临时 mock 状态验证：
  - Codex 已登录候选、Claude 未登录时，输出 `readyTargets=codex`、`waitingTargets=claude`、退出码 `0`。
  - Claude 已登录、Codex 未登录时，输出 `readyTargets=claude`、`waitingTargets=codex`、退出码 `0`。
- 计划任务复验：
  - 重新注册 `KiteBrowserLoginContinuationGate` 后，任务动作仍使用 `C:\Users\19437\AppData\Local\Programs\PowerShell\7\pwsh.exe`。
  - 手动触发计划任务后，`Get-ScheduledTaskInfo -TaskName KiteBrowserLoginContinuationGate` 显示 `LastRunTime=2026/7/4 22:33:13`、`LastTaskResult=2`、`NumberOfMissedRuns=0`。
  - 注册后的首次自动触发在 `2026/7/4 22:34:06` 自然运行，收敛后 `LastTaskResult=2`、`NextRunTime=2026/7/4 23:34:06`、`NumberOfMissedRuns=0`。
  - `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 写入 `readyTargets=[]`、`waitingTargets=["codex","claude"]`、`errorTargets=[]`、`nextAction=wait_for_real_account_authorization`。

### 调度规则 [done] 跨回合续跑门槛脚本

三问自检：

1. 目标：在 `PLAYBOOK.md` 的自动续跑方式下，把“完成后触发下一步”和“每小时兜底复验”落成可执行的只读门槛，而不是只停留在对话说明。
2. 完成标准：提供可由外部调度器调用的脚本；脚本只运行官方账号状态命令，不读取 token、不伪造 callback；退出码能区分“可继续后置验证”“仍需账号授权”“环境异常”；文档写清楚计划任务边界。
3. 前置任务：B0 三件套已存在；B5 已有 `scripts/browser-login-auth-status.ps1` 作为官方状态命令封装；ADR-B020 已固定账号完成证据来源。

已实现：

- 新增 `scripts/browser-login-continuation-gate.ps1`：
  - 调用 `scripts/browser-login-auth-status.ps1`。
  - 解析 Codex `codex login status` 与 Claude `claude auth status`。
  - 输出 `codexGateState`、`claudeGateState`、`readyTargets`、`waitingTargets`、`errorTargets`、`readyForPostAuthVerification` 和 `nextAction`。
  - 退出码 `0` 表示至少一个账号进入后置 CLI 可用性验证窗口，`2` 表示当前没有账号可后置验证且仍需真实账号授权，`1` 表示环境或输出异常。
- 新增 `scripts/register-browser-login-continuation-gate.ps1`：
  - 注册 Windows 计划任务 `KiteBrowserLoginContinuationGate`。
  - 默认每 60 分钟运行一次，持续 7 天。
  - 只把最近状态写到 `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 和 `last-status-raw.txt`。
- 已更新 `PLAYBOOK.md`、`ACCOUNT_AUTH_COMPLETION_SOP.md` 和 `DECISIONS.md`，明确跨回合不能靠 Codex 自发唤醒，必须由外部调度器或用户消息触发。

待验证：

- 已完成。

验证：

- `.\scripts\browser-login-continuation-gate.ps1 -Serial 3f8bbaad`：
  - `codex=account_required`
  - `claude=account_required`
  - `readyTargets=` 为空
  - `waitingTargets=codex,claude`
  - `errorTargets=` 为空
  - `readyForPostAuthVerification=False`
  - `nextAction=wait_for_real_account_authorization`
  - 脚本退出码为 `2`，符合“仍需真实账号授权”的门槛状态。
- `.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad` 直接复验输出仍为 Codex `Not logged in`、Claude `"loggedIn": false`。
- `.\scripts\register-browser-login-continuation-gate.ps1 -Serial 3f8bbaad -Minutes 60 -Days 7`：
  - 已注册 Windows 计划任务 `KiteBrowserLoginContinuationGate`。
  - 任务动作使用 `C:\Users\19437\AppData\Local\Programs\PowerShell\7\pwsh.exe`，避免 Windows PowerShell 5.1 与手动验证的远端 shell 引号差异。
  - 手动触发计划任务后，`Get-ScheduledTaskInfo -TaskName KiteBrowserLoginContinuationGate` 显示 `LastRunTime=2026/7/4 22:28:18`、`LastTaskResult=2`、`NumberOfMissedRuns=0`。
  - 首次定时触发后，`Get-ScheduledTaskInfo -TaskName KiteBrowserLoginContinuationGate` 显示 `LastRunTime=2026/7/4 22:29:11`、`LastTaskResult=2`、`NextRunTime=2026/7/4 23:29:11`、`NumberOfMissedRuns=0`。
  - `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 写入 Codex `Not logged in`、Claude `"loggedIn": false`、`nextAction=wait_for_real_account_authorization`。

### B5 [in_progress] Codex / Claude 账号授权完成门槛复验

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，在不读取 token、不伪造 callback 的前提下，用 CLI 官方状态命令确认 OpenAI/Codex 与 Claude Code 是否已经完成账号授权，并把后续补证入口固定下来。
2. 完成标准：能在 OnePlus 8T `3f8bbaad` 上通过 PRoot 运行 `codex login status` 与 `claude auth status`；输出不包含凭据内容；若仍未登录，明确记录为账号授权缺口而不是实现失败；提供可重复检查脚本/SOP。
3. 前置任务：B4 通用 handoff 已完成；B5 已验证 Codex/Claude 均可到账号页和 pending callback；ADR-B012 要求不能伪造 provider callback。

已实现：

- 新增 `scripts/browser-login-auth-status.ps1`，使用 `proot-launch-contract.json` 同等边界下的 PRoot 参数，只运行：
  - `codex --version`
  - `codex login status`
  - `claude --version`
  - `claude auth status`
- 新增 `docs/browser-login/ACCOUNT_AUTH_COMPLETION_SOP.md`，记录账号完成补证的只读复验步骤和判定标准。
- 状态复验只输出 CLI 版本和官方登录状态，不读取 auth json、token、cookie 或 callback code。

真机结果：

- `.\scripts\browser-login-auth-status.ps1 -Serial 3f8bbaad`：
  - Codex：`codex-cli 0.142.4`，`Not logged in`。
  - Claude Code：`2.1.201 (Claude Code)`，`{"loggedIn": false, "authMethod": "none", "apiProvider": "firstParty"}`。

当前结论：

- OpenAI/Codex 和 Claude Code 的浏览器 handoff、账号页、pending session、终端保留、loopback 可达性/官方 fallback 提示都已有证据。
- 两个 CLI 的真实账号授权完成证据仍缺失，且当前真机状态明确为未登录；下一步必须由真实账号授权或官方 fallback 完成后再复验，不能伪造 callback。

### B5 [in_progress] auth session 原始授权 URL 落盘脱敏

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，补强 browser auth session 持久化边界，避免 OAuth 授权 URL 中的 `state`、`code_challenge`、`client_id` 等临时值通过 `originalUrl` 原样落盘。
2. 完成标准：session 落盘只保存 OAuth 授权 URL 的脱敏摘要；重复 handoff 精确匹配改用不可逆请求指纹；不同 `state` 的授权请求不能因为脱敏摘要相同而误认为同一个 pending；补单测并通过浏览器包测试、构建和 OnePlus 8T prefs/日志检查。
3. 前置任务：B4 已实现 handoff session；B5 已完成 callback `returnedUrl` 脱敏和 handoff diagnostics 脱敏；真实 OpenAI/Claude 账号授权 callback 仍待外部账号补证。

已实现：

- `BrowserHandoffPolicy.requestKey(...)` 为完整授权请求生成 SHA-256 请求指纹，`findPending(...)` 继续用原始请求在内存中计算指纹做精确匹配。
- `BrowserHandoffPolicy.stateKey(...)` 为 OAuth `state` 生成 SHA-256 指纹；`BrowserAuthSessionStore` 落盘只保留 `state=present` 和 `stateKey`，不保存原始 `state`。
- `BrowserAuthSessionStore.createPending(...)` 的 `originalUrl` 改为 `BrowserHandoffPolicy.redactedUrlForDiagnostics(...)` 输出的协议摘要；旧 session 读取时会从旧原文计算指纹并进入内存脱敏形态。
- 额外把 `browser_external_reopened`、`card_run_manual_web_open`、`open_web_surface_suppressed`、`web_ready_probe_*` 和 parsed run report 的 URL 诊断字段接入同一脱敏函数；实际打开、重开、复制用的运行 URL 不改。

新增单测：

- `BrowserAuthSessionStoreTest.createPendingPersistsRedactedOriginalUrlAndRequestKey`
- `BrowserAuthSessionStoreTest.findPendingSeparatesOauthRequestsWithDifferentState`

真机验证：

- OnePlus 8T 触发带唯一 `state=kite-session-secret-state-1783174032372` 和 `code_challenge=kite-session-secret-challenge-1783174032372` 的 Google OAuth App redirect URL。
- 最新 `kite_browser_auth_sessions.xml` session 为 `kind=AppRedirect`、`source=card_run_surface`、`status=Pending`。
- `originalUrl` 为 `https://accounts.google.com/o/oauth2/v2/auth?response_type=present&client_id=present&redirect_uri=kite_app&scope=present&state=present&code_challenge=present&code_challenge_method=present`。
- 同一 session 只保存 `requestKey`、`state=present`、`stateKey`，不保存原始 `state` 或 `code_challenge`。
- `adb -s 3f8bbaad shell "run-as com.kite.app sh -c 'grep -R \"kite-session-secret-state-1783174032372\" shared_prefs files/diagnostics 2>/dev/null || true'"`：无输出。
- `adb -s 3f8bbaad shell "run-as com.kite.app sh -c 'grep -R \"kite-session-secret-challenge-1783174032372\" shared_prefs files/diagnostics 2>/dev/null || true'"`：无输出。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `git diff --check -- app docs references`：无 diff 格式错误；仅提示若 Git 触碰部分文件会进行 LF/CRLF 换行转换。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `curl.exe --max-time 5 http://127.0.0.1:18791/status`：返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `adb -s 3f8bbaad logcat -d -t 1200` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

### B5 [in_progress] handoff 事件日志授权 URL 脱敏

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，补强真实网页登录 handoff 发起阶段的日志边界，避免 OAuth 授权 URL 中的 `state`、`code_challenge`、`login_hint` 等临时值原样进入诊断事件。
2. 完成标准：`browser_auth_handoff_opened`、`browser_auth_handoff_open_failed`、CLI loopback handoff 实例事件和 OAuth 外部打开事件写入脱敏 URL 摘要；普通非 OAuth URL 日志不被误改；补单测并通过浏览器包测试、构建和 OnePlus 8T 健康检查。
3. 前置任务：B4 已有 URL 分类和 handoff 事件；B5 已补 returnedUrl 脱敏；真实 OpenAI/Claude 账号授权 callback 仍待外部账号完成。

已实现：

- 新增 `BrowserHandoffPolicy.redactedUrlForDiagnostics(...)`：仅 OAuth 授权请求进入脱敏摘要，普通非 OAuth URL 保持原样。
- OAuth 诊断摘要保留 `scheme://host/path` 和参数存在性，例如 `response_type=present`、`client_id=present`、`redirect_uri=loopback|kite_app|https`、`state=present`、`code_challenge=present`。
- `browser_auth_handoff_opened` / `browser_auth_handoff_open_failed`、`browser_external_opened` / `browser_external_open_failed`、`browser_request_*`、CLI loopback handoff 实例事件均改为写入脱敏 URL。
- `KiteDiagnostics.logOpenWebAttempt(...)`、`logOpenWebFailed(...)`、`logWebError(...)`、`logExternalUrl(...)`、`writeWebAppStatus(...)` 统一使用同一脱敏函数兜底。

新增单测：

- `BrowserHandoffPolicyTest.oauthAuthorizationDiagnosticUrlIsRedacted`
- `BrowserHandoffPolicyTest.ordinaryDiagnosticUrlIsKept`

真机验证：

- 第一次用带 `state=kite-log-redaction-secret-state-1783173160433` 和 `code_challenge=kite-log-redaction-secret-challenge-1783173160433` 的 Google OAuth URL 触发 `/open-web` 后，发现 `recipe-events.jsonl` 已脱敏，但 `last-webapp-status.json` 仍保存原始 OAuth URL；该漏点已修复。
- 重新安装后，用 `state=kite-log-redaction-secret-state-1783173254545` 和 `code_challenge=kite-log-redaction-secret-challenge-1783173254545` 再次触发 `/open-web`。
- `adb -s 3f8bbaad shell run-as com.kite.app grep -R "kite-log-redaction-secret-state-1783173254545" files/diagnostics`：无输出，说明新 secret 未进入 diagnostics。
- `recipe-events.jsonl` 中 `browser_request_opened_temporary_instance` 和 `browser_external_opened` 均记录 `https://accounts.google.com/o/oauth2/v2/auth?response_type=present&client_id=present&redirect_uri=https&scope=present&state=present&code_challenge=present&code_challenge_method=present`。
- `last-webapp-status.json` 中 `url` 同样为上述脱敏摘要，`openTarget=system_browser`。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `curl.exe --max-time 5 http://127.0.0.1:18791/status`：首次重启后有一次本地服务未完全恢复导致 `Empty reply from server`，等待 2 秒重试返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `adb -s 3f8bbaad logcat -d -t 1200` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

### B5 [in_progress] 回跳 returnedUrl 敏感参数脱敏

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，补强真实账号授权 callback 到来后的安全落盘边界，避免 `code` / token 类参数以原文进入 browser auth session。
2. 完成标准：session 只保存脱敏后的 returnedUrl；`code`、`access_token`、`id_token`、`refresh_token`、`token` 等敏感参数只记录 present；query 和 fragment 参数都能解析 state/error；补单测并通过浏览器包测试、构建和 OnePlus 8T 健康检查。
3. 前置任务：B4 已有 App redirect 骨架；B5 已补 AppRedirect/CLI loopback 匹配边界；真实 OpenAI/Claude 账号授权 callback 仍待外部账号完成。

已实现：

- `BrowserAuthRedirectParser` 解析 `kite-auth://callback` 的 query 和 fragment 参数，fragment 中的 `state` 能参与会话匹配。
- `BrowserAuthRedirect` 新增 `redactedUrl`，只保留 `error/code/access_token/id_token/refresh_token/token/state=present` 这类存在性标记。
- `BrowserAuthSessionStore.markReturned(...)` 写入 `redirect.redactedUrl`，不再把原始 callback URL 中的 `code` 或 token 类参数落盘到 `returnedUrl`。
- 读取旧 session 时，如果旧 `returnedUrl` 是 `kite-auth://callback`，会按同一 parser 得到脱敏形态后进入内存。

新增单测：

- `BrowserHandoffPolicyTest.parsesKiteAuthRedirectFragmentWithoutExposingTokens`
- `BrowserAuthSessionStoreTest.markReturnedRedactsFragmentTokensBeforePersisting`
- 更新 `parsesKiteAuthRedirect` 和 `markReturnedAcceptsMatchingAppRedirectSession`，断言 `code=present` 而不是授权 code 原文。
- 更新 provider error 测试，断言 `returnedUrl` 只记录 `error=present&state=present`，具体错误仍在 `failureReason`。

压力边界：

- 状态拥有者仍是 `BrowserAuthSessionStore`；没有新增登录事实来源。
- 没有新增整页重绘、WebView 重建、轮询或渲染时重型探测。
- 终端/Web/报告显示面逻辑未改；CLI loopback 仍由 CLI localhost callback 或官方 fallback 确认最终登录事实。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity`：启动成功，前台为 `com.kite.app/com.kite.app.MainActivity`。
- `curl.exe --max-time 5 http://127.0.0.1:18791/status`：返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `adb -s 3f8bbaad logcat -d -t 1200` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

### B5 [in_progress] App redirect 回跳匹配边界补强

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，继续扩展多站点兼容矩阵所需的通用回跳安全边界，确保 App deep link 回跳不会误消费 CLI loopback pending session。
2. 完成标准：`kite-auth://callback` 只匹配 `AppRedirect` 且 redirect URI 属于 Kite 的 pending session；state 不匹配、会话过期、CLI loopback 同 state 都不能被标记为 returned；补单测并通过浏览器包测试。
3. 前置任务：B4 已实现 session store 与 redirect 入口；B5 已验证 Codex/Claude CLI loopback pending 真实存在，账号授权 callback 仍待外部账号完成。

已实现：

- `BrowserAuthSessionStore.markReturned(...)` 只匹配 `kind=AppRedirect` 且 `redirectUri` 为 `kite-auth://callback` 的 pending session。
- CLI loopback session 即使 state 与 App callback 相同，也不会被 `kite-auth://callback` 消费。
- 过期 AppRedirect session 不会被真实或迟到 callback 重新标记为 returned。
- provider 返回 `error` 时仍进入 `Failed`，不把失败 callback 当成功交付。

新增单测：

- `markReturnedAcceptsMatchingAppRedirectSession`
- `markReturnedKeepsMismatchedStatePending`
- `markReturnedDoesNotConsumeCliLoopbackWithSameState`
- `markReturnedDoesNotReviveExpiredAppRedirectSession`
- `markReturnedRecordsProviderErrorAsFailed`

压力边界：

- 状态拥有者仍是 `BrowserAuthSessionStore`；没有新增登录事实来源。
- 没有新增整页重绘、WebView 重建、轮询或渲染时重型探测。
- 终端/Web/报告显示面逻辑未改；CLI loopback 继续由终端和 CLI 自己确认最终登录事实。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity`：启动成功，前台为 `com.kite.app/com.kite.app.MainActivity`。
- `curl.exe --max-time 5 http://127.0.0.1:18791/status`：返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `adb -s 3f8bbaad logcat -d -t 1200` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

### 调度规则 [done] 完成驱动续跑

三问自检：

1. 目标：明确浏览器线是按完成状态自动续跑，还是只能固定每小时触发。
2. 完成标准：把续跑边界写入三件套；说明当前回合内可完成一个接一个继续，跨回合需要平台、用户消息或外部调度器唤醒。
3. 前置任务：B0 已建立三件套，当前目标处于 active 状态。

已确认：

- 当前目标状态为 active。
- 本任务线采用“完成驱动 + 外部唤醒兜底”：当前回合完成一个任务后，会立刻寻找下一个就绪任务继续。
- Codex 不能在会话休眠、没有平台续跑或没有外部消息时自己发起下一回合；每小时定时只能作为外部唤醒兜底。
- 规则已写入 `PLAYBOOK.md` 的 `0.1 自动续跑方式` 和 `DECISIONS.md` 的 ADR-B009。

### B5 [in_progress] 扩展多站点兼容矩阵

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B5，建立兼容矩阵，按站点类型验证官方回跳、Custom Tabs、系统浏览器、普通网页登录和失败兜底。
2. 完成标准：至少覆盖 Google、OpenAI/ChatGPT、Anthropic/Claude；每个场景有设备、截图、日志或错误证据；不把策略绕过当作默认成功路径；记录仍需用户账号或外部权限的验证缺口。
3. 前置任务：B4 已完成，Google WebView 失败和 Custom Tabs 分流均已有 OnePlus 8T 证据。

本轮要产出：

- 新增 `docs/browser-login/COMPATIBILITY_MATRIX.md`：已完成。
- 将已验证 Google 证据写入矩阵：已完成。
- 将 OpenAI/Codex、Claude Code 需要真实 CLI/账号链路的缺口写清楚，不冒充已完成：已完成。

当前未完成项：

- Codex/OpenAI 已跑到真实 OpenAI 登录页，设备侧也能直连 CLI loopback listener；完整账号授权后的 code exchange 尚未完成验证。
- Claude Code 已跑到真实 Claude 登录页，终端显示 paste code fallback；完整账号授权后的 localhost callback 或 paste code 尚未完成验证。
- CLI loopback relay 目前已有分类、session、终端保留和设备可达性/监听证据；如果后续 provider callback 失败，再实现 relay 或走官方 fallback。

协议分类补强：

- 三问自检：
  1. 目标：补齐不依赖账号的标准 OAuth/OIDC 分类缺口，让组合型 `response_type` 和 IPv6 loopback redirect 也进入正确 handoff。
  2. 完成标准：`response_type=code id_token` 等组合值能被识别为 OAuth 授权请求；`http://[::1]:<port>/callback` 能被识别为 CLI loopback；普通本地 IPv6 Web UI 仍留在 WebView；补单测并通过浏览器包测试。
  3. 前置任务：B4 已有 `BrowserHandoffPolicy` 和单测，B5 已有 OpenAI/Claude 真实 loopback 证据。
- 已实现：
  - `BrowserHandoffPolicy` 将空格或 `+` 分隔的组合 `response_type` 按 OAuth/OIDC 语义识别。
  - `BrowserHandoffPolicy.isLoopbackRedirectUri(...)` 支持 `localhost`、`127.0.0.1`、`::1` 和 `0:0:0:0:0:0:0:1`。
  - 普通 `http://[::1]:port` 本地 Web UI 仍返回 `StayInWebView`。
- 新增单测：
  - `localIpv6HttpUrlStaysInWebView`
  - `ipv6LoopbackRedirectStartsCliCallbackHandoff`
  - `oidcHybridResponseTypeStartsExternalAuthHandoff`
- 验证：
  - `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
  - `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
  - `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
  - `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity` 后，`adb -s 3f8bbaad logcat -d -t 800` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

外部 redirect 分流：

- 三问自检：
  1. 目标：把 Kite 可接收的 App redirect 与只能回到第三方 HTTPS 页的 OAuth redirect 分开，避免对不可接收的 redirect 建立“等待回 Kite”的假 session。
  2. 完成标准：`kite-auth://callback` 继续进入 `StartAuthHandoff`；`https://...` 这类非 Kite 可接收 redirect 的 OAuth 授权 URL 只外部打开，不创建 AppRedirect session；CardRun Web surface 给出“已在系统浏览器打开”的可见状态；补单测并通过构建/真机健康检查。
  3. 前置任务：B4 已实现 Custom Tabs handoff 和 `kite-auth://callback` 回跳，B5 已验证 Google OAuthPlayground 的 redirect 是第三方 HTTPS 页面。
- 已实现：
  - `BrowserHandoffPolicy.isKiteAppRedirectUri(...)` 只接受 `kite-auth://callback`。
  - OAuth 授权 URL 如果 `redirect_uri` 是 loopback，继续走 `CliLoopback`；如果是 `kite-auth://callback`，继续走 `AppRedirect`；如果是第三方 HTTPS redirect，则返回 `OpenExternalBrowser`。
  - CardRun Web surface 对 `OpenExternalBrowser` 渲染外部浏览器占位页，并用 Custom Tabs / 系统浏览器打开 URL，不创建 browser auth session。
- 新增单测：
  - `oauthAuthorizationWithExternalHttpsRedirectOpensExternalBrowser`
- 设备验证：
  - 触发 URL：`https://accounts.google.com/o/oauth2/v2/auth?...redirect_uri=https%3A%2F%2Fdevelopers.google.com%2Foauthplayground...state=kite-external-redirect-test...`
  - `/open-web` 返回 `accepted=true`。
  - 触发前后 `kite_browser_auth_sessions.xml` session 数均为 `4`，未新增 `AppRedirect` session。
  - 前台 Activity 为 `com.heytap.browser/com.android.browser.BrowserActivity`，说明授权页离开 WebView、进入系统浏览器。
  - `adb -s 3f8bbaad logcat -d -t 1200` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。
- 备注：
  - 本轮曾尝试截图 CardRun 外部占位页，但无实例 `/open-web` 回到 Kite 后显示首页，不能作为占位页证据，已删除该无效截图。当前设备证据只证明“不新增 AppRedirect session + 系统浏览器打开”。

Pending/Expired 校准：

- 三问自检：
  1. 目标：补齐登录 handoff pending 超时后的可解释状态，避免旧 session 永久停在等待。
  2. 完成标准：过期的 Pending session 能落盘为 `Expired`；运行面同步不重建整页、不轮询、不伪造 provider callback；CLI active run 存在时保留 Terminal，active run 不存在时不凭空创建运行实例。
  3. 前置任务：B4 已实现 session store，B5 已跑出 Codex/Claude 的真实 CliLoopback pending 证据。
- 已实现：
  - `BrowserAuthSessionStore.expirePending()` 将超时 Pending 标为 `Expired`。
  - `expiredNeedingRuntimeSync()` 与 `runtimeNotifiedAt` 把“过期事实”和“运行面已同步”分离，recipe 暂不可得时允许后续前台恢复再尝试。
  - `MainActivity.onResume()` 做一次轻量校准；不轮询、不重建 WebView、不调用整页刷新。
  - 如果 active CardRun 仍存在，CLI loopback 过期时保持 `Terminal`；如果 active run 已不存在，只保留 session 层过期事实，不新建假运行面，并写入 `runtimeNotifiedAt` 让校准幂等收敛。
- 真机结果：
  - OnePlus 8T 上 Claude Code session `8269424e4f4b4b10a186c64d0052c7d7` 已从 `Pending` 变为 `Expired`，`failureReason=expired`。
  - 旧 Codex/Claude 运行记录当前只在 `history_v1`，`runs_v1` 无 active run，因此未写入新的 CardRun 运行面；这是预期边界，不做历史旁路改写。
  - 重新安装并前台恢复后，旧 Claude/Codex 三条 `CliLoopback` expired session 均已补上 `runtimeNotifiedAt`：`1783171358535`、`1783171358567`、`1783171358590`。
  - `adb -s 3f8bbaad logcat -d -t 800` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`、`browser_auth_session_expired`：无匹配。

本轮新增证据：

- 当前真实运行实例：`resource-kite.codex.cli-open`，`terminalSessionId=shell-space-main-1783168995508`。
- 截图 `docs/browser-login/evidence/codex-cli-current-state.png`：Codex CLI 首次登录菜单，默认可选择 ChatGPT 登录。
- 通过 `KFShellService` 以应用 UID 向当前终端发送 `1` 并回车后，Codex CLI 输出真实授权 URL：`https://auth.openai.com/oauth/authorize?...redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback...originator=codex-tui`。
- 截图 `docs/browser-login/evidence/codex-cli-openai-browser.png`：OnePlus 8T 系统浏览器显示 OpenAI 登录页。
- `shared_prefs/kite_browser_auth_sessions.xml`：新增 `kind=CliLoopback`、`recipeId=resource-kite.codex.cli-open`、`source=terminal_step`、`redirectUri=http://localhost:1455/auth/callback`、`status=Pending`。
- `shared_prefs/kite_card_run_store.xml`：同一实例保持 `surface=Terminal`、`terminalSessionId=shell-space-main-1783168995508`、`nextActionUrl=""`，说明 browser handoff 没有抢占终端显示面。
- 截图 `docs/browser-login/evidence/codex-cli-loopback-terminal-preserved.png`：回到 Kite 后仍显示 Codex CLI 等待浏览器登录结果。
- `adb -s 3f8bbaad shell "ss -ltnp 2>/dev/null | grep ':1455' || netstat -ltn 2>/dev/null | grep ':1455' || true"`：设备侧存在 `127.0.0.1:1455` listener。
- `adb -s 3f8bbaad shell curl --max-time 5 -i http://127.0.0.1:1455/`：返回 `HTTP/1.1 404 Not Found` 与 `Server: tiny-http (Rust)`，证明 Android 设备侧 localhost 能直接到达当前 Codex CLI callback server。
- `adb -s 3f8bbaad logcat -d -t 1200`：未发现 `AndroidRuntime`、`FATAL EXCEPTION` 或 ANR；可见 `auth.openai.com/log-in` 页面加载日志。

Claude Code 当前证据：

- 资源定义：`assets/resources/kite.claude.code/manifest.json` 的 open action 会执行 `cd /workspace` 后运行 `claude`。
- 真机启动：`adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity --es runtime_action start_resource_open --es com.kite.app.extra.RESOURCE_INSTALL_TARGET_ID kite.claude.code`。
- 当前运行实例：`resource-kite.claude.code-open`，`terminalSessionId=shell-space-main-1783169827350`。
- 截图 `docs/browser-login/evidence/claude-code-resource-start.png`：安装前终端输出 `bash: claude: command not found`。
- 安装触发：`adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity --es runtime_action start_resource_install --es com.kite.app.extra.RESOURCE_INSTALL_TARGET_ID kite.claude.code`。
- 安装结果：`recipe-events.jsonl` 记录 `resource-kite.claude.code-install` 在 `2026-07-04T13:01:50Z` `status=Completed`，有效输出为 `Claude Code installed by manifest action`；官方脚本输出 `Claude Code successfully installed`、版本 `2.1.201`。
- 命令暴露：`files/runtime/shared/ubuntu-main/.kf/bin/claude -> /workspace/.kf/software/kite.claude.code/user-home/.local/bin/claude`。
- 截图 `docs/browser-login/evidence/claude-code-theme-prompt.png`：Claude Code v2.1.201 首次启动主题选择。
- 截图 `docs/browser-login/evidence/claude-code-after-theme.png`：Claude Code 登录方式选择。
- 选择 Claude account 后，终端显示 fallback：`Browser didn't open? Use the url below ... Paste code here if prompted >`。
- `recipe-events.jsonl` 记录 `browser_auth_handoff_opened`：`recipeId=resource-kite.claude.code-open`、`kind=CliLoopback`、`source=terminal_step`、`redirect_uri=http://localhost:43299/callback`。
- 截图 `docs/browser-login/evidence/claude-code-login-method.png`：OnePlus 8T 系统浏览器显示 Claude 登录页。
- `shared_prefs/kite_browser_auth_sessions.xml`：Claude session `8269424e4f4b4b10a186c64d0052c7d7` 为 `kind=CliLoopback`、`redirectUri=http://localhost:43299/callback`、`status=Pending`。
- `shared_prefs/kite_card_run_store.xml`：`resource-kite.claude.code-open` 保持 `surface=Terminal`、`terminalSessionId=shell-space-main-1783169827350`、`nextActionUrl=""`。
- `adb -s 3f8bbaad shell "ss -ltnp 2>/dev/null | grep ':43299' || netstat -ltn 2>/dev/null | grep ':43299' || true"`：设备侧存在 `127.0.0.1:43299` listener。
- `adb -s 3f8bbaad shell curl --max-time 5 -i http://127.0.0.1:43299/`：连接未返回 HTTP 响应并在 5 秒后超时；未伪造 `/callback`，避免向 CLI 注入假 code。
- 当前结论：Claude Code 的真实浏览器登录链路已验证到账号页和 pending callback；完整登录完成仍需用户账号或可用授权 code。

本轮验证：

- `git diff --check -- app docs references`：无 diff 格式错误；仅提示若 Git 触碰部分文件会进行 LF/CRLF 换行转换。
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.BrowserHandoffPolicyTest" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- Google OAuthPlayground 第三方 HTTPS redirect 触发后，`kite_browser_auth_sessions.xml` session 数保持 `4`，未新增 `AppRedirect` session；前台为系统浏览器。
- `shared_prefs/kite_browser_auth_sessions.xml`：Claude/Codex 旧 expired sessions 均为 `status=Expired`、`failureReason=expired`，且 `runtimeNotifiedAt` 已写入非空时间戳。
- `adb -s 3f8bbaad logcat -d -t 1800` 过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`Application Not Responding`、`ANR in com.kite.app`、`Input dispatching timed out`：无匹配。

### B4 [done] 实现最小通用登录回跳

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B4，按 B3 设计实现最小可验证链路，优先复用现有 bridge、LocalServer 和 Activity/Intent 模式。
2. 完成标准：OnePlus 8T 上能从 Kite 发起登录并回到正确运行实例或浏览器上下文；失败时有可解释状态；不新增 provider 单点特判；有相关单测或集成测试保护回跳解析；完成构建、安装、截图和 logcat 检查。
3. 前置任务：B0/B1/B2/B3 已完成；工具链契约已补到 `references/toolchain.md`；当前压力通道判定为 Web Surface，状态拥有者为 `CardRunStore` 与一次性 handoff session。

压力分诊：

- 症状或功能：WebView 中 OAuth 登录被 provider 拒绝，需要切到外部浏览器并回跳。
- 可见显示面：CardRun Web surface / `KiteWebShell`。
- 压力风险：误用 `showCardRunSurface(...)` 重建 WebView、复制 `CardRunStore` 状态、在渲染路径做重型探测。
- 通道：Web Surface + Card Run State。
- 状态拥有者：`CardRunStore` 保存运行实例显示状态；新增 browser auth session 只保存一次 handoff 安全状态。
- 事件来源：WebView URL 跳转、容器 `/open-web` 请求、Android redirect Intent。
- 可见消费者：CardRun Web surface、终端/运行实例状态行。
- 触及的热路径：`KiteWebShell.shouldOverrideUrlLoading(...)`、`MainActivity.handleBrowserOpenRequest(...)`、`CardRunStore` 更新。
- 禁止的大范围刷新：不从普通状态变化调用整页 `showCardRunSurface(...)`，不因登录等待状态重建 WebView。
- 验证证据：分类器/redirect 单测、Gradle 构建、OnePlus 8T 安装、截图和 logcat。

已实现：

- 新增 `BrowserHandoffPolicy`：基于 OAuth 参数和 redirect URI 分类，不按 provider 域名写死。
- 新增 `BrowserAuthSessionStore`：保存一次性 handoff session，支持 pending、returned、delivered、failed、expired。
- `KiteWebShell` 在初始加载和 WebView 跳转前进行 handoff 分类。
- `MainActivity` 使用 Chrome Custom Tabs 打开授权页，失败时降级系统浏览器。
- `AndroidManifest.xml` 增加 `kite-auth://callback` 的 `ACTION_VIEW` + `BROWSABLE` 回跳入口。
- CardRun Web surface 对 OAuth URL 显示等待页，并提供“重新打开”“复制地址”。

验证证据：

- 新增单测：`BrowserHandoffPolicyTest`。
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.BrowserHandoffPolicyTest" --console=plain`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug --console=plain`：`BUILD SUCCESSFUL`。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：`Success`。
- `curl.exe --max-time 5 http://127.0.0.1:18791/status`：返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- Google OAuth URL 通过 `/open-web` 打开后，前台为 `com.heytap.browser/com.android.browser.BrowserActivity`，不再是 Kite WebView。
- 截图：`docs/browser-login/evidence/google-oauth-custom-tabs-handoff.png`。
- 模拟 `kite-auth://callback?state=kite-webview-test...` 后，前台回到 `com.kite.app/.CardRunActivity` 的同一 `instanceId`。
- session prefs 从 `Pending` 更新到 `Delivered`。
- 截图：`docs/browser-login/evidence/browser-auth-callback-return.png`。
- `adb -s 3f8bbaad logcat -d -t 800` 未发现 `AndroidRuntime`、`FATAL EXCEPTION` 或 ANR。
- `.\gradlew.bat :app:testDebugUnitTest --console=plain`：`BUILD SUCCESSFUL`；末尾 Robolectric 临时目录清理报 `DirectoryNotEmptyException`，但 Gradle 任务成功。

### B3 [done] 设计 Kite 登录回跳协议

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B3，设计 provider-agnostic 的浏览器 handoff 协议，明确启动、回跳、状态确认、失败展示和重试路径。
2. 完成标准：方案包含 external browser / Custom Tabs 路线；包含 App Links 或可验证 redirect 入口；包含 PKCE/code exchange 安全边界；不要求伪造浏览器环境；说明如何兼容内置浏览器里的非 OAuth 普通登录。
3. 前置任务：B1 已完成，当前 WebView 失败类别已有 OnePlus 8T 证据；B2 已完成，官方路线和反路线已写入 `WEB_LOGIN_RESEARCH.md`。

本轮要产出：

- 新增 `docs/browser-login/LOGIN_HANDOFF_DESIGN.md`：已完成。
- 追加必要 ADR，固定 B4 的默认实现方向：已完成 ADR-B006、ADR-B007。

验收对照：

- external browser / Custom Tabs 路线：已写入第 4 节。
- App Links 或可验证 redirect 入口：已写入第 5 节。
- PKCE/code exchange 安全边界：已写入第 6 节。
- 不要求伪造浏览器环境：已写入第 2 节和 ADR-B006。
- 兼容内置浏览器里的非 OAuth 普通登录：已写入第 2 节、第 3 节和第 10 节。

### B1 [done] 确认当前内置浏览器和回跳真实链路

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B1，确认当前内置浏览器、LocalServer、Intent/deep link、回调桥接和登录状态写入点的真实链路。
2. 完成标准：列出当前登录入口、跳转入口、回跳入口和状态拥有者；记录 Google 报错原文/URL 参数/触发页面环境；判断失败类别；本任务不修改代码。
3. 前置任务：B0 已完成；浏览器物理副本 `D:\xm\Kite-browser-login` 已创建；分支 `codex/browser-login-return` 已创建；OnePlus 8T `3f8bbaad` 在线。

已确认环境：

- `git status --short --branch`：`## codex/browser-login-return`，无未提交代码改动。
- `adb devices -l`：OnePlus 8T `3f8bbaad` 在线，MEIZU 18 也在线但本任务不使用。

下一步：

- 在 OnePlus 8T 上复现至少一个 Google/CLI 登录失败场景，记录报错原文、授权 URL 参数和截图。
- 基于调研结果拆 B3 设计：普通 WebView 与 OAuth handoff 分流、Custom Tabs/系统浏览器、回跳 session、CLI localhost callback relay/fallback。

已确认真实代码链路：

- 容器侧 `BROWSER` 被 `KiteBrowserProxyInstaller` 指向 `/workspace/.kf/bin/kite-open-url`。
- `kite-open-url` 把 URL POST 到 Android 本地 `http://127.0.0.1:8791/open-web`。
- `KiteLocalServer` 解析 `url`、`recipeId`、`cardInstanceId/instanceId`、`source` 后调用 `handleBrowserOpenRequest(...)`。
- `CardRunBrowserRouter` 按 `instanceId` 分发到当前 CardRun；否则请求会 pending 或创建临时网页 CardRun。
- `updateBrowserRequestState(...)` 把 `surface=Web` 和 `nextActionUrl` 写入 `CardRunStore`。
- `KiteWebShell.shouldStayInWebView(...)` 对 `browser_proxy`、`ubuntu_browser`、`terminal_page`、`terminal_step`、`shell_step` 的普通 `http/https` URL 返回 true，因此第三方登录页会留在内嵌 WebView。
- `AndroidManifest.xml` 目前没有 OAuth callback 用的 `ACTION_VIEW` + `BROWSABLE` deep link / App Link。
- `app/build.gradle` 目前没有 `androidx.browser`、Custom Tabs 或 AppAuth 依赖，只有 `androidx.webkit`。

OnePlus 8T 复现证据：

- 构建：`.\gradlew.bat :app:assembleDebug --console=plain` 返回 `BUILD SUCCESSFUL`。
- 安装：`adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk` 返回 `Success`。
- 启动：`adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity`。
- 端口：`adb -s 3f8bbaad forward tcp:18791 tcp:8791`。
- Host `/status`：`curl.exe --max-time 5 http://127.0.0.1:18791/status` 返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- Device `/status`：`adb -s 3f8bbaad shell curl --max-time 5 http://127.0.0.1:8791/status` 返回同样 JSON。
- 触发页：`CardRunActivity` 临时网页实例 `run_temp_google_oauth_webview_test2`。
- 授权 URL：`https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=407408718192.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fdevelopers.google.com%2Foauthplayground&scope=openid%20email&state=kite-webview-test&prompt=consent`。
- Google 页面原文要点：`禁止访问：“Google OAuth 2.0 Playground”的请求不符合 Google 的相关政策`，`错误 403: disallowed_useragent`。
- 截图：`docs/browser-login/evidence/google-oauth-webview-after-wake.png`。

当前判断：

- 用户所见 Google 报错已经能在上述链路中复现，失败类别判定为 `embedded user-agent / WebView OAuth 被 provider 拒绝`。
- 不优先判为密钥缺失、cookie 缺失、TLS 问题或单纯 UA 参数问题。

已写入文档：

- `docs/browser-login/CURRENT_CHAIN.md`
- `docs/browser-login/WEB_LOGIN_RESEARCH.md`

### B2 [done] 官方推荐和通用网站登录回跳模式调研

三问自检：

1. 目标：引用 `PLAYBOOK.md` 的 B2，查官方资料和成熟库文档，覆盖 Google、OAuth Native Apps、Chrome Custom Tabs、AppAuth、App Links，以及 ChatGPT/Claude Code 等网页登录回跳约束。
2. 完成标准：至少 5 个可追溯来源；区分官方要求、社区经验和推断；明确 WebView、Custom Tabs、系统浏览器、无指纹/伪装环境的风险；输出适配 Kite 的实现路线和反路线。
3. 前置任务：B1 的代码链路部分已完成，真机报错原文仍待补；本轮先完成不依赖账号的官方资料调研，B2 不标记 done。

已确认资料结论：

- Google OAuth embedded webview 会被阻止；Google 建议把 WebView OAuth 迁移到 Chrome Custom Tabs。
- RFC 8252 要求 native app 使用外部 user-agent，不应使用 embedded user-agent，并要求校验 redirect URI 与会话状态。
- Google installed apps OAuth 路线是系统浏览器 + 本地 redirect + code verifier/challenge。
- Chrome Custom Tabs 共享用户浏览器状态，避免 App 自己管理 cookie/权限/请求。
- AppAuth-Android 支持 Custom Tabs、custom URI scheme 和 Android App Links。
- Android App Links 需要 manifest intent-filter、`autoVerify=true`、`assetlinks.json` 和设备验证。
- OpenAI Codex 和 Claude Code 说明 CLI 登录还需要处理 localhost callback、device code 或 paste callback URL 这类 headless/container 场景。

已写入：

- `docs/browser-login/WEB_LOGIN_RESEARCH.md`

### B0 [done] 建立浏览器任务基线

三问自检：

1. 目标：把浏览器登录线从 X11 线中分离出来，固定物理目录、分支、设备、端口和后续研究方向。
2. 完成标准：浏览器三件套存在；双线隔离说明写明浏览器线绑定 OnePlus 8T `3f8bbaad`；不开始代码改动和资料调研实现。
3. 前置任务：无。

已完成：

- 新增 `docs/browser-login/PLAYBOOK.md`。
- 新增 `docs/browser-login/PROGRESS.md`。
- 新增 `docs/browser-login/DECISIONS.md`。
- 新增 `docs/parallel-workstreams/README.md`，记录浏览器线和 X11 线的目录、设备、端口和隔离规则。

本次不做：

- 不实现登录回跳。
- 不继续替用户搜资料。
- 不复制物理目录。
- 不创建分支或会话。
