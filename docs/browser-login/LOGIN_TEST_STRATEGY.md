# 浏览器登录测试策略

最后更新：2026-07-05

本文件用于回答“人工 Google / OpenAI / Claude 账号验证前，Kite 自己能先测到什么程度”。结论先写在前面：Kite 可以把浏览器环境、回跳入口、状态归属、敏感信息边界和体验稳定性测到高置信度；但验证码、MFA、账号风控、OAuth client 配置和 provider 账号策略不能由 Kite 保证。

## 1. 置信度分层

| 层级 | 目标 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| C0 官方合规 | 对照 Google、Chrome、RFC 8252 等资料 | 主路径是否符合官方推荐，不走 WebView/UA 伪装 | 某个账号一定不会触发风控 |
| C1 本地确定性测试 | 单测和 mock 续跑 | URL 分类、回跳匹配、状态过期、脱敏、续跑分支稳定 | 真实浏览器和账号页面表现 |
| C2 真机无账号测试 | OnePlus 8T 探测授权主机网络、解析 HTTPS 浏览器 handler 并打开真实授权页但不输入账号 | 设备侧 HTTPS 是否能到达主要授权主机、HTTPS 授权 URL 是否有外部浏览器 handler、是否离开 WebView、外部 provider 页面是否没有阻塞性错误信号、从请求到外部浏览器前台是否足够快、Custom Tabs 不可用时系统浏览器 fallback 是否实测生效、是否无崩溃、是否不抢终端 | 完整登录成功 |
| C3 账号门槛测试 | 到达真实登录页、验证码/MFA/授权确认或 paste code 提示 | provider 接受当前浏览器环境，Kite 已到 N2/N3 | 用户是否完成账号所有权证明 |
| C4 人工账号完成 | 用户真实完成登录，优先用 `manual-account-start -StartWatch` 拉起 Codex/Claude 终端登录入口并接 account watch；已有登录入口时可直接用 `account-watch -RunSmokeFirst -RunReadinessFirst` 陪跑并运行官方状态命令；可用 `-RunCompletionAuditOnVerified` 在 verified 后同轮接完成审计 | N4/N5，状态拥有者确认已登录且后置健康检查通过；完成审计能立即判断是否还缺其他账号或证据 | provider 未来策略不会变化 |

因此，“100%”只能用于 Kite 机制自身的确定性范围：例如同 state 回跳不串线、OAuth 不进入 WebView、敏感 code 不落盘、没有 ready 账号时 runner 不误触发 post-auth。对 Google 人工验证，只能做到“移除已知不合规原因，并把剩余风险明确到账号/配置/provider 层”。

### 1.1 通过率置信模型

用户最后人工验证能否一次通过，不是一个单点布尔值，而是几层条件相乘。Kite 能负责把前几层尽量做到确定，不能替 Google/OpenAI/Claude 决定账号挑战是否放行。

| 条件层 | Kite 可自动证明程度 | 当前证明方式 | 剩余真人/外部变量 |
| --- | --- | --- | --- |
| 浏览器合规 | 接近确定 | OAuth URL 不进 WebView；前台包不是 Kite；UI dump 不含 `disallowed_useragent` / `unsupported_browser`；Google 官方资料复核 | provider 未来策略变化、用户默认浏览器被换成异常 handler |
| provider 配置早期错误 | 高置信 | UI dump 扫描 `redirect_uri_mismatch`、`invalid_client`、`Error 400/403` 等阻塞信号；preflight 分桶 | 真实 client/redirect/consent screen 只有 provider 返回后才最终确定 |
| 网络和性能 | 高置信趋势 | 授权主机 HTTPS probe 带重试 attempts；`/open-web` p95；外部浏览器前台切换 p95；小时级 long-run | 当场网络波动、设备负载、浏览器冷启动策略 |
| 回跳/CLI callback | 对 Kite 协议接近确定，对真实 CLI 高置信 | AppRedirect 同 state 交付；CliLoopback session 不抢终端；设备侧 listener 探测；Claude paste code fallback 可见 | 真实授权后 provider 是否回到 CLI listener，或用户是否使用官方 device/paste fallback |
| 敏感边界 | 接近确定 | smoke 扫 app 私有文本文件、auth session 和 diagnostics，确认本轮 code/token/state 原文命中为 0 | 第三方 CLI 自己的 token 存储由对应 CLI 负责 |
| 账号所有权 | 不能自动保证 | 只能用 account watch / post-auth 官方状态命令收证 | 密码、验证码、MFA、账号风控、组织策略、人工是否完成授权 |

所以后续我自己跑测试的目标不是承诺“账号 100% 会过”，而是把失败面压缩到最后一行：如果失败，优先能明确是账号挑战、provider 配置、callback/fallback、浏览器环境，还是性能/网络。

## 2. 官方依据

当前复核的官方资料。2026-07-05 再次核对后，结论没有变化：Google OAuth 的正式路线仍是外部 user-agent、合适的 OAuth client / redirect、PKCE 和真实账号交互，不是 WebView 伪装。

