package com.kite.app.foundation.contracts

/** Ubuntu Ports 的技术端点只有这一处事实源；资源安装和新 rootfs 默认配置共同复用。 */
object UbuntuPortsSourceCatalog {
    const val HUAWEI = "https://repo.huaweicloud.com/ubuntu-ports/"
    const val ALIYUN = "https://mirrors.aliyun.com/ubuntu-ports/"
    const val TUNA = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/"
    const val OFFICIAL = "https://ports.ubuntu.com/ubuntu-ports/"

    fun sourcesList(baseUrl: String, codename: String): String {
        val base = baseUrl.trimEnd('/') + "/"
        return listOf("", "-updates", "-backports", "-security")
            .joinToString("\n") { pocket ->
                "deb $base $codename$pocket main restricted universe multiverse"
            }
    }
}
