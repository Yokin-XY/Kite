# Kite 安全政策 / Security Policy

[中文](#中文) | [English](#english)

## 中文

### 私密报告漏洞

请通过 [GitHub Private Vulnerability Reporting](https://github.com/Yokin-XY/Kite/security/advisories/new) 私密报告以下问题，不要创建公开 Issue、Discussion 或 Pull Request：

- Token、API Key、账号或其他凭据泄露；
- 认证、授权、Android 权限或文件访问绕过；
- 未经授权的命令执行、容器逃逸、宿主桥滥用或持久化；
- 资源卡、资源清单、下载、依赖、脚本、构建或更新供应链攻击；
- 隐蔽遥测、未披露外联或能够造成用户数据泄露的问题。

报告应尽量包含受影响版本和组件、影响、复现步骤、攻击前提、必要日志或最小样例，以及已知的缓解或修复建议。不要提交真实用户凭据、无关个人数据或已经武器化的大规模利用材料。

维护方会先确认是否能够复现和是否属于安全边界，再通过 Security Advisory 协调澄清、修复、验证和披露。未经协调请勿公开仍可利用的细节。

### 支持范围

安全修复优先面向当前 Latest Release 和 `main`。旧版本可能不再单独修复；维护方会根据影响、可利用性和发布成本决定回补范围。第三方组件的问题仍应同时遵循其上游安全渠道。

## English

### Report vulnerabilities privately

Use [GitHub Private Vulnerability Reporting](https://github.com/Yokin-XY/Kite/security/advisories/new) for the following issues. Do not open a public Issue, Discussion, or Pull Request:

- token, API key, account, or other credential exposure;
- authentication, authorization, Android permission, or file-access bypass;
- unauthorized command execution, container escape, host-bridge abuse, or persistence;
- supply-chain attacks involving resource cards, manifests, downloads, dependencies, scripts, builds, or updates;
- hidden telemetry, undisclosed network access, or user-data exposure.

Include affected versions and components, impact, reproduction steps, attack prerequisites, relevant logs or a minimal example, and any known mitigation or fix. Do not submit real user credentials, unrelated personal data, or broadly weaponized exploit material.

Maintainers first confirm reproducibility and security scope, then coordinate clarification, remediation, verification, and disclosure through the Security Advisory. Do not publish exploitable details before coordination.

### Supported versions

Security fixes prioritize the current Latest Release and `main`. Older versions may not receive separate fixes; backports depend on impact, exploitability, and release cost. Vulnerabilities in third-party components should also follow the upstream project's security channel.
