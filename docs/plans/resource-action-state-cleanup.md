# 资源操作状态与按钮收敛方案（待确认）

> **状态标注（2026-09-21 文档整理）**：部分完成——D1（latestVersion 剔除）、D3 大体（UiProjector 无 Repair）已落地；残留：OP_REPAIR 文案在 AndroidResourceActionGateway 未清、D6/D7 验收未做。

> 背景：安装与更新已交由官方机制（`official_command`：安装 = 更新 = 官方幂等命令）。
> 当前"获取 / 取消 / 重试 / 更新 / 修复 / 重新安装 / 打开"各状态的触发条件互相打架，
> 主按钮与 open 预检在某些状态下把整条链路锁死。本方案先梳理事实，再给收敛提案。

---

## 一、现状事实（三层投影）

### 1.1 登记层（KiteResourceInstallStore / Registry，SQLite）

每个资源×环境一行，关键列：

| 列 | 含义 | 写入者 |
|----|------|--------|
| `status` | `installed` / `failed` / … | markInstalled / markFailed |
| `operation` | `install` / `update` / `reinstall` / `repair` / `uninstall` | 随 mark* 附带 |
| `updateStatus` | `available` / `current` / `checking` / `failed` / `unsupported` | 版本检查与维护标记 |

**"需要修复"的组合判定**（`ResourceFeatureController`、`AndroidResourceFeatureGateway`）：

```text
status == installed && operation == repair && updateStatus == failed
```

### 1.2 事实层（KiteResourceRuntimeFactsProjector）

`installed / preparing / installing / installPlanInProgress / uninstalling / failed / extraBusy`
由登记行 + 安装计划快照合并。注意：

- `installed` 与 `failed` 可同时为 true（`baselineInstalled` 并集），phase 判定 failed 优先。
- `installing` 把"计划忙碌"也算进去，`extraBusy` 是所有忙碌位的并集。

### 1.3 按钮层（KiteResourceUiProjector → ResourceFeatureController → ViewSupport）

主按钮由 UiProjector 的 `(stateLabel, actionLabel)` 决定；`ResourceFeatureController` 在
"需要修复"时**强制覆盖** `primaryIntent = Repair`（不看 UiProjector 的 actionLabel）。

#### 主按钮状态矩阵（现状）

| 事实状态 | 状态标签 | 主按钮 | 副按钮 | 可点 |
|----------|----------|--------|--------|------|
| 计划进行中 | 获取中 | 获取中(=ReopenInstall) | 取消 | ✅ |
| 准备中 | 准备中 | 准备中 | — | ❌ |
| 更新中 | 更新中 | 查看进度 | — | ✅ |
| 重装中 | 重新安装中 | 查看进度 | — | ✅ |
| 修复中 | 修复中 | 查看进度 | — | ✅ |
| 获取中 | 获取中 | 获取中 | 取消 | ✅ |
| 卸载中 | 卸载中 | 卸载中 | — | ❌ |
| 卸载失败 | 卸载失败 | 重新获取 | — | 看 busy |
| 获取失败 | 获取失败 | 重新获取 | 取消 | ✅ |
| 已装+可更新 | 可更新 | 更新 | — | ✅ |
| 已装 | 已获取 | 打开 | 卸载 | ✅ |
| 已装+运行中 | 运行中 | 运行中 | 中止 | ✅ |
| 未装 | 未获取 | 获取 | — | ✅ |
| **需要修复（覆盖）** | （按上面正常显示） | **修复** | — | ✅ |

#### open() 预检拦截顺序（AndroidResourceActionGateway.open）

```text
需要修复 → 拦截："请先点击修复"     ← 硬拦
可更新   → 拦截："请先点击更新"     ← 硬拦
缺依赖   → 自动补装（不拦）
其余     → 放行启动
```

---

## 二、"修复"的触发器与病根

### 2.1 谁会标记"需要修复"（markRepairRequired）

1. **合同漂移**：安装时快照 manifest ≠ 当前 manifest 的 canonical contract
   （`KiteResourceInstallContract`，在 `loadCatalog` 和 `open()` 预检两处都会跑）。
2. **托管命令缺失**：`managedCommands`（如 `claude`）探测不到。
3. **APK 被移除**：包管理器里没了（AndroidPackageResourceFactsReconciler）。

### 2.2 病根 P1：`source.latestVersion` 进了合同比对

canonical contract 包含**整个 `source` 对象**，其中有 `latestVersion`——CI 日常维护的提示性字段
（实测：仓库 assets 里 Claude Code 为 `2.1.274`，GitHub store 推送为 `2.1.272`，各自演进）。
且所有 official_command 资源**都没有 `actions.update`**，于是版本号变化必然判
`RepairRequired` 而非 `UpdateAvailable`：

