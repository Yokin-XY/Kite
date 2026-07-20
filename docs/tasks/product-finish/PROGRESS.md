# Kite 产品正式化进度

## 状态总览

| 任务 | 状态 | 当前结论 |
| --- | --- | --- |
| T001 | completed | 设置分类、类型、成熟度和目标合同已通过聚焦测试 |
| T002 | completed | 四组八入口、二级页骨架和返回/恢复合同已通过测试 |
| T003 | completed | 原七项设置已按类别迁移并保持原 Gateway/Effect |
| T004 | completed | 终端双入口共用偏好，浏览器自动化已移入实验页 |
| T005 | completed | 权限、运行、进程、日志和版本已通过类型化入口接入 |
| T006 | completed | 563 项测试、Lint、本地化、架构、压力和构建全绿 |
| T007 | completed | 八个二级页、语言、系统入口、终端同步、返回与压力均通过真机验收 |
| T008 | completed | 终端核心图标动作和长期设置架构文档均已完成最终门禁 |

## 当前恢复指针

- 当前任务：开发与本地验收已完成
- 当前动作：保持 `0.0.4` 开发版本和未提交工作树，等待用户决定是否进入 `0.0.8` 候选发布动作。
- 下一验证：若获发布授权，先改版本并重跑正式 Release 构建/签名/安装门，再 commit、push、tag 和 GitHub Release。
- Git 边界：允许修改工作树；不 commit、不 push、不改版本号、不发布。

## 2026-07-20 T001 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T001，先为设置分类、入口类型、稳定性和类型化目标建立可回归合同，不能直接从页面重写开始。
- 完成标准是什么：目录项唯一且可穷举；稳定/实验/Debug 可见性有测试；聚焦测试和空白检查通过。
- 依赖是否满足：T001 无前置依赖；当前 `main...origin/main` 干净，现有设置 Gateway、Controller、Screen 和导航测试可作为基线，依赖满足。

### 已确认事实

- `SettingsScreen.kt` 当前手工平铺主题、语言、浏览器、恢复现场、最近任务、通知和投放区。
- `SettingsGateway` 是应用级偏好与轻量系统事实的现有拥有者，不能扩张成运行/资源/终端总 Store。
- `TerminalUiPreferences` 持久化终端字号与主题，具有功能默认值属性。
- `BrowserRuntimeMode` 同时包含稳定系统认证面和实验自动化浏览器，需要在展示层分流。
- `StatusFragment`、`BridgeFragment` 没有正式导航引用；`LogActivity` 仅有 Manifest 注册。
- 容器网络遵循 Android 给 Kite 的默认网络，设置中心不得新增 VPN 复制或路由开关。

### 压力分诊

- 症状或功能：设置能力增加后仍需可查找、可返回、可轻量校准。
- 可见显示面：设置首页和设置二级页。
- 压力风险：把权限、运行时、资源或诊断探测塞入页面绑定；设置变化引发整页或运行显示面重建。
- 通道：UI Binding + Diagnostics 摘要；不进入 Card Run、Terminal Runtime 或 Web Surface 热路径。
- 状态拥有者：`SettingsGateway`、`TerminalUiPreferences`、`RuntimeBootstrapGateway`、`CardRunStore`、Android 权限系统各自保持不变。
- 事件来源：用户点击设置行、现有 Gateway 快照、系统设置返回后的显式刷新。
- 可见消费者：当前设置页内受影响的行或二级页面。
- 触及的热路径：设置 Screen/Fragment、导航合同、轻量快照投影。
- 禁止的大范围刷新：`showCardRunSurface(...)`、`refreshResourceScreenIfVisible()`、WebView reload、终端重连。
- 验证证据：目录单测、导航测试、压力扫描、全量构建、OnePlus 8T 截图和 FATAL/ANR 检查。

### T001 验收结果

- 新增 `SettingsCatalog`，固定四个用户目标分组、八个类型化二级目标、七种入口语义和稳定/实验/Debug 成熟度。
- 目录仅包含资源 ID 与导航元数据，没有 Context、Store、Gateway、文件、数据库或网络依赖。
- `SettingsCatalogTest` 覆盖目标穷举、ID 唯一、分组顺序、实验标记和 Debug 可见性。
- `:app:testDebugUnitTest --tests com.kite.app.feature.settings.SettingsCatalogTest`：`BUILD SUCCESSFUL`。
- `git diff --check` 通过，仅有工作树既有 LF/CRLF 提示，无空白错误。

T001 状态：completed。

## 2026-07-20 T002 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T002，把设置首页变成分类索引，并建立八个二级目标的类型化导航和返回合同。
- 完成标准是什么：四组八入口可见；二级页都返回设置；导航不会触碰终端、Web、运行显示面的生命周期。
- 依赖是否满足：T001 已建立完整目录和目标枚举并通过测试；T002 可以只搭页面/导航骨架，不提前迁移业务偏好，依赖满足。

