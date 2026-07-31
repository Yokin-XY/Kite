# 首页卡片 Schema

## 文件位置

共享卡片使用 JSON 文件，放在：

```text
/sdcard/Download/Kite/cards
```

也可以把一个卡片包目录放入该位置，并在目录内提供 `recipe.json`。应用启动或卡片目录变化后会重新读取共享卡片。

## 最小示例

```json
{
  "base": {
    "id": "",
    "name": "本地服务",
    "description": "启动服务并打开网页",
    "category": "开发工具",
    "icon": {
      "type": "builtin",
      "name": "server"
    }
  },
  "card": {
    "accent": "primary",
    "status": "unknown"
  },
  "launch": {
    "openInstance": true,
    "keepFinishedNotification": false
  },
  "recipe": [
    {
      "type": "shell",
      "cmd": "python3 -m http.server 8080",
      "runMode": "detached",
      "surfaceMode": "silent",
      "workdir": "/workspace"
    },
    {
      "type": "open_web",
      "url": "http://127.0.0.1:8080"
    }
  ]
}
```

共享卡片不要写固定的顶层 `id`。`base.id` 留空，让 Kite 根据导入环境分配本地身份；卡片之间不能依赖别人的本地 ID。

## 顶层字段

| 字段 | 含义 |
| --- | --- |
| `base` | 名称、说明、分类、分组和图标 |
| `card` | 卡片强调色和初始显示提示；运行事实不会写回模板 |
| `launch` | 是否打开独立实例、是否保留终态通知 |
| `recipe` | 默认 `start` 动作的有序步骤 |

解析器仍能读取部分旧格式，但新卡片只应使用以上结构。

## 图标

内置图标使用：

```json
{ "type": "builtin", "name": "terminal" }
```

常用名称包括 `terminal`、`web`、`bot`、`file`、`logs`、`tools`、`code`、`server` 和 `more`。

图片图标使用：

```json
{ "type": "image", "name": "custom", "source": "icon.png" }
```

图片路径应位于同一卡片包内，不要使用任意外部绝对路径。

## 启动配置

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `openInstance` | `true` | 启动后打开独立运行实例 |
| `keepFinishedNotification` | `false` | 完成或失败后是否保留可清除的结果通知 |

## 步骤类型

### Shell

```json
{
  "type": "shell",
  "cmd": "python3 --version",
  "runMode": "wait",
  "surfaceMode": "panel",
  "workdir": "/workspace",
  "timeoutMs": 120000
}
```

### 终端

```json
{
  "type": "terminal",
  "text": "cd /workspace\nopencode\n",
  "surfaceMode": "panel"
}
```

终端步骤创建独立会话，不应复用其他步骤或手动窗口的终端。

### 网页

```json
{
  "type": "open_web",
  "url": "http://127.0.0.1:8080"
}
```

### Android 动作

```json
{
  "type": "android_action",
  "action": "toolchain_doctor",
  "params": {}
}
```

Android 动作名称必须由应用注册；卡片不能任意声明新的系统能力。

### Android 原生能力

```json
{
  "type": "native_capability",
  "action": "network.download_sha256",
  "params": {
    "url": "https://example.com/file",
    "destination": "/workspace/cache/file",
    "maxBytes": "104857600",
    "expectedSha256": "可选的 64 位十六进制摘要"
  }
}
```

`native_capability` 只调用应用已经注册的封闭能力，参数值目前必须是字符串。它不接受任意 shell，也不会根据命令文本或资源
名称猜测能力。没有注册、参数越界或权限不足时应直接失败，不会静默执行另一份 PRoot 任务。该步骤使用报告显示面，不创建终端。

### X11

`x11` 步骤仍可被实验代码解析，但不属于稳定卡片协议。正式卡片不要依赖它。

## 运行模式

`runMode` 可选：

- `attached`：附着当前执行。
- `wait`：等待命令完成。
- `detached`：启动后台进程并登记绑定。
- `background`：后台执行。

`surfaceMode` 可选：

- `auto`：由编排器决定显示面。
- `panel`：打开对应报告、终端或网页。
- `silent`：不主动打开显示面。

## 结果判断

Shell 步骤可以提供 `expected` 和 `outputPolicy`。它们只判断该步骤输出，不允许页面根据文字自行宣布整个实例成功。

```json
{
  "type": "shell",
  "cmd": "echo ready",
  "expected": {
    "mode": "contains",
    "text": "ready",
    "source": "lastMeaningfulOutput"
  },
  "outputPolicy": {
    "mode": "lastMeaningfulOutput",
    "tailChars": 2000
  }
}
```

## 分享边界

- 不写账号、token、Cookie、Bridge 地址或设备专属路径。
- 不假定另一台设备已经安装某个资源；需要的能力应由资源依赖表达。
- 第三方脚本必须说明来源和作用。
- 卡片 JSON 描述动作，底层权限和执行通道由 Kite 决定。
