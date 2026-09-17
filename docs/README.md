# Kite 文档

本目录保存 Kite 的长期说明、使用指南、协议参考和当前任务恢复文件。根目录的 `README.md` 只介绍项目和提供入口；会随版本或实现变化的信息在这里或 GitHub 对应页面维护。

## 信息维护位置

| 信息 | 唯一维护位置 |
| --- | --- |
| 最新正式版本、APK、校验值和更新内容 | [GitHub Latest Release](https://github.com/Yokin-XY/Kite/releases/latest) |
| 历史版本与历史更新记录 | [GitHub Releases](https://github.com/Yokin-XY/Kite/releases) |
| `main` 的实时构建与测试结果 | [GitHub Actions](https://github.com/Yokin-XY/Kite/actions/workflows/ci.yml) |
| 稳定、实验和未承诺能力 | [能力状态](reference/feature-status.md) |
| 当前架构和长期技术决策 | [架构文档](#架构) |
| 构建、使用和验证方法 | [使用指南](#使用指南) |
| Recipe、资源清单和 Bridge 格式 | [协议参考](#协议参考) |
| 当前跨会话任务的计划、进度与决策 | `tasks/<task>/` |
| 参与开发、提交和产物规则 | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| 许可证和第三方声明 | [LICENSE](../LICENSE) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) |

同一事实只在上表指定的位置维护。其他文档需要提及时使用链接，不复制版本号、实时状态或完整协议内容。

## 使用指南

- [构建与安装](guides/build-and-install.md)：开发环境、Gradle 构建、APK 安装和真机检查。
- [版本与发布规则](guides/versioning-and-releases.md)：版本号判断、Android 构建序号、预发布和 AI 执行约束。
- [卡片与资源](guides/cards-and-resources.md)：首页卡片、资源清单、安装流程和状态边界。
- [浏览器认证](guides/browser-auth.md)：WebView、系统浏览器 handoff 和认证回调。
- [应用语言](guides/app-localization.md)：资源库架构、语言切换和新增语言流程。
- [验证方式](guides/verification.md)：单元测试、静态护栏、构建和人工验收。

## 架构

- [架构总览](architecture/overview.md)：模块化单体分层、职责和核心业务链。
- [状态与生命周期](architecture/state-and-lifecycle.md)：状态拥有者、局部更新、显示面与运行生命周期。
- [混合运行路由](architecture/runtime-provider-routing.md)：原生能力、通用依赖快速通道与 Ubuntu/PRoot 的统一选择合同。
- [通用依赖快速通道](architecture/managed-runtime-fast-path.md)：Node 已验证基线、Python 候选和运行时版本租约。
- [宿主 Python 性能矩阵](architecture/host-python-performance-matrix.md)：Python go/no-go、Host/PRoot 对照和兼容分层。
- [Android/NDK 原生能力](architecture/native-capability-provider.md)：结构化原生能力、下载校验样板和安全边界。
- [Ubuntu/PRoot 兼容 Provider](architecture/proot-compatibility-provider.md)：最终 Linux 回退、温热 Runner 和可调调度档位。
- [宿主 Node 快速运行时](architecture/host-node-runtime.md)：当前合同、HN-001～HN-009 风险索引和增量回归规则。
- [设置中心架构](architecture/settings.md)：能力分类、状态拥有者、入口类型和新增设置流程。
- [主题系统规范](architecture/theme-system.md)：颜色、组件风格、固定设计基础、特殊内容边界和新模块接入标准。
- [长期决策](architecture/decisions.md)：当前仍然有效的架构决策。

## 协议参考

- [能力状态](reference/feature-status.md)：正式与实验能力的当前边界。
- [首页卡片 Schema](reference/home-card-schema.md)：Recipe 字段、步骤和启动配置。
- [资源清单协议](reference/resource-manifest.md)：资源依赖、动作、验证和首页卡片模板。
- [本地 Bridge 合同](reference/bridge-protocol.md)：本地控制服务的地址、认证和请求边界。

## 当前任务

`tasks/` 只用于跨会话任务恢复，不是产品说明或发布记录。任务完成后，应把长期有效结论提炼到架构、指南或参考文档，并清理不再需要的过程流水。

当前运行底座实验任务位于 `tasks/runtime-foundation-lab/`；它只记录执行状态，长期合同以上述架构文档为准。
