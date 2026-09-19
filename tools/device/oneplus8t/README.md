# OnePlus 8T（KB2000，调试机）熄屏冻结豁免

设备：OnePlus 8T，ColorOS（root，Magisk su 可用）。Kite 的常驻调试机。

## 结论（2026-09 实测）

OnePlus 的省电系统会冻结后台应用，且**无视标准豁免手段**：

| 手段 | 效果 |
| --- | --- |
| 前台服务（specialUse）+ 常驻通知 | ❌ 照冻 |
| PARTIAL_WAKE_LOCK | ❌ 照冻 |
| AOSP 电池优化白名单（`dumpsys deviceidle whitelist +com.kite.app`） | ❌ 照冻 |
| `settings put global cached_apps_freezer` / `use_freezer` | ❌ 与此机制无关 |
| **uid 层 cgroup v2 解冻守护（本目录脚本）** | ✅ 熄屏下持续运转 |

### 冻结机制定位

- 决策方：framework 层 `OplusHansManager`（system_server 内），日志 tag `OplusHansManager`，场景 `scene: LcdOff`。
- 执行点：`/sys/fs/cgroup/uid_<APP_UID>/cgroup.freeze` 写 1（**uid 整组冻结**，非 pid 层；`cgroup.events` 的 `frozen:1` 为准，`cgroup.freeze` 文件本身可能被复位为 0 制造假象）。
- 内核侧有定制 `hans_handler`（dmesg 可见 `FROZEN_TRANS`），native 守护 `/system_ext/bin/hans`（`sys.hans.enable` 控制）只是链路一环——**停掉 native hans 依旧会被 framework 冻结**。
- 进程特征：存活但 CPU 时间零增长；解冻后从中断处继续。

### 豁免原理

`magiskpolicy --live 'allow magisk sysfs file { open write }'` 给 Magisk su 域补 sysfs 写权限，
然后常驻循环：发现 Kite uid 的 `cgroup.freeze` 为 1 就写回 0（1 秒轮询）。
uid 动态取自 `/data/data/com.kite.app` 属主，重装换 uid 不影响。

## 使用

```bash
adb push tools/device/oneplus8t/kite-unfreeze.sh /data/local/tmp/
adb push tools/device/oneplus8t/install-guard.sh /data/local/tmp/
adb shell su -c 'sh /data/local/tmp/install-guard.sh'   # 装进 /data/adb/service.d/（开机自启）
adb shell su -c 'nohup sh /data/adb/service.d/kite-unfreeze.sh >/dev/null 2>&1 &'  # 当前会话立即生效
tail -f /data/local/tmp/kite-unfreeze.log  # 观察（root）
```

重启后 service.d 自动拉起守护（每次开机重打 SELinux 规则——`--live` 不持久）。

## 附带结论：USB 安装拦截

ColorOS 的"安全护航"会拦 `adb install`：弹系统确认框（显示包名/版本/大小/敏感权限），
需点"继续安装"。root 不会绕过（系统 UI 层行为）。自动化装机流程里 install 后
要检查弹窗并代点"继续安装"（uiautomator 定位"继续安装"按钮）。
