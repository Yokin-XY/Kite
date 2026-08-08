# 测试执行分层与本机构建协调

## 目标

Kite 保留全部测试资产，但不要求每个叶子任务都重跑完整回归。测试执行分为三层：

| 层级 | 内容 | 使用时机 | 能否作为发布证据 |
| --- | --- | --- | --- |
| Quick | `Contract/Protocol/Routing/Policy/Schema/Guard` 稳定命名族 | 日常跨层护栏、文档/脚本小改 | 否 |
| Stage | Quick 加调用方显式声明的受影响包或测试类 | 功能叶子、模块阶段验收 | 否 |
| Full | 完整 `:app:testDebugUnitTest` | 父任务、分支合并、正式发布 | 是 |

统一入口：

```powershell
.\scripts\run-kite-tests.ps1 -Profile Quick
.\scripts\run-kite-tests.ps1 -Profile Stage -Tests 'com.kite.app.platform.resources.*'
.\scripts\run-kite-tests.ps1 -Profile Full
```

Stage 没有 `-Tests` 时失败关闭；Quick/Full 不接受自定义 `-Tests`，避免报告范围与实际范围不一致。脚本执行后读取 JUnit XML，输出 suite、test、failure、error、skipped、JUnit 时间和墙钟时间。

## 为什么不删除测试

RF1551 审计确认，`app/src/test` 的 276 个 Kotlin 测试文件没有重复类组。1465 项全量测试是历史功能、架构合同和故障反例的累积，不是同一套件重复注册。压缩对象是“每次默认执行的集合”和无必要的 `--rerun-tasks`，不是已建立的回归覆盖。

Quick 通过职责后缀自动纳入新测试，不维护第二份测试源码树。新增跨层合同应使用对应稳定后缀；纯业务行为测试由 Stage 的显式包模式覆盖，最终仍进入 Full。

## RF1550 实测

2026-08-01 在同一工作树、同一台开发机上依次执行，均为零失败：

| 层级 | suites | tests | 墙钟 | 相对 Full 测试数减少 | 相对 Full 墙钟减少 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Quick | 49 | 254 | 84.572s | 82.7% | 57.9% |
| Stage：Quick + `platform.resources.*` | 55 | 288 | 74.529s | 80.4% | 62.9% |
| Full | 277 | 1467 | 201.151s | 0% | 0% |

Quick 是本轮首次编译新合同后的结果，Stage 命中编译缓存，因此两者墙钟不能互相排序；它们都明显小于 Full。RF1440 使用 `--rerun-tasks` 的历史 Full 约 343 秒，本轮默认不强制重跑的 Full 为 201.151 秒，减少约 41.4%。发布需要强制重跑时仍允许显式 `-Rerun`，但叶子任务不再默认使用。

## 本机构建协调

Git worktree 已天然隔离仓库 `.gradle`、模块 `build/`、APK 和测试报告，但同机任务仍共享用户级 Gradle 缓存、Gradle/Kotlin daemon、CPU 和内存。所有本地 Kite 长任务通过：

```powershell
.\scripts\invoke-kite-gradle.ps1 -GradleArguments ':app:assembleDebug'
```

包装器使用 `Local\KiteGradleBuildV1` Windows 命名 mutex。锁由独立工作进程持有，获取锁后才启动 Gradle；即使外层测试脚本或调用器被中断，工作进程仍会等实际 Gradle 结束再释放锁，下一条构建不会趁隙写入同一测试目录。工作进程自身异常终止时，遗弃锁由系统回收。

本地构建默认使用 `--no-daemon --max-workers=2 --console=plain`。包装器会补齐缺省参数，并拒绝启用 daemon 或把 worker 提高到 2 以上；调用方仍可把单次构建进一步收窄为 1 个 worker。直接调用 `gradlew` 时，根目录 `gradle.properties` 仍默认使用 `org.gradle.daemon=false` 和 `org.gradle.workers.max=2`，避免绕过包装器后留下数小时的 Gradle 常驻进程并占满本机并行槽。参数合同可独立验证：

```powershell
pwsh -File scripts/test-kite-gradle-contract.ps1
```

这些限制不运行 `gradlew --stop`，不执行 `clean`，也不删除或复制任何工作树、项目或用户级构建缓存。

该锁只协调使用包装器的本机 Kite 任务。CI 位于独立环境，不参与本机 mutex，但仍继承项目级 daemon/worker 上限并继续执行原有测试、Lint 等完整任务。ADB 安装和真机操作不由 Gradle 锁代替，仍必须显式指定 serial；不同手机可以在构建结束后并行验收。
