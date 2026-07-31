package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.ManagedCommandHostFileStamp
import com.kite.app.foundation.runtime.ManagedCommandVerificationBasis
import com.kite.app.foundation.runtime.RuntimeLaunchPreparationIdentity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceManagedCommandEvidenceCoordinatorTest {
    @Test
    fun `同一完整身份只执行一次真实探测`() = runTest {
        val coordinator = ResourceManagedCommandEvidenceCoordinator()
        val probes = AtomicInteger(0)
        val request = request(identity())

        repeat(2) {
            val result = coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                Result.success(emptySet())
            }
            assertEquals(emptySet<String>(), result.getOrThrow())
        }

        assertEquals(1, probes.get())
        assertEquals(1, coordinator.positiveEvidenceCount())
    }

    @Test
    fun `安装运行时命令或环境身份变化都会重新探测`() = runTest {
        val coordinator = ResourceManagedCommandEvidenceCoordinator()
        val probes = AtomicInteger(0)
        val identities = listOf(
            identity(),
            identity(installedAtMs = 24L),
            identity(installedVersion = "1.0.1"),
            identity(installRunId = "install-run-2"),
            identity(runtimeDescriptorStamp = 24L),
            identity(containerCreatedAtMs = 24L),
            identity(rootfsPath = "/runtime/containers/ubuntu-next/rootfs"),
            identity(commandLength = 24L),
            identity(linkChain = listOf("openclaw->openclaw.mjs")),
            identity(environmentId = "profile-2"),
            identity(resourceId = "other-resource"),
            identity(commands = listOf("other-command")),
        )

        identities.forEach { changedIdentity ->
            coordinator.missingResourceIds(listOf(request(changedIdentity))) {
                probes.incrementAndGet()
                Result.success(emptySet())
            }.getOrThrow()
        }

        assertEquals(identities.size, probes.get())
    }

    @Test
    fun `正向证明容量有界且淘汰后重新探测`() = runTest {
        val coordinator = ResourceManagedCommandEvidenceCoordinator(maxEntries = 1)
        val probes = AtomicInteger(0)
        val first = request(identity(resourceId = "first"))
        val second = request(identity(resourceId = "second"))

        listOf(first, second, first).forEach { request ->
            coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                Result.success(emptySet())
            }.getOrThrow()
        }

        assertEquals(3, probes.get())
        assertEquals(1, coordinator.positiveEvidenceCount())
    }

    @Test
    fun `缺失结论和探测错误都不会进入正向缓存`() = runTest {
        val coordinator = ResourceManagedCommandEvidenceCoordinator()
        val request = request(identity())
        val probes = AtomicInteger(0)

        repeat(2) {
            val missing = coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                Result.success(setOf("openclaw"))
            }.getOrThrow()
            assertEquals(setOf("openclaw"), missing)
        }
        repeat(2) {
            val failed = coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                Result.failure(IllegalStateException("probe_failed"))
            }
            assertTrue(failed.isFailure)
        }

        assertEquals(4, probes.get())
        assertEquals(0, coordinator.positiveEvidenceCount())
    }

    @Test
    fun `命令宿主文件身份不完整时不能建立正向证明`() {
        val complete = identity()
        val requirement = ResourceManagedCommandRequirement("openclaw", listOf("openclaw", "openclaw-helper"))

        val evidence = buildResourceManagedCommandEvidenceIdentity(
            environmentId = complete.environmentId,
            requirement = requirement,
            installedVersion = complete.installedVersion,
            installedAtMs = complete.installedAtMs,
            installRunId = complete.installRunId,
            isInstalled = true,
            verificationBasis = complete.verificationBasis,
        )

        assertEquals(null, evidence)
    }

    @Test
    fun `并发首次请求共享同一次探测`() = runTest {
        val coordinator = ResourceManagedCommandEvidenceCoordinator()
        val request = request(identity())
        val probes = AtomicInteger(0)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                entered.complete(Unit)
                release.await()
                Result.success(emptySet())
            }
        }
        entered.await()
        val second = async {
            coordinator.missingResourceIds(listOf(request)) {
                probes.incrementAndGet()
                Result.success(emptySet())
            }
        }
        release.complete(Unit)

        assertEquals(emptySet<String>(), first.await().getOrThrow())
        assertEquals(emptySet<String>(), second.await().getOrThrow())
        assertEquals(1, probes.get())
    }

    private fun request(identity: ResourceManagedCommandEvidenceIdentity) =
        ResourceManagedCommandEvidenceRequest(
            requirement = ResourceManagedCommandRequirement(identity.resourceId, identity.commands),
            identity = identity,
        )

    private fun identity(
        environmentId: String = "default",
        resourceId: String = "openclaw",
        commands: List<String> = listOf("openclaw"),
        installedVersion: String = "1.0.0",
        installedAtMs: Long = 23L,
        installRunId: String = "install-run",
        runtimeDescriptorStamp: Long = 23L,
        containerCreatedAtMs: Long = 23L,
        rootfsPath: String = "/runtime/containers/ubuntu-main/rootfs",
        commandLength: Long = 23L,
        linkChain: List<String> = emptyList(),
    ) = ResourceManagedCommandEvidenceIdentity(
        environmentId = environmentId,
        resourceId = resourceId,
        commands = commands,
        installedVersion = installedVersion,
        installedAtMs = installedAtMs,
        installRunId = installRunId,
        verificationBasis = ManagedCommandVerificationBasis(
            runtimeIdentity = RuntimeLaunchPreparationIdentity(
                runtimeRootPath = "/runtime",
                runtimeDescriptorStamp = runtimeDescriptorStamp,
                containerId = "ubuntu-main",
                containerCreatedAtMs = containerCreatedAtMs,
                rootfsPath = rootfsPath,
                workspacePath = "/runtime/shared/default",
                networkMode = "HOST",
            ),
            commandFiles = commands.map { command ->
                ManagedCommandHostFileStamp(
                    command = command,
                    hostPath = "/runtime/shared/default/.kf/bin/$command",
                    canonicalPath = "/runtime/shared/default/.kf/bin/$command",
                    linkChain = linkChain,
                    lastModifiedMs = 23L,
                    length = commandLength,
                )
            },
        ),
    )
}
