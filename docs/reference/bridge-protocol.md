# 本地 Bridge 合同

## 两条本地通道

Kite 使用两类本地通信：

1. Android 侧本地 HTTP 服务，接收 PRoot/终端发出的打开网页、打开显示面和安装 APK 等请求。
2. Kite Bridge Client，把 Recipe、命令和停止请求交给 KF/KFShell 执行面。

两类通道都是本机控制面，不是公开网络 API。

## Android 本地服务

默认绑定：

```text
127.0.0.1:8791
```

稳定入口包括：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/status` | 本地服务存活状态 |
| `GET` | `/capabilities` | 当前 Android/Kite 能力摘要 |
| `GET`/`POST` | `/open-web` | 把 URL 交给对应卡片或网页入口 |
| `GET`/`POST` | `/install-apk` | 请求 Android 安装 APK |

`/open-desktop` 和 `/browser-automation/*` 属于实验能力，不纳入稳定 Bridge 合同。

PRoot 内的 `kite-open-url` 命令会把 URL 发到 `/open-web`。调用方可以附带 recipe、instance 和来源信息，Kite 再通过正式动作编排确定目标窗口。

## KF/KFShell 执行入口

Bridge Client 使用 JSON 请求调用执行面：

| 路径 | 用途 |
| --- | --- |
| `/run-recipe` | 执行一份 Recipe |
| `/run-command` | 执行命令型动作 |
| `/stop-run` | 按运行身份停止一次执行 |
| `/stop-recipe` | 按 Recipe 入口请求停止 |

具体 Bridge 地址由应用运行环境提供，Recipe 和第三方资源不能覆盖它。

## 身份字段

运行请求应携带足够身份来防止旧回调影响新运行：

- `instanceId`
- `generation`
- `runId`
- `stepIndex` / `stepId`
- root owner 与 leaf owner
- 终端 session ID（存在终端时）

页面标题、Recipe 名称和命令文本不能代替运行身份。

## 输出标记

Bridge 和资源安装会在普通输出中附带结构化标记，例如：

```text
KITE_RESOURCE_STEP ...
KITE_RESOURCE_HEARTBEAT ...
KITE_RESOURCE_FAILURE ...
__kite_root_pid:...
__kite_process_group_id:...
__kite_owner_stop_outcome:...
__kite_stop_remaining:...
```

标记用于状态拥有者解析事实。UI 不应把整段内部协议直接弹给用户；用户界面投影为简短状态，原始标记保留在诊断或运行报告中。

## 停止合同

停止调用必须异步执行。UI 线程只提交意图，后台队列完成 owner 发现、信号发送和复核。实例逻辑状态单向进入 `Stopped`；未及时退出的旧代进程进入后台补偿，不能恢复旧实例。

## 安全要求

- 服务只绑定回环地址。
- 不接受 Recipe 覆盖 Bridge 地址或传输方式。
- 日志不得输出真实 OAuth code、token、Cookie 或完整凭据。
- 破坏性动作必须使用结构化运行身份；来源不完整时返回可解释结果，不能猜测目标。
- 不应通过 ADB forward、代理或端口映射把本地控制面暴露给不可信网络。
