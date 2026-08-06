# Kite 社区治理 / Community Governance

[中文](#中文) | [English](#english)

## 中文

### 治理原则

Kite 采用公开协作、事前说明、最小权限和职责分离的治理方式：

- 新工作先通过 Issue 公开消费场景、预期效果、实现机制、范围、风险和验证方式。开 Issue 是事前说明，不是申请维护者批准。
- 贡献者可以自行认领并在个人 Fork 中实现；实现机制发生实质变化时，先更新原 Issue。
- Pull Request 是正式准入和代码审查入口。自动检查通过不代表项目必须合并。
- 社区身份依据持续职责、协作质量和安全意识确认，不按代码行数、提交次数或单次贡献自动升级。
- 任务协调、代码写入、代码审查、合并、发布和安全管理是不同职责；获得其中一种职责不会自动取得其他权限。
- 仓库和外部系统始终遵循最小必要权限。职责不再需要或出现安全风险时，可以调整或撤销权限。

### 协作流程

1. **说明或认领**：创建新的 `[共建]` Issue，或者认领已有 Issue。
2. **公开方案**：说明消费场景、预期效果、实现机制、范围、风险和验证计划。
3. **自行实现**：不需要等待事前批准，在个人 Fork 和独立分支中实现。
4. **保持同步**：方案发生实质变化时，先更新 Issue；一个 Pull Request 只处理一个行为边界。
5. **正式审查**：Pull Request 关联 Issue，并接受方向、架构、来源、安全、代码和验证审查。
6. **合并与发布**：只有满足主线保护规则并由具备职责的维护者确认后才能合并；合并不等于立即发布。

### 任务分类与状态

Issue 使用少量、稳定的分类，避免靠标题猜测状态：

- 类型：`bug`、`enhancement`、`documentation`、`question`。
- 状态：`status: proposal`（方案说明中）、`status: ready`（可以认领）、`status: claimed`（已认领）、`status: blocked`（存在明确阻塞）、`status: review`（已有 Pull Request 正在审查）。
- 风险：`risk: sensitive` 用于认证、凭据、网络、资源、脚本、容器、Android 权限、构建和发布等附加安全审查范围。
- 模块：只在任务数量确实需要时增加 `area:` 标签，不为单个 Issue 创建一次性分类。

任务协调者可以整理分类、推荐负责人、跟踪认领和提醒长期无进展的任务，但不能单方面承诺功能进入主线、决定架构边界、批准代码、合并或发布。个人仓库阶段，任务协调者通过 Issue 评论提出分类和分配建议，由仓库所有者执行需要权限的操作。

### 社区角色

| 角色 | 职责 | 默认技术权限 |
| --- | --- | --- |
| `Community Member` 社区成员 | 使用、讨论、反馈问题和提出建议 | 无额外权限 |
| `Contributor` 贡献者 | 说明方向、认领任务、通过 Fork 提交代码、文档、测试或设计 | 无额外权限 |
| `Task Coordinator` 任务协调者 | 整理和推荐任务、帮助分类、跟踪认领与阻塞、促进公开讨论 | 组织仓库可映射为 `Triage`；个人仓库阶段不自动授予写权限 |
| `Collaborator` 协作者 | 持续实现边界清楚的任务，必要时在主仓库创建开发分支 | 组织仓库可映射为 `Write`；所有改动仍经 Pull Request |
| `Module Maintainer` 模块维护者 | 维护明确模块、审查相关改动、保持模块合同和测试质量 | `Write` 或 `Maintain`，并通过 `CODEOWNERS` 限定审查范围 |
| `Project Maintainer` 项目维护者 | 跨模块协调、维护规则、审查和合并符合条件的改动 | `Maintain`；不自动取得管理员或外部密钥权限 |
| `Project Owner` 项目所有者 | 项目方向、仓库管理、敏感权限和最终治理责任 | `Admin` |

`Security Reviewer`（安全审查者）与 `Release Maintainer`（发布维护者）是专项职责，不是普通晋升级别：

- 安全审查者负责认证、凭据、网络、资源、脚本、容器、Android 权限和供应链等高风险范围的附加审查。
- 发布维护者负责正式构建、签名、制品校验和发布记录。
- 两项职责均需单独记录和授权，不因成为任务协调者、协作者或模块维护者而自动获得。

### 角色确认与调整

角色根据实际需要逐步确认，主要考虑：

- 持续贡献的正确性、可维护性和验证质量；
- 能否在实现前清楚说明消费场景与机制，并在变化时主动同步；
- 对架构边界、凭据、供应链和运行风险的理解；
- 审查、沟通和处理反馈是否稳定可靠；
- 项目是否确实需要其承担对应职责。

单次提交、提交数量、代码量、Issue 活跃度或社区称号本身，都不会自动产生仓库所有权、管理权、合并权、发布权、收益权或商业权益。角色变化应在可留存的公开记录或仓库权限设置中确认。

### 当前仓库边界

Kite 当前位于 GitHub 个人账号仓库。个人仓库只有所有者和 Collaborator 这类粗粒度权限，不能单独授予只管理任务而不写代码的 `Triage` 权限。因此：

- `Contributor` 和 `Task Coordinator` 可以先作为公开社区职责运行，不需要获得仓库写权限。
- 需要真正的 `Triage`、`Write`、`Maintain` 分级时，应先把仓库迁入 GitHub Organization，再按本文件映射权限。
- 在迁移前，不以授予个人仓库 Collaborator 权限代替低权限任务协调。

## English

### Governance principles

Kite uses public collaboration, pre-implementation disclosure, least privilege, and separation of duties:

- New work starts with an Issue that states the use case, expected outcome, implementation mechanism, scope, risk, and verification plan. Opening an Issue is disclosure, not a request for prior maintainer approval.
- Contributors may claim work and implement it in their own forks. Material mechanism changes must be reflected in the original Issue first.
- Pull Requests are the formal admission and code-review gate. Passing automated checks does not require the project to merge a contribution.
- Community roles are based on sustained responsibility, collaboration quality, and security awareness—not lines of code, commit counts, or one contribution.
- Task coordination, code writing, review, merge, release, and security administration are separate duties. One duty does not automatically grant another.
- Repository and external-system access follows least privilege and may be adjusted or revoked when no longer needed or when risk changes.

### Collaboration flow

1. **Describe or claim**: open a new `[共建]` Issue or claim an existing Issue.
2. **Disclose the approach**: state the use case, expected outcome, implementation mechanism, scope, risk, and verification plan.
3. **Implement independently**: no prior approval is required; work in a personal fork and dedicated branch.
4. **Keep the proposal current**: update the Issue before material mechanism changes; keep one behavior boundary per Pull Request.
5. **Formal review**: link the Pull Request to its Issue and complete direction, architecture, provenance, security, code, and verification review.
6. **Merge and release**: changes merge only after branch protections and responsible maintainer review are satisfied; merge does not imply immediate release.

### Task classification and status

Issues use a small, stable taxonomy so status does not have to be inferred from titles:

- Type: `bug`, `enhancement`, `documentation`, and `question`.
- Status: `status: proposal` (approach being described), `status: ready` (claimable), `status: claimed` (claimed), `status: blocked` (a concrete blocker exists), and `status: review` (a linked Pull Request is under review).
- Risk: `risk: sensitive` marks authentication, credentials, network, resources, scripts, containers, Android permissions, build, and release work that needs additional security review.
- Area: add `area:` labels only when task volume requires them; do not create one-off labels for a single Issue.

Task Coordinators may organize labels, recommend assignees, track claims, and follow up on inactive tasks. They cannot promise that a feature will enter the mainline, decide architecture boundaries alone, approve code, merge, or release. While Kite remains a personal repository, Task Coordinators propose classification and assignment in Issue comments, and the repository owner performs actions that require repository permissions.

### Community roles

| Role | Responsibility | Default technical access |
| --- | --- | --- |
| `Community Member` | Use Kite, join discussions, report problems, and suggest ideas | No additional access |
| `Contributor` | Describe and claim work, then contribute code, documentation, tests, or design through a fork | No additional access |
| `Task Coordinator` | Organize and recommend tasks, help classify work, track claims and blockers, and facilitate public discussion | May map to `Triage` in an organization; no automatic write access while the repository is personal |
| `Collaborator` | Repeatedly implement well-scoped work and, when needed, create development branches in the upstream repository | May map to `Write` in an organization; all changes still use Pull Requests |
| `Module Maintainer` | Maintain a defined area, review related changes, and preserve its contracts and tests | `Write` or `Maintain`, with review scope defined through `CODEOWNERS` |
| `Project Maintainer` | Coordinate across modules, maintain governance, and review or merge eligible changes | `Maintain`; no automatic admin or external-secret access |
| `Project Owner` | Own project direction, repository administration, sensitive access, and final governance responsibility | `Admin` |

`Security Reviewer` and `Release Maintainer` are specialist duties rather than ordinary promotion levels:

- Security Reviewers provide additional review for authentication, credentials, networks, resources, scripts, containers, Android permissions, and supply-chain changes.
- Release Maintainers handle official builds, signing, artifact verification, and release records.
- Both duties require separate, recorded authorization. They are not granted automatically to Task Coordinators, Collaborators, or Module Maintainers.

### Role appointment and adjustment

Roles are assigned gradually when the project needs them, based primarily on:

- sustained correctness, maintainability, and verification quality;
- clear pre-implementation explanation of use cases and mechanisms, with proactive updates when plans change;
- understanding of architecture, credential, supply-chain, and runtime boundaries;
- reliable review, communication, and response to feedback;
- an actual project need for the responsibility.

A single contribution, contribution count, code volume, Issue activity, or community title does not create repository ownership, administrative authority, merge or release rights, revenue rights, or commercial interests. Role changes must be recorded in a durable public record or repository access settings.

### Current repository constraint

Kite is currently owned by a GitHub personal account. Personal repositories provide coarse owner and Collaborator access and cannot grant a standalone `Triage` role for task management without code write access. Therefore:

- `Contributor` and `Task Coordinator` may operate as public community responsibilities without repository write access.
- If the project later needs real `Triage`, `Write`, and `Maintain` separation, it should first move to a GitHub Organization and then apply this mapping.
- Before such a move, personal-repository Collaborator access must not be used as a substitute for low-risk task coordination.
