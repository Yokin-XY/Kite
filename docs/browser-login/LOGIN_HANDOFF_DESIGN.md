# Kite 登录回跳协议设计

最后更新：2026-07-04

## 1. 目标

为 Kite 建立通用的网页登录 handoff 协议：

```text
Kite / 容器 / 终端发起打开登录页
-> 判断是否需要安全外部浏览器
-> 用 Chrome Custom Tabs 或系统浏览器打开
-> 浏览器完成授权后回到 Android
-> Android 校验 session / state / redirect
-> 把结果交回对应 CardRun、终端或 CLI fallback
```

本设计解决的是“登录环境与回跳协议”，不是 Google 单点报错。Google、OpenAI/ChatGPT、Claude Code 和其他站点都走同一套分类、handoff、回跳和失败状态机制。

## 2. 边界

继续保留：

- `KiteWebShell` 内嵌 WebView：普通网页、localhost Web UI、资源页面、非 OAuth 的站内页面仍可使用。
- `KiteLocalServer`：继续作为容器向 Android 请求打开网页的入口。
- `CardRunStore`：继续作为 CardRun 显示面、`nextActionUrl` 和运行实例状态的拥有者。

新增能力：

- OAuth /授权页从 WebView 分流到外部 user-agent。
- 一次性 browser auth session，绑定发起来源、目标实例和回跳校验字段。
- Android 可接收的 redirect 入口。
- CLI localhost callback 的 relay 或显式 fallback。

不做：

- 不靠 User-Agent 伪装、无指纹浏览器或隐藏 WebView 特征作为主路径。
- 不从 WebView、logcat 或页面脚本里抓取 token。
- 不在页面本地复制“已登录”事实。
- 不为 Google、OpenAI、Claude 单独写硬编码成功分支。

## 3. URL 分类

新增 `BrowserHandoffPolicy` 或等价组件，输入为：

- `url`
- `source`
- `recipeId`
- `instanceId`
- 当前显示面类型

输出为：

```text
StayInWebView
OpenExternalBrowser
StartAuthHandoff
StartCliCallbackHandoff
ShowUnsupportedFallback
```

第一版分类规则：

- `localhost`、`127.0.0.1`、局域网调试页、资源 Web UI：默认 `StayInWebView`。
- 明确的 OAuth 授权端点、登录授权路径、包含 `client_id` / `redirect_uri` / `response_type=code` / `scope` / `state` 等授权参数的页面：默认 `StartAuthHandoff`。
- 授权 URL 的 `redirect_uri` 指向 `http://127.0.0.1:<port>` 或 `http://localhost:<port>`：默认 `StartCliCallbackHandoff`。
- 普通外部网页：可保留现有 WebView 行为，或由用户选择“在浏览器打开”。

分类规则必须可单测。域名可以作为风险提示或兼容矩阵的一部分，但不能成为唯一判断依据。

## 4. 外部浏览器路线

默认使用 Chrome Custom Tabs：

- 新增 `androidx.browser:browser` 依赖。
- 通过 `CustomTabsIntent` 打开登录页。
- 如果设备没有支持 Custom Tabs 的浏览器，降级为 `Intent.ACTION_VIEW` 系统浏览器。

打开前写入一次性 session：

```text
sessionId
kind = app_redirect | cli_loopback | external_only
recipeId
instanceId
source
originalUrl
redirectUri
state
createdAt
expiresAt
status = pending | returned | delivered | failed | expired
```

session 存储建议：

- 第一版使用 Android 侧轻量持久化，例如 SharedPreferences 或现有状态存储封装，避免进程重启后丢失回跳上下文。
- CardRun 相关显示状态仍写入 `CardRunStore`，例如“正在等待浏览器登录返回”。

## 5. 回跳入口

优先级：

1. 有可控 HTTPS 域名时，使用 Android App Links。
2. 没有域名时，第一版使用 custom scheme 作为可验证本地回跳入口。
3. 对只能使用 CLI localhost redirect 的工具，走 CLI relay 或 fallback，不冒充 App 自己完成 OAuth。

