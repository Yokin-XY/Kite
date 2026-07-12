# Kite 业务架构基线

本文是 T001 的可执行架构事实，不替代 `PLAYBOOK.md`。母板决定任务顺序，本文记录业务目的、
状态所有权、依赖方向和 `MainActivity` 迁移台账。

## 目标结构

```text
shell -> feature -> application -> domain
  |          |            ^          ^
  |          +------------|----------+
  +-----------------------+-----------> platform adapters
```

实际约束如下：

| 层 | 负责 | 可以依赖 | 禁止 |
| --- | --- | --- | --- |
| Shell | Activity、导航、系统 Intent、权限结果、依赖装配 | Feature 入口、Application 合同、Platform 装配 | 资源安装、配方执行、页面具体渲染 |
| Feature | Screen/Fragment、UiState、Action、局部渲染 | Application、Domain、Android UI | 其他 Feature 页面、具体 Activity、Platform 实现 |
| Application | UseCase、动作编排、Port、Effect | Domain | Activity、Fragment、View、Feature、Platform 实现 |
| Domain | 业务模型、纯规则、状态合同 | Kotlin/JDK | Android、UI、Shell、Feature、Platform |
| Platform | PRoot、bridge、browser、file、toolchain、Android Service | Application Port、Domain、Android 非页面 API | Feature、Shell、Activity、Fragment、View |

迁移期间旧包保持可编译，但每一项历史债务只能减少。新代码进入 `shell`、`feature`、
`application`、`domain`、`platform` 目标包后，立即遵守完整依赖规则。

## 通用业务合同

所有 Feature 都使用同一条业务路径：

```text
Action
-> Controller / UseCase
-> 状态拥有者写入事实
-> Projector 生成 UiState
-> Screen 局部渲染
-> 一次性 Effect 由 Shell 执行
```

共同状态语义：

| 阶段 | 前台要求 | 事实要求 |
| --- | --- | --- |
| 已接收 | 点击后立即禁用重复动作并显示处理中 | 可以是乐观承诺，不得伪装成功 |
| 进行中 | 只更新相关按钮、卡片、步骤或显示面 | 状态拥有者持续确认，监控只作兜底 |
| 成功 | 立即投影完成后的唯一合法动作 | 后台条件全部满足后才能落成功 |
| 失败 | 显示失败原因和确定重试入口 | 保留阶段、步骤和诊断，不吞异常 |
| 待确认 | 明确说明尚未得到最终确认 | 超时不能悄悄回到初始状态 |
| 取消/停止中 | 禁止重复取消或停止 | 执行层确认后才能显示已停止 |

## 六条业务链目的验收矩阵

### 1. 首页与卡片

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 浏览、组织、编辑并启动卡片；立即看到对应运行状态 |
| 入口 | Console、桌面快捷方式、外部卡片 Intent、运行窗口返回 |
| 配置事实 | `KiteRecipeLoader` 和 `KiteCardGroupStore` |
| 运行事实 | `CardRunStore`；`runtimeStates` 只能作为待移除的 Activity 投影缓存 |
| 动作入口 | `KiteRecipeActionCoordinator`，最终进入统一运行编排 |
| 显示 | Home Feature 只投影卡片、分组、处理中和运行状态 |
| 生命周期 | 离开首页只解绑显示；Activity 重建从配方与 Run Store 恢复；不得停止任务 |
| 失败目的 | 启动失败留在目标卡片，显示原因与重试；不得整页刷新后丢失失败态 |
| 当前主要债务 | `currentRecipes`、`runtimeStates`、页面缓存、配方编辑和执行入口共同位于 `MainActivity` |

