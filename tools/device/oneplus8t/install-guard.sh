#!/system/bin/sh
# 把 Kite 冻结豁免守护安装到 Magisk service.d（root、开机自启）
MODDIR=${0%/*}

# magisk 域需要 SELinux 规则：允许写 /data/adb（adb_data_file）
magiskpolicy --live 'allow magisk adb_data_file dir { write add_name search }' >/dev/null 2>&1
magiskpolicy --live 'allow magisk adb_data_file file { create write open setattr }' >/dev/null 2>&1

cat /data/local/tmp/kite-unfreeze.sh > /data/adb/service.d/kite-unfreeze.sh 2>/dev/null
if [ -f /data/adb/service.d/kite-unfreeze.sh ]; then
    chmod 755 /data/adb/service.d/kite-unfreeze.sh
    echo "INSTALLED_OK"
    ls -la /data/adb/service.d/ 2>&1 | grep kite
else
    echo "INSTALL_FAIL — magisk 域仍无权写 /data/adb"
fi
