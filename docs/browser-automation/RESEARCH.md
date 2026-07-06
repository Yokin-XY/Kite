# 浏览器自动化资料调研

最后更新：2026-07-06

## 1. 结论先行

Kite 的“自动浏览器”当前已归位为 WebView 方案：

1. A0-A32 已完成：App 内受控 WebView 自动化。
   - 适合本地端口 Web UI、资源页面、普通网页和 Kite 自己的运行面。
   - 已具备 observe、action、run/open-run、截图、网络/console、iframe、open shadow、actionability 和恢复查询。
   - 设置页第二模式 `automation_browser` 当前就封口为这个 WebView 自动浏览器方案。

2. A34 后续要验证的是 WebView 自身持久化。
   - 目标是“用户已经登录过普通网站后，下次打开是否仍保持登录态”。
   - 先验证 cookie、localStorage、IndexedDB，再做普通网站人工登录态复用验证。
   - Google / ChatGPT 等强认证挑战不作为第二模式当前验收。

3. 完整内置浏览器是下一阶段。
   - 候选包括 GeckoView + WebExtension、Chromium 级嵌入或其它真正浏览器内核。
   - 详见 `docs/browser-automation/REAL_BROWSER_RESEARCH.md`，该文件是后续参考，不是本次 WebView 封口完成声明。

旧的三层路线仍作为背景参考：

1. 第一阶段：App 内受控 WebView 自动化。
   - 适合本地端口 Web UI、资源页面、普通网页。
   - 能通过 `evaluateJavascript`、注入脚本、截图、DOM 摘要、动作队列和运行实例状态实现。
   - 不需要把 OAuth/SSO 授权页拉回 WebView。

2. 第二阶段：CDP/DevTools 兼容层。
   - 适合 debug 构建、开发设备、真机诊断和与 Playwright/Appium 思路对齐。
   - 必须把 WebView debugging 限制在 debuggable 或显式实验环境。

3. 第三阶段：外部自动化兼容。
   - 可研究 Playwright Android、Appium Hybrid、WebDriver/BiDi、Chrome DevTools Protocol。
   - 这些更适合测试/控制协议，不一定适合直接嵌入手机 App 作为用户功能。

## 2. 关键来源

### Chrome DevTools：Remote debugging WebViews

- 来源：https://developer.chrome.com/docs/devtools/remote-debugging/webviews
- 要点：Android 4.4+ 可以用 Chrome DevTools 调试 App 内 WebView；需要 App 调用 `WebView.setWebContentsDebuggingEnabled(true)`；`chrome://inspect` 能看到 debug-enabled WebViews。
- 对 Kite 的意义：这证明 WebView 可以暴露给 DevTools/CDP 类控制面，但它是调试能力，不应默认生产开启。

### Android Developers：Debug web apps

- 来源：https://developer.android.com/develop/ui/views/layout/webapps/debugging
- 要点：Android 官方把 WebView 调试、console logging、本地 server、WebView DevTools app 作为调试 WebView 的手段。
- 对 Kite 的意义：Kite 的本地端口 Web UI 和 WebView 自动化可以复用这些观测能力，先做 debug/实验闭环。

### Android WebView API

- 来源：https://developer.android.com/reference/android/webkit/WebView
- 要点：`setWebContentsDebuggingEnabled` 会让 WebView 的 HTML/CSS/JS 可被 adb/DevTools 检查和修改；官方文档明确指出这有安全风险，生产环境不应随意开启。
- 对 Kite 的意义：自动浏览器不能简单等同于“永久打开 WebView debugging”。生产能力应优先走 App 内受控 action 协议，CDP 只作为显式调试/实验。

### Android CookieManager / WebStorage / WebView data directory

- 来源：https://developer.android.com/reference/android/webkit/CookieManager、https://developer.android.com/reference/android/webkit/WebStorage、https://developer.android.com/reference/android/webkit/WebView
- 要点：CookieManager 管理 App 的 WebView 实例使用的 cookies，并提供 `flush()` 写入持久存储；WebStorage 管理 WebView 的 JavaScript storage API；`setDataDirectorySuffix` 定义当前进程 WebView 数据目录，多进程 WebView 数据目录不能直接共享。
- 对 Kite 的意义：WebView 持久化是可验证路线，但它是 App 自己的 WebView profile，不等同于系统 Chrome/Custom Tabs 的登录态。

