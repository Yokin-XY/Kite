package com.kite.app.foundation.runtime

import android.system.Os
import com.kite.app.foundation.contracts.ContainerRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ProotViewState {
    PREPARING,
    READY,
    CURRENT,
    BROKEN,
    DISCARDED
}

enum class ProotViewLeaseMode {
    READER,
    WRITER
}

data class ProotViewLease(
    val ownerId: String,
    val mode: ProotViewLeaseMode,
    val acquiredAtUnixMs: Long,
    val processSessionId: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("ownerId", ownerId)
        .put("mode", mode.name)
        .put("acquiredAtUnixMs", acquiredAtUnixMs)
        .put("processSessionId", processSessionId)

    companion object {
        fun fromJson(json: JSONObject): ProotViewLease = ProotViewLease(
            ownerId = json.getString("ownerId"),
            mode = ProotViewLeaseMode.valueOf(json.getString("mode")),
            acquiredAtUnixMs = json.getLong("acquiredAtUnixMs"),
            processSessionId = json.optString("processSessionId")
        )
    }
}

data class ProotViewRecord(
    val viewId: String,
    val containerId: String,
    val baseRootPath: String,
    val upperRootPath: String,
    val whiteoutRootPath: String,
    val controlFilePath: String,
    val parentViewId: String?,
    val purpose: String,
    val state: ProotViewState,
    val createdAtUnixMs: Long,
    val updatedAtUnixMs: Long,
    val leases: List<ProotViewLease> = emptyList(),
    // 该 View 归属的控制面环境。prepare 时写入，commit/restoreParent 校验归属与提交环境一致，
    // 防止 A 的子 View 被提交成 B 的头。旧记录缺失时视为 default，向后兼容。
    val environmentId: String = ProotViewStore.DEFAULT_ENVIRONMENT_ID
) {
    fun toJson(): JSONObject = JSONObject()
        .put("viewId", viewId)
        .put("containerId", containerId)
        .put("baseRootPath", baseRootPath)
        .put("upperRootPath", upperRootPath)
        .put("whiteoutRootPath", whiteoutRootPath)
        .put("controlFilePath", controlFilePath)
        .put("parentViewId", parentViewId ?: JSONObject.NULL)
        .put("purpose", purpose)
        .put("state", state.name)
        .put("createdAtUnixMs", createdAtUnixMs)
        .put("updatedAtUnixMs", updatedAtUnixMs)
        .put("environmentId", environmentId)
        .put("leases", JSONArray().also { array -> leases.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): ProotViewRecord {
            val leaseArray = json.optJSONArray("leases") ?: JSONArray()
            return ProotViewRecord(
                viewId = json.getString("viewId"),
                containerId = json.getString("containerId"),
                baseRootPath = json.getString("baseRootPath"),
                upperRootPath = json.getString("upperRootPath"),
                whiteoutRootPath = json.getString("whiteoutRootPath"),
                controlFilePath = json.getString("controlFilePath"),
                parentViewId = json.optString("parentViewId")
                    .takeIf { it.isNotBlank() && it != "null" },
                purpose = json.optString("purpose", "unspecified"),
                state = ProotViewState.valueOf(json.getString("state")),
                createdAtUnixMs = json.getLong("createdAtUnixMs"),
                updatedAtUnixMs = json.getLong("updatedAtUnixMs"),
                environmentId = json.optString("environmentId")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: ProotViewStore.DEFAULT_ENVIRONMENT_ID,
                leases = buildList {
                    for (index in 0 until leaseArray.length()) {
                        add(ProotViewLease.fromJson(leaseArray.getJSONObject(index)))
                    }
                }
            )
        }
    }
}

data class ProotViewBinding(
    val viewId: String,
    val baseRootPath: String,
    val upperRootPath: String,
    val whiteoutRootPath: String,
    val controlFilePath: String,
    val writable: Boolean,
    val scopeRootPaths: List<String> = emptyList(),
    val parentViewIds: List<String> = emptyList(),
    // 本绑定所属的控制面环境。运行入口可据此判断绑定是否满足请求的环境身份，
    // 避免显式请求 A 时静默回退到 default 或 B。旧 binding 缺失时视为 default。
    val environmentId: String = ProotViewStore.DEFAULT_ENVIRONMENT_ID
) {
    fun environment(): Map<String, String> = mapOf(
        ENV_CONTROL_PATH to controlFilePath,
        ENV_VIEW_ID to viewId,
        ENV_ENVIRONMENT_ID to environmentId
    )

    fun toJson(): JSONObject = JSONObject()
        .put("schema", CONTROL_SCHEMA)
        .put("viewId", viewId)
        .put("baseRootPath", baseRootPath)
        .put("upperRootPath", upperRootPath)
        .put("whiteoutRootPath", whiteoutRootPath)
        .put("controlFilePath", controlFilePath)
        .put("writable", writable)
        .put("scopeRootPaths", JSONArray(scopeRootPaths))
        .put("parentViewIds", JSONArray(parentViewIds))
        .put("environmentId", environmentId)

    companion object {
        const val CONTROL_SCHEMA = "kf_proot_view_v1"
        const val ENV_CONTROL_PATH = "KF_PROOT_VIEW_CONTROL_PATH"
        const val ENV_VIEW_ID = "KF_PROOT_VIEW_ID"
        const val ENV_ENVIRONMENT_ID = "KF_PROOT_ENVIRONMENT_ID"
    }
}

data class ProotViewCatalogSnapshot(
    val containerId: String,
    val baseRootPath: String,
    val scopeRootPaths: List<String>,
    val currentViewId: String?,
    val views: List<ProotViewRecord>,
    // 同一不可变 Base 上每个环境拥有独立的头指针。default 始终存在，其值与 currentViewId 一致；
    // 这是向后兼容旧单 current 的迁移落点。读取旧 catalog 时该字段为空，由 recover 迁移。
    val environmentCurrents: Map<String, String> = emptyMap()
) {
    val current: ProotViewRecord?
        get() = views.firstOrNull { it.viewId == currentViewId }

    /**
     * 返回指定环境的当前 View 头。环境不存在或指针悬空时返回 null。
     */
    fun environmentCurrent(environmentId: String): ProotViewRecord? {
        val viewId = environmentCurrents[environmentId] ?: return null
        return views.firstOrNull { it.viewId == viewId }
    }
}

data class ProotViewStorageStats(
    val viewId: String,
    val blockFileCount: Int,
    val blockDeltaLogicalBytes: Long,
    val blockDeltaAllocatedBytes: Long?,
    val blockMetadataBytes: Long,
    val blockMetadataAllocatedBytes: Long?,
    val regularUpperLogicalBytes: Long,
    val regularUpperAllocatedBytes: Long?,
    val whiteoutEntryCount: Int,
    val temporaryEntryCount: Int
) {
    val totalLogicalBytes: Long
        get() = blockDeltaLogicalBytes + blockMetadataBytes + regularUpperLogicalBytes

    val totalAllocatedBytes: Long?
        get() = if (blockDeltaAllocatedBytes == null || blockMetadataAllocatedBytes == null ||
            regularUpperAllocatedBytes == null
        ) {
            null
        } else {
            blockDeltaAllocatedBytes + blockMetadataAllocatedBytes + regularUpperAllocatedBytes
        }
}

/**
 * Android 控制面访问当前 PRoot View 中某个受保护路径时使用的投影。
 *
 * [visibleFile] 是新启动的 PRoot 进程当前实际可见的文件；文件不存在或已被 whiteout
 * 遮蔽时为 null。[writableFile] 始终指向当前 Upper 中的 copy-up 目标，调用方不能直接
 * 写入父层或 Base。
 */
data class ProotViewPathProjection(
    val relativePath: String,
    val visibleFile: File?,
    val writableFile: File,
    val layerRootPaths: List<String>
)

/**
 * Android 侧 PRoot View 状态拥有者。
 *
 * 这里只管理 View 生命周期、持久化、引用和启动绑定；文件访问热路径全部留在 PRoot。
 * catalog 与 current 使用同目录临时文件、fd.sync 和原子替换，WAL 只记录状态切换意图，
 * 不记录资源、npm、curl 等上层业务字段。
 */
