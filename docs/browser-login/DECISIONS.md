# Kite 浏览器登录回跳决策记录

## ADR-B001 官方推荐路径优先，策略绕过只做研究记录

- 日期：2026-07-04
- 决策：浏览器登录线优先采用系统浏览器、Chrome Custom Tabs、App Links、OAuth 2.0 for Native Apps、PKCE、AppAuth 或等价官方推荐路径。无指纹浏览器、UA/环境伪装、自动化特征隐藏等内容可以调研，但不作为默认实现路线。
- 理由：用户明确要求“不否定违反策略”，但也明确说“肯定首先以官方推荐的方式完成”。Kite 需要解决的是通用网页登录回跳能力，不是只让一个 Google 报错消失。
- 影响：后续 B2/B3 要同时记录官方路径、兼容性要求和策略绕过风险，但 B4 的默认实现不能依赖伪装浏览器环境。
- 回滚：如果用户后续明确要求做非官方兼容实验，必须单独开实验任务，并把适用范围、风险和回退方式写入本文件。

## ADR-B002 浏览器线固定绑定 OnePlus 8T

- 日期：2026-07-04
- 决策：浏览器登录线的默认真机目标为 OnePlus 8T `3f8bbaad`，物理目录为 `D:\xm\Kite-browser-login`。
- 理由：用户指定“浏览器的部分绑定一加手机”。当前 `adb devices -l` 确认 `3f8bbaad` 是 OnePlus 8T。
- 影响：所有浏览器线 ADB 命令必须带 `-s 3f8bbaad`；host 调试端口默认使用 `18791`。

## ADR-B003 不把 provider 单点报错写成硬编码特判

- 日期：2026-07-04
- 决策：Google、ChatGPT、Claude Code 或其他站点登录问题都先归入“网页登录环境与回跳协议”这个通用机制，不在入口处为某个域名写死绕行。
- 理由：用户要求“做到时候肯定做全”，且项目契约禁止硬编码式特判。
- 影响：后续实现要围绕浏览器 handoff、redirect 捕获、状态确认和失败反馈设计，不用单一域名判断替代机制修复。

## ADR-B004 当前 Kite WebView 链路不作为 OAuth 正式承载面

- 日期：2026-07-04
- 决策：当前 `KiteWebShell` 内嵌 WebView 继续承载普通网页、localhost Web UI 和资源页面，但不作为第三方 OAuth /网页登录授权页的正式承载面。
- 理由：真实代码显示 `browser_proxy`、`ubuntu_browser`、`terminal_page`、`terminal_step`、`shell_step` 的普通 `http/https` URL 都会留在 WebView；Google 官方和 RFC 8252 都要求 native app 授权请求使用外部 user-agent，Google remediation 还明确建议迁移到 Chrome Custom Tabs。
- 影响：B3 设计必须增加 OAuth/auth URL 分流，优先走 Chrome Custom Tabs 或系统浏览器；WebView 仅作为普通网页与本地 Web UI 的显示面。
- 回滚：如果后续某个站点只支持普通网页登录且不涉及 OAuth provider，可继续用 WebView，但必须由 URL 分类和用户可见状态明确区分。

## ADR-B005 CLI 登录需要 callback relay 或显式 fallback

- 日期：2026-07-04
- 决策：对 Codex、Claude Code、Google Antigravity 等容器内 CLI 登录，不能只解决“打开网页”；还要解决浏览器登录后的 callback 如何回到容器内 CLI。后续设计必须包含 localhost callback relay，或在不可 relay 时提供 device code / paste callback URL 的显式 fallback。
- 理由：OpenAI Codex 官方文档说明 localhost callback 被阻断时应使用 device code；Claude Code 官方文档说明浏览器不能回到本地 callback 时可把 code 或完整 callback URL 粘回终端。Kite 的运行环境是 Android + 容器，外部浏览器的 `127.0.0.1` 不等于容器里的 `127.0.0.1`。
- 影响：B3 设计要把“App 自己 OAuth redirect”和“容器 CLI localhost callback”分成两类，不用单一 WebView 页面冒充完整登录链路。

## ADR-B006 B4 默认实现采用 URL 分类 + Custom Tabs handoff

- 日期：2026-07-04
- 决策：B4 的最小实现从通用 URL 分类器开始，将 OAuth /授权页从 `KiteWebShell` 分流到 Chrome Custom Tabs；设备不支持 Custom Tabs 时降级为系统浏览器 `Intent.ACTION_VIEW`。
- 理由：OnePlus 8T 已在 Kite WebView 中复现 Google `错误 403: disallowed_useragent`；Google 官方 remediation、RFC 8252 和 AppAuth 路线都指向外部 user-agent。分类器比 provider 域名单点特判更适合作为通用机制。
- 影响：`KiteWebShell` 仍保留普通网页和 localhost Web UI；B4 需要新增 `androidx.browser` 或等价 Custom Tabs 支持、分类器单测、以及用户可见的“等待浏览器登录返回”状态。
- 回滚：如果某个普通网页被误分类，应修正分类规则和测试，不回退到 WebView 承载 OAuth。

## ADR-B007 回跳 session 与登录事实分离

- 日期：2026-07-04
- 决策：新增 browser auth session 只负责一次 handoff 的 `state`、redirect URI、来源实例和交付状态；最终登录事实仍由对应 OAuth client、CLI 或 CardRun 状态拥有者确认。
- 理由：Kite 可能自己接收 OAuth redirect，也可能只是帮助 Codex/Claude 等 CLI 打开浏览器并回传 callback。把 session 当作“已登录”会复制事实来源，且容易误保存敏感 token。
- 影响：B4 实现中，session 成功只表示“回跳已校验并交付”，不表示 provider 登录完成；CLI token 不由 Kite 提取或保存。

## ADR-B008 URL 分类按协议形态，不按 provider 域名单点特判

- 日期：2026-07-04
- 决策：B4 的 `BrowserHandoffPolicy` 依据 OAuth 授权参数、`response_type`、`client_id`、`redirect_uri`、`state` 和 loopback redirect 形态分类；不以 `accounts.google.com`、`chatgpt.com`、`claude.ai` 等域名作为唯一触发条件。
- 理由：用户要求做通用网页登录回跳；项目契约禁止硬编码式特判。Google 复现只用于确认 WebView 失败类别，不应变成实现的唯一条件。
- 影响：普通 `browser_proxy` 外部网页仍可留在 Kite WebView；OAuth 授权请求会进入 Custom Tabs / 系统浏览器；CLI loopback redirect 会进入 CLI handoff/fallback 路线。

## ADR-B009 浏览器线续跑采用完成驱动，定时只作外部兜底

- 日期：2026-07-04
- 决策：浏览器线的自主推进以 `PLAYBOOK.md` / `PROGRESS.md` / `DECISIONS.md` 为状态源；每次被唤起后自动恢复第一个就绪未完成任务。当前回合内完成一个任务后，立即回写状态并继续下一个就绪任务。每小时定时只作为外部唤醒兜底，不作为任务推进的唯一节奏。
- 理由：用户希望“完成了一个，下一次自动触发”，而不是每小时机械等待。Codex 不能在会话休眠时无外部触发自行发起新回合，因此需要把“完成驱动”和“外部唤醒”分开说明。
- 影响：后续浏览器线会话必须先读三件套恢复进度；只要任务验收和依赖满足，就自主继续。遇到账号授权、真实登录凭据或不可替代外部环境时，必须记录阻塞点，而不是跳过验收。

## ADR-B010 CLI loopback handoff 保留终端显示面

- 日期：2026-07-04
- 决策：当授权 URL 的 `redirect_uri` 指向 `localhost` / `127.0.0.1` loopback，且发起实例当前拥有 `Terminal` surface 和 `terminalSessionId` 时，Kite 只打开安全外部浏览器并写入 `CliLoopback` pending session；`CardRunStore` 继续保留 `surface=Terminal`，不写入 `nextActionUrl`，不把运行实例切成 Web 等待页。
- 理由：Codex、Claude Code 等 CLI 的登录事实和最终输出属于终端/CLI，自行把实例改成 Web 会抢占用户正在等待的 CLI 交互，也会把 callback 是否交付的问题伪装成网页打开成功。
- 影响：真实 Codex CLI 已验证会发起 `https://auth.openai.com/oauth/authorize?...redirect_uri=http://localhost:1455/auth/callback`；Kite 记录 `CliLoopback` 并保留终端。后续如要完成 callback relay，应在该 pending session 基础上转发给对应 CLI，而不是新增平行登录状态。

## ADR-B011 CLI loopback 采用设备直达优先，relay 作为条件兜底

- 日期：2026-07-04
- 决策：对当前 PRoot/Kite 环境里的 CLI loopback callback，先验证 Android 设备侧是否能直达 CLI listener；如果 `127.0.0.1:<port>` 已经可达，就不额外插入 Android relay。只有在真实 callback 失败、端口不可达或 CLI 绑定到设备侧不可见网络命名空间时，才实现 relay 或切到官方 fallback。
- 理由：OnePlus 8T 上，真实 Codex CLI 登录时监听 `127.0.0.1:1455`，设备侧 `curl http://127.0.0.1:1455/` 返回 `Server: tiny-http (Rust)` 和 `404 Not Found`，证明当前环境中浏览器 callback 不一定需要代理转发。
- 影响：B5 对 OpenAI/Codex、Claude Code 先补完整账号授权后的直接 callback 证据；relay 不作为未验证前的默认复杂度。`CliLoopback` pending session 仍保留，用于追踪、失败提示和后续 relay/fallback。Claude Code 已验证 `127.0.0.1:43299` listener 存在，但根路径 curl 超时，这只能证明端口行为与 Codex 不同，不能证明 callback 已完成。

## ADR-B012 不用伪造 provider callback 作为完成证据

- 日期：2026-07-04
- 决策：B5 的真实登录完成证据必须来自用户账号授权后的 provider callback、CLI 官方 fallback，或 provider 明确返回的可粘贴 code；不向 `http://localhost:<port>/callback` 注入假 `code` / `state` 来制造成功或失败。
- 理由：CLI 会把 callback 当作真实授权结果处理。伪造 callback 会污染终端状态、触发错误 exchange，甚至掩盖浏览器是否真的能回到 CLI。Claude Code 的根路径探测已出现超时，不应进一步用假 callback 破坏 pending session。
- 影响：没有账号授权时，B5 只能标为 `verified_account_gate` 并记录缺口；后续如果用户提供账号或手动完成授权，再用真实回跳结果补证。

