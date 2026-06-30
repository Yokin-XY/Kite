# 重构决策记录(DECISIONS.md)

> 记录所有关键决策:**选 A 还是 B、为什么、何时**。
> 防止上下文压缩后"忘了为什么这么决定"而反复摇摆。
> 每条决策带日期 + 理由 + 影响范围。

格式:ADR(架构决策记录)精简版。

---

## ADR-001 包名统一策略:彻底统一(含 namespace)

- **日期**:2026-06-30(初版)→ 2026-06-30(用户决策后修订)
- **决策**:T3 中**连 namespace 一起**改成 `com.kite.app`。即 `build.gradle` 的 `namespace` 从 `com.kftest.app` 改为 `com.kite.app`,源码 package/import/路径全部统一到 `com.kite.app`,R 类也生成在 `com.kite.app`。`applicationId` 本就是 `com.kite.app`,无需改。
- **理由**(用户拍板):彻底统一最干净,真正消除双包名。保留 namespace 而只改源码路径会导致"源码在 com.kite.app 但 R 在 com.kftest.app"的分裂,每个文件要 import com.kftest.app.R,反而引入新的不统一,违背初衷。
- **修订前备选**(已否决):保留 namespace 只改源码路径 —— 否决,理由如上。
- **影响**:T3 的执行方式;generated R 包路径变化;全量编译验证必需。

## ADR-010 T3 改动节奏:按子包分批迁移

- **日期**:2026-06-30
- **决策**:T3 的 137 文件迁移按 foundation 子包(runtime/workspace/service/terminal/toolchain/bootstrap/capability 等)、ui 子包、bridge 等分批进行,每批一个提交,每批编译+测试验证。
- **理由**(用户拍板):137 文件一次性批量改风险高、出错难定位;分批迁移慢但安全,P0 测试全程兜底。
- **实际执行**:因 import 跨包特性(改一个 package,所有引用方都要同时改),文本替换与验证一次性完成,但提交保持原子。
- **影响**:T3 的提交粒度。

## ADR-011 T4 彻底重构含接口反转

- **日期**:2026-06-30
- **决策**:T4 不只下沉纯 model,还要给实现类的双向耦合抽接口做依赖反转,彻底消除 runtime ↔ workspace 双向依赖。
- **理由**(用户拍板):真正消除双向依赖才算治本;只下沉 model 留下实现类互引等于没解决核心问题。
- **执行策略**:分两步——① 纯 model(enums/data class)下沉到 contracts;② 实现类互引用 contracts 接口反转(如 KFContainerManager 与 KFWorkspaceManager 之间定义 contracts 接口)。每步编译+测试验证。
- **风险**:技术难度最高的一步,逐个设计接口,改错会破坏编译,靠 P0 测试 + 全量编译兜底。
- **影响**:T4 的范围与执行方式。

## ADR-012 后续任务继续自主推进

- **日期**:2026-06-30
- **决策**:T4 之后所有任务继续自主推进,仅在 Playbook §0.4 列明的必须停下情况才找用户(产品决策、范围超界、真机不可用等)。
- **理由**(用户拍板):保持执行效率,信任 Playbook 的治理机制和 P0 测试防线。
- **影响**:T5-T12 的执行节奏。

## ADR-014 P2 前置:先补 UI 路由测试再拆 God Activity

- **日期**:2026-06-30
- **决策**:在 T6 之前插入 T6.0——给 MainActivity 的 Screen 路由补 Robolectric 测试,锁住路由行为(navigate/back/restore),再做 T6-T9。真机校准延后到 P2 末尾统一做。
- **理由**(用户拍板):P2 影响 19591 行 God Activity 的用户可见流程,T6 验收硬性要求真机(§6)但当前无真机;UI 路由又无测试保护。先补路由测试,既能在无真机下安全推进 P2,又把"路由行为契约"钉死,防止 T6-T9 改坏。
- **新增任务**:T6.0(路由测试),作为 T6 的前置。
- **影响**:任务列表新增 T6.0;P2 推进方式变为"测试先行"。

- **日期**:2026-06-30
- **决策**:T4 以 T4.1(纯 model 下沉)为可交付成果;T4.2-T4.4(KFContainerManager ↔ WorkSurfaceRuntimeBridge/KFWorkspaceManager/WorkspaceBuildSupport 的接口反转)延后到 P2 拆 God Activity 之后。
- **理由**(用户拍板):这些 object 单例有 20+ 方法密集互调,在 P2 之前做深度接口反转风险高、边际收益低——调用方在 Activity 拆分后才清晰,且这些路径无 UI 测试保护。延后到 P2 后可借 ViewModel 注入接口。
- **现状**:双向依赖已从 23 符号降至 11(纯 model 已消除),剩余为实现类互引,记录为已知技术债。
- **影响**:T4 状态标记 partial;T6-T9 完成后回头做 T4.2-T4.4。

