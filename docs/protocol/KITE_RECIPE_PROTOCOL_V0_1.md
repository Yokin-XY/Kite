# Kite Recipe Protocol V0.1

本文定义 Kite Recipe 的工作流语义。V0.1 的重点不是把所有能力写进 Recipe，而是把边界分清楚：平台传输层固定，Recipe 工作流开放加载，KF 执行层返回结构化 Run Report。

实际给 Hermes、人类或其他 AI 写首页卡片时，先看 `docs/HOME_CARD_SCHEMA.md`。那里记录当前真实加载目录、`base.id` 留空规则、共享目录 `/exchange/cards`、以及进程容器测试卡片模板。

## 1. 三层模型

```text
平台传输层
  Kite/KF 固定通信方式、Bridge 地址、token、KF 启动方式。
  这些不允许 Recipe / AI / 分享包决定。

Recipe 工作流层
  Recipe 描述卡片、执行入口、预期结果、打开结果。
  Recipe 可以来自 assets / user / imported / future remote。

KF 执行层
  KF / Bridge 在 Linux/proot 内执行脚本或命令，返回 Run Report。
```

## 2. 旧内容修正

已有正确内容继续保留：

```text
1. Recipe 自动扫描加载为首页卡片。
2. 内置 recipe、用户 recipe、导入 recipe 分层。
3. Kite 不执行 shell。
4. KF Bridge / KF Runtime 执行命令。
5. open_web 由 Kite 执行，不由 KF 打开。
6. Run Report 返回结构化结果。
```

需要修正的旧表达：

```text
1. Recipe 不应该包含 Bridge 地址、端口、token、KF 启动方式。
2. Bridge endpoint 是平台层强绑定，不属于 Recipe。
3. Recipe 不应只围绕 steps 模式设计。
4. 需要新增 execution.mode，把 steps / script / android_action 区分开。
5. source 字段不能完全信任 recipe 文件自写，应由 loader 根据来源赋值。
6. Run Report 不应默认返回大量 stdout/stderr，只返回摘要和最后有效输出。
7. expected 匹配是可选能力，不应强制所有命令都配置。
8. AI / Recipe Assistant 只负责脚本和配置，不负责传输层。
```

## 3. Recipe 定位

Kite Recipe 是可扫描、可分享、可由 AI 维护的工作流卡片入口。它不是单纯 UI 卡片，也不是完整系统传输配置。

推荐结构：

```json
{
  "schemaVersion": 1,
  "id": "hermes-webui",
  "name": "Hermes WebUI",
  "description": "启动 Hermes 图形化工作台",
  "type": "script_web",
  "defaultUrl": "http://127.0.0.1:8648",
  "shortcut": false,
  "execution": {
    "mode": "script",
    "script": "scripts/start.sh"
  },
  "expected": {
    "mode": "contains",
    "text": "ready",
    "source": "lastMeaningfulOutput"
  }
}
```

字段说明：

```text
schemaVersion
  Recipe 协议版本。

id
  Recipe 唯一身份，用于首页卡片、运行报告、快捷方式、状态绑定。

name / description
  用户可见卡片信息。

type
  用于 UI 分类和默认行为。可保留 open_url / script_web / command_web / start_service / template。

defaultUrl
  默认打开地址。它是卡片和 Workbench 的默认结果入口。

shortcut
  是否允许绑定桌面快捷方式。V0 只保存语义，不强制实现。

execution
  Recipe 的执行入口。后续重点围绕 execution.mode。

expected
  可选预期结果。没有 expected 时，不强行判断成功。
```

## 4. execution.mode

V0.1 定义三类执行入口：

```text
execution.mode = steps
execution.mode = script
execution.mode = android_action
```

### 4.1 steps 模式

定位：

```text
透明命令配置。
适合官方 Recipe、可审查分享 Recipe、极客用户、AI 自动生成的简单命令流程。
```

示例：

```json
{
  "execution": {
    "mode": "steps",
    "steps": [
      {
        "id": "step_install",
        "type": "shell",
        "cmd": "echo ready",
        "workdir": "/workspace",
        "timeoutMs": 30000,
        "delayAfterMs": 1000,
        "expected": {
          "mode": "contains",
          "text": "ready",
          "source": "lastMeaningfulOutput"
        }
      },
      {
        "id": "step_open",
        "type": "open_web",
        "url": "http://127.0.0.1:8648"
      }
    ]
  }
}
```

字段说明：

```text
cmd
  要交给 KF Linux/proot 执行的命令。

workdir
  可选。中文语义是“执行位置”。不要在 UI 暴露 cwd 这种术语。

timeoutMs
  可选。防止命令卡死。可由平台给默认值。

delayAfterMs
  可选。表示该 step 执行后等待多久再进入下一步。

expected
  可选。没有 expected 就只返回结果，不判断成功。

open_web
  逻辑动作。由 Kite 执行，不由 KF 执行。
```