2026-07-05 06:05 再核对后，测试口径继续按这几条执行：

- Google OAuth Policies 页面最近修改记录为 2025-12-15；要求每个平台使用匹配的 OAuth client、只使用拥有或获授权的 redirect domain、不得把 Google OAuth 请求导向开发者可控 embedded user-agent。
- Google installed apps 文档仍强调安装式应用要打开系统浏览器并用本地 redirect URI 处理响应；`disallowed_useragent` 仍归类为授权端点显示在被禁止的 embedded user-agent 中。
- RFC 8252 仍把 external user-agent、state/PKCE、private scheme / claimed HTTPS / loopback redirect 作为 native app 主线；loopback 对 CLI/desktop 合理，但 Google 对 Android/iOS/Chrome OAuth client 已有 loopback migration 限制，所以 Kite App 自己接 Google 应走 custom scheme 或未来 App Link，Codex/Claude CLI 则按各自官方 fallback 收证。
- Chrome Auth Tab 已是后续体验优化候选，但它要求浏览器能力和新版 `androidx.browser`，当前 OnePlus 默认浏览器没有可查询 Custom Tabs service，因此系统浏览器 fallback 仍是必须保留的主路径。
- Codex 官方文档把 device code、auth cache 和 localhost forwarding 作为 headless / localhost callback 受阻时的 fallback；Claude Code 官方文档明确浏览器无法回到本地 callback 时可把 login code 粘回终端，并用 `claude auth status` 输出 JSON 状态。

- Google OAuth 2.0 Policies：每个平台应有匹配的 OAuth client；不要把 native Android/iOS App 当作 web client；不能把 Google OAuth 请求导向开发者可控的 embedded user-agent；redirect URI / JavaScript origin 必须符合安全上下文和所有权规则。
  - https://developers.google.com/identity/protocols/oauth2/policies
- Google Help `Remediation for OAuth via WebView`：Google 建议把 WebView 里的 OAuth 请求替换成 Chrome Custom Tabs。
  - https://support.google.com/faqs/answer/12284343
- Google Developers Blog `Upcoming security changes... embedded webviews`：Google OAuth endpoint 阻止 embedded webviews，原因是 WebView 可拦截请求、注入脚本、访问 cookie 或修改页面。
  - https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/
- Google OAuth installed apps 文档：安装式应用应打开系统浏览器，并使用本地 redirect URI 接收授权响应。
  - https://developers.google.com/identity/protocols/oauth2/native-app
- Google OOB / loopback migration 文档：OOB copy/paste flow 已废弃；Android/iOS/Chrome OAuth client 不应再使用 loopback IP redirect，desktop client 仍可使用 loopback。这意味着 Kite App 自己接 Google 时应优先 App/claimed link 路线，而容器内 Codex/Claude 这类 CLI 仍按各自官方 desktop/headless 方案验证。
  - https://developers.google.com/identity/protocols/oauth2/resources/oob-migration
  - https://developers.google.com/identity/protocols/oauth2/resources/loopback-migration
- RFC 8252：原生应用应使用外部 user-agent，redirect 可用 private-use URI scheme、claimed HTTPS URI 或 loopback。
  - https://datatracker.ietf.org/doc/html/rfc8252
- AppAuth-Android：实现 RFC 8252 最佳实践，授权请求使用 Custom Tabs，明确不支持 WebView；支持 custom URI scheme 和 Android App Links。
  - https://github.com/openid/AppAuth-Android
- Android App Links / Digital Asset Links：claimed HTTPS redirect 需要在网站发布 `.well-known/assetlinks.json`，声明 app package 和签名证书指纹，并通过 HTTPS 无重定向访问。
  - https://developer.android.com/training/app-links/configure-assetlinks
  - https://developers.google.com/identity/credential-sharing/digital-asset-links
- Chrome Auth Tab 文档：Auth Tab 是面向认证流程的 Custom Tab 形态；Chrome 137 起可替换现有 Custom Tabs 认证集成，不能用时自动 fallback 到 Custom Tabs。
  - https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- Chrome Custom Tabs warmup / prefetch 文档：`warmup()` 可提前启动浏览器进程，`mayLaunchUrl()` 可预取页面，用于降低打开外部认证页的体感延迟。
  - https://developer.chrome.com/docs/android/custom-tabs/guide-warmup-prefetch
- OpenAI Codex 官方认证文档：Codex CLI 默认可通过浏览器登录；headless / localhost callback 受阻时优先 device code，后备方案包括复制 auth cache 或转发 localhost callback。
  - https://developers.openai.com/codex/auth
- Claude Code 官方认证文档：Claude Code 首次启动会打开浏览器；如果浏览器不能回到本地 callback，页面显示 login code 时可粘贴回终端；`claude auth status` 是非交互状态确认入口。
  - https://code.claude.com/docs/en/authentication
  - https://code.claude.com/docs/en/cli-reference


