package com.kite.app.application.resources

import com.kite.app.resources.KiteResourcePlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class FailedResourceRecoveryPolicyTest {

    @Test
    fun `无队列失败项只重新获取自身而队列失败项从原位置续跑`() {
        val standalone = FailedResourceRecoveryPolicy.continuation(
            resourceId = "kite.git",
            plan = KiteResourcePlanSnapshot(),
        )
        val queued = FailedResourceRecoveryPolicy.continuation(
            resourceId = "kite.git",
            plan = KiteResourcePlanSnapshot(
                targetResourceId = "kite.hermes.core",
                resourceIds = listOf("kite.curl", "kite.git", "kite.hermes.core"),
            ),
        )

        assertEquals(ResourceRunContinuation.Reinstall, standalone)
        assertEquals(ResourceRunContinuation.ResumeInstallWizard, queued)
    }
}
