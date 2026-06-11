# Kite Toolchain Reference

Last verified: 2026-06-11.

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

For native PRoot / Ubuntu-side binary rebuilds, use the KFShell toolchain
reference in the KFShell workspace instead. This file is for Kite app build and
device deployment.

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