### 2. 资源目录与安装

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 搜索资源，查看要求，获取、打开、停止、卸载、取消或重试 |
| 入口 | 资源首页、搜索、详情、管理、首页资源卡、自动化 Intent |
| 目录事实 | `KiteResourceRegistry`、`KiteResourceManifestLoader` |
| 安装事实 | `KiteResourceInstallStore` 和已安装登记；运行事实来自 `CardRunStore` |
| 动作入口 | `KiteResourceActionCoordinator`、`KiteInstallPlanActionCoordinator` |
| 投影 | `KiteResourceUiProjector`、`KiteResourceInstallStepUiProjector`、`KiteResourceRuntimeFactsProjector` |
| 生命周期 | 页面不可见只记脏；安装在后台继续；返回时先投影已有状态再校准 |
| 失败目的 | 准确区分下载、安装、注册、链接、网络和超时；只有所有条件成立才登记成功 |
| 当前主要债务 | 四个 Fragment 反向委托 Activity 渲染；目录、详情、安装向导共享 Activity 缓存和请求序号 |

### 3. 运行实例与动作执行

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 一个动作对应一个明确实例，可观察进度、打开显示面、停止并确认结果 |
| 入口 | 首页卡片、资源动作、编辑器、自动化、快捷方式 |
| 运行事实 | `CardRunStore`；进程现实由 `TaskManagerStore`、容器和运行时快照提供 |
| 编排 | 现有 Action Coordinator 生成计划，后续由 `RunOrchestrator`/`RecipeExecutor` 执行 |
| 显示 | Run Feature 将同一实例投影到 terminal、report、web 或进程管理 |
| 生命周期 | 页面销毁不停止实例；用户停止才触发停止；内存回收经过现有策略链 |
| 失败目的 | 保留失败步骤、退出码、输出和残留进程观察；停止中不能提前宣布已停止 |
| 当前主要债务 | shell/terminal/Web/X11 步骤执行、进度和停止判断仍是 Activity 方法并直接决定页面 |

### 4. 终端

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 创建、恢复、切换、输入、结束终端会话，并从运行实例打开正确会话 |
| 会话事实 | `TerminalSessionStore`；PTY 和进程连接由 `TerminalRuntimeHost`/`TerminalSessionController` 管理 |
| 动作 | 创建、切换、排队输入、结束和删除通过终端能力入口，不由 Shell 猜测状态 |
| 显示 | `TerminalFragment` 拥有终端视图和快捷面板，Run Surface 只绑定指定 sessionId |
| 生命周期 | View 销毁只 detach UI；会话继续存活；明确结束才回收 PTY/进程 |
| 失败目的 | 区分记录可恢复、PTY 已退出、输入未就绪和进程不存在 |
| 当前主要债务 | 仍通过 `TerminalChromeHost` 操作 Activity 底栏；运行窗口终端装配仍在 `MainActivity` |

### 5. Web、浏览器与认证

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 显示普通网页、让运行实例打开 Web 面、完成官方系统浏览器认证、执行自动化动作 |
| 页面事实 | Web 显示状态归目标 Run Surface；普通浏览历史只属于对应 WebView |
| 认证事实 | `BrowserAuthSessionStore`；loopback 转发状态由 `BrowserLoopbackCallbackBridge` 确认 |
| 自动化事实 | `BrowserAutomationSessionStore` 与 Controller，不能冒充认证状态 |
| 外部交互 | 系统浏览器、Custom Tab 和回跳 Intent 是 Shell Effect；OAuth 参数原样桥接 |
| 生命周期 | WebView 可回收、后台任务可继续、认证会话可等待回跳，三者不得绑定成同一寿命 |
| 失败目的 | 区分地区/网络、浏览器拒绝、回调未转发、会话过期和目标实例丢失 |
| 当前主要债务 | 普通 Web、认证桥、自动化和 CardRun Web 的入口及恢复逻辑集中在 Activity |

### 6. 设置、权限与首次启动