## ADR-B013 Pending 过期与运行面同步解耦

- 日期：2026-07-04
- 决策：`BrowserAuthSessionStore` 负责把超时的 `Pending` 会话落盘为 `Expired`；运行面同步是独立的可重试步骤，用 `runtimeNotifiedAt` 标记是否已经把过期结果同步给可见运行实例。
- 理由：真机验证发现旧 CLI 登录会话可能已在 `history_v1` 中，当前没有 active run；也可能在 Activity 前台恢复早期还查不到对应 recipe。把 session 过期和 CardRun 同步绑在同一次 `onResume` 会导致 UI 同步错过。
- 影响：如果对应 active run 仍存在，过期校准会局部更新该 run；CLI loopback 保持 Terminal，不把终端切成 Web。若 active run 已不存在，不凭空新建运行实例，只保留会话层 `Expired` 事实，并写入 `runtimeNotifiedAt`，避免每次前台恢复重复尝试。只有 active run 仍存在但 recipe 暂不可得时，才保留未同步标记，后续前台恢复继续重试。

## ADR-B014 OAuth/OIDC 分类按标准参数语义扩展

- 日期：2026-07-04
- 决策：`BrowserHandoffPolicy` 识别组合型 `response_type`，例如 `code id_token`，并把 IPv6 loopback redirect `http://[::1]:<port>/...` 归入 CLI callback handoff；普通 IPv6 localhost Web UI 仍保留在 WebView。
- 理由：OAuth/OIDC 授权请求不只使用单一 `code`，native/CLI loopback 也可能使用 IPv6 本地地址。把这些形态纳入协议分类能提升通用性，且不需要为 OpenAI、Claude、Google 等 provider 增加域名特判。
- 影响：组合型 OIDC 授权会走 Custom Tabs / 系统浏览器；IPv6 loopback CLI 登录会保留终端显示面并进入 `CliLoopback` session。普通 `http://[::1]:port` 本地 Web UI 不受 OAuth handoff 影响。

## ADR-B015 只有 Kite 可接收 redirect 才建立 AppRedirect session

- 日期：2026-07-04
- 决策：OAuth 授权 URL 只有在 `redirect_uri` 是 `kite-auth://callback` 时才建立 `AppRedirect` session；`redirect_uri` 指向第三方 HTTPS 页面时只外部打开，不创建“等待回 Kite”的 session。
- 理由：Kite 不能接收第三方 HTTPS redirect，例如 OAuth Playground 这类页面。为不可接收的 redirect 建立 pending session 会制造永远等不到的假回跳状态，并混淆“已用合规浏览器打开”和“已经具备 App 回跳能力”这两个事实。
- 影响：Google OAuth Playground 仍会离开 WebView、用系统浏览器/Custom Tabs 打开，但不会新增 `AppRedirect` session；真实 App 回跳必须使用 `kite-auth://callback` 或后续明确配置的 App Link。CLI loopback 不受影响，仍走 `CliLoopback`。

## ADR-B016 App redirect 回跳只消费 AppRedirect session

- 日期：2026-07-04
- 决策：`kite-auth://callback` 进入 App 后，只允许匹配 `kind=AppRedirect` 且 `redirectUri` 属于 Kite callback 的 pending session；`CliLoopback`、第三方 HTTPS redirect、state 不匹配或已过期 session 都不能被标记为 returned。
- 理由：Kite 同时支持 App 自己接收 OAuth redirect 和帮助容器内 CLI 打开 loopback 登录。两类 pending session 都可能带有 `state`，如果只按 `state` 匹配，App deep link 有机会误消费 CLI loopback session，破坏“回跳 session 与登录事实分离”的边界。
- 影响：真实 App redirect 仍按 `state` 校验并交付给发起运行实例；CLI loopback 继续保留终端显示面，由 CLI localhost callback 或官方 fallback 确认最终登录。迟到的 provider callback 不会复活已过期 session。

## ADR-B017 callback returnedUrl 只保存脱敏存在性

- 日期：2026-07-04
- 决策：browser auth session 的 `returnedUrl` 只保存脱敏 callback 摘要，例如 `code=present`、`access_token=present`、`state=present`；不保存授权 `code`、`access_token`、`id_token`、`refresh_token` 或其他 token 类参数原文。parser 同时读取 query 和 fragment，以便 fragment callback 也能校验 state。
- 理由：授权 code 和 token 类参数属于敏感凭据或短期凭据，Kite 的 browser auth session 只需要证明“回跳已到达并经过 state 匹配”，不需要持久化凭据本身。最终登录事实仍由 OAuth client、CLI 或对应运行实例确认。
- 影响：回跳报告仍可显示 code/token 是否出现，但不会把原值写入 SharedPreferences；provider `error` 的具体值继续放在 `failureReason`，便于失败解释。旧 session 如果读取到 `kite-auth://callback` 原始 returnedUrl，会按新 parser 得到脱敏形态后进入内存。

## ADR-B018 OAuth handoff 诊断 URL 只保存协议摘要

- 日期：2026-07-04
- 决策：Kite 的 browser/open-web 诊断事件、Web 状态文件和 Web 错误/外部打开日志中，如果 URL 符合 OAuth 授权请求形态，只保存 `scheme://host/path` 与关键参数存在性，不保存 `state`、`code_challenge`、`client_id`、`login_hint`、`scope` 等参数原文。普通非 OAuth URL 保持原样，便于诊断普通网页问题。
- 理由：OAuth 授权 URL 里的 `state`、PKCE `code_challenge` 和账号提示等值不应进入持久诊断文件。真机验证曾发现 `recipe-events.jsonl` 已脱敏但 `last-webapp-status.json` 仍保存原始 OAuth URL，因此必须在 diagnostics 层统一兜底。
- 影响：`recipe-events.jsonl`、`bridge-events.jsonl`、`last-webapp-status.json`、`webview-errors.jsonl` 和 `webview-console.log` 中的 OAuth URL 会显示为 `response_type=present`、`redirect_uri=loopback|kite_app|https`、`state=present` 等摘要。调试普通 Web URL 的能力不变。

## ADR-B019 Browser auth session 用指纹匹配，不持久化授权临时值原文

- 日期：2026-07-04
- 决策：`BrowserAuthSessionStore` 不再把 OAuth 授权请求原文保存到 `originalUrl`，也不再把 OAuth `state` 原文保存到 `state`；落盘只保存授权 URL 协议摘要、`state=present`、`requestKey` 和 `stateKey`。`requestKey` / `stateKey` 使用 SHA-256 指纹，仅用于重复 handoff 和 callback state 匹配。
- 理由：browser auth session 需要判断“同一个请求是否已经 pending”以及“callback state 是否匹配”，但不需要持久化 `client_id`、`state`、`code_challenge`、`login_hint` 等临时值原文。仅把 diagnostics 脱敏还不够，SharedPreferences 同样属于持久状态边界。
- 影响：不同 `state` 的 OAuth 请求不会因为摘要相同而误匹配；旧 session 读取时会先从旧原文计算指纹，再进入脱敏形态。运行状态里的原始 URL 仍保留给重新打开、复制地址和实际浏览器启动使用，不作为诊断或 auth session 证据输出。
- 回滚：如果未来需要排查 provider 参数差异，优先新增一次性本地调试开关或用户显式导出，不把原始授权 URL 恢复为默认持久化字段。

## ADR-B020 CLI 账号完成证据采用官方状态命令

- 日期：2026-07-04
- 决策：OpenAI/Codex 与 Claude Code 的“完整账号授权完成”证据，以 CLI 官方状态命令为准：Codex 使用 `codex login status`，Claude Code 使用 `claude auth status`。辅助脚本 `scripts/browser-login-auth-status.ps1` 只输出版本和状态，不读取 auth 文件内容。
- 理由：browser handoff session 只能证明“登录页已打开、callback 或 fallback 等待中”，不能证明 CLI 最终拥有可用账号凭据。直接读取 token 文件会扩大敏感边界，伪造 callback 又会污染 CLI 状态；官方状态命令是更窄、更可复验的证据。
- 影响：当前 OnePlus 8T 复验结果为 Codex `Not logged in`、Claude Code `"loggedIn": false`，因此 B5 仍保持 `verified_account_gate`。账号授权完成后必须重新运行该脚本，并把状态输出摘要写回 `PROGRESS.md` 和兼容矩阵。

## ADR-B021 跨回合续跑使用只读账号门槛脚本