对当前 Kite 的判断：

- 已实现的 Custom Tabs / 系统浏览器 handoff 符合 Google 对 WebView OAuth remediation 的方向。
- 当前 `androidx.browser:browser:1.8.0` 覆盖 Custom Tabs；Auth Tab 属于后续体验增强候选，不阻塞当前 B5 完成。
- 当前 OnePlus 8T 默认浏览器未暴露可查询的 Custom Tabs service，所以系统 `ACTION_VIEW` fallback 是必要路径；后续做 Auth Tab 或 warmup 前，必须先做能力探测，不把 Chrome 专属能力当作所有设备都有。
- 如果未来要让 Google OAuth 真正回到 Kite 自己的 App redirect，需要 provider 侧接受并配置 Kite 可接收的 redirect URI；OAuth Playground 这类第三方 HTTPS redirect 只能证明浏览器环境，不证明回 Kite。

## 3. Google 人工验证前的测试方法

### G1 负控和正控对照

目标：证明原失败原因已经从 Kite 主路径移除。

自动入口：

```powershell
.\scripts\browser-login-smoke-test.ps1
```

输出：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.json
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.md
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke.png
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-ui.xml
```

多轮趋势入口：

```powershell
.\scripts\browser-login-smoke-watch.ps1 -Iterations 6 -IntervalSeconds 600
```

`smoke-watch` 会反复调用 smoke test，输出：

```text
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-watch.json
%LOCALAPPDATA%\Kite\browser-login-continuation\browser-login-smoke-watch.md
```

它适合我长期自己跑测试时使用：观察成功率、`/open-web` p95、外部浏览器前台切换 p95、默认 HTTPS handler 是否变化、OpenAI/Claude OAuth 形态是否误建 auth session、OAuth 临时值是否原文落盘。它不输入账号、不读取 token、不伪造 provider callback。

步骤：

1. 用同一个 Google OAuth 授权 URL 在 Kite WebView 中复现 `403: disallowed_useragent`，作为负控。
2. 用 Kite 当前 handoff 路径打开同一个 URL，验证前台不是 Kite WebView，而是系统浏览器或 Custom Tabs。
3. 截图或记录前台 Activity，确认没有再次出现 `disallowed_useragent`。

当前状态：已完成。`google-oauth-webview-after-wake.png` 是负控，`google-oauth-custom-tabs-handoff.png` 是正控。

当前自动化状态：`browser-login-smoke-test.ps1` 已能在 OnePlus 8T 上运行。它会先用设备侧 HTTPS probe 确认 `accounts.google.com`、`auth.openai.com`、`claude.ai` 的网络路径可达，再解析 `https://accounts.google.com/` 的默认 `ACTION_VIEW` 浏览器 handler，记录 Custom Tabs service 能力诊断，然后证明 Google、OpenAI/Codex、Claude 相关 OAuth 形态 URL 都能离开 Kite WebView 并匹配外部浏览器 handler，同时记录从 `/open-web` 请求到外部浏览器前台的总耗时；UI dump 未出现 `disallowed_useragent`，也没有 `redirect_uri_mismatch`、`invalid_client`、`unsupported_browser`、`Error 400/403` 等外部 provider 页面阻塞性错误信号。第三方 HTTPS redirect 没有新增假 AppRedirect session，并继续验证 Kite 可接收 `kite-auth://callback` 的 pending、回跳交付、auth session 脱敏落盘和 app 私有文本文件临时值扫描。

### G2 浏览器会话可用性

目标：提高人工 Google 验证通过概率。

步骤：

1. 人工先在 OnePlus 8T 默认浏览器或 Chrome 中确认 Google 账号可正常打开 `https://accounts.google.com/`。
2. 从 Kite 再发起 OAuth handoff。
3. 观察是否复用浏览器账号状态，或至少进入账号选择、密码、验证码、MFA、授权确认等正常账号挑战页面。

判定：

- 进入账号挑战或 consent 页面：N2，通过浏览器环境验证。
- 出现 `disallowed_useragent`：Kite 路径回退，需要查是否误入 WebView。
- 出现 `redirect_uri_mismatch`、`invalid_client` 等：这是 OAuth client 配置问题，不是浏览器环境问题。
- 出现验证码/MFA/风控：记录为账号挑战，不作为 Kite 失败。

### G3 Redirect 类型分离

目标：防止把“网页能打开”误当作“已经能回 Kite”。

步骤：

1. 对 `redirect_uri=https://developers.google.com/oauthplayground` 这类第三方 HTTPS redirect，只验证外部浏览器打开，不创建 AppRedirect session。
2. 对 `redirect_uri=kite-auth://callback` 或未来 App Link redirect，验证 pending session、state 匹配和 CardRun 回到同一实例。
3. 对 CLI `redirect_uri=http://127.0.0.1:<port>/callback`，验证终端保留、loopback listener 或官方 paste/device-code fallback。

判定：

