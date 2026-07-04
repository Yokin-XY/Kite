# Kite X11 1080P 与超级操作模式执行手册

> 本文件是本任务的唯一事实来源。对话里的进展只做同步，任务定义、验收标准和红线以本文档为准。

## 0. 开机自检

每次继续本任务前必须先做：

1. 读 `docs/x11-super-operation/PLAYBOOK.md` 全文。
2. 读 `docs/x11-super-operation/PROGRESS.md`。
3. 读 `docs/x11-super-operation/DECISIONS.md`。
4. 按 `PROGRESS.md` 重建对话内任务清单，再动代码。

每开始一个任务前，必须在 `PROGRESS.md` 写三问自检：

1. 目标是什么。
2. 完成标准是什么。
3. 前置任务是否完成。

## 1. 北极星目标

把 Kite 的内嵌 X11 surface 做成手机端可用的远程桌面体验：

- 固定 X11 逻辑桌面为标准 1080P，即 `1920x1080`。
- Android 手机屏幕只是观察这个固定桌面的视口。
- 操作模型参考向日葵手机 App 的远控体验：缩放、拖动画面、虚拟键盘、长按/自定义键等能力分阶段适配。
- 保持现有 `CardRunSurface.X11` 的卡片实例归属，不把 X11 做成独立远控产品，也不引入 VNC/noVNC/websockify 作为兜底。

## 2. 已确认代码证据

- `app/src/main/java/com/termux/x11/LorieView.java:85`：`updateViewport()` 是当前显示策略入口。
- `app/src/main/java/com/termux/x11/LorieView.java:89`：当前调用 `X11ViewportPlan.fitLandscapeDesktop(width, height)`。
- `app/src/main/java/com/termux/x11/LorieView.java:101`：当前通过 `setViewport(...)` 把 Android 视口映射到 native X11。
- `app/src/main/java/com/termux/x11/LorieView.java:110`：当前通过 `sendWindowChange(...)` 通知 X11 逻辑桌面尺寸。
- `app/src/main/java/com/termux/x11/LorieView.java:170`：当前触摸逻辑是单指按下即左键、移动即鼠标移动、抬起即释放。
- `app/src/main/java/com/termux/x11/LorieView.java:271`：当前固定桌面是 `1280x720`。
- `app/src/test/kotlin/com/termux/x11/X11ViewportPlanTest.kt:9`：测试已锁定 `1280x720` 的视口行为。
- `app/src/main/java/com/kite/app/MainActivity.kt:9418`：`cardRunX11SurfaceBody(...)` 把 X11 surface 作为 CardRun 的一个可见面挂载。

## 3. 红线

- 不猜接口。所有改动必须依赖真实代码、测试或官方资料。
- 不把“超级操作模式”臆造成一个不存在的单一功能。公开资料里若只有触摸模式、指针模式、自定义键盘、长按交互等名称，文档要如实映射。
- 不通过 `showCardRunSurface(...)` 的普通刷新来解决 X11 操作问题。
- 不在 render/bind 路径做探测、扫描、数据库读写或网络检查。
- 不复制 `CardRunStore` 的运行事实到页面本地状态。
- 用户可见改动必须尽量上 1+8T 真机验证，至少要有构建、安装、截图或 logcat 证据。

## 4. 任务梯队

### T0 建立任务三件套

- 问题证据：用户明确指定按 `docs/AUTONOMOUS_TASK_PROTOCOL.md` 的任务模式执行。
- 解法：新建本目录三件套，记录任务拆解、进度和决策。
- 验收标准：
  - [x] `PLAYBOOK.md` 存在。
  - [x] `PROGRESS.md` 存在。
  - [x] `DECISIONS.md` 存在。
  - [x] 当前任务主线、边界、验收和红线写入文件。
- 依赖：无。

### T1 搜集向日葵手机 App 操作模式并整理文档

- 问题证据：用户要求“搜集向日葵手机APP的超级操作模式，然后整理成文档，然后把它做成任务”。
- 解法：
  - 优先使用贝锐/向日葵官方页面、App Store 页面等公开资料。
  - 把用户口径“超级操作模式”映射到资料中真实出现的远控方式、触摸模式、指针模式、自定义键盘、长按交互、虚拟键盘等能力。
  - 输出 `docs/x11-super-operation/向日葵超级操作模式调研.md`。
- 验收标准：
  - [ ] 文档包含至少 3 个可追溯来源。
  - [ ] 文档区分“官方资料明确写到”和“适配到 Kite 的推断”。
  - [ ] 文档给出 X11 分阶段适配清单。
  - [ ] 未找到官方“超级操作模式”直称时，明确说明命名不确定。
- 依赖：T0。

