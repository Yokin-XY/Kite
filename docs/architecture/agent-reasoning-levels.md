# Agent 推理强度统一语义

## 目标

Kite 的推理强度入口使用稳定的用户语义，Agent、Provider 和 Model 只公布能够真实兑现的子集。原生配置值仍用于协议请求、会话恢复和写回；中文名称只负责显示。

能力判断至少绑定 `Agent + Provider + Model + Runtime 版本`。切换模型后必须重新读取会话能力，不能把某个工具曾经出现过的档位长期缓存成该工具的固定能力。

## 七个有序档位

| 稳定 ID | 名称 | 语义 |
| --- | --- | --- |
| `off` | 关闭 | 关闭当前工具可控制的扩展推理；不承诺模型完全没有内部推理 |
| `minimal` | 最低 | 使用最少的可控推理开销 |
| `low` | 低 | 优先响应速度与资源开销 |
| `medium` | 中 | 平衡速度、开销与推理深度 |
| `high` | 高 | 为复杂任务使用更深的推理 |
| `xhigh` | 极高 | 使用模型提供的额外高强度推理 |
| `max` | 最高 | 使用模型提供的最高纯推理强度 |

## 非有序控制语义

| 稳定 ID | 名称 | 语义 |
| --- | --- | --- |
| `enabled` | 开启 | 原生能力只有开关，具体强度不可选择 |
| `adaptive` | 自动 | 工具或模型按任务动态选择强度 |

Kite 不设置“跟随默认”档位。清除原生覆盖属于配置恢复动作，不能混进推理强度选择器。

`ultra`、`ultracode` 不属于纯推理强度。它们在部分工具中还会开启子 Agent、主动编排或动态工作流，后续需要时应作为工作模式或执行策略单独建模。

## 映射与显示规则

1. 适配器只声明原生值到 Kite 语义的映射词表；实际选项必须来自当前会话协议或当前原生能力目录。
2. 原生 value 不改写，Kite 语义作为附加元数据进入 SDK 和 UI。
3. 没有适配器映射的 Select 选项不进入统一推理入口；显示层不得按产品名称或相似文案猜测。
4. 只有一个不可切换值时不显示选择器；二值 Toggle 可以显示为开启/关闭。
5. 当前值无法映射时隐藏整个选择器，不擅自选第一个值，也不静默降级到较低档位。
6. 同一语义的原生别名只显示一项，优先保留当前生效的原生 value。
7. 普通页面只消费已经投影的能力，不在绘制、列表绑定或点击处理中扫描配置、进程或网络。
8. 推理强度不进入跨会话草稿能力缓存；新会话和模型变化必须等待当前运行时重新公布。

## 五个正式 Agent 的边界

- OpenCode：Variant 由当前模型目录和 Provider 配置决定；只映射能够识别的纯推理值，不根据 `fast`、`deep` 等自定义名称猜测。
- OpenClaw：支持 Provider/Model profile 公布的有序档位、二值开启和 `adaptive`；带编排的 `ultra` 不进入本目录。
- Hermes：CLI 能接受某个字符串不等于当前 Provider/Model 能兑现；仍以会话实际公布的子集为准。
- Codex：映射当前模型目录实际提供的 `minimal` 至 `max` 子集；`ultra` 单独建模。
- Claude Code：按模型映射它真实公布的有序档位子集；原生 `auto` 明确表示自动时映射为 `adaptive`，`default` 不进入推理选择器，`ultracode` 单独建模。

## 参考事实

- [OpenCode Models](https://opencode.ai/v2/docs/models)
- [OpenClaw Thinking Levels](https://docs.openclaw.ai/tools/thinking)
- [Hermes Configuration](https://hermes-agent.nousresearch.com/docs/user-guide/configuration/)
- [Codex Configuration Reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Codex Models](https://learn.chatgpt.com/docs/models)
- [Claude Code Model Configuration](https://code.claude.com/docs/en/model-config)