Manifest 需要新增一个回跳入口 Activity 或复用已有入口 Activity：

```text
ACTION_VIEW
CATEGORY_DEFAULT
CATEGORY_BROWSABLE
scheme/host/path
```

回跳处理流程：

```text
收到 redirect Intent
-> 解析 code / state / error / redirect URI
-> 查找 pending session
-> 校验 state 和 redirect URI
-> 成功：交给对应状态拥有者
-> 失败：写入可解释失败状态
```

校验失败时不能静默打开首页，也不能把按钮恢复成初始状态。

## 6. PKCE 和 token 边界

如果 Kite 自己作为 OAuth client：

- 使用 Authorization Code + PKCE。
- `code_verifier` 只保存在 Android 侧 session 中。
- 回跳后校验 `state`、redirect URI 和 session 过期时间。
- token exchange 只能在明确拥有 client 配置和回调协议时实现。

如果 Kite 只是帮助容器里的 CLI 登录：

- Kite 不替 CLI 交换 token。
- Kite 不解析或保存 provider token。
- Kite 只负责打开合规浏览器、把 callback URL/code 安全交回发起的 CLI，或提示用户使用官方 fallback。

## 7. CLI localhost callback

问题：

```text
容器内 CLI 监听 127.0.0.1:<port>
外部浏览器回跳 http://127.0.0.1:<port>/callback
这个 127.0.0.1 是 Android 设备，不是容器
```

第一版方案分两档：

- 可 relay：Android 捕获 callback 后，将完整 callback URL 转发到容器内对应端口。
- 不可 relay：展示明确 fallback，例如 device code、复制完整 callback URL、粘回终端。

实现时要从授权 URL 中解析 CLI 的 `redirect_uri`：

- host 必须是 `127.0.0.1` 或 `localhost`。
- port 必须来自当前 pending session，不接受任意外部页面随意指定。
- relay 只允许发回当前发起实例关联的容器，不做全局开放代理。

如果某个 CLI 的官方流程支持 device code，优先把 fallback 文案指向 device code。Codex 和 Claude Code 都存在这类官方替代路径。

## 8. 用户可见状态

发起登录后：

- CardRun / 终端对应位置立即显示“正在等待浏览器登录返回”。
- 按钮进入处理中，避免重复点击。
- 后台结果分为继续等待、成功交付、失败、超时。

失败状态示例：

- `登录页需要安全浏览器，已为你打开系统浏览器`
- `浏览器已返回，但 state 校验失败`
- `浏览器回跳到了 Android 本机 localhost，尚未能交回容器 CLI`
- `等待登录返回超时，可重试或使用 device code`

状态来源：

- CardRun 显示状态由 `CardRunStore` 维护。
- Browser auth session 维护 handoff 的一次性安全状态。
- CLI 最终是否登录成功由 CLI 自己输出或对应运行实例确认。

## 9. B4 最小实现切片

建议按以下顺序实现：

1. 新增 URL 分类器和单测。
2. 新增 browser auth session 存储和过期清理。
3. 新增 Custom Tabs opener，保留 `Intent.ACTION_VIEW` fallback。
4. 给 WebView 中需要授权的 URL 增加 handoff 分流，不影响普通 WebView。
5. 新增 manifest redirect 入口和回跳解析。
6. 把回跳结果绑定回 `CardRunStore` / 发起实例。
7. 为 CLI localhost callback 增加“可 relay 时 relay，不可 relay 时 fallback”的最小机制。
8. OnePlus 8T 上构建、安装、截图、logcat 验证。

## 10. 验收

- Google OAuth 授权页不再默认停留在 Kite WebView 中触发 `disallowed_useragent`。
- 普通 localhost Web UI 仍在 Kite WebView 中打开。
- 外部浏览器返回后能定位到发起的 `instanceId`。
- `state` 或 redirect URI 不匹配时显示失败，不交付结果。
- CLI localhost callback 有明确 relay 或 fallback，不静默丢失。
- 有 URL 分类、redirect 解析和 session 校验相关测试。
