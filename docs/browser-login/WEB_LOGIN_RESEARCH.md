# 网页登录回跳调研

最后更新：2026-07-05

## 1. 官方资料结论

### 1.1 Google OAuth 不接受嵌入式 WebView

来源：

- https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/
- https://support.google.com/faqs/answer/12284343

Google 在 2021 年宣布阻止 embedded webview 里的 OAuth 请求。2026-07-05 复核 Google installed apps、embedded webview 公告和 Chrome Auth Tab 文档后，结论仍然一致：原因不是缺少某个密钥，而是内嵌 WebView 可以拦截请求、注入脚本、读取输入、访问 cookie 或修改页面内容。Google 的 remediation 文档明确建议把 WebView 里的 OAuth 请求迁移到 Chrome Custom Tabs 或系统默认处理器。

对 Kite 的含义：

- 通过 `KiteWebShell` 内嵌 WebView 打开 Google 登录页，不是官方推荐路线。
- 修改 User-Agent 或隐藏 WebView 特征不能作为正式修复。
- 正路是 external user-agent：Chrome Custom Tabs 或系统浏览器。

### 1.2 RFC 8252 要求原生应用使用外部 user-agent

来源：

- https://datatracker.ietf.org/doc/html/rfc8252

RFC 8252 是 OAuth 2.0 for Native Apps 的 Best Current Practice。核心要求：

- 原生应用的授权请求应通过外部 user-agent，主要是用户浏览器。
- 原生应用不应使用 embedded user-agent / WebView 做授权请求。
- 授权响应的 redirect URI 必须和发起请求时保存的 redirect URI 精确匹配。
- 原生应用需要防 cross-app request forgery，不能只靠“收到一个 code”就认定成功。

对 Kite 的含义：

- Kite 需要保存一次登录 handoff 的 `state`、redirect URI、发起 CardRun/terminal 信息。
- 回跳后必须校验 `state` 和 redirect URI，再决定是否把结果交给对应实例。

### 1.3 Google installed apps 路线是系统浏览器 + 本地 redirect

来源：

- https://developers.google.com/identity/protocols/oauth2/native-app

Google 的 installed apps 文档说明，安装在设备上的应用不能保密 client secret；流程会生成 code verifier/challenge，通过系统浏览器打开授权页面，然后用本地 redirect URI 接收授权服务器响应，再交换 code。

对 Kite 的含义：

- 如果 Kite 自己作为 OAuth client，需要 Authorization Code + PKCE。
- 如果 Kite 只是帮助容器里的 CLI 登录，重点不是替 CLI 换 token，而是让“浏览器打开”和“callback 回到 CLI/容器”可达。

### 1.4 Chrome Custom Tabs / Auth Tab 是 Android 推荐的认证浏览体验

来源：

- https://developer.chrome.com/docs/android/custom-tabs
- https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- https://github.com/openid/AppAuth-Android

Chrome Custom Tabs 由用户首选浏览器提供能力，共享浏览器状态和能力，避免应用自己管理请求、权限和 cookie。AppAuth-Android 也优先使用支持 Custom Tabs 的浏览器，并支持 custom URI scheme 和 Android App Links。

Chrome Auth Tab 是面向认证流程的 Custom Tab 形态。Chrome 文档说明它可以在认证完成后通过 https 或 custom scheme callback 把结果返回给 App；Chrome 137 起可替换已有 Custom Tabs 认证集成，不支持时自动 fallback 到 Custom Tabs。当前 Kite 已实现 Custom Tabs / 系统浏览器路线，Auth Tab 先记为后续体验增强候选。

对 Kite 的含义：

- 普通网页仍可继续用 Kite WebView。
- OAuth / 登录 handoff 应改为 Custom Tabs 或系统浏览器。
- 如果使用 AppAuth 或类似实现，需要新增 redirect intent-filter 和会话状态。
- 后续如追求更完整的认证体验，可评估 `androidx.browser` Auth Tab API、Custom Tabs 预热和浏览器能力探测。
- 人工账号验证前的测试重点不是绕过验证码/MFA，而是确认已经离开 WebView、redirect 类型匹配、回跳只交给正确 session、授权 code/token 不落盘。

### 1.5 Android App Links 可作为 HTTPS 回跳入口

来源：

- https://developer.android.com/training/app-links/add-applinks
- https://developer.android.com/training/app-links/configure-assetlinks
- https://developer.android.com/training/app-links/test-applinks

Android App Links 要求：

