# 资源清单协议

## 目录结构

每个内置资源位于：

```text
assets/resources/<resource-id>/
  manifest.json
  icon.png          # 可选
```

资源首页布局位于 `assets/resources/home.json`。资源 ID 必须稳定，并与目录名一致。

## 基本结构

```json
{
  "schemaVersion": 1,
  "id": "kite.example",
  "base": {
    "name": "Example",
    "description": "示例资源",
    "version": "latest",
    "icon": {
      "type": "text",
      "value": "EX"
    }
  },
  "display": {
    "sections": ["more"],
    "category": "工具",
    "accent": "teal",
    "sizeLabel": "命令行工具",
    "longDescription": "资源用途和边界。",
    "tags": ["example"]
  },
  "relations": {
    "provides": ["command.example"],
    "base": ["command.curl"],
    "defaults": [],
    "extensions": []
  },
  "source": {
    "type": "official"
  },
  "actions": {
    "install": [],
    "open": {},
    "uninstall": []
  }
}
```

## 显示字段

`display` 只描述页面内容，不决定安装状态。可用字段包括分类、强调色、大小、长说明、标签、徽标、媒体、预览卡、要求行和推荐资源。

实验资源应使用明确徽标和说明：

```json
"badge": {
  "label": "实验能力",
  "iconText": "!",
  "accent": "orange"
}
```

## 依赖关系

- `provides`：安装后提供的能力。
- `base`：目标资源的必要前置能力。
- `defaults`：推荐默认安装的能力。
- `extensions`：可选扩展资源 ID。

依赖字段描述事实，不应把依赖安装命令复制到每张资源卡。

## 受管安装

正式资源优先使用 `type: managed`。支持的步骤：

| 类型 | 用途 |
| --- | --- |
| `bundled` | 安装随 APK 分发的资产 |
| `download` | 下载文件，支持备用 URL、重试和 SHA-256 |
| `git` | 克隆或更新 Git 仓库 |
| `npm` | 安装 npm 包 |
| `apt` | 安装 Ubuntu apt 包 |
| `script` | 用指定解释器运行已获取脚本 |
| `shell` | 执行无法由上述结构表达的窄范围命令 |

示例：

```json
{
  "type": "managed",
  "steps": [
    {
      "id": "download-example",
      "type": "download",
      "url": "https://example.com/example.tar.gz",
      "destination": "$install_root/example.tar.gz",
      "sha256": ""
    },
    {
      "id": "install-example",
      "type": "shell",
      "cmd": "install -m 0755 source/example \"$install_root/bin/example\""
    }
  ],
  "managedCommands": ["example"],
  "cleanInstallRoot": true,
  "verify": [
    {
      "id": "example-command",
      "cmd": "command -v example >/dev/null 2>&1"
    }
  ],
  "timeoutMs": 600000
}
```

旧 `type: shell` 仍能加载，但新资源不应把下载、安装、验证和成功登记压成一条自由 Shell。

## 成功边界

只有以下条件都满足，资源才能登记为已安装：

1. 所有获取和安装步骤退出成功。
2. 所有 `verify` 检查通过。
3. 受管命令没有无法解释的冲突。
4. 安装事务完成 `commit`。

网络中断、下载不完整、命令无法链接或验证失败都必须失败并回滚，不能因为脚本打印了“installed”就标记成功。

## 打开与首页卡片

`actions.open.recipe` 是资源打开时执行的 Recipe。`homeCards` 提供用户创建首页卡片时使用的模板。两者都遵守首页卡片协议，不拥有额外执行权限。

## 卸载

卸载应删除该资源拥有的安装目录和受管命令，只处理清单明确拥有的文件。共享依赖和其他资源拥有的命令不能被顺带删除。
