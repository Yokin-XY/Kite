# Kite 浏览器登录回跳执行手册

> 本文件是浏览器登录任务线的唯一事实来源。后续浏览器分支会话开始前必须先读本文件、`PROGRESS.md` 和 `DECISIONS.md`。

## 0. 开机自检

每次继续本任务前必须先做：

1. 读 `docs/browser-login/PLAYBOOK.md` 全文。
2. 读 `docs/browser-login/PROGRESS.md`。
3. 读 `docs/browser-login/DECISIONS.md`。
4. 检查当前目录是否为浏览器线物理副本 `D:\xm\Kite-browser-login`。
5. 检查 ADB 目标是否为 OnePlus 8T `3f8bbaad`。

每开始一个任务前，必须在 `PROGRESS.md` 写三问自检：

1. 目标是什么。
2. 完成标准是什么。
3. 前置任务是否完成。

## 1. 北极星目标

解决 Kite 内置浏览器触发网页登录后，认证流程无法稳定回到 App 或被 Google、ChatGPT、Claude Code 等站点判定为不合规浏览环境的问题。

本任务先走官方推荐路径：系统浏览器、Chrome Custom Tabs、App Links、OAuth 2.0 for Native Apps、PKCE、AppAuth 或等价标准能力。无指纹浏览器、UA/环境伪装、自动化浏览器特征等内容只能作为兼容性研究和风险记录，不作为默认实现路线。

## 2. 工作线绑定

- 物理目录：`D:\xm\Kite-browser-login`
- 建议分支：`codex/browser-login-return`
- 绑定设备：OnePlus 8T
- ADB serial：`3f8bbaad`
- 本机调试端口：`18791 -> 8791`
- 主要真实入口候选：`KiteBrowserProxy`、`KiteLocalServer`、浏览器相关 Activity/bridge、登录回跳 Intent 处理。

## 3. 红线

- 不把 Google 当前报错当成单一 provider 特判处理。
- 不靠改 User-Agent、隐藏 WebView 特征或硬编码成功结果作为正式修复。
- 不在未确认回跳协议前保存、转发或打印敏感 token。
- 不新增平行登录状态来源；登录事实必须由已有认证/会话拥有者确认。
- 不用无设备 serial 的 ADB 命令。
- 不与 X11 线共用同一个物理目录、同一个 host 转发端口或同一个截图/日志输出路径。

## 4. 任务梯队

### B0 建立浏览器任务基线

- 问题证据：用户要求浏览器登录线独立于 X11 线，绑定 OnePlus 8T，并先明确目录、设备和方向。
- 解法：建立浏览器登录三件套和双线隔离说明。
- 验收标准：
  - [x] `docs/browser-login/PLAYBOOK.md` 存在。
  - [x] `docs/browser-login/PROGRESS.md` 存在。
  - [x] `docs/browser-login/DECISIONS.md` 存在。
  - [x] `docs/parallel-workstreams/README.md` 写明浏览器线目录、分支、设备和端口。
- 依赖：无。

### B1 确认当前内置浏览器和回跳真实链路

- 问题证据：用户描述“用内置浏览器选择登录，自动跳转后点击登录，Google 显示不符合要求”。
- 解法：只读检查当前浏览器代理、LocalServer、Intent、deep link、回调桥接和登录状态写入点，记录真实入口和当前报错可复现路径。
- 验收标准：
  - [ ] 列出当前登录入口、跳转入口、回跳入口和状态拥有者。
  - [ ] 记录 Google 报错原文、URL 参数和触发页面环境。
  - [ ] 明确当前失败属于 WebView/embedded user-agent、redirect URI、client 配置、cookie/session、TLS/UA 还是其他原因。
  - [ ] 不修改代码。
- 依赖：B0。

### B2 调研官方推荐和通用网站登录回跳模式

- 问题证据：用户要求先看别人怎么完成，再看网页登录后返回 App/软件需要什么环境和要求。
- 解法：优先查官方资料和成熟库文档，覆盖 Google、OAuth Native Apps、Chrome Custom Tabs、AppAuth、App Links，以及 ChatGPT/Claude Code 等常见网页登录回跳约束。
- 验收标准：
  - [ ] 至少 5 个可追溯来源。
  - [ ] 区分官方要求、社区经验和推断。
  - [ ] 明确 embedded WebView、Custom Tabs、系统浏览器、无指纹/伪装环境各自风险。
  - [ ] 输出适配 Kite 的实现路线和反路线。
- 依赖：B1。

### B3 设计 Kite 登录回跳协议

- 问题证据：Kite 需要让网页认证完成后回到 App 或对应软件，而不是只解决一个 Google 页面。
- 解法：设计 provider-agnostic 的浏览器 handoff 协议，明确启动、回跳、状态确认、失败展示和重试路径。
- 验收标准：
  - [ ] 设计包含 external browser / Custom Tabs 路线。
  - [ ] 设计包含 App Links 或可验证 redirect 入口。
  - [ ] 设计包含 PKCE/code exchange 的安全边界。
  - [ ] 设计不要求伪造浏览器环境作为主路径。
  - [ ] 设计说明如何兼容内置浏览器里的非 OAuth 普通登录。
- 依赖：B2。

### B4 实现最小通用登录回跳

- 问题证据：当前内置浏览器登录无法稳定通过 provider 要求并回到 Kite。
- 解法：按 B3 设计实现最小可验证链路，优先复用现有 bridge、LocalServer 和 Activity/Intent 模式。
- 验收标准：
  - [ ] OnePlus 8T 上能从 Kite 发起登录并回到正确运行实例或浏览器上下文。
  - [ ] 失败时有可解释状态，不静默回到初始可点状态。
  - [ ] 不新增 provider 单点特判。
  - [ ] 有相关单测或集成测试保护回跳解析。
  - [ ] 构建、安装、截图和 logcat 检查完成。
- 依赖：B3。

### B5 扩展多站点兼容矩阵

- 问题证据：用户明确说问题不止 Google，ChatGPT、Claude Code 或其他网页登录也可能遇到类似限制。
- 解法：建立兼容矩阵，按站点类型验证官方回跳、Custom Tabs、系统浏览器、普通网页登录和失败兜底。
- 验收标准：
  - [ ] 至少覆盖 Google、OpenAI/ChatGPT、Anthropic/Claude 相关网页登录场景。
  - [ ] 每个场景有设备、截图、日志或错误证据。
  - [ ] 不把策略绕过当作默认成功路径。
  - [ ] 记录仍需用户账号或外部权限的验证缺口。
- 依赖：B4。