- 第三方 HTTPS redirect 不回 Kite 是正确行为。
- Kite 可接收 redirect 才能要求 N3 App 回跳。
- CLI 登录必须由 CLI 自己确认 N4/N5，Kite 只负责打开和回传链路。
- 当前 smoke test 会自动覆盖第 1 和第 2 项，但第 2 项使用本机生成的假 code/token，只证明回跳机制，不证明 provider 已签发真实授权码。

### G4 敏感信息边界

目标：人工验证前确认不会把授权 code/token 写进项目文件或持久状态。

步骤：

1. 用模拟 `kite-auth://callback?state=...&code=...` 触发回跳。
2. 检查 browser auth session、diagnostics、status 文件只保存 `code=present`、`state=present` 或摘要。
3. 扫描 app 私有 `files` / `shared_prefs` 下的文本类状态和诊断文件，确认本轮生成的 Google `state`、AppRedirect `state`、假 `code` 和假 token 不以原文出现。
4. 运行完成审计和 diff check。

判定：

- 原始 `code`、`access_token`、`id_token`、`refresh_token` 不落盘。
- 只能由状态拥有者交换 code 或确认登录，Kite 不代取 token。
- 当前 smoke test 会检查 `returnedUrl` 等于 `kite-auth://callback?code=present&access_token=present&state=present`，确认 app 私有 `kite_browser_auth_sessions.xml` 不含本次假 `code` / token / `state` 原文，并进一步扫描 app 私有文本类文件，确认本轮 OAuth 临时值没有进入诊断或状态文件。

## 4. 通用多站点测试方法

| 场景 | 推荐测试 | 通过标准 |
| --- | --- | --- |
| Google OAuth | G1-G4 | C2/C3 通过，C4 等人工账号完成 |
| OpenAI / Codex CLI | 真实 Codex CLI 发起登录，Kite 打开系统浏览器，设备侧探测 loopback | 终端保留、listener 可达或 fallback 明确、`codex login status` 最终确认 |
| Claude Code CLI | 真实 Claude Code 发起登录，观察浏览器登录页和 paste code fallback | 终端保留、fallback 可见、`claude auth status --json` 最终确认 |
| 普通 localhost Web UI | smoke 通过 `/open-web` 打开 `http://127.0.0.1:8791/status` 非 OAuth 页面 | 前台保持 `com.kite.app`，不新增 browser auth session，不被错误分流 |
| 外部浏览器 handler/fallback | smoke 解析 `https://accounts.google.com/` 的系统 `ACTION_VIEW` handler，并比对 Google OAuth handoff 后前台包名 | handler 存在且不是 Kite；Custom Tabs service 可用性有记录；无 CCT service 时系统浏览器 fallback 实测生效 |
| 多站点 OAuth 形态分流 | smoke 打开 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL，不输入账号 | `/open-web` 接收，前台切到外部浏览器 handler，前台切换耗时在阈值内，不新增假 AppRedirect/CliLoopback session |
| 普通外部网页 | 从 `browser_proxy` 打开非 OAuth HTTPS 页面 | 按现有 Web surface 行为，不破坏原体验 |
| OIDC hybrid / IPv6 loopback | 单测覆盖组合 `response_type` 和 `[::1]` loopback | 分类正确，不按 provider 域名特判 |

## 5. 体验和性能测试

这些测试不证明账号成功，但能防止“能打开但不好用”：

