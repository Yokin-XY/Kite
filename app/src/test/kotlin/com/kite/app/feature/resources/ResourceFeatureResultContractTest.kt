package com.kite.app.feature.resources

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceFeatureResultContractTest {
    @Test
    fun `安装计划打开与取消请求可完整跨过 Fragment Result`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "sender").commitNow()
        val received = mutableListOf<ResourceFeatureRequest>()
        activity.supportFragmentManager.setFragmentResultListener(
            ResourceFeatureResultContract.REQUEST_KEY,
            activity
        ) { _, bundle ->
            ResourceFeatureResultContract.parse(bundle)?.let(received::add)
        }

        ResourceFeatureResultContract.send(
            fragment,
            ResourceFeatureRequest.OpenInstallPlan("target")
        )
        ResourceFeatureResultContract.send(
            fragment,
            ResourceFeatureRequest.CancelInstallPlan(
                targetResourceId = "target",
                resourceIds = listOf("base", "target")
            )
        )

        assertEquals(
            listOf(
                ResourceFeatureRequest.OpenInstallPlan("target"),
                ResourceFeatureRequest.CancelInstallPlan("target", listOf("base", "target"))
            ),
            received
        )
    }
}
