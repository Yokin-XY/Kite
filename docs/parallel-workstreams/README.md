# Kite 双线隔离工作基线

最后更新：2026-07-04

## 目的

当前 `D:\xm\Kite` 只作为封口基线和后续合并来源。浏览器登录和 X11 远控操作是两条互相独立的长期任务线，后续应使用物理复制目录、独立分支、指定 ADB 设备和互不冲突的调试端口推进。

## 工作线分配

| 工作线 | 物理目录 | 建议分支 | 绑定设备 | ADB serial | 本机转发端口 | 任务事实文件 |
| --- | --- | --- | --- | --- | --- | --- |
| 浏览器登录回跳 | `D:\xm\Kite-browser-login` | `codex/browser-login-return` | OnePlus 8T | `3f8bbaad` | `18791 -> 8791` | `docs/browser-login/` |
| X11 远控操作 | `D:\xm\Kite-x11-remote-control` | `codex/x11-remote-control` | MEIZU 18 | `181QGEYH222B9` | `18792 -> 8791` | `docs/x11-super-operation/` |

## 隔离规则

- 每条线只在自己的物理目录里构建、测试和截图。
- 所有 ADB 命令必须带 `-s <serial>`，不得使用未指定设备的 `adb install`、`adb shell`、`adb forward`。
- 两台设备可以安装同一个 `com.kite.app` 包名，但本机端口转发不能复用同一个 host 端口。
- 浏览器线默认使用 OnePlus 8T：`3f8bbaad`。
- X11 线默认使用 MEIZU 18：`181QGEYH222B9`。
- `D:\xm\Kite` 在拆线后不继续做功能实现，只用于查看封口提交、生成副本和后续合并。
- 不用整页刷新、轮询、单点特判或伪造状态来掩盖任一任务线的问题。

## 后续启动顺序

1. 在 `D:\xm\Kite` 完成封口提交。
2. 从封口提交复制出 `D:\xm\Kite-browser-login` 和 `D:\xm\Kite-x11-remote-control`。
3. 在两个副本里分别创建或切换到对应分支。
4. 浏览器会话只读取并维护 `docs/browser-login/`。
5. X11 会话只读取并维护 `docs/x11-super-operation/`。
6. 两条线各自完成构建、安装、截图、日志验证后，再回到封口基线做合并判断。

## 当前设备实测

`adb devices -l` 在 2026-07-04 返回：

```text
181QGEYH222B9          device product:meizu_18_CN model:MEIZU_18 device:meizu18
3f8bbaad               device product:OnePlus8T_CH model:KB2000 device:OnePlus8T
```
