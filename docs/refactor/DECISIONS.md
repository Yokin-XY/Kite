# 重构决策记录(DECISIONS.md)

> 记录所有关键决策:**选 A 还是 B、为什么、何时**。
> 防止上下文压缩后"忘了为什么这么决定"而反复摇摆。
> 每条决策带日期 + 理由 + 影响范围。

格式:ADR(架构决策记录)精简版。

---

## ADR-001 保留 namespace/applicationId 分离,但统一源码物理路径

- **日期**:2026-06-30
- **决策**:T3 中**保留** `namespace=com.kftest.app` + `applicationId=com.kite.app` 的分离(合法且常见),但把**源码物理路径**统一到 `com/kite/app/`。
- **理由**:namespace 决定 R/BuildConfig 生成包,改它牵涉 generated 代码和资源引用,风险大;而源码路径统一即可消除"双源码树互引"这个真正的恶果。改动最小化。
- **影响**:T3 的执行方式。
- **备选方案(否决)**:连 namespace 一起改成 com.kite.app —— 否决,风险/收益比差。

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
