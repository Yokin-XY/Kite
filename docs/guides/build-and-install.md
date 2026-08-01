# 构建与安装

## 环境要求

- Windows PowerShell 或兼容 Shell。
- JDK 17。
- Android SDK，能够编译 API 34。
- 需要真机验证时准备 ADB；项目默认测试设备为 OnePlus 8T。

本机 SDK 路径写入 `local.properties`，该文件不得提交到 Git。

## 构建 Debug APK

```powershell
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
```

输出文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

先执行单元测试再构建：

```powershell
.\scripts\run-kite-tests.ps1 -Profile Full
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
```

## ADB 安装

设备较多时必须指定 serial：

```powershell
adb -s 3f8bbaad install -r -g app/build/outputs/apk/debug/app-debug.apk
```

卸载重装会删除应用私有数据；覆盖安装会保留设置、资源登记和运行历史。验证首次安装问题时使用卸载重装，验证升级兼容时使用覆盖安装，两者不能互相替代。

## 首次启动

Kite 会检查并引导以下能力：

- 文件交换目录和共享卡片目录。
- 通知及首页卡片进度频道。
- 本地 Linux 运行环境。
- 安装 APK、忽略电池优化等按需能力。
- Shizuku/ADB 能力仅在对应功能需要时使用。

权限是否开启以 Android 系统为准，应用内页面只显示状态并打开系统设置，不伪装成可直接修改的开关。

## 版本配置

`versionCode`、`versionName`、`minSdk` 和 `targetSdk` 位于 `app/build.gradle`。修改正式版本前应先确认发布范围、升级行为和 GitHub Release 产物命名。

## 不进入 Git 的内容

APK、构建报告、真机截图、logcat、诊断文件和临时安装包统一放在 `local-artifacts/`。正式安装包上传 GitHub Releases，不提交到源码树。
