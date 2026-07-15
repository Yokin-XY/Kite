# Kite

Kite 是运行在 Android 上的本地 Linux 与 AI 工具工作台。它把卡片、资源安装、终端、网页和运行实例组织在一个应用中，并以 KF/KFShell、Ubuntu PRoot 和 Android 系统能力作为执行底座。

当前正式版本为 `0.0.1`，最低支持 Android 9（API 28）。项目仍处于快速稳定化阶段，通过 GitHub Release 分发安装包。

## 稳定能力

- **首页卡片**：用顺序 Recipe 组合 Shell、终端、网页和 Android 动作。
- **资源中心**：声明依赖、执行安装与卸载、验证结果并登记资源状态。
- **运行实例**：统一管理报告、终端、网页、通知及由实例产生的进程。
- **终端与报告**：提供交互终端、后台 Shell 报告和可复制的运行结果。
- **网页工作台**：本地页面和普通网页使用 Android WebView；OAuth/SSO 登录交给系统浏览器，回调再交还原始 CLI 或运行实例。
- **运行管理**：按实例、终端和进程事实查看及关闭运行内容。

## 实验能力

浏览器自动化和 X11 桌面能力仍保留在代码中，方便继续研究，但不属于 `0.0.1` 的稳定能力承诺。实验能力可能存在兼容、交互和生命周期缺口，不应作为正式交付验收依据。

## 工作原理

```text
用户动作
-> Feature 提交意图
-> Application 编排动作
-> 状态拥有者写入事实
-> Projector 生成页面状态
-> Screen 局部更新
-> Platform / Foundation 执行 Android、PRoot、终端或浏览器能力
```

运行工作流使用实例和代次区分每次启动。页面离开不会自动停止任务；用户关闭实例时，Kite 才会沿实例拓扑回收终端、窗口和由该实例产生的进程。

详细结构见 [架构总览](docs/architecture/overview.md) 和 [状态与生命周期](docs/architecture/state-and-lifecycle.md)。

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `app/` | Android 应用、Feature、Application、Platform 与运行时适配代码 |
| `terminal-view-local/` | 本地集成的 Termux TerminalView |
| `assets/resources/` | 资源清单、图标和资源首页布局 |
| `assets/proot/` | PRoot 运行资产 |
| `assets/toolchain/` | 随包分发的工具链资产 |
| `docs/architecture/` | 当前主线架构和长期决策 |
| `docs/guides/` | 构建、卡片、资源和登录使用说明 |
| `docs/reference/` | Recipe、资源清单和 Bridge 合同 |
| `docs/tasks/` | 当前长期任务的恢复文件，不属于正式产品说明 |
| `scripts/` | 运行底座构建、架构护栏、运行车道和体积检查 |

## 构建

需要 JDK 17 和可用的 Android SDK。Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

完整环境与安装说明见 [构建与安装](docs/guides/build-and-install.md)。

## 文档

- [能力状态](docs/reference/feature-status.md)
- [卡片与资源](docs/guides/cards-and-resources.md)
- [WebView 与系统浏览器认证](docs/guides/browser-auth.md)
- [首页卡片 Schema](docs/reference/home-card-schema.md)
- [资源清单协议](docs/reference/resource-manifest.md)
- [本地 Bridge 合同](docs/reference/bridge-protocol.md)
- [验证方式](docs/guides/verification.md)
- [参与开发](CONTRIBUTING.md)

## 安全边界

- 本地控制服务只应绑定回环地址，不应暴露到不可信网络。
- 卡片和资源清单只描述动作，不拥有 Bridge 地址、认证凭据或底层执行权限。
- 第三方脚本和资源仍会在本地 Linux 环境中执行，应确认来源后再安装。
- OAuth 授权参数和回调由通用认证桥原样转交，Kite 不伪造浏览器身份，也不保存第三方账号密码。

## 许可证

Kite 原创代码和文档使用 [PolyForm Noncommercial License 1.0.0](LICENSE)。仓库中的 Ubuntu、PRoot、Termux、Node.js、Python、uv、AndroidX、Shizuku 等第三方组件继续遵守各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
