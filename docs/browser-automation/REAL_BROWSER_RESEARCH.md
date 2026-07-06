# 完整内置浏览器后续调研

最后更新：2026-07-06

## 1. 本轮问题

A0-A32 已经证明 Kite 可以把 App 内 WebView 做成可观察、可点击、可输入、可截图、可恢复的自动浏览器底座。A33 进一步把设置页第二模式归位为 WebView 自动浏览器，不把强认证作为当前验收。

本文件只作为 A33 封口之后的“完整内置浏览器”参考，不代表当前第二模式必须立刻换引擎。

后续完整浏览器阶段主要比较两类方向：

1. 继续保留 WebView 自动浏览器，同时补齐 WebView 自身持久化验证。
2. 引入或控制真正浏览器，让完整浏览器流程逐步进入 Kite 软件内部。

## 2. 资料来源

- Chrome Auth Tab：https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
- Android Custom Tabs：https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs
- Android WebView API：https://developer.android.com/reference/android/webkit/WebView
- Android CookieManager：https://developer.android.com/reference/android/webkit/CookieManager
- Android WebStorage：https://developer.android.com/reference/android/webkit/WebStorage
- Chromium Android WebView architecture：https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/architecture.md
- GeckoView overview：https://mozilla.github.io/geckoview/
- GeckoView WebExtensions：https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html
- ChromeDriver Android：https://developer.chrome.com/docs/chromedriver/get-started/android
- Appium mobile web：https://appium.github.io/appium.io/docs/en/writing-running-appium/web/mobile-web/

## 3. 核心结论

### 3.1 Auth Tab / Custom Tabs

这是官方推荐的认证承载方式之一，适合 OAuth/OIDC、Google、OpenAI、Anthropic 这类认证和回跳。它用系统浏览器能力承载登录，完成后把结果 URL 回传给 App。

它不是自动浏览器面。App 不能把 Custom Tab 当成自己的 DOM 容器来观察、点击、注入脚本或读取 cookie。它适合作为“强认证桥”，不适合作为“元素化浏览器”。

### 3.2 WebView 持久化

WebView 有自己的 CookieManager、WebStorage 和应用数据目录，可以承担普通网页、本地端口 Web UI、资源页、后台控制台等自动化场景。只要站点允许 WebView/嵌入式浏览器登录，WebView 自己的 cookie、localStorage、IndexedDB 等状态就能作为持久化方向验证。

限制也很明确：WebView 的数据目录属于 App 自身，不等于 Chrome/Custom Tabs 的用户 profile。系统浏览器完成认证后，不能假定它的 cookie 会自然进入 WebView。可行边界是 WebView 自己保存自己登录过的网站状态，或者服务端/OAuth/OIDC 流程通过 redirect/code/session exchange 回到 App，而不是把系统浏览器 cookie 原文搬进 WebView。

这条路线的第一刀应该做“持久化证明”，而不是立刻碰真实账号：本地测试页设置 cookie、localStorage、IndexedDB，重启 App 或重开 session 后用 observe/action 验证状态仍在；再用普通测试站点验证登录态是否能在 WebView 自己的 profile 内复用。

### 3.3 GeckoView + WebExtension

GeckoView 是 Mozilla 的 Android 浏览器引擎嵌入方案，更接近“在 App 内嵌一个真正浏览器引擎”。GeckoView 支持 WebExtensions，原生侧可以通过 native messaging 与扩展通信；扩展再用 content script 观察 DOM、执行点击、输入和等待。

这条路线最像用户说的“直接拿一个真正浏览器嵌入进去”。优点是元素化可以走浏览器扩展模型，不必完全依赖 WebView JS 注入；浏览器 profile 也由这个内嵌浏览器持有。限制是它不共享用户 Chrome 登录态，也不等同于系统 Chrome；Google/OpenAI/Anthropic 等强认证是否接受 GeckoView 仍必须实测。

建议作为第一个“内嵌真浏览器”原型：先加载本地测试页，跑 WebExtension observe/click/type；再测试普通网页登录持久化；最后由人工测试强认证。

### 3.4 Android Chrome + ChromeDriver/Appium/CDP

这是“控制真实手机 Chrome”的路线。ChromeDriver 官方支持 Android Chrome 和启用 WebView debugging 的 WebView；Appium 的 mobile web 路线也通过 Chromedriver 自动化 Android Chrome。

优点是认证最接近真实用户浏览器：用的就是系统 Chrome、真实浏览器 profile、真实浏览器能力。缺点是它通常需要 ADB、host-side server、Chromedriver 版本匹配、前台 Chrome 页面和设备授权；它不像 App 内一个嵌入式控件，不适合直接包装成普通用户无感功能。