### T2 建立 X11 1080P 视口基线

- 问题证据：当前 X11 固定逻辑桌面是 `1280x720`，不满足用户“固定分辨率为1080P”。
- 解法：
  - 把 `X11ViewportPlan` 的固定逻辑桌面改为 `1920x1080`。
  - 保留按 Android 容器等比缩放、居中显示的视口策略。
  - 更新单元测试，使测试锁定 `1920x1080` 的布局。
- 验收标准：
  - [ ] `X11ViewportPlan` 逻辑桌面为 `1920x1080`。
  - [ ] `sendWindowChange(...)` 使用 `1920x1080`。
  - [ ] 竖屏和横屏容器的视口计算测试通过。
  - [ ] 不新增整页刷新、轮询或运行状态副本。
- 依赖：T1 完成初版调研，T0 完成。

### T3 适配手机远控式缩放与拖动画面

- 问题证据：当前 `onTouchEvent(...)` 把单指事件直接当远端鼠标事件，没有手机远控常见的双指缩放、视口拖动能力。
- 解法：
  - 在 `LorieView` 内维护“视口相机”状态：缩放倍率、源区域左上角、源区域宽高。
  - 双指捏合改变相机倍率。
  - 双指拖动平移相机。
  - 单指继续保留远端鼠标左键/移动/释放的基础能力。
  - 输入坐标按当前相机源区域反向映射到固定 `1920x1080` 桌面。
- 验收标准：
  - [ ] 视口相机不会超出固定桌面边界。
  - [ ] 双指缩放后，输入映射仍落在 `1920x1080` 内。
  - [ ] 双指拖动不向远端发送误触鼠标点击。
  - [ ] 单指点击/拖动原有鼠标行为保留。
  - [ ] 有单元测试覆盖相机缩放、平移、边界夹取和坐标映射。
- 依赖：T2。

### T4 构建、安装和真机验证

- 问题证据：AGENTS.md 要求 Kite 用户可见改动能上真机就上真机，默认设备是 1+8T `3f8bbaad`。
- 解法：
  - 按工具链规则运行最小测试与 APK 构建。
  - 安装到 1+8T。
  - 打开一个 X11 资源或临时 X11 步骤，截图验证 1080P 视口和基础触摸行为。
  - 检查 logcat 中 X11、InputDispatcher、ANR、崩溃相关错误。
- 验收标准：
  - [ ] `:app:testDebugUnitTest` 通过。
  - [ ] `:app:assembleDebug` 成功。
  - [ ] APK 安装到 `3f8bbaad` 成功。
  - [ ] 真机截图能看到 X11 surface。
  - [ ] logcat 无本次改动引入的崩溃、ANR 或输入异常。
- 依赖：T3。

### T5 修正 X11 手机远控三问题

- 问题证据：用户指出上一版不合格：初始状态没有满足 1080P 在手机屏幕上的渲染效果；上方白条/上半部分未自动隐藏；缩放只是双指响应，单指无法在非内容/边缘过渡区域挪动画面；实例容易闪退。
- 解法：
  - 初始视口从“完整包含”改为“主轴铺满”：横屏宽度铺满，竖屏高度铺满。
  - 对横屏高度溢出的场景默认向上裁切，避免上方白条占据远控内容可见区。
  - 单指触摸改成阈值模型：轻点才发送远端点击；移动超过阈值且相机可平移时，单指拖动画面；双指继续负责缩放。
  - X11 服务启动失败不能直接把 `:x11` 服务进程打崩；需要保留失败原因并让主进程得到可解释失败。
- 验收标准：
  - [ ] 竖屏初始视口高度等于可见高度，远端桌面左右裁切，不再小框悬浮。
  - [ ] 横屏初始视口宽度等于可见宽度，高度溢出时默认隐藏上方溢出区域。
  - [ ] 单指轻点仍能发送远端点击。
  - [ ] 单指移动超过阈值且视口可平移时，拖动画面而不是误发远端鼠标拖动。
  - [ ] 双指缩放后仍可用单指继续平移到边界内任意位置。
  - [ ] X11 Java/Kotlin 启动失败以失败状态返回，不因 `require/check` 直接崩掉 `:x11` 服务进程。
  - [ ] 相关单元测试和构建通过。
- 依赖：T2、T3。

### T6 解耦手势相机与 X11 桌面尺寸通知

- 问题证据：用户反馈“锚定锁定只完成了 30%”并继续指出多次手动缩放后实例仍会崩溃；同时明确缩放不应影响底层分辨率变化，应像远控软件一样只改变手机端观察视口。
- 解法：
  - 保持 X11 逻辑桌面固定为 `1920x1080`。
  - 将手势缩放、拖动画面限制为 `LorieView` 本地视口相机更新。
  - `sendWindowChange(...)` 只在首次或真实桌面尺寸/刷新率变化时发送，不随每个手势 MOVE 发送。
  - 避免在手势热路径重复调用与桌面尺寸无关的 renderer 全局设置。
