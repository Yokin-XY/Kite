package com.kite.app.feature.runtimemanagement

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeManagementResultContractTest {
    @Test
    fun `back and open surface cross fragment result without losing identity`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val fragment = Fragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "sender").commitNow()
        val received = mutableListOf<RuntimeManagementRequest>()
        activity.supportFragmentManager.setFragmentResultListener(
            RuntimeManagementResultContract.REQUEST_KEY,
            activity
        ) { _, bundle -> RuntimeManagementResultContract.parse(bundle)?.let(received::add) }

        RuntimeManagementResultContract.send(fragment, RuntimeManagementRequest.Back)
        RuntimeManagementResultContract.send(
            fragment,
            RuntimeManagementRequest.OpenSurface("recipe-1", "run-1", CardRunSurface.Terminal)
        )

        assertEquals(
            listOf(
                RuntimeManagementRequest.Back,
                RuntimeManagementRequest.OpenSurface("recipe-1", "run-1", CardRunSurface.Terminal)
            ),
            received
        )
    }
}
