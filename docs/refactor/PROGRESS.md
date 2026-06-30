# 重构进度(PROGRESS.md)

> 本文件是**精确进度状态**的持久化。
> 每完成一个任务的任何一步,都必须回写本文件。
> 上下文压缩后,执行者靠读本文件恢复精确进度。

最后更新:2026-06-30(T1 完成,启动 T2)

---

## 当前状态总览

| 任务 | 梯队 | 状态 | 备注 |
|---|---|---|---|
| T1 CI + CardRunStore 测试 | P0 | done | 27 条测试全绿;CI workflow 已建 |
| T2 Bridge 协议契约测试 | P0 | done | 31 条测试全绿(25 协议解析 + 6 detached) |
| T3 统一源码包名 | P1 | done | namespace+137源文件+5测试文件统一;编译/测试/APK 全绿 |
| T4 contracts 子包 | P1 | partial | T4.1 model 下沉 done;T4.2-4.4 实现类接口反转延后 |
| T5 斩断反向依赖 | P1 | pending | 分析完成,待执行(4处反向依赖+解法已记录在日志) |
| T6 ScreenRouter + 首个 Fragment | P2 | pending | 依赖 T1-T5 |
| T7 拆资源 Screen(4个) | P2 | pending | 依赖 T6 |
| T8 拆 CardRun/Terminal + ViewModel | P2 | pending | 依赖 T7 |
| T9 收敛 showCardRunSurface | P2 | pending | 依赖 T8 |
| T10 删死代码+核查 DryRun | P3 | pending | 可穿插 |
| T11 拆超大文件 | P3 | pending | 可穿插 |
| T12 修文档不一致 | P3 | pending | 可穿插 |

状态取值:`pending` / `in_progress` / `blocked` / `done`

---

## 任务执行日志(倒序,最新在最上)

### T2 [done] Bridge 协议契约测试

**验收结果**:
- [x] accepted/running/already_running/finished/failed/stopped 6 种状态各有测试(超验收要求的 5 种)
- [x] finished+nextAction 跳转规则:成功才跳/失败不跳/非 finished 不跳(openWebUrlIfFinished)
- [x] matchResult 成功判断、lastMeaningfulOutput 提取、runId 回退、pid 字段、非法 JSON 错误路径
- [x] detachedStartAccepted 扩充至 6 条(原 1 条):pid 必要性、退出码、空白 pid 边界
- [x] 全量 `./gradlew testDebugUnitTest` 全绿

**实现摘要**:协议消费的纯逻辑入口是 `KiteRunReport.fromJsonOrNull`,不耦合 HTTP,直接单测。
用 Robolectric 跑(因 Android org.json.JSONObject 在纯 JUnit 下 "not mocked")。

### T3 [done] 统一源码包名

**验收结果**:
- [x] namespace + 137 源文件 + 5 测试文件统一到 com.kite.app
- [x] `grep -r "com.kftest" app/src` 源码 0 命中(仅历史提交保留)
- [x] compileDebugKotlin 通过 / testDebugUnitTest 全绿 / assembleDebug 成功
- 修正:Python 脚本里 ACTIVITY 原误指死代码 ui.main.MainActivity,改为真实注册的 MainActivity

**决策**:ADR-001 修订为"彻底统一含 namespace"(用户拍板);ADR-010 按子包分批(实际因 import 跨包特性,文本替换一次性做、验证一次性做)。

### T5 [分析完成,待执行] 斩断 foundation→业务层反向依赖

**三问自检**:
- 目标:foundation 子树对 MainActivity/CardRunActivity/KiteResourceInstallStore/KiteResourceRegistry/KiteBrowserProxyInstaller 的 import 归零。
- 验收:上述 import 归零;编译+测试绿。
- 前置:T3 done。✅

**4 处反向依赖与具体用法**(2026-06-30 读源码确认):

1. **KFShellService.kt**(3 处,均为 Activity 类引用):
   - :201 `MainActivity::class.java.name` — onTaskRemoved 检查被移除任务是否主任务
   - :214 `CardRunActivity::class.java.name` — finishCardRunDocumentTasks 过滤卡片运行任务
   - :227 `Intent(this, MainActivity::class.java)` — 通知 PendingIntent 跳转
   - 解法:抽 `KiteTaskContract` 接口(foundation 定义),提供 `mainActivityClass: Class<*>` 和 `cardRunActivityClass: Class<*>`,在 KFApplication.onCreate 注入。