class ProotViewStore internal constructor(
    private val rootDirectory: File,
    private val containerId: String,
    baseRootDirectory: File,
    scopeRootDirectories: List<File> = listOf(baseRootDirectory),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val processSessionId: String = PROCESS_SESSION_ID,
    private val allocatedBytes: (File) -> Long? = ::allocatedBytesOnDisk
) {
    private val baseRootDirectory = baseRootDirectory.absoluteFile
    private val scopeRootDirectories = scopeRootDirectories
        .map(File::getAbsoluteFile)
        .distinctBy(File::getAbsolutePath)
    private val catalogFile = File(rootDirectory, CATALOG_FILE_NAME)
    private val currentFile = File(rootDirectory, CURRENT_FILE_NAME)
    private val walFile = File(rootDirectory, WAL_FILE_NAME)
    private val activationFile = File(rootDirectory, ACTIVATION_FILE_NAME)
    private val environmentsFile = File(rootDirectory, ENVIRONMENTS_FILE_NAME)
    private val activeEnvironmentFile = File(rootDirectory, ACTIVE_ENVIRONMENT_FILE_NAME)
    private val viewsDirectory = File(rootDirectory, VIEWS_DIRECTORY_NAME)
    private val catalogGenerationKey = catalogFile.absolutePath
    @Volatile
    private var cachedCatalog: ProotViewCatalogSnapshot? = null
    @Volatile
    private var observedCatalogGeneration: Long = 0L

    @Synchronized
    fun ensureInitialized(): ProotViewCatalogSnapshot {
        require(baseRootDirectory.isDirectory) {
            "PRoot View Base 不存在：${baseRootDirectory.absolutePath}"
        }
        require(scopeRootDirectories.isNotEmpty()) { "PRoot View 至少需要一个保护作用域" }
        scopeRootDirectories.forEach { scope ->
            require(scope == baseRootDirectory || scope.toPath().startsWith(baseRootDirectory.toPath())) {
                "PRoot View 作用域不在 Base 内：${scope.absolutePath}"
            }
        }
        ensureStoreDirectories()
        cachedCatalogIfFresh()?.let { cached ->
            scopeRootDirectories.forEach { scope ->
                require(scope.isDirectory) { "PRoot View 作用域不存在：${scope.absolutePath}" }
            }
            return cached
        }
        recoverInternal()?.let { recovered ->
            // 已封存：scope 目录必须仍然存在（封存后不应被外部删除）。
            scopeRootDirectories.forEach { scope ->
                require(scope.isDirectory) { "PRoot View 作用域不存在：${scope.absolutePath}" }
            }
            return recovered
        }
        cleanupOrphanViewDirectories(emptySet())
        // 首次封存：准备 scope 目录作为 Base 初始结构；之后对这些目录的写入进 View Upper。
        scopeRootDirectories.forEach { scope ->
            if (!scope.isDirectory) scope.mkdirs()
            require(scope.isDirectory) { "PRoot View 作用域无法创建：${scope.absolutePath}" }
        }

        val timestamp = now()
        val viewId = nextViewId("initial")
        val record = createPhysicalView(
            viewId = viewId,
            parentViewId = null,
            purpose = "initial-base-view",
            state = ProotViewState.CURRENT,
            timestamp = timestamp,
            parentLayers = emptyList()
        )
        val snapshot = ProotViewCatalogSnapshot(
            containerId = containerId,
            baseRootPath = baseRootDirectory.absolutePath,
            scopeRootPaths = scopeRootDirectories.map(File::getAbsolutePath),
            currentViewId = viewId,
            views = listOf(record),
            environmentCurrents = mapOf(DEFAULT_ENVIRONMENT_ID to viewId)
        )
        transact("INITIALIZE", viewId) {
            writeCatalog(snapshot)
            writeCurrentPointer(viewId)
            writeEnvironmentPointers(snapshot.environmentCurrents)
            writeActiveEnvironment(DEFAULT_ENVIRONMENT_ID)
        }
        return snapshot
    }

    @Synchronized
    fun recover(): ProotViewCatalogSnapshot? {
        ensureStoreDirectories()
        cachedCatalog = null
        return recoverInternal()
    }

    /**
     * 返回本进程已经恢复过的 catalog。首次访问仍执行完整恢复，之后的普通查询不再重复扫描临时文件和物理结构。
     * 崩溃恢复或工程显式校验继续调用 [recover]。
     */
    @Synchronized
    fun catalogSnapshot(): ProotViewCatalogSnapshot = requireCatalog()

    @Synchronized
    fun prepare(
        purpose: String,
        parentViewId: String? = null,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): ProotViewRecord {
        requireEnvironmentId(environmentId)
        val snapshot = ensureInitialized()
        // 未显式指定父代时，从指定环境的当前头派生；环境尚无头时建立自己的根 View，
        // 直接覆盖同一不可变 Base。根 View 也是可写 Upper，绝不能借用 default 或其他环境的根，
        // 否则新环境会继承别人的用户变化。
        val parent = parentViewId
            ?: snapshot.environmentCurrents[environmentId]
        require(parent == null || snapshot.views.any { it.viewId == parent }) {
            "PRoot View 父代不存在：$parent"
        }
        val timestamp = now()
        val viewId = nextViewId("view")
        val parentLayers = resolveAncestorChain(snapshot, parent)
        var record: ProotViewRecord? = null
        transact("PREPARE", viewId) {
            record = createPhysicalView(
                viewId = viewId,
                parentViewId = parent,
                purpose = purpose.trim().ifBlank { "unspecified" },
                state = ProotViewState.PREPARING,
                timestamp = timestamp,
                parentLayers = parentLayers,
                environmentId = environmentId
            )
            writeCatalog(snapshot.copy(views = snapshot.views + requireNotNull(record)))
        }
        return requireNotNull(record)
    }

    @Synchronized
    fun verify(viewId: String): ProotViewRecord {
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        require(record.state == ProotViewState.PREPARING || record.state == ProotViewState.READY) {
            "PRoot View 当前状态不可验证：${record.state}"
        }
        require(isPhysicalViewValid(record, snapshot)) { "PRoot View 文件结构不完整：$viewId" }
        if (record.state == ProotViewState.READY) return record
        val updated = record.copy(state = ProotViewState.READY, updatedAtUnixMs = now())
        transact("VERIFY", viewId) {
            writeCatalog(snapshot.replace(updated))
        }
        return updated
    }

    @Synchronized
    fun acquireLease(
        viewId: String,
        ownerId: String,
        mode: ProotViewLeaseMode
    ): ProotViewRecord {
        requireOwnerId(ownerId)
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        require(record.state != ProotViewState.BROKEN && record.state != ProotViewState.DISCARDED) {
            "PRoot View 不可获取引用：${record.state}"
        }
        record.leases.firstOrNull { it.ownerId == ownerId }?.let { existing ->
            require(existing.mode == mode) { "同一 owner 不得改变现有引用模式" }
            return record
        }
        val conflict = when (mode) {
            ProotViewLeaseMode.WRITER -> record.leases.isNotEmpty()
            ProotViewLeaseMode.READER -> record.leases.any { it.mode == ProotViewLeaseMode.WRITER }
        }
        require(!conflict) { "PRoot View 已被其他 owner 占用：$viewId" }
        val updated = record.copy(
            updatedAtUnixMs = now(),
            leases = record.leases + ProotViewLease(
                ownerId = ownerId,
                mode = mode,
                acquiredAtUnixMs = now(),
                processSessionId = processSessionId
            )
        )
        transact("ACQUIRE_${mode.name}", viewId, ownerId) {
            writeCatalog(snapshot.replace(updated))
        }
        return updated
    }

    @Synchronized
    fun releaseLease(viewId: String, ownerId: String): ProotViewRecord {
        requireOwnerId(ownerId)
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        if (record.leases.none { it.ownerId == ownerId }) return record
        val updated = record.copy(
            updatedAtUnixMs = now(),
            leases = record.leases.filterNot { it.ownerId == ownerId }
        )
        transact("RELEASE", viewId, ownerId) {
            writeCatalog(snapshot.replace(updated))
        }
        return updated
    }

    @Synchronized
    fun commit(
        viewId: String,
        ownerId: String,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): ProotViewRecord {
        requireOwnerId(ownerId)
        requireEnvironmentId(environmentId)
        val snapshot = requireCatalog()
        val target = snapshot.requireView(viewId)
        require(target.state == ProotViewState.READY || target.state == ProotViewState.CURRENT) {
            "PRoot View 未通过验证：${target.state}"
        }
        require(target.leases.any {
            it.ownerId == ownerId && it.mode == ProotViewLeaseMode.WRITER
        }) { "提交前必须由同一 owner 持有 writer 引用" }
        require(validateBlockDataPlane(target, snapshot)) {
            "PRoot View 块级数据面不完整：$viewId"
        }
        val existingHead = snapshot.environmentCurrents[environmentId]
        if (existingHead == viewId && target.state == ProotViewState.CURRENT) {
            return target
        }
        // 提交的 View 必须归属本次提交的环境：A 的子 View 不能提交成 B 的头。
        // 旧记录没有 environmentId 字段时视为 default，保证现有 default 流程不回归。
        require(target.environmentId == environmentId) {
            "PRoot View 不属于本环境：view=${target.environmentId}, environment=$environmentId"
        }
        // 新头只能属于本次提交的环境，不能被其他环境登记为头（避免头指针共享）。
        require(snapshot.environmentCurrents.none { it.value == viewId && it.key != environmentId }) {
            "PRoot View 已被其他环境登记为头：$viewId"
        }
        // 环境已有头：只能线性提交该头的直接子代（跨环境提交别人的子 View 被拒绝）。
        // 环境尚无头：第一代必须是该环境自己的根 View，直接覆盖不可变 Base。
        // 任何现有根 View 都包含可写 Upper，不能当成共享 Base 复用。
        if (existingHead != null) {
            require(target.parentViewId == existingHead) {
                "PRoot View 只能从本环境当前代次线性提交：environment=$environmentId"
            }
        } else {
            require(target.parentViewId == null) {
                "新环境第一代必须是直接覆盖 Base 的独立根 View：$environmentId"
            }
        }

        val timestamp = now()
        val previousHead = existingHead
        val updatedViews = snapshot.views.map { record ->
            when {
                record.viewId == viewId -> record.copy(
                    state = ProotViewState.CURRENT,
                    updatedAtUnixMs = timestamp
                )
                // 只降本环境旧头为 READY；其他环境的 current 头保持不变。旧头仍可作为祖先引用。
                record.viewId == previousHead && record.state == ProotViewState.CURRENT ->
                    record.copy(state = ProotViewState.READY, updatedAtUnixMs = timestamp)
                else -> record
            }
        }
        val updatedEnvironmentCurrents = snapshot.environmentCurrents.toMutableMap().apply {
            put(environmentId, viewId)
        }
        // default 环境的头继续作为向后兼容的 currentViewId / current.json。
        val updatedGlobalCurrent = updatedEnvironmentCurrents[DEFAULT_ENVIRONMENT_ID]
            ?: updatedEnvironmentCurrents.values.firstOrNull()
            ?: snapshot.currentViewId
        val updatedSnapshot = snapshot.copy(
            currentViewId = updatedGlobalCurrent,
            views = updatedViews,
            environmentCurrents = updatedEnvironmentCurrents
        )
        transact("COMMIT", viewId, ownerId) {
            // current.json 和 environments.json 是切换真相；catalog 若晚一步崩溃，
            // recover 会按这两个指针对齐。
            if (environmentId == DEFAULT_ENVIRONMENT_ID) {
                writeCurrentPointer(viewId)
            }
            writeEnvironmentPointers(updatedEnvironmentCurrents)
            writeCatalog(updatedSnapshot)
        }
        return updatedSnapshot.requireView(viewId)
    }

    @Synchronized
    fun discard(viewId: String): ProotViewCatalogSnapshot {
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        // 任何环境的头都不允许废弃；环境头切换只能走 commit/restoreParent。
        val protectedHeads = snapshot.environmentHeadSet()
        require(viewId !in protectedHeads) {
            "不能废弃环境头 PRoot View：$viewId"
        }
        require(viewId != snapshot.currentViewId) {
            "不能废弃当前 PRoot View：$viewId"
        }
        require(record.leases.isEmpty()) { "仍有进程引用，不能废弃 PRoot View" }
        require(snapshot.views.none {
            it.state != ProotViewState.DISCARDED && it.parentViewId == viewId
        }) { "仍有子 View 依赖，不能废弃 PRoot View" }
        val discarded = record.copy(
            state = ProotViewState.DISCARDED,
            updatedAtUnixMs = now()
        )
        var updated = snapshot.replace(discarded)
        transact("DISCARD", viewId) {
            writeCatalog(updated)
            deleteViewDirectory(record)
            updated = updated.copy(views = updated.views.filterNot { it.viewId == viewId })
            writeCatalog(updated)
        }
        return updated
    }

    @Synchronized
    fun enable(): ProotViewBinding {
        val binding = activeBinding()
        val activation = JSONObject()
            .put("schema", ACTIVATION_SCHEMA)
            .put("containerId", containerId)
            .put("enabled", true)
            .put("enabledAtUnixMs", now())
        writeAtomic(activationFile, activation.toString(2) + "\n")
        return binding
    }

    @Synchronized
    fun disable() {
        if (!activationFile.exists()) return
        val disabled = JSONObject()
            .put("schema", ACTIVATION_SCHEMA)
            .put("containerId", containerId)
            .put("enabled", false)
            .put("disabledAtUnixMs", now())
        writeAtomic(activationFile, disabled.toString(2) + "\n")
    }

    @Synchronized
    fun isEnabled(): Boolean = runCatching {
        val json = JSONObject(activationFile.readText())
        json.optString("schema") == ACTIVATION_SCHEMA &&
            json.optString("containerId") == containerId &&
            json.optBoolean("enabled", false)
    }.getOrDefault(false)

    @Synchronized
    fun currentBinding(environmentId: String = DEFAULT_ENVIRONMENT_ID): ProotViewBinding {
        requireEnvironmentId(environmentId)
        val snapshot = requireCatalog()
        val pointerViewId = if (environmentId == DEFAULT_ENVIRONMENT_ID) {
            // default 环境仍以 current.json 为切换真相，与旧读取保持一致。
            readCurrentViewId().also {
                require(it == snapshot.currentViewId) { "PRoot View current 与 catalog 不一致" }
            }
        } else {
            readEnvironmentPointers()[environmentId].also {
                require(it == snapshot.environmentCurrents[environmentId]) {
                    "PRoot View 环境指针与 catalog 不一致：$environmentId"
                }
            }
        }
        val current = snapshot.environmentCurrent(environmentId)
            ?: (if (environmentId == DEFAULT_ENVIRONMENT_ID) snapshot.current else null)
            ?: error("PRoot View 环境头不存在：$environmentId")
        require(pointerViewId == current.viewId) {
            "PRoot View 环境头与持久指针不一致：$environmentId"
        }
        require(current.state == ProotViewState.CURRENT && isPhysicalViewValid(current, snapshot)) {
            "PRoot View 环境头不可启动：${current.viewId}"
        }
        require(current.leases.none { it.mode == ProotViewLeaseMode.WRITER }) {
            "PRoot View 环境头正在执行独占切换：${current.viewId}"
        }
        return current.toBinding(snapshot)
    }

    /**
     * 把 Base 命名空间中的单个路径投影到当前 View。
     *
     * 读取顺序与 native 数据面一致：当前 whiteout / Upper、父层 whiteout / Upper、Base。
     * 本方法只解析单个路径，不承担 overlay 目录枚举；调用方写入时必须使用
     * [ProotViewPathProjection.writableFile]。
     */
    @Synchronized
    fun projectPath(
        basePath: File,
        environmentId: String = activeEnvironmentId()
    ): ProotViewPathProjection {
        val snapshot = requireCatalog()
        val binding = currentBinding(environmentId)
        val current = snapshot.requireView(binding.viewId)
        require(current.state == ProotViewState.CURRENT && isPhysicalViewValid(current, snapshot)) {
            "PRoot View 环境头不可投影：$environmentId:${current.viewId}"
        }
        require(current.leases.none { it.mode == ProotViewLeaseMode.WRITER }) {
            "PRoot View current 正在执行独占切换：${current.viewId}"
        }

        val canonicalBase = baseRootDirectory.canonicalFile
        val canonicalTarget = basePath.canonicalFile
        require(canonicalTarget.toPath().startsWith(canonicalBase.toPath())) {
            "PRoot View 投影路径不在 Base 内：${canonicalTarget.absolutePath}"
        }
        require(scopeRootDirectories.any { scope ->
            val canonicalScope = scope.canonicalFile.toPath()
            canonicalTarget.toPath() == canonicalScope || canonicalTarget.toPath().startsWith(canonicalScope)
        }) { "PRoot View 投影路径不在保护作用域：${canonicalTarget.absolutePath}" }

        val relative = canonicalTarget.relativeTo(canonicalBase).invariantSeparatorsPath
        require(relative.isNotBlank() && relative != ".") { "PRoot View 不允许投影 Base 根目录" }
        val layers = listOf(current) + resolveAncestorChain(snapshot, current.parentViewId)
        var visible: File? = null
        for (layer in layers) {
            if (isWhiteouted(File(layer.whiteoutRootPath), relative)) {
                visible = null
                break
            }
            val candidate = File(layer.upperRootPath, relative)
            if (candidate.exists()) {
                visible = candidate
                break
            }
        }
        if (visible == null && layers.none { isWhiteouted(File(it.whiteoutRootPath), relative) }) {
            visible = canonicalTarget.takeIf(File::exists)
        }
        return ProotViewPathProjection(
            relativePath = relative,
            visibleFile = visible,
            writableFile = File(current.upperRootPath, relative),
            layerRootPaths = layers.map(ProotViewRecord::upperRootPath) + canonicalBase.absolutePath
        )
    }

    /**
     * 返回当前所有环境头指针的只读快照（environmentId -> viewId）。
     */
    @Synchronized
    fun environmentCurrents(): Map<String, String> {
        val snapshot = requireCatalog()
        return snapshot.environmentCurrents.toMap()
    }

    /**
     * 返回显式 View 工程入口选择的活跃环境。普通 PRoot 启动不读取该指针。
     * 旧版本没有指针或指针损坏时，recover 会确定性回到合法 default。
     */
    @Synchronized
    fun activeEnvironmentId(): String {
        val snapshot = requireCatalog()
        val active = readActiveEnvironmentId()
            ?: error("PRoot View 活跃环境指针不存在")
        require(active in snapshot.environmentCurrents) {
            "PRoot View 活跃环境不存在：$active"
        }
        return active
    }

    @Synchronized
    fun activeBinding(): ProotViewBinding = currentBinding(activeEnvironmentId())

    /** 原子切换显式 View 工程入口的活跃环境；进程收口属于 platform 编排层。 */
    @Synchronized
    fun switchActiveEnvironment(environmentId: String): ProotViewBinding {
        requireEnvironmentId(environmentId)
        require(environmentId in environmentCurrents()) {
            "PRoot View 环境不存在：$environmentId"
        }
        val binding = currentBinding(environmentId)
        if (readActiveEnvironmentId() != environmentId) {
            transact("SWITCH_ACTIVE_ENVIRONMENT", binding.viewId, environmentId) {
                writeActiveEnvironment(environmentId)
            }
        }
        return binding
    }

    @Synchronized
    fun binding(viewId: String): ProotViewBinding {
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        require(record.state != ProotViewState.BROKEN && record.state != ProotViewState.DISCARDED) {
            "PRoot View 不可启动：${record.state}"
        }
        require(isPhysicalViewValid(record, snapshot)) { "PRoot View 文件结构不完整：$viewId" }
        return record.toBinding(snapshot)
    }

    @Synchronized
    fun storageStats(viewId: String): ProotViewStorageStats {
        val snapshot = requireCatalog()
        val record = snapshot.requireView(viewId)
        require(record.state != ProotViewState.BROKEN && record.state != ProotViewState.DISCARDED) {
            "PRoot View 不可统计：${record.state}"
        }
        return inspectStorage(record, snapshot, requireValid = true)
    }

    @Synchronized
    fun restoreParent(
        viewId: String,
        ownerId: String,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): ProotViewRecord {
        requireOwnerId(ownerId)
        requireEnvironmentId(environmentId)
        val snapshot = requireCatalog()
        val current = snapshot.requireView(viewId)
        val environmentHead = snapshot.environmentCurrents[environmentId] ?: snapshot.currentViewId
        require(environmentHead == viewId && current.state == ProotViewState.CURRENT) {
            "只能恢复本环境当前 PRoot View：environment=$environmentId"
        }
        require(current.leases.any {
            it.ownerId == ownerId && it.mode == ProotViewLeaseMode.WRITER
        }) { "恢复前必须由同一 owner 持有 writer 引用" }
        val parentId = current.parentViewId ?: error("当前 PRoot View 没有可恢复父代")
        val parent = snapshot.requireView(parentId)
        require(isPhysicalViewValid(parent, snapshot)) {
            "PRoot View 恢复父代文件结构不完整：$parentId"
        }
        require(parent.state == ProotViewState.READY || parent.state == ProotViewState.CURRENT) {
            "PRoot View 恢复父代不可用：${parent.state}"
        }
        // 恢复到的父代如果属于其他环境，只能是可以共享的 Base 投影（无父代 view）；
        // 非 initial 的其他环境头不允许共享，避免跨环境污染。
        val parentOwnedByOther = snapshot.environmentCurrents
            .filter { (env, head) -> env != environmentId && head == parentId }
        require(parentOwnedByOther.isEmpty() || parent.parentViewId == null) {
            "PRoot View 恢复目标已被其他环境登记为非共享头：$parentId"
        }
        val timestamp = now()
        val updatedViews = snapshot.views.map { record ->
            when (record.viewId) {
                viewId -> record.copy(state = ProotViewState.READY, updatedAtUnixMs = timestamp)
                parentId -> record.copy(state = ProotViewState.CURRENT, updatedAtUnixMs = timestamp)
                else -> record
            }
        }
        val updatedEnvironmentCurrents = snapshot.environmentCurrents.toMutableMap().apply {
            put(environmentId, parentId)
        }
        val updatedGlobalCurrent = updatedEnvironmentCurrents[DEFAULT_ENVIRONMENT_ID]
            ?: updatedEnvironmentCurrents.values.firstOrNull()
            ?: parentId
        val updated = snapshot.copy(
            currentViewId = updatedGlobalCurrent,
            views = updatedViews,
            environmentCurrents = updatedEnvironmentCurrents
        )
        transact("RESTORE_PARENT", parentId, ownerId) {
            if (environmentId == DEFAULT_ENVIRONMENT_ID) {
                writeCurrentPointer(parentId)
            }
            writeEnvironmentPointers(updatedEnvironmentCurrents)
            writeCatalog(updated)
        }
        return updated.requireView(parentId)
    }

    private fun recoverInternal(): ProotViewCatalogSnapshot? {
        val removedTemps = cleanupTemporaryFiles(rootDirectory)
        val original = readCatalog() ?: return null
        require(original.containerId == containerId) { "PRoot View catalog 容器不匹配" }
        require(File(original.baseRootPath).absoluteFile == baseRootDirectory) {
            "PRoot View catalog Base 不匹配"
        }
        require(original.scopeRootPaths.map(::File).map(File::getAbsoluteFile) == scopeRootDirectories) {
            "PRoot View catalog 作用域不匹配"
        }

        val pointer = readCurrentViewId()
        val persistedEnvironments = readEnvironmentPointers()
        val persistedActiveEnvironment = readActiveEnvironmentId()
        val physicallyValidViewIds = original.views.filter { record ->
            record.state != ProotViewState.DISCARDED &&
                isPhysicalViewValid(record, original)
        }.mapTo(mutableSetOf()) { it.viewId }
        val validViews = original.views.map { record ->
            val currentProcessLeases = record.leases.filter {
                it.processSessionId == processSessionId
            }
            val lineageValid = record.viewId in physicallyValidViewIds && runCatching {
                resolveAncestorChain(original, record.parentViewId).all {
                    it.viewId in physicallyValidViewIds
                }
            }.getOrDefault(false)
            if (record.state != ProotViewState.DISCARDED && !lineageValid) {
                record.copy(
                    state = ProotViewState.BROKEN,
                    updatedAtUnixMs = now(),
                    leases = currentProcessLeases
                )
            } else {
                record.copy(leases = currentProcessLeases)
            }
        }
        val validViewById = validViews.associateBy { it.viewId }
        // 判断一个 view 是否可作为环境头：物理有效、祖先链有效、且状态为 READY/CURRENT。
        fun isUsableHead(candidate: ProotViewRecord?): Boolean {
            val record = candidate ?: return false
            return record.viewId in validViewById &&
                (record.state == ProotViewState.READY || record.state == ProotViewState.CURRENT)
        }
        // 沿合法祖先链找最近的可作为头的 view；找不到返回 null。候选必须归属本环境，
        // 根 View 同样是环境私有的可写层，绝不因为 parentViewId=null 而跨环境借用。
        fun firstUsableAncestor(startViewId: String?, environmentId: String): String? {
            var cursor = startViewId
            val visited = mutableSetOf<String>()
            while (cursor != null) {
                if (!visited.add(cursor)) return null
                val record = validViewById[cursor] ?: return null
                if (isUsableHead(record) && record.environmentId == environmentId) return cursor
                cursor = record.parentViewId
            }
            return null
        }
        // 迁移旧单 current：catalog 缺少 environmentCurrents 时，把全局 current 当作 default 头。
        // 旧 catalog 没有环境概念，全局 current 天然属于 default；候选只用持久指针和全局 current，
        // 并沿其祖先链找最近有效头，绝不借用其他（此时也不存在其他）环境的 view。
        val migratedEnvironments = if (original.environmentCurrents.isEmpty()) {
            val rootCandidate = sequenceOf(
                pointer,
                persistedEnvironments[DEFAULT_ENVIRONMENT_ID],
                original.currentViewId
            ).filterNotNull().firstOrNull { candidate ->
                isUsableHead(validViewById[candidate]) &&
                    validViewById[candidate]?.environmentId == DEFAULT_ENVIRONMENT_ID
            }
            val defaultHead = rootCandidate?.let { firstUsableAncestor(it, DEFAULT_ENVIRONMENT_ID) }
            if (defaultHead != null) mapOf(DEFAULT_ENVIRONMENT_ID to defaultHead) else emptyMap()
        } else {
            original.environmentCurrents
        }
        // 每个环境只能用自己的持久指针、catalog 记录以及合法祖先链恢复；绝不回退到其他环境的
        // CURRENT/READY。非 default 环境无法恢复时移除该环境，default 失败则回退到自己的合法根 View。
        val resolvedEnvironments = migratedEnvironments.mapNotNull { (envId, recordedHead) ->
            val pointerCandidate = if (envId == DEFAULT_ENVIRONMENT_ID) pointer
                else persistedEnvironments[envId]
            val persistedCandidate = persistedEnvironments[envId]
            fun belongsToEnv(candidate: String?): Boolean {
                val record = candidate?.let { validViewById[it] } ?: return false
                if (!isUsableHead(record)) return false
                return record.environmentId == envId
            }
            val selected = sequenceOf(pointerCandidate, persistedCandidate, recordedHead)
                .filterNotNull()
                .firstOrNull(::belongsToEnv)
                ?: firstUsableAncestor(recordedHead, envId)
                ?: firstUsableAncestor(persistedCandidate, envId)
                ?: firstUsableAncestor(pointerCandidate, envId)
            if (selected != null) {
                envId to selected
            } else if (envId == DEFAULT_ENVIRONMENT_ID) {
                // default 最后只回退到自己直接覆盖 Base 的根 View，保证兼容入口仍有可启动头。
                val defaultRoot = validViews.firstOrNull {
                    it.parentViewId == null && it.environmentId == DEFAULT_ENVIRONMENT_ID &&
                        isUsableHead(it)
                }
                defaultRoot?.let { envId to it.viewId }
            } else {
                null
            }
        }.toMap()
        val environmentHeads = resolvedEnvironments.values.toSet()
        // 全局 current 退化为 default 头（兼容旧读取）；default 缺失时不再借用其他环境头。
        val selectedCurrent = resolvedEnvironments[DEFAULT_ENVIRONMENT_ID] ?: original.currentViewId
        val aligned = validViews.map { record ->
            val shouldBeCurrent = record.viewId in environmentHeads
            when {
                shouldBeCurrent && record.state != ProotViewState.CURRENT ->
                    record.copy(state = ProotViewState.CURRENT, updatedAtUnixMs = now())
                !shouldBeCurrent && record.state == ProotViewState.CURRENT ->
                    record.copy(state = ProotViewState.READY, updatedAtUnixMs = now())
                else -> record
            }
        }.toMutableList()
        aligned.filter { it.state == ProotViewState.DISCARDED && it.leases.isEmpty() }
            .forEach { record ->
                deleteViewDirectory(record)
                aligned.remove(record)
            }
        val recovered = original.copy(
            currentViewId = selectedCurrent,
            views = aligned,
            environmentCurrents = resolvedEnvironments
        )
        val resolvedActiveEnvironment = persistedActiveEnvironment
            ?.takeIf { it in resolvedEnvironments }
            ?: DEFAULT_ENVIRONMENT_ID.takeIf { it in resolvedEnvironments }
        cleanupOrphanViewDirectories(recovered.views.mapTo(mutableSetOf()) { it.viewId })
        val changed = recovered != original || removedTemps > 0 ||
            pointer != selectedCurrent ||
            persistedEnvironments != resolvedEnvironments ||
            persistedActiveEnvironment != resolvedActiveEnvironment
        if (changed) {
            transact("RECOVER", selectedCurrent ?: "none") {
                writeCatalog(recovered)
                if (resolvedEnvironments.isNotEmpty()) {
                    writeEnvironmentPointers(resolvedEnvironments)
                }
                if (selectedCurrent != null && resolvedEnvironments.containsKey(DEFAULT_ENVIRONMENT_ID)) {
                    writeCurrentPointer(selectedCurrent)
                }
                if (resolvedActiveEnvironment != null) {
                    writeActiveEnvironment(resolvedActiveEnvironment)
                }
            }
        }
        observedCatalogGeneration = catalogGeneration(catalogGenerationKey)
        cachedCatalog = recovered
        return recovered
    }

    private fun createPhysicalView(
        viewId: String,
        parentViewId: String?,
        purpose: String,
        state: ProotViewState,
        timestamp: Long,
        parentLayers: List<ProotViewRecord>,
        environmentId: String = DEFAULT_ENVIRONMENT_ID
    ): ProotViewRecord {
        requireSafeId(viewId, "viewId")
        val directory = File(viewsDirectory, viewId)
        val upper = File(directory, "upper")
        val whiteout = File(directory, "whiteout")
        val control = File(directory, "control.conf")
        require(upper.mkdirs() || upper.isDirectory) { "无法创建 View Upper：${upper.absolutePath}" }
        require(whiteout.mkdirs() || whiteout.isDirectory) {
            "无法创建 View Whiteout：${whiteout.absolutePath}"
        }
        val record = ProotViewRecord(
            viewId = viewId,
            containerId = containerId,
            baseRootPath = baseRootDirectory.absolutePath,
            upperRootPath = upper.absolutePath,
            whiteoutRootPath = whiteout.absolutePath,
            controlFilePath = control.absolutePath,
            parentViewId = parentViewId,
            purpose = purpose,
            state = state,
            createdAtUnixMs = timestamp,
            updatedAtUnixMs = timestamp,
            environmentId = environmentId
        )
        writeAtomic(control, renderControl(record, parentLayers))
        return record
    }

    private fun resolveAncestorChain(
        snapshot: ProotViewCatalogSnapshot,
        parentViewId: String?
    ): List<ProotViewRecord> {
        if (parentViewId == null) return emptyList()
        val result = mutableListOf<ProotViewRecord>()
        val visited = mutableSetOf<String>()
        var cursor: String? = parentViewId
        while (cursor != null) {
            require(visited.add(cursor)) { "PRoot View 父代形成循环：$cursor" }
            require(result.size < MAX_PARENT_LAYERS) {
                "PRoot View 父层超过上限：$MAX_PARENT_LAYERS"
            }
            val record = snapshot.views.firstOrNull { it.viewId == cursor }
                ?: error("PRoot View 父代不存在：$cursor")
            require(record.state != ProotViewState.BROKEN &&
                record.state != ProotViewState.DISCARDED
            ) { "PRoot View 父代不可使用：${record.state}" }
            result += record
            cursor = record.parentViewId
        }
        return result
    }

    private fun renderControl(
        record: ProotViewRecord,
        parentLayers: List<ProotViewRecord>
    ): String = buildString {
        append("schema=").append(ProotViewBinding.CONTROL_SCHEMA).append('\n')
        append("view_id=").append(record.viewId).append('\n')
        append("base_root=").append(record.baseRootPath).append('\n')
        append("upper_root=").append(record.upperRootPath).append('\n')
        append("whiteout_root=").append(record.whiteoutRootPath).append('\n')
        scopeRootDirectories.forEach { scope ->
            append("scope_root=").append(scope.absolutePath).append('\n')
        }
        parentLayers.forEach { parent ->
            append("parent_upper_root=").append(parent.upperRootPath).append('\n')
            append("parent_whiteout_root=").append(parent.whiteoutRootPath).append('\n')
        }
        append("mode=read_write\n")
    }

    private fun isWhiteouted(whiteoutRoot: File, relativePath: String): Boolean {
        var cursor: File? = File(relativePath)
        while (cursor != null && cursor.path.isNotBlank() && cursor.path != ".") {
            if (File(whiteoutRoot, cursor.path).exists()) return true
            cursor = cursor.parentFile
        }
        return false
    }

    private fun isPhysicalViewValid(
        record: ProotViewRecord,
        snapshot: ProotViewCatalogSnapshot
    ): Boolean {
        if (record.containerId != containerId ||
            File(record.baseRootPath).absoluteFile != baseRootDirectory ||
            !File(record.upperRootPath).isDirectory ||
            !File(record.whiteoutRootPath).isDirectory
        ) return false
        val control = File(record.controlFilePath)
        if (!control.isFile) return false
        val text = runCatching { control.readText() }.getOrNull() ?: return false
        val parents = runCatching {
            resolveAncestorChain(snapshot, record.parentViewId)
        }.getOrNull() ?: return false
        return parents.size <= MAX_PARENT_LAYERS &&
            text == renderControl(record, parents)
    }

    private fun validateBlockDataPlane(
        record: ProotViewRecord,
        snapshot: ProotViewCatalogSnapshot
    ): Boolean = runCatching {
        inspectStorage(record, snapshot, requireValid = true)
        true
    }.getOrDefault(false)

    private fun inspectStorage(
        record: ProotViewRecord,
        snapshot: ProotViewCatalogSnapshot,
        requireValid: Boolean
    ): ProotViewStorageStats {
        require(isPhysicalViewValid(record, snapshot)) {
            "PRoot View 文件结构不完整：${record.viewId}"
        }
        val upper = File(record.upperRootPath).toPath().toAbsolutePath().normalize()
        val whiteout = File(record.whiteoutRootPath).toPath().toAbsolutePath().normalize()
        val internal = upper.resolve(BLOCK_INTERNAL_DIRECTORY)
        val blockRoot = internal.resolve(BLOCK_DIRECTORY_NAME)
        val temporaryRoot = internal.resolve(BLOCK_TEMP_DIRECTORY_NAME)
        val parentRoots = resolveAncestorChain(snapshot, record.parentViewId)
            .map { File(it.upperRootPath).toPath().toAbsolutePath().normalize() }
        val allowedSourceRoots = (listOf(baseRootDirectory.toPath().toAbsolutePath().normalize()) +
            parentRoots).map { it.toRealPath(LinkOption.NOFOLLOW_LINKS) }
        val blockDeltaFiles = linkedSetOf<File>()
        val blockMetadataFiles = linkedSetOf<File>()
        val blockKeys = linkedSetOf<String>()
        var temporaryEntries = 0

        if (Files.isDirectory(blockRoot, LinkOption.NOFOLLOW_LINKS)) {
            val hiddenFiles = regularFiles(blockRoot)
            val metadataFiles = hiddenFiles.filter { it.fileName.toString().endsWith(BLOCK_META_SUFFIX) }
            val sourceFiles = hiddenFiles.filter { it.fileName.toString().endsWith(BLOCK_SOURCE_SUFFIX) }
            val hiddenDeltas = hiddenFiles.filterNot { path ->
                val name = path.fileName.toString()
                name.endsWith(BLOCK_META_SUFFIX) || name.endsWith(BLOCK_SOURCE_SUFFIX)
            }
            val sourceKeys = sourceFiles.mapTo(linkedSetOf()) {
                relativeKey(blockRoot, it).removeSuffix(BLOCK_SOURCE_SUFFIX)
            }
            hiddenDeltas.forEach { delta ->
                val key = relativeKey(blockRoot, delta)
                blockKeys += key
                blockDeltaFiles += delta.toFile()
            }
            metadataFiles.forEach { metaPath ->
                val key = relativeKey(blockRoot, metaPath).removeSuffix(BLOCK_META_SUFFIX)
                val sourcePath = blockRoot.resolve("$key$BLOCK_SOURCE_SUFFIX")
                val visibleDelta = upper.resolve(key)
                val hiddenDelta = blockRoot.resolve(key)
                val visibleDeltaExists = Files.isRegularFile(visibleDelta, LinkOption.NOFOLLOW_LINKS)
                val hiddenDeltaExists = Files.isRegularFile(hiddenDelta, LinkOption.NOFOLLOW_LINKS)
                val delta = when {
                    visibleDeltaExists -> visibleDelta
                    hiddenDeltaExists -> hiddenDelta
                    else -> null
                }
                if (requireValid) {
                    require(key in sourceKeys) { "PRoot View 块来源记录缺失：$key" }
                    require(delta != null) { "PRoot View 块 delta 缺失：$key" }
                    require(visibleDeltaExists xor hiddenDeltaExists) {
                        "PRoot View 块 delta 身份冲突：$key"
                    }
                    require(validateBlockSource(sourcePath.toFile(), allowedSourceRoots)) {
                        "PRoot View 块来源越界或不存在：$key"
                    }
                    require(validateBlockMetadata(metaPath.toFile(), requireNotNull(delta).toFile())) {
                        "PRoot View 块元数据损坏：$key"
                    }
                }
                blockKeys += key
                delta?.let { blockDeltaFiles += it.toFile() }
                blockMetadataFiles += metaPath.toFile()
                if (Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
                    blockMetadataFiles += sourcePath.toFile()
                }
            }
            if (requireValid) {
                val metaKeys = metadataFiles.mapTo(linkedSetOf()) {
                    relativeKey(blockRoot, it).removeSuffix(BLOCK_META_SUFFIX)
                }
                require(sourceKeys == metaKeys) { "PRoot View 块来源记录与元数据不成对" }
                require(hiddenDeltas.all { relativeKey(blockRoot, it) in metaKeys }) {
                    "PRoot View 存在无元数据的隐藏 delta"
                }
            }
        }
        if (Files.isDirectory(temporaryRoot, LinkOption.NOFOLLOW_LINKS)) {
            temporaryEntries = regularFiles(temporaryRoot).size
            if (requireValid) require(temporaryEntries == 0) {
                "PRoot View 存在未完成的块临时文件"
            }
        }

        val regularUpperFiles = regularFiles(upper).filterNot { path ->
            path.startsWith(internal) || path.toFile() in blockDeltaFiles
        }.map { it.toFile() }
        val whiteoutEntries = if (Files.isDirectory(whiteout)) {
            regularFiles(whiteout).size
        } else {
            0
        }
        return ProotViewStorageStats(
            viewId = record.viewId,
            blockFileCount = blockKeys.size,
            blockDeltaLogicalBytes = sumLengths(blockDeltaFiles),
            blockDeltaAllocatedBytes = sumAllocated(blockDeltaFiles),
            blockMetadataBytes = sumLengths(blockMetadataFiles),
            blockMetadataAllocatedBytes = sumAllocated(blockMetadataFiles),
            regularUpperLogicalBytes = sumLengths(regularUpperFiles),
            regularUpperAllocatedBytes = sumAllocated(regularUpperFiles),
            whiteoutEntryCount = whiteoutEntries,
            temporaryEntryCount = temporaryEntries
        )
    }

    private fun validateBlockSource(sourceFile: File, allowedRoots: List<java.nio.file.Path>): Boolean {
        if (!sourceFile.isFile || sourceFile.length() !in 1..MAX_SOURCE_RECORD_BYTES) return false
        val value = runCatching { sourceFile.readText().trimEnd('\r', '\n') }.getOrNull()
            ?: return false
        if (value.isBlank() || value.indexOf('\u0000') >= 0) return false
        val sourceFileValue = File(value)
        if (!sourceFileValue.isAbsolute) return false
        val source = runCatching {
            sourceFileValue.toPath().toAbsolutePath().normalize()
                .toRealPath(LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
            ?: return false
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return false
        return allowedRoots.any { root -> source != root && source.startsWith(root) }
    }

    private fun validateBlockMetadata(metaFile: File, deltaFile: File): Boolean {
        val length = metaFile.length()
        if (length < BLOCK_META_ALIGNMENT * 2L ||
            length > MAX_BLOCK_META_BYTES || length % 2L != 0L
        ) return false
        val slotSize = length / 2L
        if (slotSize < BLOCK_META_ALIGNMENT || slotSize % BLOCK_META_ALIGNMENT != 0L ||
            slotSize > Int.MAX_VALUE
        ) return false
        val bytes = runCatching { metaFile.readBytes() }.getOrNull() ?: return false
        if (bytes.size.toLong() != length) return false
        val first = readValidBlockMeta(bytes, 0, slotSize.toInt())
        val second = readValidBlockMeta(bytes, slotSize.toInt(), slotSize.toInt())
        val selected = listOfNotNull(first, second).maxByOrNull(BlockMetaSlot::generation)
            ?: return false
        return deltaFile.isFile && deltaFile.length() >= selected.visibleSize
    }

    private fun readValidBlockMeta(bytes: ByteArray, offset: Int, slotSize: Int): BlockMetaSlot? {
        if (offset < 0 || slotSize < BLOCK_META_HEADER_BYTES ||
            offset > bytes.size - slotSize
        ) return null
        val buffer = ByteBuffer.wrap(bytes, offset, slotSize).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.long
        val version = buffer.int
        val blockSize = buffer.int
        val generation = buffer.long
        val baseSize = buffer.long
        val visibleSize = buffer.long
        val baseCutoff = buffer.long
        val bitmapBytes = buffer.long
        val checksum = buffer.long
        if (magic != BLOCK_META_MAGIC || version != BLOCK_META_VERSION ||
            blockSize != BLOCK_SIZE_BYTES || generation < 0 || baseSize < 0 ||
            visibleSize < 0 || baseCutoff < 0 || baseCutoff > baseSize ||
            bitmapBytes < 0 || bitmapBytes > slotSize - BLOCK_META_HEADER_BYTES
        ) return null
        val capacityBlocks = bitmapBytes * 8L
        if (blocksFor(baseSize, blockSize) > capacityBlocks ||
            blocksFor(visibleSize, blockSize) > capacityBlocks
        ) return null
        val checkedLength = BLOCK_META_HEADER_BYTES + bitmapBytes.toInt()
        val checked = bytes.copyOfRange(offset, offset + checkedLength)
        for (index in BLOCK_META_CHECKSUM_OFFSET until BLOCK_META_CHECKSUM_OFFSET + Long.SIZE_BYTES) {
            checked[index] = 0
        }
        if (checksum == 0L || checksum != checksumBytes(checked)) return null
        return BlockMetaSlot(generation, visibleSize)
    }

    private fun blocksFor(size: Long, blockSize: Int): Long =
        if (size == 0L) 0L else ((size - 1L) / blockSize.toLong()) + 1L

    private fun checksumBytes(bytes: ByteArray): Long {
        var hash = BLOCK_META_CHECKSUM_SEED
        bytes.forEach { byte ->
            hash = hash xor (byte.toLong() and 0xffL)
            hash *= BLOCK_META_CHECKSUM_MULTIPLIER
        }
        return hash
    }

    private fun regularFiles(root: java.nio.file.Path): List<java.nio.file.Path> {
        val result = mutableListOf<java.nio.file.Path>()
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) result.add(path)
            }
        }
        return result
    }

    private fun relativeKey(root: java.nio.file.Path, path: java.nio.file.Path): String =
        root.relativize(path).joinToString("/") { it.toString() }

    private fun sumLengths(files: Collection<File>): Long = files.fold(0L) { total, file ->
        Math.addExact(total, file.length())
    }

    private fun sumAllocated(files: Collection<File>): Long? {
        var total = 0L
        files.forEach { file ->
            val value = allocatedBytes(file) ?: return null
            total = Math.addExact(total, value)
        }
        return total
    }

    private fun cleanupOrphanViewDirectories(knownViewIds: Set<String>) {
        viewsDirectory.listFiles().orEmpty()
            .filter(File::isDirectory)
            .filterNot { it.name in knownViewIds }
            .forEach { target ->
                val canonical = target.canonicalFile
                require(canonical.parentFile == viewsDirectory.canonicalFile) {
                    "拒绝清理 View 根目录外路径：${canonical.absolutePath}"
                }
                canonical.walkBottomUp().forEach { entry ->
                    require(entry.delete() || !entry.exists()) {
                        "无法清理孤立 View 路径：${entry.absolutePath}"
                    }
                }
            }
    }

    private data class BlockMetaSlot(val generation: Long, val visibleSize: Long)

    private fun transact(
        operation: String,
        viewId: String,
        ownerId: String? = null,
        block: () -> Unit
    ) {
        val transactionId = "tx-${now()}-${idFactory()}"
        appendWal(transactionId, operation, "BEGIN", viewId, ownerId)
        block()
        appendWal(transactionId, operation, "COMMIT", viewId, ownerId)
    }

    private fun appendWal(
        transactionId: String,
        operation: String,
        phase: String,
        viewId: String,
        ownerId: String?
    ) {
        ensureStoreDirectories()
        val entry = JSONObject()
            .put("schema", WAL_SCHEMA)
            .put("transactionId", transactionId)
            .put("operation", operation)
            .put("phase", phase)
            .put("containerId", containerId)
            .put("viewId", viewId)
            .put("ownerId", ownerId ?: JSONObject.NULL)
            .put("atUnixMs", now())
            .toString() + "\n"
        FileOutputStream(walFile, true).use { stream ->
            stream.write(entry.toByteArray(StandardCharsets.UTF_8))
            stream.fd.sync()
        }
    }

    private fun writeCatalog(snapshot: ProotViewCatalogSnapshot) {
        val json = JSONObject()
            .put("schema", CATALOG_SCHEMA)
            .put("containerId", snapshot.containerId)
            .put("baseRootPath", snapshot.baseRootPath)
            .put("scopeRootPaths", JSONArray(snapshot.scopeRootPaths))
            .put("currentViewId", snapshot.currentViewId ?: JSONObject.NULL)
            .put("updatedAtUnixMs", now())
            .put("environmentCurrents", JSONObject().also { envs ->
                snapshot.environmentCurrents.forEach { (env, viewId) -> envs.put(env, viewId) }
            })
            .put("views", JSONArray().also { array -> snapshot.views.forEach { array.put(it.toJson()) } })
        writeAtomic(catalogFile, json.toString(2) + "\n")
        observedCatalogGeneration = advanceCatalogGeneration(catalogGenerationKey)
        cachedCatalog = snapshot
    }

    private fun readCatalog(): ProotViewCatalogSnapshot? {
        if (!catalogFile.isFile) return null
        val json = JSONObject(catalogFile.readText())
        require(json.getString("schema") == CATALOG_SCHEMA) { "PRoot View catalog schema 不支持" }
        val array = json.getJSONArray("views")
        val environmentCurrents = json.optJSONObject("environmentCurrents")?.let { envs ->
            buildMap {
                for (key in envs.keys()) {
                    val viewId = envs.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    if (viewId != null) put(key, viewId)
                }
            }
        }.orEmpty()
        return ProotViewCatalogSnapshot(
            containerId = json.getString("containerId"),
            baseRootPath = json.getString("baseRootPath"),
            scopeRootPaths = json.optJSONArray("scopeRootPaths")?.let { scopes ->
                buildList {
                    for (index in 0 until scopes.length()) add(scopes.getString(index))
                }
            }.orEmpty().ifEmpty { listOf(json.getString("baseRootPath")) },
            currentViewId = json.optString("currentViewId")
                .takeIf { it.isNotBlank() && it != "null" },
            views = buildList {
                for (index in 0 until array.length()) {
                    add(ProotViewRecord.fromJson(array.getJSONObject(index)))
                }
            },
            environmentCurrents = environmentCurrents
        )
    }

    private fun writeCurrentPointer(viewId: String) {
        requireSafeId(viewId, "viewId")
        val json = JSONObject()
            .put("schema", CURRENT_SCHEMA)
            .put("containerId", containerId)
            .put("viewId", viewId)
            .put("updatedAtUnixMs", now())
        writeAtomic(currentFile, json.toString(2) + "\n")
    }

    private fun readCurrentViewId(): String? {
        if (!currentFile.isFile) return null
        return runCatching {
            val json = JSONObject(currentFile.readText())
            require(json.getString("schema") == CURRENT_SCHEMA)
            require(json.getString("containerId") == containerId)
            json.getString("viewId")
        }.getOrNull()
    }

    private fun writeEnvironmentPointers(environmentCurrents: Map<String, String>) {
        environmentCurrents.values.forEach { requireSafeId(it, "viewId") }
        environmentCurrents.keys.forEach { requireEnvironmentId(it) }
        val json = JSONObject()
            .put("schema", ENVIRONMENTS_SCHEMA)
            .put("containerId", containerId)
            .put("updatedAtUnixMs", now())
            .put("environments", JSONObject().also { envs ->
                environmentCurrents.forEach { (env, viewId) -> envs.put(env, viewId) }
            })
        writeAtomic(environmentsFile, json.toString(2) + "\n")
    }

    private fun readEnvironmentPointers(): Map<String, String> {
        if (!environmentsFile.isFile) return emptyMap()
        return runCatching {
            val json = JSONObject(environmentsFile.readText())
            require(json.getString("schema") == ENVIRONMENTS_SCHEMA)
            require(json.getString("containerId") == containerId)
            val envs = json.optJSONObject("environments") ?: JSONObject()
            buildMap {
                for (key in envs.keys()) {
                    val viewId = envs.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    if (viewId != null) put(key, viewId)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeActiveEnvironment(environmentId: String) {
        requireEnvironmentId(environmentId)
        val json = JSONObject()
            .put("schema", ACTIVE_ENVIRONMENT_SCHEMA)
            .put("containerId", containerId)
            .put("environmentId", environmentId)
            .put("updatedAtUnixMs", now())
        writeAtomic(activeEnvironmentFile, json.toString(2) + "\n")
    }

    private fun readActiveEnvironmentId(): String? {
        if (!activeEnvironmentFile.isFile) return null
        return runCatching {
            val json = JSONObject(activeEnvironmentFile.readText())
            require(json.getString("schema") == ACTIVE_ENVIRONMENT_SCHEMA)
            require(json.getString("containerId") == containerId)
            json.getString("environmentId").also(::requireEnvironmentId)
        }.getOrNull()
    }

    private fun writeAtomic(target: File, content: String) {
        val parent = target.parentFile ?: error("原子文件缺少父目录：${target.absolutePath}")
        require(parent.mkdirs() || parent.isDirectory) { "无法创建目录：${parent.absolutePath}" }
        val temp = File(parent, ".${target.name}.tmp-${UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(content.toByteArray(StandardCharsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IllegalStateException("当前文件系统不支持 PRoot View 原子切换", unsupported)
            }
            syncDirectoryBestEffort(parent)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun syncDirectoryBestEffort(directory: File) {
        runCatching {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        }
    }

    private fun ensureStoreDirectories() {
        require(rootDirectory.mkdirs() || rootDirectory.isDirectory) {
            "无法创建 PRoot View 根目录：${rootDirectory.absolutePath}"
        }
        require(viewsDirectory.mkdirs() || viewsDirectory.isDirectory) {
            "无法创建 PRoot View 数据目录：${viewsDirectory.absolutePath}"
        }
    }

    private fun cleanupTemporaryFiles(directory: File): Int {
        var removed = 0
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                removed += cleanupTemporaryFiles(child)
            } else if (child.name.contains(".tmp-") && child.delete()) {
                removed++
            }
        }
        return removed
    }

    private fun deleteViewDirectory(record: ProotViewRecord) {
        val target = File(record.upperRootPath).parentFile?.canonicalFile
            ?: error("View 目录不可解析：${record.viewId}")
        val root = viewsDirectory.canonicalFile
        require(target.parentFile == root) { "拒绝删除 View 根目录外路径：${target.absolutePath}" }
        target.walkBottomUp().forEach { entry ->
            require(entry.delete() || !entry.exists()) { "无法删除 View 路径：${entry.absolutePath}" }
        }
    }

    private fun requireCatalog(): ProotViewCatalogSnapshot =
        cachedCatalogIfFresh() ?: recoverInternal() ?: error("PRoot View 尚未初始化")

    private fun cachedCatalogIfFresh(): ProotViewCatalogSnapshot? {
        val cached = cachedCatalog ?: return null
        return cached.takeIf {
            observedCatalogGeneration == catalogGeneration(catalogGenerationKey)
        } ?: run {
            cachedCatalog = null
            null
        }
    }

    private fun nextViewId(prefix: String): String {
        val value = "$prefix-${now()}-${idFactory()}"
        requireSafeId(value, "viewId")
        return value
    }

    private fun requireSafeId(value: String, field: String) {
        require(value.isNotBlank() && value.length <= 160 &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
            "$field 含不安全字符"
        }
    }

    private fun requireOwnerId(ownerId: String) = requireSafeId(ownerId, "ownerId")

    private fun requireEnvironmentId(environmentId: String) {
        require(environmentId.isNotBlank() && environmentId.length <= MAX_ENVIRONMENT_ID_LENGTH &&
            environmentId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) { "PRoot View 环境 ID 含不安全字符：$environmentId" }
    }

    private fun ProotViewCatalogSnapshot.requireView(viewId: String): ProotViewRecord =
        views.firstOrNull { it.viewId == viewId }
            ?: error("PRoot View 不存在：$viewId")

    private fun ProotViewCatalogSnapshot.replace(record: ProotViewRecord): ProotViewCatalogSnapshot =
        copy(views = views.map { if (it.viewId == record.viewId) record else it })

    private fun ProotViewCatalogSnapshot.environmentHeadSet(): Set<String> =
        environmentCurrents.values.toSet()

    private fun ProotViewRecord.toBinding(
        snapshot: ProotViewCatalogSnapshot
    ): ProotViewBinding = ProotViewBinding(
        viewId = viewId,
        baseRootPath = baseRootPath,
        upperRootPath = upperRootPath,
        whiteoutRootPath = whiteoutRootPath,
        controlFilePath = controlFilePath,
        writable = true,
        scopeRootPaths = snapshot.scopeRootPaths,
        parentViewIds = resolveAncestorChain(snapshot, parentViewId).map { it.viewId },
        environmentId = environmentId
    )

    companion object {
        private const val CATALOG_SCHEMA = "kite_proot_view_catalog_v1"
        private const val CURRENT_SCHEMA = "kite_proot_view_current_v1"
        private const val WAL_SCHEMA = "kite_proot_view_wal_v1"
        private const val ACTIVATION_SCHEMA = "kite_proot_view_activation_v1"
        private const val ENVIRONMENTS_SCHEMA = "kite_proot_view_environments_v1"
        private const val ACTIVE_ENVIRONMENT_SCHEMA = "kite_proot_view_active_environment_v1"
        // 兼容环境身份。default 始终存在，旧单 current 迁移到这里；现有终端、工作台、桥接和
        // 资源运行入口默认继承 default，不需要每个调用方各自传参。
        const val DEFAULT_ENVIRONMENT_ID = "default"
        // 环境身份只是控制面标签，不进入 native 路径或资源 ID 特判；与 viewId/ownerId 同样受限。
        private const val MAX_ENVIRONMENT_ID_LENGTH = 64
        private const val CATALOG_FILE_NAME = "catalog.json"
        private const val CURRENT_FILE_NAME = "current.json"
        private const val WAL_FILE_NAME = "wal.jsonl"
        private const val ACTIVATION_FILE_NAME = "activation.json"
        private const val ENVIRONMENTS_FILE_NAME = "environments.json"
        private const val ACTIVE_ENVIRONMENT_FILE_NAME = "active-environment.json"
        private const val VIEWS_DIRECTORY_NAME = "views"
        private const val BLOCK_INTERNAL_DIRECTORY = ".kite-proot-view"
        private const val BLOCK_DIRECTORY_NAME = "blocks"
        private const val BLOCK_TEMP_DIRECTORY_NAME = "tmp"
        private const val BLOCK_META_SUFFIX = ".meta"
        private const val BLOCK_SOURCE_SUFFIX = ".source"
        private const val BLOCK_SIZE_BYTES = 64 * 1024
        private const val BLOCK_META_HEADER_BYTES = 64
        private const val BLOCK_META_CHECKSUM_OFFSET = 56
        private const val BLOCK_META_ALIGNMENT = 4096L
        private const val BLOCK_META_MAGIC = 0x4b46424c4f434b32L
        private const val BLOCK_META_VERSION = 1
        private const val BLOCK_META_CHECKSUM_SEED = 1469598103934665603L
        private const val BLOCK_META_CHECKSUM_MULTIPLIER = 1099511628211L
        private const val MAX_BLOCK_META_BYTES = 64L * 1024L * 1024L
        private const val MAX_SOURCE_RECORD_BYTES = 4096L
        private val PROCESS_SESSION_ID = UUID.randomUUID().toString()
        const val RUNTIME_CAPABILITY = "filesystem_view_v1"
        const val BLOCK_RUNTIME_CAPABILITY = "filesystem_view_block_cow_v2"
        // native 协议支持 32 层；Android 先按真机已验证的 8 层设运行上限。
        // 达到上限必须先做 compaction，不能静默把路径热开销无限叠加。
        private const val MAX_PARENT_LAYERS = 8
        private const val MAX_CACHED_STORES = 8
        private val catalogGenerations = ConcurrentHashMap<String, AtomicLong>()
        private val storeCache = object : LinkedHashMap<String, ProotViewStore>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ProotViewStore>?,
            ): Boolean = size > MAX_CACHED_STORES
        }

        private fun catalogGeneration(key: String): Long =
            catalogGenerations[key]?.get() ?: 0L

        private fun advanceCatalogGeneration(key: String): Long =
            catalogGenerations.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()

        private fun allocatedBytesOnDisk(file: File): Long? = runCatching {
            Math.multiplyExact(Os.stat(file.absolutePath).st_blocks, 512L)
        }.getOrNull()

        fun forContainer(container: ContainerRecord): ProotViewStore {
            val rootfs = File(container.rootfsPath).absoluteFile
            val containerRoot = rootfs.parentFile
                ?: error("容器 rootfs 缺少父目录：${rootfs.absolutePath}")
            val containersRoot = containerRoot.parentFile
                ?: error("容器目录缺少父目录：${containerRoot.absolutePath}")
            val runtimeRoot = containersRoot.parentFile
                ?: error("运行时目录不存在：${containersRoot.absolutePath}")
            val filesRoot = runtimeRoot.parentFile
                ?: error("应用文件目录不存在：${runtimeRoot.absolutePath}")
            val workspace = File(container.workspacePath).absoluteFile
            // T014g：forContainer 是无副作用的路径构造，不再 mkdirs .kf/software/.kf/bin。
            // scope 目录只在 Base 首次封存前由 ensureInitialized 准备；封存后写入进 View Upper。
            val software = File(workspace, ".kf/software")
            val bin = File(workspace, ".kf/bin")
            val storeRoot = File(File(filesRoot, "proot-views"), container.id)
            val cacheKey = listOf(
                storeRoot.absolutePath,
                container.id,
                container.createdAt.toString(),
                runtimeRoot.absolutePath,
                rootfs.absolutePath,
                software.absolutePath,
                bin.absolutePath,
            ).joinToString("|")
            return synchronized(storeCache) {
                storeCache[cacheKey] ?: ProotViewStore(
                    rootDirectory = storeRoot,
                    containerId = container.id,
                    baseRootDirectory = runtimeRoot,
                    scopeRootDirectories = listOf(rootfs, software, bin)
                ).also { storeCache[cacheKey] = it }
            }
        }
    }
}

