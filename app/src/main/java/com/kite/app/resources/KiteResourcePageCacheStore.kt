package com.kite.app.resources

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KiteResourcePageCacheStore(context: Context) {
    private val database = PageCacheDatabase(context.applicationContext)

    fun putPage(cacheKey: String, payloadJson: String, maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        database.writableDatabase.insertWithOnConflict(
            TABLE_PAGE_CACHE,
            null,
            ContentValues().apply {
                put(COL_CACHE_KEY, cacheKey)
                put(COL_PAYLOAD_JSON, payloadJson)
                put(COL_EXPIRES_AT, now + maxAgeMs)
                put(COL_UPDATED_AT, now)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun page(cacheKey: String): String? {
        val now = System.currentTimeMillis()
        return database.readableDatabase.query(
            TABLE_PAGE_CACHE,
            arrayOf(COL_PAYLOAD_JSON, COL_EXPIRES_AT),
            "$COL_CACHE_KEY = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val expiresAt = cursor.getLong(1)
            if (expiresAt < now) null else cursor.getString(0)
        }
    }

    fun clearExpired() {
        database.writableDatabase.delete(
            TABLE_PAGE_CACHE,
            "$COL_EXPIRES_AT < ?",
            arrayOf(System.currentTimeMillis().toString())
        )
    }

    fun clearAll() {
        database.writableDatabase.delete(TABLE_PAGE_CACHE, null, null)
    }

    private class PageCacheDatabase(context: Context) :
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
                CREATE TABLE IF NOT EXISTS $TABLE_PAGE_CACHE (
                    $COL_CACHE_KEY TEXT PRIMARY KEY NOT NULL,
                    $COL_PAYLOAD_JSON TEXT NOT NULL,
                    $COL_EXPIRES_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_resource_page_cache_expiry ON $TABLE_PAGE_CACHE($COL_EXPIRES_AT)")
        }
    }

    companion object {
        private const val DATABASE_NAME = "kite_resource_page_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_PAGE_CACHE = "resource_page_cache"
        private const val COL_CACHE_KEY = "cache_key"
        private const val COL_PAYLOAD_JSON = "payload_json"
        private const val COL_EXPIRES_AT = "expires_at"
        private const val COL_UPDATED_AT = "updated_at"
    }
}