- 日期：2026-07-04
- 决策：浏览器线跨回合续跑不依赖 Codex 自己“睡醒”。当前回合内完成驱动继续推进；跨回合由外部调度器或人工唤醒。项目提供 `scripts/browser-login-continuation-gate.ps1` 作为只读门槛：退出码 `0` 表示至少一个账号进入后置验证窗口，退出码 `2` 表示仍需真实账号授权，退出码 `1` 表示环境需要检查。可选的 `scripts/register-browser-login-continuation-gate.ps1` 注册 Windows 计划任务，每小时兜底复验。
- 理由：用户希望完成一个任务后自动触发下一步，但当前 Codex 会话休眠后不能无外部事件自行发消息。用只读门槛脚本可以把“任务是否值得唤醒”从“固定每小时盲跑”里拆出来，同时不扩大敏感凭据边界。
- 影响：计划任务只写 `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 和 `last-status-raw.txt`，不修改项目代码、不读 token、不伪造 callback。真正的 B5 完成仍必须来自账号授权后的 CLI 官方状态和后置可用性验证。

## ADR-B022 账号门槛按单账号粒度唤醒

- 日期：2026-07-04
- 决策：`scripts/browser-login-continuation-gate.ps1` 不要求 Codex 和 Claude 两个账号同时完成才返回 `0`。只要任一账号状态进入后置 CLI 可用性验证窗口，就返回 `0` 并在状态 JSON 中写入 `readyTargets`、`waitingTargets` 和 `errorTargets`。
- 理由：真实账号授权可能逐个完成。若等两个账号都完成才唤醒，会导致已完成的 Codex 或 Claude 账号缺少及时补证，降低持续任务的精度。
- 影响：外部调度器看到退出码 `0` 后，应先读取 `%LOCALAPPDATA%\Kite\browser-login-continuation\last-status.json` 的 `readyTargets`，只补已准备好的账号；仍在 `waitingTargets` 的账号继续等待真实授权。退出码 `2` 仅表示当前没有任何账号可做后置验证。

## ADR-B023 后置补证只运行官方非交互健康命令

- 日期：2026-07-04
- 决策：新增 `scripts/browser-login-post-auth-verify.ps1` 作为 B5 账号完成后的补证入口。Codex ready 后运行 `codex login status` 与 `codex doctor --json`；Claude ready 后运行 `claude auth status --json`。脚本默认只处理 `readyTargets`，没有 ready 账号时返回 `2`，并且不会运行后置 probe。
- 理由：B5 不能在账号未完成时伪造 callback，也不能把“打开登录页”当作完成。账号授权一旦真实完成，需要一条可重复、非敏感、非交互的证据链。Codex 的 `doctor --json` 明确是官方脱敏报告；Claude Code 的 `auth status --json` 是官方认证状态入口。Claude 的 `doctor` 会检查自动更新和可能启动工作区 MCP，不适合作为账号补证默认命令。
- 影响：`post-auth-status.json` 和 `post-auth-raw.txt` 只保存脱敏摘要，不保存账号邮箱、token、API key、callback code。脚本不启动交互 CLI，不发送 prompt，不调用模型生成内容。真正完成 B5 仍需要真实账号授权后该脚本返回对应 `verifiedTargets`。

## ADR-B024 计划任务调用 runner 串联 gate 与 post-auth

- 日期：2026-07-04
- 决策：Windows 计划任务不再直接调用 `browser-login-continuation-gate.ps1`，而是调用 `scripts/browser-login-continuation-runner.ps1`。runner 每次先运行 gate 并写 `last-status.json`；若 gate 退出码为 `0`，立即使用已有 gate 状态运行 `browser-login-post-auth-verify.ps1`；若 gate 退出码为 `2`，不运行 post-auth，只记录继续等待账号授权。runner 还会写 `runner-status.json` 作为跨回合汇总状态。
- 理由：用户希望“完成了一个，下一次自动触发”。只让计划任务写 gate 状态仍然需要下一轮人工/会话再读状态并触发 post-auth。runner 把“发现账号 ready -> 立即补证”放到同一次外部唤醒里，符合完成驱动续跑。
- 影响：计划任务的 `LastTaskResult=2` 仍表示没有账号 ready；`LastTaskResult=0` 表示 post-auth 补证成功，下一回合应读取 `runner-status.json`、`post-auth-status.json` 和 `post-auth-raw.txt` 更新兼容矩阵；`LastTaskResult=1` 表示 gate 或 post-auth 需要检查。runner 只编排已有只读脚本，不读取 token，不伪造 callback。

## ADR-B025 后置账号证据报告只整理脱敏状态

- 日期：2026-07-04
- 决策：新增 `scripts/browser-login-evidence-report.ps1`，从 `last-status.json`、`runner-status.json`、`post-auth-status.json` 和已脱敏的 `post-auth-raw.txt` 生成 Markdown 摘要，供后续更新 B5 兼容矩阵和进度记录使用。
- 理由：账号授权完成后，需要快速判断哪些账号已经通过官方状态命令和 post-auth 验证；直接人工翻 raw 输出容易遗漏边界，也容易误把敏感信息写入项目文档。报告脚本只做整理，不扩大认证边界。
- 影响：报告不是登录事实来源，登录事实仍以 CLI 官方状态命令、`post-auth-status.json` 和必要的设备/日志证据为准。报告继续遵守不保存账号邮箱、token、API key 或 callback code 原文的红线。

## ADR-B026 续跑链路用 mock 自测试保护

- 日期：2026-07-04
- 决策：新增 `scripts/test-browser-login-continuation.ps1`，用临时 mock gate/post-auth 状态覆盖等待态、ready 态和 stale post-auth 状态，作为修改续跑链路后的最小回归。
- 理由：B5 剩余完成依赖真实账号授权，但账号 ready 后的自动补证链路必须在等待期就保持可验证。过去已经出现旧 `post-auth-status.json` 污染当前等待态的风险，因此需要自测试把这些边界固定下来。
- 影响：该自测试不访问真实账号、不读取 token、不修改设备状态。它只证明续跑编排和脱敏报告逻辑，没有替代真实 Codex/Claude 账号授权完成证据。

## ADR-B027 完成状态必须通过审计脚本确认

- 日期：2026-07-04
- 决策：新增 `scripts/browser-login-completion-audit.ps1`，在宣称浏览器线完成前检查文档、实现、OnePlus 8T 证据、续跑脚本、runner 状态和 Codex/Claude 账号完成证据。只有该脚本输出 `status=complete` 且退出码为 `0`，才允许进入目标完成审计。
- 理由：当前 B0-B4 和 B5 账号门槛前证据已经很多，容易误把“机制完成”和“整条目标完成”混淆。用户的目标要求包含 OpenAI/ChatGPT 和 Claude 相关网页登录验证；没有真实账号完成证据时不能宣称完成。
- 影响：当前审计结果应为 `incomplete`，失败项为 `codex-account` 和 `claude-account`。审计脚本不读取 token、不伪造 callback、不替代真实账号授权，只负责把完成判定显式化。

## ADR-B028 runner 每次自动刷新 evidence report

- 日期：2026-07-04
- 决策：`scripts/browser-login-continuation-runner.ps1` 每次写入 `runner-status.json` 后自动调用 `scripts/browser-login-evidence-report.ps1`，刷新 `%LOCALAPPDATA%\Kite\browser-login-continuation\post-auth-evidence-report.md`。等待账号授权、gate 环境异常、post-auth 成功、post-auth 仍等待和 post-auth 失败分支都执行同一整理步骤。
- 理由：计划任务是跨回合唤醒器，只写 JSON 总状态不够直观；自动刷新 Markdown 摘要可以让下一次会话、人工检查或完成审计直接读取当前证据，而不需要额外等下一小时或手动补跑报告脚本。
- 影响：runner 退出码语义保持不变：`2` 表示继续等待真实账号授权，`0` 表示后置验证成功，`1` 表示环境或后置验证需要检查。等待态下 evidence report 退出码为 `2` 是可解释状态，不视为 runner 失败。报告继续只整理脱敏状态，不读取 token、不伪造 callback。

## ADR-B029 完成审计必须覆盖自动续跑链路

- 日期：2026-07-04
- 决策：`scripts/browser-login-completion-audit.ps1 -RefreshState` 不只检查账号完成项，还必须重新运行 runner、执行续跑自测试、确认 runner 自动生成的 `post-auth-evidence-report.md` 是新鲜的，并检查 Windows 计划任务 `KiteBrowserLoginContinuationGate` 启用、动作指向 `D:\xm\Kite-browser-login\scripts\browser-login-continuation-runner.ps1`、包含 `3f8bbaad` 且重复间隔不高于 5 分钟。
- 理由：浏览器线是长任务和跨回合续跑任务。若完成审计只检查最终账号状态，会漏掉“账号 ready 后是否能自动接 post-auth”和“外部唤醒是否仍指向正确物理副本”这两个长期运行风险。
- 影响：当前审计新增 `continuation-self-test`、`runner-refresh`、`runner-evidence-report` 和 `scheduled-continuation-task` 等审计项；这些项通过只能证明续跑链路健康，不能替代 `codex-account` 和 `claude-account` 的真实账号授权完成证据。

## ADR-B030 完成审计必须运行当前实现验证

- 日期：2026-07-04
- 决策：`scripts/browser-login-completion-audit.ps1 -RefreshState` 必须运行 `:app:testDebugUnitTest --tests com.kite.app.browser.*` 和 `:app:assembleDebug`，并把输出分别写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\browser-unit-test-output.txt` 与 `assemble-debug-output.txt`。审计报告新增 `browser-unit-tests` 和 `debug-apk-build` 项。
- 理由：只检查 `BrowserHandoffPolicy`、`BrowserAuthSessionStore` 和测试文件存在，不能证明当前 worktree 的通用网页登录回跳实现仍可编译、测试和打包。最终完成声明需要当前证据，而不是历史进度里的旧 `BUILD SUCCESSFUL`。
- 影响：宣称浏览器线完成前，审计必须证明浏览器 handoff 单测退出码为 `0`，`assembleDebug` 退出码为 `0`，且 debug APK 存在。账号完成证据仍独立要求真实 Codex/Claude 授权后 `verifiedTargets` 命中，不能由构建成功替代。

## ADR-B031 完成审计必须确认绑定设备在线

- 日期：2026-07-04
- 决策：`scripts/browser-login-completion-audit.ps1` 必须运行 `adb devices -l`，把输出写入 `%LOCALAPPDATA%\Kite\browser-login-continuation\adb-devices-output.txt`，并新增 `oneplus-device-online` 审计项，要求 `3f8bbaad` 处于 `device` 状态。
- 理由：浏览器线明确绑定 OnePlus 8T `3f8bbaad`。只保留历史截图和构建结果，不能证明当前验收环境仍然指向正确手机；如果误用 MEIZU/X11 设备或设备离线，最终完成声明会失真。
- 影响：当前 `adb devices -l` 同时能看到 MEIZU 和 OnePlus 8T，审计只接受 `3f8bbaad device product:OnePlus8T_CH model:KB2000 device:OnePlus8T` 作为浏览器线设备在线证据。该项通过不代表账号已授权，账号完成仍由 `codex-account` 和 `claude-account` 审计项独立判断。

## ADR-B032 账号验证采用通用节点模型

