package com.kftest.app.foundation.runtime

/**
 * 底座版本定义。
 *
 * 每个枚举值对应一个 Ubuntu LTS 发行版的完整配置：
 * 资源路径、镜像标识、apt 源、目录名。
 *
 * 当前主线：NOBLE（24.04）
 * 候选底座：JAMMY（22.04）— 兼容保留
 */
enum class BaseImageProfile(
    val label: String,
    val versionId: String,
    val codename: String,
    val assetTarGz: String,
    val assetTar: String,
    val imageDirName: String,
    val imageName: String,
    val aptSources: String
) {
    JAMMY(
        label = "Ubuntu 22.04 LTS (Jammy)",
        versionId = "22.04",
        codename = "jammy",
        assetTarGz = "rootfs/ubuntu-base-22.04-arm64.tar.gz",
        assetTar = "rootfs/ubuntu-base-22.04-arm64.tar",
        imageDirName = "ubuntu-base",
        imageName = "ubuntu-base-22.04-arm64",
        aptSources = """
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ jammy main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ jammy-updates main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ jammy-backports main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ jammy-security main restricted universe multiverse
""".trimIndent()
    ),
    NOBLE(
        label = "Ubuntu 24.04 LTS (Noble)",
        versionId = "24.04-kite-offline-20260627",
        codename = "noble",
        assetTarGz = "rootfs/ubuntu-base-24.04-arm64.tar.gz",
        assetTar = "rootfs/ubuntu-base-24.04-arm64.tar",
        imageDirName = "ubuntu-noble",
        imageName = "ubuntu-base-24.04-arm64",
        aptSources = """
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-backports main restricted universe multiverse
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main restricted universe multiverse
""".trimIndent()
    );

    val rootfsAssetCandidates: List<String>
        get() = listOf(assetTarGz, assetTar)

    companion object {
        val DEFAULT = NOBLE

        fun fromImageName(imageName: String): BaseImageProfile =
            entries.firstOrNull { it.imageName == imageName } ?: NOBLE

        fun fromCodename(codename: String): BaseImageProfile =
            entries.firstOrNull { it.codename.equals(codename, ignoreCase = true) } ?: NOBLE
    }
}
