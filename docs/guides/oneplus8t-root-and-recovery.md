# 一加 8T（KB2000）Root 与恢复方案

> 状态：**已完成（2026-09-19）**。目标设备：一加 8T 国内版，serial `3f8bbaad`。Root 已验证（`uid=0`，Magisk 30.7），Shell（ADB）策略为允许。
> 实战记录见文末 §11。

## 0. 用户目标与设计对应

| 用户要求 | 方案如何满足 |
| --- | --- |
| 不变砖 | 只写 `boot` 一个分区；`system/vendor/product` 一律不碰。软砖（开机循环）用原厂 boot.img 即可救活 |
| 可以恢复 | 三层恢复梯度：① 刷回原厂 boot.img（去 root）② fastboot 刷完整固件（回原厂）③ MSM 9008 线刷（一加 8T 有对应官方通道，属"杀不死"级） |
| 能成功 root | Magisk 修补 boot.img 流程，版本精确匹配当前固件（见 §2 版本锁定） |

诚实边界：无法承诺数学意义上的"零变砖"，但一加 8T 是救砖资料与工具最全的机型之一，本方案所有写操作均有对应回滚路径。

## 1. 现状快照（实测于方案定稿时）

| 项 | 值 |
| --- | --- |
| 型号 | KB2000（OnePlus 8T 国内版） |
| 系统 | ColorOS 14（Android 14） |
| 版本号 | `KB2000_14.0.0.602(CN01)` |
| incremental | `R.1983865_1_2`（OTA: `KB2000_11.H.25_3250_202408141854`，2024-08-14） |
| 活动槽 | `_a` |
| Bootloader | **仍锁定**（`ro.boot.flash.locked=1`）。用户仅开启了开发者选项中的"OEM 解锁"开关 |
| Magisk | 未安装 |

## 2. 版本锁定原则（成功 root 的命门）

- 刷入的 boot.img 必须来自 incremental 完全等于 `R.1983865_1_2` 的完整固件包。
- 解锁 BL 只清数据、不改系统版本，因此提前提取的 boot.img 在解锁后仍然匹配。
- 若官网找不到 602(CN01) 的完整包而只有更高版本：先不做 root，改为"升级系统到可获取完整包的版本 → 重新提取"，禁止用版本不匹配的 boot.img 硬刷。

## 3. 物料清单（阶段 0，全部电脑端）

1. platform-tools（adb/fastboot，现有）。
2. 官方完整固件包：一加 8T 国内版 `KB2000_14.0.0.602(CN01)` 完整 zip。来源：OPPO/一加中国官网支持页的固件下载；执行时取直链并记录 sha256。
3. `payload-dumper-go`（Windows x64）：从固件 zip 内的 `payload.bin` 提取分区镜像。
4. Magisk 稳定版 APK（≥ v27，GitHub releases）。
5. 兜底：KB2000 对应的 MSM Download Tool 完整包（本地方案，不一定随每次执行下载，但救砖前必须备好）。

## 4. 阶段 1：提取并备份原厂 boot（可逆性基础）

1. 下载完整固件 zip，记录 sha256。
2. `payload-dumper-go -p boot firmware.zip` 提取 `boot.img`。
   - 本机预期**无** `init_boot` 分区（2020 年 A/B 老机型，ramdisk 在 boot 内）；若提取列表出现 `init_boot` 则一并备份并在执行时上报，届时改按 Magisk 官方"init_boot 机型"流程。
