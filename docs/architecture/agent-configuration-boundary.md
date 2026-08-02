# Agent 配置显示层与 SDK 边界

## 目标

Kite 只维护一套模型、权限、推理强度和工作模式的显示语义，也只开放一套 SDK 配置端口。具体 Agent 的协议、原生 ID、配置文件和能力差异全部留在 ACP 或专用 Adapter 中。

以后增加或调优一个 Agent 时，正常改动范围应当位于该 Agent 的兼容目录。若必须修改公共合同，先证明是所有 Agent 都需要的新语义，而不是当前工具的特殊值。

## 固定调用链

```text
Agent 会话与设置 UI
        ↓ 只提交本轮输入草稿或持久配置意图
Kite Agent SDK
  - AgentSessionControlApi：本轮输入草稿中的模型、权限、推理强度、工作模式
  - AgentProviderCatalogApi：Kite 保存的 Provider、模型、凭据、权限、推理与工作模式目录
  - AgentConfigurationApi：MCP、Skill、核心文档等 Agent 原生配置
        ↓ 点击发送后，按稳定 ID 把本轮草稿映射为原生动作
ACP 或 AdapterBackedAgentConfigurationApi
        ↓ 翻译原生协议、命令、配置文件和 ID
具体 Agent / Provider / Model
```

会话输入器整体视为 Kite 管理的本轮草稿：文字、附件、模型、权限、推理强度和工作模式在发送前都只改变本地草稿。点击发送后，运行层才把这些选择映射为当前 Agent 的原生值或动作，再提交消息。草稿选择保留给下一轮，远端会话加载态和消息返回不能决定固定入口是否存在。

显示层不能引用 `AgentConfigAdapter`、`AgentConfigAdapterRegistry`、`AgentConfigApplyRequest`、`AgentPersistentConfigChange` 或供应商预设目录。该限制由 `AgentConfigurationArchitectureTest` 自动检查。

## 固定公共语义

### 模型来源

模型来源只有三种：

| 来源 | 准确定义 |
| --- | --- |
| 免费 | 无需登录、无需 URL、无需 API Key，选择后直接可用；当前是为 OpenCode 免费模型建立的来源 |
| 官方登录 | 通过 Agent 官方账号入口登录后获得的模型 |
| 用户自定义 | 用户提供 URL、API Key 和模型 ID；预设只是帮助填写自定义连接，分组只负责整理这些连接 |

不存在“Agent 内置”来源。显示层不得根据模型名、分组名或工具名猜测来源；Adapter 必须在模型选项上明确填写 `modelSource`。

### 权限

公共权限固定为七项：`只读`、`受限`、`审批`、`宽松`、`智能`、`完全`、`自定义`。

- Agent 只能公布自己能够真实兑现的子集，不能为了凑齐七项而生成选项。
- `自定义`目前只表示读取并保留用户的 Agent 原生权限配置，不代表 Kite 已经提供统一编辑器。
- 默认权限与会话中的本轮权限草稿是两个作用域，但使用同一语义目录；选择本轮权限时不提前修改 Agent 状态。

### 推理强度

有序档位固定为：`关闭`、`最低`、`低`、`中`、`高`、`极高`、`最高`。非有序控制固定为：`自动`、`开启`。

- 没有推理控制能力时不显示。
- UI 只消费 Agent 经 ACP 或 Adapter 公布并映射后的子集，不自行判断某个模型应有哪些档位。
- 只有一个不可切换值时不显示选择器；原生 Toggle 可以显示为开关。
- 当前值无法映射时隐藏，不选择第一个值，不静默降档。
- 不提供“跟随默认”。清除覆盖是独立恢复动作。
- `ultra`、`ultracode` 等同时改变编排或工作流的值不能冒充纯推理强度。

### 工作模式

- 工作模式目录和 Kite 草稿默认选择保存到 `AgentProviderCatalogStore`，页面打开时只读本地快照。
- Adapter 保留 Agent 原生模式 ID，只负责补充 Kite 显示名称、说明和已核验的随应用预设。
- ACP 公布真实模式时经过同一 Adapter 映射后更新目录；ACP 没有模式目录但 Adapter 已核验发送映射时，可以使用随应用快照。
- 选择工作模式只更新 Kite 草稿；发送时才通过统一运行端口调用 ACP `session.setMode` 或专用 Adapter 动作。
- 原生配置中名为 `Mode` 的字段不当然是工作模式。权限模式必须先由 Adapter 归类为权限，不能写入工作模式目录。

## 代码目录与职责