这条路线适合做“开发者/设备托管/长期自动化测试 lane”：Codex 或外部 agent 在电脑侧通过 Appium/ChromeDriver 控制 OnePlus 8T 的 Chrome，验证强认证和真实站点元素化；Kite App 内则作为任务发起、状态展示或桥接端。

## 4. 路线矩阵

| 路线 | 强认证成功概率 | 元素化能力 | 是否内嵌 App | 是否共享系统浏览器状态 | 主要限制 | Kite 判断 |
| --- | --- | --- | --- | --- | --- | --- |
| WebView 持久化 + 系统认证桥 | 中，取决于服务是否允许回跳换会话 | 已有 A0-A32 能力 | 是 | 否 | WebView 常被强认证限制；不能偷搬系统 cookie | 保留为低成本主线和普通站点路线 |
| Auth Tab / Custom Tabs | 高，符合官方移动认证思路 | 低，不能 DOM 自动化 | 半内嵌体验，不是 App DOM | 是，借系统浏览器 | 只适合登录回跳 | 认证桥，不当自动浏览器 |
| GeckoView + WebExtension | 待实测，可能高于 WebView 但低于 Chrome | 高，可走扩展/content script | 是 | 否 | 依赖 GeckoView 体积、兼容和强认证接受度 | 内嵌真浏览器首个原型 |
| Android Chrome + ChromeDriver/Appium | 最高，最接近真实 Chrome | 高，WebDriver/CDP 生态成熟 | 否 | 是 | 依赖 ADB、驱动、前台和版本匹配 | 外部真 Chrome lane |
| 系统 Accessibility 控制 Chrome | 中到高 | 低到中，偏坐标/可访问节点 | 否 | 是 | 授权重、脆弱、难以后台化 | 只作兜底研究 |
| UA/指纹伪装 | 不稳定 | 不解决通用元素化 | 是/否 | 否 | 易碎且偏绕策略 | 反路线 |

## 5. 下一步原型顺序

1. P1：WebView 持久化证明。
   - 增加无账号本地测试页，写入 cookie、localStorage、IndexedDB。
   - 重启 App 或关闭再打开 WebView session。
   - 用 `/browser-automation/observe` 和受控 `evaluate` 验证同源持久状态。
   - 验证 `CookieManager.flush()`、WebStorage 状态和多进程 WebView 数据目录约束。

2. P2：GeckoView + WebExtension 最小嵌入。
   - 新增实验开关，不替换现有 `automation_browser` 默认实现。
   - 用 GeckoView 打开本地测试页。
   - 用 WebExtension content script 做 observe/click/type/waitFor 的最小闭环。
   - 记录 APK 体积、初始化耗时、崩溃日志、cookie/storage profile 是否持久。

3. P3：Android Chrome + ChromeDriver/Appium 外部控制 lane。
   - 在桌面侧建最小脚本连接 OnePlus 8T 的 Chrome。
   - 打开本地测试页，完成 find/click/type/screenshot。
   - 再人工登录强认证站点，验证登录态是否能被后续自动化复用。
   - Kite App 先只记录外部 lane 的状态和报告，不把它伪装成 App 内嵌浏览器。

## 6. 当前代码入口判断

当前代码不适合马上新增第三个用户可见浏览器模式。

- `BrowserRuntimeMode.kt` 只有 `webview_system_auth` 和 `automation_browser`，设置页直接枚举这两个值。
- `KiteWebShell` 是现有 WebView 承载点，负责 `BrowserHandoffPolicy`、外部浏览器回跳、console/network 记录和 `BrowserAutomationController`。
- `MainActivity.showCardRunWebView(...)` 在 `automation_browser` 模式下给当前 WebView 开启 automation。
- `KiteLocalServer` 的 `/browser-automation/action`、`/run`、`/open-run` 复用同一套模式门禁和 controller registry。
- `app/build.gradle` 目前已有 `androidx.browser` 和 `androidx.webkit`，没有 GeckoView 依赖。

因此真浏览器原型应先作为 `automation_browser` 内部 engine/lane：

- `webview` engine：继续使用 A0-A32 的默认能力。
- `geckoview` engine：实验性 App 内嵌真浏览器，使用 WebExtension/content script 做元素化。
- `external_chrome` lane：桌面侧通过 ChromeDriver/Appium 控制 OnePlus 8T 的 Chrome，Kite 只承接状态和报告。

## 7. 红线

- 不保存账号、密码、cookie、token、authorization code 原文到文档、日志或状态文件。
- 不把系统 Chrome/Custom Tabs 的 cookie 原文导入 WebView。
- 不把 Auth Tab 当成可自动化 DOM surface。
- 不用 UA/指纹伪装当默认路线。
- 不用某个站点的按钮路径或登录页特判冒充通用自动浏览器能力。
- 强认证最后必须由人工真账号测试确认；自动化阶段只证明环境、profile、回跳和元素化链路。
