# Kite 行为合同

最后更新：2026-06-16。

这份文档记录以后修 bug 时不能修丢的行为。当前 main 不是完美金标准，
它只是回归基线：它告诉我们哪些能力已经存在，后续优化不能把它们砍掉。

## 一、全局合同

Kite 是基于 KF/KFShell 的卡片化 Android 工作台。必须保留：

- 卡片 JSON 描述工作流，不写运行态。
- 资源 manifest 描述资源身份和动作，不写 live 状态。
- `CardRunStore` 负责卡片 / run instance 状态。
- `KiteResourceInstallStore` 负责资源安装 registry 和 plan 状态。
- 终端、SH 报告、Web 是首页卡和资源卡共享的底层表面。
- 性能修复只能把重活移出热路径，不能删除功能。

硬规则：

```text
任何 ANR 修复都不能删掉：
terminal 预输入
SH 报告输出
Web 打开
资源安装状态
安装向导队列
```

## 二、首页卡片

必须保留：

- 卡片从既定目录加载。
- 启动卡片时按 launch 规则创建或复用正确 run instance。
- 首页卡状态来自最新 `CardRunStore`。
- 首页卡计时使用共享运行时间格式。
- 卡片 JSON 不写 runId、pid、status、output、timestamp。
- 桌面快捷方式启动和普通启动遵守同一运行身份语义。

允许刷新：

- launch、pull-to-refresh、onResume、store 变化时同步首页卡运行态。
- 只更新可见卡片状态，不触发资源重探测。

禁止修法：

- 因为刷新压力删掉首页卡状态/计时。
- 把运行态写回卡片 JSON。
- 首页卡刷新顺手触发 resource probe。

## 三、资源卡片

必须保留：

- 资源卡是原子安装/卸载单位。
- 高层依赖和顺序属于安装计划层。
- 安装时可以补齐依赖。
- 卸载时只卸载自己管理的文件和状态。
- 资源卡可以打开 SH 报告、终端、网页。
- installed / failed / busy 标签来自中心状态路径。

允许刷新：

- 资源列表读取缓存的资源 UI snapshot。
- install / uninstall / failed / onResume / 手动刷新时让 snapshot dirty。
- 后台探测刷新 snapshot 后通知可见页面。

禁止修法：

- 每个资源 row render 时重新探测安装状态。
- 资源卡绘制时做文件扫描、DB 全量查询、health check。
- 修 UI/ANR 时改 Hermes WebUI 脚本或 manifest。
- 修渲染时改安装完成判定。

## 四、安装向导

必须保留：

- 展示目标资源和完整安装计划。
- 队列行按 pending -> running -> done / failed / blocked 变化。
- 顶部状态和 active 队列行同步。
- running 行显示 live elapsed。
- completed / failed 行固定最终耗时。
- 队列行能打开对应 run 报告或表面。
- 安装工作继续时，安装向导可以一直开着。

允许刷新：

- plan / registry 变化产生 revision 或 dirty signal。
- 当前向导可见时消费 revision，只更新顶部状态和受影响队列行。
- elapsed ticker 只更新可见 elapsed label。

禁止修法：

- 每秒重建整个安装向导。
- 队列一变就重建整个资源页。
- 需要用户返回/重新进入才能看到状态。
- 为了 UI live 改安装脚本或完成判定。

## 五、SH 报告

必须保留：

- SH progress 更新 `CardRunStore.shellReportText`。
- 报告页展示当前 run 的 `shellReportText`。
- 当前页面打开时，LIVE 输出能持续出现。
- run 活跃时 elapsed 持续增长。
- 完成/失败/停止后，输出和最终耗时保留。
- 复制输出使用当前展示的同一份报告文本。

允许刷新：

- shell progress 到达后更新 `CardRunStore`。
- 当前报告页匹配同一个 run instance 时，只更新 output TextView。
- 轻量 ticker 只更新当前报告页 elapsed TextView。

禁止修法：

- shell progress 调用整页 `showCardRunSurface(...)`。
- 新增第二套 SH 报告数据源。
- 修报告绑定时改 SH 执行/流式机制。
- 为了不卡静默丢弃大段报告；如果需要截断，必须设计可见的 bounded display 策略。

## 六、终端表面

必须保留：

- terminal step 创建 terminal session。
- run state 记录 `terminalSessionId`。
- `PendingTerminalFlow` 负责用户完成后进入下一步。
- 预设 terminal text / command 会发送到创建出的 session。
- terminal 命令注入不依赖页面重建速度。
- 首页卡和资源卡都能打开 terminal detail surface。

允许刷新：

- terminal session snapshot 更新终端 UI 和任务列表。
- 卡片/报告表面通过 `CardRunStore` 观察 terminal 绑定。

禁止修法：

- 为了降低 UI 压力删掉或延迟预输入。
- 无关资源/报告/安装向导刷新时清掉 `PendingTerminalFlow`。
- 把 terminal attach 慢当成可以丢命令。
- 修报告/安装向导 live 时改 terminal 执行语义。

## 七、Web 表面

必须保留：

- `open_web` 打开目标本地 URL。
- 外部 auth / login 必要时能跳系统浏览器。
- `nextActionUrl` 存在 run state。
- Web diagnostics 绑定 recipe / run 上下文。
- 资源卡和首页卡都能使用 Web 表面。

允许刷新：

- Web 只在明确 route / open 事件时加载。
- Web diagnostics 独立更新。

禁止修法：

- 资源 install dirty signal 触发 WebView 重建/reload。
- 无关状态刷新时清掉 `nextActionUrl`。
- 修 runtime live 时改 WebUI 安装逻辑。

## 八、状态归属表

| 状态 | 归属 |
| --- | --- |
| resource installed / failed / installing | `KiteResourceInstallStore` |
| install plan step status | `KiteResourceInstallStore` |
| run status / output / session / url | `CardRunStore` |
| terminal session lifecycle | terminal runtime/store |
| WebView loaded page | Web surface |
| 可见 TextView / row / button | UI 绑定 |

## 九、新功能默认归类

新增任何同步能力，先判定：

1. 静态/重探测：资源静态快照或专门 cache lane。
2. 动态 run 状态：`CardRunStore`。
3. 资源安装状态：`KiteResourceInstallStore`。
4. 终端专属：Terminal Runtime。
5. Web 专属：Web Surface。
6. 只影响文本/行/耗时显示：UI 绑定。

不能判定时，先做诊断，不先写共享刷新逻辑。

## 十、未来 bug 模板

每个 bug 实现前先变成这张卡：

```text
Symptom:
Visible surface:
Expected state owner:
Actual state owner:
Event that should change state:
View that should update:
Forbidden code paths:
Regression checks:
```

如果 state owner 不清楚，先加日志和复现证据。