- 验收标准：
  - [ ] 双指缩放和单指拖动画面不触发高频 `sendWindowChange(...)`。
  - [ ] 首次 surface 初始化仍会向 X11 通知 `1920x1080`。
  - [ ] 固定 1080P 桌面不因缩放/平移改变。
  - [ ] 不新增整页刷新、轮询、运行状态副本或渲染时重型探测。
  - [ ] 定向单元测试和 APK 构建通过。
- 依赖：T2、T3、T5。

### T7 改用 native source-rect 相机并修复初始铺满/四向拖动

- 问题证据：用户截图显示当前竖屏初始为黑屏，桌面内容位于画布外侧；手动挪动后内容仍贴在左侧，且纵向锚定在上方无法向下挪动；用户反馈本轮仍崩溃一次。
- 解法：
  - 不再用负坐标的大目标矩形模拟裁剪。
  - `setViewport(...)` 只接收 Android surface 内的非负绘制矩形，遵循 Termux:X11 native 的真实语义。
  - 使用 `setRendererZoom(percent)` 触发 native source-rect 裁剪和回传，让输入映射跟随 native 的 `setRendererViewport(...)`。
  - 初始状态按主轴铺满计算 renderer zoom；横屏宽度铺满，竖屏高度铺满。
  - 单指拖动画面时不发送点击，通过移动远端鼠标到 source-rect 边缘带来驱动 native panning，允许缩放后上下左右移动。
  - 构建安装后抓取近期崩溃/ANR/Xlorie 日志；若真机手动复测仍崩，继续以日志栈为依据处理。
- 验收标准：
  - [ ] `setViewport(...)` 不再收到负 `x/y` 或超出 Android surface 的目标矩形。
  - [ ] 竖屏初始不再因为中心裁切空桌面而全黑不可发现。
  - [ ] 横屏初始宽度铺满，竖屏初始高度铺满。
  - [ ] 缩放后单指拖动画面能驱动 native source-rect 在上下左右边界内移动。
  - [ ] 输入映射使用 native 回传 source rect，不再使用 Java 自造的负目标矩形映射。
  - [ ] 相关单元测试、APK 构建和 1+8T 安装通过。
- 依赖：T2、T3、T5、T6。

### T8 文件管理资源卡片 X11 生命周期与自适应初始视口

- 问题证据：用户在 `kite.pcmanfm.x11` 文件管理资源卡片真机复测中确认：X11 画面会跳回 SH 报告；初始比例过大，桌面被放到可见画布外；上下拖动无效；点击映射异常。
- 解法：
  - 保留资源卡片通过 shell 桌面代理启动 X11 的现有机制，不切换成全局 X11 模式。
  - 当 `/open-desktop` 已把同一运行实例交给 `CardRunSurface.X11` 后，后续 shell progress/result 只能补充运行绑定和报告文本，不能把 surface 覆盖回 `Report`。
  - 初始视口改为远控式自动适配：固定 X11 桌面仍为 `1920x1080`，Android 端初始完整显示桌面；双指放大后才进入 source-rect 裁切和平移。
  - 触摸映射以当前可见 viewport/source rect 为准，黑边区域不发送远端点击；缩放后单指移动用于平移观察窗口。
- 验收标准：
  - [x] 文件管理资源通过 shell proxy 打开 X11 后不再被同一步 shell 结果顶回 SH 报告。
  - [x] 初始状态不再过度放大，`1920x1080` 桌面完整自动适配到手机可见区域。
  - [x] 点击只在 X11 可见 viewport 内映射到远端桌面，黑边不误触远端。
  - [ ] 双指放大后单指拖动能在 source rect 边界内上下左右平移。
  - [x] 定向单测、资源启动约束测试、构建、1+8T 安装通过。
  - [x] 真机复测文件管理资源后抓取 logcat，确认未见本次链路引入的崩溃、ANR 或 X11 native 错误。
- 依赖：T2、T3、T5、T6、T7。

### T9 PCManFM 首屏 1080P 客户端窗口约束

