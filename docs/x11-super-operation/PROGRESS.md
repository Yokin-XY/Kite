# Kite X11 1080P 与超级操作模式进度

最后更新：2026-07-04 双线隔离基线

## 当前状态总览

| 任务 | 状态 | 备注 |
| --- | --- | --- |
| T0 建立任务三件套 | done | 三件套已建立 |
| T1 向日葵操作模式调研文档 | done | 调研文档已写入，命名不确定性已说明 |
| T2 X11 1080P 视口基线 | done | 已改为 `1920x1080`，定向单测通过 |
| T3 手机远控式缩放与拖动画面 | done | 视口相机与双指手势已实现，定向单测通过 |
| T4 构建安装真机验证 | blocked | 测试、构建、安装已通过；X11 surface 未能在当前设备运行时打开 |
| T5 修正 X11 手机远控三问题 | done | 初始化、单指拖动画面、沉浸式显示和实例稳定性已修正并真机验证 |
| T6 解耦手势相机与 X11 桌面尺寸通知 | done | 缩放/拖动已与 X11 桌面尺寸通知解耦，测试、构建、安装通过 |
| T7 native source-rect 相机与铺满/四向拖动 | blocked | 代码已替换并通过测试/构建/安装/X11 进程验证；当前设备容器缺 GUI 客户端，文件窗口视觉复测需用户触发 |
| T8 文件管理资源 X11 生命周期与自适应视口 | done | 已修复 SH 报告回退和初始自动适配；用户复测证明 PCManFM 客户端自身仍是默认小窗，T9 继续 |
| T9 PCManFM 首屏 1080P 客户端窗口约束 | done | 已将 PCManFM 启动约束到 `1920x1080`，真机截图显示首屏大窗口，X11 surface 保持运行 |
| T10 双线隔离后的远控交互研究基线 | pending | 后续 X11 副本中执行，绑定 MEIZU 18 `181QGEYH222B9` |
| T11 魅族设备上的 X11 长跑验证基线 | pending | 依赖 T10，使用 host 端口 `18792` |

状态取值：`pending` / `in_progress` / `blocked` / `done`

## 待验证清单

- [x] `:app:testDebugUnitTest`
- [x] `:app:assembleDebug`
- [x] 1+8T `3f8bbaad` 安装
- [x] X11 surface 真机截图
- [x] PCManFM 首屏大窗口真机截图
- [x] X11 单指滑动压力与可见路径验证
- [x] 滑动压力后的崩溃、ANR、InputDispatcher、Xlorie 日志复核
- [x] logcat 初步检查崩溃、ANR、InputDispatcher、X11 native 错误
- [ ] X11 线物理目录为 `D:\xm\Kite-x11-remote-control`
- [ ] X11 线分支为 `codex/x11-remote-control` 或用户确认的等价分支
- [ ] MEIZU 18 `181QGEYH222B9` 长跑验证基线完成

## 任务日志

### T10/T11 [pending] 双线隔离与魅族长跑基线

三问自检：

1. 目标：把 X11 后续任务从当前基线中拆到独立物理目录、独立分支和魅族设备上，并把远控交互研究和长跑验证作为下一阶段入口。
2. 完成标准：`PLAYBOOK.md` 新增 T10/T11；`docs/parallel-workstreams/README.md` 写明 X11 目录、设备、端口；后续 X11 会话只维护本目录三件套。
3. 前置任务：T9 已完成，当前仅做基线准备，不开始实现。

已完成：

- X11 线后续物理目录记录为 `D:\xm\Kite-x11-remote-control`。
- X11 线建议分支记录为 `codex/x11-remote-control`。
- X11 线绑定设备记录为 MEIZU 18 `181QGEYH222B9`。
- X11 线 host 转发端口记录为 `18792 -> 8791`。

本次不做：

- 不复制物理目录。
- 不创建分支或会话。
- 不启动 X11 实现改动。
- 不替代后续真实竞品资料调研。

### T9 [done] PCManFM 首屏 1080P 客户端窗口约束

三问自检：

1. 目标：按用户最新截图和反馈，解决文件管理资源首屏仍是小窗口、看起来黑屏、窗口在画布左侧/外侧且无法直接使用的问题。
2. 完成标准：PCManFM 资源启动时客户端窗口本身按固定 `1920x1080` 桌面约束为大窗口；打开后不依赖用户手动拖动；仍保持底层 X11 分辨率固定、手机端缩放只改变观察视口；构建、安装、真机打开、截图和 logcat 检查完成。
3. 前置任务：T8 已证明资源能稳定停留在 `CardRunSurface.X11`，但没有约束 X11 客户端窗口尺寸；T9 在资源启动层修复，不回头改指导文档，不切换全局 X11 模式。

