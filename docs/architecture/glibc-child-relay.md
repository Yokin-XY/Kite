# 通用 glibc Child Relay

## 问题边界

Node、纯 Python 与 Git 已证明同一条分层事实：Ubuntu glibc 父 ELF 可以通过 Kite Host launcher 直接运行，但父进程一旦创建外部 child，child 会重新遇到 Android 根目录、动态链接器、PATH 与 Linux 工具语义差异。

Child relay 的候选职责只有一个：在 Host glibc 父进程真正执行外部 child 前，把该 child 的 argv、env、cwd 和进程语义交给既有 PRoot 物理前缀。它不识别 Git、Python、Node、资源 ID、subcommand 或脚本内容。

```text
Host glibc parent
  -> exec/spawn boundary
  -> generic relay
  -> existing PRoot argv/env/bind/network builder
  -> exactly one Linux child
```

父进程不回到 PRoot；只有它主动创建的外部 child 进入兼容车道。若 relay 无法保持 POSIX 观察语义，父请求必须在创建前整条选择 PRoot。

## 已有实现事实

- `kite-glibc-host-launcher` 使用 glibc loader 的 `--preload` 只把正式 compat 库加载到当前目标，不自动把它注入后代。
- 正式 compat 库当前只处理 Android seccomp 下的 syscall/robust mutex 兼容，不拦截 exec/spawn。
- Node child bridge 位于 JS `child_process` 层，已经实现受控根路径映射、cwd 替换、环境净化和 PRoot 前缀复用；它只能证明 Node API 路径，不能覆盖任意 glibc 程序。
- `WorkSurfaceRuntimeBridge.buildArgvExecConfig` 最终复用 `KFContainerManager` 的 rootfs、bind、network、View 和环境构造；relay 不得复制这些规则形成第二份 PRoot builder。

## 必须覆盖的创建入口

只拦截一个 `execve` 名称不足以证明完整性。候选矩阵至少包含：

| 类别 | 入口 | 风险 |
| --- | --- | --- |
| exec | `execve/execv/execvp/execvpe`、`execl/execlp/execle`、`fexecve` | glibc 内部 hidden symbol 可能绕过动态符号 interpose |
| spawn | `posix_spawn/posix_spawnp` | file actions 与 attributes 必须作用于同一个最终 child |
| shell | `system/popen` 与 `sh -c` | 可能由 libc 内部直接 spawn shell |
| shebang | 可执行脚本 | kernel 解释器解析必须发生在 PRoot 根语义内 |
| fork/vfork | fork 后 exec | 多线程进程中不得在 child 侧做 malloc、锁或复杂路径扫描 |

`clone` 或纯 fork 不应被 relay 接管：它们没有外部 executable，仍属于父程序自己的进程语义。

## 必须保持的观察语义

### argv、env 与 cwd

- argv 顺序、空参数、非 ASCII 和大参数不能变化；
- 当前 cwd 只能从物理 workspace/control/rootfs 映射到 `/workspace/.kf`、`/workspace` 或容器 `/`，未知宿主路径失败关闭；
- envp 必须保留调用方覆盖，同时移除 Host loader/preload 私有字段并合入 PRoot 物理环境；
- 只替换受控根的完整物理前缀，不能把普通文本误判为路径；任何含 NUL 的值本来就不属于 POSIX argv/env。

### fd、stdio 与进程属性

- stdin/stdout/stderr、pipe、重定向和非 `CLOEXEC` fd 必须传到最终 child；
- `CLOEXEC` fd 应在第一次 exec 时关闭，不能因 wrapper 多一层而泄漏；
- `posix_spawn_file_actions`、process group、signal mask/default 和 scheduler attributes 必须保持；
- relay 不创建旁路消费线程，不复制 stdout/stderr，也不改变 terminal/PTY owner。

### exit、signal、取消与唯一执行

- 父进程只能观察一个 child PID/handle；wait exit、signal death 和 core 状态必须等价；
- 向该 child 发 signal 必须到达实际 tracee，不能只终止 PRoot tracer后遗留进程；
- 取消、超时和父进程死亡不得留下第二个 child 或 PRoot 残留；
- relay 自己不能先直接尝试 child，再失败补跑 PRoot；路由必须在任何业务 child 创建前决定。

## 两个先验高风险点

### 同步 exec/spawn 错误

直接 `execve` 找不到目标时会返回 `-1/ENOENT`，`posix_spawn` 也可以同步返回错误。若 relay 先成功 exec/spawn PRoot wrapper，而容器目标随后失败，父进程只会看到异步 exit 127。这个差异可能改变上层控制流，不能用“最终都失败”掩盖。

Debug 矩阵必须包含 missing、permission denied、错误 shebang 和错误动态链接器。若无法在不复制容器解析器的前提下保留同步错误，生产合同只能显式声明并验证异步 child 语义；无法声明的任意 glibc 父进程保持 PRoot。

### glibc 内部绕过与递归

