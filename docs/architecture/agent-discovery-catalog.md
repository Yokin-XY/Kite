# Agent 公共发现目录

## 目的

Kite 不再为每个新 Agent 从零手工寻找包名、版本和 Android arm64 制品。用户在 Agent 设置中主动刷新时，应用读取 ACP 官方 Registry；网络失败时依次复用上次成功缓存和随应用发布的兜底快照。

目录解决的是“外部有哪些 ACP Agent、当前版本和分发形式是什么”，不替代 Kite 自己的安装、配置和运行事实。

## 三层事实

1. **外部候选目录**：`AcpAgentDiscoveryRepository` 读取 ACP Registry，只保存候选元数据。
2. **Kite 兼容目录**：声明外部 Registry ID 与 Kite 稳定 `agentId` 的关系，以及已经验证的配置、会话和运行能力。
3. **Kite Agent 名册**：`KiteAgentRegistry` 继续由资源 manifest、自定义登记和真实安装状态组成，是页面能否打开 Agent 的唯一事实源。

外部候选不能直接写进 Agent 名册。这样 Registry 中出现一个新包时，Kite 可以立刻发现它，但不会在未经过 Android、PRoot、ACP 握手和安全校验前把它伪装成可安装、可运行的 Agent。

## 刷新与回退

- 普通页面绑定只读取内存、缓存或随包目录，不触发网络请求。
- 只有用户明确点击刷新目录时才请求 `https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json`。
- 请求带 ETag；内容未变化时复用缓存。
- 在线内容必须通过大小、字段、URL、包版本和命令路径校验后才原子写入缓存。
- 在线失败或内容无效时保留最后一次成功缓存；缓存损坏时使用随包快照。

## 当前接收的分发形式

- 固定版本的 `npx` 包。
- 固定版本的 `uvx` 包。
- `linux-aarch64` 二进制归档。

Windows、macOS 和其他架构不会进入 Android 候选。二进制摘要即使存在，也只证明下载内容与发布者声明一致，不代表已经通过 Kite 兼容验证。

## 信任边界

- Registry、仓库地址、图标和描述都属于不可信外部元数据。
- 外部包不能绕过 Kite 资源发布清单、下载校验、候选目录安装和提交/回滚事务。
- 密钥、Provider 和本机会话配置仍由各 Agent 的原生配置或 Kite 已登记 Adapter 管理。
- UI 不按产品显示名猜协议或配置路径；所有复用都通过稳定 ID 和兼容能力声明完成。

## 新 Agent 接入路径

1. 公共目录自动发现候选及其当前版本。
2. 通用分发适配器准备候选，但不发布为正式资源。
3. 运行 ACP 初始化探测并记录协议能力。
4. 只为确有原生差异的配置或会话行为增加 Adapter；通用能力沿用共享实现。
5. 通过 Android/PRoot 结果验证后，兼容目录才把候选映射为 Kite 正式 Agent。

这条路径让其他项目维护版本和分发信息，Kite 只维护 Android 兼容结论；Codex、Hermes 已修复的公共协议、进程和错误语义则由共享层复用。
