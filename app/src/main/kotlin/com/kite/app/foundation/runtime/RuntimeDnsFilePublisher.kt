package com.kite.app.foundation.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * 发布所有运行通道共享的 Android 默认网络 DNS。
 *
 * PRoot 通过 bind mount 读取该文件，宿主 Node/glibc 通道通过已打开的 fd 读取该文件。
 * 因此更新时必须保留已有文件 inode，不能用 rename/atomic move 替换目标文件。
 */
internal object RuntimeDnsFilePublisher {
    fun sharedResolverFile(context: Context): File =
        File(AssetExtractor.getRuntimeLayout(context.applicationContext).tmpDir, "resolv.conf")

    fun resolveFromAndroidDefaultNetwork(
        context: Context,
        preferredNetwork: Network? = null,
    ): List<String> {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return emptyList()
        val network = preferredNetwork ?: connectivityManager.activeNetwork ?: return emptyList()
        return connectivityManager.getLinkProperties(network)
            ?.dnsServers
            ?.mapNotNull { it.hostAddress?.trim() }
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
    }

    /**
     * 原地截断并重写目标文件，使已经由宿主启动器打开的 fd 继续看到最新内容。
     */
    @Synchronized
    fun publish(target: File, dnsServers: List<String>): Boolean {
        val parent = target.parentFile ?: error("runtime resolv.conf has no parent")
        check(parent.mkdirs() || parent.isDirectory) { "cannot create runtime DNS directory" }

        val path = target.toPath()
        if (Files.isSymbolicLink(path) || (target.exists() && !target.isFile)) {
            Files.deleteIfExists(path)
        }

        val content = ContainerDnsPolicy.renderResolvConf(dnsServers)
        if (target.isFile && runCatching { target.readText() == content }.getOrDefault(false)) {
            return false
        }
        FileOutputStream(target, false).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        return true
    }
}
