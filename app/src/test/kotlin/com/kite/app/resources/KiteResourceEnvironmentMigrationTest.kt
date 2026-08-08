package com.kite.app.resources

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class KiteResourceEnvironmentMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `旧全局资源登记升级后只属于default环境`() {
        context.deleteDatabase(REGISTRY_DATABASE)
        val db = openDatabase(REGISTRY_DATABASE)
        db.execSQL(
            """
            CREATE TABLE resource_registry (
                resource_id TEXT PRIMARY KEY NOT NULL,
                status TEXT NOT NULL,
                operation TEXT NOT NULL DEFAULT '',
                version TEXT NOT NULL DEFAULT '',
                latest_version TEXT NOT NULL DEFAULT '',
                update_status TEXT NOT NULL DEFAULT '',
                last_checked_at INTEGER NOT NULL DEFAULT 0,
                run_id TEXT NOT NULL DEFAULT '',
                summary TEXT NOT NULL DEFAULT '',
                installed_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.insert(
            "resource_registry",
            null,
            ContentValues().apply {
                put("resource_id", RESOURCE_ID)
                put("status", KiteResourceRegistry.STATUS_INSTALLED)
                put("operation", KiteResourceRegistry.OP_INSTALL)
                put("version", "1.2.3")
                put("updated_at", 1L)
            }
        )
        db.version = 3
        db.close()

        val registry = KiteResourceRegistry(context)

        assertEquals("1.2.3", registry.entry(RESOURCE_ID, "default")?.version)
        assertNull(registry.entry(RESOURCE_ID, "profile-2"))
    }

    @Test
    fun `旧全局资源快照升级后只属于default环境`() {
        context.deleteDatabase(SNAPSHOT_DATABASE)
        val db = openDatabase(SNAPSHOT_DATABASE)
        db.execSQL(
            """
            CREATE TABLE installed_resource_snapshot (
                resource_id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                icon_json TEXT NOT NULL DEFAULT '',
                version TEXT NOT NULL DEFAULT '',
                manifest_json TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.insert(
            "installed_resource_snapshot",
            null,
            ContentValues().apply {
                put("resource_id", RESOURCE_ID)
                put("name", "Example")
                put("version", "1.2.3")
                put("manifest_json", "{\"id\":\"$RESOURCE_ID\"}")
                put("created_at", 1L)
                put("updated_at", 1L)
            }
        )
        db.version = 1
        db.close()

        val snapshots = KiteInstalledResourceSnapshotStore(context)

        assertEquals("{\"id\":\"$RESOURCE_ID\"}", snapshots.manifestJson(RESOURCE_ID, "default"))
        assertNull(snapshots.manifestJson(RESOURCE_ID, "profile-2"))
    }

    private fun openDatabase(name: String): SQLiteDatabase {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null)
    }

    companion object {
        private const val REGISTRY_DATABASE = "kite_system_registry.db"
        private const val SNAPSHOT_DATABASE = "kite_installed_resource_snapshot.db"
        private const val RESOURCE_ID = "kite.migration.example"
    }
}