```text
agent/contract/
  稳定 ID、三种模型来源、七项权限、九种推理语义

agent/sdk/configuration/
  AgentControlCatalog.kt       固定控件消费的类型化目录
  AgentSessionControlApi.kt    本轮输入草稿控制端口
  AgentConfigurationApi.kt     持久配置端口和统一意图

agent/config/
  AgentConfigContract.kt                  Adapter SPI 与安全快照
  AdapterBackedAgentConfigurationApi.kt  SDK 到 Adapter 的唯一桥
  AgentReasoningControl.kt                无产品名的通用正规化逻辑
  opencode/                               OpenCode 兼容实现与能力 profile
  native/NativeAgentConfigAdapterCore.kt  原生文件 Adapter 的共享机制
  native/<agent>/                         每个原生 Agent 独立的 Adapter 与能力 profile

feature/runsurface/
  AgentFixedSessionControlStrip.kt  固定模型与权限入口
  AgentRunSurfaceModels.kt          页面内部状态
  AgentManagementListAdapters.kt    MCP 与 Skill 列表显示
  ProviderCredentialFieldBinding.kt 凭据字段交互
  RunAgentSurfaceBinding.kt          会话页面协调；只依赖 SDK，不接触 Adapter SPI
```

`AgentProviderCatalogStore` 是 Provider 名称、URL、模型 ID、模型名称、凭据存在性、默认选择、
权限目录、推理目录、工作模式目录和工作模式草稿默认值的唯一持久事实源。API Key 使用 Android Keystore 主密钥加密，不进入公开快照、
页面状态、日志或普通 JSON。`AgentModelLibraryStore` 只保存分组、会话可见性和显示别名。

## 文件拆分规则

1. 每个 Agent 的模型、权限、推理值、Provider 和原生配置规则分别放在该 Agent 的兼容目录；不再新增“所有 Agent 映射大全”。
2. 公共文件只容纳无产品名、无原生路径、无工具 ID 的合同或算法。
3. UI 文件按固定控件、页面状态、列表、表单字段和页面协调拆分。新增配置能力时优先增加独立组件或协调器，不继续扩大总页面文件。
4. 两个 Agent 恰好使用同一字符串不构成公共语义；只有合同含义相同且能够独立测试时才抽取 helper。
5. ACP 已提供稳定能力时直接投影；ACP 缺失的部分由专用 Adapter 补齐，但结果仍必须经 SDK 返回。

## 状态与交互

- 页面提交用户意图；长期 Provider 与能力事实只保存到统一目录。工作模式默认草稿选择也由统一目录保存，当前会话覆盖由运行注册表持有。
- 选择模型、权限、推理强度或工作模式时只更新本轮草稿，不调用 Agent，不写原生配置。
- 点击发送时，运行层先从统一目录读取选中 Provider 的 URL、API Key 和模型，再由 Adapter 完成必要的原生配置与重连，然后按模型、推理强度、权限、其他配置、工作模式、消息的顺序应用草稿。
- 消息发送完成后保留本轮选择供下一轮继续使用；切换到另一条历史会话时，再用该会话公布的当前值初始化它的输入草稿。
- Agent 返回局部配置更新时，只替换它实际公布的配置项或类别，不能删除未包含在本次更新中的固定能力目录。
- 配置缺失时隐藏对应组件，不显示“适配通过/失败”给用户。
- 状态更新只刷新相关控件，不清空会话、不重建输入器、不改变滚动位置。
- 页面绘制和列表绑定期间不得扫描文件、探测进程或检查网络。

Provider 目录只有三种更新入口：用户自定义由 Kite 编辑页直接保存；无需登录的免费目录由 Kite 随版本提供
首次本地快照，之后只在用户于 Provider 管理页主动下拉时由 Adapter 扫描并原子替换该免费来源；刷新期间
继续展示旧快照，失败时也保留旧快照。打开管理页、进入会话和普通状态刷新都只读本地目录。官方登录目录
不参加刷新，只在一次官方登录成功并取得目录后保存版本。旧版原生自定义 Provider 只允许通过带标记的
一次性迁移吸收，不能成为长期回填通道。

## 新 Agent 接入顺序

1. 登记稳定 `agentId`、`adapterId` 和连接方式；ACP 可用时优先 ACP。
2. 读取真实模型来源、模型 ID、权限、推理能力和工作模式，不从产品文案猜测。
3. 在该 Agent 目录声明模型、权限、推理和工作模式映射；没有的能力不声明。
4. Adapter 把 SDK 意图翻译为原生配置或协议请求，并在写入后重新读取事实。
5. 运行 Adapter 合同测试、架构守卫、完整单测和 Debug 构建。
6. 最后按真实启动、配置、发消息和能力切换路径进行真机验收。