### Chrome Auth Tab / Custom Tabs

- 来源：https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab、https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs
- 要点：Auth Tab 是面向 Android App 的专用认证 Custom Tab，完成后通过 https 或 custom scheme 回调 App；Custom Tabs 提供 App 内定制浏览器体验，但承载者仍是浏览器。
- 对 Kite 的意义：它是强认证回跳桥，不是 Kite 可注入、可观察 DOM、可执行 action 的自动浏览器 surface。

### GeckoView / WebExtensions

- 来源：https://mozilla.github.io/geckoview/、https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html
- 要点：GeckoView 可把 Gecko 浏览器引擎嵌入 Android App；GeckoView 支持通过 WebExtensions 与网页内容双向通信。
- 对 Kite 的意义：这是“内嵌真正浏览器”的首个候选。元素化可以走扩展/content script 模型，但强认证接受度、APK 体积和 profile 持久化必须真机验证。

### ChromeDriver Android / Appium mobile web

- 来源：https://developer.chrome.com/docs/chromedriver/get-started/android、https://appium.github.io/appium.io/docs/en/writing-running-appium/web/mobile-web/
- 要点：ChromeDriver 支持 Android Chrome 和启用 WebView debugging 的 WebView；Appium 支持自动化真实或模拟 Android 设备上的 Chrome，但需要 Chrome、Chromedriver 和对应设备/版本配置。
- 对 Kite 的意义：这是“真实手机 Chrome + 真实 profile”的最高保真外部控制路线，适合开发者 lane 和长时间设备托管，不等同于 App 内嵌浏览器控件。

### Chrome DevTools Protocol

- 来源：https://chromedevtools.github.io/devtools-protocol/
- 要点：CDP 用于 instrument、inspect、debug、profile Chromium/Blink 浏览器；命令和事件按 DOM、Network、Runtime、Page 等 domain 组织。
- 对 Kite 的意义：Kite 的内部协议可以借鉴 CDP 的 domain 思路，但不必第一阶段完整实现 CDP。

### Playwright Android

- 来源：https://playwright.dev/docs/api/class-android
- 要点：Playwright 对 Android 自动化是 experimental，覆盖 Chrome for Android 和 Android WebView；要求 ADB、Chrome 87+，并有已知限制，例如需要设备保持唤醒、未完整跑全量测试。
- 对 Kite 的意义：可作为外部测试工具和长期兼容方向，不适合第一阶段直接嵌入 App 内核。

### W3C WebDriver

- 来源：https://www.w3.org/TR/webdriver2/
- 要点：WebDriver 是远程控制 user agent 的平台无关协议，用于让进程外程序控制浏览器行为。
- 对 Kite 的意义：如果未来要让外部 AI/脚本像 Selenium 那样控制 Kite 自动浏览器，可以借鉴 session、command、element reference 和 error model。

### WebDriver BiDi

- 来源：https://github.com/w3c/webdriver-bidi 与 https://developer.chrome.com/blog/webdriver-bidi-2023
- 要点：WebDriver BiDi 是双向浏览器自动化协议，目标是结合 WebDriver 的标准化和 CDP 的实时事件能力。
- 对 Kite 的意义：Kite 后续 action/result/event 协议应从一开始保留双向事件流，而不是只做一次性 HTTP 命令。

### Selenium WebDriver

- 来源：https://www.selenium.dev/documentation/webdriver/
- 要点：Selenium WebDriver 驱动浏览器，模拟用户在本地或远程机器上的操作，并且是 W3C Recommendation。
- 对 Kite 的意义：Selenium 证明“浏览器自动化”不是单纯 JS 注入，还要有用户动作语义、等待、错误和跨浏览器抽象。

### Appium Hybrid Apps

- 来源：https://appium.github.io/appium.io/docs/en/writing-running-appium/web/hybrid/
- 要点：Appium 通过 Chromedriver 支持自动化 Chrome-backed Android WebViews；App 需要启用 WebView debugging。
- 对 Kite 的意义：如果要和移动测试生态兼容，WebView debugging/CDP 是通路；但它依赖外部 server 和驱动版本匹配，不适合作为第一阶段用户功能唯一内核。

### Android Accessibility Service

