# Kite Agent 模型来源受控编辑决策

## ADR-001 显示名称偏好以 Kite 来源 ID 与真实模型引用为键

- 日期：2026-08-01
- 状态：已接受
- 背景：自定义 Provider 的原生模型 ID、协议发现模型的 `AgentConfigChoice.value`、官方账号的合成 Kite 来源 ID 属于不同身份空间。显示名称若反向替代真实值，会破坏默认模型、恢复、发送和适配器匹配。
- 决定：
  - Kite 模型来源投影必须显式区分 `sourceId`、真实协议分组和真实模型引用。
  - 显示名称偏好仅由 `AgentModelLibraryStore` 保存，并通过稳定 Kite 来源 ID 查找。
  - 自定义 Provider 的偏好模型键使用原生模型 ID；协议发现的免费/官方模型键使用原始 `AgentConfigChoice.value`。
  - 所有 UI 仅替换 `AgentConfigChoice.name/description`；`value`、原生模型 ID、Provider ID 和官方模型目录保持不变。
- 原因：显示与请求从数据结构上分开，既兼容现有自定义 Provider 数据，也能支持系统来源，不需要按具体 Agent 或模型特判。
- 影响：模型库策略、草稿策略和系统来源编辑 UI 需要共享同一投影合同；请求层和认证层无需修改。

## ADR-002 系统来源只写 Kite 展示偏好

- 日期：2026-08-01
- 状态：已接受
- 背景：免费/官方来源由系统或 Agent 目录提供，用户只获准调整 Kite 显示名称、会话可见性和既有默认偏好。
- 决定：系统来源编辑保存时只调用 `AgentModelLibraryStore`，不构造 `AgentPersistentConfigChange.ConfigureProvider`，不显示 URL、API Key、可编辑模型 ID或删除入口；官方认证继续由 `AgentOfficialAccountManager` 独立处理。
- 原因：保留原生配置和认证事实的唯一拥有者，避免 Kite 形成第二份 Provider 配置。
- 影响：系统来源使用独立的受控编辑页面；自定义 Provider 继续使用现有完整编辑页面。
