package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.ManagedCommandVerificationBasis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap

/** 一次成功探测所绑定的完整事实身份；任一字段变化都必须重新探测。 */
internal data class ResourceManagedCommandEvidenceIdentity(
    val environmentId: String,
    val resourceId: String,
    val commands: List<String>,
    val installedVersion: String,
    val installedAtMs: Long,
    val installRunId: String,
    val verificationBasis: ManagedCommandVerificationBasis,
)

internal data class ResourceManagedCommandEvidenceRequest(
    val requirement: ResourceManagedCommandRequirement,
    val identity: ResourceManagedCommandEvidenceIdentity?,
)

internal fun buildResourceManagedCommandEvidenceIdentity(
    environmentId: String,
    requirement: ResourceManagedCommandRequirement,
    installedVersion: String,
    installedAtMs: Long,
    installRunId: String,
    isInstalled: Boolean,
    verificationBasis: ManagedCommandVerificationBasis?,
): ResourceManagedCommandEvidenceIdentity? {
    if (!isInstalled || verificationBasis == null) return null
    val commandFilesByName = verificationBasis.commandFiles.associateBy { commandFile -> commandFile.command }
    val commandFiles = requirement.commands.mapNotNull(commandFilesByName::get)
    if (commandFiles.size != requirement.commands.size) return null
    return ResourceManagedCommandEvidenceIdentity(
        environmentId = environmentId,
        resourceId = requirement.resourceId,
        commands = requirement.commands,
        installedVersion = installedVersion,
        installedAtMs = installedAtMs,
        installRunId = installRunId,
        verificationBasis = verificationBasis.copy(commandFiles = commandFiles),
    )
}

/**
 * 受管命令正向证明协调器。
 *
 * - 只缓存完整成功证明，不缓存缺失或探测错误；
 * - 同一进程内容量有界；
 * - 首次探测在 Mutex 中 single-flight，并发调用等待后会重新命中正向证明。
 */
internal class ResourceManagedCommandEvidenceCoordinator(
    private val maxEntries: Int = 128,
) {
    private val reconcileMutex = Mutex()
    private val positiveEvidence = LinkedHashMap<ResourceManagedCommandEvidenceIdentity, Unit>(16, 0.75f, true)

    suspend fun missingResourceIds(
        requests: Collection<ResourceManagedCommandEvidenceRequest>,
        probe: suspend (Collection<ResourceManagedCommandRequirement>) -> Result<Set<String>>,
    ): Result<Set<String>> = reconcileMutex.withLock {
        val normalized = requests
            .filter { request -> request.requirement.resourceId.isNotBlank() && request.requirement.commands.isNotEmpty() }
            .distinctBy { request -> request.requirement.resourceId }
        val pending = synchronized(positiveEvidence) {
            normalized.filterNot { request ->
                request.identity?.let(positiveEvidence::containsKey) == true
            }
        }
        if (pending.isEmpty()) return@withLock Result.success(emptySet())

        probe(pending.map(ResourceManagedCommandEvidenceRequest::requirement)).map { missing ->
            val missingIds = missing.toSet()
            synchronized(positiveEvidence) {
                if (missingIds.isNotEmpty()) {
                    positiveEvidence.keys.removeAll { identity -> identity.resourceId in missingIds }
                }
                pending.forEach { request ->
                    val identity = request.identity ?: return@forEach
                    if (identity.resourceId !in missingIds) {
                        positiveEvidence.keys.removeAll { previous ->
                            previous.environmentId == identity.environmentId &&
                                previous.resourceId == identity.resourceId
                        }
                        positiveEvidence[identity] = Unit
                    }
                }
                trimToCapacityLocked()
            }
            missingIds
        }
    }

    internal fun positiveEvidenceCount(): Int = synchronized(positiveEvidence) {
        positiveEvidence.size
    }

    private fun trimToCapacityLocked() {
        val capacity = maxEntries.coerceAtLeast(1)
        while (positiveEvidence.size > capacity) {
            val eldest = positiveEvidence.entries.firstOrNull()?.key ?: break
            positiveEvidence.remove(eldest)
        }
    }
}
