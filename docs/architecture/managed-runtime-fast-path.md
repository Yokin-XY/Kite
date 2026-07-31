# 通用依赖快速通道

## 定位

快速通道面向 Node.js、Python 等高复用运行时依赖，不面向 OpenClaw 或其他最终应用制作特殊发行版。Provider 只根据解释器、
ABI、资产身份、环境和语义要求判断，不读取资源 ID 或产品名称。

Ubuntu/PRoot 始终是兼容底座；快速通道的价值是让满足严格合同的主进程绕开逐次 PRoot 路径翻译和大量短命令固定成本。

## Node：已验证基线

Node 已经具备宿主启动器、glibc 身份绑定副本、Android seccomp 标准回退、DNS FD、受管 shebang 解析和 Linux 子进程回到
PRoot 的能力。RF210 不重新开发或重跑全部历史矩阵，只完成：

1. 将 Node 现有请求适配为统一 Execution Request；
2. 将现有 Ready/Fallback 映射为统一 Provider 结果；
3. 保持终端、Agent 和后台入口行为等价；
4. 保留 `runtimeLane`、回退原因和唯一进程证明。

Node 的详细合同、HN-001～HN-009 风险索引见[宿主 Node 快速运行时](host-node-runtime.md)，既有性能证据见
[宿主 Node 性能矩阵](host-node-performance-matrix.md)。

只有以下情况触发专项重验：

- Node、Ubuntu rootfs、loader/libc 或兼容库代次改变；
- Provider 抽象改变 argv、env、cwd、stdio、信号或子进程路由；
- 新的真实原生 addon、插件或 Gateway 样本暴露旧矩阵未覆盖的能力；
- 旧证据对应的入口已不再是正式路径。

## Python：首个新增候选

Python 不能因为“也是解释器”直接复制 Node 结论。它需要独立证明：

- 解释器与 glibc/Android seccomp 的启动兼容；
- stdlib、编码、证书、时区、HOME、cwd 和物理 workspace 路径；
- `import`、小文件、CPU、I/O 与 1/4/8/16 并发；
- `subprocess` 对 Python、受管 CLI、shell 和 Linux ELF 的分流；
- venv、pip、纯 Python wheel；
- C 扩展、`dlopen`、构建工具链和直接 syscall。

RF230 先形成 Host/PRoot 同版本对照和 go/no-go。没有达到预先固定的收益、稳定性或回退可证明性时，Python 保持 PRoot，
不为了完成路线而强行上线。

RF230 已在 OnePlus 8T 上形成 go/no-go：纯 Python、标准库、内置 C 扩展和纯 Python wheel 可在 Host 启动；Python
`subprocess` 与 venv 子解释器不能直接执行 Linux ELF，必须在进程创建前回到 PRoot。完整数据和证据边界见
[宿主 Python 性能矩阵](host-python-performance-matrix.md)。

## Python 第一阶段边界

若 RF230 给出 go，RF240 只开放：

- 结构化 `python` executable 与 argv；
- 无 shell 展开；
- cwd、env、stdin/stdout/stderr、退出码和取消可以完整映射；
- 标准库和纯 Python 模块位于身份已验证的运行时布局；
- 不要求完整 `/proc`、Linux mount 视图或未验证的 C 扩展。

任一能力不满足时，必须在 Python 进程创建前整条进入 PRoot。Host Python 已经开始后发生的异常保留在同一个运行实例，不自动
再运行一份 PRoot。

## 子进程原则

快速运行时内部创建子进程时仍通过统一 Planner：

```text
同类受管解释器或最终 shebang
→ 对应快速 Provider 再判断

shell、Git、编译器、Linux ELF 或不满足能力门
→ 完整 PRoot argv/env
```

这里的 Git 只是 Linux 外部命令示例，不是硬编码路由对象。直接绕过解释器 API 调用 `execve/system` 的扩展必须纳入独立
兼容矩阵，不能用应用名补丁。

## 版本和租约

每个通用运行时由唯一资源 Provider 管理安装、版本、资产身份和代次。活跃进程持有代次租约；升级发布新代次，旧进程退出后
才能回收旧代次。存在反向依赖或活跃租约时，不允许卸载当前唯一可用运行时。

## 发布门

- 不改变既有 Node 路径的结果、退出、信号和回退；
- Python 必须先有 go/no-go 矩阵，再有实现；
- 每个解释器分别覆盖真实首个可消费结果，而非只测 `--version`；
- 插件、包管理器、子进程、原生扩展和升级按层验收；
- 每次运行可从状态拥有者证明实际 Provider 与回退原因。