```text
CI 维护 latestVersion → 合同漂移 → 标记修复 → open() 硬拦 → 被迫点修复
→ 重跑官方命令 → 快照更新 → 恢复 → 下次版本号一变再来一轮
```

这就是"修复反而把安装搞成死循环"的机制根源：修复本身能收敛，但触发条件把
**纯提示性字段变化**也当成需要重装的结构漂移，反复发作、每次锁死"打开"。

真·死循环场景（更严重）：若官方安装落点与 `managedCommands` 探测点不一致
（官方命令装到 Kite 探不到的路径），修复成功后仍判缺失 → 再标记 → 永远打不开。

### 2.3 次级打架点

| # | 问题 | 证据 |
|---|------|------|
| P2 | "可更新"也硬拦 open（"请先点击更新"），用户不能选择先用旧版 | AndroidResourceActionGateway.open 预检 |
| P3 | 修复/更新失败 `markFailed` 把 `status` 打成 `failed`，抹掉 installed 事实：卡片显示"获取失败"，与文案承诺"原有版本会保留到验证成功"矛盾 | ResourceRunCoordinator.settleFailure → markFailed |
| P4 | official_command 资源"修复 / 重新安装 / 更新"三按钮 = 同一个动作（官方命令），语义重叠 | AndroidResourceRecipeFactory: OP_REPAIR→installActions |
| P5 | `markInstalled` 不清 `updateStatus`：修复成功后维护面板仍按 FAILED 分支显示文案 | KiteResourceRegistry.upsertRegistry 保留 existing.updateStatus |
| P6 | open() 预检在 IO 线程跑，拦截结果用 toast 反馈，按钮无"处理中"状态，不符合"点击后先给出处理中"的交互规约 | open() 返回 message() |
| P7 | repairRequired 强制覆盖主按钮 intent，但状态标签仍走普通投影（如"可更新"），标签与按钮可能不同源 | ResourceFeatureController.projectItem |
| P8 | **按钮分栏与样式三处不统一**：列表卡片=右侧单胶囊小按钮（副按钮无处安放）；详情页=0.7/0.3 分栏，副按钮恒为红色危险样式（哪怕是"取消"）；维护面板=整行按钮与 1f/1f 五五并排混用，高度 44/42 混用，圆角 15/16 混用 | ResourceFeatureViewSupport / ResourceDetailScreen / ResourceMoreScreen |

---

## 三、收敛提案（决策点，待用户确认）

### D1（核心）：合同比对剔除提示性字段

`canonicalContract` 的 `source` 投影剔除 `latestVersion`（以及未来同类提示性字段）。
**纯版本号变化 → 不触发修复**；下次"检查更新"自然发现差异。

- 收益：消除 CI 版本维护引发的假修复，直接解开"死循环"感知。
- 保留：`source.command / uninstallCommand / package`、`management`、`paths`、
  `actions.install`、`relations.base/defaults`、`base.version` 变化仍判真漂移。

### D2：真漂移不再硬拦 open

真漂移（源命令、路径、依赖关系变了）时：

- **打开不拦**：旧版本还能用就先用，卡片/详情给"需要重新收敛"徽标；
- 收敛动作由用户在详情页主动触发，或后台静默重跑官方命令（幂等）。

### D3（改）："修复"概念彻底退场

用户拍板：不打算逐个管理"Agent 损坏"，修复逻辑复杂且无实际价值。处理：

- **按钮、状态、文案全部移除**：主按钮不再出现"修复"，维护面板不再暴露修复入口，"修复中/需要修复"状态不再存在；
- 原修复承担的职责全部并入现有语义：
  - 价签变化（latestVersion）→ D1 已剔除，不管；
  - 真漂移（安装命令/路径/依赖变了）→ 打开时后台静默重跑官方命令收敛（幂等），不打扰用户；
  - 命令真缺失（探测不到）→ 就当"未获取"，按钮=[获取]，重跑官方命令自然修好；
  - APK 被移除 → 登记自动清掉，回到未获取；
- "重新安装"按钮同样退场：需要重装 = 卸载后重新获取，两步已够。

### D4：维护失败不抹 installed 事实

修复/更新/重装失败：`status` 保持 `installed`，失败信息进摘要
（卡片显示"已获取（上次更新失败）"），主按钮仍是"打开"，open() 不再因失败态被预检拦下
（现状：失败 → status=failed → isInstalled=false → open 预检误判"缺依赖"触发自动重装）。

### D5：open() 预检降级为提示 + 处理中反馈

- 缺依赖：维持自动补装；
- 可更新/漂移：不阻断，放行 + 非阻断提示（用户可选"立即更新 / 稍后"）；
- 点击后按钮先进"处理中"，预检完成后再给结论（符合会话工作规约）。