1. 启动延迟：当前 smoke 分两层记录。第一层是本地 `/open-web` 接收，默认 `1500ms` 阈值，验证 Kite 能快速承诺；第二层是从 `/open-web` 请求到系统浏览器前台，默认 `5000ms` 阈值，验证 handoff 后用户能尽快看到外部浏览器。两个阈值都不覆盖 provider 页面加载、网络等待或账号挑战速度。
2. 重复点击：同一 pending 登录重复触发不应产生多个冲突 session，也不应重复抢占终端。
3. 前后台恢复：回到 Kite 后应先显示已有等待态，再轻量校准，不整页闪烁。
4. 过期状态：pending 超时后进入可解释的 `Expired`，不能悄悄回到初始状态。
5. ANR/崩溃：每次真机验证后过滤 `AndroidRuntime`、`FATAL EXCEPTION`、`ANR in com.kite.app`、`Input dispatching timed out`。
6. 普通网页回归：非 OAuth WebView 页面不能因为登录 handoff 被外部打开。
7. 终端保持：CLI 登录打开浏览器后，CardRun surface 保持 `Terminal`，不被 Web 等待页覆盖。
8. 可恢复操作：外部打开失败时要有错误提示、重新打开或复制地址入口。
9. 授权主机可达：当前 smoke 会用 OnePlus 8T 设备侧 `curl` 探测 `accounts.google.com`、`auth.openai.com` 和 `claude.ai`。HTTP `2xx..4xx` 视为 DNS/TLS/网络路径可达；该检查不读取账号、不证明 provider 接受授权请求，只用于提前排除明显网络层失败。
10. 浏览器 handler/fallback：当前 smoke 会记录默认 HTTPS handler 和 Custom Tabs service 数量。若 Custom Tabs service 为 `0`，只要 HTTPS `ACTION_VIEW` handler 存在且真实 handoff 前台匹配外部浏览器，仍视为官方外部 user-agent 路线可用。
11. 多站点分流：当前 smoke 会对 OpenAI/Codex 与 Claude 相关 OAuth 形态 URL 进行无账号 handoff 检查。通过只证明 Kite 的 URL 分类和外部浏览器 handoff 不限于 Google；不证明 provider 接受该测试 client，也不证明真实账号完成。
12. 前台切换、授权主机网络和 provider 页面趋势：当前 schema 10 会输出 `foregroundHandoffElapsedMs`、`providerOAuthForegroundMaxElapsedMs`、授权主机探测 attempts、`providerPageSignalState`、`providerPageBlockingErrorCount` 和 `providerPageChallengeHintCount`。若前台值变慢，优先查 Kite 本地 `/open-web`、Custom Tabs/系统浏览器 fallback、默认浏览器变化和设备前台状态；若单个 host 偶发超时但重试恢复，记录为网络毛刺；若持续不可达，再归为人工账号验证前阻塞；若页面命中 `redirect_uri_mismatch`、`invalid_client` 或 `unsupported_browser`，按 provider 配置或浏览器环境桶处理。
13. 人工账号启动和 watch：用户在手机浏览器里完成 Codex/Claude 账号挑战时，优先运行 `browser-login-manual-account-start.ps1 -StartWatch -RunCompletionAuditOnVerified`。它先刷新无账号 smoke，再确认 manual readiness 仍处于可人工验证窗口，然后拉起 Codex/Claude 真实终端登录入口并接 account watch；已有登录入口时可直接运行带 `-RunSmokeFirst -RunReadinessFirst` 的 `browser-login-account-watch.ps1`。两者都不输入账号、不读取 token、不伪造 callback。
14. 多轮趋势 watch：长时间自主跑测试时，运行 `browser-login-smoke-watch.ps1`。一次 smoke 通过只能说明当前链路健康，多轮 watch 通过才能更有把握排除偶发卡顿、默认浏览器切换、设备网络漂移和 session 泄漏。
15. 浏览器能力盘点：每轮 smoke 都要记录 HTTPS `ACTION_VIEW` handler、前台浏览器包名、Custom Tabs service 数量和 provider 页面信号。后续如果引入 Chrome Auth Tab 或 Custom Tabs warmup，要先用能力探测证明目标浏览器支持，再把不支持设备继续落到系统浏览器 fallback。
16. 端到端分段计时：不要只看“页面最终打开”。长期 watch 需要分开看 `/open-web` 本地接收耗时、外部浏览器前台切换耗时、provider 页面是否出现挑战/阻塞信号、回到 Kite 或 CLI 后置状态。这样才能区分 Kite 卡顿、浏览器启动慢、网络慢和账号挑战慢。
17. 交互完整性：重复触发同一登录、App 前后台切换、屏幕锁屏后恢复、默认浏览器被用户更改、账号页返回失败、CLI listener 超时，都应保留可解释状态；不能靠整页刷新或重新启动资源掩盖。
18. 会话策略：默认使用用户真实外部浏览器状态，因为这更接近官方 external user-agent 路线，也更可能复用已登录账号；Ephemeral/无痕/隔离浏览器只适合后续隐私实验，不作为提高 Google/OpenAI/Claude 通过率的默认路径。
19. 冷启动/热启动对照：如果后续要优化“更快”，同一 OAuth 形态至少分冷启动和连续二次打开两组看 `/open-web`、前台切换和 provider 页面信号。冷启动慢优先考虑 Custom Tabs warmup / Auth Tab 能力；热启动慢优先查 Kite 路由、重复 session、设备前台状态。
20. 浏览器能力完整性：每次长期观察都记录默认 HTTPS handler、Custom Tabs service、Auth Tab 是否可检测、fallback 是否生效、用户能否从外部浏览器返回 Kite/终端。没有能力时不能报失败，但必须确保系统浏览器路径仍稳定。
21. 人工验证陪跑截图点：不保存账号、验证码或 token；只允许保存“外部浏览器已到账号挑战/授权确认/fallback 提示”的脱敏截图或 UI 摘要。这样可以证明已到 N2/N3，但不泄露 N4/N5 凭据。

## 6. 我自己长期跑的测试组合

这部分用于把“最后你人工验证前，我自己能做什么”固定下来。它不是为了制造 100% 账号成功承诺，而是把 Kite 可控问题尽量提前打掉。

