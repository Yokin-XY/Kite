package com.kite.app.resources

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class KiteResourceRegistryEntry(
    val resourceId: String,
    val status: String = "",
    val operation: String = "",
    val version: String = "",
    val runId: String = "",
    val summary: String = "",
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val installed: Boolean get() = status == KiteResourceRegistry.STATUS_INSTALLED
    val failed: Boolean get() = status == KiteResourceRegistry.STATUS_FAILED
    val installing: Boolean get() = status == KiteResourceRegistry.STATUS_INSTALLING
    val uninstalling: Boolean get() = status == KiteResourceRegistry.STATUS_UNINSTALLING
    val busy: Boolean get() = installing || uninstalling
}

data class KiteResourcePlanSnapshot(
    val targetResourceId: String = "",
    val status: String = "",
    val resourceIds: List<String> = emptyList(),
    val runningResourceIds: List<String> = emptyList(),
    val pendingResourceIds: List<String> = emptyList(),
    val stepStatusByResourceId: Map<String, String> = emptyMap()
) {
    fun stepStatus(resourceId: String): String =
        stepStatusByResourceId[resourceId.trim()].orEmpty()
}

class KiteResourceRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val database = KiteResourceRegistryDatabase(appContext)

    init {
        migrateLegacyPreferences()
    }

    fun status(resourceId: String): String? =
        registryString(resourceId, COL_STATUS).takeIf { it.isNotBlank() }

    fun isInstalled(resourceId: String): Boolean =
        status(resourceId) == STATUS_INSTALLED

    fun isFailed(resourceId: String): Boolean =
        status(resourceId) == STATUS_FAILED

    fun isInstalling(resourceId: String): Boolean =
        status(resourceId) == STATUS_INSTALLING

    fun isUninstalling(resourceId: String): Boolean =
        status(resourceId) == STATUS_UNINSTALLING

    fun isBusy(resourceId: String): Boolean =
        isInstalling(resourceId) || isUninstalling(resourceId)

    fun failedOperation(resourceId: String): String =
        registryString(resourceId, COL_OPERATION)

    fun snapshot(resourceIds: Collection<String> = emptyList()): Map<String, KiteResourceRegistryEntry> {
        val normalizedIds = resourceIds
            .map { normalizeResourceId(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val selection = if (normalizedIds.isEmpty()) null else "$COL_RESOURCE_ID IN (${normalizedIds.joinToString(",") { "?" }})"
        val selectionArgs = normalizedIds.takeIf { it.isNotEmpty() }?.toTypedArray()
        return database.readableDatabase.query(
            TABLE_REGISTRY,
            arrayOf(
                COL_RESOURCE_ID,
                COL_STATUS,
                COL_OPERATION,
                COL_VERSION,
                COL_RUN_ID,
                COL_SUMMARY,
                COL_INSTALLED_AT,
                COL_UPDATED_AT
            ),
            selection,
            selectionArgs,
            null,
            null,
            null
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val entry = KiteResourceRegistryEntry(
                        resourceId = cursor.getString(0).orEmpty(),
                        status = cursor.getString(1).orEmpty(),
                        operation = cursor.getString(2).orEmpty(),
                        version = cursor.getString(3).orEmpty(),
                        runId = cursor.getString(4).orEmpty(),
                        summary = cursor.getString(5).orEmpty(),
                        installedAt = cursor.getLong(6),
                        updatedAt = cursor.getLong(7)
                    )
                    put(entry.resourceId, entry)
                }
            }
        }
    }

    fun entry(resourceId: String): KiteResourceRegistryEntry? =
        snapshot(listOf(resourceId))[normalizeResourceId(resourceId)]

    fun planSnapshot(): KiteResourcePlanSnapshot {
        val db = database.readableDatabase
        val plan = db.query(
            TABLE_PLAN,
            arrayOf(COL_TARGET_RESOURCE_ID, COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty()
            } else {
                null
            }
        } ?: return KiteResourcePlanSnapshot()
        val resourceIds = mutableListOf<String>()
        val running = mutableListOf<String>()
        val pending = mutableListOf<String>()
        val statuses = linkedMapOf<String, String>()
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_RESOURCE_ID, COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val resourceId = cursor.getString(0).orEmpty()
                val status = cursor.getString(1).orEmpty()
                if (resourceId.isNotBlank() && !statuses.containsKey(resourceId)) {
                    resourceIds.add(resourceId)
                    statuses[resourceId] = status
                    if (status == PLAN_STEP_RUNNING && plan.second == PLAN_STATUS_ACTIVE) {
                        running.add(resourceId)
                    }
                    if (status == PLAN_STEP_PENDING && plan.second == PLAN_STATUS_ACTIVE) {
                        pending.add(resourceId)
                    }
                }
            }
        }
        return KiteResourcePlanSnapshot(
            targetResourceId = plan.first,
            status = plan.second,
            resourceIds = resourceIds,
            runningResourceIds = running,
            pendingResourceIds = pending,
            stepStatusByResourceId = statuses
        )
    }

    fun markInstalling(resourceId: String, runId: String? = null) {
        upsertRegistry(
            resourceId = resourceId,
            status = STATUS_INSTALLING,
            operation = OP_INSTALL,
            runId = runId.orEmpty(),
            summary = "获取中"
        )
    }

    fun markUninstalling(resourceId: String, runId: String? = null) {
        upsertRegistry(
            resourceId = resourceId,
            status = STATUS_UNINSTALLING,
            operation = OP_UNINSTALL,
            runId = runId.orEmpty(),
            summary = "卸载中"
        )
    }

    fun markInstalled(resourceId: String, version: String, runId: String?, summary: String?) {
        upsertRegistry(
            resourceId = resourceId,
            status = STATUS_INSTALLED,
            operation = OP_INSTALL,
            version = version,
            runId = runId.orEmpty(),
            summary = summary.orEmpty(),
            installedAt = System.currentTimeMillis()
        )
    }

    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String?) {
        upsertRegistry(
            resourceId = resourceId,
            status = STATUS_FAILED,
            operation = operation,
            runId = runId.orEmpty(),
            summary = reason.orEmpty()
        )
    }

    fun clear(resourceId: String) {
        database.writableDatabase.delete(
            TABLE_REGISTRY,
            "$COL_RESOURCE_ID = ?",
            arrayOf(normalizeResourceId(resourceId))
        )
    }

    fun beginPlan(targetResourceId: String, resourceIds: List<String>) {
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            clearPlanLocked(this)
            insertWithOnConflict(
                TABLE_PLAN,
                null,
                ContentValues().apply {
                    put(COL_PLAN_ID, ACTIVE_PLAN_ID)
                    put(COL_TARGET_RESOURCE_ID, normalizeResourceId(targetResourceId))
                    put(COL_STATUS, PLAN_STATUS_ACTIVE)
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            resourceIds.map { normalizeResourceId(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .forEachIndexed { index, resourceId ->
                    insertWithOnConflict(
                        TABLE_PLAN_STEP,
                        null,
                        ContentValues().apply {
                            put(COL_PLAN_ID, ACTIVE_PLAN_ID)
                            put(COL_STEP_INDEX, index)
                            put(COL_RESOURCE_ID, resourceId)
                            put(COL_STATUS, PLAN_STEP_PENDING)
                            put(COL_UPDATED_AT, now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
        }
    }

    fun pendingPlanResourceIds(): List<String> =
        pendingPlanResourceIds(database.readableDatabase)

    fun runningPlanResourceIds(): List<String> =
        runningPlanResourceIds(database.readableDatabase)

    fun planResourceIds(): List<String> =
        planResourceIds(database.readableDatabase)

    fun planStepStatus(resourceId: String): String =
        planStepStatus(database.readableDatabase, normalizeResourceId(resourceId)).orEmpty()

    fun markPlanStepRunning(resourceId: String): Boolean {
        val normalized = normalizeResourceId(resourceId)
        if (normalized.isBlank()) return false
        var marked = false
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            val running = runningPlanResourceIds(this)
            val firstPending = pendingPlanResourceIds(this).firstOrNull()
            when {
                running.isNotEmpty() -> {
                    marked = running.first() == normalized
                }
                firstPending != normalized -> {
                    marked = false
                }
                else -> {
                    val stepIndex = firstPendingStepIndex(this, normalized)
                    if (stepIndex != null) {
                        update(
                            TABLE_PLAN_STEP,
                            ContentValues().apply {
                                put(COL_STATUS, PLAN_STEP_RUNNING)
                                put(COL_UPDATED_AT, now)
                            },
                            "$COL_PLAN_ID = ? AND $COL_STEP_INDEX = ?",
                            arrayOf(ACTIVE_PLAN_ID, stepIndex.toString())
                        )
                        update(
                            TABLE_PLAN,
                            ContentValues().apply { put(COL_UPDATED_AT, now) },
                            "$COL_PLAN_ID = ?",
                            arrayOf(ACTIVE_PLAN_ID)
                        )
                        marked = true
                    }
                }
            }
        }
        return marked
    }

    fun advancePlanAfter(resourceId: String): List<String> {
        val normalized = normalizeResourceId(resourceId)
        var remaining = emptyList<String>()
        database.writableDatabase.runInTransaction {
            val stepIndex = planStepIndex(this, normalized)
            if (stepIndex == null) {
                remaining = emptyList()
                return@runInTransaction
            }
            val now = System.currentTimeMillis()
            update(
                TABLE_PLAN_STEP,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STEP_DONE)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ? AND $COL_STEP_INDEX = ?",
                arrayOf(ACTIVE_PLAN_ID, stepIndex.toString())
            )
            remaining = pendingPlanResourceIds(this)
            if (remaining.isEmpty()) {
                clearPlanLocked(this)
            } else {
                update(
                    TABLE_PLAN,
                    ContentValues().apply { put(COL_UPDATED_AT, now) },
                    "$COL_PLAN_ID = ?",
                    arrayOf(ACTIVE_PLAN_ID)
                )
            }
        }
        return remaining
    }

    fun failPlanAt(resourceId: String) {
        val normalized = normalizeResourceId(resourceId)
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            val failedIndex = planStepIndex(this, normalized) ?: firstPendingStepIndex(this, normalized)
            if (failedIndex != null) {
                update(
                    TABLE_PLAN_STEP,
                    ContentValues().apply {
                        put(COL_STATUS, PLAN_STEP_FAILED)
                        put(COL_UPDATED_AT, now)
                    },
                    "$COL_PLAN_ID = ? AND $COL_STEP_INDEX = ?",
                    arrayOf(ACTIVE_PLAN_ID, failedIndex.toString())
                )
                update(
                    TABLE_PLAN_STEP,
                    ContentValues().apply {
                        put(COL_STATUS, PLAN_STEP_BLOCKED)
                        put(COL_UPDATED_AT, now)
                    },
                    "$COL_PLAN_ID = ? AND $COL_STEP_INDEX > ? AND $COL_STATUS = ?",
                    arrayOf(ACTIVE_PLAN_ID, failedIndex.toString(), PLAN_STEP_PENDING)
                )
            }
            update(
                TABLE_PLAN,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STATUS_FAILED)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ?",
                arrayOf(ACTIVE_PLAN_ID)
            )
        }
    }

    fun resumePlanFrom(resourceId: String): Boolean {
        val normalized = normalizeResourceId(resourceId)
        if (normalized.isBlank()) return false
        val now = System.currentTimeMillis()
        var changed = false
        database.writableDatabase.runInTransaction {
            val stepIndex = planStepIndex(this, normalized) ?: return@runInTransaction
            val rows = update(
                TABLE_PLAN_STEP,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STEP_PENDING)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ? AND $COL_STEP_INDEX >= ? AND $COL_STATUS IN (?, ?)",
                arrayOf(ACTIVE_PLAN_ID, stepIndex.toString(), PLAN_STEP_FAILED, PLAN_STEP_BLOCKED)
            )
            update(
                TABLE_PLAN,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STATUS_ACTIVE)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ?",
                arrayOf(ACTIVE_PLAN_ID)
            )
            changed = rows > 0
        }
        return changed
    }

    fun clearPlan() {
        database.writableDatabase.runInTransaction {
            clearPlanLocked(this)
        }
    }

    private fun registryString(resourceId: String, column: String): String =
        database.readableDatabase.query(
            TABLE_REGISTRY,
            arrayOf(column),
            "$COL_RESOURCE_ID = ?",
            arrayOf(normalizeResourceId(resourceId)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }

    private fun registryLong(resourceId: String, column: String): Long =
        database.readableDatabase.query(
            TABLE_REGISTRY,
            arrayOf(column),
            "$COL_RESOURCE_ID = ?",
            arrayOf(normalizeResourceId(resourceId)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    private fun upsertRegistry(
        resourceId: String,
        status: String,
        operation: String,
        version: String? = null,
        runId: String,
        summary: String,
        installedAt: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val normalizedResourceId = normalizeResourceId(resourceId)
        val existing = if (version == null || installedAt == null) entry(normalizedResourceId) else null
        database.writableDatabase.insertWithOnConflict(
            TABLE_REGISTRY,
            null,
            ContentValues().apply {
                put(COL_RESOURCE_ID, normalizedResourceId)
                put(COL_STATUS, status)
                put(COL_OPERATION, operation)
                put(COL_VERSION, version ?: existing?.version.orEmpty())
                put(COL_RUN_ID, runId)
                put(COL_SUMMARY, summary)
                put(COL_INSTALLED_AT, installedAt ?: existing?.installedAt ?: 0L)
                put(COL_UPDATED_AT, now)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun pendingPlanResourceIds(db: SQLiteDatabase): List<String> {
        val planActive = db.query(
            TABLE_PLAN,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == PLAN_STATUS_ACTIVE
        }
        if (!planActive) return emptyList()
        return db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_RESOURCE_ID),
            "$COL_PLAN_ID = ? AND $COL_STATUS = ?",
            arrayOf(ACTIVE_PLAN_ID, PLAN_STEP_PENDING),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun runningPlanResourceIds(db: SQLiteDatabase): List<String> {
        val planActive = db.query(
            TABLE_PLAN,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == PLAN_STATUS_ACTIVE
        }
        if (!planActive) return emptyList()
        return db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_RESOURCE_ID),
            "$COL_PLAN_ID = ? AND $COL_STATUS = ?",
            arrayOf(ACTIVE_PLAN_ID, PLAN_STEP_RUNNING),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun planResourceIds(db: SQLiteDatabase): List<String> {
        val planExists = db.query(
            TABLE_PLAN,
            arrayOf(COL_PLAN_ID),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            null,
            "1"
        ).use { cursor -> cursor.moveToFirst() }
        if (!planExists) return emptyList()
        return db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_RESOURCE_ID),
            "$COL_PLAN_ID = ?",
            arrayOf(ACTIVE_PLAN_ID),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun firstPendingStepIndex(db: SQLiteDatabase, resourceId: String): Int? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STEP_INDEX),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ? AND $COL_STATUS = ?",
            arrayOf(ACTIVE_PLAN_ID, resourceId, PLAN_STEP_PENDING),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun planStepIndex(db: SQLiteDatabase, resourceId: String): Int? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STEP_INDEX),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(ACTIVE_PLAN_ID, resourceId),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun planStepStatus(db: SQLiteDatabase, resourceId: String): String? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(ACTIVE_PLAN_ID, resourceId),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun clearPlanLocked(db: SQLiteDatabase) {
        db.delete(TABLE_PLAN_STEP, "$COL_PLAN_ID = ?", arrayOf(ACTIVE_PLAN_ID))
        db.delete(TABLE_PLAN, "$COL_PLAN_ID = ?", arrayOf(ACTIVE_PLAN_ID))
    }

    private fun migrateLegacyPreferences() {
        val db = database.writableDatabase
        if (db.metaValue(META_LEGACY_PREFS_MIGRATED) == "1") return
        val prefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val now = System.currentTimeMillis()
        db.runInTransaction {
            all.keys
                .filter { it.endsWith(".$COL_STATUS") && !it.startsWith("install_plan.") }
                .forEach { statusKey ->
                    val resourceId = statusKey.removeSuffix(".$COL_STATUS")
                    val status = all[statusKey] as? String ?: return@forEach
                    insertWithOnConflict(
                        TABLE_REGISTRY,
                        null,
                        ContentValues().apply {
                            put(COL_RESOURCE_ID, resourceId)
                            put(COL_STATUS, status)
                            put(COL_OPERATION, (all["$resourceId.$COL_OPERATION"] as? String).orEmpty())
                            put(COL_VERSION, (all["$resourceId.$COL_VERSION"] as? String).orEmpty())
                            put(COL_RUN_ID, (all["$resourceId.runId"] as? String).orEmpty())
                            put(COL_SUMMARY, (all["$resourceId.$COL_SUMMARY"] as? String).orEmpty())
                            put(COL_INSTALLED_AT, 0L)
                            put(COL_UPDATED_AT, (all["$resourceId.updatedAt"] as? Long) ?: now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }

            val target = (all[LEGACY_KEY_PLAN_TARGET] as? String).orEmpty()
            val pendingRaw = (all[LEGACY_KEY_PLAN_PENDING] as? String).orEmpty()
            if (target.isNotBlank() && pendingRaw.isNotBlank()) {
                val pending = runCatching {
                    val array = JSONArray(pendingRaw)
                    buildList {
                        for (index in 0 until array.length()) {
                            array.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }.getOrDefault(emptyList())
                if (pending.isNotEmpty()) {
                    insertWithOnConflict(
                        TABLE_PLAN,
                        null,
                        ContentValues().apply {
                            put(COL_PLAN_ID, ACTIVE_PLAN_ID)
                            put(COL_TARGET_RESOURCE_ID, target)
                            put(COL_STATUS, PLAN_STATUS_ACTIVE)
                            put(COL_CREATED_AT, now)
                            put(COL_UPDATED_AT, (all[LEGACY_KEY_PLAN_UPDATED_AT] as? Long) ?: now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                    pending.forEachIndexed { index, resourceId ->
                        insertWithOnConflict(
                            TABLE_PLAN_STEP,
                            null,
                            ContentValues().apply {
                                put(COL_PLAN_ID, ACTIVE_PLAN_ID)
                                put(COL_STEP_INDEX, index)
                                put(COL_RESOURCE_ID, resourceId)
                                put(COL_STATUS, PLAN_STEP_PENDING)
                                put(COL_UPDATED_AT, now)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                }
            }
            putMeta(META_LEGACY_PREFS_MIGRATED, "1")
        }
    }

    private fun SQLiteDatabase.metaValue(key: String): String? =
        query(
            TABLE_META,
            arrayOf(COL_VALUE),
            "$COL_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun SQLiteDatabase.putMeta(key: String, value: String) {
        insertWithOnConflict(
            TABLE_META,
            null,
            ContentValues().apply {
                put(COL_KEY, key)
                put(COL_VALUE, value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun normalizeResourceId(resourceId: String): String =
        resourceId.trim().ifBlank { "resource" }

    private fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private class KiteResourceRegistryDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
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
                CREATE TABLE IF NOT EXISTS $TABLE_REGISTRY (
                    $COL_RESOURCE_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_OPERATION TEXT NOT NULL DEFAULT '',
                    $COL_VERSION TEXT NOT NULL DEFAULT '',
                    $COL_RUN_ID TEXT NOT NULL DEFAULT '',
                    $COL_SUMMARY TEXT NOT NULL DEFAULT '',
                    $COL_INSTALLED_AT INTEGER NOT NULL DEFAULT 0,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_resource_registry_status ON $TABLE_REGISTRY($COL_STATUS)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_resource_registry_operation ON $TABLE_REGISTRY($COL_OPERATION)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_PLAN (
                    $COL_PLAN_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_TARGET_RESOURCE_ID TEXT NOT NULL,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_PLAN_STEP (
                    $COL_PLAN_ID TEXT NOT NULL,
                    $COL_STEP_INDEX INTEGER NOT NULL,
                    $COL_RESOURCE_ID TEXT NOT NULL,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_PLAN_ID, $COL_STEP_INDEX),
                    FOREIGN KEY($COL_PLAN_ID) REFERENCES $TABLE_PLAN($COL_PLAN_ID) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_plan_step_pending ON $TABLE_PLAN_STEP($COL_PLAN_ID, $COL_STATUS, $COL_STEP_INDEX)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_META (
                    $COL_KEY TEXT PRIMARY KEY NOT NULL,
                    $COL_VALUE TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    companion object {
        const val STATUS_INSTALLED = "installed"
        const val STATUS_FAILED = "failed"
        const val STATUS_INSTALLING = "installing"
        const val STATUS_UNINSTALLING = "uninstalling"
        const val OP_INSTALL = "install"
        const val OP_UNINSTALL = "uninstall"

        private const val DATABASE_NAME = "kite_system_registry.db"
        private const val DATABASE_VERSION = 2
        private const val ACTIVE_PLAN_ID = "active"

        private const val TABLE_REGISTRY = "resource_registry"
        private const val TABLE_PLAN = "resource_install_plan"
        private const val TABLE_PLAN_STEP = "resource_install_plan_step"
        private const val TABLE_META = "resource_meta"

        private const val COL_RESOURCE_ID = "resource_id"
        private const val COL_STATUS = "status"
        private const val COL_OPERATION = "operation"
        private const val COL_VERSION = "version"
        private const val COL_RUN_ID = "run_id"
        private const val COL_SUMMARY = "summary"
        private const val COL_INSTALLED_AT = "installed_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val COL_PLAN_ID = "plan_id"
        private const val COL_TARGET_RESOURCE_ID = "target_resource_id"
        private const val COL_STEP_INDEX = "step_index"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_KEY = "key"
        private const val COL_VALUE = "value"

        private const val PLAN_STATUS_ACTIVE = "active"
        private const val PLAN_STEP_PENDING = "pending"
        const val PLAN_STEP_RUNNING = "running"
        const val PLAN_STEP_DONE = "done"

        private const val LEGACY_PREFS_NAME = "kite_resource_installs"
        private const val LEGACY_KEY_PLAN_TARGET = "install_plan.target"
        private const val LEGACY_KEY_PLAN_PENDING = "install_plan.pending"
        private const val LEGACY_KEY_PLAN_UPDATED_AT = "install_plan.updatedAt"
        private const val META_LEGACY_PREFS_MIGRATED = "legacy_prefs_migrated"

        const val PLAN_STEP_FAILED = "failed"
        const val PLAN_STEP_BLOCKED = "blocked"

        private const val PLAN_STATUS_FAILED = "failed"
    }
}
