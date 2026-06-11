# Kite 资源卡片编写说明

最后更新：2026-06-11。

这份文档记录资源商店第一版的规则。资源商店不是 Ubuntu 软件管理器，
也不接管用户自己装的所有软件。第一版只管理 Kite 自己声明过的资源卡
片：Kite 知道它的资源 ID、展示信息、安装逻辑、清理逻辑和状态登记。

## 当前结论

资源卡片可以先放一放，进入 UI 和体验打磨。当前结构已经足够支撑：

- 内置资源包安装，例如 Node.js。
- 网络命令安装，例如 Hermes WebUI。
- 安装后登记状态，卸载后清除状态。
- 打开独立资源实例，显示 SH 报告、终端或网页。
- 安装完成、清理完成、失败重试这些顶部胶囊交互。

后面主要是继续加资源样本、优化报告 UI、把资源清单逐步做成可从服务器
刷新。

## 三层结构

一个资源卡片分三层：

```text
展示层：资源页和二级详情页要显示什么
执行层：安装、卸载、重装分别怎么跑
状态层：Kite 自己记录它装没装、有没有失败
```

展示层和执行层可以来自本地 manifest，后期也可以来自服务器。状态层不写
进 manifest，而是运行时由 Kite 自己维护。

## 稳定资源 ID

每个资源必须有一个稳定 ID，类似安卓包名：

```text
kite.nodejs
kite.tool.env
kite.hermes.webui
```

显示名称可以改，比如 `Node.js` 以后想叫 `Node 运行环境` 也可以。但是
安装目录、缓存目录、bin 入口和状态登记都只认这个稳定 ID。

注意事项：

- 不要用中文显示名当安装身份。
- 不要用会变化的版本号当资源 ID。
- 如果资源是官方内置，建议使用 `kite.xxx`。
- 如果未来允许第三方资源，可以再扩展成类似 `author.package` 的形式。

## 工作区目录

资源卡片统一使用这三个目录：

```text
/workspace/.kf/cache/resources/<resource-id>/   资源缓存
/workspace/.kf/software/<resource-id>/          Kite 管理的安装目录
/workspace/.kf/bin/                             共享命令入口
```

含义：

- `cache/resources`：放内置资源解出来的缓存、下载缓存、manifest 缓存。
- `software`：放这个资源真正由 Kite 管理的安装内容。
- `bin`：放给用户和脚本直接调用的入口，比如 `node`、`npm`、`npx`。

这不是隔离系统，也不是禁止全局安装。该用 apt、npm、pip 的地方仍然可以
用。这里只是让 Kite 对自己声明过的资源有一个清晰管理点。

## 本地文件怎么放

当前内置资源清单放在：

```text
assets/resources/<resource-id>/manifest.json
```

例子：

```text
assets/resources/kite.nodejs/manifest.json
assets/resources/kite.tool.env/manifest.json
assets/resources/kite.hermes.webui/manifest.json
```

真正的大资源包目前放在：

```text
assets/toolchain/ai-dev-pack/
```

Node.js 使用的包在这个内置包里：

```text
assets/toolchain/ai-dev-pack/packages/node-v24.15.0-linux-arm64.tar.xz
```

安装前，Kite 会把内置包复制到工作区缓存：

```text
/workspace/.kf/cache/resources/kite.nodejs/ai-dev-pack/
```

然后安装脚本从这个缓存位置读取文件，不直接从 APK asset 里运行。

## manifest 第一版字段

第一版 manifest 是资源页和后续服务器化的雏形。以 Node.js 为例：

```json
{
  "schemaVersion": 1,
  "id": "kite.nodejs",
  "base": {
    "name": "Node.js",
    "description": "现代 JavaScript 运行环境",
    "version": "24.15.0",
    "icon": {
      "type": "text",
      "value": "JS"
    }
  },
  "display": {
    "sections": ["featured"],
    "tags": ["node", "npm", "npx", "javascript"]
  },
  "source": {
    "type": "bundled",
    "asset": "toolchain/ai-dev-pack"
  },
  "paths": {
    "cacheRoot": "/workspace/.kf/cache/resources/kite.nodejs",
    "installRoot": "/workspace/.kf/software/kite.nodejs",
    "binRoot": "/workspace/.kf/bin"
  },
  "actions": {
    "install": [
      {
        "type": "shell",
        "cmd": "install.sh --install-node"
      }
    ],
    "uninstall": [
      {
        "type": "shell",
        "cmd": "remove software root and node/npm/npx wrappers"
      }
    ]
  }
}
```

现在 UI 里还有一部分资源清单是代码里写死的。这个 manifest 是为了把结构
先定下来，后面再逐步让资源页真正扫描这些文件或从服务器拉取。

## Node.js 样本思路

Node.js 是第一条真实资源样本，思路是：

1. 资源 ID 固定为 `kite.nodejs`。
2. 内置包来源是 `assets/toolchain/ai-dev-pack`。
3. 安装前复制到缓存：

```text
/workspace/.kf/cache/resources/kite.nodejs/ai-dev-pack/
```

4. 安装脚本收到这些环境变量：

```sh
KF_RESOURCE_ID="kite.nodejs"
KF_TOOLCHAIN_PACK_DIR="/workspace/.kf/cache/resources/kite.nodejs/ai-dev-pack"
KF_TOOLCHAIN_DIR="/workspace/.kf/software/kite.nodejs"
KF_TOOLCHAIN_BIN_DIR="/workspace/.kf/bin"
UV_LINK_MODE="copy"
```

