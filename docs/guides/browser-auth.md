# WebView 与系统浏览器认证

## 默认模式

Kite 的稳定网页方案是：

```text
本地页面和普通网页 -> Android WebView
OAuth / SSO 授权       -> 系统浏览器
认证回调               -> Kite 认证桥 -> 原始 CLI 或运行实例
```

系统浏览器负责账号输入、Cookie、风控、人机验证和多因素验证。Kite 不伪造浏览器指纹，也不接管第三方账号密码。

## 请求分类

Browser Handoff 根据 URL 和来源分类：

- `http://localhost`、`http://127.*` 和 IPv6 loopback 页面留在 WebView。
- 普通卡片 Web 页面默认留在 WebView。
- 带有标准 OAuth 参数的授权请求交给系统浏览器。
- 非 HTTP/HTTPS scheme 交给 Android 系统处理。

授权请求至少需要能识别 `client_id`、`redirect_uri` 和 `response_type`。Provider 自己仍负责校验 client、state、PKCE、scope 和账号条件。

## 两种回调

### 应用回调

应用 scheme：

```text
kite-auth://callback
```

Android 把该 Intent 交给 Kite。Kite 使用认证会话中的 request/state 身份定位原始运行实例，再交付回调。

### CLI loopback 回调

Codex 等 CLI 通常在 Linux 环境中监听类似地址：

```text
http://localhost:<port>/<path>
```

Android 系统浏览器访问的是 Android 自己的 loopback，不能天然命中 PRoot 内的监听器。Kite 会为本次认证建立临时 relay，把浏览器回调转发到原 CLI 监听地址，并把 CLI 的 HTTP 响应原样返回浏览器。

认证桥只解决 Android 与 PRoot 的 loopback 地址空间差异。OAuth code 校验、token 交换和凭据存储仍由发起认证的 CLI 负责。

## 成功链路

```text
CLI 输出授权 URL
-> Kite 识别并建立认证会话
-> 系统浏览器完成登录
-> 浏览器访问 redirect_uri
-> Kite 接收并转发回调
-> CLI 返回 HTTP 结果
-> Kite 把结果回应系统浏览器
-> 运行实例恢复到对应终端
```

## 常见失败

- `invalid_client`：Provider 不接受请求中的 client 配置，Kite 不能替 Provider 修正。
- `unsupported_country_region_territory`：网络出口地区不受 Provider 支持。
- 浏览器一直转圈：检查 CLI loopback 监听是否仍存在、redirect port/path 是否一致，以及 Kite 是否仍持有对应认证会话。
- 登录成功但终端未生效：检查回调是否送到原始 CLI，而不是只停留在 Android loopback。
- 覆盖安装后异常、卸载重装正常：检查旧认证会话持久化与当前运行实例是否一致。

诊断日志不得输出真实 code、token 或完整 state；只记录参数是否存在及去敏后的请求身份。

## 实验自动化模式

设置中的“自动浏览器”是基于 WebView 的实验入口，用于元素观察和自动动作研究。它不改变认证边界：账号授权仍交给系统浏览器。该模式不属于当前正式版本的稳定承诺。
