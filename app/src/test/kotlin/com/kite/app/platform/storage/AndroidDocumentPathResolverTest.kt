package com.kite.app.platform.storage

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.runtime.KFContainerManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidDocumentPathResolverTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workspaceRoot: File

    @Before
    fun setUp() {
        workspaceRoot = KFContainerManager.resolveWorkspaceDirectory(context)
        workspaceRoot.deleteRecursively()
        workspaceRoot.resolve("Demo/note.txt").apply {
            parentFile?.mkdirs()
            writeText("hello")
        }
    }

    @After
    fun tearDown() {
        workspaceRoot.deleteRecursively()
    }

    @Test
    fun `Kite Ubuntu document keeps workspace path`() {
        val uri = DocumentsContract.buildDocumentUri(
            KiteWorkspaceDocumentsProvider.authority(context),
            "workspace:Demo/note.txt",
        )

        assertEquals(
            "/workspace/Demo/note.txt",
            AndroidDocumentPathResolver.resolveAgentVisiblePath(context, uri),
        )
    }

    @Test
    fun `workspace root is not sent as file attachment`() {
        val uri = DocumentsContract.buildDocumentUri(
            KiteWorkspaceDocumentsProvider.authority(context),
            KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID,
        )
        assertNull(AndroidDocumentPathResolver.resolveAgentVisiblePath(context, uri))
    }

    @Test
    fun `system tree selection keeps the real Ubuntu project directory`() {
        val uri = DocumentsContract.buildTreeDocumentUri(
            KiteWorkspaceDocumentsProvider.authority(context),
            "workspace:Demo",
        )

        assertEquals(
            "/workspace/Demo",
            AndroidDocumentPathResolver.resolveWorkspaceDirectory(context, uri),
        )
    }

    @Test
    fun `workspace root cannot be registered as a named project`() {
        val uri = DocumentsContract.buildTreeDocumentUri(
            KiteWorkspaceDocumentsProvider.authority(context),
            KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID,
        )

        assertNull(AndroidDocumentPathResolver.resolveWorkspaceDirectory(context, uri))
    }
}
