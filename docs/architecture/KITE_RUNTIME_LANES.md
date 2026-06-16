# Kite 运行车道

最后更新：2026-06-16。

这份文档是 Kite 之后修运行类 bug 的总地图。它解决一个问题：

```text
以后每个 bug 先归车道，再在车道里修。
不能为了修性能，把别的车道功能砍掉。
不能为了修实时刷新，把整页重绘恢复回来。
```

## 一、核心原则

Kite 可以同时做到“实时”和“不卡”。冲突不在同步本身，而在把不同重量、
不同频率、不同生命周期的东西塞进同一条 UI 刷新链。

正确模型：

```text
重活 -> 后台 / 缓存车道
动态状态 -> store / signal 车道
可见 UI -> 局部绑定车道
终端 / Web / 报告 -> 各自 runtime 车道
```

错误模型：

```text
timer / progress / 状态变化
-> showCardRunSurface()
-> 整页重建
-> 顺手重探测 / 重查库 / 重建 Web / 重绑终端
```

以后任何修复都要先回答：

```text
这个 bug 属于哪条车道？
状态事实由谁拥有？
变化事件从哪里来？
当前可见页面只需要更新哪一块？
哪些跨车道东西绝对不能碰？
```

## 二、刷新频率梯队

每个数据源必须先进入一个梯队，不能直接丢进 UI。

| 梯队 | 含义 | 例子 | UI 规则 |
| --- | --- | --- | --- |
| 冷数据 | 重、慢、很少变 | resource catalog、manifest、文件扫描、安装探测、health check、SQLite 全量 snapshot | 不能在 render 里同步执行 |
| 温数据 | 事件驱动变化 | resource plan、installed/failed/busy 标签、card runtime 摘要 | dirty/signal 后刷新缓存 |
| 热数据 | 用户正在看的 live 状态 | SH 输出、run status、elapsed、wizard 当前行、terminal attach | 只更新当前可见小区域 |
| 控制链路 | 不能错序的输入/动作 | terminal 预输入、stop/cancel、open web、auth callback | 保序、打日志、不能被 UI 慢速影响 |

## 三、车道 1：资源静态快照

负责：

- `resourceCatalog(...)`
- manifest 解析
- workspace 探测
- `ToolchainPackInstaller` 安装状态探测
- `resourceProductVerified`
- 资源卡展示元数据

允许：

- 后台构建资源 UI snapshot。
- install / uninstall / failed / onResume / 手动刷新时标记 dirty。
- UI 只读最新轻量 snapshot。

禁止：

- render 过程中做 `File.exists`、`listFiles`、manifest scan、toolchain probe、health check。
- 每画一行资源卡就重新探测一次安装状态。
- 为了修资源标签不准，把资源页变成每秒整页刷新。

bug 归类：

- 资源标签错：先归资源静态快照。
- 资源列表卡死：先归资源静态快照。
- manifest 修改后 UI 不更新：资源静态快照 + dirty 条件。

## 四、车道 2：资源安装计划

负责：

- `KiteResourceInstallStore`
- plan step：pending / running / done / failed / blocked
- install / uninstall registry entry
- target resource
- plan 顺序
- 安装向导顶部状态和队列行状态

允许：

- store 内部读写 SQLite。
- plan / registry 变化后发 revision 或 dirty signal。
- 当前安装向导可见时，只更新顶部文字、按钮和受影响队列行。

禁止：

- UI render 每秒轮询 plan / registry。
- 为了更新一个队列行，重建整个资源 catalog。
- 把安装完成判定放进渲染副作用。
- 修 UI 实时性时改安装完成规则。

bug 归类：

- 队列行一直“排队”：资源安装计划。
- 顶部“安装中/完成/失败”和队列行不同步：资源安装计划 + UI 绑定。
- 完成/失败后不自动变：资源安装计划 + UI 绑定。

## 五、车道 3：卡片运行状态

负责：

- `CardRunStore`
- run instance identity
- run status
- step index
- `runId` / `pid` / `terminalSessionId` / `nextActionUrl`
- `shellReportText`
- `createdAt` / `updatedAt`

允许：

- 运行态存在本地 store，不写入卡片 JSON。
- bridge progress / result / 用户动作 更新 `CardRunStore`。
- 首页卡、报告页、安装向导子 run、任务页都从同一个状态源读取。

禁止：

- 把 SH 报告复制到资源卡自己的状态里。
- 把 runId、pid、状态、输出写回卡片 JSON 或资源 manifest。
- 为了修一个页面，新建第二套 run state。
- 优化报告刷新时丢掉 `terminalSessionId` / `nextActionUrl`。

bug 归类：

- 报告输出旧：先看 `CardRunStore` 是否更新，再看 UI 绑定。
- 首页卡状态错：卡片运行状态。
- 安装向导子报告打开错 run：卡片运行状态。

## 六、车道 4：SH 报告

负责：

- bridge shell progress
- `handleShellProgress(...)`
- streaming report text
- 报告 output TextView
- 报告 elapsed TextView

允许：

