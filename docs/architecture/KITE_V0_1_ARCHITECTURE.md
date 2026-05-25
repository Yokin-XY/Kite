# Kite V0.1 Architecture

## 一、目标

Kite 的目标是把 KF 从“能运行的移动 Linux / AI 底座”包装成“可图形化操作、可配置表启动、可 Web 工作台交互的手机 AI 工作站操作层”。

Kite 不重新实现 KF。KF / KFShell 继续负责 proot Ubuntu、rootfs、进程管理、AI 工具、服务端口、日志、OCR、Hermes 等执行环境。Kite 负责把这些能力组织成用户和 AI 都能理解、启动、查看、诊断的操作层。

Kite V0.1 要解决的问题：

- 配置表启动。
- 一键任务执行。
- 本地 Web 工作台。
- 日志展示。
- 结果交互。
- 外链 / 认证代理。
- WebView 诊断。
- 未来 Android 原生能力入口。

命名约定：

- 应用名：Kite
- 首页 / 控制台：Kite Console
- 配置表：Kite Recipe
- 任务卡片：Kite Card
- Web 工作台：Kite Workbench
- 浏览器壳：Kite Web Shell
- 端口协议：Kite Bridge Protocol
- 诊断层：Kite Diagnostics

## 二、非目标

Kite V0.1 不做：

- 不做完整浏览器。
- 不做独立浏览器内核。
- 不做 GeckoView。
- 不做自带 Chromium。
- 不做无障碍。
- 不做 Shizuku / 截图 / 高权限桥。
- 不改 KF 核心 runtime。
- 不做复杂浏览器自动化。
- 不做完整配置表生态。
- 不做第三方权限小应用。
- 不把所有能力塞回 KF 主 APK。

## 三、物理应用关系

V0.1 主要是两个应用：

1. KF / KFShell

   执行底座。负责 proot Ubuntu、进程管理、AI 工具、服务端口、日志、OCR、Hermes 等。

2. Kite

   操作层。负责配置表、Web 壳、诊断、快捷入口、认证代理。

后期如果无障碍、Shizuku、截图等权限变重，可以拆第三个可选应用：

- Permission Bridge / Android Permission Bridge

但 V0.1 不做第三个应用，也不把权限桥作为 Kite 的首要任务。

## 四、逻辑层级

逻辑上可以分层，但 V0.1 的物理形态不要拆太散。Kite 先作为一个独立应用工程存在，内部模块清晰即可。

推荐逻辑层级：

```text
KF Runtime
  执行层：proot Ubuntu、shell、AI 工具、OCR、Web 服务、日志、进程管理。

Kite
  操作层：配置表、一键启动、任务身份、WebView 工作台、诊断日志。

Kite Recipe
  任务身份层：既是配置表，也是快捷入口、工作台身份、日志目录、状态对象。

Kite Web Shell
  显示层：打开本地 Web 子应用、捕获错误、生成能力报告、跳转外链。

Work Plane
  工作平面：Hermes WebUI、OCR 页面、文件服务、Codex WebUI、Python/Node 服务页面等。
```

## 五、模块划分

Kite 内部建议模块：

- Kite Console / Kite Recipe Console
- Kite Web Shell
- Kite Bridge Client
- Kite Diagnostics
- Kite Capability Reporter
- Kite Browser/Auth Bridge
- Kite Shortcut/Task Identity Manager

这些只是 Kite 内部模块，不是多个独立 App。V0.1 需要保持工程边界轻量，避免一开始就把操作层拆成过多物理组件。

## 六、Kite Recipe 配置表草案

Kite Recipe 初版 JSON schema 草案：

```json
{
  "id": "hermes-webui",
  "name": "Hermes WebUI",
  "description": "启动 Hermes 图形化工作台",
  "icon": "hermes",
  "taskLabel": "Hermes",
  "defaultUrl": "http://127.0.0.1:8648",
  "shortcut": true,
  "taskMode": "separate",
  "steps": [
    {
      "type": "shell",
      "cmd": "hermes-web-ui start --port 8648",
      "wait": true
    },
    {
      "type": "healthcheck",
      "url": "http://127.0.0.1:8648/health"
    },
    {
      "type": "open_web",
      "url": "http://127.0.0.1:8648"
    }
  ]
}
```

