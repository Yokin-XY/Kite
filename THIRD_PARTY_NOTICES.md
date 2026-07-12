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
- `AGENTS.md`: project coding-agent guidance adapted from Ponytail
  (https://github.com/DietrichGebert/ponytail), MIT License, copyright
  (c) 2026 DietrichGebert.

The repository also contains local runtime control code for ADB,
Shizuku, PRoot, process lifecycle management, and Android/KF execution
boundaries. These components are intended for local development devices
and controlled runtime environments. Do not expose local bridge endpoints
or packaged runtime control surfaces to untrusted networks.

For commercial use of the original Kite project code, contact the
repository owner. For third-party components, review and comply with the
corresponding upstream licenses separately.
