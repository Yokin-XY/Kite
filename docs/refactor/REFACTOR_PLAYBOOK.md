# Kite 重构执行手册(Playbook)

> 这是整个重构的 **source of truth(唯一事实来源)**。
> 对话里的讨论若与本文档冲突,**以本文档为准**。
> 每完成一个任务,必须回写本文件的进度状态。

本文件解决两个核心问题:**上下文压缩后如何恢复**、**长任务如何防止目标漂移**。
所有任务状态、验收标准、当前进度都持久化在这里,不依赖对话记忆。

---

## 0. 自治理机制(给执行者自己看的)

### 0.1 每次开始工作前,必做的"开机自检"

无论何时被唤起、无论上下文是否被压缩过,执行者都必须先做这三步:

1. **读本文件全文**(尤其是 §2 任务梯队、§3 当前进度、§4 红线)。
2. 读 `docs/refactor/PROGRESS.md`(若存在)拿精确进度。
3. 读 `docs/refactor/DECISIONS.md`(若存在)拿关键决策记录。
4. 用 `TodoWrite` 重建任务清单(状态从 PROGRESS.md 同步)。

只有完成这四步,才允许动代码。

### 0.2 上下文压缩的应对

- **任务清单不复述在对话里**,而是以本文件 + PROGRESS.md 为准。TodoWrite 只是对话内的镜像,压缩后从文件恢复。
- 任何关键决策(选 A 还是 B、为什么放弃某方案)写入 DECISIONS.md,带日期和理由。
- 每个 PR/提交的 commit message 必须引用任务号(如 `[T6]`)。

### 0.3 防止目标漂移的三道闸

每开始一个新任务前,执行者必须自问并记录在 PROGRESS.md 的"任务日志"里:

1. **这个任务的目标是什么?**(引用本文件 §2 的任务定义,不自己发明目标)
2. **完成标准是什么?**(引用本文件 §2 的验收清单,不自己降低标准)
3. **它依赖哪些前置任务已完成?**(确认 §2 的依赖已绿)

三问回答不清,不许动代码。

### 0.4 何时必须停下来问用户(而不是闷头继续)

自主执行不等于"永远不问"。以下情况**必须停下找用户**:

- 任务定义本身有歧义、需要产品决策(例如"Recipe 协议要不要向后兼容老字段")。
- 发现本文件与代码现实严重矛盾,且无法自行判断谁对(例如某文件已不存在)。
- 一个任务触发的改动范围远超其定义(范围蔓延 signal)。
- 测试持续失败且根因不在当前任务范围。
- 需要真机验证但当前环境不可用(见 §6 真机策略)。

其他情况一律自主推进。

### 0.5 真机检查习惯(继承自 AGENTS.md)

- 默认设备:1+8T(serial: 3f8bbaad)。
- 用户可见改动(首页卡片、资源卡片、安装向导、运行窗口、终端、报告、网页面)能上真机就上真机。
- 真机不可用时:必须改用自动测试覆盖,并在 PROGRESS.md 记"待真机校准",**不许跳过验证直接交付**。

---

## 1. 北极星目标(整个重构的最高准则)

> **让 Kite 从"一个人写出来的能跑的 9 万行项目"变成"有测试保护、有清晰分层、可被第二个开发者接手的工程"。**

判据:一个新人按 `docs/architecture/` 的文档,能在不读 `MainActivity` 源码的情况下,定位并修改一个资源卡片的渲染逻辑。

所有任务都服务于这个目标。任何"偏离这个目标的优化"都是漂移,要拒绝。

---

## 2. 任务梯队(执行顺序不可乱)

### 铁律

1. **P0 不通过,P1 不许动**。P0 是重构的安全网。
2. **P2 的每个任务必须独立可发布**。任何一步停下,项目仍可正常运行。
3. **每个 PR/提交:CI 绿 + 既有行为不变 + 验收清单全过**。

### 梯队总览

```
P0【安全网】T1, T2      ← 不动结构,先让现有代码可验证
P1【地基】  T3 → T4 → T5 ← 包名/分层/依赖方向,纯机械改造
P2【重构】  T6 → T7 → T8 → T9 ← 拆 God Activity,渐进迁移
P3【清理】  T10, T11, T12 ← 删死代码、拆大文件、修文档
```