压力检查：

- 主要通道：资源 manifest open shell + `kite-open-desktop` + X11 native surface。
- 状态拥有者：`CardRunStore` 继续拥有运行实例事实；PCManFM 配置只影响资源自己的 X11 客户端窗口。
- 触及热路径：不改 `LorieView.onTouchEvent(...)` 手势热路径，不改页面刷新路径。
- 禁止改法：不把 PCManFM 小窗当成 Android 视口问题继续硬调；不写死截图坐标；不回滚用户已有改动。

已确认真实依据：

- 用户最新截图中 X11 surface 没有回到 SH 报告，但 PCManFM 客户端窗口仍是小窗口，因此当前主失败点已从 CardRun surface 生命周期转为 X11 客户端窗口约束。
- 1+8T 当前实例真实配置 `/root/.config/pcmanfm/default/pcmanfm.conf` 为 `win_width=640`、`win_height=480`，与截图中的小窗尺寸吻合。
- PCManFM man page 明确默认配置目录为 `~/.config/pcmanfm/default/pcmanfm.conf`，并支持 `-n, --new-win` 打开新窗口。

实现摘要：

- `kite.pcmanfm.x11` 安装依赖增加 `xdotool`，用于 X11 内窗口归位/缩放兜底。
- PCManFM open 命令在交给 `kite-open-desktop` 前构造桌面命令：启动前写入 `win_width=1920`、`win_height=1080`、`splitter_pos=240`。
- PCManFM 启动改用 `--no-desktop --new-win /workspace`，避免复用旧小窗。
- 如果容器中有 `xdotool`，启动后搜索 PCManFM 窗口并执行 `windowmove 0 0` 与 `windowsize 1920 1080`，同时输出窗口 geometry 供报告/日志复核。
- 补充资源启动约束单测，锁定 `xdotool`、`win_width/win_height`、`--new-win` 和窗口 move/size 逻辑仍存在。

验收：

- [x] PCManFM 配置在真机实例中变为 `win_width=1920`、`win_height=1080`。
- [x] 文件管理资源打开后 PCManFM 首屏不再是 640x480 小窗。
- [x] 当前运行实例保持 `CardRunSurface.X11`，不回 SH 报告。
- [x] 真机截图显示 PCManFM 大窗口位于可见 X11 桌面带内。
- [x] 相关单测、构建、安装通过。
- [x] 打开和触摸压力后 logcat 未见崩溃、ANR、InputDispatcher 或 Xlorie 关键错误。

验证：

- `ConvertFrom-Json assets/resources/kite.pcmanfm.x11/manifest.json`：JSON 可解析，`install` 包含 `xdotool`，`open` 包含 `win_width=$KITE_X11_DESKTOP_WIDTH`。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.kite.app.resources.KiteVscodeResourceLaunchTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：Success。
- `adb -s 3f8bbaad shell am start ... runtime_action start_resource_install ... kite.pcmanfm.x11`：资源安装完成；报告中确认新增安装 `libxdo3 xdotool`，`/usr/bin/xdotool` 存在。
- `adb -s 3f8bbaad shell am start ... runtime_action start_resource_open ... kite.pcmanfm.x11`：资源打开 Intent 已送达。
- 真机 PCManFM 配置：`/root/.config/pcmanfm/default/pcmanfm.conf` 已由 `win_width=640`、`win_height=480` 改为 `win_width=1920`、`win_height=1080`，`splitter_pos=240`。
- `shared_prefs/kite_card_run_store.xml`：当前 `resource-kite.pcmanfm.x11-open` 为 `ownerKind="x11"`、`status="Running"`、`surface="X11"`、`x11Display=":123"`。
- 进程状态：`pidof com.kite.app` 与 `pidof com.kite.app:x11` 均有输出，前台为 `com.kite.app/com.kite.app.CardRunActivity`。
- 真机截图：`docs/screenshots/kite-x11-t9-pcmanfm-window.png`，PCManFM 横向铺满可见 X11 桌面带，不再是左侧 640x480 小窗。
- ADB 单指上/下/左/右滑动压力 8 轮后：`com.kite.app` 与 `com.kite.app:x11` 仍存活，前台仍为 `CardRunActivity`。
- 压力后截图：`docs/screenshots/kite-x11-t9-after-swipes.png`，PCManFM 大窗口保持可见，未回 SH 报告。
- `logcat -d -v time` 过滤 `AndroidRuntime|FATAL EXCEPTION|ANR in|InputDispatcher|Xlorie|Fatal signal|native X11|KiteX11|crash|signal 11|signal 6`：仅见 `KiteX11ServerService` 启动普通记录，未见崩溃、ANR、输入异常或 Xlorie fatal。