- manifest 中声明 `ACTION_VIEW`、`CATEGORY_BROWSABLE`、`http/https` scheme 和 host。
- 使用 `android:autoVerify="true"`。
- 网站在 `/.well-known/assetlinks.json` 发布 app 包名和签名证书指纹。
- 可以用 Digital Asset Links API 和 `adb shell dumpsys package domain-preferred-apps` 测试验证结果。

对 Kite 的含义：

- 如果 Kite 有可控 HTTPS 域名，App Links 是更标准的 OAuth redirect 路线。
- 如果没有可控域名，短期可能要用 custom scheme 或容器 loopback relay。

### 1.6 OpenAI Codex 和 Claude Code 说明了 CLI 登录的两类难点

来源：

- https://developers.openai.com/codex/auth
- https://code.claude.com/docs/en/authentication
- https://code.claude.com/docs/en/mcp

OpenAI Codex 文档说明，ChatGPT 登录依赖浏览器登录 UI，并可能因为 remote/headless 环境或 localhost callback 被网络配置阻断而失败；推荐 device code。Claude Code 文档说明首次运行会打开浏览器登录，若浏览器无法回到本地 callback，可把代码或完整 callback URL 粘回终端。

对 Kite 的含义：

- ChatGPT/Codex/Claude Code 这类 CLI 登录不只是“网页能打开”。
- 关键是浏览器完成登录后，callback 能回到发起登录的 CLI 或终端。
- Kite 需要支持至少两类回传：
  - 标准 App/Custom Tab redirect 回到 Android，再由 Android 交给 CardRun/终端。
  - CLI 本身监听 localhost callback 时，Android 需要 relay 到容器内对应端口，或引导用户使用 device code / paste callback URL。

## 2. Kite 的路线判断

### 2.0 设备复现结论

OnePlus 8T `3f8bbaad` 已在 Kite `CardRunActivity` / `KiteWebShell` 内嵌 WebView 中复现 Google OAuth 阻断：

- URL：`https://accounts.google.com/o/oauth2/v2/auth?...`
- 页面错误：`错误 403: disallowed_useragent`
- 页面说明：请求不符合 Google 的“使用安全浏览器”政策。
- 截图：`docs/browser-login/evidence/google-oauth-webview-after-wake.png`

这与 Google 官方 remediation、RFC 8252 和 AppAuth 的外部 user-agent 路线一致。当前主因按 `embedded user-agent / WebView OAuth 被 provider 拒绝` 处理，而不是密钥缺失、cookie 缺失或单纯 UA 参数问题。

### 正路 A：普通网页继续 WebView，OAuth 登录切到外部 user-agent

适用：

- Web UI、localhost 页面、资源管理界面。
- 不涉及第三方 OAuth 授权页的普通网页。

做法：

- 保留 `KiteWebShell`。
- 增加登录 URL 分类和“在浏览器中登录” handoff。
- 对明确 OAuth / auth provider 页面使用 Custom Tabs 或系统浏览器。

### 正路 B：建立 provider-agnostic callback/session 协议

适用：

- Kite 自己接 OAuth redirect。
- 从 Custom Tab 回到对应 CardRun 或终端。

做法：

- 新增一次性 browser auth session：`sessionId`、`state`、`recipeId`、`instanceId`、`source`、`redirectUri`、`createdAt`。
- Manifest 增加回跳 Activity/intent-filter。
- 回跳校验 `state` 和 redirect URI。
- 校验后只把结果交给状态拥有者，不在页面本地复制登录事实。

### 正路 C：为容器 CLI 的 localhost callback 做 relay 或显式 fallback

适用：

- Codex CLI、Claude Code、Google Antigravity CLI、Kimi Code 等终端工具。

可能做法：

- 检测授权 URL 的 `redirect_uri=http://127.0.0.1:<port>/...` 或 `localhost:<port>`。
- Custom Tab 打开授权 URL。
- Android 本地接收外部浏览器返回后，把完整 callback URL 或 code/state 转发到容器内 CLI 监听端口。
- 如果无法 relay，显示可解释 fallback：device code、复制完整 callback URL、粘贴回终端。

## 3. 反路线

- 不把 Google 单个错误域名写成特判。
- 不靠 User-Agent 伪装、隐藏 WebView、自动化浏览器指纹绕过作为正式路线。
- 不从 WebView 或日志里抓取 token。
- 不把登录成功状态写进页面本地变量；必须由对应 CLI、OAuth session 或 CardRun 状态拥有者确认。
