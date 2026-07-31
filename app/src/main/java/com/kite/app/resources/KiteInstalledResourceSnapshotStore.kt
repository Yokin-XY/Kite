package com.kite.app.resources

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KiteInstalledResourceSnapshotStore(context: Context) {
    private val database = SnapshotDatabase(context.applicationContext)

    fun save(
        resourceId: String,
        name: String,
        iconJson: String,
        version: String,
        manifestJson: String,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        val now = System.currentTimeMillis()
        database.writableDatabase.insertWithOnConflict(
            TABLE_SNAPSHOT,
            null,
            ContentValues().apply {
                put(COL_ENVIRONMENT_ID, normalizeEnvironmentId(environmentId))
                put(COL_RESOURCE_ID, normalizeResourceId(resourceId))
                put(COL_NAME, name)
                put(COL_ICON_JSON, iconJson)
                put(COL_VERSION, version)
                put(COL_MANIFEST_JSON, manifestJson)
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun manifestJson(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): String? =
        database.readableDatabase.query(
            TABLE_SNAPSHOT,
            arrayOf(COL_MANIFEST_JSON),
            "$COL_ENVIRONMENT_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(normalizeEnvironmentId(environmentId), normalizeResourceId(resourceId)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun clear(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        database.writableDatabase.delete(
            TABLE_SNAPSHOT,
            "$COL_ENVIRONMENT_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(normalizeEnvironmentId(environmentId), normalizeResourceId(resourceId))
        )
    }

    fun clear(resourceIds: Collection<String>, environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        val normalizedIds = resourceIds
            .map(::normalizeResourceId)
            .filter(String::isNotBlank)
            .distinct()
        if (normalizedIds.isEmpty()) return
        val placeholders = normalizedIds.joinToString(",") { "?" }
        val selectionArgs = (listOf(normalizeEnvironmentId(environmentId)) + normalizedIds).toTypedArray()
        database.writableDatabase.delete(
            TABLE_SNAPSHOT,
            "$COL_ENVIRONMENT_ID = ? AND $COL_RESOURCE_ID IN ($placeholders)",
            selectionArgs
        )
    }

    private fun normalizeResourceId(resourceId: String): String =
        resourceId.trim().ifBlank { "resource" }

    private fun normalizeEnvironmentId(environmentId: String): String =
        environmentId.trim().ifBlank { DEFAULT_ENVIRONMENT_ID }

    private class SnapshotDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            createSchema(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL(
                    """
                    CREATE TABLE ${TABLE_SNAPSHOT}_v2 (
                        $COL_ENVIRONMENT_ID TEXT NOT NULL,
                        $COL_RESOURCE_ID TEXT NOT NULL,
                        $COL_NAME TEXT NOT NULL DEFAULT '',
                        $COL_ICON_JSON TEXT NOT NULL DEFAULT '',
                        $COL_VERSION TEXT NOT NULL DEFAULT '',
                        $COL_MANIFEST_JSON TEXT NOT NULL DEFAULT '',
                        $COL_CREATED_AT INTEGER NOT NULL,
                        $COL_UPDATED_AT INTEGER NOT NULL,
                        PRIMARY KEY($COL_ENVIRONMENT_ID, $COL_RESOURCE_ID)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO ${TABLE_SNAPSHOT}_v2 (
                        $COL_ENVIRONMENT_ID, $COL_RESOURCE_ID, $COL_NAME, $COL_ICON_JSON,
                        $COL_VERSION, $COL_MANIFEST_JSON, $COL_CREATED_AT, $COL_UPDATED_AT
                    ) SELECT ?, $COL_RESOURCE_ID, $COL_NAME, $COL_ICON_JSON,
                        $COL_VERSION, $COL_MANIFEST_JSON, $COL_CREATED_AT, $COL_UPDATED_AT
                    FROM $TABLE_SNAPSHOT
                    """.trimIndent(),
                    arrayOf(DEFAULT_ENVIRONMENT_ID)
                )
                db.execSQL("DROP TABLE $TABLE_SNAPSHOT")
                db.execSQL("ALTER TABLE ${TABLE_SNAPSHOT}_v2 RENAME TO $TABLE_SNAPSHOT")
            }
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SNAPSHOT (
                    $COL_ENVIRONMENT_ID TEXT NOT NULL,
                    $COL_RESOURCE_ID TEXT NOT NULL,
                    $COL_NAME TEXT NOT NULL DEFAULT '',
                    $COL_ICON_JSON TEXT NOT NULL DEFAULT '',
                    $COL_VERSION TEXT NOT NULL DEFAULT '',
                    $COL_MANIFEST_JSON TEXT NOT NULL DEFAULT '',
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_ENVIRONMENT_ID, $COL_RESOURCE_ID)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_installed_snapshot_updated ON $TABLE_SNAPSHOT($COL_UPDATED_AT)")
        }
    }

    companion object {
        private const val DATABASE_NAME = "kite_installed_resource_snapshot.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_SNAPSHOT = "installed_resource_snapshot"
        private const val COL_ENVIRONMENT_ID = "environment_id"
        private const val COL_RESOURCE_ID = "resource_id"
        private const val COL_NAME = "name"
        private const val COL_ICON_JSON = "icon_json"
        private const val COL_VERSION = "version"
        private const val COL_MANIFEST_JSON = "manifest_json"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val DEFAULT_ENVIRONMENT_ID = "default"
    }
}