- 来源：https://developer.android.com/guide/topics/ui/accessibility/service
- 要点：Accessibility Service 是辅助工具，能在后台观察 UI 事件并代表用户交互；Android 官方提醒它是特殊工具，不是普通可访问性实现方式。
- 对 Kite 的意义：它可以研究为“跨 App 可见层兜底”，但不该作为 Kite 自动浏览器默认路线，尤其不用于绕过登录限制。

### OWASP MAS：WebView debugging production security

- 来源：https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0008/
- 要点：生产构建应关闭 WebView debugging；如需调试，应只在 debuggable 状态下启用。
- 对 Kite 的意义：自动浏览器能力必须有 debug/实验开关和敏感边界，不能为了自动化长期打开高权限调试口。

## 3. 路线比较

| 路线 | 优点 | 风险 | Kite 判断 |
| --- | --- | --- | --- |
| 受控 WebView + action 协议 | 与 App 状态绑定最紧，成本低，适合本地 Web UI | 需要自己做 selector、wait、报告 | 第一阶段主线 |
| WebView 持久化 | 保留现有能力，普通站点可复用 WebView 自己的 cookie/storage | 不共享系统 Chrome/Custom Tabs cookie；真实网站登录态尚未验收 | A34 验证 |
| Auth Tab / Custom Tabs | 官方强认证体验，复用系统浏览器能力 | 不能 DOM 自动化，不能当自动浏览器 surface | 认证桥 |
| GeckoView + WebExtension | App 内嵌真浏览器引擎，扩展模型适合元素化 | 不共享 Chrome profile；强认证接受度、体积和兼容要实测 | 内嵌真浏览器首个原型 |
| Android Chrome + ChromeDriver/Appium | 真实 Chrome/profile，强认证保真最高，生态成熟 | 依赖 ADB、host server、版本匹配和前台 Chrome | 外部真 Chrome lane |
| WebView debugging + CDP | DOM/Network/Runtime 能力强，生态成熟 | 生产安全风险，debug 依赖 ADB/DevTools | 第二阶段实验/调试 |
| Playwright Android | AI/测试生态熟悉，支持 Chrome/WebView | experimental，设备要求和限制较多 | 外部测试和兼容参考 |
| Appium Hybrid | 移动测试成熟，WebView context 模型清楚 | 重依赖 server/Chromedriver/version | 外部测试参考 |
| WebDriver/BiDi | 标准化协议，适合长期开放接口 | 实现完整协议成本高 | 协议设计参考 |
| Accessibility Service | 可跨 App 操作系统浏览器 | 授权重、风险大、不是普通浏览器自动化 | 后置兜底研究 |
| UA/指纹伪装 | 可能绕过个别检测 | 高风险、易碎、违反默认官方路线 | 反路线，不做默认 |

## 4. 推荐第一阶段实现

1. 新建 automation session model：`sessionId`、`instanceId`、`url`、`mode`、`status`、`lastAction`、`lastError`。
2. 在 `automation_browser` 模式下，打开本地 URL 时创建受控 WebView surface。
3. 暴露最小动作：
   - `open(url)`
   - `snapshot()`
   - `find(selector/text)`
   - `click(selector/text)`，包含受控 pointer/mouse down/up prelude
   - `doubleClick(selector/text)`，包含两轮受控 pointer/mouse/click activation 和 `dblclick`
   - `hover(selector/text)`，包含受控 pointer/mouse over/enter/move prelude
   - `navigate(back/forward/reload)`，只控制当前 WebView session 历史，不打开任意 URL
   - `type(selector/text, value)`
   - `clear(target)`
   - `press(target, key)`
   - `select(target, value/text/index)`
   - `check(target, true/false/toggle)`
   - `scroll(direction)`
   - `waitFor(selector/text/url/state)`
   - observe suggested target 带 `index`，用于同名同 role 元素去歧义
   - disabled / readonly actionability 守卫，避免对用户不可操作控件误报成功
   - 同源 iframe 内元素纳入 selector / snapshot；跨源或 sandbox iframe 只标记边界
   - open shadow root 内元素纳入 selector / snapshot；closed shadow root 只保留 host 边界
   - `evaluate(script)`，默认只对本地/可信页面开放。
   - `sessions()` 只读列出当前自动浏览器 session，支持跨回合恢复和多页面选择。
4. 每个动作写回结果：成功/失败、耗时、页面 URL、可见文本摘要、截图路径或 hash。
5. OAuth/SSO URL 仍调用现有 `BrowserHandoffPolicy`，切外部系统浏览器。
