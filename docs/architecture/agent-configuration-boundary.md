# Agent 配置显示层与 SDK 边界

## 目标

Kite 只维护一套模型、权限和推理强度的显示语义，也只开放一套 SDK 配置端口。具体 Agent 的协议、原生 ID、配置文件和能力差异全部留在 ACP 或专用 Adapter 中。

以后增加或调优一个 Agent 时，正常改动范围应当位于该 Agent 的兼容目录。若必须修改公共合同，先证明是所有 Agent 都需要的新语义，而不是当前工具的特殊值。

## 固定调用链

```text
Agent 会话与设置 UI
        ↓ 只提交本轮输入草稿或持久配置意图
Kite Agent SDK
  - AgentSessionControlApi：本轮输入草稿中的模型、权限、推理强度
  - AgentConfigurationApi：默认模型、Provider、MCP、Skill、核心文档
        ↓ 点击发送后，按稳定 ID 把本轮草稿映射为原生动作
ACP 或 AdapterBackedAgentConfigurationApi
        ↓ 翻译原生协议、命令、配置文件和 ID
具体 Agent / Provider / Model
```

会话输入器整体视为 Kite 管理的本轮草稿：文字、附件、模型、权限和推理强度在发送前都只改变本地草稿。点击发送后，运行层先把模型、推理强度和权限映射为当前 Agent 的原生值或动作，再提交消息。草稿选择保留给下一轮，远端会话加载态和消息返回不能决定固定入口是否存在。

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

## 文件拆分规则

1. 每个 Agent 的模型、权限、推理值、Provider 和原生配置规则分别放在该 Agent 的兼容目录；不再新增“所有 Agent 映射大全”。
2. 公共文件只容纳无产品名、无原生路径、无工具 ID 的合同或算法。
3. UI 文件按固定控件、页面状态、列表、表单字段和页面协调拆分。新增配置能力时优先增加独立组件或协调器，不继续扩大总页面文件。
4. 两个 Agent 恰好使用同一字符串不构成公共语义；只有合同含义相同且能够独立测试时才抽取 helper。
5. ACP 已提供稳定能力时直接投影；ACP 缺失的部分由专用 Adapter 补齐，但结果仍必须经 SDK 返回。

## 状态与交互

- 页面提交用户意图，不保存第二份长期配置事实；当前本轮草稿由运行注册表统一持有。
- 选择模型、权限、推理强度或工作模式时只更新本轮草稿，不调用 Agent，不写原生配置。
- 点击发送时，运行层按模型、推理强度、权限、其他配置、工作模式、消息的顺序应用草稿；具体原生值和动作由 ACP 或 Adapter 映射。
- 消息发送完成后保留本轮选择供下一轮继续使用；切换到另一条历史会话时，再用该会话公布的当前值初始化它的输入草稿。
- Agent 返回局部配置更新时，只替换它实际公布的配置项或类别，不能删除未包含在本次更新中的固定能力目录。
- 配置缺失时隐藏对应组件，不显示“适配通过/失败”给用户。
- 状态更新只刷新相关控件，不清空会话、不重建输入器、不改变滚动位置。
- 页面绘制和列表绑定期间不得扫描文件、探测进程或检查网络。

## 新 Agent 接入顺序

1. 登记稳定 `agentId`、`adapterId` 和连接方式；ACP 可用时优先 ACP。
2. 读取真实模型来源、模型 ID、权限和推理能力，不从产品文案猜测。
3. 在该 Agent 目录声明模型、权限和推理映射；没有的能力不声明。
4. Adapter 把 SDK 意图翻译为原生配置或协议请求，并在写入后重新读取事实。
5. 运行 Adapter 合同测试、架构守卫、完整单测和 Debug 构建。
6. 最后按真实启动、配置、发消息和能力切换路径进行真机验收。
