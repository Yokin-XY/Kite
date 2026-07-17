# 架构总览

## 目标

Kite 使用模块化单体架构。当前只有 `:app` 和 `:terminal-view-local` 两个 Gradle 模块，业务边界通过包和合同表达，不为了目录整齐提前拆分更多模块。

架构目标是让页面只负责显示和提交意图，运行事实由明确的状态拥有者维护，Android、PRoot、终端和浏览器副作用集中在平台层。

## 分层

```text
App Shell
MainActivity / CardRunActivity / AppNavigator / AppIntentRouter
        |
        v
Feature
home / recipeeditor / resources / runsurface / terminal / web / settings
        |
        v
Application
动作编排 / 业务规则 / 状态投影 / Gateway 合同
        |
        v
Platform + Foundation
Android 适配 / Bridge / PRoot / 终端 / 浏览器 / 文件 / 工具链 / 服务
```

### App Shell

应用壳接收 Android 生命周期、Intent、系统返回和模块 Effect。它负责组合依赖和导航，不拥有资源安装、运行实例或终端的第二份状态。

### Feature

Feature 按用户可见能力组织：

- `home`：首页卡片和启动入口。
- `recipeeditor`：卡片创建、编辑与 JSON 预览。
- `resources`：资源首页、详情、管理和安装向导。
- `runsurface`：实例窗口、报告、终端和网页显示面。
- `runtimemanagement`：卡片、终端与进程的统一运行视图。
- `terminal`、`web`、`settings`：各自显示生命周期和交互合同。

Feature 只能提交 Action、消费 UiState 和发出 Effect，不直接承担 PRoot、文件扫描或进程等待。

### Application

Application 层把一个用户意图编排成确定动作，例如启动卡片、安装资源、关闭实例、打开网页或处理认证回跳。它通过 Gateway 调用平台能力，并把结果交回状态拥有者。

### Platform 与 Foundation

Platform 是 Android 适配器；Foundation 保存可跨 Feature 复用的运行底座。主要能力包括：

- KF/KFShell Bridge 与本地 HTTP 服务。
- Ubuntu PRoot、owner 遥测和进程终止。
- Android 默认网络与容器 DNS 对齐；容器流量保持 Kite 应用 UID，由系统按应用网络规则接管。
- 终端会话与 TerminalView。
- WebView、系统浏览器和认证回调桥。
- 资源安装、工具链、共享目录、日志和启动诊断。

## 核心业务链

### 卡片运行

```text
首页/快捷方式
-> RecipeActionWorkflowCoordinator
-> RunOrchestrator
-> AndroidRecipeExecutor
-> CardRunStore
-> 报告、终端、网页或通知显示面
```

每次启动使用 `instanceId + generation` 标识代次。步骤产生的窗口和终端属于该实例，底层命令通过 owner 身份关联到同一次运行。

### 资源安装

```text
资源页面
-> ResourceActionWorkflowCoordinator
-> 安装计划
-> resolve / acquire / install / verify / commit / cleanup
-> KiteResourceInstallStore + ResourceRegistry
-> 资源卡片与安装向导局部更新
```

清单可以使用不同官方渠道，但都必须经过统一生命周期、验证和登记边界。

### 网页与认证

普通本地页面留在 WebView。识别到 OAuth/SSO 授权请求后，Browser Handoff 把请求交给系统浏览器；`kite-auth://callback` 或 CLI 的 loopback 回调由进程级认证桥送回原始消费者。

## 稳定与实验边界

稳定主线包含卡片、资源、终端、运行实例、运行管理和 WebView + 系统浏览器认证桥。

浏览器自动化和 X11 仍是实验实现。它们可以使用既有模块边界继续研究，但不得改变稳定能力的默认路径，也不作为正式版本完成标准。
