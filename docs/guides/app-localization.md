# Kite 应用语言架构

## 结论

Kite 不做“看到一句文字后再按内容匹配替换”。应用内稳定文字统一使用 Android
资源键，例如 `R.string.settings_title`；Android 根据当前应用语言选择
`values/strings.xml` 或 `values-en/strings.xml` 中同名资源。

当前支持：

- 跟随系统（默认，不设置应用级覆盖语言）。
- 简体中文（`zh-CN`）。
- English（`en`）。

Android 13 及以上由系统应用语言能力持有状态；旧版本通过 AppCompat 兼容并持久化。
应用内设置和系统设置读取、写入的是同一份应用语言状态。

## 文字边界

### 编译进 APK 的界面文字

标题、按钮、状态名称、提示、通知通道名称和格式化句子放入 `strings.xml`。
业务层和状态层优先保留枚举、布尔值、状态码、数量和时间等语义事实，由显示面在
当前 `Context` 上解析文字。不要让 projector 提前拼出固定中文。

### 卡片和资源清单中的内容

用户导入的卡片名、分组名、命令、网页内容、报告原文和错误原文属于运行数据，不能引用
APK 内的 `R.string`，也不能按中文原文自动替换。需要多语言内容时，应在对应 JSON 协议中
增加明确的本地化字段并保留默认值；这是数据协议演进，不与应用壳层资源混在一起。

资源页要进一步区分“内容原文”和“应用解释”：

- 资源名称、简介、海报文字、第三方版本说明和未知元数据保持清单原文。
- “打开”“获取中”“资源管理”“全部”“基础环境”等 Kite 自身的动作、状态和标准分类必须使用资源键。
- 已登记的 tab/section 使用稳定 ID 选择本地化文案；未知分类继续显示清单提供的标题。
- 常见清单语义标签可在显示适配层按稳定字段归一化，例如 `sourceType`、标准 size label；不得修改原始 JSON。

### 不翻译的内容

命令、路径、协议字段、诊断代码、第三方产品名和机器原始输出保持原样。外层说明可以使用
资源格式化，例如“打开失败：%1$s”，其中 `%1$s` 是原始错误。

## 新增一种语言

1. 在 `app/src/main/res/` 新建 Android 标准语言目录，例如法语使用 `values-fr/`。
2. 复制默认 `strings.xml`，保持资源键完全一致，只翻译资源值。
3. 在 `AppLanguagePreference` 增加稳定语言标签，并在 `SettingsLocalization` 增加显示名称。
4. 运行 `LocalizationResourcesTest`、相关设置测试、`assembleDebug`。
5. 在真机上验证：跟随系统、手动切换、杀进程重启、切回系统，以及当前页面恢复。

`resources.properties` 的 `unqualifiedResLocale=zh-CN` 声明默认资源实际是简体中文；
AGP 会从资源目录自动生成 Android `LocaleConfig`，不要再维护第二份语言清单。

## 新增界面流程

- 新文字先创建语义清楚的资源键，再在 UI 绑定层调用 `getString`。
- 数量优先使用 `plurals`；带动态值的句子使用带位置编号的占位符。
- 不在 enum、store、gateway、projector 中保存面向用户的固定中文或英文。
- 显示文字不得承担业务判断。分类使用稳定 ID，动作和状态使用 Intent、Phase 或枚举；不要比较“全部”“仅搜索”“获取中”等本地化文本。
- 普通语言状态变化不增加轮询、扫描或整页刷新。用户主动切换语言时，允许 Android 进行一次
  标准 Activity 配置重建；终端、网页、报告等运行事实仍由原状态拥有者恢复。
- 提交前保证默认资源和各语言资源键一致；不要用 `getIdentifier` 或文字内容匹配做兜底。

## 代码级硬编码审计

不需要先在真机上逐页寻找固定中文。运行：

```powershell
.\tools\audit-localization-hardcodes.ps1
.\tools\audit-localization-hardcodes.ps1 -Details
```

扫描器按代码形状将候选行分为：

- `direct-ui`：`text`、`hint`、`contentDescription`、Dialog、Toast 等高置信度固定 UI。
- `presentation-state`：Projector/Presentation/Contract 中可能提前拼好的展示文字。
- `runtime-or-diagnostic`：运行原文、日志、Shell/协议错误，通常不应直接翻译。
- `review-needed`：仍需结合调用路径判断的候选。

扫描结果是审计入口，不是自动替换器。迁移时仍使用稳定资源键；真机负责验证截断、布局、
配置重建和运行时内容边界。`-Json` 可供后续 CI 或报告脚本消费。
