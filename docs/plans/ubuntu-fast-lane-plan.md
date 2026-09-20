# Ubuntu 通用车道方案（讨论稿）

> 2026-09-21 · 状态：讨论稿，未开工
> 前置：快速通道整改（`fast-lane-remediation-plan.md`）与 openat2 监护进程攻坚（进行中）按原计划收口，本方案不改变任何在飞工作。
> 依据：外部同类方案调研（proroot / andClaw / DSHA / lroot / proroom）+ 本仓快速通道全部实证记录。

## 定位与冻结线

- **当前版本 = 收口版**：per-tool 车道（HostNode / HostPython / 终端 shim）+ openat2 监护进程完成后冻结。已啃下的骨头（DNS/seccomp/openat2 降级/glibc 兼容/车道路由与遥测）构成稳定基线，不再扩大投入。剩余 P1/P2 尾项按「已知债务」记录，不强制纳入收口。
- **之后的运行时开发 = 面向整个 Ubuntu rootfs 的通用车道**。从通用层开始做，不再按工具开车道。

## 一、动机：维护轴的转移

per-tool 车道的成本不在建设（工具就那么几个），而在维护：每次官方发版都要重验该工具的路径假设、venv 布局、ABI evidence 与清单合同。通用车道把翻译职责从**入口合同**挪到**进程内部翻译层**——翻译层不认识 Node 也不认识 Python，只认识路径和 syscall，二进制换版本与它无关。

| 维护轴 | per-tool 车道 | 通用车道 |
|---|---|---|
| Node/Python/工具官方发版 | 每次重验合同 | 零改动 |
| 新增冷门 syscall 被 seccomp 拒 | 按工具补 | 按系统补，监护兜底 |
| rootfs 代际升级（24.04→26.04） | 同样要过 | 唯一保留的重轴（一年一两次，代际夹具重跑） |
| exec 长尾（脚本/symlink 花活） | 不碰（走 PRoot） | 变为自己的责任，靠降级缓存兜 |

## 二、外部调研结论（决策依据）

| 项目 | 事实 | 对本方案的含义 |
|---|---|---|
| coderredlab/proroot | 闭源专有（禁改、禁再分发修改版、须署名）。LD_PRELOAD + ELF 内联 svc 二进制补丁 + seccomp/SIGSYS trampoline，完全去 ptrace；vkmark 7.45×。补不到直接 SIGSEGV（issue #22），只能等作者。主力已转 proroom，仓库停更快 3 个月 | 验证路线可行；**不可采用**（许可+单人+停更）；其 issue 列表 = 我们的测试矩阵 |
| coderredlab/andClaw | proroot+Ubuntu 24.04 跑 OpenClaw 网关，Play 在售。生产链路（rootfs 分发、W^X 伪 .so、watchdog、survivor reattach）完整 | 链路可参照；无 LICENSE |
| DSH-APP/DSHA | 第三方双引擎接入：proroot 默认 + PRoot fallback，三层回退，供应链硬校验 | 双引擎模式的自研版即本方案形态 |
| huangshaozhe/lroot | Rust 4557 行开源最小实现，零实战、无许可证 | 不可依赖；「最小正确集 + 典型死法」教材（见坑位表） |
| proroom（作者去向） | 一体化通用 Linux App（终端+桌面、rootfs 下载、Play Billing），未发布 | 竞品观察项；Kite 定位是 Agent 容器，不与其正面重叠 |

**结论**：方向是 Android 约束下的必然（ptrace 太慢、seccomp 会杀、只剩进程内翻译+syscall 兜底），无捷径可抄。我们已有的「hook 层为主 + 跨进程监护兜底」混合架构恰是 proroom（纯进程内、无兜底）所缺的差异化。

## 三、目标与非目标

**目标**
1. 之后所有运行时开发面向整个 Ubuntu rootfs：任意 rootfs 内动态 aarch64 ELF 默认宿主直跑，无需 per-tool 合同。
2. 工具官方版本更新零适配；rootfs 代际升级为唯一维护轴。
3. PRoot 降为兜底车道：任何补不住的命令自动降级且遥测可见，绝不静默崩溃。
4. 现有 node/python 车道保留为优质预设，不推翻。