### T002 验收结果

- 设置首页改为四组八入口的稳定目录，不再平铺具体开关。
- 新增统一 `SettingsCategoryFragment/Screen` 二级页骨架，页面只使用目录元数据和现有主题快照。
- `AppNavigator` 增加八个类型化设置子目标，统一返回设置首页，并支持直接恢复。
- `SettingsFeatureResultContract` 通过类型化分类名称路由，不使用页面字符串或单点 URL 特判。
- 设置目录、页面、结果合同和导航聚焦测试：`BUILD SUCCESSFUL`。
- `git diff --check` 通过，无空白错误。

T002 状态：completed。

## 2026-07-20 T003 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T003，把原设置页七项能力按用户目标迁入二级页，同时修复说明文本截断。
- 完成标准是什么：所有旧能力都可达且行为不变；普通状态变化只局部绑定；浏览器和语言说明完整可读。
- 依赖是否满足：T002 已提供八个稳定二级目标和返回合同；现有 `SettingsFeatureController`、Gateway 和 Effect 不需要改变，依赖满足。

### T003 验收结果

- 外观与语言页承接主题和语言；应用行为页承接恢复现场与最近任务；浏览器与登录页承接原浏览器入口；权限与文件页承接通知和投放区。
- 所有可写项继续通过原 `SettingsFeatureController -> SettingsGateway`；通知、投放区和主壳副作用继续使用原 Effect。
- 二级页快照变化只更新对应副标题或开关，不重建页面。
- 浏览器与语言对话框说明取消两行上限，长说明可以完整显示。
- 设置目录、页面、控制器、结果合同和导航聚焦测试：`BUILD SUCCESSFUL`。

T003 状态：completed。

## 2026-07-20 T004 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T004，让终端持久化默认值在设置中可见，并把浏览器自动化从稳定登录页分离到实验页。
- 完成标准是什么：设置与终端现场读写同一偏好；返回已存在终端时能轻量同步；实验开关仍写入现有 `BrowserRuntimeMode`，不增加 Store。
- 依赖是否满足：T003 已建立对应二级页和原浏览器 Controller；`TerminalUiPreferences` 已是终端现场唯一持久化入口，可直接扩展轻量投影，依赖满足。

### T004 验收结果

- 终端与工作台页增加默认字号和终端主题，两处都直接读写 `TerminalUiPreferences`；没有新增 Gateway 或 Store。
- 已存在的 `TerminalFragment` 在重新可见时只比较并同步字号/主题，不重建会话、不重新连接终端。
- `TerminalThemeMode` 的用户标签改为资源映射，中文与英文不再依赖枚举里的硬编码中文。
- 浏览器与登录页只说明稳定系统浏览器认证和 Android 默认网络边界；自动化开关仅在实验功能页显示，并继续写入原 `BrowserRuntimeMode`。
- 终端偏好和设置页面聚焦测试：`BUILD SUCCESSFUL`；非法字号继续收敛到原预设范围。
- 删除已无入口的混合浏览器模式对话框，避免稳定/实验选择器重新出现。

T004 状态：completed。

## 2026-07-20 T005 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T005，让权限、运行环境、进程、日志和版本能力从设置中心可达，但不复制状态或嵌入旧轮询页。
- 完成标准是什么：权限使用现有系统事实和系统入口；运行环境只消费 `RuntimeBootstrapGateway` 快照；进程、日志和版本可达；不原样挂载 `StatusFragment/BridgeFragment`。
- 依赖是否满足：T003 已提供权限页，T004 已完成终端/浏览器分流；进程管理、RuntimeBootstrapGateway、LogActivity 和系统设置函数均已存在，可通过新的类型化 Effect 路由，依赖满足。

### T005 验收结果

- `KFApplication` 暴露现有 `RuntimeBootstrapGateway` 依赖所有权；设置二级页只订阅该 Owner 的快照，没有合并进 `SettingsGateway`。
- 权限页显示全部文件访问、通知和投放区；全部文件与通知继续打开 Android 系统入口。
- 运行环境页显示 Ubuntu/default container readiness，并提供现有运行任务与进程管理入口；工具链只说明由资源中心拥有，不复制安装事实。
- 帮助与关于页显示运行时读取的版本信息和现有 `LogActivity` 入口。
- runtime 刷新只在权限/运行页进入或从系统页返回时触发一次；没有定时器、文件扫描或 UI 线程健康探测。
- 设置、终端、runtime projector/controller 和导航共 41 项聚焦测试：`BUILD SUCCESSFUL`。

T005 状态：completed。

## 2026-07-20 T006 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T006，完成中英文、可访问语义、压力护栏和全量构建封口。
- 完成标准是什么：本地化键和 Lint 为零问题；点击行/开关具备可读语义；无新增重型绑定或轮询；全量单测和 Debug 构建通过。
- 依赖是否满足：T004、T005 已完成并通过聚焦测试；当前只做横向质量封口，不扩展业务能力，依赖满足。

