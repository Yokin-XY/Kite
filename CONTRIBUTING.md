# 参与开发

Kite 当前优先稳定已有能力。开始修改前，先在 [能力状态](docs/reference/feature-status.md) 中确认目标属于稳定主线还是实验能力；浏览器自动化和 X11 的改动不能改变稳定路径的默认行为。

## 开发流程

1. 从真实入口确认问题属于 Feature、Application、Platform 还是 Foundation。
2. 让页面提交动作并消费 UiState，不在页面复制业务事实或执行重型探测。
3. 让状态拥有者确认最终结果，普通状态变化只局部更新相关控件。
4. 为行为变化补最窄的单元测试或机器检查；用户可见流程尽量在 OnePlus 8T 真机验证。
5. 一个提交只承载一个可解释的行为边界，不混入截图、APK、日志和无关格式化。

架构与生命周期规则见 [架构总览](docs/architecture/overview.md) 和 [状态与生命周期](docs/architecture/state-and-lifecycle.md)。

## 常用检查

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
powershell -File scripts/KITE_ARCHITECTURE_CHECKS.ps1
powershell -File scripts/KITE_RUNTIME_LANE_STATIC_CHECKS.ps1
```

资源清单先做 JSON 解析，再按 [资源清单协议](docs/reference/resource-manifest.md) 检查依赖、验证和卸载边界。首页卡片按 [首页卡片 Schema](docs/reference/home-card-schema.md) 编写。

## 文档与产物

- 长期有效说明进入 `README.md`、`docs/architecture/`、`docs/guides/` 或 `docs/reference/`。
- 当前跨会话任务文件进入 `docs/tasks/<task>/`，任务完成后提炼结论并清理流水。
- APK、截图、logcat、诊断 JSON 和临时报告进入被忽略的 `local-artifacts/`；发布安装包进入 GitHub Releases。