| 组合 | 自动入口 | 主要发现的问题 | 通过后说明 |
| --- | --- | --- | --- |
| T0 官方合规复核 | 读本文件第 2 节和 `WEB_LOGIN_RESEARCH.md` | WebView 承载 OAuth、错误 client type、OOB、Android loopback 误用、无 PKCE、redirect 不可接收 | 主路线没有违反已知官方要求 |
| T1 白盒协议测试 | `.\gradlew.bat :app:testDebugUnitTest --tests "com.kite.app.browser.*" --console=plain` | URL 分类误判、同 state 串线、AppRedirect/CLI loopback 边界、脱敏回归 | 代码机制本身稳定 |
| T2 真机单轮 smoke | `.\scripts\browser-login-smoke-test.ps1 -Serial 3f8bbaad` | 设备网络不可达、默认浏览器 handler 不存在、OAuth 留在 WebView、provider 页面阻塞错误、前台切换慢、普通 localhost 被误分流、临时值落盘 | 当前 OnePlus 8T 的无账号链路健康 |
| T3 真机多轮趋势 | `.\scripts\browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 6 -IntervalSeconds 600` | 偶发卡顿、默认浏览器变化、设备网络漂移、provider 页面偶发阻塞错误、provider OAuth 误建 session、私有状态泄漏 | 长时间稳定性更可信 |
| T4 人工账号启动/陪跑 | `.\scripts\browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`；自动短验证可加 `-WatchMaxAttempts 1 -WatchPollSeconds 0` | 准备度通过后未拉起真实登录入口、账号完成后漏收证、runner/post-auth 没接上、另一个账号仍缺证据 | 真实资源登录入口已拉起，真实账号状态能被官方命令确认 |
| T5 完成审计 | `.\scripts\browser-login-completion-audit.ps1 -RefreshState` | 构建/单测/ADB/smoke/续跑/manual account start/account watch/账号任一强证据缺失 | 只有退出码 `0` 且 `status=complete` 才能宣称完成 |
| T6 人工准备度汇总 | `.\scripts\browser-login-manual-readiness.ps1 -RefreshState` | smoke / smoke-watch / runner / account watch / manual account start / completion audit 信息分散，人工验证前容易漏看一项 | `ready_for_manual_account` 表示可以进入真人账号挑战；`not_ready` 会指出先修哪一项，包含最近启动入口失败 |
| T7 账号等待期 long-run cycle | `.\scripts\browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 3 -SmokeIntervalSeconds 600` | 账号长期未授权期间，只跑 runner 会漏掉浏览器链路回归、默认浏览器漂移和性能变慢 | 一次调度内同时确认账号状态和浏览器稳定性；短 cycle 少于 3 轮时不覆盖主趋势证据 |
| T8 provider 验证前综合预检 | `.\scripts\browser-login-provider-preflight.ps1 -Serial 3f8bbaad` | 官方要求、浏览器环境、provider 页面阻塞错误、redirect 类型、CLI callback/fallback、敏感边界、性能和账号状态分散，人工验证前容易误判 | JSON/Markdown/控制台输出语义 `exitCode`；退出 `2` 且 `ready_for_manual_provider_auth` 表示 Kite 可控环境 ready、只剩真人账号；退出 `1` 会给出失败桶；`smokeCheckedAt` / `smokeWatchCheckedAt` 用 ISO 时间对账 |
| T9 人工验证作战清单 | `docs/browser-login/ACCOUNT_AUTH_COMPLETION_SOP.md` 的“人工验证作战清单” | 到了真人 Google / OpenAI / Claude 验证窗口时，测试命令、外部浏览器选择、截图边界、失败归因和完成证据容易混在一起 | 先看 status summary / provider preflight，再启动 manual account start 或 account watch；只保存脱敏证据；失败按浏览器环境、provider 配置、账号挑战、callback/fallback、性能分层处理 |

长期自测时，T2/T3/T7/T8 要一起看，不把任何一个单点结果当作全部答案：

| 观测项 | 主要命令 | 目标阈值或判断 | 失败优先归因 |
| --- | --- | --- | --- |
| 本地承诺速度 | smoke / smoke watch | `/open-web` p95 不超过 `1500ms` | Kite LocalServer、ADB/设备负载、资源实例路由 |
| 外部浏览器出现速度 | smoke / smoke watch | provider OAuth 前台 p95 不超过 `5000ms` | 默认浏览器启动、Custom Tabs fallback、设备前台状态 |
| 浏览器合规 | smoke / provider preflight | 前台包不是 Kite，页面无 `disallowed_useragent` / `unsupported_browser` | WebView 分流回退、handler 解析错误 |
| provider 配置 | smoke / provider preflight | 页面无 `redirect_uri_mismatch` / `invalid_client` / `Error 400/403` | OAuth client type、redirect allowlist、第三方 HTTPS redirect 误判 |
| 多站点完整性 | smoke / completion audit | Google、OpenAI/Codex、Claude OAuth 形态都离开 WebView，且不新增假 session | URL 分类过窄、AppRedirect/CliLoopback 边界回归 |
| CLI 回传能力 | manual account start / account watch | 终端保留，listener 或 paste/device-code fallback 明确 | loopback callback 不可达、官方 fallback 未暴露 |
| 敏感边界 | smoke / completion audit | 本轮 code/token/state 原文命中 `0` | diagnostics、CardRunStore、auth session 持久化回归 |
| 账号真实完成 | account watch / completion audit | 官方状态命令 verified，最终审计 `status=complete` | 仍在账号挑战、未授权、provider 风控或另一个账号缺证 |