残余说明：

- ADB 标准 `input` 工具只支持单指 `tap/swipe/motionevent`，不能可靠注入真实双指 pinch；本轮没有为了验证硬塞新的 instrumentation 框架。缩放后的上下左右 source-rect 边界继续由 `X11ViewportPlanTest` 覆盖，真机侧已完成首屏和单指滑动稳定性验证。

### T8 [done] 文件管理资源 X11 生命周期与自适应初始视口

三问自检：

1. 目标：按文件管理资源卡片真机反馈，解决 X11 surface 跳回 SH 报告、初始比例过大、上下拖不动、点击映射异常和缩放后稳定性问题。
2. 完成标准：同一资源实例交给 X11 后不被 shell progress/result 改回 Report；初始完整自动适配 `1920x1080` 桌面；黑边不误触；放大后单指可平移；定向测试、构建、安装和 logcat 检查完成。
3. 前置任务：T2/T3/T5/T6/T7 已提供固定 1080P、手势相机和 native source-rect 基础，但 T7 的主轴铺满策略与真机文件管理场景不匹配，本任务改为初始自动适配。

压力检查：

- 主要通道：CardRun 状态所有权 + X11 native surface + LorieView 触摸热路径。
- 状态拥有者：`CardRunStore` 继续拥有运行实例事实；shell 桌面代理交接到 X11 后，shell 回调不能夺回 surface。
- 触及热路径：`handleShellProgress(...)`、`handleSequenceShellResult(...)`、`LorieView.onTouchEvent(...)`、`X11ViewportPlan`。
- 禁止改法：不切换成全局 X11 模式，不用整页刷新掩盖回退，不改指导文件，不写死文件管理资源单一 case。

已确认真实依据：

- `kite.pcmanfm.x11` 资源启动路径是 shell step 调用 `KITE_DESKTOP_PROXY/kite-open-desktop`，不是 `type: x11` step。
- `/open-desktop` 会把同一 `instanceId` 的 `CardRunStore` 状态更新为 `CardRunSurface.X11`。
- 当前 shell progress/result 路径会继续写 `CardRunSurface.Report`，并在单步 recipe 结束时进入报告页，这是 X11 surface 回退到 SH 报告的直接链路。
- T7 测试锁定了初始主轴裁切放大；用户最新真机反馈证明文件管理资源需要初始自动适配而不是初始裁切放大。

实现摘要：

- `handleShellProgress(...)` 增加 X11 handoff 识别：同一步 shell 已经被 `/open-desktop` 交给 X11 后，后续进度只更新运行绑定和报告文本，保持 `CardRunSurface.X11`。
- `handleSequenceShellResult(...)` 增加 X11 handoff 保持逻辑：detached shell 返回 running/accepted 时不再推进到 recipe 完成，也不再把单步资源卡片改回 `Report`。
- `X11ViewportPlan.fitLandscapeDesktop(...)` 初始回到完整 `1920x1080` 自动适配，renderer zoom 为 `100`；双指放大后才裁切 source rect。
- `CameraState` 的触摸相对坐标改为基于实际绘制 viewport，而不是整个 Android view，避免黑边参与点击映射。
- 单指轻点只在 X11 可见 viewport 内发送远端点击；黑边区域不再误触远端桌面。
- 任务文档补充 T8 和 ADR-010/ADR-011，明确不改指导文档、不切换全局 X11 模式。

验收：