### T006 验收结果

- 设置导航行和开关补齐动态 content description；主题、语言和单选项具备可聚焦语义。
- AppCompat 应用语言所有权从 Platform 设置适配器上移到 Shell 注入，架构依赖守卫恢复通过。
- 设置分类到 Shell 目标的双向映射移出 MainActivity；MainActivity 物理行数保持基线 `2578`，未放宽债务上限。
- 运行车道静态守卫更新为检查当前本地化徽标路径，继续要求首页动作和徽标消费共享投影，不改变首页行为。
- 全量 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`。
- 563 项测试、0 失败、0 错误、1 项既有跳过；`MissingTranslation=0`、`ExtraTranslation=0`、`HardcodedText=0`。
- 架构检查、运行车道检查、压力扫描和 `git diff --check` 通过；设置新增代码没有整页重建、渲染时探测、定时轮询、WebView reload 或终端重连。

T006 状态：completed。

## 2026-07-20 T007 启动与三问自检

- 目标是什么：依据 `PLAYBOOK.md` 的 T007，在默认 OnePlus 8T 上验证真实设置中心、语言、系统返回和终端双入口。
- 完成标准是什么：全部页面可达且无截断/闪白/错误返回；中英文和跟随系统可用；终端偏好同步；没有新增 FATAL、ANR 或输入超时。
- 依赖是否满足：T006 已完成 Debug APK 构建和全部自动门；工具链参考指定 OnePlus 8T `3f8bbaad` 为默认目标，依赖满足。

### T007 验收结果

- 当前 Debug APK 覆盖安装到 OnePlus 8T `3f8bbaad`，八个设置二级页全部可达且长说明完整显示。
- 应用语言切到 English 后冷启动仍保持英文；卡片用户数据没有被误翻译；随后已恢复“跟随系统”并回到中文。
- 默认终端字号从 35sp 改到 28sp 后新会话立即采用 28sp；恢复 35sp 后同一终端会话重新进入即采用 35sp，测试会话已清理。
- 全部文件访问与通知入口分别打开 Android 对应系统页，返回后仍停在“权限与文件”；日志入口返回“帮助与关于”。
- 真机发现“运行管理”原固定返回首页，已把该目标改为带首页安全回退的上下文返回；从设置进入时准确返回“运行环境”，聚焦测试和架构门禁通过。
- 真机发现设置目录从下半部分进入二级页后返回会丢失滚动位置，已用 Activity 级纯 UI 状态保存并恢复目录位置，不复制任何偏好或运行事实；单测与真机复测通过。
- 最终进程存活，`topResumedActivity` 为 `com.kite.app/.MainActivity`；logcat 中 `FATAL/ANR/Input dispatching timed out` 匹配为 0。

T007 状态：completed。

## 2026-07-20 T008 启动与三问自检

- 目标是什么：基于真机控件树继续收尾一个正式产品差距，让终端核心图标动作具备标准触控面积和可读语义。
- 完成标准是什么：新建终端与返回会话按钮不少于 48dp；中文、English 内容描述完整；真机控件树可识别动作；不改变终端会话或运行生命周期。
- 依赖是否满足：T007 已证明终端主路径与偏好同步正常；真机控件树明确显示 `btnListAdd` 为可点击 `ImageButton` 但内容描述为空，且布局仅 36dp，证据充分。

### T008 验收结果

- 终端列表的新建按钮和列表/独立详情返回按钮统一为 48dp；图标视觉尺寸保持不变，触控面积扩大。
- 新建按钮复用 `terminal_new_session`，返回按钮复用 `common_back`，中文和 English 资源均存在；新增两项 Robolectric 布局合同测试。
- OnePlus 8T 控件树确认“新建终端”边界为 `[882,133][1026,277]`、“返回”边界为 `[36,127][180,271]`，均为 144px（设备密度下 48dp），内容描述准确。
- 终端空状态、新建、详情返回和清理测试会话均正常；最终空状态已恢复，进程存活且 FATAL/ANR 匹配为 0。
- 长期结论已提炼到 `docs/architecture/settings.md`、`docs/architecture/decisions.md`、`docs/reference/feature-status.md` 和文档索引。
- 最终全量门禁：121 个测试套件、566 项测试、0 失败、0 错误、1 项既有跳过；Lint、架构、运行车道、本地化和 Debug APK 构建全部通过。
- 最终 Debug APK：233,507,670 bytes，SHA-256 `17B0F6924E48C70AC6437EB04D1E0980C87CFF1F8800F2A5D2AFC6F69774475B`。
- 当前完成范围达到 `0.0.8` 候选含义，但仍保持 `versionName=0.0.4`、`versionCode=4`；未 commit、push、tag 或发布。

T008 状态：completed。
