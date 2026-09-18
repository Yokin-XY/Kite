# CC Switch 供应商数据 vendor 目录

上游：[farion1231/cc-switch](https://github.com/farion1231/cc-switch)（MIT, Copyright (c) 2025 Jason Young）。
本目录保存其预设源码快照（`src/config/*.ts` 等），并转换为 Kite 可消费的目录数据。

## 更新流程

```bash
export HTTPS_PROXY=http://127.0.0.1:7897   # 按需
# 1) 重拉上游源文件（见下方清单）
# 2) 转换为 CC Switch 原生形状
node convert-all.mjs
# 3) 转换为 Kite 形状（生成 out/kite-catalog.json + app 内 CcSwitchCatalogBundle.kt）
node convert-kite.mjs
```

vendor 文件清单（src/config/）：piProviderPresets / piModelCatalog / piThinkingProfiles /
claude / codex / gemini / grokBuild / hermes / mcode / openclaw / opencode / universal ProviderPresets、
constants、types、types-shim；src/utils/：deepClone、grokBuildConfig；stubs/smol-toml.ts。

产物：`out/kite-catalog.json`（检查用）与 `app/src/main/java/com/kite/app/agent/config/CcSwitchCatalogBundle.kt`
（随 APK 编译，解析方 CcSwitchBundledCatalogParser）。

已剔除：官方登录条目、`${...}` 模板占位符 URL、无模型目录条目（待"拉模型"功能接入后放开）。
