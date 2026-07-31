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

RF1210 只完成候选和边界审计。生产资源卡、Git shim、统一 Planner 和运行状态均未修改。
