# 账号验证通用节点

本文件用于把“账号验证”从某个具体站点、验证码、MFA 或人工账号挑战中抽象出来。浏览器登录线要证明的是通用网页登录回跳机制，而不是替用户绕过或代办某个账号的安全验证。

人工账号验证前的测试策略见 `docs/browser-login/LOGIN_TEST_STRATEGY.md`。本文件定义“节点怎么判”，测试策略定义“怎么把每个节点测到高置信度”。

## 核心判断

账号验证分成两类证据：

1. 浏览器环境与回跳机制证据：Kite 是否用合规外部浏览器承载登录页，是否能把结果回到 App 或 CLI 所属运行实例。
2. 账号所有权完成证据：用户是否用自己的账号通过 provider 的验证码、MFA、风控或授权确认，并让状态拥有者确认已登录。

第一类是 Kite 必须实现和验证的通用能力。第二类不能伪造，也不一定必须选择 Google 这类容易触发验证码或多重验证的账号作为唯一证明对象。

## 通用节点

| 节点 | 含义 | 典型证据 | 当前策略 |
| --- | --- | --- | --- |
| N0 环境拒绝复现 | 内嵌 WebView 或不合规环境被 provider 拒绝 | Google `403: disallowed_useragent` 截图、URL、日志 | 用于证明原问题类别，不作为成功路径 |
| N1 合规浏览器打开 | 授权页离开 WebView，进入 Custom Tabs 或系统浏览器 | 前台浏览器 Activity、截图、无 WebView 拒绝错误 | Kite 必须满足 |
| N2 账号挑战到达 | provider 接受浏览器环境，并进入登录页、验证码、MFA、授权确认或 paste code 提示 | 登录页截图、CLI fallback 提示、终端保留 | 可作为“环境和登录入口通过”的证据 |
| N3 回跳入口可达 | App redirect、localhost callback 或官方 fallback 的入口存在且不被 Kite 抢占 | `kite-auth://callback` 回到 CardRun、loopback listener 可达、paste code 提示 | Kite 必须验证至少一种通用回跳能力 |
| N4 状态拥有者确认 | App/CLI/OAuth client 自己确认认证状态，不由 Kite 猜测 | `codex login status`、`claude auth status`、App 会话状态 | 只在真实账号完成后成立 |
| N5 后置健康检查 | 已登录状态能通过非交互健康命令或只读状态命令复验 | `codex doctor --json`、`claude auth status --json`、脱敏 report | 用于最终账号完成补证 |

## 完成口径

- Google 一类容易触发验证码、MFA 或风控的账号，不是唯一完成路径。
- 如果某个 provider 已到达 N2，但后续需要用户真人账号挑战，则记录为 `verified_account_gate` 或 `blocked_by_account_or_cli`，不能伪造 N4/N5。
- Kite 的通用机制完成，应至少证明 N1、N3，以及普通 App redirect 或 CLI fallback/loopback 的可解释状态。
- 对 Codex/Claude 这类 CLI，若用户完成真实账号授权，则再用 N4/N5 补强完成证据。
- 没有真实账号授权时，可以说明“网页登录环境和回跳机制已验证到账号挑战/回跳入口”，但不能声称“该账号已完成登录”。
- 人工 Google 验证前，Kite 可以把 N1/N3 测到确定性范围，把 N2 测到真实账号挑战页；N4/N5 必须等用户完成账号所有权验证后再由状态拥有者确认。

## 当前映射

- Google OAuth WebView：N0 已复现，证明原问题是 embedded user-agent / WebView 被拒绝。
- Google OAuth via system browser：N1 已验证，第三方 HTTPS redirect 不创建假 AppRedirect session。
- `kite-auth://callback`：N3 已用模拟 redirect 验证 App 回跳和 session 交付。
- Codex/OpenAI CLI：N1/N2/N3 已验证到 OpenAI 登录页、终端保留和设备侧 loopback listener；N4/N5 仍需用户真实账号完成。
- Claude Code CLI：N1/N2/N3 已验证到 Claude 登录页、paste code fallback、终端保留和 listener 存在；N4/N5 仍需用户真实账号完成或 paste code 完成。