### D6：按钮布局统一规则（七三分）

**布局规则（全局唯一）：**

- 一行最多两个按钮：**主 70% / 辅 30%**；只有一个动作时主按钮独占整行；
- 主位 = 正向推进动作（获取、重试、打开、查看进度）；辅位 = 退回/破坏动作（取消、清理、卸载、中止），红色样式；
- 卡片、详情、维护三处按钮高度统一 44dp、圆角统一、字号统一；卡片小胶囊样式与详情大按钮视觉同源；
- 卡片上副按钮的呈现：卡片右侧胶囊区域在需要辅操作时展开为主+辅两个小胶囊（同比例）。

**状态 × 按钮总表（修复退场后）：**

| 状态 | 主（70%） | 辅（30%） | 说明 |
|------|----------|----------|------|
| 未获取 | 获取 | — | || 准备中 | 准备中（禁用） | — | 短暂态 |
| 获取中 | 查看进度 | 取消 | 取消=终止计划 |
| 失败 | 重试 | 清理 | 清理=清残局回未获取 |
| 已获取 | 打开 | 卸载 | || 已获取·可更新 | 打开 | 卸载 | 标签显示"可更新 vX"；更新入口见下 |
| 启动中 | 启动中（禁用） | — | || 运行中 | 运行中 | 中止 | 点主按钮=回到会话 |
| 停止中 | 停止中（禁用） | — | || 卸载中 | 卸载中（禁用） | — | |

**更新的入口（不在卡片主位）：**

- 已安装页**下拉刷新 → 批量检查更新**（手动主动）；
- 自动检查：进入已安装页且距上次超过 N 小时静默检查（间隔可调，取代"每天一次"的武断限制）；
- 发现新版后：卡片标签"可更新 vX"；详情页维护区出现整行"更新到 vX"（高亮主色）；顶部可加"全部更新"。

### D7：更新策略双通道

- 手动：下拉刷新批量检查（ResourceUpdateBatchPolicy 已有筛选能力，复用）；
- 自动：低频静默检查（启动后/进入页面时，带间隔阈值）；
- 具体 N 值与触发时机实施时定，不预设武断值。

### 目标主按钮矩阵（D1–D6 落地后）

| 状态 | 主按钮 | 副按钮 | open() |
|------|--------|--------|--------|
| 未获取 | 获取 | — | 自动安装 |
| 获取中 | 查看进度 | 取消 | — |
| 失败 | 重试 | 清理 | — |
| 已获取 | 打开 | 卸载 | ✅ |
| 已获取+可更新 | 打开（标签：可更新） | 卸载 | ✅ + 提示 |
| 已获取+漂移 | 打开（后台静默收敛） | 卸载 | ✅ |
| 运行中 | 运行中 | 中止 | 复用实例 |

---

## 四、分阶段计划

| 阶段 | 内容 | 验收 |
|------|------|------|
| P0（本轮） | 本梳理文档，用户确认 D1–D7 | 方案定稿 |
| P1 | D1（合同剔除 latestVersion）+ D4（失败不抹 installed） | 单测：版本号变化不再触发 RepairRequired；失败后卡片仍"已获取" |
| P2 | D3（修复/重装退场：删按钮、删状态、预检不硬拦）+ D5 | 单测 + 手动：模拟 drift 后 open 放行 |
| P3 | D6（七三分布局统一）+ D7（更新双通道） | 魅族 18 真机：5 个官方 Agent 逐个安装→更新→打开链路验收 |

## 五、边界外（另行立任务，不在本方案内）

1. **超大型 Agent 匹配**：Codex / Claude Code / OpenCode / Hermes / OpenClaw——每个都是独立大工程，维度：官方登录/第三方供应商、Skill、MCP、思考模式、快捷命令、图片文件传输。前置依赖正是本方案（安装/更新/按钮链路稳定后才能开始逐个匹配）。
2. **国内源与版本匹配优化**：现有 npm 多源循环（华为云→npmmirror→阿里云→官方）与版本匹配已可用，需再优化一轮（具体问题实施时盘点）。
3. **资源冷冻区**：`kite.zcode` 与 `kite.deepseek.harness`（DeepSeek 桌面版）——不删除资产、不直接下线，标记"冷冻"（可复用 availability 机制新增 `frozen` 值）：正式版资源列表不再展示，未来支持桌面版时解冻回归。待确认：已安装用户在冷冻后的卡片处理方式（保留 + "不再维护"标记，或引导卸载）。`kite.hermes.core` 不在冷冻范围——Hermes 特殊，属 5 Agent 单独匹配工程之一，后续单独处理。

---

*生成：2026-XX-XX，基于 main @ b42000b2 的代码事实梳理；未做任何代码改动。*
