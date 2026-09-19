#!/system/bin/sh
# Kite 冻结豁免守护（OnePlus OFreezer 对抗）
# 原理：OnePlus 的 OplusHansManager 在熄屏时把应用整个 uid 组写进
#       /sys/fs/cgroup/uid_*/cgroup.freeze=1（cgroup v2 冻结，进程存活但停跑）。
#       前台服务/WakeLock/电池白名单均不豁免（实测）。
# 本脚本以 root 常驻，检测到冻结立即写回 0。放 /data/adb/service.d/ 开机自启。
# Kite 调试机专用；正式用户方案是引导系统设置手动豁免。

MODDIR=${0%/*}

# SELinux：给 magisk 域补 sysfs 写权限（重启失效，本脚本每次开机重打）
magiskpolicy --live 'allow magisk sysfs file { open write }' >/dev/null 2>&1

LOG=/data/local/tmp/kite-unfreeze.log
KITE_PKG=com.kite.app

uid_of_kite() {
    # /data/data/<pkg> 的属主 uid；未安装时输出空
    ls -ln /data/data/ 2>/dev/null | awk -v p="$KITE_PKG" '$NF==p {print $2; exit}'
}

echo "$(date '+%m-%d %H:%M:%S') guard started" >> "$LOG"

while true; do
    UID_K=$(uid_of_kite)
    if [ -n "$UID_K" ]; then
        F=/sys/fs/cgroup/uid_$UID_K/cgroup.freeze
        if [ -e "$F" ]; then
            V=$(cat "$F" 2>/dev/null)
            if [ "$V" = "1" ]; then
                echo 0 > "$F" 2>/dev/null \
                    && echo "$(date '+%m-%d %H:%M:%S') unfroze uid=$UID_K" >> "$LOG"
            fi
        fi
    fi
    sleep 1
done