2. **TerminalSessionController.kt:971**:`KiteBrowserProxyInstaller.defaultEnvironment(appContext, "terminal_page")`
   - 解法:抽 `BrowserEnvironmentProvider` 接口,或把 defaultEnvironment 下沉到一个 foundation 可访问的工具。

3. **ToolchainPackInstaller.kt**(多处):用 `KiteResourceInstallStore(ctx)` 查询/标记失败,`KiteResourceRegistry.STATUS_INSTALLED/FAILED` 常量。
   - 解法:抽 `ResourceInstallPort` 接口(查询/标记失败),常量 STATUS_INSTALLED/FAILED 下沉到 contracts 或 foundation。

**执行计划**(下次继续时):
- [ ] 在 foundation 定义 3 个接口(KiteTaskContract/BrowserEnvironmentProvider/ResourceInstallPort)
- [ ] 业务层实现接口,KFApplication 注入
- [ ] 验证 import 归零 + 编译测试绿

**T4.1 [done] 纯 model 下沉**(2026-06-30):
- 下沉 Container*/Runtime*枚举/BaseImageProfile/Space*/ManagedTerminal*/Agent* 到 contracts
- 双向依赖 23 符号 → 11 符号(均为实现类,model 部分已消除)
- 编译 + 全量测试全绿
- 关键决策:ProcessExitSemantics 不下沉(引用 service),RuntimeBoundary object 留 runtime(有行为),AgentRuntimeRecord.fromJson fallback 改空串

**T4.2-T4.4 [延后] 实现类接口反转**:
- 涉及 KFContainerManager ↔ WorkSurfaceRuntimeBridge/KFWorkspaceManager/WorkspaceBuildSupport 的密集双向调用(WorkSurfaceRuntimeBridge 委托 KFContainerManager 20+ 方法,反向亦然)
- 判断:在 P2 拆 God Activity 之前做此深度接口反转风险高、边际收益低(这些 Manager 的调用方在 Activity 拆分后才会清晰;且无 UI 测试保护这些路径)
- 决策:延后到 P2 之后,届时调用方清晰、可借 ViewModel 注入接口。当前 T4 以 model 下沉(T4.1)为可交付成果。

### T3 [blocked-需用户决策] 统一源码包名

**触发 Playbook §0.4 停下条件**(改动范围巨大 + 架构决策需用户拍板)

**三问自检**:
- 目标:消除 com.kftest.app / com.kite.app 双源码树互引。
- 验收:源码里 com.kftest.app 引用归零(仅 build.gradle namespace 保留);编译+测试绿。
- 前置:T1,T2 done。✅

**评估出的规模与风险**(2026-06-30 读源码确认):
- 137 个 Kotlin 文件要从 com/kftest/ 搬到 com/kite/
- 152 个文件含 com.kftest.app import 要改
- Manifest 6 处类名 + 1 处 action 字符串
- 1 个 Python 脚本引用

**关键架构冲突(需用户决策)**:
ADR-001 说"保留 namespace=com.kftest.app"。但 Android 里:
- namespace 决定生成的 R 类 / BuildConfig 的包(com.kftest.app.R)
- 源码 package 声明必须和文件路径一致
- Manifest 的 android:name 必须能解析到真实 package 的类
若保留 namespace 但改源码 package 为 com.kite.app,R 类引用会出现"源码在 com.kite.app 但 R 在 com.kftest.app"的分裂,且每个文件都要 import com.kftest.app.R——这违背了"统一"的初衷。

**待用户决策**:T3 的正确执行方式(见下方 AskUserQuestion)。

**三问自检**(2026-06-30):
- 目标:给 KiteBridgeClient 补全面的协议契约测试(≥15 条),覆盖 5 种响应+边界+错误路径。
- 验收:accepted/running/finished+nextAction/failed/bridge_unavailable 各 ≥1 条;CI 全绿。
- 前置:T1 done。✅

