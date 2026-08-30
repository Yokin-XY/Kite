# Kite 运行与安装语言分工

## 决策原则

语言只按稳定边界选择，不按页面、Agent 名称或当前设备选择。先证明真实负载、结果等价、安全和失败清理，再决定是否进入生产；
没有证据的模块保持现状。跨语言调用必须粗粒度，业务状态只有一份，JNI 不建立第二套会话、资源或安装事实。

## 当前分工

| 层 | 首选 | 负责 | 不负责 |
| --- | --- | --- | --- |
| Android 产品与 Agent SDK | Kotlin | 页面、生命周期、会话状态、ACP/Provider 适配、资源锁、进度、取消、回滚、持久化 | 大文件逐块处理、压缩算法 |
| 制品事务核心 | Rust | 大文件摘要、ZIP/`tar.gz`/`tar.xz` 解析、安全限制、候选目录、原子发布 | 资源业务规则、UI、Agent 协议、长期状态 |
| Android/Linux ABI 薄层 | C | PTY、signal、loader、preload、必须直接对接 syscall/ABI 的极小模块 | 安装调度、字符串协议、并发状态机 |
| Linux 兼容执行 | shell + PRoot | 任意 shell、发行版工具、编译器、未证明的 Linux 语义 | Android UI 和产品状态 |
| Agent 自身运行时 | Python/Node | Hermes、OpenCode 等上游 Agent 的真实运行逻辑 | Kite 客户端核心重写 |

Go 当前没有合适落点：安装核心需要 Android/JNI 与现有事务紧密配合，Go runtime 和跨 JNI 打包没有带来比 Rust 更好的包体、取消、
安全或库生态证据；ACP 客户端也不应为了换语言复制 Kotlin 状态。C++ 只随已有第三方库使用，不新增一套产品核心。`tar.zst`、7z、
Wasm 沙箱等能力等真实调用方出现后再独立立项。

## 已验证事实

- Rust ZIP 固定样本 105 ms，Kotlin 3,589 ms，PRoot 956 ms。
- 真实 Ubuntu rootfs 在包含 SHA-256 的 Rust 完整事务中约 2.24 秒，Kotlin 解包约 8.30 秒；输出树一致。
- 真实 Node `tar.xz` 的 Rust 完整事务约 2.70 秒，PRoot 约 7.64 秒；输出树一致。
- C/libarchive 与 Rust 的纯解包速度同档，没有形成抵消 C 手工安全成本的优势。
- 通用 glibc child relay 已有真机矩阵：部分 direct exec/spawn 能获益，但同步错误语义、`system/popen/fexecve` 和 fork-safe 生命周期不完整，
  因此生产 no-go。
- ACP 进程通道当前只是分离 stdout/stderr 的缓冲行流；Hermes 启动等待主要来自 PRoot、Python、Agent 初始化和外部服务，不来自 Kotlin
  行读写。把该通道改成 Rust 或 Go 不会消除主等待项。

## 生产接入顺序

1. 保留当前 Debug Rust 引擎和固定矩阵，补实时取消与低频有界进度桥；JNI 仍为“一次请求、一次终态”的粗粒度接口。
2. 把 Rust 工具链、NDK、依赖锁和产物身份纳入可复现构建；再补至少一台低性能/旧系统设备的真实制品矩阵。
3. 先接入独立候选目录的制品准备事务，由 Kotlin 继续持有资源级 single-flight、`writeScopes`、状态和回滚。
4. 首批只迁移 rootfs、工具链包和明确声明 ZIP/`tar.gz`/`tar.xz` 的资源；任意 shell 安装继续 PRoot。
5. 只有新的进程启动 trace 证明 Kotlin/Java 管道本身成为显著 CPU 或延迟热点，才重开 Rust supervisor；现有 C PTY/signal 层不重写。

生产门关闭前，普通 Debug、Release、资源清单和正式路由均保持不变。
