# 验证方式

## 自动检查

完整单元测试和 Debug 构建：

```powershell
# 日常跨层护栏
.\scripts\run-kite-tests.ps1 -Profile Quick

# 阶段回归：Quick 加受影响模块
.\scripts\run-kite-tests.ps1 -Profile Stage -Tests 'com.kite.app.platform.resources.*'

# 父任务、合并和发布门
.\scripts\run-kite-tests.ps1 -Profile Full
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
```

不要把 Quick 或 Stage 写成发布全量。多个本地 worktree 必须通过上述包装器进入 Gradle；CI 运行在独立机器，继续使用工作流中的
原始全量命令。分层原则与锁边界见[测试执行分层与本机构建协调](../architecture/test-execution-profiles.md)。

Android Lint：

```powershell
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:lintDebug'
```

架构和运行车道护栏：

```powershell
powershell -File scripts/KITE_ARCHITECTURE_CHECKS.ps1
powershell -File scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1
```

## 真机范围

用户可见改动优先在 OnePlus 8T `3f8bbaad` 验证。一次完整稳定版检查至少覆盖：

1. 首次启动或覆盖安装后进入首页。
2. 首页卡片启动、返回、停止和再次启动。
3. 新建终端和网页窗口，分别关闭，再关闭整个实例。
4. 资源获取、失败或成功投影、打开和卸载。
5. WebView 本地页面与系统浏览器登录回跳。
6. 运行管理中的卡片、终端和进程在操作后自动收敛。
7. logcat 与 DropBox 没有新增崩溃、ANR 或输入超时。

实验 X11 和浏览器自动化不阻塞稳定版发布；只有明确领取对应实验任务时才纳入专项验收。

## 证据管理

测试源码和机器基线进入 Git。APK、截图、logcat、DropBox、诊断 JSON 和临时报告放在 `local-artifacts/`，需要发布的安装包进入 GitHub Releases。

“测试通过”必须来自真实命令或真机结果；静态字符串检查不能代替运行行为，单纯不崩溃也不能代替用户流程通过。
