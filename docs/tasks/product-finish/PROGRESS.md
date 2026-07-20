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
| T009 | completed | 不可变色板、动作副作用和双语资源合同已由聚焦测试锁定 |
| T010 | completed | 明暗 16 色已在同一真机会话中切换并显示清晰，色板与会话刷新门通过 |
| T011 | completed | 输入栏、分页、触控语义和动作后输入状态已通过最终真机复测 |
| T012 | completed | 明暗主题、全量门禁、日志、恢复现场和清理均已完成 |

## 当前恢复指针

- 当前任务：T009-T012 终端正式化第二轮已完成。
- 当前动作：保持 `0.0.4` 开发版本；前一批已由用户授权本地提交为 `536c824`，本批终端改动留在工作树等待下一次明确提交决定。
- 下一验证：进入下一项产品收尾时，以当前暗色主题、空终端列表和全绿门禁为基线。
- Git 边界：本轮终端改动未 commit、未 push、未改版本号、未发布。

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

## 2026-07-20 T009 启动与三问自检

- 目标是什么：把终端颜色、输入栏、快捷方式和动作逻辑从“功能够用”收敛到可重复校准、可访问、可扩展的正式产品状态。
- 完成标准是什么：色板不受可变全局状态污染；明暗基础色可读；操作面视觉层级明确；输入动作的保留/清理语义准确；不破坏会话和实时预输入。
- 依赖是否满足：T008 已验证终端列表、创建、返回和持久化主题路径；当前 OnePlus 8T 在线，旧 KF 与 Kite 当前代码、亮色/暗色截图和控件树都已取得，依赖满足。

### 已确认事实

- Kite 当前 `applyTerminalColorScheme()` 与旧 KF 基本相同，说明旧方案已被迁入；本轮应修正它的确定性和覆盖范围，而不是再次复制。
- `TerminalColors.COLOR_SCHEME` 是进程级可变单例，而 `baseTerminalPalette` 是 Fragment 首次显示时从该单例克隆；后创建页面可能把已校准色板误当基础色。
- 暗色现行逻辑只显式修正 ANSI 0、7、8、15；亮色只调整前 16 色，默认亮色仍偏霓虹。
- 终端颜色切换后已有 `TerminalColors.reset()` 路径，可以在不重连会话的前提下更新当前终端。
- 操作注册表已有两页和扩展接口，功能无需推倒重做；缺口集中在资源化、状态表达、可访问性和动作副作用。
- OnePlus 8T 亮色基线显示输入栏占用约 228px 高度、三个孤立白色控件缺少主次，展开状态和第二页不可见；方向盘中心 Enter 在现有尺寸下截断。

### 压力分诊

- 症状或功能：终端颜色偏差、输入区视觉和快捷动作反馈不成熟。
- 可见显示面：终端画布、输入栏、快捷操作页和主题菜单。
- 通道：Terminal Runtime + UI Binding。
- 状态拥有者：终端会话/输入继续由现有 terminal controller 与 `TerminalRuntimeHost` 持有；终端外观偏好继续由 `TerminalUiPreferences` 持有。
- 事件来源：用户输入、快捷动作、主题和字号选择。
- 可见消费者：当前 `TerminalFragment` 的终端画布与操作控件。
- 触及的热路径：主题应用、输入差量同步、小型操作控件构建和点击分发。
- 禁止的大范围动作：终端重连、会话重建、整页刷新、Web 显示面重载、渲染时文件/网络探测、删除实时预输入机制。
- 验证证据：纯色板/动作合同单测、布局与本地化检查、全量构建、OnePlus 8T 明暗截图、控件树和 FATAL/ANR 日志。

### T009 验收结果

- 新增 `TerminalColorPaletteTest`，锁定重复生成不漂移、完整 259 索引、标准扩展色和明暗基础色对比度。
- 第一次聚焦测试按预期因缺少 `TerminalColorPalette`、`TerminalComposerEffect` 和资源化字段失败，证明合同先于实现生效。
- `TerminalPanelActionRegistry` 的固定标题和说明改为资源 ID；Ctrl+C 显式声明 `RESET_AFTER_ACTION`，其他编辑/显示动作默认保留输入。
- 中文和 English 已补齐输入提示、展开/收起、发送、动作说明、方向键和分页语义。
- 实现后两项聚焦测试 `BUILD SUCCESSFUL`，色板与动作合同由红转绿。

T009 状态：completed。

## 2026-07-20 T010 启动与三问自检

