# Kite Toolchain Reference

Last verified: 2026-06-14.

This file is the local contract for building and deploying Kite from
`D:\xm\Kite`. Read it before APK build, APK install, or OnePlus 8T deployment.
Keep it factual and command-oriented; do not use it as a progress log.

## Scope

- Project root: `D:\xm\Kite`
- Main Android module: `:app`
- Local terminal module: `:terminal-view-local`
- Application id installed on device: `com.kite.app`
- Android namespace: `com.kftest.app`
- Launcher activity: `com.kite.app.MainActivity`
- Card instance activity: `com.kite.app.CardRunActivity`

Kite-owned Ubuntu-side helper applets are built from this workspace and packaged
under `assets/system`. Do not use Android NDK output for these applets unless a
task explicitly asks for an Android/Bionic binary.

## WSL Host Network

The active WSL distro for Kite native helper work is:

```text
Ubuntu-24.04
```

The Windows-side WSL config is:

```text
C:\Users\19437\.wslconfig
```

Verified host-network settings on 2026-06-14:

```ini
[wsl2]
networkingMode=mirrored
autoProxy=true
dnsTunneling=true
firewall=true
```

`networkingMode=mirrored` makes WSL follow the host network shape, including
host VPN/TUN virtual adapters. `autoProxy=true` imports the Windows system
proxy. On this machine the active Windows system proxy is:

```text
http://127.0.0.1:7897
```

Inside WSL, `127.0.0.1:7897` is reachable and should be preferred for this
proxy shape. `host.docker.internal:7897` refused connections during
verification.

Reapply the host-network bridge, WSL sudo policy, and apt proxy detector:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\configure-wsl-host-network.ps1
```

The script:

- updates `.wslconfig`
- backs up the previous `.wslconfig` as `.wslconfig.kf-backup-<timestamp>`
- configures `/etc/sudoers.d/99-kf-wsl-nopasswd`
- installs `/usr/local/bin/kf-host-proxy-detect`
- installs `/etc/profile.d/kf-host-proxy.sh`
- installs `/etc/apt/apt.conf.d/99kf-host-proxy`
- restarts WSL so `.wslconfig` takes effect

Do not add `localhostForwarding=true` for the current mirrored setup; WSL 2.6
warned about that key when mirrored networking was enabled.

After configuration, these checks should pass:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "wslinfo --networking-mode"
wsl -d Ubuntu-24.04 -- bash -lc "sudo -n true && echo sudo_nopasswd_ok"
wsl -d Ubuntu-24.04 -- bash -lc "/usr/local/bin/kf-host-proxy-detect"
wsl -d Ubuntu-24.04 -u root -- bash -lc "apt-get update"
```

Expected highlights:

```text
mirrored
sudo_nopasswd_ok
http://127.0.0.1:7897
Reading package lists...
```

## Build

Run from `D:\xm\Kite`:

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output:

```text
D:\xm\Kite\app\build\outputs\apk\debug\app-debug.apk
```

Expected successful install artifact on 2026-06-11:

```text
app-debug.apk
```

## Ubuntu Native Applets

These binaries run inside the Ubuntu/PRoot workspace, not in Android's JNI
runtime.

Packaged asset directory:

```text
D:\xm\Kite\assets\system
```

Installed container path:

```text
/workspace/.kf/system/bin
```

Existing process applet:

```text
assets/system/kf-procps-arm64
```

`kf-procps-arm64` is a static AArch64 Linux executable and is copied into the
container as `ps`, `pgrep`, `pkill`, `kill`, `pidof`, `pstree`, `free`, `top`,
`kf-resource-sampler`, `systemctl`, and `service`.

Runner applet source and output:

```text
native/kf-runner/kf-runner.c
assets/system/kf-runner-arm64
```

Build the runner from Windows through WSL:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-kf-runner.ps1
```

The build script expects `Ubuntu-24.04` to have an ARM64 Linux cross compiler:

```bash
sudo apt-get install -y gcc-aarch64-linux-gnu libc6-dev-arm64-cross
```

On this Windows host, the active VPN proxy was verified from WSL through
`127.0.0.1:7897`. `host.docker.internal:7897` refused connections, so prefer
WSL localhost for this proxy shape. If the toolchain package is missing, install
it through the proxy from Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install-kf-runner-toolchain.ps1
```

