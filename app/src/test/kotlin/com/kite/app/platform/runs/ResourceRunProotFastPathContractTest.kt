package com.kite.app.platform.runs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRunProotFastPathContractTest {

    @Test
    fun `正式卡片和资源运行不再隐式注入 View 环境`() {
        val source = locateKiteAppGraphSource().readText()

        assertTrue(source.contains("RunExecutionEnvironmentProvider.None"))
        assertTrue(source.contains("AndroidResourceVersionGateway(bridgeClient)"))
        assertFalse(source.contains("resourceViewEnvironment(request.previousState.environmentId)"))
        assertFalse(source.contains("environmentFor = ::resourceViewEnvironment"))
        assertFalse(source.contains("private fun resourceViewEnvironment"))
        assertTrue(source.contains("KiteResourceInstallStore(appContext)"))
        assertFalse(source.contains("initialResourceEnvironmentId"))
        assertFalse(source.contains("resourceInstallStore.activateEnvironment"))
    }

    private fun locateKiteAppGraphSource(): File = sequenceOf(
        File("src/main/java/com/kite/app/shell/KiteAppGraph.kt"),
        File("app/src/main/java/com/kite/app/shell/KiteAppGraph.kt"),
    ).first(File::isFile)
}