### 4.2 script 模式

定位：

```text
脚本模式。
适合 AI 生成、官方包、可信分享包、复杂工作流。
```

关键思想：

```text
AI 高效模式不是一条条填命令，而是在 KF Linux 内写一个主脚本，再写一个 recipe.json。
Kite 扫描 recipe 生成卡片；KF 执行脚本；Kite 打开结果。
```

示例：

```json
{
  "execution": {
    "mode": "script",
    "script": "scripts/start.sh",
    "workdir": "/workspace/hermes",
    "timeoutMs": 60000
  },
  "defaultUrl": "http://127.0.0.1:8648",
  "expected": {
    "mode": "contains",
    "text": "ready",
    "source": "lastMeaningfulOutput"
  }
}
```

说明：

```text
script 路径应相对 Recipe 包目录。
复杂命令逻辑应放进脚本，不要强迫用户在 UI 里配置很多 step。
script 包适合可信来源；第三方 script 后续需要安全提示。
```

### 4.3 android_action 模式

定位：

```text
后期安卓原生能力预留。
当前只写概念，不实现。
```

示例：

```json
{
  "execution": {
    "mode": "android_action",
    "action": "open_app",
    "params": {
      "package": "com.example.app"
    }
  }
}
```

说明：

```text
android_action 后期用于 ADB / Shizuku / 无障碍 / 系统助手等能力封装。
V0 不实现，只预留。
```

## 5. Recipe 不控制传输层

Recipe 不包含 Kite 和 KF 的传输配置。Recipe 不允许声明：

```text
bridgeUrl
bridgePort
token
kfPackage
transport
KF 启动方式
```

禁止或忽略字段示例：

```json
{
  "bridgeUrl": "http://127.0.0.1:8799",
  "token": "...",
  "transport": "http"
}
```

处理原则：

```text
如果导入 Recipe 中出现这类字段：
  V0 可以忽略。
  后续可以标记为风险。
```

核心规则：

```text
AI / Recipe 作者负责脚本和配置。
平台负责通信通道。
```

## 6. source 与 runtimeSource

`source` 或 `runtimeSource` 不应被 recipe 文件本身信任。真正来源应由 RecipeLoader 根据文件位置赋值。

推荐运行时来源：

```text
assets/recipes/                -> runtimeSource = assets
files/recipes/imported/         -> runtimeSource = imported
files/recipes/                  -> runtimeSource = user
future remote / package import  -> runtimeSource = remote
```

如果 recipe 文件里自带 `"source": "assets"`，loader 不应直接信任。它可以保留为 metadata，但运行时来源以 loader 判定为准。

## 7. 自动加载机制

Kite 首页卡片来自 Recipe loader：

```text
assets/recipes/*.json
files/recipes/imported/*.json
files/recipes/*.json
```

规则：

```text
新增或导入 recipe 后，只要进入扫描目录，首页就能出现卡片。
删除 recipe 后，首页消失。
App 重启后重新扫描恢复。
Kite 不依赖硬编码组件绑定业务卡片。
Recipe 是开放工作流单元。
```

冲突优先级：

```text
user > imported > assets
```

后续分享码、GitHub recipe pack、zip recipe pack 都应落到这个加载机制上。

## 8. Recipe Pack 推荐目录结构

```text
hermes-webui/
  recipe.json
  scripts/
    start.sh
    stop.sh
  icon.png
  README.md
  assistant.md
```

说明：

```text
recipe.json
  Kite 扫描并生成卡片。

scripts/start.sh
  KF Linux 内执行的主脚本。

assistant.md
  给 AI / Recipe Assistant 看的维护说明，可选。

README.md
  给人看的说明，可选。
```

## 9. 轻量安全审查

steps 模式适合透明审查。script 模式能力更强，但第三方脚本风险更高。

V0 可做简单关键词扫描，本协议先定义风险语义，不实现完整扫描。

中风险：

```text
curl
wget
git clone
npm install
pip install
apt install
```

高风险：

```text
curl ... | bash
wget ... | sh
rm -rf
chmod +x downloaded_file && ./downloaded_file
sudo / su
adb
shizuku
```

提示语义：

```text
如果 Recipe 包含从网络下载并执行脚本，应提示用户来源风险。
如果 Recipe 来自第三方 script 包，应提示执行前审查。
```

## 10. Recipe Assistant

Recipe Assistant / Skill 负责：

```text
写 Linux 脚本
写 recipe.json
根据 Run Report 修脚本
根据 lastMeaningfulOutput 建议 expected
根据输出 URL 建议 defaultUrl / open_web
```

Recipe Assistant 不负责：

```text
不负责决定 Bridge 地址。
不负责决定 token。
不负责决定 KF 怎么启动。
不负责决定传输层。
```

一句话：

```text
AI 生成工作流，平台控制通道。
```
