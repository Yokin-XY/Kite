package com.kite.app.resources

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class KiteResourceRegistryEntry(
    val environmentId: String = KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID,
    val resourceId: String,
    val status: String = "",
    val operation: String = "",
    val version: String = "",
    val latestVersion: String = "",
    val updateStatus: String = "",
    val lastCheckedAt: Long = 0L,
    val runId: String = "",
    val summary: String = "",
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val installed: Boolean get() = status == KiteResourceRegistry.STATUS_INSTALLED
    val failed: Boolean get() = status == KiteResourceRegistry.STATUS_FAILED
    val preparing: Boolean get() = status == KiteResourceRegistry.STATUS_PREPARING
    val installing: Boolean get() = status == KiteResourceRegistry.STATUS_INSTALLING
    val uninstalling: Boolean get() = status == KiteResourceRegistry.STATUS_UNINSTALLING
    val busy: Boolean get() = preparing || installing || uninstalling
}

data class KiteResourcePlanSnapshot(
    val targetResourceId: String = "",
    val status: String = "",
    /** 计划行的稳定创建代次；状态推进只更新 updatedAt，不改变此值。 */
    val generation: Long = 0L,
    val resourceIds: List<String> = emptyList(),
    val runningResourceIds: List<String> = emptyList(),
    val pendingResourceIds: List<String> = emptyList(),
    val stepStatusByResourceId: Map<String, String> = emptyMap()
) {
    val isPreparing: Boolean
        get() = status == KiteResourceRegistry.PLAN_STATUS_PREPARING

    val isActive: Boolean
        get() = status == KiteResourceRegistry.PLAN_STATUS_ACTIVE

    fun stepStatus(resourceId: String): String =
        stepStatusByResourceId[resourceId.trim()].orEmpty()
}

class KiteResourceRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val database = KiteResourceRegistryDatabase(appContext)

    init {
        migrateLegacyPreferences()
    }

    fun status(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): String? =
        registryString(resourceId, environmentId, COL_STATUS).takeIf { it.isNotBlank() }

    fun isInstalled(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        status(resourceId, environmentId) == STATUS_INSTALLED

    fun isFailed(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        status(resourceId, environmentId) == STATUS_FAILED

    fun isInstalling(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        status(resourceId, environmentId) == STATUS_INSTALLING

    fun isPreparing(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        status(resourceId, environmentId) == STATUS_PREPARING

    fun isUninstalling(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        status(resourceId, environmentId) == STATUS_UNINSTALLING

    fun isBusy(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean =
        isPreparing(resourceId, environmentId) ||
            isInstalling(resourceId, environmentId) ||
            isUninstalling(resourceId, environmentId)

    fun failedOperation(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): String =
        registryString(resourceId, environmentId, COL_OPERATION)

    fun snapshot(
        resourceIds: Collection<String> = emptyList(),
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): Map<String, KiteResourceRegistryEntry> {
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        val normalizedIds = resourceIds
            .map { normalizeResourceId(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val selection = buildString {
            append("$COL_ENVIRONMENT_ID = ?")
            if (normalizedIds.isNotEmpty()) {
                append(" AND $COL_RESOURCE_ID IN (${normalizedIds.joinToString(",") { "?" }})")
            }
        }
        val selectionArgs = (listOf(normalizedEnvironmentId) + normalizedIds).toTypedArray()
        return database.readableDatabase.query(
            TABLE_REGISTRY,
            arrayOf(
                COL_ENVIRONMENT_ID,
                COL_RESOURCE_ID,
                COL_STATUS,
                COL_OPERATION,
                COL_VERSION,
                COL_LATEST_VERSION,
                COL_UPDATE_STATUS,
                COL_LAST_CHECKED_AT,
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
                        environmentId = cursor.getString(0).orEmpty(),
                        resourceId = cursor.getString(1).orEmpty(),
                        status = cursor.getString(2).orEmpty(),
                        operation = cursor.getString(3).orEmpty(),
                        version = cursor.getString(4).orEmpty(),
                        latestVersion = cursor.getString(5).orEmpty(),
                        updateStatus = cursor.getString(6).orEmpty(),
                        lastCheckedAt = cursor.getLong(7),
                        runId = cursor.getString(8).orEmpty(),
                        summary = cursor.getString(9).orEmpty(),
                        installedAt = cursor.getLong(10),
                        updatedAt = cursor.getLong(11)
                    )
                    put(entry.resourceId, entry)
                }
            }
        }
    }

    fun entry(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): KiteResourceRegistryEntry? =
        snapshot(listOf(resourceId), environmentId)[normalizeResourceId(resourceId)]

    fun planSnapshot(environmentId: String = DEFAULT_ENVIRONMENT_ID): KiteResourcePlanSnapshot {
        val activePlanId = planId(environmentId)
        val db = database.readableDatabase
        val plan = db.query(
            TABLE_PLAN,
            arrayOf(COL_TARGET_RESOURCE_ID, COL_STATUS, COL_CREATED_AT),
            "$COL_PLAN_ID = ?",
            arrayOf(activePlanId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Triple(
                    cursor.getString(0).orEmpty(),
                    cursor.getString(1).orEmpty(),
                    cursor.getLong(2),
                )
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
            arrayOf(activePlanId),
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
            generation = plan.third,
            resourceIds = resourceIds,
            runningResourceIds = running,
            pendingResourceIds = pending,
            stepStatusByResourceId = statuses
        )
    }

    fun markInstalling(
        resourceId: String,
        runId: String? = null,
        operation: String = OP_INSTALL,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        upsertRegistry(
            environmentId = environmentId,
            resourceId = resourceId,
            status = STATUS_INSTALLING,
            operation = operation,
            runId = runId.orEmpty(),
            summary = "获取中"
        )
    }

    fun markPreparing(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        upsertRegistry(
            environmentId = environmentId,
            resourceId = resourceId,
            status = STATUS_PREPARING,
            operation = OP_INSTALL,
            runId = "",
            summary = "准备中"
        )
    }

    fun markUninstalling(
        resourceId: String,
        runId: String? = null,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        upsertRegistry(
            environmentId = environmentId,
            resourceId = resourceId,
            status = STATUS_UNINSTALLING,
            operation = OP_UNINSTALL,
            runId = runId.orEmpty(),
            summary = "卸载中"
        )
    }

    fun markInstalled(
        resourceId: String,
        version: String,
        runId: String?,
        summary: String?,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        upsertRegistry(
            environmentId = environmentId,
            resourceId = resourceId,
            status = STATUS_INSTALLED,
            operation = OP_INSTALL,
            version = version,
            runId = runId.orEmpty(),
            summary = summary.orEmpty(),
            installedAt = System.currentTimeMillis()
        )
    }

    fun markFailed(
        resourceId: String,
        operation: String,
        runId: String?,
        reason: String?,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        upsertRegistry(
            environmentId = environmentId,
            resourceId = resourceId,
            status = STATUS_FAILED,
            operation = operation,
            runId = runId.orEmpty(),
            summary = reason.orEmpty()
        )
    }

    fun markVersionCheck(
        resourceId: String,
        updateStatus: String,
        installedVersion: String = "",
        latestVersion: String = "",
        summary: String? = null,
        operation: String? = null,
        status: String? = null,
        checkedAt: Long = System.currentTimeMillis(),
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        val normalizedResourceId = normalizeResourceId(resourceId)
        val existing = entry(normalizedResourceId, normalizedEnvironmentId) ?: return
        database.writableDatabase.insertWithOnConflict(
            TABLE_REGISTRY,
            null,
            ContentValues().apply {
                put(COL_ENVIRONMENT_ID, normalizedEnvironmentId)
                put(COL_RESOURCE_ID, normalizedResourceId)
                put(COL_STATUS, status ?: existing.status)
                put(COL_OPERATION, operation ?: existing.operation)
                put(COL_VERSION, installedVersion.ifBlank { existing.version })
                put(COL_LATEST_VERSION, latestVersion.ifBlank { existing.latestVersion })
                put(COL_UPDATE_STATUS, updateStatus)
                put(COL_LAST_CHECKED_AT, checkedAt)
                put(COL_RUN_ID, existing.runId)
                put(COL_SUMMARY, summary ?: existing.summary)
                put(COL_INSTALLED_AT, existing.installedAt)
                put(COL_UPDATED_AT, System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clear(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        database.writableDatabase.delete(
            TABLE_REGISTRY,
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
            TABLE_REGISTRY,
            "$COL_ENVIRONMENT_ID = ? AND $COL_RESOURCE_ID IN ($placeholders)",
            selectionArgs
        )
    }

    fun beginPlan(
        targetResourceId: String,
        resourceIds: List<String>,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ) {
        val activePlanId = planId(environmentId)
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            clearPlanLocked(this, activePlanId)
            insertWithOnConflict(
                TABLE_PLAN,
                null,
                ContentValues().apply {
                    put(COL_PLAN_ID, activePlanId)
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
                            put(COL_PLAN_ID, activePlanId)
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

    /**
     * 接受一个尚未完成依赖计算的安装计划。
     *
     * 同一环境只允许一个计划事实。相同目标的 PREPARING 写入视为幂等成功；
     * 其他目标或已经进入 ACTIVE/FAILED 的计划不能被准备动作覆盖。
     */
    fun beginPreparingPlan(
        targetResourceId: String,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): Boolean {
        val normalizedTarget = targetResourceId.trim()
            .takeIf(String::isNotBlank)
            ?.let(::normalizeResourceId)
            ?: return false
        val activePlanId = planId(environmentId)
        val now = System.currentTimeMillis()
        var accepted = false
        database.writableDatabase.runInTransaction {
            val current = planTargetAndStatus(this, activePlanId)
            when {
                current == null -> {
                    insertWithOnConflict(
                        TABLE_PLAN,
                        null,
                        ContentValues().apply {
                            put(COL_PLAN_ID, activePlanId)
                            put(COL_TARGET_RESOURCE_ID, normalizedTarget)
                            put(COL_STATUS, PLAN_STATUS_PREPARING)
                            put(COL_CREATED_AT, now)
                            put(COL_UPDATED_AT, now)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                    accepted = true
                }
                current.first == normalizedTarget && current.second == PLAN_STATUS_PREPARING -> {
                    accepted = true
                }
            }
        }
        return accepted
    }

    /**
     * 把同一目标的 PREPARING 计划原子转换为可执行计划。
     *
     * 状态校验、步骤替换和 ACTIVE 写入处于同一事务中；迟到的准备结果不能覆盖
     * 已取消、已失败、已激活或属于其他目标的计划。
     */
    fun activatePreparedPlan(
        targetResourceId: String,
        resourceIds: List<String>,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): Boolean {
        val normalizedTarget = targetResourceId.trim()
            .takeIf(String::isNotBlank)
            ?.let(::normalizeResourceId)
            ?: return false
        val normalizedResourceIds = resourceIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::normalizeResourceId)
            .distinct()
        val activePlanId = planId(environmentId)
        val now = System.currentTimeMillis()
        var activated = false
        database.writableDatabase.runInTransaction {
            val rows = update(
                TABLE_PLAN,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STATUS_ACTIVE)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ? AND $COL_TARGET_RESOURCE_ID = ? AND $COL_STATUS = ?",
                arrayOf(activePlanId, normalizedTarget, PLAN_STATUS_PREPARING)
            )
            if (rows != 1) return@runInTransaction
            delete(TABLE_PLAN_STEP, "$COL_PLAN_ID = ?", arrayOf(activePlanId))
            normalizedResourceIds.forEachIndexed { index, resourceId ->
                insertWithOnConflict(
                    TABLE_PLAN_STEP,
                    null,
                    ContentValues().apply {
                        put(COL_PLAN_ID, activePlanId)
                        put(COL_STEP_INDEX, index)
                        put(COL_RESOURCE_ID, resourceId)
                        put(COL_STATUS, PLAN_STEP_PENDING)
                        put(COL_UPDATED_AT, now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            activated = true
        }
        return activated
    }

    fun pendingPlanResourceIds(environmentId: String = DEFAULT_ENVIRONMENT_ID): List<String> =
        pendingPlanResourceIds(database.readableDatabase, planId(environmentId))

    fun runningPlanResourceIds(environmentId: String = DEFAULT_ENVIRONMENT_ID): List<String> =
        runningPlanResourceIds(database.readableDatabase, planId(environmentId))

    fun planResourceIds(environmentId: String = DEFAULT_ENVIRONMENT_ID): List<String> =
        planResourceIds(database.readableDatabase, planId(environmentId))

    fun planStepStatus(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): String =
        planStepStatus(database.readableDatabase, planId(environmentId), normalizeResourceId(resourceId)).orEmpty()

    fun markPlanStepRunning(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean {
        val activePlanId = planId(environmentId)
        val normalized = normalizeResourceId(resourceId)
        if (normalized.isBlank()) return false
        var marked = false
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            val running = runningPlanResourceIds(this, activePlanId)
            val firstPending = pendingPlanResourceIds(this, activePlanId).firstOrNull()
            when {
                running.isNotEmpty() -> {
                    marked = running.first() == normalized
                }
                firstPending != normalized -> {
                    marked = false
                }
                else -> {
                    val stepIndex = firstPendingStepIndex(this, activePlanId, normalized)
                    if (stepIndex != null) {
                        update(
                            TABLE_PLAN_STEP,
                            ContentValues().apply {
                                put(COL_STATUS, PLAN_STEP_RUNNING)
                                put(COL_UPDATED_AT, now)
                            },
                            "$COL_PLAN_ID = ? AND $COL_STEP_INDEX = ?",
                            arrayOf(activePlanId, stepIndex.toString())
                        )
                        update(
                            TABLE_PLAN,
                            ContentValues().apply { put(COL_UPDATED_AT, now) },
                            "$COL_PLAN_ID = ?",
                            arrayOf(activePlanId)
                        )
                        marked = true
                    }
                }
            }
        }
        return marked
    }

    fun advancePlanAfter(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): List<String> {
        val activePlanId = planId(environmentId)
        val normalized = normalizeResourceId(resourceId)
        var remaining = emptyList<String>()
        database.writableDatabase.runInTransaction {
            val stepIndex = planStepIndex(this, activePlanId, normalized)
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
                arrayOf(activePlanId, stepIndex.toString())
            )
            remaining = pendingPlanResourceIds(this, activePlanId)
            if (remaining.isEmpty()) {
                clearPlanLocked(this, activePlanId)
            } else {
                update(
                    TABLE_PLAN,
                    ContentValues().apply { put(COL_UPDATED_AT, now) },
                    "$COL_PLAN_ID = ?",
                    arrayOf(activePlanId)
                )
            }
        }
        return remaining
    }

    /**
     * 失败事实属于具体资源；环境级计划只负责串行调度，不能在失败后继续占用入口。
     * 资源失败已经由运行协调器写入登记表，这里把计划失败与释放执行槽放在同一事务。
     */
    fun failPlanAt(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        val activePlanId = planId(environmentId)
        val normalized = normalizeResourceId(resourceId)
        val now = System.currentTimeMillis()
        database.writableDatabase.runInTransaction {
            val failedIndex = planStepIndex(this, activePlanId, normalized)
                ?: firstPendingStepIndex(this, activePlanId, normalized)
            if (failedIndex != null) {
                update(
                    TABLE_PLAN_STEP,
                    ContentValues().apply {
                        put(COL_STATUS, PLAN_STEP_FAILED)
                        put(COL_UPDATED_AT, now)
                    },
                    "$COL_PLAN_ID = ? AND $COL_STEP_INDEX = ?",
                    arrayOf(activePlanId, failedIndex.toString())
                )
                update(
                    TABLE_PLAN_STEP,
                    ContentValues().apply {
                        put(COL_STATUS, PLAN_STEP_BLOCKED)
                        put(COL_UPDATED_AT, now)
                    },
                    "$COL_PLAN_ID = ? AND $COL_STEP_INDEX > ? AND $COL_STATUS = ?",
                    arrayOf(activePlanId, failedIndex.toString(), PLAN_STEP_PENDING)
                )
            }
            update(
                TABLE_PLAN,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STATUS_FAILED)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ?",
                arrayOf(activePlanId)
            )
            clearPlanLocked(this, activePlanId)
        }
    }

    fun resumePlanFrom(resourceId: String, environmentId: String = DEFAULT_ENVIRONMENT_ID): Boolean {
        val activePlanId = planId(environmentId)
        val normalized = normalizeResourceId(resourceId)
        if (normalized.isBlank()) return false
        val now = System.currentTimeMillis()
        var changed = false
        database.writableDatabase.runInTransaction {
            val stepIndex = planStepIndex(this, activePlanId, normalized) ?: return@runInTransaction
            val rows = update(
                TABLE_PLAN_STEP,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STEP_PENDING)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ? AND $COL_STEP_INDEX >= ? AND $COL_STATUS IN (?, ?)",
                arrayOf(activePlanId, stepIndex.toString(), PLAN_STEP_FAILED, PLAN_STEP_BLOCKED)
            )
            update(
                TABLE_PLAN,
                ContentValues().apply {
                    put(COL_STATUS, PLAN_STATUS_ACTIVE)
                    put(COL_UPDATED_AT, now)
                },
                "$COL_PLAN_ID = ?",
                arrayOf(activePlanId)
            )
            changed = rows > 0
        }
        return changed
    }

    fun clearPlan(environmentId: String = DEFAULT_ENVIRONMENT_ID) {
        val activePlanId = planId(environmentId)
        database.writableDatabase.runInTransaction {
            clearPlanLocked(this, activePlanId)
        }
    }

    private fun registryString(resourceId: String, environmentId: String, column: String): String =
        database.readableDatabase.query(
            TABLE_REGISTRY,
            arrayOf(column),
            "$COL_ENVIRONMENT_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(normalizeEnvironmentId(environmentId), normalizeResourceId(resourceId)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }

    private fun upsertRegistry(
        environmentId: String,
        resourceId: String,
        status: String,
        operation: String,
        version: String? = null,
        runId: String,
        summary: String,
        installedAt: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        val normalizedResourceId = normalizeResourceId(resourceId)
        val existing = if (version == null || installedAt == null) {
            entry(normalizedResourceId, normalizedEnvironmentId)
        } else null
        database.writableDatabase.insertWithOnConflict(
            TABLE_REGISTRY,
            null,
            ContentValues().apply {
                put(COL_ENVIRONMENT_ID, normalizedEnvironmentId)
                put(COL_RESOURCE_ID, normalizedResourceId)
                put(COL_STATUS, status)
                put(COL_OPERATION, operation)
                put(COL_VERSION, version ?: existing?.version.orEmpty())
                put(COL_LATEST_VERSION, existing?.latestVersion.orEmpty())
                put(COL_UPDATE_STATUS, existing?.updateStatus.orEmpty())
                put(COL_LAST_CHECKED_AT, existing?.lastCheckedAt ?: 0L)
                put(COL_RUN_ID, runId)
                put(COL_SUMMARY, summary)
                put(COL_INSTALLED_AT, installedAt ?: existing?.installedAt ?: 0L)
                put(COL_UPDATED_AT, now)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun pendingPlanResourceIds(db: SQLiteDatabase, activePlanId: String): List<String> {
        val planActive = db.query(
            TABLE_PLAN,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(activePlanId),
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
            arrayOf(activePlanId, PLAN_STEP_PENDING),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun runningPlanResourceIds(db: SQLiteDatabase, activePlanId: String): List<String> {
        val planActive = db.query(
            TABLE_PLAN,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(activePlanId),
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
            arrayOf(activePlanId, PLAN_STEP_RUNNING),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun planResourceIds(db: SQLiteDatabase, activePlanId: String): List<String> {
        val planExists = db.query(
            TABLE_PLAN,
            arrayOf(COL_PLAN_ID),
            "$COL_PLAN_ID = ?",
            arrayOf(activePlanId),
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
            arrayOf(activePlanId),
            null,
            null,
            "$COL_STEP_INDEX ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }.distinct()
        }
    }

    private fun planTargetAndStatus(db: SQLiteDatabase, activePlanId: String): Pair<String, String>? =
        db.query(
            TABLE_PLAN,
            arrayOf(COL_TARGET_RESOURCE_ID, COL_STATUS),
            "$COL_PLAN_ID = ?",
            arrayOf(activePlanId),
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
        }

    private fun firstPendingStepIndex(db: SQLiteDatabase, activePlanId: String, resourceId: String): Int? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STEP_INDEX),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ? AND $COL_STATUS = ?",
            arrayOf(activePlanId, resourceId, PLAN_STEP_PENDING),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun planStepIndex(db: SQLiteDatabase, activePlanId: String, resourceId: String): Int? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STEP_INDEX),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(activePlanId, resourceId),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }

    private fun planStepStatus(db: SQLiteDatabase, activePlanId: String, resourceId: String): String? =
        db.query(
            TABLE_PLAN_STEP,
            arrayOf(COL_STATUS),
            "$COL_PLAN_ID = ? AND $COL_RESOURCE_ID = ?",
            arrayOf(activePlanId, resourceId),
            null,
            null,
            "$COL_STEP_INDEX ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun clearPlanLocked(db: SQLiteDatabase, activePlanId: String) {
        db.delete(TABLE_PLAN_STEP, "$COL_PLAN_ID = ?", arrayOf(activePlanId))
        db.delete(TABLE_PLAN, "$COL_PLAN_ID = ?", arrayOf(activePlanId))
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
                            put(COL_ENVIRONMENT_ID, DEFAULT_ENVIRONMENT_ID)
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

    private fun normalizeEnvironmentId(environmentId: String): String =
        environmentId.trim().ifBlank { DEFAULT_ENVIRONMENT_ID }

    private fun planId(environmentId: String): String =
        normalizeEnvironmentId(environmentId).let { normalized ->
            if (normalized == DEFAULT_ENVIRONMENT_ID) ACTIVE_PLAN_ID else "$ACTIVE_PLAN_ID:$normalized"
        }

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
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE_REGISTRY ADD COLUMN $COL_LATEST_VERSION TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $TABLE_REGISTRY ADD COLUMN $COL_UPDATE_STATUS TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $TABLE_REGISTRY ADD COLUMN $COL_LAST_CHECKED_AT INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 4) {
                db.execSQL(
                    """
                    CREATE TABLE ${TABLE_REGISTRY}_v4 (
                        $COL_ENVIRONMENT_ID TEXT NOT NULL,
                        $COL_RESOURCE_ID TEXT NOT NULL,
                        $COL_STATUS TEXT NOT NULL,
                        $COL_OPERATION TEXT NOT NULL DEFAULT '',
                        $COL_VERSION TEXT NOT NULL DEFAULT '',
                        $COL_LATEST_VERSION TEXT NOT NULL DEFAULT '',
                        $COL_UPDATE_STATUS TEXT NOT NULL DEFAULT '',
                        $COL_LAST_CHECKED_AT INTEGER NOT NULL DEFAULT 0,
                        $COL_RUN_ID TEXT NOT NULL DEFAULT '',
                        $COL_SUMMARY TEXT NOT NULL DEFAULT '',
                        $COL_INSTALLED_AT INTEGER NOT NULL DEFAULT 0,
                        $COL_UPDATED_AT INTEGER NOT NULL,
                        PRIMARY KEY($COL_ENVIRONMENT_ID, $COL_RESOURCE_ID)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO ${TABLE_REGISTRY}_v4 (
                        $COL_ENVIRONMENT_ID, $COL_RESOURCE_ID, $COL_STATUS, $COL_OPERATION,
                        $COL_VERSION, $COL_LATEST_VERSION, $COL_UPDATE_STATUS, $COL_LAST_CHECKED_AT,
                        $COL_RUN_ID, $COL_SUMMARY, $COL_INSTALLED_AT, $COL_UPDATED_AT
                    ) SELECT ?, $COL_RESOURCE_ID, $COL_STATUS, $COL_OPERATION,
                        $COL_VERSION, $COL_LATEST_VERSION, $COL_UPDATE_STATUS, $COL_LAST_CHECKED_AT,
                        $COL_RUN_ID, $COL_SUMMARY, $COL_INSTALLED_AT, $COL_UPDATED_AT
                    FROM $TABLE_REGISTRY
                    """.trimIndent(),
                    arrayOf(DEFAULT_ENVIRONMENT_ID)
                )
                db.execSQL("DROP TABLE $TABLE_REGISTRY")
                db.execSQL("ALTER TABLE ${TABLE_REGISTRY}_v4 RENAME TO $TABLE_REGISTRY")
            }
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_REGISTRY (
                    $COL_ENVIRONMENT_ID TEXT NOT NULL,
                    $COL_RESOURCE_ID TEXT NOT NULL,
                    $COL_STATUS TEXT NOT NULL,
                    $COL_OPERATION TEXT NOT NULL DEFAULT '',
                    $COL_VERSION TEXT NOT NULL DEFAULT '',
                    $COL_LATEST_VERSION TEXT NOT NULL DEFAULT '',
                    $COL_UPDATE_STATUS TEXT NOT NULL DEFAULT '',
                    $COL_LAST_CHECKED_AT INTEGER NOT NULL DEFAULT 0,
                    $COL_RUN_ID TEXT NOT NULL DEFAULT '',
                    $COL_SUMMARY TEXT NOT NULL DEFAULT '',
                    $COL_INSTALLED_AT INTEGER NOT NULL DEFAULT 0,
                    $COL_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_ENVIRONMENT_ID, $COL_RESOURCE_ID)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_resource_registry_status ON $TABLE_REGISTRY($COL_ENVIRONMENT_ID, $COL_STATUS)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_resource_registry_operation ON $TABLE_REGISTRY($COL_ENVIRONMENT_ID, $COL_OPERATION)")
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
        const val STATUS_PREPARING = "preparing"
        const val STATUS_INSTALLING = "installing"
        const val STATUS_UNINSTALLING = "uninstalling"
        const val OP_INSTALL = "install"
        const val OP_UNINSTALL = "uninstall"

        private const val DATABASE_NAME = "kite_system_registry.db"
        private const val DATABASE_VERSION = 4
        private const val ACTIVE_PLAN_ID = "active"

        private const val TABLE_REGISTRY = "resource_registry"
        private const val TABLE_PLAN = "resource_install_plan"
        private const val TABLE_PLAN_STEP = "resource_install_plan_step"
        private const val TABLE_META = "resource_meta"

        private const val COL_ENVIRONMENT_ID = "environment_id"
        private const val COL_RESOURCE_ID = "resource_id"
        private const val COL_STATUS = "status"
        private const val COL_OPERATION = "operation"
        private const val COL_VERSION = "version"
        private const val COL_LATEST_VERSION = "latest_version"
        private const val COL_UPDATE_STATUS = "update_status"
        private const val COL_LAST_CHECKED_AT = "last_checked_at"
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

        const val PLAN_STATUS_PREPARING = "preparing"
        const val PLAN_STATUS_ACTIVE = "active"
        const val PLAN_STEP_PENDING = "pending"
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

        const val DEFAULT_ENVIRONMENT_ID = "default"
    }
}