- 问题证据：用户最新真机截图显示 X11 surface 仍在，但 PCManFM 文件管理器是左侧小窗，首屏大面积黑屏；真机实例配置中 `~/.config/pcmanfm/default/pcmanfm.conf` 保存为 `win_width=640`、`win_height=480`。
- 解法：
  - 不再把 PCManFM 小窗当成 Android 视口公式问题继续调。
  - 保持 X11 逻辑桌面固定 `1920x1080`。
  - 在 `kite.pcmanfm.x11` 资源 open 命令里启动前写入 PCManFM 窗口尺寸配置。
  - 用 PCManFM `--new-win` 打开新窗口，避免复用旧小窗。
  - 安装 `xdotool` 并在窗口出现后执行 `windowmove 0 0`、`windowsize 1920 1080` 作为 X11 内兜底。
  - 构建安装后在 1+8T 打开资源，截图确认首屏文件管理窗口为大窗口，并检查 logcat。
- 验收标准：
  - [x] PCManFM 配置变为 `win_width=1920`、`win_height=1080`。
  - [x] 文件管理资源首屏不再显示 640x480 小窗。
  - [x] X11 surface 不回 SH 报告。
  - [x] 真机截图显示 PCManFM 大窗口在可见 X11 桌面带内。
  - [x] 相关单测、构建、安装通过。
  - [x] logcat 未见本次链路引入的崩溃、ANR 或 X11 native 错误。
- 依赖：T8。

### T10 双线隔离后的远控交互研究基线

- 问题证据：用户在 2026-07-04 明确要求 X11 线独立绑定魅族手机，并研究透向日葵远程以及其他远程软件手机版控制电脑的操作逻辑，再长期开发 Kite X11。
- 解法：
  - X11 线使用物理副本 `D:\xm\Kite-x11-remote-control` 和分支 `codex/x11-remote-control`。
  - 默认设备改为 MEIZU 18 `181QGEYH222B9`，和浏览器线的一加设备隔离。
  - 继续维护本目录三件套，不新建平行 X11 事实来源。
  - 调研向日葵、ToDesk、TeamViewer、AnyDesk、RustDesk、Microsoft Remote Desktop 等手机远控电脑的公开操作逻辑。
  - 输出远控交互矩阵：观察视口、触控/鼠标模式、单指/双指手势、键盘、长按/右键、滚轮、边缘滚动、工具栏、断线恢复、错误反馈。
- 验收标准：
  - [ ] `docs/parallel-workstreams/README.md` 写明 X11 目录、分支、设备和端口。
  - [ ] X11 后续会话确认当前目录为 `D:\xm\Kite-x11-remote-control`。
  - [ ] ADB 目标固定为 `181QGEYH222B9`，所有命令带 `-s 181QGEYH222B9`。
  - [ ] 远控交互调研文档包含至少 5 个可追溯来源。
  - [ ] 调研文档区分“竞品资料明确写到”和“适配到 Kite X11 的推断”。
  - [ ] 输出分阶段 X11 实现任务，不用单次大改重写。
- 依赖：T9。

### T11 魅族设备上的 X11 长跑验证基线

- 问题证据：用户要求 X11 后续全程由 Codex 用 ADB 自己测试、引导和截图，并长期运行观察稳定性。
- 解法：
  - 在魅族设备上建立可重复的构建、安装、打开 X11 资源、截图、logcat、进程存活和前台 Activity 检查流程。
  - host 转发端口使用 `18792 -> 8791`，避免和浏览器线冲突。
  - 先验证现有 T9 基线在魅族设备上的表现，再继续做交互逻辑开发。
- 验收标准：
  - [ ] `adb -s 181QGEYH222B9 devices -l` 可确认目标在线。
  - [ ] APK 构建和安装到魅族设备成功。
  - [ ] X11 资源能在魅族设备打开并截图。
  - [ ] 至少完成一次长时间运行检查，包含进程、前台 Activity、截图和 logcat 摘要。
  - [ ] 发现崩溃、ANR、输入异常或 X11 native 错误时，以日志栈为依据建后续任务，不写特判。
- 依赖：T10。

## 5. 当前压力分类

- Primary lane：UI Binding + X11 native surface。
- State owner：`CardRunStore` 继续拥有运行实例事实；`LorieView` 只拥有本地视口相机状态。
- Pressure risk：触摸事件高频、surface 尺寸变化、native viewport 更新。
- Hot path touched：`LorieView.onTouchEvent(...)`、`LorieView.updateViewport()`。
- Forbidden broad refresh avoided：不改 `showCardRunSurface(...)` 普通刷新路径，不改资源页刷新，不改 CardRun 运行状态模型。

## 6. 双线隔离绑定

- X11 线后续物理目录：`D:\xm\Kite-x11-remote-control`。
- X11 线建议分支：`codex/x11-remote-control`。
- X11 线默认设备：MEIZU 18 `181QGEYH222B9`。
- X11 线本机调试端口：`18792 -> 8791`。
- 浏览器登录线的设备和端口由 `docs/browser-login/` 维护；X11 线不得占用浏览器线的一加设备和 `18791` host 端口。
