# Kite Device Bridge 架构

## 目标

Kite Device Bridge 把当前 Android 设备的可用能力稳定地暴露给 Ubuntu 中运行的
Hermes、Codex、OpenCode 等 Agent。Agent 只依赖 Kite 的统一能力合同，不直接依赖
Shizuku、Root、Android 私有 API 或具体手机品牌。

“正式接入 Shizuku”指完成这座能力桥，而不是只增加授权开关或状态页面。

## 当前基线

现有 `HostSelfAdbBridgeWorker` 是仍待替换传输层的 V0 通道：

- Ubuntu 通过共享目录写入请求，APK 轮询并返回结果。
- 对外模拟 `kf-host-self` ADB 目标。
- 支持 `shell`、`exec-out`、`push`、`pull` 和 `install`。
- Android 侧已经通过正式 `Shizuku UserService` 与 AIDL 进程合同执行；旧的
  `IShizukuService.newProcess()` 路径已经移除。
- 不支持交互式终端；结构化能力目录和命令已经由 Rust `kite-device` 提供，但底层仍
  复用这条文件传输。

该实现证明了“Ubuntu Agent 可以借助 APK 操作当前手机”，但不能作为长期正式核心。
Shizuku 已将 `newProcess` 定义为迁移接口，复杂调用应使用 `UserService`。正式路径必须
避免继续扩大对 `newProcess`、文本命令拼接和轮询目录的依赖。

## 分层结构

```text
Agent
  │
  ├─ adb -s kf-host-self ...       兼容既有工具
  └─ kite-device ...               Kite 正式结构化入口
                 │
                 ▼
Ubuntu Rust CLI / 传输协议
                 │
                 ▼
Kite Android Device Bridge Host
能力目录、策略、任务状态、流式转发、审计
                 │
                 ▼
Android Privilege Backend
  ├─ Shizuku UserService           正式默认后端
  └─ Root Backend                  后续可替换后端
                 │
                 ▼
Android shell / Binder system services / 系统 API
```

## 事实拥有者

### Android 应用进程

负责：

- Shizuku Binder 到达、死亡和授权结果信号。
- 当前后端、身份、Android 版本和能力快照。
- 用户确认策略和用户可见状态。
- Ubuntu 请求的接收、鉴权、调度、取消和结果转发。
- 普通应用进程才能可靠完成的 `Context`、包变化和前后台生命周期工作。

页面只消费状态和提交动作，不扫描文件、探测进程或复制一份长期事实。

### Shizuku UserService

负责：

- 在 shell UID（Sui/Root 场景可能是 root UID）下执行受控操作。
- 结构化调用 Android Binder system services。
- 命令的标准输入、标准输出、标准错误、退出码和取消。
- 服务断开后的明确失败；恢复由 Android 应用进程重新绑定。

UserService 不是普通 Android 应用进程。依赖广播、`ContentResolver` 或完整应用
`Context` 的能力继续留在应用进程，不强行放入 UserService。

### Ubuntu CLI

负责：

- 提供 `kite-device` 稳定命令和 JSON 输出。
- 将 `adb -s kf-host-self` 的已支持语义翻译为同一协议。
- 流式转发输入输出并传播退出码、超时和取消。
- 通过 `kite-device capabilities --json` 向任何 Agent 暴露实时能力。

Agent 不需要为 Shizuku、Root 或手机品牌分别适配。

## 语言放置

### Kotlin / Java

以下部分必须保留在 Kotlin/Java：

- Shizuku API、Binder、AIDL 和 UserService 生命周期。
- Android 权限请求、PackageManager、ActivityManager 和界面状态。
- 后端选择、安全策略以及任务事实持有。

这些工作受 Android 框架和 Binder 限制，不是 CPU 密集计算。为使用 Rust 而增加 JNI
层只会增加生命周期、异常和数据搬运成本。

### Rust

Ubuntu 侧 `kite-device` CLI 优先使用 Rust：

- 生成独立 ARM64 可执行文件，不依赖 Python、Node 或 JVM。
- 适合低启动开销的频繁短命令。
- 适合流式协议、取消、多路输出和严格的二进制边界。
- 可以同时实现 `kite-device` 和 ADB 兼容翻译的共享协议客户端。

Android 侧只有在真机基准证明协议编解码、流复制或摘要计算是 CPU 热点时，才把该
粗粒度模块下沉到 Rust；不得频繁跨 JNI 搬运小消息。

## 能力合同

候选能力由 `DeviceBridgeCatalog` 定义，运行时快照再声明每项能力是：

- `available`：当前后端和设备已真实支持。
- `probe_required`：合同允许，但尚未完成设备/OEM 探测。
- `unsupported`：Android 版本、后端身份或实现明确不支持。
- `blocked`：能力存在，但当前授权、服务或用户确认不满足。

