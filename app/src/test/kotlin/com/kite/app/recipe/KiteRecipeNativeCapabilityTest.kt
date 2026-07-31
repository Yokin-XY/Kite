package com.kite.app.recipe

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteRecipeNativeCapabilityTest {
    @Test
    fun `native capability action and string parameters survive recipe round trip`() {
        val source = JSONObject(
            """
                {
                  "base": {"id": "native-download", "name": "Native Download"},
                  "recipe": [{
                    "id": "download",
                    "type": "native_capability",
                    "action": "network.download_sha256",
                    "params": {
                      "url": "https://example.test/file",
                      "destination": "/workspace/file",
                      "maxBytes": "1024"
                    }
                  }]
                }
            """.trimIndent()
        )

        val recipe = KiteRecipe.fromJson(source, KiteRecipe.SOURCE_USER)
        val step = recipe.steps.single()
        assertEquals(KiteRecipe.STEP_NATIVE_CAPABILITY, step.type)
        assertEquals("network.download_sha256", step.action)
        assertEquals("1024", step.params?.getString("maxBytes"))

        val restored = KiteRecipe.fromJson(recipe.toJson(includeLocalIdentity = true), KiteRecipe.SOURCE_USER)
        assertEquals(step.action, restored.steps.single().action)
        assertEquals(step.params.toString(), restored.steps.single().params.toString())
    }
}
