# Kite

[![CI](https://github.com/Yokin-XY/Kite/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/Yokin-XY/Kite/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/Yokin-XY/Kite?display_name=tag&sort=semver)](https://github.com/Yokin-XY/Kite/releases/latest)

Kite 是运行在 Android 上的本地 Linux 与 AI 工具工作台。它把卡片、资源安装、终端、网页和运行实例组织在一个应用中，并以 KF/KFShell、Ubuntu PRoot 和 Android 系统能力作为执行底座。

最低支持 Android 9（API 28）。

## 获取 Kite

- [下载最新正式版](https://github.com/Yokin-XY/Kite/releases/latest)：自动跳转到 GitHub 当前标记为 Latest 的版本，可查看 APK、校验值和本次更新内容。
- [查看全部版本](https://github.com/Yokin-XY/Kite/releases)：保留每个正式版和测试版的产物与更新记录。
- [查看主线状态](https://github.com/Yokin-XY/Kite/actions/workflows/ci.yml)：显示 `main` 当前的 Lint 与单元测试结果。

Latest Release 是已经锁定的正式产物；`main` 是下一版本的开发主线，可能包含尚未发布的修复。README 不固定记录具体版本号，发布新版本后以上入口会自动指向最新状态。

## 主要能力

- 用首页卡片把 Shell、终端、网页和 Android 动作组合成有序流程。
- 通过资源中心管理依赖、安装、验证、打开和卸载。
- 在运行实例中统一管理终端、SH 报告、网页、通知和实例产生的进程。
- 普通网页使用 WebView；OAuth/SSO 登录交给系统浏览器，再把回调送回原始 CLI 或运行实例。

稳定能力、实验能力和未承诺范围只在 [能力状态](docs/reference/feature-status.md) 中维护。代码中存在的能力不等于已经进入正式支持范围，具体以该状态页为准。

## 参与共建

Kite 欢迎问题反馈、方案讨论、文档、测试和代码贡献。默认协作流程是：先确认一个边界清楚的方向，在个人 Fork 中实现并提交 Pull Request，经过自动检查、代码审查和必要的运行验证后，由维护方合并进入主线。

资源安装、Shell/PRoot、Agent 进程、认证凭据、Android 权限、构建和发布链属于高风险范围，会进行额外的来源与安全审查。完整流程、角色职责和验证要求见 [参与开发](CONTRIBUTING.md)；提交贡献前请阅读 [贡献者许可协议](CONTRIBUTOR_LICENSE_AGREEMENT.md)。

## 文档入口

| 需要了解 | 入口 |
| --- | --- |
| 文档分类和信息维护位置 | [文档总览](docs/README.md) |
| 构建、安装和本地环境 | [构建与安装](docs/guides/build-and-install.md) |
| 卡片、资源与使用方式 | [卡片与资源](docs/guides/cards-and-resources.md) |
| WebView 与系统浏览器认证 | [浏览器认证](docs/guides/browser-auth.md) |
| 模块职责和业务链 | [架构总览](docs/architecture/overview.md) |
| 状态拥有者与生命周期 | [状态与生命周期](docs/architecture/state-and-lifecycle.md) |
| Recipe、资源清单和 Bridge 协议 | [参考文档](docs/README.md#协议参考) |
| 参与开发和提交要求 | [参与开发](CONTRIBUTING.md) |

详细内容由对应文档维护，根 README 只提供长期稳定的项目介绍和入口。

## 安全边界

Kite 会在本地 Linux 环境中执行卡片、资源清单和第三方脚本。使用前应确认来源；本地控制服务不应暴露到不可信网络。Kite 不伪造浏览器身份，也不保存第三方账号密码。

## 许可证

Kite 原创代码和文档使用 [PolyForm Strict License 1.0.0](LICENSE)：允许符合条款的非商业使用，但不授权修改、衍生或分发。仓库中的 Ubuntu、PRoot、Termux、Node.js、Python、uv、AndroidX、Shizuku 等第三方组件继续遵守各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
