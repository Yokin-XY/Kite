# Host Git 快速通道

## 候选依据

正式资源关系中，Git 被 10 个上层资源依赖，是 Node 之后覆盖面最大的通用底层工具。Git 的本地 `status/add/diff/log` 会大量访问仓库小文件和元数据，理论上可能放大 PRoot 路径翻译成本。

选择 Git 不是给 OpenClaw、Hermes 或某个 Agent 特化。目标若成立，是让满足同一 Git 身份和能力合同的结构化执行请求受益；不满足时仍整条走 PRoot。

其他候选暂后置：

| 候选 | 正式直接依赖数 | 当前结论 |
| --- | ---: | --- |
| Git | 10 | 首个未覆盖高 reach 候选，进入矩阵 |
| curl | 4 | 静态 HTTPS 已有 Android 原生能力，任意网络请求的启动收益占比低 |
| uv | 1 | 依赖 Python 子进程、venv 与下载，当前能力边界要求 PRoot |
| tool.env/shell applets | 多个间接使用 | shell、PATH、管道和工具差异过宽，不能作为一个 Provider 一次开放 |

## 身份与资产边界

- 受管命令必须从 `/workspace/.kf/bin/git` 沿受控链接解析到当前 rootfs 的 `/usr/bin/git`；未知路径、循环、越界或非 ARM64 ELF 失败关闭。
- 只复用 `GlibcHostRuntimePreparer` 发布的 Kite launcher、修补 loader/libc、compat 和 Android DNS 副本；不复制第二套 Git 或 glibc。
- rootfs/Git/launcher/compat 任一身份换代都重新解析，不能复用旧计划。
- Host cwd 只允许映射 rootfs、workspace 与 `.kf` 受控根；容器绝对路径环境必须逐字段映射。

## 不能一次开放的 Git 语义

Git 既有 builtin，也会按配置或子命令创建外部进程：

- hooks；
- pager/editor；
- external diff、textconv、clean/smudge filter；
- credential helper、askpass、SSH；
- `git-remote-*`、Git LFS；
- submodule 递归；
- 自定义 alias 中的 shell。

Python Host 已证明任意子进程不能自动继承 Host glibc 映射，因此这些能力必须逐层验证。第一阶段不得按“git 命令能启动”外推全部 Git 兼容。

## RF1220 矩阵

同一 Git 二进制、同一工作区仓库分别通过 Host glibc 与独立 PRoot 执行：

1. `--version`、`init`、`rev-parse`；
2. 受控 1/100/1000 文件仓库的 `status --porcelain`；
3. `add`、无 hook 的 `commit -m`、`log`、`diff`；
4. 1/4/8 并发只读 status；
5. hook、pager、external diff/filter、remote helper、SSH/credential 与 submodule 反例；
6. 输出、exit code、仓库/index/commit 身份一致后才比较 P50/P95。

Host 进程启动成功不等于矩阵通过。若只能通过按 subcommand 写白名单、屏蔽错误或跳过 Git 配置才能工作，则 RF1230 直接 no-go。

## 当前状态

RF1220 已在 OnePlus 8T 连续完成两套固定矩阵：

- 1000 文件仓库的 `init/add/commit/rev-parse/status/log/diff` 输出、HEAD 与 index 和独立 PRoot 对照一致；
- Host `status` 的顺序 P50 为 104～107ms，PRoot 为 105～407ms；8 并发 Host batch wall 为 128～132ms，PRoot 为 559～702ms，Host 尾延迟稳定得多；
- shell alias、hook、external diff、clean filter、remote helper 和 submodule 均出现 Host 子进程能力缺口；其中 clean filter 甚至返回 0，但 marker 和 index 内容证明已经静默改变语义；
- PRoot 对照在全部反例中均成功，并产生预期 marker/内容。

因此，“rootfs Git 能在 Host 启动”与“任意 Git 命令可走 Host”是两件事。RF1230 最终判 direct Host Git 生产 no-go：

- argv 不能证明仓库配置、attributes、hooks 与 helper 不会触发子进程；
- 扫描这些输入既不完整，又存在扫描后到执行前被修改的窗口；
- 运行后回退不安全，filter 已证明命令可以 exit 0 却写入错误 index；
- 按 subcommand 白名单只是在底层复制 Git 语义，并非通用能力合同；
- 10 个资源的 `relations.base` 表示依赖可用性，不等于十条安装热路径都执行 Git。正式 manifest 中除 Git 自检外没有静态 `git clone/fetch` 安装步骤，上层运行时的动态 Git 子进程继续由既有 PRoot child bridge 保兼容。

生产资源卡、Git shim、统一 Planner 和运行状态保持不变。若以后继续研究，边界应是“通用 glibc 子进程代理是否能把 Host 父进程的任意 child 原样转入 PRoot”，而不是为 Git 增加命令白名单。
