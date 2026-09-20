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
| 当前跨会话任务的计划、进度与决策 | `docs/autonomous-task/`（三件套流水） |
| 已完成并归档的计划与研究 | `docs/archive/`（只读历史，不维护） |
| 参与开发、提交和产物规则 | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| 许可证和第三方声明 | [LICENSE](../LICENSE) 与 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) |

同一事实只在上表指定的位置维护。其他文档需要提及时使用链接，不复制版本号、实时状态或完整协议内容。

## 运行时体系（先读这两份）

- [Ubuntu 模拟态纲领](architecture/ubuntu-simulation-doctrine.md)：运行时统一总方针——目标、95/5 原则、确定性锚点、三层架构、雷库与收编闭环。**所有运行时工作的出发点。**
- [Ubuntu 通用车道方案](plans/ubuntu-fast-lane-plan.md)：现行主计划——工程分期（U0~U5）、外部调研（proroot/andClaw/DSHA/lroot）与坑位防线。

## 使用指南

- [构建与安装](guides/build-and-install.md)：开发环境、Gradle 构建、APK 安装和真机检查。
- [版本与发布规则](guides/versioning-and-releases.md)：版本号判断、Android 构建序号、预发布和 AI 执行约束。
- [卡片与资源](guides/cards-and-resources.md)：首页卡片、资源清单、安装流程和状态边界。
- [性能架构](guides/performance-architecture.md)：快速通道的性能分层、测量方法和调优边界。
- [浏览器认证](guides/browser-auth.md)：WebView、系统浏览器 handoff 和认证回调。
- [应用语言](guides/app-localization.md)：资源库架构、语言切换和新增语言流程。
- [验证方式](guides/verification.md)：单元测试、静态护栏、构建和人工验收。
- [OnePlus 8T 刷机与恢复](guides/oneplus8t-root-and-recovery.md)：开发机维护手册。

## 架构

### 总纲与业务架构

- [架构总览](architecture/overview.md)：模块化单体分层、职责和核心业务链。
- [状态与生命周期](architecture/state-and-lifecycle.md)：状态拥有者、局部更新、显示面与运行生命周期。
- [长期决策](architecture/decisions.md)：当前仍然有效的架构决策（含六 Agent、CC Switch、模拟态）。
- [设置中心架构](architecture/settings.md)：能力分类、状态拥有者、入口类型和新增设置流程。
- [主题系统规范](architecture/theme-system.md)：颜色、组件风格、固定设计基础和新模块接入标准。
- [测试执行档位](architecture/test-execution-profiles.md)：三层测试与本机 Gradle 协调。

### Agent 会话体系

- [Agent 配置边界](architecture/agent-configuration-boundary.md)：配置 UI 与 SDK 边界、模型来源与 Provider 目录。
- [Agent 发现目录](architecture/agent-discovery-catalog.md)：ACP Registry 三层发现与六 Agent 接入路径。
- [Agent 原生扩展矩阵](architecture/agent-native-extension-matrix.md)：各 Agent Skill/MCP 原生配置位置。
- [Agent 推理强度](architecture/agent-reasoning-levels.md)：七档统一语义与六 Agent 映射。
- [后台运行强身份](architecture/background-runtime-strong-identity.md)：后台 lease 迁移与强进程身份。

### 运行时与车道

- [混合运行路由](architecture/runtime-provider-routing.md)：三车道选择合同与运行时总架构。
- [通用依赖快速通道](architecture/managed-runtime-fast-path.md)：车道原则、肯定式保证、版本租约和发布门。
- [宿主 Node 快速运行时](architecture/host-node-runtime.md)：Node 车道合同与 HN 债务索引。
- [宿主 Node 性能矩阵](architecture/host-node-performance-matrix.md)：Host/PRoot 对照与重跑规则。
- [宿主 Python 性能矩阵](architecture/host-python-performance-matrix.md)：Python go/no-go 与兼容分层。
- [受管命令原生化证明](architecture/managed-command-native-proof.md)：command -v 原生化链。
- [Android/NDK 原生能力](architecture/native-capability-provider.md)：下载、文件、归档与版本能力。
- [Ubuntu/PRoot 兼容 Provider](architecture/proot-compatibility-provider.md)：保底车道准入、温热池与档位。
- [统一 PRoot 容量](architecture/unified-proot-capacity.md)：短任务与长期 owner 统一容量与保底余量合同。
- [PRoot 活跃开销归因](architecture/proot-active-runtime-overhead.md)：v24 消融与重建合同。
- [混合底座兼容总账](architecture/runtime-compatibility-backlog.md)：三条车道的债务总索引。
- [语言分工](architecture/runtime-language-placement.md)：Kotlin/Rust/C/PRoot 边界。
- [设备能力桥](architecture/kite-device-bridge.md)：Shizuku 桥分层与能力合同。

## 协议参考

- [能力状态](reference/feature-status.md)：正式与实验能力的当前边界。
- [首页卡片 Schema](reference/home-card-schema.md)：Recipe 字段、步骤和启动配置。
- [资源清单协议](reference/resource-manifest.md)：资源依赖、动作、验证和首页卡片模板。
- [本地 Bridge 合同](reference/bridge-protocol.md)：本地控制服务的地址、认证和请求边界。
- [资源源路由](resource-source-routing.md)：安装源的优先序与选择规则。

## 计划与任务

- `plans/`：**尚未完成**的现行计划（已完成或被取代的计划在 `plans/` 头部标注状态，完成后移入 `archive/`）。
- `autonomous-task/`：跨会话任务三件套流水（ledger/state/结论），任务完成后结论提炼进正式文档并清理流水。
- `tasks/stabilization/architecture-baseline.json`：架构静态护栏基线（被 `scripts/KITE_ARCHITECTURE_CHECKS.ps1` 消费，勿删）。
- `archive/`：已完成计划与 no-go 研究的只读历史，每份头部有归档说明与结论去向。

## 文档维护规则

1. 计划定稿并执行完后，把长期结论沉淀进 architecture/guides/reference，计划文件加归档头移入 `archive/`。
2. 架构文档与 plans/ 不重复维护同一事实：plans 讲"怎么做与何时做"，architecture 讲"是什么与为什么"。
3. 运行时类改动必须回查 [模拟态纲领](architecture/ubuntu-simulation-doctrine.md) 的优先级纪律（雷源头补丁 > 兼容垫片 > 运行时保镖 > PRoot）。
4. 涉及验收设备的表述统一双机口径：OnePlus 8T 开发 + 魅族 18 验收。
