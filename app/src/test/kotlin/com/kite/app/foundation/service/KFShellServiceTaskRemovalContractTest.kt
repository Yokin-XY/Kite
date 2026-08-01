package com.kite.app.foundation.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KFShellServiceTaskRemovalContractTest {
    private val service = File(
        "src/main/kotlin/com/kite/app/foundation/service/KFShellService.kt"
    ).readText()
    private val initializer = File(
        "src/main/java/com/kite/app/KiteTaskContractInitializer.kt"
    ).readText()

    @Test
    fun `foundation 只转发任务移除而不读取业务运行事实`() {
        assertTrue(service.contains("KiteTaskContractHost.get().onTaskRemoved(applicationContext, rootIntent)"))
        assertFalse(service.contains("CardRunStore"))
        assertFalse(service.contains("RunOrchestrator"))
        assertFalse(service.contains("KiteResourceInstallStore"))
    }

    @Test
    fun `主任务移除时每个文档任务先转发身份再关闭窗口`() {
        val forward = service.indexOf("forwardTaskRemoval(baseIntent)")
        val finish = service.indexOf("task.finishAndRemoveTask()", startIndex = forward)

        assertTrue(forward >= 0)
        assertTrue(finish > forward)
    }

    @Test
    fun `业务注入层只接受经过 URI 与 extra 双重校验的代次`() {
        assertTrue(initializer.contains("CardRunIntents.taskIdentity(rootIntent) ?: return"))
        assertTrue(initializer.contains("expectedGeneration = identity.generation"))
        assertTrue(initializer.contains("runInstanceCloseCoordinator.request"))
    }
}
