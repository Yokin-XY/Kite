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
  "writeScopes": ["global:package-index"],
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

`writeScopes` 用于声明安装动作除自身资源目录和 `managedCommands` 之外还会修改的共享事实。
相同作用域的动作不会并发执行；以 `global:` 开头的作用域跨环境互斥，其他作用域只在同一环境内互斥。
资源自身目录和已声明命令会自动形成作用域，因此普通独立资源不需要重复填写。

旧 `type: shell` 仍能加载，但新资源不应把下载、安装、验证和成功登记压成一条自由 Shell。

需要兼顾官方 Git 仓库和镜像新鲜度的资源，可以在 `git` 步骤声明由签名商店维护的近期版本窗口：

```json
{
  "id": "acquire-example-source",
  "type": "git",
  "repositories": [
    "https://example.cn/example.git",
    "https://github.com/example/example.git"
  ],
  "destination": "$install_root/example",
  "latestVersionWindow": [
    {"version": "v3.0.0", "ref": "v3.0.0", "commit": "<40 位提交>"},
    {"version": "v2.9.0", "ref": "v2.9.0", "commit": "<40 位提交>"},
    {"version": "v2.8.0", "ref": "v2.8.0", "commit": "<40 位提交>"}
  ]
}
```

窗口最多三个版本，不能与固定 `ref` / `commit` 同时使用。安装器按用户的来源顺序向每个仓库查询最新 tag；只有最新 tag 命中窗口且实际检出的提交也一致才发布，并把选中的版本、ref 和提交写入资源目录的 `.kite-source-selection/`，供后续安装与验证步骤使用。`actions.updateStrategy: "reinstall"` 表示更新时复用同一套完整安装事务，而不是运行一套可能漏掉获取和核验的轻量脚本。

NPM 资源把同类窗口写在 `source.latestVersionWindow`。单包可以省略 `artifact`；多包必须明确包名，每个包各保留最多三个版本：

```json
{
  "source": {
    "type": "npm",
    "package": "@example/agent",
    "companionPackages": ["@example/acp"],
    "latestVersionWindow": [
      {"artifact": "@example/agent", "version": "3.0.0", "integrity": "sha512-..."},
      {"artifact": "@example/agent", "version": "2.9.0", "integrity": "sha512-..."},
      {"artifact": "@example/acp", "version": "8.0.0", "integrity": "sha512-..."}
    ]
  }
}
```

存在窗口时，`packages` 只能声明裸包名或 `@latest`，不能固定某个版本。运行时对用户排序后的每个注册源查询 `latest` 与 `dist.integrity`，全部包命中窗口后才按查询到的精确版本安装；选中身份写入 `.kite-source-selection/<step>.<package>.version|integrity`，主包同时写入无包名后缀的版本与摘要文件。

PyPI 资源使用同一来源级窗口，但摘要字段为目标 ARM64 Linux wheel 的 `sha256`。安装步骤声明 `type: "pypi"` 和一个裸包名；运行时先读取当前索引的 Simple API，确认该索引公开的最新版本命中窗口，并同时校验索引片段摘要和下载后文件摘要，再把 wheel 交给 `uv tool install`。找不到当前版本的 ARM64/通用 wheel、版本越窗或摘要不一致时只淘汰当前索引，不能退回窗口中的旧版本。

厂商直链或 GitHub Release 可使用 `type: "latest_download"`。来源声明 `latestUrl`、`latestFormat`、可选的 `latestJsonField` / `latestRegex` / `latestStripPrefix`，窗口的每个候选声明 `version + url + sha256`。`json` 读取指定字段，`text` 读取首行，`regex` 只读取第一个捕获组。步骤本身只声明目标路径、重试策略和 `maxBytes`，不得再写死当前制品 URL。运行时先请求来源的最新元数据，只有返回版本命中窗口时才下载该候选 URL；下载摘要不符、版本越窗、元数据超限或制品超限都会淘汰当前来源。动态归档仍先在资源缓存完成这次核验，再交给 Rust 原生解压；Rust 接受的摘要集合只来自同一签名窗口。

## 成功边界

只有以下条件都满足，资源才能登记为已安装：

1. 所有获取和安装步骤退出成功。
2. 所有 `verify` 检查通过。
3. 受管命令没有无法解释的冲突。
4. 安装事务完成 `commit`。

网络中断、下载不完整、命令无法链接或验证失败都必须失败并回滚，不能因为脚本打印了“installed”就标记成功。

## 打开与首页卡片

`actions.open.recipe` 是资源打开时执行的 Recipe。`homeCards` 提供用户创建首页卡片时使用的模板。两者都遵守首页卡片协议，不拥有额外执行权限。

## Agent 与后台运行保证

Managed Agent 的 `launch` 以及其中的 `runtimeDependencies` 可以声明 `runtimeGuarantees`。它不是性能开关，而是调用方对任务闭包
能够在启动前证明的事实；未知值会使该 Agent 声明失效，旧清单缺省为空并继续走 PRoot。

```json
{
  "argv": ["python3", "agent.py"],
  "runtimeGuarantees": [
    "no_child_process",
    "verified_native_imports"
  ],
  "runtimeGuaranteeEvidence": {
    "pythonAbi": "cpython-314-aarch64-linux-gnu"
  }
}
```

当前只接受：

- `no_child_process`：脚本及其导入闭包不会创建、替换或通过 shell 启动子进程；
- `verified_native_imports`：脚本的原生导入闭包只包含当前 Host 兼容矩阵已验证的模块。

两项同时存在时，受管 Python 才有资格进入 Host Provider；这只是候选资格，解释器身份、ABI、网络、cwd、环境和资产门仍须全部
通过。`verified_native_imports` 还必须提供与当前受管解释器精确一致的 `pythonAbi`；缺失或版本不一致会在进程创建前回到 PRoot，
未知证据键会使清单失效。不得根据资源 ID、应用名、脚本名或包名自动补上保证。

## 卸载

卸载应删除该资源拥有的安装目录和受管命令，只处理清单明确拥有的文件。共享依赖和其他资源拥有的命令不能被顺带删除。