### 执行依赖图

```
T1 ─┐
T2 ─┤              ┌─ T6 ─ T7 ─ T8 ─ T9 ─┐
    ├─ T3 ─ T4 ─ T5 ┤                     ├─ T10
    │   (P1 地基)   └   (P2 重构,渐进)   │   T11
    │                                       └─ T12
    └─ P0 是所有后续任务的防线
```

---

## 3. 任务详细定义

> 每个任务格式:**问题证据 → 解法 → 验收标准 → 依赖**。
> 验收标准是不可妥协的(执行时不许自行放宽),但可以经用户同意后追加。

### 梯队 P0:安全网

#### T1 建立 CI + 给 CardRunStore 补状态流转测试

- **问题证据**:测试覆盖 0.6%;`CardRunStore`(905 行)是车道 3 中心状态源,零测试;无 CI,11 个测试也没自动跑。
- **解法**:
  1. 加 `.github/workflows/ci.yml`,自动跑 `testDebugUnitTest` + `lintDebug`。
  2. 引入 Robolectric(`CardRunStore.initialize(context)` 需要 Context)。
  3. 锁死 CardRunStore 状态机契约:Starting/Running/Failed/Stopped + 进程恢复归一化 + 重复 start 复用 + interruptible 状态。
- **验收**(按 ADR-007 上调):
  - [ ] `.github/workflows/ci.yml` 存在并在 main/push 触发。
  - [ ] 新增测试覆盖:核心状态流转(Starting/Running/Failed/Stopped/进程恢复)、重复 start 复用、interruptible 状态、并发写入安全性、边界条件、错误路径。**目标 ≥15 条**(T1)。
  - [ ] 本地 `./gradlew testDebugUnitTest` 全绿。
- **依赖**:无。

#### T2 给 KiteBridgeClient 补协议契约测试

- **问题证据**:`KiteBridgeClient`(1289 行)只有 1 个 13 行单测;协议文档 §8/§9 定义的 `accepted/running/finished/failed/bridge_unavailable` 零契约测试。
- **解法**:把协议文档的状态语义翻译成断言(文档即测试)。覆盖 5 种协议响应 + 边界条件 + 错误路径。
- **验收**(按 ADR-007 上调):
  - [ ] `accepted`/`running`/`finished+nextAction`/`failed`/`bridge_unavailable` 各有 ≥1 条主路径测试。
  - [ ] 额外覆盖:超时、连接拒绝 vs 任务失败的区分、非 2xx、body 解析失败等边界/错误路径。**目标 ≥15 条**(T2)。
  - [ ] CI 全绿。
- **依赖**:与 T1 并行,无相互依赖。

### 梯队 P1:地基(机械改造,不改业务逻辑)

#### T3 统一源码包名

- **问题证据**:`namespace=com.kftest.app` + `applicationId=com.kite.app`,源码树互引,是循环依赖温床。
- **解法**:**保留** namespace/applicationId 分离(合法),但把**源码物理路径** `com/kftest/app/**` → `com/kite/app/**`,全量改 import,改 Manifest,改 Python 脚本里的 adb 字符串。
- **验收**:
  - [ ] `grep -r "com.kftest.app" app/src` 在源码里 0 命中(仅 build.gradle 的 namespace 保留)。
  - [ ] 编译通过,CI 绿。
  - [ ] 真机启动 App 正常(P1 末尾一次性真机校准)。
- **依赖**:T1, T2(测试兜底)。

#### T4 抽 contracts 子包,消除 runtime ↔ workspace 双向依赖

- **问题证据**:22 个 runtime 文件 import workspace,反向也存在。
- **解法**:把两子包都依赖的共享 data class/enum 下沉到新 `foundation/contracts` 子包,让 runtime 和 workspace 单向依赖 contracts。
- **验收**:
  - [ ] `grep -l "import com.kite.app.foundation.workspace" runtime/**` 空。
  - [ ] `grep -l "import com.kite.app.foundation.runtime" workspace/**` 空。
  - [ ] 编译通过,CI 绿。