Kite Recipe 不只是脚本。它同时承担：

- 启动配置。
- 任务身份。
- Web 工作台入口。
- 快捷方式来源。
- 最近任务卡片身份。
- 日志 / 状态绑定对象。
- 后续可分享工作流。

V0.1 只需要能读取和执行最小 Recipe，不需要定义完整生态、市场、权限模型或复杂依赖解析。

## 七、Kite Bridge Protocol 端口协议草案

Kite 和 KF 之间的早期临时协议可以先定义为本地 HTTP 接口：

```text
GET  /status
POST /open-web
POST /open-auth
POST /run-recipe
GET  /logs/{jobId}
GET  /capabilities
```

V0.1 阶段可以先临时绑定端口，不要求 KF 本体正式集成。

早期允许：

```text
Ubuntu/proot 内临时脚本
-> curl Kite 端口
-> 测试 open-web / run-recipe / status
```

后期再由 KF 本体提供：

- Local Service Registry
- 标准命令 shim
- 默认浏览器代理
- token / handshake
- 关联启动协议

V0.1 文档只规划，不实现这些正式绑定。后续实现时必须避免直接修改 KF 核心 runtime，除非测试证明必须轻改。

## 八、Kite Web Shell 方案

Kite Web Shell 使用：

- Android System WebView
- AndroidX WebKit
- JS Bridge
- console / error / unhandledrejection 捕获
- capability report
- 外链跳系统浏览器

允许在壳内打开的本地 URL：

- `http://127.0.0.1:*`
- `http://localhost:*`

默认跳系统浏览器 / Chrome 的外部 URL：

- `https://github.com`
- `https://chatgpt.com`
- `https://openai.com`
- 其他公网链接

Kite Web Shell 不是完整浏览器。它只服务 KF 本地 Web 子应用和手机 AI 工作站工作台。它不负责通用网页浏览、复杂标签页管理、下载器生态或浏览器自动化。

## 九、认证代理思路

认证 URL 不一定放在 WebView 内完成。

建议策略：

```text
本地工具页面：
  使用 Kite Web Shell 打开。

外部登录 / OAuth / 账号验证：
  优先跳系统浏览器 / Chrome。
```

后续可规划 Linux 默认浏览器代理：

```text
proot Ubuntu 内 xdg-open / BROWSER
-> KF/Kite 代理
-> Android Chrome 打开
-> localhost callback 回到 proot 内 CLI
```

V0.1 只写设计，不实现默认浏览器代理、OAuth callback 绑定或账号系统。

## 十、Kite Diagnostics 诊断日志设计

建议日志位置由 Kite 自己管理，并让 Hermes / Codex / AI 可以读懂。V0.1 可以先使用应用私有目录或共享诊断目录，后续再和 KF 的日志目录建立关联。

建议日志文件：

```text
webview-console.log
webview-errors.jsonl
webview-capabilities.json
last-webapp-status.json
```

诊断内容至少包含：

- 当前 URL。
- 页面标题。
- console message。
- JS error。
- unhandledrejection。
- WebView capability report。
- 外链跳转记录。
- 本地服务打开失败记录。

日志格式应优先机器可读，例如 JSONL。人类可读摘要可以另做，但不能替代结构化日志。

## 十一、Web API 能力分级

Kite Web Shell 需要给 Web 能力做分级，避免一个网页缺少某个浏览器 API 就整体崩掉。

A 类：WebView 天然支持

示例：

- fetch
- WebSocket
- localStorage
- IndexedDB
- Canvas

处理方式：检测 + 报告。

B 类：AI Web 应用高频，值得补

示例：

- speechSynthesis
- clipboard
- file picker
- download
- TTS / STT

处理方式：shim / Android 原生桥 / 降级。

