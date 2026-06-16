# Kite 运行回归矩阵

最后更新：2026-06-16。

这份矩阵用于确定性验证。截图可以辅助看 UI，但不能当主要证据。
主要证据必须是：

```text
adb/logcat 事件
固定测试输入
可见状态断言
ANR 不出现
```

## 一、基线策略

当前稳定 main 是回归基线，不是完美设计。

它用来回答：

- 这个功能之前是否能工作？
- 工作路径上有哪些日志/状态？
- 哪些行为在优化后不能消失？

不能用它证明已知性能问题应该保留。也不能接受“不卡了”，但只是因为原功能被砍掉。

## 二、实时报告证据表

报告页 live 回归必须填这张表：

| 时间 | progress 到达 | `CardRunStore` 更新 | 报告页文本更新 | elapsed 更新 | 是否整页重绘 |
| --- | --- | --- | --- | --- | --- |
| T+1s | yes/no | yes/no | yes/no | yes/no | yes/no |
| T+2s | yes/no | yes/no | yes/no | yes/no | yes/no |
| T+3s | yes/no | yes/no | yes/no | yes/no | yes/no |
| T+10s | yes/no | yes/no | yes/no | yes/no | yes/no |

正确目标：

```text
progress 到达=yes
CardRunStore 更新=yes
报告页文本更新=yes
elapsed 更新=yes
是否整页重绘=no
```

## 三、固定 SH live 脚本

报告页实时性统一用这个脚本：

```sh
for i in 1 2 3 4 5 6 7 8 9 10; do
  echo "LIVE-$i $(date +%H:%M:%S)"
  sleep 1
done
```

期望：

- `LIVE-1` 到 `LIVE-10` 不离开报告页也能逐秒出现。
- run 活跃时 elapsed 每秒增长。
- 报告页不明显跳动、不整页重建。
- 执行期间和执行后都不弹 ANR。

## 四、ANR 检查

每个检查分开做：

| 页面 | 时长 | 期望 |
| --- | --- | --- |
| 资源页停留 | 60s | 不弹 “Kite 没有响应” |
| 安装向导运行中停留 | 60s | 不弹 “Kite 没有响应” |
| SH 报告运行中停留 | 60s | 不弹 “Kite 没有响应” |
| 资源列表滚动 | 手动/自动 | 不冻结 |

需要收集的 logcat 证据：

```text
ActivityManager ANR
Input dispatching timed out
Choreographer skipped frames
Kite runtime / diagnostic markers
```

如果出现 ANR，不能从截图猜。必须走：

```text
main-thread stack -> owning lane -> heavy work source -> minimal lane fix
```

## 五、功能回归矩阵

| 区域 | 必须验证 | 第一车道 |
| --- | --- | --- |
| 首页卡启动 | 正确 run instance 启动，状态更新 | 卡片运行状态 |
| 首页卡计时 | 运行时 elapsed 变化，结束后固定 | 卡片运行状态 + UI 绑定 |
| terminal step | session 创建，run 有 session id，预设命令发送 | 终端 Runtime |
| SH 报告 | progress 到 store，当前输出可见 | SH 报告 |
| Web step | `nextActionUrl` 打开 Web surface | Web Surface |
| 资源列表 | 标签来自缓存 snapshot，render 不重探测 | 资源静态快照 |
| 资源详情 | action button 和状态一致，绘制不重扫 | 资源静态快照 |
| 安装向导 | 顶部和队列行按 plan/registry 更新 | 资源安装计划 |
| 安装子报告 | 队列行打开正确 child run 报告 | 卡片运行状态 |
| cancel/stop | 先停实际工作，再清状态 | 对应 runtime 车道 |
| onResume | store 重新同步，UI 不沿用旧缓存 | 对应 store + UI 绑定 |

## 六、bug 分诊决策树

改代码前先走这个：

```text
1. App 是否卡死或 ANR？
   -> 先收 logcat / main-thread 证据。

2. 底层状态有没有变化？
   -> 有：UI 绑定。
   -> 没有：对应 store/runtime。

3. 事件有没有到达？
   -> 到了：store update bug。
   -> 没到：bridge/runtime/input lane。

4. 修复是否需要每秒整页重绘？
   -> 停，改成局部可见绑定。

5. 修复是否删了 terminal/report/web/resource 行为？
   -> 拒绝这个修法，重新分诊原 bug。
```

## 七、最小日志点

需要打日志时，只打 handoff 点：

| handoff | 日志要证明 |
| --- | --- |
| shell progress received | recipe id、instance id、run id、output signature |
| `CardRunStore.update` | instance id、status、surface、text length/session/url |
| report UI bind | visible instance id、output text length、elapsed label |
| terminal session create | recipe id、instance id、session id |
| terminal command send | session id、command length、delayed/enqueued/sent |
| install plan state change | resource id、old status、new status、revision |
| wizard row update | resource id、row status、elapsed label |
| resource snapshot refresh | reason、background/main-thread、item count、duration |
| Web open | recipe id、instance id、url、source |

避免记录超长输出正文。优先记录稳定 id、长度、签名、首尾短片段。

## 八、禁止通过的修复

出现以下任意一条，修复不通过：

- 恢复每秒 full `showCardRunSurface(...)`。
- render 中做文件扫描、DB snapshot、health check、安装探测。
- 为 UI bug 改 Hermes WebUI 安装脚本 / manifest / 完成判定。
- 没证据证明 progress 不到，就为报告绑定 bug 改 SH 执行/流式。
- 新增第二套 SH 报告数据源。
- 把 run output 复制到资源卡局部状态。
- 删除 terminal 预输入，或让它依赖页面 render timing。
- 隐藏异常或压掉 ANR 提示。

## 九、静态守门

运行设备验收前，先跑一次静态守门：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\KITE_RUNTIME_LANE_STATIC_CHECKS.ps1
```

这个脚本只做代码结构检查，用来防止以下回归：

- shell progress 又接回整页 redraw。
- terminal auth transcript 又回到 UI 路径全量 `readText()`。
- install store signal 丢失。
- report / install wizard / console card 局部 binding 被删。
- 资源管理页重新在 render 中同步构建 catalog / DB snapshot。

## 十、验收模板

每个 runtime 修复最后按这个输出：

```text
Lane:
Files changed:
State owner preserved:
Feature behavior preserved:
Whole-page redraw restored? no
Render-time heavy probe introduced? no
Regression checks:
ANR checks:
Known residual risk:
```