- 日期：2026-07-04
- 决策：账号验证证据按 `ACCOUNT_VERIFICATION_NODES.md` 的 N0-N5 节点记录，不绑定 Google、验证码、MFA 或某个特定 provider。N1/N2/N3 用来证明浏览器环境和回跳入口，N4/N5 才能证明具体账号已完成登录。
- 理由：用户明确补充账号验证不一定非得使用 Google 或某些必须验证码、多重验证的方法。浏览器线目标是完成通用网页登录回跳能力，而不是替用户绕过账号所有权挑战。
- 影响：兼容矩阵可以把 Google/OpenAI/Claude 记录为 `verified_account_gate`，表示已到账号挑战或回跳入口；完成审计仍必须把 `codex-account` 和 `claude-account` 的 N4/N5 真实账号完成证据单独列为强缺口。

## ADR-B033 人工账号验证前先做高置信度测试分层

- 日期：2026-07-04
- 决策：新增 `LOGIN_TEST_STRATEGY.md`，把人工 Google / OpenAI / Claude 验证前的测试拆成 C0-C4：官方合规、本地确定性测试、真机无账号测试、账号门槛测试和人工账号完成。Google 线先跑 G1-G4，确认 WebView 负控、Custom Tabs 正控、redirect 类型和敏感信息边界，再进入真人账号验证。
- 理由：仅等待用户最后人工点登录，无法判断失败属于 Kite 浏览器机制、OAuth client 配置、账号挑战还是 provider 策略。分层测试能把 Kite 可控部分测到确定性范围，同时不承诺验证码/MFA/风控一定通过。
- 影响：`browser-login-completion-audit.ps1` 将 `LOGIN_TEST_STRATEGY.md` 纳入必备文档；后续如果 Google 人工验证失败，先按测试策略归类失败节点，而不是回到 UA 伪装或 provider 特判。

## ADR-B034 真机 smoke test 作为人工账号验证前置证据

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-smoke-test.ps1` 作为 OnePlus 8T 无账号 smoke test。它启动 Kite、恢复 `18791 -> 8791` 转发、通过 `/open-web` 打开 Google OAuthPlayground、确认前台离开 Kite WebView、保存截图/UI dump、检查 UI dump 没有 `disallowed_useragent`、确认第三方 HTTPS redirect 不新增假 AppRedirect session，并过滤 FATAL/ANR/Input timeout。完成审计读取最近 24 小时 smoke 结果；只有传入 `-RunSmokeTest` 时才同轮重跑真机 smoke。
- 理由：用户要求我自己思考并长期跑测试方法，而不是只等人工账号验证。smoke test 能把 Kite 可控部分持续验证，同时避免完成审计每次都打断手机前台或反复打开浏览器。
- 影响：`browser-login-completion-audit.ps1` 新增 `browser-login-smoke` 审计项。该项通过只能证明 C2/C3 前置机制，不代表 Google/Codex/Claude 账号 N4/N5 已完成；真实账号完成仍由官方状态命令和 post-auth 证据判断。

## ADR-B035 smoke test 覆盖 App redirect 回跳与脱敏边界

- 日期：2026-07-05
- 决策：扩展 `scripts/browser-login-smoke-test.ps1`：在 Google OAuthPlayground 外部浏览器正控之后，再用 `redirect_uri=kite-auth://callback` 的 OAuth 形态 URL 创建唯一 `AppRedirect` pending session；随后通过 Android `ACTION_VIEW` 触发同 `state` 的 `kite-auth://callback`，要求 session 进入 `Delivered`，并断言 `returnedUrl` 只含 `code=present`、`access_token=present`、`state=present`，app 私有 `kite_browser_auth_sessions.xml` 不含本次假 `code` / token / `state` 原文。
- 理由：仅证明“Google 页面离开 WebView”还不够，人工账号验证前还要确认 Kite 可接收 redirect、同 state 匹配、交付到正确运行实例，以及敏感参数不会落盘。这个测试覆盖的是 Kite 机制本身，可以自动化；真实账号 N4/N5 仍必须等待用户完成。
- 影响：完成审计中的 `browser-login-smoke` 审计项现在同时代表 G1/G3/G4 的可控部分。该测试使用本机生成的假 code/token，不会向 provider 换 token，不会伪造 CLI 或账号登录成功；Codex/Claude 账号完成仍由官方状态命令和 post-auth 证据判断。

## ADR-B036 完成审计必须验证 smoke schema 和关键字段

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 输出结构化 schema。初版加固为 `schemaVersion=2`，后续 ADR-B038 / ADR-B039 / ADR-B040 / ADR-B041 / ADR-B042 / ADR-B043 / ADR-B051 / ADR-B061 已提升到当前 `schemaVersion=10`。`scripts/browser-login-completion-audit.ps1` 的 `browser-login-smoke` 项不再只接受最近 24 小时内 `status=passed`；它还必须看到 schema 达到当前要求、关键 item id 完整、外部浏览器 handler/fallback 证据、provider 页面无阻塞错误信号、多站点 OAuth 形态 URL 分流证据、`/open-web` 响应耗时、外部浏览器前台切换耗时、`appRedirectStatus=Delivered`、`appRedirectReturnedUrl=kite-auth://callback?code=present&access_token=present&state=present`、`appRedirectRawSecretHitCount=0`。
- 理由：旧版 smoke 结果也可能在 24 小时内显示 `status=passed`，但它只证明 Google OAuth 离开 WebView，不能证明 App redirect 回跳和脱敏边界。如果完成审计只看 `status`，后续会话可能把旧格式证据误认为已经覆盖 G3/G4。
- 影响：完成审计在没有刷新 smoke 时，会拒绝旧 schema 或残缺字段；需要传入 `-RunSmokeTest` 或先手动运行新版 smoke 生成证据。该加固不改变账号完成要求，Codex/Claude 的 N4/N5 仍必须来自真实账号授权后的官方状态命令和 post-auth 证据。

## ADR-B037 smoke test 纳入本地 handoff 响应耗时

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 增加 `open-web-responsive` 检查项，默认要求 Google OAuth URL 和 Kite App redirect URL 两次本地 `/open-web` 接收耗时都不高于 `1500ms`。完成审计把 `open-web-responsive` 纳入 required smoke item。
- 理由：用户要求浏览器登录链路既完整又不能卡顿。账号页面加载和验证码/MFA 速度由 provider、网络和账号策略决定，不能作为 Kite 的确定性验收；但 Kite 本地 server 接收请求、分类并发起 handoff 应该能快速给出承诺，这是可自动化、可重复验证的体验指标。
- 影响：smoke 报告会显示 `googleElapsedMs`、`appRedirectElapsedMs` 和阈值。该项失败时说明 Kite 本地 handoff 响应变慢，需要先查 LocalServer、UI 线程调度或 ADB/设备状态；该项通过不代表外部 provider 页面一定加载快，也不代表账号 N4/N5 完成。

## ADR-B038 smoke schema 3 覆盖普通 localhost WebView 回归

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=3`，新增普通 localhost WebView 回归：通过 `/open-web` 打开 `http://127.0.0.1:8791/status`，要求前台仍为 `com.kite.app`，且不新增 browser auth session。完成审计要求 `schemaVersion>=3`，并把 `local-web-open-accepted`、`local-webview-stays-in-kite`、`local-webview-no-auth-session` 纳入 required smoke item，同时直接检查 `localWebForegroundPackage=com.kite.app`。
- 理由：OAuth 授权必须离开 WebView，但普通本地 Web UI、资源页面和开发服务不能因此被误分流到外部浏览器。只靠分类器单测还不够，真机 smoke 应证明实际 `/open-web` 到 Activity/WebView 的链路没有破坏普通 localhost 页面。
- 影响：旧 `schemaVersion=2` smoke 结果会被完成审计拒绝，需要运行新版 smoke。该项通过只证明普通 localhost Web UI 回归，不替代真实 Codex/Claude 账号 N4/N5 完成证据。

## ADR-B039 smoke schema 4 扫描 app 私有文本文件中的 OAuth 临时值

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=4`，新增 `no-oauth-temporary-values-in-app-files` 检查项。smoke 在生成本轮 Google `state`、AppRedirect `state`、假 `code` 和假 token 后，扫描 app 私有 `files` / `shared_prefs` 下的文本类状态和诊断文件，要求这些临时值不以原文出现。完成审计要求 `schemaVersion>=4`，并直接检查 `appPrivateRawTemporaryValueHitCount=0`。
- 理由：只确认 `kite_browser_auth_sessions.xml` 脱敏还不够。OAuth 授权 URL 和 callback 参数也可能通过 diagnostics、status、recipe run 或日志类文件进入持久状态；人工账号验证前应把 Kite 自己可控的敏感值边界测成自动门禁。
- 影响：旧 `schemaVersion=3` smoke 结果会被完成审计拒绝。该扫描只处理本轮脚本生成的临时值，不读取真实 token，不证明 provider 已签发授权，也不替代 Codex/Claude 真实账号 N4/N5 完成证据。本轮首次运行 schema 4 smoke 时曾发现 `shared_prefs/kite_card_run_store.xml` 命中临时值，后续修复为 `CardRunStore` 持久化 OAuth URL 时写脱敏摘要，并在 App redirect 交付/失败后清理 `nextActionUrl`；因此该门禁会继续作为持久状态边界回归。

## ADR-B040 smoke schema 5 增加授权主机设备侧 HTTPS 可达性

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=5`，新增 `auth-hosts-network-reachable` 检查项。smoke 在进入浏览器 handoff 前，用 OnePlus 8T 设备侧 `curl` 分别访问 `accounts.google.com`、`auth.openai.com`、`claude.ai`，要求 `curl` 退出码为 `0` 且 HTTP 状态码在 `200..499` 范围内。完成审计要求 `schemaVersion>=5`，并把该检查项纳入 required smoke item。
- 理由：用户要求在人工 Google/OpenAI/Claude 账号验证前尽可能提高通过概率。Kite 已经能证明 WebView 分流、回跳、状态归属和敏感信息边界，但如果设备侧 DNS/TLS/网络路径无法到达授权主机，人工登录仍会失败。网络可达性是无账号、无 token、可自动化的前置风险面。
- 影响：该检查只证明设备侧能到达主要授权主机，不证明账号挑战、验证码/MFA、OAuth client 配置或 provider 授权会成功。HTTP `4xx` 仍视为网络路径可达，因为很多授权站会对非浏览器请求返回拒绝页；真正账号完成仍必须由 Codex/Claude 官方状态命令和 post-auth 证据确认。