第一批能力族：

| 能力族 | 典型能力 |
| --- | --- |
| Shell | 执行、流式输出、标准输入、取消 |
| 文件 | 读取、写入、推送、拉取、临时文件 |
| 应用 | 查询、安装、卸载、启动、停止、清理、权限、AppOps |
| 输入 | 点击、滑动、文字、按键、剪贴板 |
| 屏幕 | 截图、录屏、显示信息 |
| 系统 | 进程、日志、`dumpsys`、设置、电池、存储和网络信息 |

Shizuku 通常提供 shell UID，不等于 Root。任何访问其他应用私有数据、修改内核或仅
Root 可用的能力都必须返回真实的 `unsupported`，不得通过名称或命令存在性误报。

## 风险与用户确认

能力定义同时声明风险等级：

- `read_only`：读取设备状态，不改变外部状态。
- `mutating`：启动应用、输入或写入普通文件。
- `sensitive`：安装、停止应用、修改设置、权限或 AppOps。
- `destructive`：卸载、清空数据或其他难恢复动作。

`kite-device capabilities` 只声明 `transportCapabilityIds`，不把后端可达误报成当前会话
已授权。Agent 调用仍先经过其原生会话权限；目录风险供 Kite 的统一会话管理和后续
审批中介消费。Android 后端不得把“Shizuku 已授权”当成“用户已批准当前敏感操作”。

当前阶段已经统一能力风险和 Agent 调用入口，但尚未实现独立于 Agent 原生权限之外的
逐次设备操作审批弹窗。需要逐次审批的场景必须保持在 Agent 的 `Approval` 档位；在该
审批中介完成前，Kite 不宣称 Device Bridge 自身提供了第二层逐次授权。

## 协议与执行语义

- 协议有独立版本号，与 APK 版本和资源版本解耦。
- 正式协议请求必须包含唯一任务 ID、能力 ID、操作、参数和超时；当前兼容传输已经有
  任务 ID、操作、参数、超时与取消，能力 ID 仍由 Rust 命令和目录共同投影。
- 输出区分 stdout、stderr、结构化结果和最终退出状态。
- 取消是正式协议事件，退出语义固定为 `cancelled`，不能伪装成普通失败。
- Binder 死亡、授权撤销和后端切换会终止旧任务；任务开始后不得静默切换后端重放。
- 大文件和长输出使用文件描述符或流，不经 Binder/JNI 反复复制整块数据。
- 正式 Ubuntu 传输必须带本次运行生成的鉴权材料，不能仅凭本机端口或共享路径信任
  请求；当前文件桥尚未达到这一条，因此继续标记为待替换的 V0 传输，而不是最终协议。

## Root 后端

Root 使用同一能力目录、请求、事件和策略合同。无 Root 真机时可以完成：

- 后端接口和选择规则。
- `su` 不可用、拒绝和异常退出语义。
- 模拟后端合同测试。
- 非 Root 真机的安全不可用验证。

未完成真实 UID 0、授权、撤权、重启和进程清理验证前，产品中必须标记为实验性，
不能宣称 Root 已正式验收。

## Shizuku 资源卡片

资源卡片属于最后一层，只负责 APK 的发现、下载、校验和系统安装器交接。以下三个
状态必须分离：

1. Shizuku APK 是否已安装。
2. Shizuku 服务是否正在运行。
3. Kite 是否获得 Shizuku 授权。

安装卡片不得成为 Device Bridge 状态拥有者，也不得为 Shizuku 在页面中写死一条
不可复用的 APK 安装分支。

当前已落地通用 `android_apk` 资源链路：资源事务完成有界 HTTPS 下载与 SHA-256 校验，
Android 动作只打开系统安装器，后一独立动作再通过 `PackageManager` 校验清单声明的包名
和 APK 版本。包已安装但资源登记丢失时可以恢复登记；包被外部移除时会进入修复状态。
`kite.shizuku` 只声明官方制品、包身份和三种状态的说明，没有 Shizuku 专用安装分支。

资源详情中的“服务运行”和“Kite 授权”实时投影仍属于后续管理界面工作；它们必须直接
消费 `ShizukuBridgeStateOwner`，不能从“APK 已安装”推导。

## 阶段验收

1. 能力合同与语言边界：目录稳定、风险和身份规则有单元测试。
2. 生命周期：Binder 到达/死亡、授权、撤销、重启状态转换通过测试。
3. UserService：不再由正式路径调用 `newProcess`，流式输入输出和取消通过验证。
4. Ubuntu：`kite-device capabilities --json` 和基础命令可从 PRoot 调用。
5. 能力扩展：每项能力有真实探测和目标效果验证。
6. Root：先完成实验后端；真实 Root 真机验收后才能转正式。
7. 资源卡片：APK 安装、返回、包状态和授权状态各自闭环。
