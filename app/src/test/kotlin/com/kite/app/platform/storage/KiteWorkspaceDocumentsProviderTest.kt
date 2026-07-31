package com.kite.app.platform.storage

import android.provider.DocumentsContract
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.kite.app.foundation.runtime.KFContainerManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class KiteWorkspaceDocumentsProviderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var provider: KiteWorkspaceDocumentsProvider

    @Before
    fun setUp() {
        KFContainerManager.resolveWorkspaceDirectory(context).deleteRecursively()
        val providerInfo = ProviderInfo().apply {
            authority = KiteWorkspaceDocumentsProvider.authority(context)
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        }
        provider = Robolectric.buildContentProvider(KiteWorkspaceDocumentsProvider::class.java)
            .create(providerInfo)
            .get()
    }

    @After
    fun tearDown() {
        KFContainerManager.resolveWorkspaceDirectory(context).deleteRecursively()
    }

    @Test
    fun `provider exposes one writable Kite Ubuntu root`() {
        provider.queryRoots(null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                KiteWorkspaceDocumentsProvider.ROOT_TITLE,
                cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_TITLE))
            )
            val flags = cursor.getInt(cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_FLAGS))
            assertTrue(flags and DocumentsContract.Root.FLAG_SUPPORTS_CREATE != 0)
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun `project picker starts from the Kite Ubuntu root`() {
        assertEquals(
            "content://${KiteWorkspaceDocumentsProvider.authority(context)}/root/${KiteWorkspaceDocumentsProvider.ROOT_ID}",
            KiteWorkspaceDocumentsProvider.pickerRootUri(context).toString()
        )
    }

    @Test
    fun `android document operations change the real workspace tree`() {
        val rootId = KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID
        val projectId = provider.createDocument(
            rootId,
            DocumentsContract.Document.MIME_TYPE_DIR,
            "Demo"
        )
        val noteId = provider.createDocument(projectId, "text/plain", "note.txt")

        val root = KFContainerManager.resolveWorkspaceDirectory(context)
        assertTrue(File(root, "Demo/note.txt").isFile)

        val renamedId = provider.renameDocument(noteId, "README.md")
        assertTrue(File(root, "Demo/README.md").isFile)

        provider.deleteDocument(renamedId)
        assertFalse(File(root, "Demo/README.md").exists())
    }

    @Test(expected = FileNotFoundException::class)
    fun `guessed document id cannot escape the workspace`() {
        provider.queryDocument("workspace:../../containers/ubuntu-main/rootfs/etc/passwd", null)
    }

    @Test
    fun `runtime control directories are hidden from Android`() {
        val root = KFContainerManager.resolveWorkspaceDirectory(context)
        File(root, ".kf").mkdirs()
        File(root, "VisibleProject").mkdirs()

        provider.queryChildDocuments(
            KiteWorkspaceDocumentsProvider.ROOT_DOCUMENT_ID,
            null,
            null as String?
        ).use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val names = buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
            assertEquals(listOf("VisibleProject"), names)
        }
    }
}