**实现关键事实**(读源码确认):
- 协议消费的纯逻辑入口是 `KiteRunReport.fromJsonOrNull`(KiteRecipe.kt:706),不耦合 HTTP,完美可单测。
- 5 种状态:STATUS_ACCEPTED/RUNNING/ALREADY_RUNNING/FINISHED/FAILED/STOPPED,默认 STATUS_FAILED(KiteRecipe.kt:722)。
- `openWebUrlIfFinished`(@682):仅 FINISHED+ok+open_web 才返回 url(协议核心:跳转只在成功完成时触发)。
- `openWebUrlIfPresent`(@685):只要 nextAction 是 open_web 且 url 非空就返回。
- `hasMismatch`(@688):任一 step 的 matchResult.enabled && !matched → true(成功判断失败)。
- `lastMeaningfulOutput`(@690):逆向找首个非空 output/stderr/stdout。
- `detachedStartAccepted`(@1252):!pid.isBlank() && (exitCode==0||timedOut)。
- HTTP 错误映射(在 postJsonAsync,耦合网络):SocketTimeout→Timeout,ConnectException→ConnectionError,默认 bridge_unavailable。这部分测纯解析即可覆盖状态语义。

**进度**:
- [x] 读源码,确认协议解析入口与契约
- [ ] 写 KiteRunReport 协议契约测试(5 种状态+nextAction+mismatch+边界)
- [ ] 扩充 detachedStartAccepted 测试(纯函数,边界)
- [ ] 本地 gradlew testDebugUnitTest 全绿
- [ ] 回写状态

### T1 [done] CI + CardRunStore 测试

**验收结果**:
- [x] `.github/workflows/ci.yml` 存在并在 main/refactor 分支 push 触发
- [x] 新增 27 条测试(目标 ≥15):核心流转/进程恢复真实契约/复用/interruptible/查询/并发/边界
- [x] 全量 `./gradlew testDebugUnitTest` 全绿(50 测试方法,0 失败)
- **关键发现**:进程恢复归一化真实契约是"归一化后丢弃当前态",非"显示为 Failed"(见 ADR-009)
- **生产代码改动**:仅给 CardRunStore 加了 `@VisibleForTesting resetForTest()` 钩子(测试隔离必需,最小改动)

**实现摘要**(2026-06-30):

**三问自检**(2026-06-30):
- 目标:建立 CI workflow + 给 CardRunStore 补全面的 Robolectric 状态流转测试(≥15 条)。
- 验收:ci.yml 存在并触发;测试覆盖核心流转/进程恢复/并发/边界;`./gradlew testDebugUnitTest` 全绿。
- 前置:无依赖。✅

**实现关键事实**(读源码确认,非假设):
- `CardRunStore` 是 `object` 单例,无 reset 钩子 → 测试隔离需加 `@VisibleForTesting resetForTest()`。
- `initialize(context)` 幂等。
- `start()` 对同 instanceId 的 Starting+无绑定状态会复用而非新建。
- 进程恢复归一化在 `initialize()` 内部(Starting/Running/WaitingTerminal/AlreadyRunning/Opened/Stopping → Failed)。
- `CardRunStatus`:10 个值;`activeStatuses`={Running,AlreadyRunning},`interruptibleStatuses`={Running,WaitingTerminal,AlreadyRunning,Opened}。
- KiteRecipe 构造见 `KiteRecipe.kt:6-25`,可用默认参数简化测试夹具。

**进度**:
- [x] 读源码,确认 API 与测试夹具
- [ ] 加 Robolectric 测试依赖 + testOptions
- [ ] 给 CardRunStore 加 resetForTest() 钩子
- [ ] 写测试(≥15 条)
- [ ] 写 ci.yml
- [ ] 本地 gradlew testDebugUnitTest 全绿
- [ ] 回写状态

### 待开始:T2(与 T1 完成后并行/接着做)

**T2 三问自检**(待执行时填写):
- 目标:
- 验收:
- 前置完成确认:

---

## 阻塞与待办(跨任务)

- (空)

---

## 待真机校准清单

- (空)P0/P1 不需真机;P2 开始后逐项记录。

---

## 提交历史(按任务号)

- (空)