**非目标**
- 不改变当前收口版的在飞范围。
- 不追求 100% 去兜底（与 proroot 哲学相反：性能换确定性）。
- 不做多 arch（aarch64-only；x86 模拟器、多架构 syscall 表不承诺）。
- 不做桌面 GUI / X11 / VNC（proroom 方向，不是 Kite 方向）。

## 四、架构：四件套（全部长在现有设施上）

```
命令入口（终端 / Agent / 后台 / 资源卡）
→ 车道 Planner（通用规则：rootfs 动态 ELF → Ubuntu 车道；声明式例外 → PRoot）
→ RootfsHostExecutor 通用启动器（PT_INTERP 解析 → rootfs loader + 兼容 so + guest env + chdir）
→ libkite-glibc-compat.so 翻译层（路径正向翻译 / 返回值剥离 / exec 链 / proc 伪造）
→ kite-syscall-tracer 车道监护（seccomp 捕获名单，openat2 起逐步扩到 renameat2/statx/faccessat2/clone3/rseq）
→ 失败上报 → 降级缓存（按命令负缓存 → 下次自动 PRoot）→ RuntimeLaneTelemetry / 诊断面板
```

| 层 | 职责 | 从何泛化 |
|---|---|---|
| RootfsHostExecutor | 任意 ELF 通用启动：读 PT_INTERP 用 rootfs loader、挂兼容 so、注入 guest 视图（PATH/HOME/TMPDIR） | HostNodeRuntimePreparer 去专用化（ELF 验证、身份绑定资产全保留） |
| 翻译层 | 进程内透明翻译：带路径调用 guest→host 正向翻译；getcwd/realpath//proc 返回值方向剥离；shebang/symlink/envp exec 链；/proc 伪造。全部裸 syscall 直发 | libkite-glibc-compat.so（现有 /tmp 重写 + ENOSYS 仿真为起点） |
| 车道监护 | 跨进程兜底：seccomp SECCOMP_RET_TRACE 只拦名单内 syscall，两段单步降级（保 flags）；静态独立进程 | kite-syscall-tracer 扩捕获名单，架构不变 |
| Planner + 降级缓存 | 通用准入规则（动态 aarch64 ELF 即默认）+ 按命令负缓存自动降级 | RuntimeLane / RuntimeLaneTelemetry / 诊断面板复用，加 lane 枚举 |

hook 层与监护层是**分工**不是叠加：glibc 正常调用（apt/dpkg 的 renameat2 密集负载）被 PLT hook 在进程内零开销接住；只有绕过 PLT 的内联 svc（uv/cargo/rustc/go/esbuild）才进 tracer 两段停止。这是对 PRoot（全量 ptrace）与 proroot（无兜底）的双优结构。

## 五、与现有设施的关系（不推翻清单）

| 现有设施 | 处置 |
|---|---|
| HostNode / HostPython / 终端 shim 车道 | 保留为优质预设（身份绑定 libc 补丁等额外保证不撤）；通用层覆盖面够了再评估吸收 |
| openat2 监护进程（在飞） | 照常完成；收口后即成为车道监护层的首个名单项，只扩名单不改架构 |
| RuntimeLaneTelemetry / 诊断面板 / lane Planner | 复用，扩展 lane 枚举与降级缓存消费方 |
| PRoot 兜底车道 + 健康探测 | 原样保留，地位从「默认」降为「兜底」 |
| HN-003 式身份夹具 | 泛化为 rootfs 代际夹具：rootfs/glibc 换代时全量重跑 |
| resolv.conf fd99 / DNS 通道 | 通用启动器直接继承 |

## 六、分期计划（每期独立可交付、可验收）