## ADR-B041 smoke schema 6 验证外部浏览器 handler 与 fallback

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=6`，新增 `external-browser-handler-resolved` 与 `external-browser-handler-observed` 检查项。smoke 先用 Android `cmd package resolve-activity` 确认 `https://accounts.google.com/` 的默认 `ACTION_VIEW` handler 指向 `com.kite.app` 之外的浏览器 Activity，再记录 `android.support.customtabs.action.CustomTabsService` 查询结果；随后 Google OAuth handoff 后要求前台包名离开 Kite，并与默认 HTTPS handler 包名一致。完成审计要求 `schemaVersion>=6`。
- 理由：Google/RFC 的要求是使用真实外部 user-agent，而不是伪造 WebView。当前 OnePlus 8T 没有可查询的 Custom Tabs service，但系统 HTTPS handler 为 `com.heytap.browser/com.android.browser.RealBrowserActivity`，而实际 handoff 前台为 `com.heytap.browser`。因此测试应明确证明 Custom Tabs 不可用时系统浏览器 fallback 是可用路径，而不是把 CCT service 数量为 `0` 当成失败。
- 影响：后续人工账号验证前，smoke 会同时给出浏览器能力诊断和实测前台证据。Custom Tabs service 数量只作为体验优化和预热能力参考；真正的硬门槛是 HTTPS 授权 URL 能交给真实外部浏览器，且 Kite 不回落到 WebView。

## ADR-B042 smoke schema 7 增加 OpenAI/Claude OAuth 形态 URL 分流

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=7`，新增 `provider-oauth-openai-external-browser`、`provider-oauth-claude-external-browser` 和 `provider-oauth-no-auth-session` 检查项。smoke 使用 OpenAI/Codex 已观察到的 `auth.openai.com/oauth/authorize` 形态 URL，以及 Claude host 的登录/OAuth 形态 URL，在不输入账号、不伪造 callback 的前提下通过 `/open-web` 发起，要求前台从 Kite 切到外部浏览器 handler，并且不新增 AppRedirect、CliLoopback 或其他 browser auth session。
- 理由：B5 的目标不是只让 Google 样例通过，而是覆盖 Google、OpenAI/ChatGPT、Claude 相关网页登录场景。真实账号完成仍需要用户授权，但无账号 smoke 可以证明 Kite 的外部 user-agent 分流是按 OAuth 形态和 redirect 语义工作，不依赖 Google 域名单点特判。
- 影响：完成审计要求 `schemaVersion>=7`。该检查不声明 OpenAI 或 Claude provider 接受测试 client，不证明账号 N4/N5；它只证明多站点 OAuth 形态 URL 能走同一外部浏览器机制，且不会为第三方 HTTPS redirect 制造假的回跳 session。

## ADR-B043 smoke schema 8 增加外部浏览器前台切换耗时

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=8`，新增 `external-foreground-responsive` 检查项。smoke 在 Google、OpenAI/Codex、Claude 相关 OAuth 形态 URL 上记录从 `/open-web` 请求发出到外部浏览器成为前台的总耗时，默认阈值为 `5000ms`；完成审计要求 schema 8、新 item 和 `foregroundHandoffElapsedMs` / `providerOAuthForegroundMaxElapsedMs` 字段。
- 理由：用户要求浏览器链路不仅完整，还要“更高效、更完整、不卡顿、反应快”。旧的 `open-web-responsive` 只证明 Kite 本地 server 快速接收请求，不能证明用户实际看到了外部浏览器。前台切换耗时是无账号、无 token、可自动化的体验门禁。
- 影响：该检查失败时优先排查 Kite 本地 `/open-web`、Custom Tabs/系统浏览器 fallback、默认浏览器变化、设备前台状态和 ADB 观测延迟。该检查不覆盖 provider 页面加载、验证码/MFA、账号风控或 OAuth client 配置，也不能替代 Codex/Claude 的真实账号完成证据。

## ADR-B044 人工账号授权阶段使用 account watch 收证

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-account-watch.ps1`，作为人工 Codex/Claude 账号授权时的本机陪跑入口。watch 可选先跑 smoke test，然后按间隔调用现有 runner；账号仍未授权时写等待态，账号进入 ready 后由 runner 同轮触发 post-auth，最后写 `account-watch-status.json` 和 `account-watch-report.md`。
- 理由：B5 的最后强缺口必须由真实账号授权补齐。单纯让用户“登录完再告诉我”容易漏掉 runner/post-auth/evidence report 的即时证据，也可能混入旧状态。watch 把人工输入账号和自动收证分开：用户只处理 provider 账号挑战，脚本负责等待、补证和脱敏整理。
- 影响：watch 不输入账号、不读取 token、不伪造 callback、不调用模型生成内容。它只能把真实账号授权后的官方状态命令证据收集起来；如果仍未登录，退出 `2` 并保持 `waiting_for_real_account_authorization`。完成目标仍必须通过 `browser-login-completion-audit.ps1 -RefreshState` 证明 `codex-account` 和 `claude-account` 都通过。

## ADR-B045 多轮 smoke watch 用于人工账号验证前的稳定性置信度

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-smoke-watch.ps1`，长期或多轮调用现有 `browser-login-smoke-test.ps1`，把成功率、`/open-web` p95、外部浏览器前台切换 p95、HTTPS handler 稳定性、provider OAuth 形态误建 session、OAuth 临时值原文落盘等指标汇总为 `browser-login-smoke-watch.json` / `.md`。
- 理由：用户要求在人工 Google/OpenAI/Claude 账号验证前，我自己思考并持续跑测试，尽可能提高通过概率。一次 smoke 只能证明某一刻机制健康；多轮 watch 可以提前发现偶发网络漂移、默认浏览器变化、前台切换卡顿、状态泄漏或敏感字段回归。
- 影响：smoke watch 不输入账号、不读取 token、不伪造 provider callback，不替代 N4/N5 真实账号完成证据。它通过时只说明 Kite 可控机制在多轮无账号测试里稳定；失败时优先检查失败迭代的 smoke JSON、默认浏览器 handler、设备网络、前台切换耗时、session 计数和 app 私有文件临时值扫描。

## ADR-B046 account watch verified 后可显式接完成审计

- 日期：2026-07-05
- 决策：`scripts/browser-login-account-watch.ps1` 新增 `-RunCompletionAuditOnVerified`。只有显式传入该开关时，watch 在指定 targets 全部 verified 后才同轮调用 `browser-login-completion-audit.ps1 -RefreshState`，并把 `completionAuditExit`、`completionAuditStatus`、`completionAuditJsonPath` 和 `completionAuditReportPath` 写入 watch 状态。
- 理由：人工账号授权完成后，最容易漏掉的是“账号已登录”和“浏览器线整体验收完成”之间的最后一步审计。把完成审计接入 watch 可以减少人工调度，但默认不自动跑，是为了避免等待态或单账号验证时反复触发重构建和重审计。
- 影响：等待态不会触发完成审计；未传开关时 watch 行为保持不变。传入开关后，`completionAuditStatus=complete` 且 audit 退出码为 `0` 才能宣称浏览器线完成；如果 audit 仍为 `incomplete`，通常表示另一个账号或强证据仍未完成。

## ADR-B047 人工账号验证前采用多方法置信度组合

- 日期：2026-07-05
- 决策：人工 Google / OpenAI / Claude 账号验证前，不用单一页面或单一 smoke 结果判断“准备好了”。固定采用 T0-T5 组合：官方合规复核、白盒协议单测、OnePlus 8T 单轮 smoke、多轮 smoke watch、人工账号 account watch、完成审计。失败现象按 `disallowed_useragent`、`redirect_uri_mismatch` / `invalid_client`、账号挑战、官方 fallback、loopback callback 未交付、性能 p95 超阈值分层归因。
- 理由：用户要求我自己思考多种测试方式，尽可能提高最终人工账号验证通过概率，同时不把验证码、MFA、provider 风控或 OAuth client 配置伪装成 Kite 可自动保证的事项。Google 当前政策要求 secure browser 和匹配平台的 OAuth client；OpenAI Codex 与 Claude Code 又各自提供 headless / callback fallback，因此测试必须区分 Kite App redirect、第三方 HTTPS redirect 和 CLI loopback。
- 影响：`LOGIN_TEST_STRATEGY.md` 第 6 节成为人工账号验证前的执行矩阵。多轮 smoke watch 通过只能证明 Kite 可控机制稳定；Codex/Claude 完成仍必须由官方状态命令和 `browser-login-completion-audit.ps1 -RefreshState` 确认。

