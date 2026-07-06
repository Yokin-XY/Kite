# Kite 浏览器登录当前链路

最后更新：2026-07-04

## 结论

当前 Kite 的浏览器链路是“容器请求打开网页 -> Android 本地服务接收 -> CardRun WebView 显示”。它还不是 OAuth /网页登录回跳协议。

如果 Google 登录页是通过这个链路进入 Kite，当前默认会落在内嵌 `WebView`，这与 Google、RFC 8252 和 AppAuth 一类资料要求的外部 user-agent / Chrome Custom Tabs 路线冲突。

OnePlus 8T `3f8bbaad` 已复现该失败：在 Kite 的 `CardRunActivity` / `KiteWebShell` 中打开 Google OAuth 授权页后，Google 返回 `错误 403: disallowed_useragent`，页面提示请求不符合 Google 的“使用安全浏览器”政策。

证据截图：

- `docs/browser-login/evidence/google-oauth-webview-after-wake.png`

触发 URL：

```text
https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=407408718192.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fdevelopers.google.com%2Foauthplayground&scope=openid%20email&state=kite-webview-test&prompt=consent
```

## 入口和跳转链路

### 1. 容器侧浏览器代理

真实入口：

- `app/src/main/java/com/kite/app/bridge/KiteBrowserProxy.kt`

`KiteBrowserProxyInstaller.environment(...)` 会注入：

- `KITE_OPEN_URL_ENDPOINT=http://127.0.0.1:8791/open-web`
- `KITE_RECIPE_ID`
- `KITE_CARD_INSTANCE_ID`
- `KITE_INSTANCE_ID`
- `KITE_BROWSER_SOURCE`
- `KITE_BROWSER_PROXY=/workspace/.kf/bin/kite-open-url`
- `BROWSER=/workspace/.kf/bin/kite-open-url`

`ensureInstalled(...)` 会把同一代理脚本写成：

- `kite-open-url`
- `xdg-open`
- `sensible-browser`
- `www-browser`
- `x-www-browser`
- `gnome-open`
- `kde-open`
- `kde-open5`
- `exo-open`

所以容器或终端里常见的“打开浏览器”动作会被导向 Android 本地 `/open-web`。

### 2. 环境变量注入点

真实入口：

- `app/src/main/java/com/kite/app/MainActivity.kt`
- `app/src/main/kotlin/com/kite/app/foundation/terminal/TerminalSessionController.kt`
- `app/src/main/java/com/kite/app/KiteTaskContractInitializer.kt`

已确认注入来源：

- `terminal_page`：普通终端会话通过 `BrowserEnvironmentProviderHost` 注入默认浏览器代理环境。
- `terminal_step`：recipe 终端步骤注入代理环境，并绑定当前 CardRun instance。
- `shell_step`：recipe shell 步骤注入代理环境。
- `x11_step`：X11 启动 shell 也注入代理环境，本浏览器线只记录事实，不进入 X11 任务。
- `card_run_blank_terminal`：CardRun 内新建终端注入代理环境。

### 3. Android 本地服务

真实入口：

- `app/src/main/java/com/kite/app/bridge/KiteLocalServer.kt`

`KiteLocalServer` 绑定：

- host：`127.0.0.1`
- 默认端口：`8791`
- endpoint：`/open-web`

`/open-web` 支持 `GET` / `POST`，从这些位置解析 URL：

- JSON body 的 `url`
- query 的 `url`
- 纯文本 body，前提是以 `http://` 或 `https://` 开头

它还会解析：

- `recipeId`
- `cardInstanceId`
- `instanceId`
- `source`

解析成功后调用 `openWeb(openRequest)`，并返回 `accepted=true`。这里的 accepted 只表示 Android 收到打开请求，不表示登录或 OAuth 已完成。

OnePlus 8T 复测结果：

- Host 转发：`tcp:18791 -> tcp:8791`
- Host 请求：`curl.exe --max-time 5 http://127.0.0.1:18791/status`
- Device 请求：`adb -s 3f8bbaad shell curl --max-time 5 http://127.0.0.1:8791/status`
- 结果：均返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`

此前出现过一次 `/status` 超时，但当时手机处于锁屏/AOD 状态；解锁后复测通过，因此目前不把 LocalServer 判为不可用。

### 4. CardRun 路由

真实入口：

- `app/src/main/java/com/kite/app/run/CardRunBrowserRouter.kt`
- `app/src/main/java/com/kite/app/MainActivity.kt`

`CardRunBrowserRouter` 按 `instanceId` 注册 handler。若目标 CardRun 已注册 handler，请求会进入 `openBrowserRequestInCardRun(...)`；如果 instance 尚未就绪，请求会 pending；如果没有 recipe/instance，则创建临时网页 CardRun。

`updateBrowserRequestState(...)` 会把状态写入 `CardRunStore`：

- `surface = CardRunSurface.Web`
- `nextActionUrl = request.url`
- `lastMeaningfulOutput = "Ubuntu 请求打开网页"`

状态拥有者仍是 `CardRunStore`。

### 5. WebView 显示

真实入口：

- `app/src/main/java/com/kite/app/web/KiteWebShell.kt`
- `app/src/main/java/com/kite/app/MainActivity.kt`

当前 `KiteWebShell` 是 Android `WebView`：

- 启用 JavaScript。
- 启用 DOM storage。
- 支持缩放。
- 用 `WebViewClient.shouldOverrideUrlLoading(...)` 判断是否留在 WebView。

`shouldStayInWebView(url)` 当前规则：

- `http://127.0.0.1` 和 `http://localhost` 留在 WebView。
- 来源为 `card_run_surface`、`browser_proxy`、`ubuntu_browser`、`terminal_page`、`terminal_step`、`shell_step` 的 `http/https` URL 留在 WebView。
- 非 `http/https` 使用 `Intent.ACTION_VIEW` 外部打开。

因此：从容器代理进入的 Google / ChatGPT / Claude 等网页登录页面，如果 URL 是普通 `https://...`，当前会留在 Kite 内嵌 WebView。

## 当前缺失

- `AndroidManifest.xml` 里没有用于 OAuth callback 的 `ACTION_VIEW` + `BROWSABLE` intent-filter。
- `app/build.gradle` 当前没有 `androidx.browser`、Custom Tabs 或 AppAuth 依赖；已有的是 `androidx.webkit`。
- 没有 `Chrome Custom Tabs` / `androidx.browser` 的登录 handoff。
- 没有 AppAuth 或等价的 Authorization Code + PKCE 会话状态。
- 没有 provider-agnostic 的 `state`、`code_verifier`、redirect URI 校验。
- 没有把系统浏览器/Custom Tab 的回跳重新绑定到 `CardRunStore` 的协议。
- 没有容器内 CLI localhost callback 的 relay 机制。

## B1 当前判断

用户描述的 Google “不符合要求”已在上述 WebView 链路中复现。当前失败类别判定为：

```text
embedded user-agent / WebView OAuth 被 provider 拒绝
```

不是优先怀疑缺少密钥、cookie、TLS 或简单 UA 参数。正式修复不应在 WebView 里伪造环境，而应把 OAuth /授权页切到外部 user-agent，并设计回跳到对应 CardRun/终端的协议。

仍需在 B4/B5 中补的是真实业务资源、Codex/Claude 等 CLI 场景的 callback 回传验证；B1 的当前链路与 Google WebView 失败类别已经有设备证据。