- **依赖**:T3(统一包名后再搬路径更干净)。

#### T5 斩断 foundation → 业务层的反向依赖

- **问题证据**:
  - `KFShellService.kt:21-22` import `MainActivity/CardRunActivity`
  - `ToolchainPackInstaller.kt:5-6` import `KiteResourceInstallStore/KiteResourceRegistry`
  - `TerminalSessionController.kt:4` import `KiteBrowserProxyInstaller`
- **解法**:接口反转。foundation 定义接口,业务层实现,在 `KFApplication.onCreate` 注入。
- **验收**:
  - [ ] foundation 子树对 `com.kite.app.MainActivity/CardRunActivity/resources.Kite/bridge.KiteBrowserProxyInstaller` 的 import 归零。
  - [ ] 编译通过,CI 绿,真机启动正常。
- **依赖**:T3。

### 梯队 P2:重构(拆 God Activity,渐进迁移)

> 策略:**抽屉式抽取**。先搭框架,用一个最简单的 Screen 验证整套机制,再批量迁移。
> **绝不一次性大爆炸重写**。

#### T6 引入 ScreenRouter + 抽出第一个 Fragment(样板)

- **问题证据**:MainActivity 内 17 个 Screen 分支(`MainActivity.kt:19464`),921 个函数挤在一个类。
- **解法**:
  1. 建 `ScreenRouter` 管理 Screen 切换,过渡期老路径用 `navigateLegacy` 兜底。
  2. 抽 `RecipeDetailFragment` 作为样板(最简单的 Screen)。
  5. 验证机制跑通(编译 + 启动 + 真机走一遍)。
- **验收**:
  - [ ] `RecipeDetail` Screen 完全由 Fragment 渲染。
  - [ ] MainActivity 里对应的老函数已删除。
  - [ ] 真机走通进入 RecipeDetail 的流程。
  - [ ] 其他 Screen 仍走老路径不受影响。
- **依赖**:T1-T5 全部完成。

#### T7 拆资源系列 Screen(4 个)

- **问题证据**:Resources/ResourceSearch/ResourceDetail/ResourceManage + 安装向导,违反 AGENTS.md 最严重(`removeAllViews()` 20+ 处)。
- **解法**:按 T6 模式逐个抽 Fragment,**每抽一个发一个 PR**。同时修掉 `refreshDropZoneRecipes(showToast=true)`(@1999)这类"用 Toast 表进度"的违规。
- **验收**:
  - [ ] 5 个资源相关 Fragment 存在并各自渲染。
  - [ ] 资源路径用 `RecyclerView + ListAdapter + DiffUtil` 替代 `LinearLayout+removeAllViews`。
  - [ ] `showCardRunSurface` 在资源路径上的调用点归零。
  - [ ] 真机走通资源浏览/搜索/详情/管理/安装向导。
- **依赖**:T6(样板机制)。

#### T8 拆 CardRun / Terminal Screen + 引入 ViewModel

- **问题证据**:`CardRunActivity` 只 3 行,运行窗口是 MainActivity 的 Screen 分支;UI 无 ViewModel。
- **解法**:拆成 Fragment + 首次引入 `ViewModel` 作为 UI 与 Store 间防腐层。
- **验收**:
  - [ ] CardRunFragment + CardRunViewModel + TerminalFragment 存在。
  - [ ] CardRunActivity 启动路径全程走 Fragment+ViewModel,无 `removeAllViews`。
  - [ ] CardRunStore 订阅改走 `vm.uiState.collect { render(it) }`。
  - [ ] 真机走通卡片运行 + 终端。
- **依赖**:T7。

#### T9 收敛 showCardRunSurface 30+ 调用点

- **问题证据**:`showCardRunSurface()`(@8347)被调 30+ 处响应普通状态变化。
- **解法**:T8 的收尾。30+ 调用点全改为 `vm.handleIntent(...)`,由 ViewModel 的 uiState.collect 做局部 diff。
- **验收**:
  - [ ] `grep -c "showCardRunSurface" MainActivity.kt` 归零或仅剩初始化入口。
  - [ ] 真机:状态变化只局部更新,不重建 surface(对照 AGENTS.md"信号即局部更新")。