The helper runs apt as the WSL root user so it does not hang on a `sudo`
password prompt, and injects the proxy only for that command.

After `configure-wsl-host-network.ps1` has been applied, direct root apt
commands can use the WSL apt auto-detect config:

```powershell
wsl -d Ubuntu-24.04 -u root -- bash -lc "apt-get update"
```

Verify the generated asset:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "file /mnt/d/xm/Kite/assets/system/kf-runner-arm64"
wsl -d Ubuntu-24.04 -- bash -lc "readelf -h /mnt/d/xm/Kite/assets/system/kf-runner-arm64 | grep -E 'Class|Type|Machine'"
```

Expected format:

```text
ELF 64-bit LSB executable, ARM aarch64, statically linked
Machine: AArch64
```

## PRoot / Ubuntu Command Ledger

Use these commands as the known-good Kite/KF Ubuntu-side maintenance path:

```powershell
# Confirm WSL network inheritance and sudo/proxy setup.
powershell -ExecutionPolicy Bypass -File .\tools\configure-wsl-host-network.ps1

# Install or repair the ARM64 Linux cross compiler inside WSL.
powershell -ExecutionPolicy Bypass -File .\tools\install-kf-runner-toolchain.ps1

# Build the Ubuntu/PRoot runner applet.
powershell -ExecutionPolicy Bypass -File .\tools\build-kf-runner.ps1

# Verify the runner asset format.
wsl -d Ubuntu-24.04 -- bash -lc "file /mnt/d/xm/Kite/assets/system/kf-runner-arm64"
wsl -d Ubuntu-24.04 -- bash -lc "readelf -h /mnt/d/xm/Kite/assets/system/kf-runner-arm64 | grep -E 'Class|Type|Machine'"

# Build and install Kite after asset changes.
.\gradlew.bat assembleDebug
adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Container-side managed helper location:

```text
/workspace/.kf/system/bin
```

Kite currently installs `kf-runner` there from:

```text
assets/system/kf-runner-arm64
```

## Device Targets

Primary deployment target:

```text
serial: 3f8bbaad
model:  KB2000 / OnePlus 8T
```

Secondary device currently seen on this machine:

```text
serial: 181QGEYH222B9
model:  MEIZU 18
```

Do not install to the secondary device unless the user explicitly asks.

The active Windows ADB resolved during verification was:

```text
D:\KF\Android\Sdk\platform-tools\adb.exe
Android Debug Bridge 37.0.0-14910828
```

`C:\Users\19437\scoop\shims\adb.exe` may also exist in PATH, but `adb version`
should report the real installed platform-tools path above.

## Deploy To OnePlus 8T

Check devices:

```powershell
adb devices -l
```

Install the current debug APK:

```powershell
adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The expected success line is:

```text
Success
```

On the OnePlus 8T, ADB install may create an Android package installer
confirmation prompt. If the command appears to hang, check the phone and confirm
the install. Verify the new app by querying `com.kite.app`; older test builds
may still have the legacy package `com.kftest.app` installed and should not be
confused with the current APK.

## Optional Runtime Checks

Use these only when the task calls for launch/runtime verification:

```powershell
adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity
adb -s 3f8bbaad shell pidof com.kite.app
adb -s 3f8bbaad shell dumpsys activity activities
```

Filter `dumpsys` output locally with `Select-String` if needed.

## Visual QA Boundary

- If the user asks to push/install, build and install to `3f8bbaad`.
- If the user says not to screenshot or says they will inspect manually, do not
  take screenshots and do not open visual inspection tools.
- Do not treat "do not screenshot" as "do not install".

## Git Hygiene

- Do not use `git add .` for checkpoint commits.
- Add explicit files only.
- Do not commit build outputs, installed APKs, runtime state, tokens, device
  dumps, or local screenshots unless the user explicitly asks.
- Existing ignored screenshot patterns include `/kite-*.png`,
  `/card-run-*.png`, and `/terminal-*-check.png`.

## Maintenance

Update this file in the same turn when any of these facts change:

- APK path or Gradle build task
- package id, launcher activity, or instance activity
- default deployment device serial
- ADB platform-tools path
- install or verification command
