package com.kite.app.foundation.runtime

import com.kite.app.foundation.fileprotection.KiteFileProtectionProtocol
import java.io.File

/** Android 控制面与 PRoot 文件保护数据面共享的稳定入口。 */
internal object ProotFileProtectionRuntime {
    // 环境变量和文件名属于已发布 native ABI；通用协议升级不改变启动入口。
    const val CONTROL_ENV = "KF_PROOT_TXN_CONTROL_PATH"
    const val OPERATION_ENV = "KF_FILE_PROTECTION_OPERATION_ID"
    private const val CONTROL_FILE_NAME = "kf-resource-transaction.active"

    fun controlFile(layout: AssetExtractor.RuntimeLayout): File = File(layout.tmpDir, CONTROL_FILE_NAME)

    fun activeControlFile(layout: AssetExtractor.RuntimeLayout): File? =
        controlFile(layout).takeIf(File::isFile)

    fun activeEnvironment(layout: AssetExtractor.RuntimeLayout): Map<String, String> {
        val controlFile = activeControlFile(layout) ?: return emptyMap()
        val control = runCatching {
            KiteFileProtectionProtocol.decodeControl(controlFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return mapOf(CONTROL_ENV to controlFile.absolutePath)
        return mapOf(
            CONTROL_ENV to controlFile.absolutePath,
            OPERATION_ENV to control.operationId
        )
    }
}
