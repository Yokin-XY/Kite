# Kite Recipe Protocol V0.1

Kite Recipe 是 Kite 和 KF Runtime 之间共享的工作流单元。它不是单纯的首页卡片数据，而是同时承担配置、身份、启动入口、运行报告绑定对象和未来分享单元。

## 1. 存储位置

```text
assets/recipes/
  官方内置 Recipe，只读，随 APK 分发。

files/recipes/
  用户创建 Recipe，可编辑，可删除。

files/recipes/imported/
  未来导入 Recipe。V0.3.1 只规划目录语义，不提供导入 UI。

files/recipe-runs/
  运行报告目录，保存 KF Bridge 返回的 Run Report 结构化摘要。
```

Kite 启动时扫描所有可用 Recipe，并由扫描结果生成首页卡片，不通过硬编码组件绑定业务卡片。

同 `id` 冲突时，V0.3.1 使用固定优先级：

```text
user > imported > assets
```

## 2. Recipe 结构

```json
{
  "schemaVersion": 1,
  "id": "hermes-webui",
  "name": "Hermes WebUI",
  "description": "启动 Hermes 图形化工作台",
  "type": "command_web",
  "defaultUrl": "http://127.0.0.1:8648",
  "shortcut": false,
  "source": "assets",
  "steps": []
}
```

字段说明：

```text
schemaVersion
  Recipe 协议版本。V0.3.1 固定为 1。

id
  唯一身份，用于首页卡片、快捷方式、运行报告、状态绑定。

name
  用户可见名称。

description
  首页描述。

type
  open_url / start_service / command_web / template。

defaultUrl
  默认打开地址。

shortcut
  是否计划生成快捷方式。V0.3.1 保存字段，不真实创建快捷方式。

source
  assets / user / imported / remote。

steps
  实际执行步骤。Kite 不执行 shell step。
```

## 3. Step 结构

V0.3.1 支持两类 step：

```text
shell
open_web
```

Shell step 示例：

```json
{
  "id": "step_start_hermes",
  "type": "shell",
  "cmd": "hermes-web-ui start --port 8648",
  "runMode": "wait",
  "expected": {
    "mode": "contains",
    "text": "running",
    "source": "lastMeaningfulOutput"
  },
  "outputPolicy": {
    "mode": "lastMeaningfulOutput",
    "tailChars": 2000
  }
}
```

Open web step 示例：

```json
{
  "id": "step_open_web",
  "type": "open_web",
  "url": "http://127.0.0.1:8648"
}
```

字段说明：

```text
id
  Step 唯一 ID，便于 Run Report 对应。

type
  shell / open_web。

cmd
  shell step 的命令。Kite 只传给 KF Bridge，不在本地执行。

runMode
  wait / detached。V0.3.1 只声明协议。

expected
  可选预期结果。不填则 Kite 不强行判断成功。

expected.mode
  contains / equals。regex 后续再评估。

expected.text
  要匹配的文本。

expected.source
  默认 lastMeaningfulOutput。

outputPolicy
  输出压缩策略，避免大日志直接返回给 Kite。
```

## 4. 输出压缩

KF Bridge 返回时不应把完整 stdout/stderr 全量塞给 Kite。推荐返回：

```text
lastMeaningfulOutput
  stdout/stderr 中最后一条非空、去除 ANSI 控制字符后的有效输出。

stdoutTail
  stdout 最后 N 个字符，默认 2000。

stderrTail
  stderr 最后 N 个字符，默认 2000。
```

完整日志未来由 KF 侧保存，Run Report 只返回引用或摘要。

## 5. 自动加载机制

Kite 首页卡片来自 Recipe loader：

```text
assets/recipes/*.json
+ files/recipes/imported/*.json
+ files/recipes/*.json
```

新增或导入 recipe 后，只要进入扫描目录，首页就能自动出现卡片。删除 recipe 后，首页消失。App 重启后重新扫描恢复。

## 6. Recipe Assistant 预留

Recipe Assistant 是未来的 AI 辅助配置层，职责包括：

```text
读取 recipe 草稿
读取 Run Report
根据 lastMeaningfulOutput 建议 expected 规则
根据输出中的 URL 建议 defaultUrl / open_web step
生成可保存的 Kite Recipe
```

普通用户不需要理解命令成功规则。官方 Recipe、分享 Recipe 和 Recipe Assistant 承担主要配置成本。