5. Kite 先清空自己的安装目录，再执行安装：

```sh
rm -rf "$KF_TOOLCHAIN_DIR"
mkdir -p "$KF_TOOLCHAIN_DIR" "$KF_TOOLCHAIN_BIN_DIR"
bash "$KF_TOOLCHAIN_PACK_DIR/install.sh" "--install-node"
```

6. `install.sh --install-node` 做的事：

```text
解压 node-v24.15.0-linux-arm64.tar.xz
安装到 /workspace/.kf/software/kite.nodejs/node-v24.15.0
把 node/npm/npx 链接到 /workspace/.kf/bin
执行 node/npm/npx 版本检查
```

7. 验证命令：

```sh
/workspace/.kf/bin/node -v
/workspace/.kf/bin/npm -v
/workspace/.kf/bin/npx -v
```

普通 PATH 正常时也可以用：

```sh
node -v && npm -v && npx -v
```

## 安装脚本注意事项

资源安装本质上走 SH。Kite 负责给身份、路径、状态和报告，真正怎么装由
资源脚本负责。

脚本建议：

- 使用 `set -e`，关键步骤失败就退出非 0。
- 尊重 Kite 传入的路径变量，不要自己猜目录。
- 安装前可以清理自己的安装目录，实现覆盖安装。
- 输出 `KITE_RESOURCE_STEP`，让 SH 报告有进度感。
- 安装完成后打印版本或摘要，方便用户判断。

推荐输出：

```sh
echo "KITE_RESOURCE_STEP clean-install-root $KF_TOOLCHAIN_DIR"
echo "KITE_RESOURCE_STEP prepare-install-root $KF_TOOLCHAIN_DIR"
echo "KITE_RESOURCE_STEP run-install-script $KF_TOOLCHAIN_PACK_DIR/install.sh --install-node"
```

不要做的事：

- 不要删除不属于自己资源 ID 的目录。
- 不要把用户自己安装的外部软件当成 Kite 管理对象。
- 不要只因为系统里已有同名命令就默认接管它。
- 不要把运行状态写进 manifest。

## 卸载和清理

卸载不是通用能力，必须由资源自己声明。

Node.js 的卸载逻辑是：

```text
删除 /workspace/.kf/software/kite.nodejs
删除 /workspace/.kf/bin/node
删除 /workspace/.kf/bin/npm
删除 /workspace/.kf/bin/npx
删除 /workspace/.kf/cache/resources/kite.nodejs
顺手清理旧版本遗留路径
```

卸载成功后，Kite 清除资源状态登记，按钮回到安装。

如果卸载失败，状态变成清理异常，用户可以继续清理。第一版不分析到底坏
在哪里。

## 状态规则

Kite 只记录自己的资源状态：

```text
installing     安装中
installed      已安装
uninstalling   清理中
failed         安装失败或清理失败
```

状态不放在资源 manifest 里，而是由 Android 层登记。

成功规则：

```text
安装动作所有步骤完成 -> registered installed
卸载动作所有步骤完成 -> clear registered state
```

失败规则：

```text
SH 退出码非 0
Ubuntu 通道不可用
终端或网页步骤没有完成
App 被杀后发现没有活跃实例
```

这些都会变成异常状态。第一版不做复杂修复，只提供重新安装、继续清理、
查看报告。

## 内置资源和网络资源

内置资源：

```text
资源说明在 assets/resources/<id>/manifest.json
资源文件放在 assets/toolchain/... 或后续专门的 assets/resources/<id>/files
安装前复制到 /workspace/.kf/cache/resources/<id>/
安装脚本从缓存目录执行
```

网络资源：

```text
manifest 可以先内置，也可以以后从服务器拉取
安装命令可以自己下载并安装
缓存目录可以只保存 manifest、下载包或日志
安装目录仍建议使用 /workspace/.kf/software/<id>
需要全局 npm/pip/apt 时，由资源脚本自己明确处理
```

Hermes WebUI 当前就是网络命令型资源：先要求 Node.js 可用，再跑 npm 安装。

## 资源实例外壳

资源安装实例和普通首页卡片共用底层渲染：

```text
SH 报告
终端
网页
```

但顶部外壳不同：

- 普通卡片实例可以保留双按钮胶囊。
- 资源安装实例只保留三点菜单。
- 安装完成或失败时，同一个右上角位置变成结果胶囊。
- 成功点一下关闭实例。
- 失败点一下重跑当前动作。
- 关闭入口放在三点菜单里，不常驻。

## 新增资源卡片清单

写一个新资源时，至少确认这些问题：

```text
1. 稳定 ID 是什么？
2. 展示名称、简介、版本、图标是什么？
3. 来源是 bundled 还是 network？
4. 安装目录是否使用 /workspace/.kf/software/<id>？
5. 是否需要把命令暴露到 /workspace/.kf/bin？
6. 安装动作是否能覆盖安装？
7. 卸载动作是否能清理安装中断后的半成品？
8. 成功后用什么命令验证？
9. 失败时用户看 SH 报告能不能知道大概原因？
10. 是否需要终端交互或网页交互？
```

如果只是不确定怎么修复坏状态，第一版不做修复系统，直接设计成：

```text
重新安装 = 清理自己的安装目录后再安装
继续清理 = 继续执行卸载/清理脚本
```
