# Kite Bridge Protocol V0.1

Kite Bridge Protocol 定义 Kite 与 KF Runtime / KF Bridge 的 JSON 调用边界。V0.3.1 只补齐 Kite 侧请求结构、Run Report 消费模型和文档，不实现 KF Bridge 服务。

## 1. 安全边界

```text
Kite 不执行 shell。
Kite 只保存、展示、调用 Recipe。
KF Runtime / KF Bridge 负责执行 shell / service / command。
V0.1 Bridge 只允许绑定 127.0.0.1。
正式敏感接口后续必须加入 token / handshake。
open_web step 由 Kite 执行，不由 KF 打开。
```

## 2. GET /status

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

## 3. POST /run-recipe

Kite 点击卡片时，把完整 Recipe 传给 KF Bridge。KF 只执行需要执行层处理的 step，例如 shell。`open_web` 只作为 nextAction 语义返回给 Kite。

请求示例：

```json
{
  "protocolVersion": 1,
  "requestId": "req_...",
  "recipe": {
    "schemaVersion": 1,
    "id": "hermes-webui",
    "name": "Hermes WebUI",
    "type": "command_web",
    "defaultUrl": "http://127.0.0.1:8648",
    "steps": [
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
      },
      {
        "id": "step_open_hermes",
        "type": "open_web",
        "url": "http://127.0.0.1:8648"
      }
    ]
  }
}
```

## 4. POST /run-command

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
  },
  "outputPolicy": {
    "mode": "lastMeaningfulOutput",
    "tailChars": 2000
  }
}
```

## 5. Run Report

KF Bridge 返回结构化运行报告。Kite 不解析复杂 stdout，只按 Run Report 的状态、匹配结果和 nextAction 做轻量消费。

响应示例：

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
      "stepId": "step_start_hermes",
      "type": "shell",
      "status": "finished",
      "exitCode": 0,
      "lastMeaningfulOutput": "server running at http://127.0.0.1:8648",
      "stdoutTail": "server running at http://127.0.0.1:8648",
      "stderrTail": "",
      "matchResult": {
        "enabled": true,
        "matched": true,
        "mode": "contains",
        "text": "running",
        "source": "lastMeaningfulOutput"
      }
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
status
  accepted / running / finished / failed / bridge_unavailable。

ok
  整体是否可继续。

steps
  每一步的结构化结果。

matchResult
  expected 存在时才有实际判断。expected 不存在时 enabled=false。

nextAction
  KF 告诉 Kite 下一步该做什么，例如 open_web。
```

## 6. Kite 消费规则

```text
finished + ok + nextAction.open_web
  Kite 打开 URL。

bridge_unavailable
  Kite 显示“桥接不可用”，记录 diagnostics。

failed
  Kite 显示失败状态，记录 diagnostics。

matchResult.enabled=false
  Kite 不强行判断成功，只显示“已返回结果”。

matchResult.enabled=true && matched=false
  Kite 显示“结果不匹配”，记录 Run Report。
```

## 7. Diagnostics

Kite 侧保存结构化摘要：

```text
files/diagnostics/bridge-events.jsonl
  requestId / runId / status / nextAction / error

files/recipe-runs/{runId}.json
  KF Bridge 返回的 Run Report 摘要
```

完整 stdout/stderr 日志未来由 KF 侧保存，Run Report 只返回摘要和引用。