- 目标是什么：用确定性明暗色板替换 Fragment 从全局可变色表捕获基准并局部修色的路径。
- 完成标准是什么：16 个基础 ANSI 色全部显式校准；16-255 保持标准索引；主题重复切换不漂移；当前会话只重置颜色、不重连。
- 依赖是否满足：T009 已建立色板尺寸、扩展色、对比度和重复生成合同并通过，依赖满足。

### T010 验收结果

- 新增 `TerminalColorPalette`，每次从标准 256 色索引和主题专属 ANSI 16 色重新生成 259 项色板，不再读取已变异的 Termux 全局色表作为输入。
- 暗色和亮色分别显式设置前景、背景和光标；全部 16 个基础色经过对比度合同，扩展色保留 xterm 标准索引与背景语义。
- OnePlus 8T 会话 `#5721` 内从亮色切到暗色、再切回亮色，编号、历史和 Shell 均保持；16 色测试输出在两种背景下均可辨。
- 主题切换后继续调用现有 `mColors.reset()`，没有增加会话重连、页面刷新或后台探测。
- 终端聚焦测试与 Debug APK 构建 `BUILD SUCCESSFUL`。

T010 状态：completed。

## 2026-07-20 T011 启动与三问自检

- 目标是什么：在保留现有两页快捷能力和实时预输入的前提下，收紧输入栏视觉层级、可访问语义与动作后的输入状态。
- 完成标准是什么：输入/展开/发送形成清晰主次；快捷页可发现；触控区和双语语义完整；Ctrl+C 与 Enter 清理输入，其他动作保留；不重建终端。
- 依赖是否满足：T010 已稳定终端画布和主题颜色，操作面可以直接使用同一明暗 token；现有注册表和输入 controller 保持可复用，依赖满足。

### T011 验收结果

- 折叠输入栏改为终端入口图标、18dp 圆角输入面和蓝色主发送按钮，左右动作均为 48dp；展开时入口切换为明确关闭图标和选中颜色。
- 两页快捷面增加当前页指示；动作卡片扩大到 80x48dp，方向盘扩大到 48dp 单元，中心改为“↵ / 回车”，真机不再截断。
- 全部动作标题、说明、输入提示、展开/收起、发送、方向键和分页状态进入中英文资源；真机控件树可读出准确内容描述。
- `TerminalComposerEffect` 成为动作注册项唯一副作用声明，执行器统一先运行 handler、再兑现输入状态；Ctrl+C 和 Enter 清理实时输入，其他动作保留。
- 真机确认实时输入 `abc` 后 Ctrl+C，输入框立即恢复提示；发送 `echo ok` 后输入框清空并继续保持同一会话。
- 主题切换最初通过重建快捷页更新副标题，真机发现横向滚动动画竞态后改为局部更新现有主题卡片；快速切换不再出现两页各露一半。

T011 状态：completed。

## 2026-07-20 T012 启动与三问自检

- 目标是什么：用最终 Debug APK 在 OnePlus 8T 完成明暗颜色、输入动作、分页、会话保持、可访问性和压力日志验收。
- 完成标准是什么：所有用户可见路径可用；同一会话切主题不丢历史；全量自动门全绿；设备恢复原暗色主题且测试会话清理。
- 依赖是否满足：T011 的聚焦测试和 Debug 构建已通过，竞态修复已进入最终 APK，依赖满足。

### T012 验收结果