## ADR-B048 人工账号验证前使用只读准备度汇总

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-manual-readiness.ps1` 作为人工账号验证前的只读汇总入口。它读取最新 smoke、smoke watch、runner、account watch 和 completion audit 状态，输出 `manual-account-readiness.json` / `.md`，并给出 `ready_for_manual_account`、`not_ready`、`partial_account_verified_continue_watch`、`account_verified_run_completion_audit` 或 `complete`。
- 理由：T0-T5 证据分散在多个脚本和状态文件里，长时间续跑后容易漏看某个失败项。准备度脚本可以把“是否该让用户开始真人账号挑战”从口头判断变成可复查状态，同时不扩大敏感边界。
- 影响：该脚本不输入账号、不读取 token、不伪造 provider callback；`ready_for_manual_account` 只表示 Kite 可控链路和当前审计形态已经准备好，真实完成仍必须由 account watch、官方状态命令和 completion audit 确认。准备度要求 completion audit 在默认 24 小时新鲜窗口内，旧审计会输出 `not_ready` 并提示先刷新审计。完成审计已把该脚本列入必备续跑脚本。

## ADR-B049 完成审计必须验证 smoke watch 趋势证据

- 日期：2026-07-05
- 决策：`scripts/browser-login-completion-audit.ps1` 新增 `browser-login-smoke-watch` 审计项，读取最近的 `browser-login-smoke-watch.json` / `.md`，要求 24 小时内至少 3 轮通过、`failureCount=0`、`/open-web` p95 和外部浏览器前台切换 p95 不超过阈值、HTTPS handler 稳定且不是 Kite、provider OAuth 未误建 session、OAuth 临时值未原文落盘。
- 理由：B5 的验收不只是单轮 smoke，还明确要求长期或多轮趋势验证，用于发现偶发卡顿、默认浏览器变化、session 泄漏和临时值落盘。若完成审计只检查单轮 smoke，会把“某一刻通过”误当成“人工账号验证前的稳定性风险已排除”。
- 影响：最终完成前如果 smoke watch 过期、缺失或趋势指标失败，completion audit 会新增失败项 `browser-login-smoke-watch`。这不会替代 `codex-account` / `claude-account` 的真实账号完成证据；它只是把用户要求的长期稳定性置信度变成强审计门槛。

## ADR-B050 account watch 可先执行人工准备度预检

- 日期：2026-07-05
- 决策：`scripts/browser-login-account-watch.ps1` 新增并推荐使用 `-RunReadinessFirst`。开启后，watch 会先运行 `browser-login-manual-readiness.ps1 -RefreshState -RunCompletionAudit`；只有准备度为 `ready_for_manual_account`、`partial_account_verified_continue_watch` 或 `account_verified_run_completion_audit` 时，才继续调用 runner 轮询账号状态。准备度失败时写入 `manual_readiness_failed`，不调用 runner。
- 理由：人工账号验证阶段最容易把环境问题、过期 smoke/audit、账号仍未授权混在一起。watch 入口前增加只读准备度门槛，可以在用户开始 Google/OpenAI/Claude 账号挑战前先排除 Kite 可控链路缺口，同时仍允许“一个账号已验证、另一个账号继续等待”的半程状态。
- 影响：`browser-login-manual-readiness.ps1` 推荐的 account watch 命令会带 `-RunReadinessFirst`；续跑自测试新增 readiness 通过/失败分支。该预检不输入账号、不读取 token、不伪造 callback，也不替代 `codex-account` / `claude-account` 的真实完成证据。

## ADR-B051 smoke schema 9 为授权主机探测增加重试证据

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=9`，授权主机 HTTPS 探测对每个 host 默认允许 2 次尝试，并在 `authHostNetworkResults[].attempts` / `attemptCount` 中记录每次 `curl` 的退出码、HTTP 状态和输出摘要。该阶段 `browser-login-completion-audit.ps1` 与 manual readiness 要求 smoke schema 不低于 9；后续 ADR-B061 已把当前门槛提升到 10。
- 理由：6 轮真机 smoke watch 中曾出现一次 `accounts.google.com` 设备侧 5 秒连接超时，后续同轮又恢复 `200`。这类瞬时网络毛刺不应被静默当成 Kite handoff 失败，也不能直接忽略；重试加证据可以区分偶发网络抖动和持续不可达。
- 影响：旧 `schemaVersion=8` smoke 结果会被 completion audit 和 manual readiness 拒绝，需要重新运行新版 smoke。若某个授权主机所有 attempts 都失败，`auth-hosts-network-reachable` 仍失败；该机制不替代真实账号授权完成证据。后续 ADR-B061 已把当前完成审计门槛提升到 `schemaVersion=10`。

## ADR-B052 account watch 的 smoke 刷新先于 readiness 门槛

- 日期：2026-07-05
- 决策：`scripts/browser-login-account-watch.ps1` 同时传入 `-RunSmokeFirst -RunReadinessFirst` 时，必须先运行无账号 smoke test，再运行 `browser-login-manual-readiness.ps1 -RefreshState -RunCompletionAudit`。manual readiness 推荐命令和 SOP 命令也统一展示为 `-RunSmokeFirst -RunReadinessFirst`。
- 理由：manual readiness 会检查最新 smoke schema、smoke watch 和 completion audit。若 account watch 先做 readiness，旧 smoke 或过期 smoke 会在 `-RunSmokeFirst` 有机会刷新前导致 `manual_readiness_failed`，让人工账号陪跑入口误拒绝。先 smoke 再 readiness 可以把“可自动刷新的证据过期”和“真实环境不 ready”分开。
- 影响：人工账号验证前的推荐入口会先排除当前设备浏览器环境、回跳、脱敏和性能门槛，再进入账号状态轮询。smoke 失败仍会停止 watch 并写 `smoke_failed`；smoke 通过但 readiness 失败会写 `manual_readiness_failed`。该顺序不输入账号、不读取 token、不伪造 callback，也不替代 Codex/Claude 真实账号完成证据。

## ADR-B053 人工账号启动入口只拉起真实登录流程

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-manual-account-start.ps1` 作为人工账号验证的默认入口。该脚本先运行 smoke/readiness 门槛，再通过现有 `runtime_action=start_resource_open` 启动 `kite.codex.cli` 和/或 `kite.claude.code` 的真实终端登录入口；可选 `-StartWatch` 接 `browser-login-account-watch.ps1` 继续等待官方状态命令变为 verified。默认 watch 行为仍适合人工长时间陪跑；自动短验证显式传 `-WatchMaxAttempts 1 -WatchPollSeconds 0`。
- 理由：B5 最后缺口必须由用户真实账号授权补齐。只有 watch 时，用户仍需要手动找到并启动资源，容易漏步骤或启动到错误设备；新增启动入口可以把“准备度通过 -> 拉起真实 CLI 登录 -> watch 收证”做成同一条可复查路径，同时复用现有资源运行和状态拥有者。
- 影响：启动入口成功只表示真实 Codex/Claude 终端登录流程已被拉起，不表示账号已授权。它不输入账号、不读取 token、不伪造 callback；最终完成仍以 post-auth 官方状态命令和 `browser-login-completion-audit.ps1 -RefreshState` 为准。completion audit 把该脚本列为必备续跑脚本，续跑自测试覆盖 plan、launch、readiness 失败跳过启动、bounded watch 和 watch 等待分支。

## ADR-B054 manual readiness 汇总人工账号启动状态但不当作登录事实

- 日期：2026-07-05
- 决策：`scripts/browser-login-manual-readiness.ps1` 读取 `manual-account-start-status.json`，把人工账号启动入口纳入 T4 准备度检查。`planned`、`launched`、`watch_waiting_for_real_account_authorization` 和 `watch_verified` 表示启动入口已知或处于可继续收证状态；状态文件缺失时，只要 runner 当前健康，视为可重新生成；`launch_failed` 和 `watch_needs_inspection` 会让 readiness 进入 `not_ready`。
- 理由：人工账号验证前的关键状态分散在 smoke、runner、account watch、manual account start 和 completion audit 中。只看 readiness 而不看启动入口，可能漏掉“准备度通过但资源启动失败”的问题；但把 `launched` 或 `watch_waiting_for_real_account_authorization` 当成登录成功又会复制登录事实来源。
- 影响：readiness 报告现在会显示 `manualStartStatus`、`manualStartLaunchedTargets`、`manualStartWatchExit` 和 `manualStartWatchMaxAttempts`。这些字段只用于判断是否该继续人工账号挑战或先修启动入口；真实完成仍以 Codex/Claude 官方状态命令、post-auth 和 completion audit 为准。

## ADR-B055 completion audit 审计人工账号启动状态

- 日期：2026-07-05
- 决策：`scripts/browser-login-completion-audit.ps1` 新增 `manual-account-start-state` 审计项。账号未全部 verified 时，manual account start 状态缺失但 runner 可读，或最近 24 小时内为 `planned`、`launched`、`watch_waiting_for_real_account_authorization`、`watch_verified` 才通过；`launch_failed` 和 `watch_needs_inspection` 必须先检查。账号都已 verified 后，旧启动状态不再阻塞完成。
- 理由：完成审计是浏览器线宣称完成前的最后门槛，只检查 manual account start 脚本存在还不够。人工账号验证入口如果最近启动失败，审计应把它暴露出来；但账号已经由官方状态命令 verified 后，启动入口的旧等待/失败状态不再是登录事实来源。
- 影响：审计 JSON 会写入 `manualStartStatus`、`manualStartTargets`、`manualStartLaunchedTargets`、`manualStartWatchExit` 和 `manualStartWatchMaxAttempts`。该审计项仍不读取 token、不输入账号、不伪造 callback；它只检查人工验证入口是否可继续或可重新生成。

## ADR-B056 completion audit 审计 account watch 状态

- 日期：2026-07-05
- 决策：`scripts/browser-login-completion-audit.ps1` 新增 `account-watch-state` 审计项。账号未全部 verified 时，account watch 状态缺失但 runner 可读，或状态为 `waiting_for_real_account_authorization` / `verified` 才通过；新鲜的 `smoke_failed`、`manual_readiness_failed`、`needs_inspection` 必须先检查。陈旧 watch 状态可由当前 runner 状态兜底。账号都已 verified 后，旧 watch 状态不再阻塞完成。
- 理由：account watch 是人工账号授权期间的陪跑收证入口。若最近 watch 明确失败，完成审计应直接暴露；但 watch 状态可能来自一次短验证或旧陪跑，不能让旧等待/失败状态复制登录事实或误伤当前 runner/post-auth 结果。
- 影响：审计 JSON 会写入 `accountWatchStatus`、`accountWatchTargets`、`accountWatchWaitingTargets`、`accountWatchVerifiedTargets`、`accountWatchAttempts` 和 `accountWatchMaxAttempts`。该审计项不读取 token、不输入账号、不伪造 callback；真实完成仍以 runner/post-auth 官方状态命令和账号审计项为准。

## ADR-B057 smoke test 不把 AppRedirect Returned 当作终态

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 在验证 `kite-auth://callback` 时，只把 `Delivered` 和 `Failed` 当作等待结束状态；`Returned` 只是 App 已接到 redirect、尚未完成运行实例交付的中间态。
- 理由：真机短验证中出现过 session 先进入 `Returned`，随后同一 session 正常变为 `Delivered` 的情况。旧 smoke 把 `Returned` 放进等待终止集合，会提前采样并把可恢复中间态误判为 `appredirect-callback-delivered` 失败。
- 影响：smoke 仍要求最终 `appRedirectStatus=Delivered` 才通过；如果运行实例交付真的失败，等待结束后仍会保留 `Returned` 或 `Failed` 作为失败证据。该改动不放宽完成标准，不读取 token，不伪造 callback，也不改变 App 运行时状态拥有者。