- **依赖**:T8。

### 梯队 P3:清理(低风险收尾)

#### T10 删死代码 + 核查 DryRun 文件

- **问题证据**:`com.kftest.app.ui.main.MainActivity`(795 行)未注册无引用;33 个 `*DryRun.kt` 疑似未接入。
- **解法**:删未注册 MainActivity;`grep` 核查 33 个 DryRun,无引用的删,有引用的保留并补文档。
- **验收**:
  - [ ] 死 MainActivity 删除。
  - [ ] DryRun 文件核查清单完成(每个文件标注:删/留+理由)。
  - [ ] CI 绿。
- **依赖**:无(可穿插)。

#### T11 拆超大文件

- **问题证据**:`RuntimeHealthStore.kt`(3166 行)、`ProotTelemetryStore.kt`(1803 行)model 与 store 混杂。
- **解法**:把 model 定义与 store 逻辑分文件,单文件 ≤500 行。纯机械搬运。
- **验收**:
  - [ ] 两个文件各自拆分,单文件 ≤500 行。
  - [ ] CI 绿。
- **依赖**:无(可穿插)。

#### T12 修文档-代码不一致

- **问题证据**:Shizuku 非目标 vs 依赖冲突;Bridge 端口 8799/8648 混淆;行为合同未落地。
- **解法**:更新 `KITE_V0_1_ARCHITECTURE.md` Shizuku 表述;集中澄清端口;标注行为合同落地状态。
- **验收**:
  - [ ] Shizuku 冲突段落更新。
  - [ ] 端口角色集中澄清。
  - [ ] 行为合同各条款标注落地状态。
- **依赖**:无(可穿插)。

---

## 4. 红线(任何时候不可触碰)

1. **绝不跳过验证交付**。每个任务必须有"CI 绿 + 行为不变"两证之一(优先 CI)。
2. **绝不为通过测试而改测试期望值**。测试是契约,代码是实现,冲突时改代码不改测试(除非证明测试本身错了,且需在 DECISIONS.md 记录)。
3. **绝不一次性大爆炸重写 MainActivity**。必须抽屉式,T6 验证机制后才能 T7/T8。
4. **绝不擅自放宽验收标准**。标准在本文件,要改需用户同意。
5. **绝不把任务清单只存在对话里**。状态必须回写 PROGRESS.md。
6. **P2 期间,任何 Fragment 抽取失败必须可回滚**,不让 main 进入不可运行状态。

---

## 5. 每个任务的标准执行流程(SOP)

每个任务按这 7 步执行,不省略:

1. **开机自检**:读本文件 + PROGRESS.md + DECISIONS.md。
2. **三问自检**:目标?验收?前置是否完成?写入 PROGRESS.md 任务日志。
3. **实现**:按解法写代码,小步提交,commit message 带 `[Tn]`。
4. **自测**:`./gradlew testDebugUnitTest` 全绿 + 必要时 lint。
5. **验证**:对照验收清单逐项打勾。
6. **回写**:更新 PROGRESS.md 状态 + 任何决策到 DECISIONS.md。
7. **触发下一个**:按依赖图找下一个就绪任务,回到第 1 步。

---

## 6. 真机策略

- 默认 1+8T(serial: 3f8bbaad)。
- P0/P1 主要是后端逻辑/包名/测试,不需真机(CI 即可)。
- P2 每个 Fragment 抽取后**必须真机校准**(影响用户可见流程)。
- P3 文档类无需真机。
- 真机不可用时:用自动测试替代,并在 PROGRESS.md 标 `待真机校准`,不跳过。

---

## 7. 完成判据(整个重构的终点)

当且仅当以下全部满足,重构完成:

- [ ] 12 个任务全部 status=done,验收清单全勾。
- [ ] CI 存在且绿。
- [ ] `grep -c "com.kftest.app" app/src` 在源码里为 0(仅 build.gradle namespace 保留)。
- [ ] `MainActivity.kt` 行数显著下降(P2 完成后应从 19591 降到数千行以内)。
- §1 北极星判据:新人不读 MainActivity 源码即可定位修改一个资源卡片渲染逻辑。