人工 Google 或 OpenAI/Claude 页面失败时，先按下面分类判断，不先回到 UA 伪装：

| 现象 | 优先归类 | Kite 侧下一步 |
| --- | --- | --- |
| `disallowed_useragent` | OAuth 仍进入 embedded user-agent | 查 `/open-web` 分类、前台包名、WebView 拦截和 smoke 负控/正控 |
| `redirect_uri_mismatch` / `invalid_client` / unsupported custom scheme | provider OAuth client / redirect 配置 | 确认 client type、redirect allowlist、Android/desktop/CLI flow 是否混用；先看 smoke 的 `provider-page-no-blocking-error` 和 preflight 的 `provider_configuration` / `browser_environment` 桶 |
| 账号选择、密码、验证码、MFA、设备确认 | 正常账号挑战 | 由用户完成人工验证；account watch 只收证 |
| 页面显示 paste code 或 device code | 官方 fallback | 按 Codex/Claude 官方 fallback 完成，不伪造 callback |
| 浏览器打开但终端不变为 logged in | loopback callback / fallback 未交付 | 先查设备侧 listener、callback URL、paste code 提示；真实失败后再考虑 relay |
| 多轮 smoke p95 超阈值 | Kite/设备交互卡顿 | 查 LocalServer、前台切换、默认浏览器、设备负载和 ADB 观测延迟 |

## 7. 后续改进候选

这些不是当前完成阻塞项，但适合排入后续增强：

1. 评估 Chrome Auth Tab：如果项目升级 `androidx.browser` 后 API 稳定，可用 Auth Tab 简化认证回调和减少浏览器菜单干扰。
2. Custom Tabs 预热：绑定 Custom Tabs service 做 warmup / mayLaunchUrl，降低打开授权页的首屏等待；若设备没有可查询的 Custom Tabs service，继续保留系统 `ACTION_VIEW` fallback。
3. Chrome / 浏览器可用性分层：在有 Chrome 137+ 和 Auth Tab 支持的设备上补一组 Auth Tab 正控；在没有 Auth Tab / Custom Tabs service 的设备上继续验证系统浏览器 fallback。
4. Google 专用人工清单：当用户准备人工登录时，按 G1-G4 逐项执行并把结果补进 `COMPATIBILITY_MATRIX.md`。
5. 完整 App Link 回跳：如果 Kite 后续有可控 HTTPS 域名，按 Android App Links + Digital Asset Links 建立 claimed HTTPS redirect，再补 Google 真实 redirect 配置证据。
6. 浏览器选择实验：在不伪装环境的前提下，后续可比较 OnePlus 默认浏览器、Chrome、Edge/Firefox 等真实外部浏览器的 handler、Custom Tabs 能力、前台切换耗时和账号页兼容性。

## 8. 下一轮高置信度自动跑法

这部分回答“几小时里我自己该怎么测，才能让最后人工验证尽量只剩账号问题”。

1. 先跑 `browser-login-provider-preflight.ps1 -Serial 3f8bbaad`。如果输出 `ready_for_manual_provider_auth` 且 `blockingFailureIds=(none)`，说明浏览器环境、provider 页面阻塞错误信号、redirect 类型、CLI fallback、敏感边界和性能都已汇总通过；退出码 `2` 只表示还等真人账号，不是环境失败。
2. 如果 preflight 为 `not_ready`，先按 `failedBuckets` 修：`browser_environment` 查 WebView 分流/default handler；`provider_configuration` 查 client type / redirect allowlist；`cli_callback_or_fallback` 查 loopback listener 或 paste/device-code；`sensitive_boundary` 查 diagnostics / run store；`performance` 查 LocalServer、浏览器前台切换和设备负载。
3. 再跑 `browser-login-manual-readiness.ps1 -RefreshState`。如果不是 `ready_for_manual_account`，不进入账号验证，先按失败 item 修环境；其中 manual account start 检查项只说明启动入口状态，不代表账号已登录。
4. 接着跑 `browser-login-smoke-watch.ps1 -Serial 3f8bbaad -Iterations 6 -IntervalSeconds 600`，或用 `browser-login-long-run-cycle.ps1 -Serial 3f8bbaad -SmokeIterations 3 -SmokeIntervalSeconds 600` 把 runner、smoke watch 和 readiness 合成一次调度循环。当前也已注册 `KiteBrowserLoginLongRunCycle` 小时级计划任务，默认每小时以 `SmokeIterations=6` / `SmokeIntervalSeconds=600` 观察一次长周期稳定性。目标不是重复证明一次通过，而是看 1 小时内是否出现网络漂移、默认浏览器变化、前台切换 p95 变慢、假 session 泄漏或临时值落盘。
5. 如果多轮 watch 失败，按失败类别拆开：`disallowed_useragent` 查 WebView 分流；handler 变化查默认浏览器；p95 超阈值查 LocalServer、设备负载和浏览器启动；session 泄漏查 `BrowserAuthSessionStore`；临时值落盘查 diagnostics / run store。
6. 如果多轮 watch 通过，再跑一次 `browser-login-completion-audit.ps1 -RefreshState`。此时如果只剩 `codex-account`、`claude-account`，就说明 Kite 可控链路已经进入人工账号挑战窗口。
7. 人工开始时运行 `browser-login-manual-account-start.ps1 -Serial 3f8bbaad -Targets codex,claude -StartWatch -RunCompletionAuditOnVerified`。我先刷新无账号 smoke，再做 readiness 预检，然后拉起真实 Codex/Claude 终端登录入口并陪跑收证，不输入账号、不读取 token、不伪造 callback。
8. 如果用户人工页面卡在验证码、MFA、设备确认或风险验证，记录为账号挑战节点；如果页面出现 `redirect_uri_mismatch`、`invalid_client` 或 unsupported scheme，记录为 provider 配置节点；如果重新出现 `disallowed_useragent`，才回到浏览器环境问题。
9. 如果只是为了当前会话快速确认设备状态，可以运行 `browser-login-long-run-cycle.ps1 -SmokeIterations 1 -SmokeIntervalSeconds 0`；这种短 cycle 的 smoke watch 会写到独立子目录，不会降低 completion audit 对主 `browser-login-smoke-watch.json` 的至少 3 轮要求。