| 期 | 内容 | 验收标准 |
|---|---|---|
| **U0 收口冻结**（前置，随当前版本） | 在飞 openat2 监护完成；per-tool 车道合同测试固化为回归护栏；剩余尾项记为已知债务 | 冻结版全部既有测试绿；债务清单落档 |
| **U1 通用启动器** | RootfsHostExecutor 去专用化（PT_INTERP 解析、guest env、ELF 前置验证拒绝） | `/bin/true`、`/bin/sh -c`、`git --version`、rootfs python3 任意路径直跑成功且无 per-tool 合同；失败在进程创建前拒绝 |
| **U2 翻译层覆盖面**（大头，按工具族渐进） | U2a 路径正向翻译（per-call 栈缓冲）→ U2b 返回值方向剥离（精确相等断言，禁止 ends_with 自欺）→ U2c exec 链（shebang/绝对 symlink 追随/envp LD_PRELOAD 存活检查/显式重建 envp）→ U2d /proc 伪造（maps/cmdline/mountinfo） | bash 脚本套件、git 基本操作、uv/cargo（内联 svc 代表）、Python $ORIGIN vendored wheel 全绿 |
| **U3 监护扩名单 + 降级缓存** | tracer 捕获名单扩 renameat2（保 flags 或拒绝，禁丢 RENAME_NOREPLACE）/statx/faccessat2/clone3/rseq；按命令负缓存自动降级 | uv renameat2 场景跑通；不可仿真命令自动降 PRoot 且面板可见；apt 类 renameat2 密集负载确认走 PLT hook 层（无 ptrace 开销） |
| **U4 Planner 语义反转** | 通用规则默认化：rootfs 动态 ELF → Ubuntu 车道；FULL_LINUX/ANDROID_NATIVE 声明式例外 | 默认命令走 Ubuntu 车道；回退率入面板；git RF1230 no-go 结论在 exec 链翻译就绪后重测 |
| **U5 收编与前瞻**（可选） | node/python 预设吸收评估；16KB page 能力门；glibc 代际夹具演练（模拟 rootfs 升级一轮） | 吸收决策有数据支撑；16KB 设备能力判定落地 |

顺序硬约束：U1→U2→U3 可与 U2 子项交错；U4 必须在 U2c（exec 链）+ U3（降级缓存）之后，保证反转当天兜底闭环。U2/U3 每补一块，PRoot 兜底面缩一块，无一夜切换风险点。

## 七、坑位清单（外部尸检 → 我们的防线）

| 坑 | 来源 | 防线 |
|---|---|---|
| 内联 svc 绕过 PLT | proroot #5/#11（uv renameat2）、#4（curl SIGSYS） | tracer 捕获名单（U3） |
| renameat2 降级丢 flags → 静默覆盖用户文件 | lroot | 降级保 flags 或显式拒绝（U3） |
| ptrace 自锁（hook 导出 ptrace 桩 + tracer 走 PLT） | lroot | tracer 静态独立进程 + 全裸 syscall（现有架构已具备，保持） |
| 返回值方向泄漏（getcwd/maps/cmdline） | lroot | U2b 精确相等断言 |
| execvp 传 NULL envp → 子进程环境全丢 | lroot | exec 族 hook 显式重建 envp（U2c） |
| 单缓冲 clobber | lroot | per-call 栈缓冲（U2a） |
| syscall 号按 cfg(android) 误硬编码（≠aarch64） | lroot | aarch64-only 范围声明；syscall 表按 (os, arch) 组织 |
| glibc 私有 ABI 漂移（_rtld_global/link_map） | proroot（cleanroom 链接器动机） | 身份绑定副本 + rootfs 代际夹具（HN-003 泛化） |
| $ORIGIN 相对 RPATH 解析失败 | proroot #10 | U2 验收矩阵覆盖 |
| /proc/self/exe 未伪造 → 解释器路径漂移 | proroot/lroot | U2d |
| 16KB page 设备 | 新 Android 要求 | U5 能力门，非当前阻塞 |
| 补不到就崩 | proroot #22（Debian 13 /bin/true SIGSEGV） | 混合架构：监护兜底 + 降级缓存（本方案差异化） |

## 八、决策点（需拍板）

1. **收口冻结边界**：在飞工作完成后，快速通道整改 P1 剩余项（会话管理命令、supervisorctl 原生化、版本探测治理）是否纳入收口。建议：只收在飞的 openat2 监护；尾项记入已知债务随冻结版落档。
2. **git 重测时机**：建议 U4（exec 链翻译就绪后重测 RF1230 no-go 是否仍成立）。
3. **node/python 预设吸收时机**：建议 U5 用数据评估，不预设结论。
4. **版本节奏**：通用车道属下一周期用户可见新能力；版本号按《版本与发布规则》另议，本方案不定版本。