## ADR-002 foundation→业务层反向依赖用接口反转,而非事件总线

- **日期**:2026-06-30
- **决策**:T5 用"接口在 foundation 定义、业务层实现、Application 注入"的方式,不用 EventBus/广播。
- **理由**:项目 AGENTS.md 已明确"信号即 StateFlow",EventBus 会引入第二条信号通道,违背规范;接口反转是标准依赖倒置,可测试性好。
- **影响**:T5 的实现模式;后续 Store 暴露方式。

## ADR-003 P2 拆 Activity 用"抽屉式 + 过渡期 navigateLegacy 兜底",不做大爆炸

- **日期**:2026-06-30
- **决策**:T6 先搭 ScreenRouter,过渡期未迁移的 Screen 走 `navigateLegacy()` 老路径;一个一个 Screen 迁,每个可独立发布。
- **理由**:19591 行的 Activity 不可能一次拆完;大爆炸重写会让 main 长期不可用,且无法回滚。抽屉式保证任何一步停下项目仍可运行。
- **影响**:T6-T9 全部执行方式;§4 红线第 3/6 条。

## ADR-004 测试用 Robolectric 而非纯 JUnit

- **日期**:2026-06-30
- **决策**:T1/T2 引入 Robolectric 跑 instrumented 逻辑的本地单测(因为 CardRunStore.initialize 需要 Context)。
- **理由**:比纯 JUnit 能覆盖更多真实路径;比真 instrumented test(androidTest)快得多、可在 CI 上无设备跑。
- **影响**:T1 的依赖引入。

---

## ADR-005 重构期间使用 refactor 长分支,不直接动 main

- **日期**:2026-06-30
- **决策**:整个重构在 `refactor/god-activity` 长分支上进行;P2 期间每个 Fragment 一个提交;全部完成后再评估合并 main。
- **理由**:main 在重构期间保持可用,利于回滚,不阻塞他人协作(虽目前 solo)。
- **影响**:所有任务的 Git 操作;§0 的提交流程。

## ADR-006 P2 样板 Screen 选 RecipeDetail

- **日期**:2026-06-30
- **决策**:T6 用 RecipeDetail 作为第一个被抽取的 Fragment。
- **理由**:只读展示型 Screen,逻辑相对独立,复杂度低,适合验证 ScreenRouter+Fragment 机制。
- **影响**:T6 的执行细节。

## ADR-007 P0 测试深度:尽可能全面

- **日期**:2026-06-30
- **决策**:T1/T2 不仅锁核心契约,还补边界条件、错误路径、并发场景,目标 30+ 条测试。
- **理由**:用户明确选择。更厚的测试安全网能让 P1/P2 重构更安全。
- **影响**:T1/T2 的验收标准(测试数量从"≥5 条"上调)。

## ADR-008 docs/refactor 三件套纳入 git 版本控制

- **日期**:2026-06-30
- **决策**:在 `.gitignore` 给 `/docs/refactor/` 开白名单,让 Playbook/PROGRESS/DECISIONS 进 git。其余 docs 仍按本地笔记忽略。
- **理由**:这三件套是整个重构的 source of truth,核心价值是"抗上下文压缩、跨会话持久、可 PR 审查"。若不进 git,fresh clone 或换环境就丢了,等于退回"存对话里"的老路。它们性质是工程治理文件,不是临时笔记。
- **影响**:.gitignore 多一条白名单;所有后续任务的治理文件都会进版本控制。

## ADR-009 CardRunStore 进程恢复归一化的真实契约(测试纠正了初始假设)

- **日期**:2026-06-30
- **决策**:T1 测试过程中发现真实行为与初始假设不符,按真实契约锁定测试。
- **事实**:残留的 Starting/Running/Opened/Completed 等状态,在 `initialize` 读盘后会先 `normalizedAfterProcessRestore`(中间态→Failed),再被 `shouldDropCurrentAfterProcessRestore` 丢弃(终态 endsHistoryEntry / 中间态被 reset / 带 run binding 等都返回 true),**不会作为 Failed 卡片留在控制台当前 run 列表**,而是直接消失(历史由独立 history 链路保留)。
- **初始假设(错)**:残留中间态会显示为 Failed 卡片。
- **理由**:尊重代码现状,不擅自改契约。测试钉死真实行为,后续若要改这个语义需显式决策。
- **影响**:T1 的进程恢复测试组断言为 `assertNull`(被丢弃),而非 `assertEquals(Failed)`。

---

## 决策待记录项(TBD)

执行过程中遇到以下情况,必须新增 ADR:
- 用户回答了一个产品决策问题(记录问题和答案)。
- 发现某验收标准需要调整(记录原因和新标准)。
- 某任务发现更优解法,偏离了 Playbook 的解法描述(记录偏离和理由)。
- 放弃某文件/某改动(记录放弃对象和理由)。