| 项目 | 合同 |
| --- | --- |
| 用户目的 | 修改主题和行为偏好，完成运行环境所需权限与首次准备，并能中断后继续 |
| 偏好事实 | `kite_theme`、`kite_app_settings`；系统权限以 Android 实际查询为准 |
| 准备事实 | `BootstrapCoordinator.snapshot`、`AssetExtractor.rootfsProgress` 和工具链状态 |
| 动作 | 设置 Feature 提交偏好动作；权限请求和系统设置页通过 Shell Effect |
| 显示 | 设置页面和首次启动引导分别投影，不能复用一个临时字段互相覆盖 |
| 生命周期 | 权限弹层返回、进后台和进程重建都有恢复点；主题变化不停止后台任务 |
| 失败目的 | 明确缺少哪项权限或准备步骤，允许重试；诊断页只在真实启动失败时出现 |
| 当前主要债务 | SharedPreferences、权限回调、首次引导、运行时 gate 和设置渲染共同位于 Activity |

## MainActivity 迁移台账

机器基线见 `architecture-baseline.json`。下面记录职责归属，行号只用于定位，不作为完成标准。

| 当前职责区域 | 当前入口 | 目标所有者 | 任务 | 完成条件 |
| --- | --- | --- | --- | --- |
| 生命周期、导航与恢复 | `onCreate`、`onNewIntent`、`onResume`、`ScreenRouter` | Shell | T002 | Activity 只转交，不解释 Feature 业务 |
| 浏览器认证回跳与自动化 Intent | `handleBrowserAuthRedirect`、`handleRuntimeAutomationIntent` | Shell router + Web/Application | T002/T009 | Intent 分类与业务处理分离 |
| 权限、rootfs gate、主题与设置 | `maybeStartFirstRunPermissionOnboarding`、`showSettings` | Settings/Onboarding | T010 | Activity 只执行系统 Effect |
| 首页与卡片 | `showConsole`、`renderConsolePageBody`、卡片绑定 | Home | T005 | 页面、缓存和动作提交离开 Activity |
| 资源目录、搜索、详情、管理 | `showResources` 至资源详情/管理渲染 | Resources | T003/T004 | 四个 Host 委托删除 |
| 资源安装向导与计划 | `showResourceInstallWizard`、安装计划和步骤渲染 | Resources/Application | T003/T004 | UiState 与执行计划分层 |
| 运行显示面 | `showCardRunSurface`、terminal/report/web | Run Surface | T007/T009 | CardRunActivity 独立组合显示面 |
| 运行管理和运行时面板 | `showKiteProcessOverview`、runtime panel | Runtime Management | T008 | 页面只消费统一快照 |
| 配方执行和停止 | `startRecipe`、`execute*Step`、`stopRecipe` | Run Application | T006 | 执行层无 Android View 依赖 |
| 配方编辑 | `showRecipeForm`、步骤/图标/草稿处理 | Recipe Editor | T005 | 草稿和校验归编辑模块 |
| 平台请求 | browser/desktop/APK installer/local server 回调 | Platform Port + Shell Effect | T002/T009 | Platform 不反向调用页面 |
| 终端 Chrome | `TerminalChromeHost` | Terminal Surface Effect | T011 | 终端不依赖具体 Activity |

## 当前机器债务基线

| 指标 | 基线 | 收敛任务 |
| --- | ---: | --- |
| `MainActivity` 物理行数 | 21,144 | T002-T011 |
| `MainActivity` 成员函数 | 854 | T002-T011 |
| `MainActivity` 私有字段 | 171 | T002-T011 |
| Activity 实现的 Host/Provider 接口 | 8 | T004/T005/T011 |
| 资源反向渲染委托 | 4 | T004 |
| Activity 内资源职责函数 | 64 | T003/T004 |
| `ScreenRouter` 对 `MainActivity.Screen` 引用 | 46 | T002 |
| 继承 `MainActivity` 的 Activity | 1 | T007 |
| `runtimeStates` 引用 | 64 | T005/T006 |

这些数字是防回涨护栏，不是架构完成定义。某项职责完成的判断仍然是：状态、动作、页面和
生命周期所有权已经转移，旧入口被删除，业务路径验证通过。
