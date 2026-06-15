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
        manifestJson: String
    ) {
        val now = System.currentTimeMillis()
        database.writableDatabase.insertWithOnConflict(
            TABLE_SNAPSHOT,
            null,
            ContentValues().apply {
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

    fun manifestJson(resourceId: String): String? =
        database.readableDatabase.query(
            TABLE_SNAPSHOT,
            arrayOf(COL_MANIFEST_JSON),
            "$COL_RESOURCE_ID = ?",
            arrayOf(normalizeResourceId(resourceId)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun clear(resourceId: String) {
        database.writableDatabase.delete(
            TABLE_SNAPSHOT,
            "$COL_RESOURCE_ID = ?",
            arrayOf(normalizeResourceId(resourceId))
        )
    }

    private fun normalizeResourceId(resourceId: String): String =
        resourceId.trim().ifBlank { "resource" }

    private class SnapshotDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            createSchema(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SNAPSHOT (
                    $COL_RESOURCE_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_NAME TEXT NOT NULL DEFAULT '',
                    $COL_ICON_JSON TEXT NOT NULL DEFAULT '',
                    $COL_VERSION TEXT NOT NULL DEFAULT '',
                    $COL_MANIFEST_JSON TEXT NOT NULL DEFAULT '',
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_installed_snapshot_updated ON $TABLE_SNAPSHOT($COL_UPDATED_AT)")
        }
    }

    companion object {
        private const val DATABASE_NAME = "kite_installed_resource_snapshot.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SNAPSHOT = "installed_resource_snapshot"
        private const val COL_RESOURCE_ID = "resource_id"
        private const val COL_NAME = "name"
        private const val COL_ICON_JSON = "icon_json"
        private const val COL_VERSION = "version"
        private const val COL_MANIFEST_JSON = "manifest_json"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
    }
}
