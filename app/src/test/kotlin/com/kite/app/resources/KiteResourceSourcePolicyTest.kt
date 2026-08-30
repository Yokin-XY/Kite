package com.kite.app.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceSourcePolicyTest {
    @Test
    fun `用户策略统一重排 npm git 与下载候选`() {
        val action = action(
            KiteResourceInstallStep(
                id = "npm",
                type = KiteResourceInstallPlanCompiler.STEP_NPM,
                packages = listOf("example"),
            ),
            KiteResourceInstallStep(
                id = "git",
                type = KiteResourceInstallPlanCompiler.STEP_GIT,
                repositories = listOf(
                    "https://github.com/example/tool.git",
                    "https://gitcode.com/example/tool.git",
                ),
                destination = "\$install_root/tool",
                commit = "5fc308a70719a83cccdbba4c0e39c23f5a8239d5",
            ),
        )

        val domestic = KiteResourceSourcePolicy.apply(
            action,
            KiteResourceSourcePreferences(
                listOf(KiteResourceSourceCatalog.GITCODE, KiteResourceSourceCatalog.NPM_MIRROR),
            ),
        )
        val official = KiteResourceSourcePolicy.apply(
            action,
            KiteResourceSourcePreferences(
                listOf(KiteResourceSourceCatalog.OFFICIAL, KiteResourceSourceCatalog.HUAWEI),
            ),
        )

        assertEquals("https://registry.npmmirror.com", domestic.installSteps[0].registries.first())
        assertEquals("https://registry.npmjs.org", official.installSteps[0].registries.first())
        assertEquals("https://gitcode.com/example/tool.git", domestic.installSteps[1].repositories.first())
        assertEquals("https://github.com/example/tool.git", official.installSteps[1].repositories.first())
    }

    @Test
    fun `用户排序为所有 npm 资源注入候选且不在安装前测速`() {
        val script = KiteResourceInstallPlanCompiler.compile(
            action(
                KiteResourceInstallStep(
                    id = "npm",
                    type = KiteResourceInstallPlanCompiler.STEP_NPM,
                    packages = listOf("example"),
                ),
            ),
            KiteResourceSourcePreferences(),
        )

        assertTrue(script.contains("KITE_RESOURCE_SOURCE_ORDER='huawei,npmmirror,aliyun,tuna,gitcode,official'"))
        assertTrue(script.contains(".kite-source-attempt/npm/\$source_id"))
        assertTrue(!script.contains("time_total"))
        assertTrue(script.contains("registry.npmmirror.com"))
        assertTrue(script.contains("registry.npmjs.org"))
        assertTrue(script.contains("repo.huaweicloud.com/repository/npm"))
        assertTrue(script.contains("KITE_RESOURCE_PYPI_ROUTES"))
        assertTrue(script.contains("KITE_RESOURCE_PYPI_INDEXES"))
    }

    private fun action(vararg steps: KiteResourceInstallStep) = KiteResourceShellAction(
        type = KiteResourceInstallPlanCompiler.ACTION_MANAGED,
        cmd = "",
        surfaceMode = "panel",
        workdir = "/workspace",
        timeoutMs = 1_800_000L,
        managedCommands = emptyList(),
        cleanInstallRoot = true,
        npmUninstallPackages = emptyList(),
        installSteps = steps.toList(),
    )
}
