# Third-Party Notices

The root `LICENSE` file applies to the original Kite project code,
documentation, recipes, and integration work unless a file or bundled
component says otherwise. It does not relicense third-party software,
binary assets, operating-system images, or package archives included
for local development and device runtime use.

Known bundled third-party components retain their upstream license
terms:

- `app/src/main/assets/rootfs/ubuntu-base-24.04-arm64.tgz`: Ubuntu
  24.04 ARM64 base rootfs and packages. Ubuntu package licenses and
  Canonical/Ubuntu notices continue to apply.
- `assets/toolchain/ai-dev-pack/packages/node-v26.4.0-linux-arm64.tar.xz`:
  Node.js binary distribution and bundled npm dependencies.
- `assets/toolchain/ai-dev-pack/packages/pnpm-11.9.0.tgz`: pnpm package
  archive from npm.
- `assets/toolchain/ai-dev-pack/packages/cpython-3.14.6+20260623-aarch64-unknown-linux-gnu-install_only_stripped.tgz`:
  Python standalone build from Astral.
- `assets/toolchain/ai-dev-pack/packages/uv-aarch64-unknown-linux-gnu.tgz`:
  uv binary archive from Astral.
- `assets/proot/*`: packaged PRoot, loader, libtalloc, and KF patched
  PRoot assets. The current descriptor is `assets/proot/proot-runtime.json`;
  it identifies Termux PRoot fork sources and GPL-family licensing for
  the PRoot-family assets.
- `terminal-view-local/*`: local TerminalView-derived Android terminal
  view code. Upstream Termux/Android project license terms continue to
  apply where applicable.
- Gradle dependencies declared in `app/build.gradle` and
  `terminal-view-local/build.gradle.kts`, including AndroidX, Material,
  Shizuku, Kotlin/coroutines, and Apache Commons Compress, retain their
  own upstream license terms.
- `com.agentclientprotocol:acp:0.26.0`: official Agent Client Protocol
  Kotlin SDK, Apache License 2.0. The published POM, repository SPDX
  metadata, and repository license file identify Apache-2.0; an older
  README badge/paragraph still saying MIT is treated as stale documentation.
- `blue.endless:jankson:1.2.3`: JSON5/HJSON parser and writer used to
  preserve comments and field order in Agent-native JSONC configuration;
  MIT License. The published POM identifies the upstream repository as
  `falkreon/Jankson` and the developer as Isaac Ellingson.
- `org.yaml:snakeyaml:2.2`: YAML parser and emitter used to validate and
  update Hermes Agent native configuration; Apache License 2.0.
- `org.tomlj:tomlj:1.1.1`: TOML parser used to validate Codex native
  `config.toml` while Kite performs narrow text-preserving edits; Apache
  License 2.0. Its declared runtime dependencies retain their own terms.
- `codex-relay` 0.5.5: optional local Responses API to Chat Completions
  protocol bridge installed from PyPI for compatible Codex providers;
  MIT License, copyright (c) 2025 MetaFARS.
- `AGENTS.md`: project coding-agent guidance adapted from Ponytail
  (https://github.com/DietrichGebert/ponytail), MIT License, copyright
  (c) 2026 DietrichGebert.

The repository also contains local runtime control code for ADB,
Shizuku, PRoot, process lifecycle management, and Android/KF execution
boundaries. These components are intended for local development devices
and controlled runtime environments. Do not expose local bridge endpoints
or packaged runtime control surfaces to untrusted networks.

For commercial use, modification, redistribution, derivative works, or
incorporation of original Kite project code into another project, contact
the repository owner for written authorization. For third-party components,
review and comply with the corresponding upstream licenses separately.
