package com.kite.app.foundation.runtime

import com.kite.app.foundation.contracts.ContainerRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * T013c 启动绑定环境身份验收：受管入口解析出确定环境绑定，native 热路径只收到 viewId。
 *
 * 这里只验证控制面的环境解析与优先级；进程漂移隔离由 [proot-view.md] 与 native 夹具覆盖。
 */
@RunWith(RobolectricTestRunner::class)
class ProotViewEnvironmentBindingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun requestedEnvironmentResolvesToThatEnvironmentHeadWithoutFallback() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        // 建立 A 环境头。
        val aChild = env.store.prepare("a-update", environmentId = ENV_A)
        env.store.verify(aChild.viewId)
        env.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        env.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        env.store.releaseLease(aChild.viewId, "owner-a")

        val descriptor = fullCapabilityDescriptor()
        val binding = ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = descriptor,
            requestedEnvironmentId = ENV_A
        )
        assertNotNull(binding)
        assertEquals(aChild.viewId, binding?.viewId)
        assertEquals(ENV_A, binding?.environmentId)
    }

    @Test
    fun explicitViewIdMatchingEnvironmentResolvesSuccessfully() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        val aChild = env.store.prepare("a-update", environmentId = ENV_A)
        env.store.verify(aChild.viewId)
        env.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        env.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        env.store.releaseLease(aChild.viewId, "owner-a")

        // 同时给出 viewId 和 environmentId 时，View 归属环境一致即可解析。
        val descriptor = fullCapabilityDescriptor()
        val resolved = ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = descriptor,
            requestedViewId = aChild.viewId,
            requestedEnvironmentId = ENV_A
        )
        assertEquals(aChild.viewId, resolved?.viewId)
        assertEquals(ENV_A, resolved?.environmentId)
    }

    @Test
    fun explicitViewIdFromOtherEnvironmentIsRejected() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        val aChild = env.store.prepare("a-update", environmentId = ENV_A)
        env.store.verify(aChild.viewId)
        env.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        env.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        env.store.releaseLease(aChild.viewId, "owner-a")

        // A 的 View 不能通过显式 viewId 绑定到 default 或 B；必须 fail-closed。
        val descriptor = fullCapabilityDescriptor()
        assertThrows(IllegalArgumentException::class.java) {
            ProotViewRuntime.resolveActiveBinding(
                container = env.container,
                runtimeDescriptor = descriptor,
                requestedViewId = aChild.viewId,
                requestedEnvironmentId = ProotViewStore.DEFAULT_ENVIRONMENT_ID
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProotViewRuntime.resolveActiveBinding(
                container = env.container,
                runtimeDescriptor = descriptor,
                requestedViewId = aChild.viewId,
                requestedEnvironmentId = ENV_B
            )
        }
    }

    @Test
    fun unknownRequestedEnvironmentFailsClosedInsteadOfFallingBackToDefault() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        val descriptor = fullCapabilityDescriptor()
        assertThrows(IllegalArgumentException::class.java) {
            ProotViewRuntime.resolveActiveBinding(
                container = env.container,
                runtimeDescriptor = descriptor,
                requestedEnvironmentId = "env-missing"
            )
        }
    }

    @Test
    fun fullRuntimeKeepsViewDisabledForOrdinaryLaunch() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        val descriptor = fullCapabilityDescriptor()
        val binding = ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = descriptor
        )
        assertNull(binding)
        assertFalse(env.store.isEnabled())
    }

    @Test
    fun ordinaryLaunchIgnoresPersistedActiveViewEnvironment() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        val aRoot = env.store.prepare("a-root", environmentId = ENV_A)
        env.store.verify(aRoot.viewId)
        env.store.acquireLease(aRoot.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        env.store.commit(aRoot.viewId, "owner-a", environmentId = ENV_A)
        env.store.releaseLease(aRoot.viewId, "owner-a")
        env.store.switchActiveEnvironment(ENV_A)

        val binding = ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = fullCapabilityDescriptor()
        )

        assertNull(binding)
        assertEquals(ENV_A, env.store.activeEnvironmentId())
    }

    @Test
    fun unsupportedRuntimeKeepsCompatibleBoundaryAndReturnsNull() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        // runtime 不具备完整 View 能力时，仍返回 null（兼容边界），不抛异常。
        val descriptor = JSONObject().put("capabilities", JSONArray())
        assertNull(ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = descriptor
        ))
    }

    @Test
    fun ordinaryLaunchDoesNotCreateOrExposeViewBinding() {
        val env = newContainerFixture()
        val descriptor = fullCapabilityDescriptor()
        val binding = ProotViewRuntime.resolveActiveBinding(
            container = env.container,
            runtimeDescriptor = descriptor
        )
        assertNull(binding)
        assertFalse(env.store.isEnabled())
    }

    @Test
    fun disabledViewRejectsExplicitEnvironmentRequestWithDiagnostics() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        // View 未启用但显式请求环境身份：不得静默回退 default，应给出明确诊断。
        val descriptor = fullCapabilityDescriptor()
        assertThrows(IllegalArgumentException::class.java) {
            ProotViewRuntime.resolveActiveBinding(
                container = env.container,
                runtimeDescriptor = descriptor,
                requestedEnvironmentId = ENV_A
            )
        }
    }

    @Test
    fun bindingExposesEnvironmentIdForRunIngress() {
        val env = newContainerFixture()
        env.store.ensureInitialized()
        env.store.enable()
        val aChild = env.store.prepare("a-update", environmentId = ENV_A)
        env.store.verify(aChild.viewId)
        env.store.acquireLease(aChild.viewId, "owner-a", ProotViewLeaseMode.WRITER)
        env.store.commit(aChild.viewId, "owner-a", environmentId = ENV_A)
        env.store.releaseLease(aChild.viewId, "owner-a")

        val binding = env.store.currentBinding(ENV_A)
        val environment = binding.environment()
        assertEquals(ENV_A, environment[ProotViewBinding.ENV_ENVIRONMENT_ID])
        assertEquals(aChild.viewId, environment[ProotViewBinding.ENV_VIEW_ID])
    }

    private fun fullCapabilityDescriptor(): JSONObject = JSONObject().put("capabilities", JSONArray()
        .put(ProotViewStore.RUNTIME_CAPABILITY)
        .put(ProotViewStore.BLOCK_RUNTIME_CAPABILITY))

    private fun newContainerFixture(): ContainerFixture {
        val filesRoot = temporaryFolder.newFolder("binding-files-${System.nanoTime()}")
        val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
        val rootfs = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }
        val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L
        )
        val store = ProotViewStore.forContainer(container)
        return ContainerFixture(container, store)
    }

    private data class ContainerFixture(
        val container: ContainerRecord,
        val store: ProotViewStore
    )

    companion object {
        private const val ENV_A = "env-a"
        private const val ENV_B = "env-b"
    }
}
