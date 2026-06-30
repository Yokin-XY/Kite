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
| T2 Bridge 协议契约测试 | P0 | in_progress | 接着 T1 做 |
| T2 Bridge 协议契约测试 | P0 | pending | 与 T1 并行 |
| T3 统一源码包名 | P1 | pending | 依赖 T1,T2 |
| T4 contracts 子包 | P1 | pending | 依赖 T3 |
| T5 斩断反向依赖 | P1 | pending | 依赖 T3 |
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

### T2 [in_progress] Bridge 协议契约测试

**三问自检**(2026-06-30):
- 目标:给 KiteBridgeClient 补全面的协议契约测试(≥15 条),覆盖 5 种响应+边界+错误路径。
- 验收:accepted/running/finished+nextAction/failed/bridge_unavailable 各 ≥1 条;CI 全绿。
- 前置:T1 done。✅

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