C 类：成本高、风险高、维护重

示例：

- WebUSB
- WebBluetooth
- WebGPU
- 复杂 Service Worker 后台能力

处理方式：不补，只报告，必要时建议外部浏览器。

特别说明：

speechSynthesis 这类能力缺失时，不能让整个页面崩溃。V0.1 可以先规划 noop shim，后续再接 Android TTS。

## 十二、V0.1 最小验收标准

Kite V0.1 后续实现的最小验收标准：

1. Kite 能启动。
2. Kite 能打开本地 WebView 页面。
3. Kite 能打开 `http://127.0.0.1:8648` 这类本地服务。
4. 外链会跳系统浏览器。
5. console / error 能记录。
6. capability report 能生成。
7. 能读取一个 Kite Recipe 文件。
8. 能根据 Kite Recipe 打开 `defaultUrl`。
9. 能通过临时端口调用 KF 侧测试接口。
10. 不修改 KF 本体核心 runtime。

## 十三、后续实现阶段建议

建议阶段：

1. 第二步：做最小 Kite 工程骨架。
2. 第三步：Hermes 在手机 KF 环境里做真机测试。
3. 第四步：根据测试结果再决定是否轻改 KF 本体。
4. 第五步：后期再考虑强绑定、默认浏览器代理、快捷方式和 task 身份。

当前阶段只定义方向、边界、模块、接口和验收标准，不实现功能代码。

## 十四、V0.1 补充边界

### 1. 端口角色

V0.1 允许两种临时通信形态同时存在，但必须区分角色：

- Kite 可以临时开启只绑定 `127.0.0.1` 的本地 HTTP 端口，供 proot Ubuntu 内的临时脚本通过 `curl` 调用，例如测试 `/status`、`/open-web`。
- Kite 也可以作为 client 调用 KF 侧的临时测试端口，例如读取 KF 暴露的状态、日志或能力报告。

端口角色不能被理解为“Kite 取代 KF 执行层”。只要涉及 Linux shell、proot 进程、AI 工具启动、端口服务启动，最终执行权都属于 KF 或测试 stub，不属于 Kite 原生侧。

### 2. Recipe step 执行权

Kite Recipe 中的 `shell` step 不是让 Kite 原生侧直接执行 Linux 命令。

Kite 在 V0.1 的职责是：

- 读取 Recipe。
- 展示 Recipe。
- 根据 Recipe 打开 `defaultUrl`。
- 请求 KF 执行 Recipe。
- 或在开发期通过 mock / stub 模拟执行结果。

Kite 原生侧不得直接实现 proot shell 执行器。否则会把 KF 的执行层重新混入 Kite，破坏“KF 负责执行，Kite 负责操作”的边界。

### 3. V0.1 安全边界

正式 token / handshake 可以留到后续阶段，但 V0.1 必须遵守最小安全边界：

- 所有开发测试接口默认只绑定 `127.0.0.1`。
- 不监听 `0.0.0.0`，除非单独写明测试原因。
- 不把敏感接口暴露到局域网。
- 文档和日志中明确标注：无 token 的接口仅限开发测试。

后续所有敏感接口都必须设计 token / handshake，包括但不限于：

- `POST /run-recipe`
- `POST /open-auth`
- 任何会触发 KF 执行层动作的接口
- 任何会读取日志、状态、路径、能力报告的接口

### 4. Diagnostics 开发期路径

V0.1 工程骨架阶段先固定 Kite 自己的诊断目录，避免实现时反复猜路径。

建议开发期路径：

```text
Kite app private files dir:
  files/diagnostics/

Suggested files:
  files/diagnostics/webview-console.log
  files/diagnostics/webview-errors.jsonl
  files/diagnostics/webview-capabilities.json
  files/diagnostics/last-webapp-status.json
```

这些日志先归 Kite 管理，不强依赖 KF 共享目录。后续如果需要让 Hermes / Codex / KF 侧 AI 直接读取，再规划同步到 KF 共享诊断目录或通过 Kite Bridge Protocol 暴露。
