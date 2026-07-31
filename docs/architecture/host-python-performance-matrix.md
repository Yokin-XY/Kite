# 宿主 Python 性能与兼容矩阵

## 结论

RF230 对“纯 Python 结构化快速通道”给出 **go**，对“Python 全能力直接 Host 化”给出 **no-go**。

- go：解释器启动、stdlib、内置 C 扩展、pip 模块入口、纯 Python wheel 安装、venv 创建，以及本矩阵五类纯 Python 负载。
- no-go：Python `subprocess` 直接执行 Linux ELF、venv 子解释器直接启动、未验证的第三方 C 扩展和需要完整 Linux 视图的任务。
- 路由要求：上述 no-go 能力必须在 Python 进程创建前整条进入 PRoot；Host 已开始后不得自动重放。

这不是为某个资源或应用制作 Python 特版。候选 Provider 只允许依据解释器身份、ABI、argv、环境和能力声明选择。

## 证据环境

- 日期：2026-07-31
- 设备：OnePlus 8T，serial `3f8bbaad`
- Python：同一份受管 Python 3.14.6
- 对照：独立 Host glibc 启动与独立 Ubuntu/PRoot 启动
- 构建：Debug；覆盖安装保留既有 Ubuntu、工作区和 Python 资产
- 轮数：每个性能测点 3 轮
- 并发：1、4、8、16

固定入口：

```powershell
.\scripts\python-runtime-benchmark.ps1 -Mode Benchmark
.\scripts\python-runtime-benchmark.ps1 -Mode Compatibility
```

Debug Receiver 不接受命令、路径、负载或并发度；外部只能选择上述两组固定矩阵。

## 性能结果

表中时间是同一轮并发任务全部完成的 wall-clock p50/p95；“降低”按 `(PRoot p50 - Host p50) / PRoot p50` 计算。

| 负载 | 并发 | Host p50 | PRoot p50 | p50 降低 | Host p95 | PRoot p95 | 失败 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 启动 | 1 | 105ms | 167ms | 37.1% | 105ms | 186ms | 0 |
| 启动 | 4 | 108ms | 331ms | 67.4% | 109ms | 362ms | 0 |
| 启动 | 8 | 131ms | 427ms | 69.3% | 134ms | 486ms | 0 |
| 启动 | 16 | 167ms | 483ms | 65.4% | 176ms | 519ms | 0 |
| import | 1 | 105ms | 545ms | 80.7% | 105ms | 637ms | 0 |
| import | 4 | 111ms | 265ms | 58.1% | 117ms | 354ms | 0 |
| import | 8 | 222ms | 417ms | 46.8% | 229ms | 437ms | 0 |
| import | 16 | 265ms | 750ms | 64.7% | 312ms | 757ms | 0 |
| 小文件 | 1 | 112ms | 834ms | 86.6% | 113ms | 843ms | 0 |
| 小文件 | 4 | 119ms | 359ms | 66.9% | 120ms | 359ms | 0 |
| 小文件 | 8 | 220ms | 596ms | 63.1% | 227ms | 598ms | 0 |
| 小文件 | 16 | 340ms | 888ms | 61.7% | 366ms | 917ms | 0 |
| CPU | 1 | 108ms | 334ms | 67.7% | 112ms | 335ms | 0 |
| CPU | 4 | 118ms | 253ms | 53.4% | 206ms | 262ms | 0 |
| CPU | 8 | 235ms | 392ms | 40.1% | 250ms | 393ms | 0 |
| CPU | 16 | 368ms | 593ms | 37.9% | 416ms | 649ms | 0 |
| I/O | 1 | 110ms | 534ms | 79.4% | 114ms | 539ms | 0 |
| I/O | 4 | 111ms | 258ms | 57.0% | 125ms | 259ms | 0 |
| I/O | 8 | 229ms | 386ms | 40.7% | 236ms | 402ms | 0 |
| I/O | 16 | 347ms | 730ms | 52.5% | 357ms | 732ms | 0 |

20 组 Host/PRoot 对照、40 个通道测点全部零失败。Host p50 降低范围为 37.1%～86.6%。16 路仍快于 PRoot，
但不能据此把 16 路固定为全局并发；正式调度仍由压力档位和任务类型决定。

## 兼容结果

| 能力 | Host | PRoot | 边界 |
| --- | --- | --- | --- |
| stdlib 与内置 C 扩展 | 通过 | 通过 | 覆盖 `_ssl`、`_sqlite3`、`_hashlib`、`ctypes`、证书与本地 DNS |
| pip 模块入口 | 通过 | 通过 | 只证明入口可用，不开放任意网络安装 |
| 本地纯 Python wheel 安装与 import | 通过 | 通过 | 固定无网络 wheel；生命周期留在 RF250 |
| venv 创建 | 通过 | 通过 | 只证明环境文件可创建 |
| Python `subprocess` 启动同一解释器 | 失败 | 通过 | Linux ELF 解释器路径在 Android 宿主不可直接解析 |
| venv 子解释器启动 | 失败 | 通过 | 与 subprocess 属于同一执行边界 |
| 第三方 C 扩展 wheel | 未开放 | 保持兼容底座 | 内置扩展成功不能外推到任意 manylinux wheel、`dlopen` 或直接 syscall |

Host 的两个失败均发生在 Python 主进程创建后，因此正式 RF240 不能“先试 Host、失败再跑 PRoot”。Planner 必须根据能力声明在
创建任何 Python 进程前选择 PRoot。

## RF240 发布门

以下门槛在生产 Provider 实现前固定：

1. 只接受结构化 `python`/`python3` argv，不接受 shell 文本和资源 ID 条件。
2. 请求声明 subprocess、完整 Linux、View 或未验证 C 扩展时，Host 进程创建数必须为 0，PRoot 只创建一次。
3. Host 与 PRoot 的 cwd、env、stdin/stdout/stderr、退出码、取消和运行事实对照通过。
4. OnePlus 8T 的启动、import、小文件在并发 1 和 8 时，Host p50 相对 PRoot 至少降低 20%，三轮零失败。
5. 真机无新增 ANR/FATAL；Provider 抽象不得改变已冻结 Node 入口、glibc 资产身份或子进程语义。

## 证据限制

- 单设备、Debug 构建和三轮样本适合做 go/no-go，不是全机型容量承诺。
- 未冷却 Linux page cache，因此“启动”表示独立进程启动，不表示首次安装后的物理冷盘启动。
- 本矩阵没有宣称第三方 C 扩展、编译工具链、网络 pip 安装或长期 Python 服务兼容。
- RF240 接入正式 Provider 后仍需按发布门跑最窄生产链路，不重复 Node 历史矩阵。
