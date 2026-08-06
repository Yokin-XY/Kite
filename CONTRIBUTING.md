# 参与开发

中文 | [English](CONTRIBUTING.en.md) | [社区治理 / Community Governance](GOVERNANCE.md)

Kite 当前优先稳定已有能力。开始修改前，先在 [能力状态](docs/reference/feature-status.md) 中确认目标属于稳定主线还是实验能力；浏览器自动化和 X11 的改动不能改变稳定路径的默认行为。

## 共创模式与权限边界

Kite 欢迎问题反馈、方案讨论、文档、测试和代码贡献。公开仓库的默认共创方式是 `Fork + Pull Request`，提交普通贡献不需要主仓库写权限。

普通功能想法、使用问题和开放交流先进入 [Discussions](https://github.com/Yokin-XY/Kite/discussions)。当想法已经形成可执行范围，或者有人准备亲自实现时，再创建关联 Issue；不要把尚未收敛的讨论直接伪装成开发任务。

1. 开始新的功能、修复、测试或文档改动前，先创建或认领一个公开 Issue。已有任务可以直接认领，不重复开题；来自 Discussion 的工作应链接原讨论。
2. Issue 必须说明真实消费场景、预期效果、计划采用的实现机制、范围边界、风险和验证方式。实现机制发生实质变化时，先更新 Issue，让后续代码审查能够对照真实方案。
3. 创建 Issue 是事前公开说明，不是申请维护者批准。贡献者说明清楚后即可自行认领并在个人 Fork 中实现；维护者和社区可以提前指出重复、冲突、架构边界或安全风险。
4. 每个 Pull Request 只处理一个可解释的问题，并关联对应 Issue。项目方在 Pull Request 阶段正式检查方向一致性、来源、代码、依赖、权限、资源执行面和验证证据；自动检查通过不代替人工审查。
5. 维护方根据 Pull Request 审查结果决定合并、要求修改或不合并；正式构建、签名和发布由明确承担对应职责的维护者执行。
6. 社区身份、职责、晋级条件和 GitHub 权限映射以 [社区治理](GOVERNANCE.md) 为准。角色根据持续贡献质量、协作稳定性、安全意识和实际职责确认，不以单次提交或单一数量指标自动升级。

社区角色对应的是协作职责，不改变代码和项目依据许可证、贡献者协议及其他书面约定形成的权利边界。任务协调、代码维护、安全审查和版本发布是不同职责，不因取得其中一种职责而自动获得其他权限。

主线不接受未经审查的直接推送。即使贡献者已经获得仓库写权限，功能修改仍应通过 Pull Request 进入 `main`。

## 贡献授权

提交代码、文档、资源清单、设计或其他材料前，请完整阅读 [贡献者许可协议](CONTRIBUTOR_LICENSE_AGREEMENT.md)。提交 Pull Request 并勾选其中的授权确认，表示贡献者同意该协议。

贡献者保留其原创贡献的著作权，同时向项目方提供持续维护、修改、发布、商业使用和再许可所需的长期授权。社区角色、专门维护职责和商业合作分别依据本文件、仓库设置或另行书面约定确认。

为解决当前项目许可证不允许一般修改的问题，贡献者许可协议同时提供一项狭窄的“贡献准备许可”：仅允许为了向 Kite 官方仓库提交贡献而在 GitHub Fork 中复制和修改项目。它不授权独立分发、商业使用或将 Kite 作为其他项目重新发布。

如贡献属于雇主、客户或其他组织，提交者必须先确认自己有权按贡献者许可协议提供授权；第三方代码、素材、模型、数据或生成内容必须在 Pull Request 中说明来源和适用许可。

## 开发流程

1. 从真实入口确认问题属于 Feature、Application、Platform 还是 Foundation。
2. 让页面提交动作并消费 UiState，不在页面复制业务事实或执行重型探测。
3. 让状态拥有者确认最终结果，普通状态变化只局部更新相关控件。
4. 为行为变化补最窄的单元测试或机器检查；用户可见流程尽量在 OnePlus 8T 真机验证。
5. 一个提交只承载一个可解释的行为边界，不混入截图、APK、日志和无关格式化。

以下范围属于高风险变更，必须在 Pull Request 中单独说明威胁、来源、回退和验证证据：

- `.github/workflows/`、构建脚本、Gradle 配置和发布流程；
- `assets/` 下会进入 APK 的资源、资源清单、下载地址、依赖版本、安装/卸载脚本；
- Shell、终端、PRoot、Bridge、Agent 进程、认证、凭据、网络和文件访问；
- `AndroidManifest.xml` 中的权限、导出组件、Intent、Provider、Service 和 Receiver；
- 签名、密钥、Release、自动更新和任何可能改变外部状态的能力。

不得提交后门、隐蔽遥测、未披露的外联、凭据收集、绕过权限、持久化残留或只为通过当前审查而设计的特判。资源下载和第三方脚本必须说明真实来源；能够固定版本、提交或摘要时应固定，无法固定时必须说明原因和剩余风险。

架构与生命周期规则见 [架构总览](docs/architecture/overview.md) 和 [状态与生命周期](docs/architecture/state-and-lifecycle.md)。

## 常用检查

```powershell
.\scripts\run-kite-tests.ps1 -Profile Full
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
powershell -File scripts/KITE_ARCHITECTURE_CHECKS.ps1
powershell -File scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1
```

资源清单先做 JSON 解析，再按 [资源清单协议](docs/reference/resource-manifest.md) 检查依赖、验证和卸载边界。首页卡片按 [首页卡片 Schema](docs/reference/home-card-schema.md) 编写。

## 文档与产物

- 根 `README.md` 只保留长期稳定的项目介绍和入口；具体说明进入 `docs/architecture/`、`docs/guides/` 或 `docs/reference/`，分类见 [文档总览](docs/README.md)。
- 版本、APK、校验值和更新内容只写入 GitHub Releases；主线实时状态由 GitHub Actions 提供，不在文档中手写当前值。
- 同一事实只维护一份，其他页面使用链接指向它，不复制版本号、能力状态、协议正文或任务进度。
- 当前跨会话任务文件进入 `docs/tasks/<task>/`，任务完成后提炼结论并清理流水。
- APK、截图、logcat、诊断 JSON 和临时报告进入被忽略的 `local-artifacts/`；发布安装包进入 GitHub Releases。