- [x] 文件管理资源通过 shell proxy 打开 X11 后不再被同一步 shell 结果顶回 SH 报告。
- [x] 初始状态不再过度放大，`1920x1080` 桌面完整自动适配到手机可见区域。
- [x] 点击只在 X11 可见 viewport 内映射到远端桌面，黑边不误触远端。
- [x] 定向单测、资源启动约束测试、CardRunStore 状态测试、构建、1+8T 安装通过。
- [x] 真机文件管理资源启动后抓取 logcat，未见 `AndroidRuntime`、`FATAL EXCEPTION`、`ANR` 或本次链路相关 X11 native 崩溃。
- [ ] 双指放大后的单指上下左右平移需要用户在真机手势场景补充复测；ADB 当前未完成可靠多指 pinch 注入。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.kite.app.resources.KiteVscodeResourceLaunchTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.kite.app.run.CardRunStoreStateTransitionTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `git diff --check -- app/src/main/java/com/kite/app/MainActivity.kt app/src/main/java/com/termux/x11/LorieView.java app/src/test/kotlin/com/termux/x11/X11ViewportPlanTest.kt docs/x11-super-operation/PLAYBOOK.md docs/x11-super-operation/PROGRESS.md docs/x11-super-operation/DECISIONS.md`：无空白错误；仅提示 LF/CRLF 转换。
- `adb -s 3f8bbaad install -r app\build\outputs\apk\debug\app-debug.apk`：Success。
- `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity --es runtime_action start_resource_open --es com.kite.app.extra.RESOURCE_INSTALL_TARGET_ID kite.pcmanfm.x11`：资源打开 Intent 已送达。
- 前台窗口：`com.kite.app/com.kite.app.CardRunActivity`，Intent 指向 `kite://card-run/resource-kite.pcmanfm.x11-open`。
- 进程状态：`pidof com.kite.app` 和 `pidof com.kite.app:x11` 均有输出。
- `shared_prefs/kite_card_run_store.xml`：当前 `resource-kite.pcmanfm.x11-open` 为 `ownerKind="x11"`、`status="Running"`、`surface="X11"`、`x11Display=":123"`。
- 真机截图：`docs/screenshots/kite-x11-t8-pcmanfm-resource.png`，可见 PCManFM 在自动适配后的 `1920x1080` 桌面带内，不在画布外；当前为完整桌面适配，因此竖屏存在上下黑边。
- `logcat -d -v time '*:E'`：未见 `AndroidRuntime`、`FATAL EXCEPTION`、`ANR`；仅见系统性能/厂商日志、`Xlorie: Initialized EGL version 1.5`、`window changed: 1920 1080 kite` 等信息。
- ADB 连续发送上/下/左/右四组单指 `input swipe` 后：`com.kite.app` 与 `com.kite.app:x11` 仍存活，前台仍为 `CardRunActivity`，清空后的错误日志未匹配到崩溃、ANR、InputDispatcher、Xlorie 或 fatal signal。

### T7 [blocked] native source-rect 相机与铺满/四向拖动

三问自检：

1. 目标：按用户截图和本轮反馈，修复竖屏初始黑屏、内容位于画布外、纵向锁在上方无法下挪、缩放后仍崩溃的问题。
2. 完成标准：`setViewport(...)` 不再收到负 `x/y` 或超出 Android surface 的目标矩形；初始状态按主轴铺满；输入映射使用 native 回传 source rect；缩放后单指拖动画面能驱动 native source-rect 上下左右移动；相关单元测试、构建和 1+8T 安装通过。
3. 前置任务：T2、T3、T5、T6 已完成，但 T6 的负目标矩形仍不符合 native 真实语义，本任务替换相机模型。

压力检查：

- 主要通道：UI Binding + X11 native surface + Diagnostics。
- 状态拥有者：`CardRunStore` 继续拥有运行实例事实；`LorieView` 只拥有本地 renderer zoom、目标绘制矩形和手势瞬态。
- 触及热路径：`LorieView.updateViewport()`、`applyViewport(...)`、`handleCameraGesture(...)`、`panCameraFromSingleTouch(...)`。
- 禁止改法：不调用 `showCardRunSurface(...)`，不刷新资源页，不复制运行状态，不在 render/bind 路径做扫描，不隐藏崩溃。

已确认真实依据：

- Termux:X11 upstream `LorieView.updateViewport()` 将 `setViewport(...)` 用作 Android surface 内的非负绘制矩形；不是负坐标大画布裁剪。
- Termux:X11 native `rendererSetViewport(...)` 保存目标绘制矩形；`rendererRedrawLocked(...)` 用 `setRendererZoom(...)` 后的 source rect 绘制，并通过 `setRendererViewport(...)` 回传输入映射。
- 本地 `libXlorie.so` 字符串中存在 `rendererSetZoom`、`reportRendererViewport`、`setRendererViewport`、`rendererSetViewport`，与 upstream 机制一致。
- 1+8T 当前未见 `com.kite.app`/`:x11` 存活进程；最近 1500 行 logcat 未抓到本次崩溃栈。

实现摘要：

- `LorieView.applyViewport(...)` 改为向 `setViewport(...)` 发送非负目标绘制矩形，不再发送负坐标或超出 Android surface 的大矩形。
- `X11ViewportPlan` 改为同时计算目标绘制矩形、source rect 和 `rendererZoomPercent`；初始铺满通过 native `setRendererZoom(...)` 实现。
- 输入映射先使用计划 source rect，并继续接受 native `setRendererViewport(...)` 回传的真实 source rect。
- 双指缩放只更新 renderer zoom/source rect，不通知 X11 桌面尺寸变化。
- 单指拖动画面不发送点击，改为无按键鼠标移动到 source rect 边缘带，以驱动 native panning。
- `X11ViewportPlanTest` 重写为验证非负目标矩形、竖屏/横屏主轴铺满 source rect、缩放焦点稳定、source 边界夹取和最大缩放。