## ADR-B058 当前会话内使用短周期六轮 smoke watch 做高置信度样本

- 日期：2026-07-05
- 决策：在没有真实账号授权输入时，当前会话内优先运行短周期六轮 `browser-login-smoke-watch.ps1 -Iterations 6 -IntervalSeconds 0`，用于快速刷新 Kite 可控链路的稳定性样本；小时级或 24 小时长期 watch 仍交给外部调度器按较长间隔唤醒。
- 理由：用户要求我自己思考并持续测试，提高最终人工验证通过概率。短周期六轮能在一个会话内及时暴露默认浏览器漂移、前台切换卡顿、假 session 泄漏和临时值落盘；但它不能替代真实账号挑战，也不应让 Codex 长时间独占设备等待无人输入。
- 影响：完成审计仍只要求最近 24 小时内至少 3 轮趋势证据通过；六轮短样本通过时可作为人工账号验证前的更强准备度证据。最终完成仍必须由 Codex/Claude 官方状态命令和 `browser-login-completion-audit.ps1 -RefreshState` 证明。

## ADR-B059 账号等待期 long-run cycle 不污染主趋势证据

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-long-run-cycle.ps1` 作为账号等待期间的单次长期巡检循环，串联 runner、smoke watch、manual readiness 和可选 completion audit。少于 3 轮的短 cycle 只把 smoke watch 输出写入 `browser-login-long-run-cycle-smoke\` 子目录；只有 3 轮及以上才刷新 completion audit 使用的主 `browser-login-smoke-watch.json`。
- 理由：真实验证中，1 轮短 cycle 曾把主 smoke watch 证据覆盖为 `iterations=1`，导致 completion audit 正确新增 `browser-login-smoke-watch` 失败项。短周期验证适合快速看当前设备状态，但不能降级最终审计要求的至少 3 轮趋势证据。
- 影响：`browser-login-long-run-cycle.json` 成为调度器读取的总状态；`waiting_account_browser_stable` 只表示账号等待期间浏览器链路仍健康。现有 `KiteBrowserLoginContinuationGate` runner 计划任务不被自动替换；如需要小时级浏览器巡检，可显式运行 `register-browser-login-long-run-cycle.ps1` 注册独立任务。

## ADR-B060 provider preflight 只做人工验证前的综合归因

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-provider-preflight.ps1`，把 Google / OpenAI / Claude 人工验证前的官方合规、外部浏览器环境、provider 页面阻塞错误、redirect 类型、CLI callback/fallback、敏感边界、性能、runner 和 completion audit 状态汇总到 `provider-auth-preflight.json` / `.md`。状态 `ready_for_manual_provider_auth` 与退出码 `2` 表示 Kite 可控链路 ready 但仍等真人账号；不是账号已登录。
- 理由：用户要求在人工验证前尽量由 Codex 自己跑测试并思考失败归因。原有 smoke、smoke watch、manual readiness、long-run cycle 和 completion audit 都能证明不同层面，但人工开始前仍需要一个“现在为什么能/不能开始”的总仪表盘，避免把验证码/MFA、provider client 配置、provider 页面阻塞错误、WebView 回退、loopback fallback 和性能卡顿混在一起。
- 影响：provider preflight 的失败桶用于下一步排查：`browser_environment`、`provider_configuration`、`account_challenge`、`cli_callback_or_fallback`、`sensitive_boundary`、`performance`、`post_auth`。它不输入账号、不读取 token、不伪造 callback，不替代 Codex/Claude 官方状态命令和最终 completion audit。

## ADR-B061 smoke schema 10 增加 provider 页面阻塞错误信号

- 日期：2026-07-05
- 决策：`scripts/browser-login-smoke-test.ps1` 提升到 `schemaVersion=10`，在外部浏览器 UI dump 上解析 provider 页面信号，新增 `provider-page-no-blocking-error` 检查项和 `providerPageSignalState`、`providerPageBlockingErrorCount`、`providerPageBlockingErrorMatches`、`providerPageChallengeHintCount`、`providerPageChallengeHintMatches` 字段。`browser-login-smoke-watch.ps1` 汇总 `providerPageBlockingErrorRunCount`，`browser-login-provider-preflight.ps1` 和 `browser-login-completion-audit.ps1` 都把该证据纳入人工验证前门槛。
- 理由：只证明 OAuth 授权页离开 WebView，还不能排除 provider 直接显示 `redirect_uri_mismatch`、`invalid_client`、`unsupported_browser`、`Error 400/403` 等阻塞性错误。用户要求我在人工账号验证前主动设计测试方法，提高最终人工验证通过概率；页面阻塞错误信号能把“浏览器环境/配置问题”和“验证码/MFA/账号挑战”提前分开。
- 影响：旧 `schemaVersion=9` smoke 结果会被 completion audit 和 provider preflight 拒绝，需要重新运行新版 smoke。该检查不输入账号、不读取 token、不伪造 callback；登录、继续、验证码、MFA、paste code 等只作为账号挑战提示，不表示账号 N4/N5 已完成。OnePlus 8T 最新三轮 smoke watch 结果为 `providerPageBlockingErrorRunCount=0`，completion audit 仍只缺 `codex-account` 和 `claude-account`。

## ADR-B062 人工验证前按分段指标判断浏览器效率和完整性

- 日期：2026-07-05
- 决策：人工 Google / OpenAI / Claude 验证前，浏览器线不再用单一“页面打开成功”作为高置信度依据，而是固定读取分段指标：`/open-web` 本地接收耗时、外部浏览器前台切换耗时、默认 HTTPS handler、Custom Tabs 能力、provider 页面阻塞信号、多站点 OAuth 形态是否误建 session、CLI listener 或官方 fallback、敏感值落盘扫描和最终官方账号状态。
- 理由：用户要求长期自主跑测试时同时关注“能否通过人工验证”和“浏览器是否更高效、更完整、不卡顿”。这些风险分属不同层：Kite LocalServer、Android handler、浏览器启动、provider 配置、账号挑战、CLI callback 和敏感边界。混成一个成功/失败会导致误修 UA、伪装环境或重启页面。
- 影响：`LOGIN_TEST_STRATEGY.md` 的长期自测矩阵成为人工验证前的判断口径。`ready_for_manual_provider_auth` 只表示 Kite 可控链路 ready；最终完成仍必须等 Codex/Claude 官方状态命令和 completion audit `status=complete`。

## ADR-B063 小时级 long-run cycle 作为浏览器稳定性外部调度

- 日期：2026-07-05
- 决策：注册独立 Windows 计划任务 `KiteBrowserLoginLongRunCycle`，每小时调用本副本 `scripts/browser-login-long-run-cycle.ps1`，绑定 OnePlus 8T `3f8bbaad`，使用 `SmokeIterations=6` / `SmokeIntervalSeconds=600`。它只负责账号等待期的浏览器稳定性、性能和趋势巡检；现有 `KiteBrowserLoginContinuationGate` 继续以 5 分钟频率负责账号 ready 后立即接 post-auth。
- 理由：账号 gate 只判断是否有 Codex/Claude 账号进入后置验证窗口，不能发现长时间等待中的默认浏览器漂移、设备网络毛刺、前台切换变慢、provider 页面阻塞错误或 session 泄漏。用户要求我在几个小时内自己持续跑测试并思考验证方法，因此需要把浏览器长期稳定性从账号门槛里拆成独立调度。
- 影响：completion audit 新增 `scheduled-long-run-cycle-task`，同时检查账号 gate 和小时级 long-run cycle 两条计划任务。long-run cycle 不输入账号、不读取 token、不伪造 provider callback；它通过只能证明 Kite 可控链路稳定，B5 最终完成仍以 Codex/OpenAI 与 Claude/Anthropic 官方账号状态和 completion audit `status=complete` 为准。

## ADR-B064 长跑 progress JSON 只表示运行中可观测性

- 日期：2026-07-05
- 决策：`browser-login-smoke-watch.ps1` 写入 `browser-login-smoke-watch-progress.json`，逐轮记录已完成轮数、剩余轮数和最后一轮状态；`browser-login-long-run-cycle.ps1` 写入 `browser-login-long-run-cycle-progress.json`，记录当前处于 runner、smoke watch、manual readiness、completion audit 或 finished 阶段。
- 理由：小时级 long-run cycle 一次运行可能持续近一小时；只有最终 JSON 会让后续会话无法判断它是正常长跑、卡在某轮 smoke，还是已经进入 readiness/audit。progress JSON 可以把“运行中”从“无输出”里分离出来，降低误判和重复启动的概率。
- 影响：progress JSON 不是完成证据，不参与账号完成判断，不读取 token，不输入账号，不伪造 callback。最终完成仍只看 runner/post-auth 官方账号状态和 `browser-login-completion-audit.ps1`；progress 只用于长跑调度和排障。

## ADR-B065 当前状态汇总只做只读调度判断

- 日期：2026-07-05
- 决策：新增 `scripts/browser-login-status-summary.ps1`，只读汇总 Windows 计划任务、runner、long-run、latest smoke、smoke watch、manual readiness、completion audit、provider preflight 和 progress 文件，写出 `browser-login-status-summary.json` / `.md`。它把账号等待、长跑运行中、最近单轮浏览器健康样本、ready targets、非账号审计失败等状态归成一个当前判断。
- 理由：长任务续跑时，单看某一个 JSON 容易误判。例如当前 `KiteBrowserLoginLongRunCycle` 正在运行旧进程，progress 文件缺失但最新 smoke 文件已刷新；这应被解释为 `running_without_progress_from_pre_progress_script`，而不是认为长跑失败或需要并发再跑一遍。汇总脚本把这些分散事实拼成可复查的只读看板。
- 影响：该脚本不启动真机测试、不输入账号、不读取 token、不伪造 provider callback、不修改计划任务。它的退出码 `0` 可表示“当前健康但仍等待账号”；`smoke` 字段只表示最新无账号单轮样本，不替代 smoke watch 趋势或真实账号完成证据。最终完成仍必须由 runner/post-auth 官方账号状态和 completion audit `status=complete` 证明。

## ADR-B066 人工验证通过率按分层置信度管理