- OnePlus 8T `3f8bbaad` 上最终会话 `#1640` 输出 ANSI 0-15，亮色与暗色截图均清晰；前景、背景和光标随主题即时变化。
- 同一会话在亮色/暗色间切换，编号、命令历史和 Shell 保持；第二页主题副标题即时更新，分页位置稳定。
- 控件树确认输入、展开、发送均为 48dp；动作卡片、方向键、回车和分页状态均有中文语义。输入栏折叠/展开、两页滑动、Ctrl+C、发送和主题动作通过。
- 全量 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`；122 个测试套件、570 项测试、0 失败、0 错误、1 项既有跳过。
- 架构与运行车道静态检查通过；运行车道守卫已同步要求 `action.execute(...)` 同时兑现 handler 与类型化 composer effect，没有放宽生命周期保护。
- `MissingTranslation=0`、`ExtraTranslation=0`、`HardcodedText=0`；`git diff --check` 通过。
- 最终 Debug APK 为 233,507,670 bytes，SHA-256 `5FD6FFA5005C5479DA2A0F324592681A4B5B6682680C1D88C28F42E897F63FFD`。
- 验收后终端偏好恢复 `dark`、字号保持 35sp，测试会话用 `exit` 正常结束并从列表清理；Kite 进程存活，FATAL/ANR/Input timeout 匹配为 0。

T012 状态：completed。

## 2026-07-20 T014 启动与三问自检

- 目标是什么：把主题从分散的颜色偏好升级成一个可扩展的应用级入口；基础层统一颜色与有效明暗，进阶层允许组件和局部显示面通过同一合同选择样式。
- 完成标准是什么：主题只有一个持有与解析路径，主要页面不再各自读取偏好；设置能切换跟随系统/亮色/暗色并消费样式注册表；系统栏、应用外壳和终端跟随同一有效主题；自动门和 OnePlus 8T 真机验收通过。
- 依赖是否满足：T012 已稳定终端明暗色板和无重连切换路径，T013 已建立首页组件形状与间距基础；当前代码审查已确认散落入口和系统暗色不生效的真实路径，依赖满足。

### 已确认事实

- `ThemeConfig` 目前只有 `themeColor/backgroundColor`，`KiteTheme.resolve()` 固定按亮色生成 token，没有系统/亮色/暗色模式。
- `MainActivity` 是当前可见外壳的主题应用者，但 `CardRunActivity`、首页、资源、编辑器和运行管理仍有直接读取 `kite_theme` 的路径，形成多个解释入口。
- 系统状态栏和导航栏在资源主题中固定为亮色；真机系统报告暗色时，Kite 首页、设置和资源页仍为亮色。
- `TerminalUiPreferences.SYSTEM` 直接读取系统 `uiMode`，终端外壳则使用应用全局亮色 token，存在同一页面内外明暗分裂的所有权问题。
- 现有终端明暗 ANSI 色板已人工校准，本轮只统一有效主题来源，不重做色板或会话生命周期。

### 压力分诊

- 事件：用户显式切换主题，或系统明暗状态变化。
- 状态拥有者：主题偏好由 `SettingsGateway` 持有；有效主题由应用级主题环境提供者解析。
- 可见消费者：当前 Activity 系统栏与根容器、首页、资源、设置、编辑器、运行管理、运行窗口和终端显示面。
- 触及的热路径：显式主题应用、Activity 配置变化、当前可见控件的轻量重绑。
- 禁止的大范围动作：主题偏好分散读取、页面各自推导明暗、周期轮询、资源/运行状态重查、终端重连、WebView 重建或以整页刷新代替主题信号。
- 验证证据：模式/样式/作用域纯合同测试，Gateway 持久化测试，设置交互测试，全量门禁，OnePlus 8T 逐页截图和 FATAL/ANR 日志。

T014 状态：in_progress。

### T014 验收结果

- 新增 `ThemeEnvironment`、`KiteThemeMode`、`ThemeStyleDefinition`、`ThemeComponentStyle` 和 `ThemeScope`；颜色、组件形态与局部作用域分层，`standard` 作为首套默认样式登记到中央注册表。
- `SettingsGateway` 新增 `themeMode/themeStyleKey` 与类型化命令；旧主题色和背景色键保持不变，缺失新键时默认跟随系统并回退 `standard`。
- `ThemeEnvironmentGateway` 成为应用级唯一主题读取合同，Android 系统明暗由 Platform 适配，Window 系统栏由 UI 绑定；生产代码直接读取 `kite_theme` 或调用旧 `KiteTheme.resolve()` 的位置均清零。
- 首页使用 `HOME` 作用域的组件样式；资源、设置、编辑器、运行管理、运行历史、运行窗口和终端均消费统一有效环境。终端界面文案由“跟随系统”改为“跟随应用”，已校准 ANSI 色板和会话重置路径不变。
- 设置主题页新增“跟随系统/亮色/暗色”和注册表驱动的“组件样式”入口；当前只有 `standard`，以后新增样式不需要修改设置页或逐页写样式名分支。
- 自动门：580 项测试、0 失败、0 错误、1 项既有跳过；Lint 0 错误；架构债务 `lines=2575/functions=127/fields=41`，未调高基线；架构检查、运行车道检查、本地化审计、`git diff --check` 和 Debug APK 构建通过。
- Debug APK：232,993,266 bytes，SHA-256 `0D037E0FC640F615D68FB8BB750CB19ABF9FB94984E7CC0FA2A0D5E04D100C03`。
- OnePlus 8T `3f8bbaad` 真机证明：系统暗色 + 默认跟随为暗色；显式亮色即时变亮；显式暗色在 Android 临时切到亮色并冷启动后仍保持暗色；首页、设置、资源外壳和终端外壳可读，进程日志中 FATAL/ANR/Input timeout 为 0。
- 验收后 Android 已恢复暗色，Kite 已恢复 `theme_mode=system`，最终前台为 `com.kite.app/.MainActivity`；没有创建终端会话或改变终端原有独立主题偏好。

T014 状态：completed。