能接近 100% 自动确认的部分：URL 分类、WebView 分流、外部浏览器前台、回跳 state 匹配、敏感值不落盘、普通 localhost 不误跳、runner/watch/audit 状态流。不能自动承诺 100% 的部分：Google/ChatGPT/Claude 的账号风控、验证码/MFA、provider OAuth client 配置、未来策略变化。这里的边界必须一直写清楚，避免把“浏览器链路健康”误写成“账号已经通过”。

## 9. 后续自主测试实验池

这些实验是账号等待期我可以继续自主推进的方向，按风险从低到高排序；它们都不输入账号、不读取 token、不伪造 provider callback。

| 实验 | 目标 | 自动证据 | 触发条件 |
| --- | --- | --- | --- |
| 长跑状态对账 | 确认计划任务、runner、long-run、progress JSON 没漂移 | `browser-login-status-summary.json`、计划任务状态、最近 smoke 时间、provider preflight `exitCode`、`longRunProgress.phase`、`smokeWatchProgress.completedIterations/remainingIterations/nextExpectedAt`、`noProgressOverdueAt`、`noProgressMinutesRemaining` | 每次续跑先做 |
| latest smoke 关联 | 判断无 progress 长跑期间是否仍出现新 smoke 样本 | `latestSmokeAfterCurrentRunStart`、`latestSmokeAgeMinutes`、`longRunObservation` | long-run 运行中但没有 progress JSON 时先看 |
| 冷/热启动性能采样 | 区分 Kite 本地慢、浏览器冷启动慢、provider 网络慢 | 多轮 `/open-web`、foreground p95、provider signal | long-run 完成后或 p95 接近阈值时 |
| 默认浏览器漂移检查 | 防止用户或系统把 HTTPS handler 换成不合规环境 | handler 包名、前台 Activity、Custom Tabs service 数量 | 每轮 smoke/watch |
| Auth Tab / warmup 可行性评估 | 让浏览器打开更快、更少菜单干扰 | 能力探测结果、冷/热启动对照、fallback 证据 | 当前主链稳定后作为增强，不阻塞账号完成 |
| CLI fallback 明确性 | Codex/Claude callback 不通时不迷路 | listener 探测、终端是否保留、paste/device-code 提示是否可见 | 真实账号回跳失败或用户看到 callback 错误时 |
| 人工失败归因复盘 | 让一次人工失败直接生成下一轮修复目标 | 失败页面脱敏截图/UI 文本、preflight failedBuckets、account watch 状态 | 用户人工验证遇到错误后 |

如果某次人工 Google / OpenAI / Claude 验证失败，我的处理顺序固定为：

1. 先看页面是否是 `disallowed_useragent` / `unsupported_browser`。是的话回到浏览器环境，查分类和前台包。
2. 再看是否是 `redirect_uri_mismatch` / `invalid_client` / `Error 400/403`。是的话归 provider 配置或 client type，不改 UA。
3. 如果是验证码、MFA、设备确认、账号选择或授权确认，归正常账号挑战，继续由用户完成。
4. 如果账号完成后 CLI 没登录，归 callback/fallback，先查 listener 和官方 paste/device-code，再考虑 relay。
5. 如果没有明显错误但体验慢，按分段计时查 `/open-web`、浏览器前台、provider 网络和设备负载。
