# 冻结模块

这些模块已从主构建中移除，源代码保留供未来恢复。

## 冻结原因

| 模块 | 原因 |
|------|------|
| agent/antigravity | 冻结层 Agent，不再维护适配 |
| agent/pi | 冻结层 Agent，不再维护适配 |
| feature/recipeeditor | 官方命令直装后自定义安装脚本需求大减 |
| native/kite-device-cli | Ubuntu/PRoot 内 Device Bridge 客户端预研，未接入任何构建链（2026-09-24 移入） |

## 恢复方法

把目录移回原位置（`agent/`、`feature/` 移回 `app/src/main/java/com/kite/app/`，`native/` 移回 `native/`），修复编译即可。
