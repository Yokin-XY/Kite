## 改动说明 / Change summary

请说明解决的问题、真实入口和用户可见结果。一个 Pull Request 只处理一个可解释的行为边界。
Describe the problem, real entry point, and user-visible result. Keep one explainable behavior boundary per Pull Request.

关联 Issue / Linked Issue:

> 外部贡献、实质性产品行为或敏感改动必须关联 Issue。无需公开 Issue 的维护者低风险小修填写 `N/A` 并说明原因。 / External contributions, material product behavior, and sensitive changes require an Issue. Use `N/A` with a reason only for low-risk maintainer fixes that do not require one.

- [ ] 需要关联 Issue 时，原 Issue 已在实现前说明消费场景、预期效果和实现机制；否则已填写 `N/A` 并说明原因 / When an Issue is required, it disclosed the use case, outcome, and mechanism before implementation; otherwise `N/A` includes the reason
- [ ] 当前实现与已公开机制一致；如有实质变化，已先更新 Issue 并说明原因 / The implementation matches the disclosed mechanism, or the Issue explains material changes

实现机制摘要 / Mechanism summary:

## 风险声明 / Risk disclosure

请勾选所有涉及的范围 / Select every applicable area:

- [ ] 普通 UI、文档或测试 / Ordinary UI, documentation, or tests
- [ ] 资源卡、资源清单、下载地址、依赖或第三方脚本 / Resources, manifests, downloads, dependencies, or third-party scripts
- [ ] Shell、终端、PRoot、Bridge 或 Agent 进程 / Shell, terminal, PRoot, bridge, or Agent processes
- [ ] 网络、认证、凭据、文件访问或 Android 权限 / Network, authentication, credentials, file access, or Android permissions
- [ ] GitHub Actions、构建、签名、发布或自动更新 / Actions, builds, signing, releases, or updates
- [ ] 不涉及以上高风险范围 / None of the high-risk areas above

如涉及高风险范围，请说明来源、权限变化、可能的持久化位置、失败回退和剩余风险。
For high-risk changes, describe sources, permission changes, persistence, fallback, and residual risk.

## 验证证据 / Verification evidence

- [ ] 已复读全部改动文件 / All changed files were reviewed
- [ ] 已运行与改动相关的最小测试或静态检查 / Relevant tests or static checks were run
- [ ] 行为改动已构建或运行关键路径 / Behavior changes were built or exercised on the critical path
- [ ] 用户可见改动已进行页面、截图或真机验证，或已说明无法验证的原因 / User-visible changes were checked in UI or on device, or the limitation is explained

执行的命令和结果 / Commands and results:

## 来源与贡献授权 / Source and contribution authorization

- [ ] 我有权提交全部内容，并已说明第三方或生成材料的来源与许可 / I may submit all content and disclosed third-party or generated material and licenses
- [ ] 本 PR 不故意包含后门、恶意代码、隐蔽遥测、未披露外联、凭据收集或规避审查的机制 / This PR intentionally contains no backdoor, malicious code, hidden telemetry, undisclosed network access, credential collection, or review evasion
- [ ] 我已经阅读并同意 [Kite 贡献者许可协议](https://github.com/Yokin-XY/Kite/blob/main/CONTRIBUTOR_LICENSE_AGREEMENT.md) / I have read and agree to the linked Kite Contributor License Agreement
- [ ] 我已经阅读 [参与开发](https://github.com/Yokin-XY/Kite/blob/main/CONTRIBUTING.md) / [Contributing Guide](https://github.com/Yokin-XY/Kite/blob/main/CONTRIBUTING.en.md)，并理解自动检查不能代替人工审查 / I understand automated checks do not replace human review

> 维护方会根据项目方向、兼容性、安全、质量和验证结果决定是否合并。 / Maintainers decide whether to merge based on project direction, compatibility, security, quality, and verification.