- 日期：2026-07-05
- 决策：人工 Google / OpenAI / Claude 验证前，不再用“能打开页面”或“多跑几轮 smoke”单独代表通过概率，而是按浏览器合规、provider 配置早期错误、网络性能、AppRedirect/CLI callback、敏感边界和账号所有权六层判断。Kite 可控层尽量自动化到高置信或接近确定；账号所有权、验证码、MFA、组织策略和 provider 风控只由用户真实授权和官方状态命令收证。
- 理由：用户要求我在账号等待期主动思考测试方法，提高最终人工验证通过概率，同时不要陷入单一测试。官方资料也要求使用真实外部 user-agent、匹配的 client/redirect、state/PKCE 和用户交互；这意味着“100%”只能用于 Kite 自己的机制，不应用来承诺第三方账号放行。
- 影响：`LOGIN_TEST_STRATEGY.md` 增加通过率置信模型和自主测试实验池。后续如果人工验证失败，先按 `disallowed_useragent`、provider 配置错误、正常账号挑战、callback/fallback 和性能慢五类归因，不先改 UA、不做 WebView 伪装、不伪造 provider callback。

## ADR-B067 状态汇总时间戳固定为 ISO 8601

- 日期：2026-07-05
- 决策：`scripts/browser-login-status-summary.ps1` 输出的计划任务时间、状态文件 `checkedAt` 和控制台 `smokeCheckedAt` 统一格式化为 ISO 8601，例如 `2026-07-05T05:49:25+08:00`。
- 理由：PowerShell 读取 JSON 后可能把时间值转成 `DateTime`，控制台插值时会使用本机区域格式，导致同一轮长跑证据在 JSON、Markdown 和控制台里表现不一致。浏览器线后续会长期依赖计划任务和状态文件对账，时间格式必须可复制、可排序、可机器读取。
- 影响：该改动只影响状态汇总的可观测性，不启动真机测试、不改变账号 gate、不读取 token、不伪造 callback。后续所有人工验证和长跑排障报告优先使用 ISO 时间写入三件套。

## ADR-B068 状态汇总负责识别长跑无 progress 超时

- 日期：2026-07-05
- 决策：`scripts/browser-login-status-summary.ps1` 在 `KiteBrowserLoginLongRunCycle` 运行中但缺少 `browser-login-long-run-cycle-progress.json` 时，不再只能长期输出 `running_without_progress_from_pre_progress_script`。它会解析计划任务动作中的 `SmokeIterations` 和 `SmokeIntervalSeconds`，按 `(SmokeIterations - 1) * SmokeIntervalSeconds + 1800` 估算宽限时间；超过宽限才输出 `running_without_progress_overdue` / `long_run_running_without_progress_overdue` 并返回退出码 `1`。
- 理由：当前长跑可能在 progress JSON 功能加入前启动，短时间没有 progress 是预期；但 24 小时巡检里如果进程真的卡住，继续把它解释成“旧进程无 progress”会掩盖浏览器稳定性验证缺口。用保守超时判断能在不并发启动新 smoke/watch 的前提下提示检查。
- 影响：该判断只读计划任务和状态文件，不停止任务、不重启任务、不输入账号、不读取 token、不伪造 callback。它只提高调度可观测性；真实账号完成仍以 runner/post-auth 官方状态命令和 completion audit 为准。

## ADR-B069 latest smoke 关联只作调度证据

- 日期：2026-07-05
- 决策：`scripts/browser-login-status-summary.ps1` 在长跑运行中且没有 progress JSON 时，额外输出 latest smoke 与当前 long-run 启动时间的关系：`latestSmokeCheckedAt`、`latestSmokeAgeSeconds/Minutes`、`latestSmokeAfterCurrentRunStart` 和 `latestSmokeSecondsAfterCurrentRunStart`。当 latest smoke 晚于本轮 long-run 启动且未超过无 progress 宽限时，`longRunObservation` 使用 `running_without_progress_latest_smoke_after_current_run_start`。
- 理由：账号等待期可能连续数小时运行，旧进程没有 progress JSON 但仍会刷新 smoke 样本。只看“无 progress”容易误以为长跑空转；只看 latest smoke 又容易误以为该样本一定由当前长跑写入。把时间关系写成只读字段，可以让后续续跑更快判断是否需要等待、检查长跑或避免并发启动新测试。
- 影响：该关联不是强归因，不证明 latest smoke 一定由当前计划任务写入，也不替代 smoke watch 趋势、provider preflight、manual readiness 或真实账号 N4/N5 完成证据。该脚本仍不启动真机测试、不输入账号、不读取 token、不伪造 provider callback。

## ADR-B070 status summary 输出无 progress 告警时间

- 日期：2026-07-05
- 决策：`scripts/browser-login-status-summary.ps1` 在已有 `noProgressGraceSeconds` 的基础上输出 `noProgressOverdueAt`、`noProgressSecondsRemaining` 和 `noProgressMinutesRemaining`。这些字段只用于告诉后续续跑“何时才应检查长跑”，不作为自动停止、重启或并发启动 smoke/watch 的触发器。
- 理由：账号等待期的 long-run cycle 可能持续接近一小时。只输出 `noProgressOverdue=False` 仍需要人工或后续 Codex 计算剩余时间，容易在接近边界时误判是否该检查。直接输出告警时间和剩余分钟，能让持续任务更稳地避免重复长跑和误报。
- 影响：该改动只增加只读调度可观测性，不改变计划任务、不启动真机测试、不输入账号、不读取 token、不伪造 provider callback。真实账号完成仍以 runner/post-auth 官方状态命令和 completion audit 为准。

## ADR-B071 provider preflight 输出语义退出码和 ISO 时间戳

- 日期：2026-07-05
- 决策：`scripts/browser-login-provider-preflight.ps1` 在 `provider-auth-preflight.json` 顶层写入语义 `exitCode`，并在 Markdown 和控制台同步输出；`smokeCheckedAt` / `smokeWatchCheckedAt` 统一用 ISO 8601 落盘。
- 理由：人工 provider 验证前的预检既给人读，也会被后续长任务续跑读取。PowerShell 读 JSON 后可能把时间对象插值成本机区域格式；非零退出码又容易被工具面板概括成失败。把语义退出码和 ISO 时间写进状态文件，可以让后续判断“环境 ready 但等待账号”和“环境不 ready”时不靠猜。
- 影响：该改动只增加 preflight 可观测性，不改变任何登录判定、计划任务或账号状态，不输入账号、不读取 token、不伪造 provider callback。等待真实账号时仍是 `ready_for_manual_provider_auth` / `exitCode=2`；B5 最终完成仍必须由 Codex/Claude 官方账号状态和 completion audit 证明。

## ADR-B072 status summary 输出运行中 progress 细节

- 日期：2026-07-05
- 决策：`scripts/browser-login-status-summary.ps1` 在 `running_with_progress` 场景下输出 `longRunProgress.phase/status/ageMinutes`、`smokeWatchProgress.completedIterations/remainingIterations/nextExpectedAt/nextExpectedOverdue`，并把 `providerPreflight.exitCode` 纳入当前汇总。
- 理由：账号等待期的小时级 long-run 会持续近一小时，只有 `running_with_progress` 还需要继续打开 `browser-login-long-run-cycle-progress.json` 和 `browser-login-smoke-watch-progress.json` 才能知道当前轮次、是否正在睡眠、下一轮何时应出现。把这些只读字段放进汇总，可以减少误判卡住、误启动并发 smoke/watch 或漏看 preflight 语义退出码。
- 影响：这些字段只用于调度可观测性和人工复核，不改变计划任务、不停止或重启长跑、不启动真机测试、不输入账号、不读取 token、不伪造 provider callback。`nextExpectedAt` 不是触发器，只表示当前 progress 文件推导出的下一轮 smoke 预计时间；真实账号完成仍以 runner/post-auth 官方状态命令和 completion audit 为准。

## ADR-B073 人工 provider 验证使用分层作战清单收口

- 日期：2026-07-05
- 决策：人工 Google / OpenAI / Claude 验证前，统一使用 `ACCOUNT_AUTH_COMPLETION_SOP.md` 的“人工验证作战清单”收口。先读 status summary 和 provider preflight，再决定是否进入 manual account start / account watch；失败归因固定按浏览器环境、provider 配置、账号挑战、callback/fallback、性能分层处理。
- 理由：用户要求 Codex 在人工验证前主动思考测试方式，提高通过概率并关注浏览器是否完整、不卡顿。官方资料复核后，正路仍是 external user-agent、合适 redirect、PKCE/state、App Links 或 CLI 官方 fallback；账号所有权、验证码、MFA 和 provider 风控不能由 Kite 自动保证。把清单写成 SOP，可以让长跑和后续分支会话按同一个判断入口执行。
- 影响：默认使用真实系统浏览器 / Custom Tabs / Chrome Auth Tab 候选能力，不把无痕、无指纹、UA 伪装或自动化隐藏作为提高通过率的默认路径。Codex 只保存脱敏 UI 摘要和官方状态命令结果，不读取 token、不输入账号、不伪造 callback；最终完成仍必须以 Codex/Claude 官方状态命令和 completion audit `status=complete` 为准。

## ADR-B074 浏览器运行模式切换不改变认证安全边界

- 日期：2026-07-05
- 决策：Kite 设置中新增浏览器运行模式选择：默认“WebView + 系统浏览器登录”继续承载本地 Web UI 和普通网页，并把 OAuth/SSO 授权交给系统浏览器；“自动浏览器”作为后续实验模式的显式入口，先只建立持久化状态和产品边界，不宣称自动化内核已完成。
- 理由：用户要求长期推进自动浏览器能力，但当前已经验证有效的官方路线是 external user-agent。自动浏览器未来主要服务元素化、网页自动操作和非敏感会话管理；涉及 Google/OpenAI/Anthropic 等账号授权时，仍必须优先尊重官方外部浏览器、state/PKCE、callback/fallback 和账号所有权验证要求。
- 影响：后续实现可以读取统一的浏览器运行模式来选择自动化控制面，但不得因为切到自动浏览器就把 OAuth 授权页拉回 WebView，也不得把 UA/指纹伪装作为默认登录方案。自动浏览器未完成前，设置入口只能表达实验意图和后续能力，不伪造成功状态。
