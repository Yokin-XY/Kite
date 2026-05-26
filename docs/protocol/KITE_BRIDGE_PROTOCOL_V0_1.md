# Kite Bridge Protocol V0.1

本文定义 Kite 与 KF 执行层之间的调用语义。V0.1 仍可用 localhost HTTP 验证，但协议不绑定某一种传输实现。

## 1. 三层边界

```text
平台传输层
  Kite/KF 固定通信方式、Bridge 地址、token、KF 启动方式。
  这些不允许 Recipe / AI / 分享包决定。

Recipe 工作流层
  Recipe 描述卡片、执行入口、预期结果、打开结果。

KF 执行层
  KF / Bridge 在 Linux/proot 内执行脚本或命令，返回 Run Report。
```

## 2. 平台执行接口

当前验证阶段：

```text
Kite -> 127.0.0.1:8799 -> KF Ubuntu/Linux Bridge
```

这是验证阶段的传输实现，不代表最终必须永远走端口。

后期可替换为：

```text
Android Bound Service / Binder
Internal RuntimeCommandExecutor
Unix domain socket
同 App 内部直接调用
```

但语义不变：

```text
run-recipe
run-command
Run Report
nextAction
lastMeaningfulOutput
```

也就是说：

```text
传输层可替换。
Recipe / Run Report 协议语义不变。
```

## 3. Recipe 不控制传输层

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

## 4. GET /status

用于检查 KF Bridge 是否就绪。

响应示例：

```json
{
  "ok": true,
  "service": "kf-bridge",
  "version": "0.1",
  "status": "ready"
}
```

## 5. POST /run-recipe

Kite 可以向 KF Bridge 发送完整 Recipe，也可以发送 normalized execution payload。

如果发送完整 Recipe，KF Bridge 只读取执行相关字段：

```text
execution
expected
defaultUrl
```

KF Bridge 不应该依赖 UI-only 字段，例如 name、description、icon、shortcut。

推荐 normalized execution request：

```json
{
  "protocolVersion": 1,
  "requestId": "req_...",
  "recipeId": "hermes-webui",
  "execution": {
    "mode": "script",
    "script": "scripts/start.sh",
    "workdir": "/workspace/hermes"
  },
  "expected": {
    "mode": "contains",
    "text": "ready",
    "source": "lastMeaningfulOutput"
  },
  "nextActionHint": {
    "type": "open_web",
    "url": "http://127.0.0.1:8648"
  }
}
```

说明：

```text
V0.3.1 代码当前可能仍发送完整 recipe。
文档规定未来推荐 normalized execution request。
open_web 是 Kite 动作，不由 KF 执行。
KF 可以在 Run Report 中返回 nextAction。
```

## 6. POST /run-command

用于单命令测试或未来 AI 辅助配置。

请求示例：

```json
{
  "protocolVersion": 1,
  "requestId": "req_...",
  "cmd": "echo hello",
  "expected": {
    "mode": "contains",
    "text": "hello",
    "source": "lastMeaningfulOutput"
  }
}
```

`expected` 可选。没有 expected 时，KF 只返回摘要结果，不强制判断成功。

## 7. Run Report 摘要模型

Run Report 不默认返回大量 stdout/stderr。最小结构：

```json
{
  "protocolVersion": 1,
  "requestId": "req_...",
  "runId": "run_...",
  "recipeId": "hermes-webui",
  "status": "finished",
  "ok": true,
  "steps": [
    {
      "stepId": "step_start",
      "type": "shell",
      "status": "finished",
      "exitCode": 0,
      "lastMeaningfulOutput": "ready http://127.0.0.1:8648"
    }
  ],
  "nextAction": {
    "type": "open_web",
    "url": "http://127.0.0.1:8648"
  }
}
```

字段说明：

```text
lastMeaningfulOutput
  最后一条非空、去除 ANSI 控制字符后的有效输出。

stdoutTail / stderrTail
  可选 debug 字段，不应默认返回大量内容。

完整 stdout/stderr
  后续由 KF 侧保存完整日志，Run Report 只返回摘要或引用。

expected / matchResult
  可选。
  KF 可以只返回 lastMeaningfulOutput。
  Kite 可以根据 expected 自己计算 matchResult。
  不应强制 KF 必须做复杂匹配。
```

## 8. 状态语义

```text
accepted
  KF 已接收请求，可能异步执行。

running
  正在执行。

finished
  执行结束。

failed
  执行失败。

bridge_unavailable
  Kite 本地派生状态，表示 Kite 无法连接平台执行层。
  它不是 KF Bridge 返回的远端 Run Report 状态，因为请求没有成功到达 KF。
```

## 9. Kite 消费规则

```text
finished + ok + nextAction.open_web
  Kite 打开 URL。

bridge_unavailable
  Kite 显示“桥接不可用”，记录 diagnostics。

failed
  Kite 显示失败状态，记录 diagnostics。

matchResult.enabled=false
  Kite 不强行判断成功，只显示“已返回结果”。

matchResult 缺失
  等同于 matchResult.enabled=false。
  Kite 不强行判断成功，只根据 status / ok / nextAction 继续处理。

matchResult.enabled=true && matched=false
  Kite 显示“结果不匹配”，记录 Run Report。
```

复杂 stdout 理解不属于 Kite 主流程。复杂判断交给 AI / 用户 / 后续 Recipe Assistant。

## 10. 输出压缩

KF Bridge 推荐返回：

```text
lastMeaningfulOutput
  stdout/stderr 中最后一条非空、去除 ANSI 控制字符后的有效输出。

stdoutTail
  可选 debug 字段，stdout 最后 N 个字符。

stderrTail
  可选 debug 字段，stderr 最后 N 个字符。
```

完整日志后续由 KF 侧保留路径，Run Report 里只返回摘要或引用。

## 11. Diagnostics

Kite 侧保存结构化摘要：

```text
files/diagnostics/bridge-events.jsonl
  requestId / runId / status / nextAction / error

files/recipe-runs/{runId}.json
  KF Bridge 返回的 Run Report 摘要
```

## 12. Recipe Assistant 边界

Recipe Assistant / Skill 可以：

```text
写 Linux 脚本
写 recipe.json
根据 Run Report 修脚本
根据 lastMeaningfulOutput 建议 expected
根据输出 URL 建议 defaultUrl / open_web
```

Recipe Assistant / Skill 不可以：

```text
决定 Bridge 地址
决定 token
决定 KF 怎么启动
决定传输层
```

一句话：

```text
AI 生成工作流，平台控制通道。
```