- shell progress 到达后更新 `CardRunStore.shellReportText`。
- 当前报告页显示的是同一个 run instance 时，只更新 output TextView。
- 报告页可见时，启动轻量 elapsed ticker，只更新时间 TextView。

禁止：

- progress 事件调用 `showCardRunSurface(...)`。
- 每秒 elapsed tick 重建整个报告页。
- UI 自己去轮询 bridge 输出。
- 没证据证明 progress 没到达时，去改 SH 执行/流式机制。

bug 归类：

- progress 到了，store 更新了，UI 不变：SH 报告 + UI 绑定。
- progress 到了，store 没更新：卡片运行状态。
- progress 本身不到：bridge / SH 执行链，不归 UI。
- elapsed 停住：SH 报告 ticker 绑定。

## 七、车道 5：终端 Runtime

负责：

- `TerminalRuntimeHost`
- terminal session 创建 / 切换 / 关闭
- terminal command 预输入
- `PendingTerminalFlow`
- terminal detail fragment
- terminal session snapshot store

允许：

- 先创建 session，再绑定 run state，再排队/发送预输入。
- 打日志证明：recipe id、instance id、session id、命令长度、enqueue/send。
- terminal 命令注入不依赖页面是否重建完成。

禁止：

- 让预输入依赖 `showCardRunSurface(...)` 是否跑完。
- 报告/资源/安装向导刷新时清掉 `PendingTerminalFlow`。
- 为了修 ANR 延迟、删除或跳过 terminal 预输入。
- 把“终端打开慢”和“命令根本没发送”混成一个 bug。

bug 归类：

- 终端打开但命令没进去：终端 Runtime。
- 有 session 但 run state 没记录：卡片运行状态。
- 命令已发送但终端 UI 慢：终端 UI/render，不改预输入链路。

## 八、车道 6：Web Surface

负责：

- WebView surface
- 本地 URL loading
- 外部 auth / open-web route
- `nextActionUrl`
- Web diagnostics

允许：

- WebView 生命周期独立于资源刷新、报告刷新。
- `nextActionUrl` 通过 `CardRunStore` 进入 run state。
- 只有明确 route/action 事件才 open web/auth。

禁止：

- 资源状态变化导致 WebView 重建或 reload。
- 优化报告/资源状态时清掉 `nextActionUrl`。
- 为了修 Web route，改资源安装完成逻辑。

bug 归类：

- Web 没打开：Web Surface + 卡片运行状态。
- auth route 丢了：Web Surface。
- WebView 意外 reload：Web Surface + UI 绑定。

## 九、车道 7：UI 绑定

负责：

- 当前可见 TextView / row / button
- 局部 updater handle
- screen-local diff/apply
- scroll position 保持

允许：

- 当前报告页绑定一个 run instance。
- 当前安装向导绑定 plan/registry revision。
- 一秒 tick 只更新当前可见 elapsed label。
- store 正确时，只把对应小控件更新掉。

禁止：

- 在 UI 绑定层做探测、DB 查询、health check。
- 为了状态新鲜就整页重建。
- 每秒更新隐藏页面。
- 一个页面 updater 改另一个页面的状态事实。

bug 归类：

- store 正确但屏幕不变：UI 绑定。
- 必须滑动/返回/重新进入才刷新：UI 绑定。
- progress 造成页面跳动：UI 绑定。

## 十、车道 8：诊断与回归

负责：

- 确定性日志
- adb/logcat 检查
- ANR / input timeout 证据
- baseline 和实验分支对照

允许：

- 增加或使用 handoff 日志。
- 用固定脚本、固定 runId、固定页面验证。
- 先证明事件到没到、store 变没变、UI 更新没更新。

禁止：

- 只看截图猜。
- 只说“好像不卡了”但不证明功能还在。
- 隐藏异常或关闭 ANR 提示。

bug 归类：

- 原因未知：先归诊断车道。
- 回归不清楚：baseline 对照后再归车道。

## 十一、默认 bug 路由表

| 现象 | 第一归属 | 第二归属 |
| --- | --- | --- |
| 全局 “Kite 没有响应” | 诊断 | 资源静态快照 / UI 绑定 |
| 资源页卡死 | 资源静态快照 | UI 绑定 |
| 安装向导卡死 | 资源安装计划 | UI 绑定 |
| 安装向导不实时 | 资源安装计划 | UI 绑定 |
| SH 输出不实时 | SH 报告 | 卡片运行状态 |
| SH elapsed 停住 | SH 报告 | UI 绑定 |
| 首页卡状态错 | 卡片运行状态 | UI 绑定 |
| 终端预输入丢失 | 终端 Runtime | 卡片运行状态 |
| Web URL 没打开 | Web Surface | 卡片运行状态 |
| stop/cancel 没停实际工作 | 对应 runtime 车道 | 资源安装计划 / 终端 Runtime |
| onResume 后状态旧 | 对应 store 车道 | UI 绑定 |

## 十二、改动前检查

任何非小修都先写清楚：

```text
Lane:
State owner:
Event source:
Visible consumer:
Forbidden cross-lane changes:
Regression checks:
```

如果写不出来，先打日志，不先改共享刷新逻辑。