3. 校验与双备份：
   - 记录 `boot.img` 的 sha256、字节数；
   - 保存两份：工作目录 + `D:\xm\Kite\` 之外的独立目录。
4. 检查点：`magiskboot unpack boot.img` 能解出 ramdisk（确认修补目标正确）后才进入下一阶段。

## 5. 阶段 2：解锁 Bootloader（⚠ 唯一需要用户物理操作）

1. `adb reboot bootloader`。
2. `fastboot flashing unlock`。
3. **用户操作**：屏幕出现解锁确认界面 → 音量键选择 `UNLOCK` → 电源键确认。
4. 设备自动 wipe 全部数据并重启（闲置机，无保留诉求）。
5. 检查点：
   - 重启后 `adb shell getprop ro.boot.flash.locked` = `0`；
   - 开机显示 bootloader 解锁提示（红色感叹号/黄字）属正常现象；
   - 重新开启"USB 调试"并完成一次 ADB 授权弹窗（此步需要用户在手机上点一次"允许"）。

副作用说明：解锁后 Widevine 降级（本机无关紧要）；系统 OTA 通道可能提示异常——root 期间不执行系统 OTA。

## 6. 阶段 3：修补 boot.img

首选（最稳，Magisk 官方路径）：
1. `adb install Magisk-v27.apk`。
2. 将原厂 `boot.img` 推入手机 `Download/`。
3. Magisk App → 安装 → 选择并修补一个文件 → 选 `boot.img` → 生成 `magisk_patched-XXXX.img`。
   - 该步骤在 App 内点选；执行时可用 uiautomator 辅助定位，用户在场时也可顺手点两下。
4. `adb pull` 修补镜像回电脑。

备选：电脑端 `magiskboot` CLI 直接修补（参数按 Magisk 源码脚本），仅当手机端路径受阻时启用。

## 7. 阶段 4：刷入并验证

1. `adb reboot bootloader`。
2. `fastboot flash boot magisk_patched-XXXX.img`（写入当前槽对应分区；A/B 设备 fastboot 会自动路由活动槽）。
3. `fastboot reboot`。
4. 验证链：
   - 开机后打开 Magisk App → 状态"正常：已安装（XXXXXXXX）"；
   - `adb shell su -c id` → `uid=0(root)`；
   - 冒烟：安装 Kite debug 包、启动、冷启动时间记录（复用现有验收链路）。

## 8. 恢复手册（三层梯度）

### 8.1 去 root（保留系统与数据）
`fastboot flash boot boot.img`（刷回 §4 备份的原厂镜像）→ 重启即恢复未 root 状态。

### 8.2 回到完全原厂（可再锁 BL）
1. 恢复原厂 boot（同 8.1）；
2. 如需系统层复原：fastboot 刷完整固件镜像组，或经 MSM 完整包线刷；
3. `fastboot flashing lock`（再次清数据）。
   - ⚠ 禁止在 Magisk 未移除时直接上锁（AVB 验证不过 → 无法开机）。

### 8.3 硬救（fastboot 都进不去）
1. KB2000 的 MSM Download Tool（高通 9008 模式）：手机关机 → 按住音量上/下（KB2000 为音量上）插 USB 进 9008 → MSM 工具自动识别 → 刷完整包。
2. 该通道与系统状态无关，是"最后保险"。执行阶段 2 前先把对应 MSM 包与驱动备好。

## 9. 阶段 5（另行确认后执行）：root 后的测试自动化环境

root 权限只服务"测试操控层"，Kite 本体始终以普通应用身份运行（PRoot 路径与用户一致，防止测试失真）：

- 免弹窗授予无障碍/悬浮窗等权限，静默安装任意来源；
- 冻结干扰包，保证测试环境干净；
- scrcpy server 常驻（视频流+控制注入直连，替代截图→识图环路）；
- 以上形成清单后可另行成文，不混入本方案。

## 10. 分工与执行时机

| 步骤 | 执行者 |
| --- | --- |
| 物料下载、提取、备份、修补、刷入、验证、恢复 | AI（本方案执行者） |
| 阶段 2 的 fastboot 物理确认（音量+电源） | 用户 |
| 解锁后重新授予 USB 调试授权（手机上点"允许"） | 用户 |

执行时机由用户指定；建议避开其他窗口正在使用该设备的时段。执行期间任一检查点不过 → 停在该点上报，不带病推进。

## 11. 实战记录（2026-09-19 执行完毕）

与原方案的差异与新增经验，均已实测验证：

1. **固件来源**：onfix 付费线刷包（`KB2000domestic_11_14.0.0.602CN01_2024081418540120.zip`，11.2GB，MD5 通过）。免费渠道（大侠阿木宕机、官方页面仅覆盖现役机型、XDA/TG/GitHub 无国行版）当期全部不可用。
2. **OFP 格式提取**：线刷包内是 MSM 的 `.ofp` 加密容器（AES-128-CFB，密钥表来自 RayMarmAung/ofp_extractor，本次命中 `V1.6.6-1.7.6` 组）。注意：**每段仅前 0x40000（256KB）加密，其后为明文**；manifest 位于文件尾（pageSize 4096，magic 0x7cef）。提取脚本：`D:/xm/kite-device-work/ofp_boot_extract.py`。boot/dtbo/vbmeta 均通过 manifest 内置官方 sha256 校验。
3. **Windows fastboot 驱动死结与解法**：Win11 拒绝未签名 INF；Google 官方 usb_driver 无 `18D1:D00D`。最终通道：**winget 安装 `dorssel.usbipd-win` → UAC 提权 `usbipd bind/attach --wsl --busid 3-7` → WSL2(docker-desktop) 里 Docker 容器（--privileged -v /dev/bus/usb）跑 Linux 版 platform-tools fastboot**。刷入命令样例：
   ```
   docker run --rm --privileged -v /dev/bus/usb:/dev/bus/usb \
     -v "D:/xm/kite-device-work/bin/ptl/platform-tools:/pt" \
     -v "D:/xm/kite-device-work/images:/img" kite-matrix/pi:latest \
     sh -c "/pt/fastboot flash boot /img/magisk_patched-*.img"
   ```
4. **解锁物理确认**：`fastboot flashing unlock` 下发后手机出现确认画面，用户按音量+电源确认，随后自动 wipe 重启；ADB 授权未丢失。
5. **Magisk 修补**：手机端 App 流程全程可用 uiautomator dump + input tap 自动化（安装 → 选择并修补一个文件 → SAF 导航 Download → boot.img → 输出 `magisk_patched-30700_2CW9g.img`）。
6. **ColorOS 授权弹窗被拦**：Shell 的 su 请求不弹 SuRequestActivity（后台 Activity 启动限制，通知也未观察到）。**解法：Magisk App → 超级用户页 → 长按/操作 Shell 条目切换策略为允许**。之后 `adb shell su -c id` 直接 `uid=0`。
7. **root 后冒烟全过**：`settings put` 免弹窗、`pm grant` 静默授权、`/data/adb` 可读。
8. **文件资产**（均在项目外 `D:/xm/kite-device-work/`）：`images/`（boot/dtbo/vbmeta + patched）、`D:/xm/kite-device-backup-cold/`（boot 冷备×2）、`boot-backup.sha256`、`bin/`（Magisk v30.7、payload-dumper-go、Linux platform-tools）、`ofp/`（解出的 OFP + manifest）、`drv/`（驱动尝试存档）。救砖兜底：原始 zip 保留在 `C:/Users/yokin/Downloads/`，内含 MsmDownloadTool.exe + 完整 OFP。
9. **遗留**：Zygisk 未启用（后续按需在 Magisk 设置中开启，需重启一次）；su 超级用户默认响应仍为"提示"，其他 App 首次请求 root 时的弹窗路径未验证（Termux 等场景如遇拦截，参考第 6 条思路处理）。