验收：

- [x] `setViewport(...)` 不再收到负 `x/y` 或超出 Android surface 的目标矩形。
- [x] 横屏初始宽度铺满，竖屏初始高度铺满。
- [x] 缩放后单指拖动画面能计算并驱动 native source-rect 在上下左右边界内移动。
- [x] 输入映射使用 native 回传 source rect，不再使用 Java 自造的负目标矩形映射。
- [x] 相关单元测试、APK 构建和 1+8T 安装通过。
- [ ] 竖屏初始不再因为中心裁切空桌面而全黑不可发现。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：Success。
- `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity`：MainActivity 可启动。
- `/status`：`{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `/open-desktop`：accepted=true，`display=:58`，`error=""`。
- 进程状态：`pidof com.kite.app` 和 `pidof com.kite.app:x11` 均有输出。
- 前台窗口：`com.kite.app/com.kite.app.CardRunActivity`。
- 截图：`docs/screenshots/kite-x11-t7-validation.png`，可见 X11 surface 与 cursor；本次验证命令未确认 GUI 客户端可用，因此截图不作为窗口铺满最终证据。
- 过滤 logcat：未见 `AndroidRuntime`、`FATAL EXCEPTION`、`ANR`、`InputDispatcher`、`KiteX11`/`Xlorie` 关键错误；仅见 `gles-renderer` surface/window 更新日志。
- `git diff --check -- app/src/main/java/com/termux/x11/LorieView.java app/src/test/kotlin/com/termux/x11/X11ViewportPlanTest.kt`：无空白错误；仅提示 LF/CRLF 转换。
- 设备容器检查：未找到 `pcmanfm`、`thunar`、`nautilus`、`nemo`、`xterm`、`xclock`、`xeyes`、`xmessage`、`leafpad`、`mousepad`、`lxterminal`、`xfce4-terminal` 等可直接用于视觉复测的 GUI 客户端。
- ADB 单指拖动压力验证：在当前 X11 surface 上执行上/下/左/右四组 `input swipe` 后，`pidof com.kite.app` 和 `pidof com.kite.app:x11` 均仍有输出，过滤 logcat 未见崩溃、ANR 或输入异常。

残余说明：

- 当前截图仍为黑底，是因为验证命令里的 GUI 客户端不可确认，不能证明用户提供的文件窗口场景已视觉通过。需要用户在同一文件窗口场景手动复测：竖屏初始、横屏初始、双指缩放、单指上下左右拖动、连续缩放是否崩溃。
- 如果复测仍崩溃，下一步必须在清空 logcat 后立即复现并抓取 `AndroidRuntime`/native crash 栈，不再继续仅凭截图调整几何公式。

### T6 [done] 解耦手势相机与 X11 桌面尺寸通知

三问自检：

1. 目标：按用户本轮要求，让缩放/拖动画面只改变手机端本地视口相机，不改变固定 `1920x1080` X11 桌面，也不在手势热路径高频通知窗口尺寸变化。
2. 完成标准：双指缩放和单指拖动画面不触发高频 `sendWindowChange(...)`；首次 surface 初始化仍通知 `1920x1080`；固定 1080P 桌面不因缩放/平移改变；不新增整页刷新、轮询、运行状态副本或渲染时重型探测；定向单元测试和 APK 构建通过。
3. 前置任务：T2、T3、T5 已完成，本任务只收敛手势热路径和稳定性压力，不扩展到菜单或新远控工具栏。

压力检查：

- 主要通道：UI Binding + X11 native surface。
- 状态拥有者：`CardRunStore` 继续拥有运行实例事实；`LorieView` 只拥有本地视口相机和 native viewport。
- 触及热路径：`LorieView.applyViewport(...)`、`handleCameraGesture(...)`、`panCameraFromSingleTouch(...)`。
- 禁止改法：不调用 `showCardRunSurface(...)`，不刷新资源页，不复制运行状态，不隐藏 native/X11 异常。

实现摘要：

- `LorieView.updateViewport()` 在初始化或真实 surface/尺寸变化时调用 `applyViewport(..., true)`。
- 手势路径中的 `applyViewport(...)` 默认只更新本地 renderer viewport 和输入映射，不再通知 X11 桌面尺寸变化。
- `sendWindowChangeIfNeeded(...)` 缓存最后一次发送的桌面宽高和刷新率，重复的 `1920x1080` 通知会被跳过。
- `setRendererZoom(100)` 改为 surface 生命周期内只在需要时设置一次，surface 重建时重置。

验收：

- [x] 双指缩放和单指拖动画面不触发高频 `sendWindowChange(...)`。
- [x] 首次 surface 初始化仍会向 X11 通知 `1920x1080`。
- [x] 固定 1080P 桌面不因缩放/平移改变。
- [x] 不新增整页刷新、轮询、运行状态副本或渲染时重型探测。
- [x] 定向单元测试和 APK 构建通过。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `adb devices -l`：默认 1+8T `3f8bbaad` 在线。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：Success。
- `git diff --check -- app/src/main/java/com/termux/x11/LorieView.java docs/x11-super-operation/PLAYBOOK.md docs/x11-super-operation/PROGRESS.md docs/x11-super-operation/DECISIONS.md`：无空白错误；仅提示该 Java 文件下次 Git 触碰时 LF/CRLF 转换。

残余说明：

- 崩溃压力的根因目前按用户提出的模型先收敛为“缩放不应改变底层 X11 桌面尺寸”。本轮已去掉高频窗口尺寸通知，但“多次手动缩放后是否还崩”仍需要真机人工手势复测。

### T5 [done] 修正 X11 手机远控三问题

三问自检：

1. 目标：按用户反馈修正上一版不足，解决初始视口不铺满、上方白条/上半部分未隐藏、单指无法拖动画面、X11 实例启动失败容易表现为闪退这三个问题。
2. 完成标准：竖屏初始高度铺满、横屏初始宽度铺满并默认隐藏上方溢出；轻点仍发远端点击，单指移动可拖动画面，双指缩放后可继续单指平移；X11 Java/Kotlin 启动失败返回可解释失败而不是 `:x11` 服务进程直接崩；相关测试和构建通过。
3. 前置任务：T2、T3 已完成初版，但验收标准不足，本任务作为修正任务继续。

压力检查：

- 主要通道：UI Binding + X11 native surface + Diagnostics。
- 状态拥有者：`CardRunStore` 继续拥有运行实例事实；`LorieView` 只拥有本地视口相机和手势瞬态。
- 触及热路径：`LorieView.updateViewport()`、`LorieView.onTouchEvent(...)`、`KiteX11SurfaceServer.ensureStarted(...)`、`KiteX11ServerService.startFromIntent(...)`。
- 禁止改法：不用 `showCardRunSurface(...)` 做普通显示刷新，不复制运行状态，不隐藏异常。

实现摘要：

- `X11ViewportPlan.fitLandscapeDesktop(...)` 从完整包含改为主轴铺满：竖屏高度铺满并左右裁切，横屏宽度铺满并默认隐藏上方溢出区域。
- `LorieView` 单指输入改为阈值模型：轻点发送远端点击；移动超过阈值且相机可平移时拖动画面；无平移空间时才保留远端鼠标拖动。
- `LorieView` 聚焦后主动隐藏软键盘，避免 X11 初始画面被 IME 压缩。
- `CardRunActivity` 的 X11 surface 进入沉浸式系统栏隐藏，离开 X11/Activity 暂停或销毁时恢复。
- `KiteX11ServerService` 捕获 Java/Kotlin 启动失败并通过 Binder 暴露错误；`KiteX11SurfaceServer` 绑定后读取错误，避免 `require/check` 直接打崩 `:x11` 服务进程。
- `/open-desktop` 响应增加 `error` 字段，失败时能返回可解释原因。

验收：

- [x] 竖屏初始视口高度等于可见高度，远端桌面左右裁切，不再小框悬浮。
- [x] 横屏初始视口宽度等于可见宽度，高度溢出时默认隐藏上方溢出区域。
- [x] 单指轻点仍能发送远端点击。
- [x] 单指移动超过阈值且视口可平移时，拖动画面而不是误发远端鼠标拖动。
- [x] 双指缩放后仍可用单指继续平移到边界内任意位置。
- [x] X11 Java/Kotlin 启动失败以失败状态返回，不因 `require/check` 直接崩掉 `:x11` 服务进程。
- [x] 相关单元测试和构建通过。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:testDebugUnitTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：Success。
- `/status`：`{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。
- `/open-desktop`：accepted=true，`display=:88`，`error=""`。
- 真机截图：`docs/screenshots/kite-x11-t5-bars.png`，X11 surface 前台显示，系统栏和软键盘未占用显示区域。
- 进程状态：`pidof com.kite.app` 和 `pidof com.kite.app:x11` 均有输出。
- 前台窗口：`com.kite.app/com.kite.app.CardRunActivity`。
- 过滤 logcat：未见 `AndroidRuntime`、`FATAL EXCEPTION`、`ANR`、`InputDispatcher`、`KiteX11`/`native X11` 关键错误。

残余说明：

- 本轮真机用 `sleep 600` 启动 X11 surface，因此截图验证的是 surface、系统栏、软键盘和实例存活；当前容器仍缺真实 GUI 客户端，具体桌面内容的视觉裁切需要后续用实际 X 客户端/资源复核。

### T4 [blocked] 构建、安装和真机验证

三问自检：

1. 目标：对本轮 X11 用户可见改动跑完整验证闭环。
2. 完成标准：`testDebugUnitTest` 通过，`assembleDebug` 成功，APK 安装到 1+8T `3f8bbaad`，真机能看到 X11 surface，logcat 无本次改动引入的崩溃/ANR/输入异常。
3. 前置任务：T3 已完成。

已确认工具链：

- 已读 `D:\xm\Kite\references\toolchain.md`。
- debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 应用包名：`com.kite.app`。
- 默认设备：1+8T `3f8bbaad`。

已完成验证：

- `.\gradlew.bat :app:testDebugUnitTest --console=plain`：BUILD SUCCESSFUL。
- `.\gradlew.bat :app:assembleDebug --console=plain`：BUILD SUCCESSFUL。
- `adb devices -l`：1+8T `3f8bbaad` 在线，同时还有一台 MEIZU_18 在线；本轮只安装到 1+8T。
- `adb -s 3f8bbaad install -r .\app\build\outputs\apk\debug\app-debug.apk`：Success。
- `adb -s 3f8bbaad shell am start -n com.kite.app/com.kite.app.MainActivity`：MainActivity 可启动。
- `adb forward tcp:18791 tcp:8791` 后访问 `/status`：返回 `{"ok":true,"app":"Kite","version":"0.3","server":"running"}`。

X11 surface 打开验证：

- 已确认 `KiteLocalServer` 的 `/open-desktop` 会走 `acceptDesktopOpenRequest(...)`，再调用 `KiteX11SurfaceServer.ensureStarted(...)` 启动原生嵌入式 X11 surface。
- 当前 1+8T 容器 `files/runtime/containers/ubuntu-main/rootfs` 存在。
- 首次检查时 `rootfs/usr/share/X11/xkb` 不存在，`KiteX11SurfaceServer.ensureStarted(...)` 的前置条件不满足。
- 当前容器中没有 `apt-get`，也没有 `xterm`、`xclock`、`xeyes`、`xmessage`、`xsetroot`、`pcmanfm`、`lxterminal`、`xcalc` 等可用 X 客户端。
- 为排除 XKB 缺失阻塞，临时从本机 WSL Ubuntu-24.04 打包 `/usr/share/X11/xkb`，通过 `adb push` 和 `run-as com.kite.app` 解包到 1+8T 对应 rootfs。
- XKB 临时补齐后再次调用 `/open-desktop?title=X11%201080P%20Validation`，body 使用 `sleep 600`，HTTP 结果为 `The response ended prematurely. (ResponseEnded)`。
- 失败后 `pidof com.kite.app` 仍有主进程，`pidof com.kite.app:x11` 无输出，前台仍停留在 MainActivity，未进入 CardRunActivity。
- 失败后本地 `/status` 一度超时，说明 `/open-desktop` 启动链路可能卡住或提前断开。
- 近段 `logcat '*:E'` 未抓到直接 `AndroidRuntime` 崩溃；`files/runtime/logs/kftest.log` 主要是容器 bootstrap 重复检查和 timezone fallback，没有直接 X11 service 成功启动证据。

阻塞结论：

- 本轮代码改动已经完成测试、构建、安装验证。
- 真机可视化截图和触摸实测还未完成，阻塞点在当前 1+8T 的 X11 运行时底座：XKB 原本缺失、容器缺少包管理器和 GUI/X 客户端，临时补齐 XKB 后 `:x11` 进程仍未拉起。
- 继续完成 T4 需要一个已具备 X11 依赖和可启动 GUI 客户端的容器/资源，或继续单独排查 `KiteX11ServerService` 启动失败链路。

### T3 [done] 手机远控式缩放与拖动画面

三问自检：

1. 目标：在 `LorieView` 内加入手机远控式视口相机，支持双指缩放和双指拖动画面。
2. 完成标准：相机边界不超出 `1920x1080`；双指手势不误发鼠标点击；单指鼠标行为保留；有单元测试覆盖缩放、平移、边界夹取和坐标映射。
3. 前置任务：T2 已完成。

下一步：

- 已完成，进入 T4。

实现摘要：

- `LorieView` 增加本地视口相机状态，不改 `CardRunStore` 和 `showCardRunSurface(...)`。
- 双指手势只调整 X11 renderer viewport，不发送远端鼠标误触。
- 单指鼠标按下、移动、释放行为保留。
- `X11ViewportPlan.CameraState` 覆盖缩放、平移、边界夹取和坐标映射。

验收：

- [x] 视口相机不会超出固定桌面边界。
- [x] 双指缩放后，输入映射仍落在 `1920x1080` 内。
- [x] 双指拖动不向远端发送误触鼠标点击。
- [x] 单指点击/拖动原有鼠标行为保留。
- [x] 有单元测试覆盖相机缩放、平移、边界夹取和坐标映射。

验证：

- 第一次 `X11ViewportPlanTest` 失败：Kotlin 测试对 Java 方法使用了命名参数。
- 已修复为位置参数。
- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。

压力检查：

- State owner preserved：`CardRunStore` 保持运行状态所有者。
- Whole-page redraw added：no。
- Render-time heavy probe added：no。
- Terminal/Web/report behavior preserved：未触碰相关路径。

### T2 [done] X11 1080P 视口基线

三问自检：

1. 目标：把 X11 固定逻辑桌面从 `1280x720` 改为标准 1080P `1920x1080`。
2. 完成标准：`X11ViewportPlan`、`sendWindowChange(...)` 和测试都锁定 `1920x1080`；竖屏/横屏容器视口计算测试通过；不新增整页刷新或运行状态副本。
3. 前置任务：T0、T1 已完成。

已确认真实入口：

- `app/src/main/java/com/termux/x11/LorieView.java` 内的 `X11ViewportPlan` 当前常量为 `1280x720`。
- `app/src/test/kotlin/com/termux/x11/X11ViewportPlanTest.kt` 当前测试断言 `1280x720`。

下一步：

- 已完成，进入 T3。

实现摘要：

- `X11ViewportPlan` 固定逻辑桌面从 `1280x720` 改为 `1920x1080`。
- `X11ViewportPlanTest` 更新桌面尺寸断言，并新增完整 `1920x1080` 容器测试。

验收：

- [x] `X11ViewportPlan` 逻辑桌面为 `1920x1080`。
- [x] `sendWindowChange(...)` 使用 `layout.desktopWidth/layout.desktopHeight`，因此随计划输出 `1920x1080`。
- [x] 竖屏和横屏容器的视口计算测试通过。
- [x] 不新增整页刷新、轮询或运行状态副本。

验证：

- `.\gradlew.bat :app:testDebugUnitTest --tests com.termux.x11.X11ViewportPlanTest --console=plain`：BUILD SUCCESSFUL。

### T1 [done] 向日葵操作模式调研文档

三问自检：

1. 目标：把用户口径“向日葵手机 App 的超级操作模式”整理成可追溯文档，并拆成 Kite X11 的适配任务。
2. 完成标准：文档包含可追溯来源，区分官方资料和 Kite 推断，给出分阶段适配清单，并说明“超级操作模式”命名不确定。
3. 前置任务：T0 已完成。

已完成：

- 搜索官方资料与公开 App 信息。
- 找到移动远控核心操作：单指点击、长按拖动、双指缩放、虚拟键盘。
- 找到超级会员长按交互、自定义键盘和触摸/指针模式相关资料。

下一步：

- 已完成，进入 T2。

验收：

- [x] 文档包含至少 3 个可追溯来源。
- [x] 文档区分“官方资料明确写到”和“适配到 Kite 的推断”。
- [x] 文档给出 X11 分阶段适配清单。
- [x] 未找到官方“超级操作模式”直称时，明确说明命名不确定。

### T0 [done] 建立任务三件套

三问自检：

1. 目标：按 `docs/AUTONOMOUS_TASK_PROTOCOL.md` 建立本任务的 `PLAYBOOK.md`、`PROGRESS.md`、`DECISIONS.md`。
2. 完成标准：三件套存在，任务主线、边界、验收和红线写入文件。
3. 前置任务：无。

验收：

- [x] `docs/x11-super-operation/PLAYBOOK.md`
- [x] `docs/x11-super-operation/PROGRESS.md`
- [x] `docs/x11-super-operation/DECISIONS.md`

启动证据：

- 已读 `docs/AUTONOMOUS_TASK_PROTOCOL.md`。
- 已读 `AGENTS.md`。
- 已读 `kite-runtime-pressure-lanes` 技能与必要压力/真机参考。
- 已检查当前 X11 真实入口和测试。