动态符号 interpose 未必覆盖 libc 内部对 hidden `__execve` 或 spawn helper 的调用。探针必须记录每个 API 是否真正进入 relay，并用实际 child 结果交叉验证。PRoot wrapper 和其后代不能再次加载 relay，否则会递归套 PRoot；独立 Debug preload 只注入被测父目标，并设置一次性 guard。

## RF1320 固定矩阵

Debug-only 资产与正式 compat 库完全分离，比较直接 Host、Host+relay、独立 PRoot：

1. argv/env/cwd 与相对路径；
2. stdin、stdout、stderr、pipe 和额外 fd；
3. exit 0/非零、signal、timeout/取消；
4. PATH executable、绝对 executable、shebang；
5. missing、EACCES、坏 shebang、坏 loader 的同步错误；
6. exec 与 posix_spawn 家族的命中计数；
7. 1/4/8 并发和残留进程；
8. guard 防递归与一次业务 child。

## 发布门

- 不允许按工具名、命令名、资源或调用页面选择；
- 不允许复制 rootfs/bind/network/View 构造；
- 不允许只看 exit code，必须核验输出、文件状态、fd、signal 和残留；
- 任一常见入口漏拦、同步错误不可接受、路径文本误改、双执行或递归即 no-go；
- RF1320/1330 通过前，正式 launcher、compat、Provider、资源和运行 lane 全部不变。

## RF1320 真机结论

OnePlus 8T 固定矩阵得到分层结论，而不是一个笼统的“可用/不可用”：

- `execve/execv/execvp/execvpe/execl/execlp/execle`、`posix_spawn/posix_spawnp`、fork 后 exec 均实际命中 relay；
- argv/env/cwd、stdin/stdout/stderr、脚本 shebang、spawn file actions、exit 37 与 signal 15 和独立 PRoot 对照一致；
- 1/4/8 并发三轮均零失败。8 并发 Host parent + relay batch wall 137ms，PRoot parent 为 209ms；relay 没有把 child 变快，但保留 Host parent 后减少了父层 PRoot 成本；
- `system/popen/fexecve` 没有命中导出 exec/spawn interpose。用 Ubuntu-only `git --version` 反例后，system/popen 明确失败；fexecve 输出也与 PRoot 不同；
- missing executable、missing spawn、EACCES 与坏 shebang 均从父进程的同步返回变为 PRoot wrapper 的异步失败，预设风险被证实。

因此 unrestricted glibc child relay 为 no-go。RF1330 只允许验证一个更窄但仍通用的候选合同：调用方明确只使用已捕获的 direct exec/spawn 家族，并接受 child 创建成功后以异步 exit 报告容器目标失败。它仍不识别 Git/Python；若真实依赖进入 system/popen/fexecve 或依赖同步 errno，则整条 PRoot。

## RF1330 调用方复算结论

OnePlus 8T 用同一 relay 复算 Git 与 Python，结果证明选择单位必须是“调用语义”，不能是“工具”：

- Git 的 shell alias、hook、external diff、clean filter、remote helper 与 submodule 在被测版本中均通过 `execve` 命中 relay；marker、index、输出与独立 PRoot 一致。8 并发 Host parent + relay 为 145ms，独立 PRoot parent 为 566ms；
- Python 的 `subprocess`、直接 `os.execve`、venv 创建与 venv child 可经 `execv/execve` 进入 PRoot child；
- 同一个 Python 父进程的 `os.system` 不命中 relay，Host 失败而 PRoot 成功。这个反例否决“Host Python + relay 等于完整 Python 兼容”；
- relay 对 `PATH` 的每个分段和受控根开头的环境值做通用容器路径映射，未读取工具名、资源 ID、subcommand 或脚本内容。

因此可进入 RF1340 的只有肯定式窄合同：调用方明确保证只走已覆盖 direct exec/spawn，并接受目标解析失败由 child exit 异步表达。未知或空声明、shell API、fd exec、同步 errno 依赖全部失败关闭到 PRoot。正式 Provider、launcher、compat 资产、资源和 lane 在 RF1330 仍未修改。

## RF1340 生产门结论

当前 preload relay 不进入正式运行链。阻断不是 Git/Python 结果矩阵，而是实现级生命周期：

- `exec` 路径读取 prefix/env 文件并动态分配；多线程父进程 fork 后的 child 只能调用 async-signal-safe 操作，现有实现没有该证明；
- Debug runner 自己创建并删除控制目录，正式 Provider 生成计划时没有可绑定的 run identity 与进程终态清理钩子；
- 把控制文件改成全局共享会引入并发覆盖，把它永久保留会泄漏环境事实，两者均违反既有唯一运行和状态拥有者合同。

因此 RF1300 以“窄协议可行、当前实现生产 no-go”收口。Debug C 源、构建脚本和固定矩阵继续作为研究证据；正式 glibc 资产、Host Python、资源声明、Planner 与运行事实不变。未来重开必须先提供 fork-safe 的预计算/无分配路径，以及由既有 run owner 管理、进程终态可回收的配置生命周期。