object ProotViewRuntime {
    fun resolveActiveBinding(container: ContainerRecord): ProotViewBinding? {
        val rootfs = File(container.rootfsPath).absoluteFile
        val runtimeRoot = rootfs.parentFile?.parentFile?.parentFile ?: return null
        val descriptor = runCatching {
            JSONObject(File(runtimeRoot, "proot-runtime.json").readText())
        }.getOrNull() ?: return null
        return resolveActiveBinding(container, descriptor)
    }

    fun resolveActiveBinding(
        container: ContainerRecord,
        runtimeDescriptor: JSONObject,
        requestedViewId: String? = null,
        requestedEnvironmentId: String? = null
    ): ProotViewBinding? {
        val cleanRequestedViewId = requestedViewId?.trim()?.takeIf(String::isNotBlank)
        val cleanRequestedEnvironmentId = requestedEnvironmentId?.trim()?.takeIf(String::isNotBlank)
        val hasExplicitRequest = cleanRequestedViewId != null || cleanRequestedEnvironmentId != null
        // 正式版普通 PRoot 走直接 rootfs 快路径。activation 只保留为 View 控制面状态，
        // 不能再让历史启用记录把所有普通启动隐式带回 View。
        if (!hasExplicitRequest) return null
        val runtimeSupported = runtimeDescriptor.hasCapability(ProotViewStore.RUNTIME_CAPABILITY) &&
            runtimeDescriptor.hasCapability(ProotViewStore.BLOCK_RUNTIME_CAPABILITY)
        require(runtimeSupported || cleanRequestedViewId == null) {
            "指定 PRoot View 需要完整 View 运行时能力：$cleanRequestedViewId"
        }
        require(runtimeSupported || cleanRequestedEnvironmentId == null) {
            "指定 PRoot View 环境需要完整 View 运行时能力：$cleanRequestedEnvironmentId"
        }
        if (!runtimeSupported) return null
        val store = ProotViewStore.forContainer(container)
        if (!store.isEnabled()) {
            throw IllegalArgumentException(
                "指定 PRoot View/环境时 View 运行时尚未启用：$cleanRequestedViewId/$cleanRequestedEnvironmentId"
            )
        }
        // 解析优先级：显式 viewId（事务子 View）> 显式 environmentId 的 current 头。
        // 同时收到 viewId 和 environmentId 时，必须校验该 View 归属请求环境；不一致立即拒绝，
        // 不允许显式 View 跨环境覆盖。受管入口明确请求某个环境时，不得静默回退到 default 或其他环境。
        cleanRequestedViewId?.let { viewId ->
            val binding = store.binding(viewId)
            cleanRequestedEnvironmentId?.let { envId ->
                require(binding.environmentId == envId) {
                    "PRoot View 不属于请求环境：view=${binding.environmentId}, environment=$envId"
                }
            }
            return binding
        }
        val environmentId = requireNotNull(cleanRequestedEnvironmentId) {
            "显式 PRoot View 请求缺少 viewId/environmentId"
        }
        val currents = store.environmentCurrents()
        require(environmentId in currents) {
            "PRoot View 环境不存在：$environmentId"
        }
        return store.currentBinding(environmentId)
    }

    internal fun JSONObject.hasCapability(capability: String): Boolean {
        val capabilities = optJSONArray("capabilities") ?: return false
        for (index in 0 until capabilities.length()) {
            if (capabilities.optString(index) == capability) return true
        }
        return false
    }
}
