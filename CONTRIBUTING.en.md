# Contributing to Kite

[中文](CONTRIBUTING.md) | English | [Community Governance](GOVERNANCE.md#english)

Kite currently prioritizes stabilizing existing capabilities. Before making changes, check [Feature Status](docs/reference/feature-status.md) to determine whether the target is on the stable path or remains experimental. Browser automation and X11 changes must not alter stable-path defaults.

## Collaboration model and permission boundaries

Kite welcomes problem reports, design discussions, documentation, tests, and code contributions. The default public collaboration model is `Fork + Pull Request`; ordinary contributions require no write access to the upstream repository.

1. Before starting a new feature, fix, test, or documentation change, open or claim a public Issue. Existing tasks can be claimed without opening a duplicate.
2. The Issue must state the real use case, expected outcome, implementation mechanism, scope, risk, and verification plan. Update the Issue before any material mechanism change so code review can compare the implementation with the disclosed approach.
3. Opening an Issue is pre-implementation disclosure, not a request for maintainer approval. Once the direction is clear, contributors may claim it and implement it in their own forks. Maintainers and community members may point out duplication, conflicts, architecture boundaries, or security risk early.
4. Each Pull Request must address one explainable behavior boundary and link to the corresponding Issue. Formal direction, provenance, dependency, permission, execution-surface, code, and verification review happens at the Pull Request stage. Automated checks do not replace human review.
5. Maintainers decide whether to merge, request changes, or decline a Pull Request. Official builds, signing, and releases are performed only by explicitly authorized maintainers.
6. Community identities, responsibilities, appointment criteria, and GitHub permission mappings are defined in [Community Governance](GOVERNANCE.md#english). Roles depend on sustained quality, collaboration, security awareness, and actual responsibility—not one contribution or a numeric threshold.

Community roles describe responsibilities; they do not change rights established by the project license, contributor agreement, or other written terms. Task coordination, code maintenance, security review, and release are separate duties, and one does not automatically grant another.

The main branch does not accept unreviewed direct pushes. Even contributors with upstream write access must use Pull Requests for functional changes to `main`.

## Contribution authorization

Before submitting code, documentation, resource manifests, designs, or other material, read the [Kite Contributor License Agreement](CONTRIBUTOR_LICENSE_AGREEMENT.md). By submitting a Pull Request and checking the authorization confirmation, a contributor agrees to that agreement. The linked Chinese text is currently the governing version.

Contributors retain copyright in their original contributions while granting the project the long-term rights needed to maintain, modify, publish, commercially use, and relicense them. Community roles, dedicated maintenance responsibilities, and commercial arrangements are governed separately.

Because the current project license does not generally permit modification, the contributor agreement includes a narrow contribution-preparation permission: it permits copying and modifying Kite in a GitHub fork only to prepare a contribution for the official Kite repository. It does not authorize independent redistribution, commercial use, or republishing Kite as another project.

If an employer, customer, or other organization may own a contribution, the contributor must first confirm their authority to submit it. The source and applicable license of third-party code, assets, models, data, or generated content must be disclosed in the Pull Request.

## Development flow

1. Confirm from the real entry point whether the problem belongs to Feature, Application, Platform, or Foundation.
2. UI submits actions and consumes `UiState`; it must not duplicate business facts or perform heavyweight probes.
3. The state owner confirms final results. Ordinary state changes update only the relevant controls.
4. Add the narrowest meaningful unit test or machine check for behavior changes. Validate user-visible flows on a supported physical device whenever practical.
5. Keep each commit within one explainable behavior boundary and exclude screenshots, APKs, logs, and unrelated formatting.

The following are high-risk changes and require separate threat, provenance, rollback, and verification disclosure in the Pull Request:

- `.github/workflows/`, build scripts, Gradle configuration, and release flows;
- resource cards, manifests, download locations, dependency versions, and install or uninstall scripts under `assets/`;
- Shell, terminal, PRoot, bridges, Agent processes, authentication, credentials, networks, and file access;
- permissions, exported components, intents, providers, services, and receivers in `AndroidManifest.xml`;
- signing, keys, releases, automatic updates, and any capability that can change external state.

Do not submit backdoors, hidden telemetry, undisclosed network access, credential collection, permission bypasses, persistent residue, or special cases designed only to pass the current review. Resource downloads and third-party scripts must disclose their true source. Pin versions, commits, or digests when possible; otherwise explain why and state the residual risk.

See [Architecture Overview](docs/architecture/overview.md) and [State and Lifecycle](docs/architecture/state-and-lifecycle.md) for architecture and lifecycle rules.

## Common checks

```powershell
.\scripts\run-kite-tests.ps1 -Profile Full
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
powershell -File scripts/KITE_ARCHITECTURE_CHECKS.ps1
powershell -File scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1
```

Parse resource manifests as JSON before validating them against the [Resource Manifest Contract](docs/reference/resource-manifest.md). Home cards follow the [Home Card Schema](docs/reference/home-card-schema.md).

## Documentation and artifacts

- Root `README.md` contains only stable project information and entry points. Detailed material belongs in `docs/architecture/`, `docs/guides/`, or `docs/reference/`; see the [Documentation Index](docs/README.md).
- Versions, APKs, checksums, and release notes belong only in GitHub Releases. GitHub Actions is the source of truth for current main-branch status.
- Maintain one source of truth for each fact and link to it elsewhere instead of duplicating versions, capability status, protocols, or task progress.
- Active cross-session task files belong under `docs/tasks/<task>/`. Extract stable conclusions and remove temporary workflow files when the task finishes.
- APKs, screenshots, logcat output, diagnostic JSON, and temporary reports belong in ignored `local-artifacts/`; release packages belong in GitHub Releases.
