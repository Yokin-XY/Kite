# Kite Agent 模型来源受控编辑进度

## 状态总览

| 任务 | 状态 | 当前结论 |
| --- | --- | --- |
| T001 | 已完成 | 目标存储/草稿/请求链测试通过 |
| T002 | 已完成 | 三类来源别名、官方可见性和草稿真实引用已统一 |
| T003 | 进行中 | 增加免费/官方来源受控显示名称编辑页面 |
| T004 | 待开始 | 等待 T003 |

## 2026-08-01 T001 启动与三问自检

- 目标是什么：按 `PLAYBOOK.md` 的 T001，固定显示名称与真实模型引用分离、系统来源映射和全链路不混用的测试合同。
- 完成标准是什么：免费/官方/自定义别名投影、可见性、默认/草稿/实际配置值均有断言，目标 JVM 测试通过。
- 依赖是否满足：T001 无前置依赖；项目规则、两个相关技能及必要参考已完整读取。

### 启动审计

- 工作区：`D:\xm\Kite-agent-conversation`。
- 分支/HEAD：干净 `main`，`889ecc99fde83e98dc10ae1163be6ece3173449d`。
- 远端关系：`main...origin/main [ahead 2]`；领先提交为既有 `d02adc33`、`889ecc99`，本任务不改写它们。
- 状态文件：启动时不存在 `docs/autonomous-task/`，本次按协议新建。
- 事实拥有者：
  - 原生 Provider、默认模型、URL、凭据存在性：`AgentConfigAdapter` / `AgentLiveConfigSnapshot` 及各 Agent 原生适配器。
  - Kite 显示名称和会话可见性：`AgentModelLibraryStore`。
  - 官方认证状态：`AgentOfficialAccountManager`。
  - 会话/草稿选择：`AgentDraftModelPolicy`、`AgentRuntimeRegistry`。
  - 实际模型设置：`AgentConfigAdapter.sessionModelSelection` 映射到 `KiteAgentConnection.setConfiguration`；ACP 最终使用真实 `ModelId`。
- 压力分诊：
  - 可见显示面：Agent 设置 > 模型库，以及会话模型选择器。
  - 通道：UI Binding + Kite 展示偏好；不属于运行创建或底座。
  - 状态拥有者：原生适配器、`AgentModelLibraryStore`、`AgentRuntimeRegistry` 各自保持不变。
  - 压力风险：保存后现有实现会重建当前模型库页面；不得扩大为定时器、全局刷新或渲染时探测。
  - 禁止的大范围刷新：不触碰 `showCardRunSurface(...)`、资源刷新、终端/WebView 生命周期。

### 待验证

- 系统来源显示别名键应绑定“合成 Kite 来源 ID + 真实协议模型值”，不改写真实 choice value。
- 官方来源可见性需要从真实 `modelGroupIds` 显式映射到合成 Kite 来源 ID。
- 免费/官方别名需同时进入模型库卡片、活动会话选项和新会话草稿选项。

### 安全网补充

- 已确认现有 `AgentRuntimeRegistryTest` 覆盖：草稿选中 `providerId/modelId` 后，首次发送的调用顺序为 `new -> model -> prompt`，实际 `setConfiguration` 值为真实 `zhipu/glm-5.2`。
- 已确认现有 `AgentSurfaceNavigationPolicyTest` 覆盖：自定义别名只改变草稿 choice 名称，选择结果仍为 `AgentDraftModelSelection(providerId, modelId)`。
- 新增系统来源偏好存储隔离测试：官方合成来源 ID、免费来源 ID 和真实协议模型值可以独立保存，不互相覆盖。
- 基线 Agent 测试首次执行因 60 秒工具窗口终止，未形成成功或失败结论；残留的两个 wrapper 进程已定点结束，后续使用可轮询的后台执行方式重跑。

## 2026-08-01 T001 验收

- 目标测试：`AgentModelLibraryStoreTest`、`AgentSurfaceNavigationPolicyTest`、`AgentRuntimeRegistryTest`。
- 结果：`BUILD SUCCESSFUL in 2m 13s`，48 个 Gradle 任务（8 executed，40 up-to-date）。
- 关键证据：系统来源偏好按 Kite 来源 ID 与真实模型值隔离；自定义别名不改变草稿 `providerId/modelId`；首次发送顺序和实际配置值的既有断言继续通过。
- 残余说明：编译存在既有 Kotlin 注解/可空性和 Java deprecated 警告，本阶段没有新增失败。

## 2026-08-01 T002 启动与三问自检

- 目标是什么：按 `PLAYBOOK.md` 的 T002，统一免费、官方、自定义来源投影和显示名称查找，同时保持真实请求引用不变。
- 完成标准是什么：来源/模型身份显式映射；官方可见性生效；模型库、活动会话和草稿显示别名；默认解析不依赖显示名称；测试通过并本地提交。
- 依赖是否满足：T001 已通过目标 JVM 测试，依赖满足。

## 2026-08-01 T002 验收

- 实现：`AgentModelLibraryPolicy` 把官方账号声明的 `modelGroupIds` 映射到合成 Kite 来源 ID；免费/官方使用原始协议 choice value 作为别名键，自定义来源兼容既有短模型 ID 键。
- 链路：模型库投影、活动会话过滤和新会话草稿均使用同一别名装饰；选择与发送仍保留原始 value 或解码后的真实 `providerId/modelId`。
- 性能：官方账号目录随现有后台草稿配置读取一并取得；渲染路径只读取内存字段和 SharedPreferences 小型快照，没有新增文件扫描、进程探测、网络检查、轮询或整页运行面重建。
- 验证：`AgentModelLibraryPolicyTest`、`AgentSurfaceNavigationPolicyTest`、`AgentRuntimeRegistryTest` 通过，`BUILD SUCCESSFUL in 13s`，48 个 Gradle 任务（2 executed，46 up-to-date）。

## 2026-08-01 T003 启动与三问自检

- 目标是什么：按 `PLAYBOOK.md` 的 T003，为免费和官方来源增加只编辑 Kite 显示名称的页面，保留自定义 Provider 完整编辑和官方登录动作。
- 完成标准是什么：系统来源有明确入口；显示名称可改；模型 ID 只读；保存只写展示偏好；不暴露 URL/API Key/删除/来源改写；UI 与压力合同通过。
- 依赖是否满足：T002 的来源身份与别名投影已通过测试，依赖满足。
