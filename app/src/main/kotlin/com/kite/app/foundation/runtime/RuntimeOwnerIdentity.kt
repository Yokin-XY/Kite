package com.kite.app.foundation.runtime

import java.security.MessageDigest

/**
 * PRoot 运行身份。root 表示一次实例代次，owner 表示该代次中的一个可独立回收叶子。
 */
internal data class RuntimeOwnerHandle(
    val rootOwnerId: String,
    val ownerId: String,
    val unitId: String
) {
    fun environment(): Map<String, String> = mapOf(
        RuntimeOwnerIdentity.RUNTIME_ID_ENV to ownerId,
        RuntimeOwnerIdentity.UNIT_ID_ENV to unitId
    )
}

internal enum class RuntimeOwnerNamespace(val wireName: String) {
    Card("card"),
    Resource("resource")
}

internal object RuntimeOwnerIdentity {
    const val RUNTIME_ID_ENV = "KF_RUNTIME_ID"
    const val UNIT_ID_ENV = "KF_UNIT_ID"

    /** 后台运行项沿用登记表 id，确保遥测、运行管理和停止入口指向同一个事实身份。 */
    fun backgroundRuntime(runtimeId: String, kind: String): RuntimeOwnerHandle {
        val ownerId = runtimeId.trim().ifBlank { "background-runtime" }
        val kindToken = stableToken(kind.lowercase().replace('_', '-'))
        return RuntimeOwnerHandle(
            rootOwnerId = ownerId,
            ownerId = ownerId,
            unitId = "background:$kindToken:${stableToken(ownerId)}"
        )
    }

    fun isBackgroundRuntime(ownerId: String, unitId: String): Boolean =
        unitId.startsWith("background:") || ownerId.startsWith("background-")

    fun step(
        namespace: RuntimeOwnerNamespace,
        instanceId: String,
        generation: Long,
        stepIndex: Int,
        stepId: String,
        attemptId: Long
    ): RuntimeOwnerHandle {
        val root = root(namespace, instanceId, generation)
        val stepToken = stableToken(stepId.ifBlank { "step-$stepIndex" })
        return RuntimeOwnerHandle(
            rootOwnerId = root,
            ownerId = "$root/step/$stepIndex-$stepToken/attempt/$attemptId",
            unitId = "step:$stepIndex:$stepToken:attempt:$attemptId"
        )
    }

    fun terminal(
        rootNamespace: RuntimeOwnerNamespace,
        instanceId: String,
        generation: Long,
        terminalSessionId: String,
        stepIndex: Int? = null,
        stepId: String? = null,
        attemptId: Long? = null
    ): RuntimeOwnerHandle {
        val root = root(rootNamespace, instanceId, generation)
        val sessionToken = stableToken(terminalSessionId)
        val instanceToken = stableToken(instanceId)
        val leaf = buildString {
            append("terminal:").append(sessionToken)
            append("/instance/").append(instanceToken).append('@').append(generation)
            if (stepIndex != null) {
                append("/step/").append(stepIndex).append('-')
                    .append(stableToken(stepId.orEmpty().ifBlank { "step-$stepIndex" }))
            } else {
                append("/manual")
            }
            attemptId?.let { append("/attempt/").append(it) }
        }
        val unit = if (stepIndex == null) {
            "terminal:$sessionToken:manual"
        } else {
            "terminal:$sessionToken:step:$stepIndex:attempt:${attemptId ?: 0L}"
        }
        return RuntimeOwnerHandle(rootOwnerId = root, ownerId = leaf, unitId = unit)
    }

    fun operation(
        namespace: RuntimeOwnerNamespace,
        instanceId: String,
        generation: Long,
        operationId: String
    ): RuntimeOwnerHandle {
        val root = root(namespace, instanceId, generation)
        val operationToken = stableToken(operationId)
        return RuntimeOwnerHandle(
            rootOwnerId = root,
            ownerId = "$root/operation/$operationToken",
            unitId = "operation:$operationToken"
        )
    }

    fun root(
        namespace: RuntimeOwnerNamespace,
        instanceId: String,
        generation: Long
    ): String = "${namespace.wireName}:${stableToken(instanceId)}@$generation"

    fun terminalSessionId(ownerId: String): String? = ownerId
        .takeIf { it.startsWith("terminal:") }
        ?.substringAfter(':')
        ?.substringBefore('/')
        ?.takeIf { it.isNotBlank() }

    fun generation(ownerId: String): Long? = GENERATION_PATTERN
        .findAll(ownerId)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    /** root owner 是一次实例代次的作用域，不是会直接写入 PRoot 的进程 owner。 */
    fun isRoot(ownerId: String): Boolean =
        (ownerId.startsWith("card:") || ownerId.startsWith("resource:")) &&
            generation(ownerId) != null &&
            !ownerId.substringAfter('@', missingDelimiterValue = "").contains('/')

    /**
     * 终端真正启动前会先生成步骤 owner；终端会话 owner 随后接管同一步骤、同一次尝试。
     * 两者尾部身份完全相同时，前者只是协议占位，不代表另一棵进程树。
     */
    fun supersedes(actualOwnerId: String, provisionalOwnerId: String): Boolean {
        if (!actualOwnerId.startsWith("terminal:")) return false
        if (!provisionalOwnerId.startsWith("card:") && !provisionalOwnerId.startsWith("resource:")) {
            return false
        }
        val actualTail = actualOwnerId.substringAfter("/instance/", missingDelimiterValue = "")
        val provisionalTail = provisionalOwnerId.substringAfter(':', missingDelimiterValue = "")
        return actualTail.isNotBlank() && actualTail == provisionalTail
    }

    /** 判断叶子 owner 是否属于指定实例代次作用域。 */
    fun belongsToRoot(ownerId: String, rootOwnerId: String): Boolean {
        if (!isRoot(rootOwnerId) || ownerId == rootOwnerId) return false
        if (ownerId.startsWith("$rootOwnerId/")) return true
        val rootTail = rootOwnerId.substringAfter(':', missingDelimiterValue = "")
        val terminalTail = ownerId
            .takeIf { it.startsWith("terminal:") }
            ?.substringAfter("/instance/", missingDelimiterValue = "")
            .orEmpty()
        return terminalTail.isNotBlank() &&
            (terminalTail == rootTail || terminalTail.startsWith("$rootTail/"))
    }

    private fun stableToken(raw: String): String {
        val value = raw.trim().ifBlank { "unknown" }
        if (value.length <= MAX_TOKEN_CHARS && value.all(::isWireSafe)) return value
        val readable = value
            .map { if (isWireSafe(it)) it else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "id" }
            .take(READABLE_TOKEN_CHARS)
        return "$readable-${value.sha256Prefix()}"
    }

    private fun isWireSafe(char: Char): Boolean =
        char.isLetterOrDigit() || char == '.' || char == '_' || char == '-'

    private fun String.sha256Prefix(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(HASH_BYTES)
        .joinToString("") { byte -> "%02x".format(byte) }

    private const val MAX_TOKEN_CHARS = 72
    private const val READABLE_TOKEN_CHARS = 48
    private const val HASH_BYTES = 6
    private val GENERATION_PATTERN = Regex("@(\\d+)(?=/|$)")
}
