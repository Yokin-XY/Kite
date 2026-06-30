package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.service.BackgroundRuntimeHealthStatus
import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.BackgroundRuntimeMode
import com.kite.app.foundation.service.BackgroundRuntimeRecord
import com.kite.app.foundation.service.BackgroundRuntimeRegistry
import com.kite.app.foundation.service.BackgroundRuntimeRestartPolicy
import com.kite.app.foundation.service.BackgroundRuntimeStatus
import com.kite.app.foundation.service.RuntimeRetentionClass
import com.kite.app.foundation.workspace.KFWorkspaceManager
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object RuntimeControlledLeaseProbeRegistration {
    const val RUNTIME_ID = "background-hermes-controlled-lease-probe"
    const val UNIT_ID = "unit-hermes-controlled-lease-probe"
    const val TITLE = "Hermes controlled lease probe"
    const val COMMAND = "python3 /workspace/.kf/hermes-controlled-lease-probe.py"
    const val START_COMMAND = "exec $COMMAND"

    fun buildRecord(
        spaceId: String,
        logPath: String,
        createdAt: Long = System.currentTimeMillis()
    ): BackgroundRuntimeRecord {
        return BackgroundRuntimeRecord(
            id = RUNTIME_ID,
            spaceId = spaceId,
            kind = BackgroundRuntimeKind.CUSTOM,
            mode = BackgroundRuntimeMode.PROCESS,
            title = TITLE,
            workingDirectory = WorkSurfaceRuntimeBridge.defaults.workspaceDir,
            startCommand = START_COMMAND,
            exposureScope = RuntimeExposureScope.HOST_LOCAL_ONLY,
            stopCommand = null,
            statusCommand = null,
            healthCommand = null,
            logPath = logPath,
            createdAt = createdAt,
            status = BackgroundRuntimeStatus.REGISTERED,
            healthStatus = BackgroundRuntimeHealthStatus.INACTIVE,
            pid = null,
            restartPolicy = BackgroundRuntimeRestartPolicy.NEVER,
            retentionClass = RuntimeRetentionClass.EPHEMERAL
        )
    }

    fun register(
        context: Context,
        spaceId: String,
        createdAt: Long = System.currentTimeMillis()
    ): BackgroundRuntimeRecord {
        val appContext = context.applicationContext
        val record = buildRecord(
            spaceId = spaceId,
            logPath = BackgroundRuntimeRegistry.buildLogFile(appContext, RUNTIME_ID).absolutePath,
            createdAt = createdAt
        )
        upsertActiveManifestUnit(appContext)
        return BackgroundRuntimeRegistry.upsert(appContext, record)
    }

    fun registerForCurrentSpace(
        context: Context,
        createdAt: Long = System.currentTimeMillis()
    ): BackgroundRuntimeRecord {
        val appContext = context.applicationContext
        val space = KFWorkspaceManager.getCurrentSpace(appContext)
            ?: KFWorkspaceManager.ensureDefaultSpace(appContext)
        return register(
            context = appContext,
            spaceId = space.id,
            createdAt = createdAt
        )
    }

    internal fun upsertActiveManifestUnit(context: Context): File? {
        val workspacePath = WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
            ?.workspacePath
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val manifestFile = WorkspaceBuildSupport.runtimeProcessManifestFile(File(workspacePath))
        upsertManifestUnit(manifestFile)
        return manifestFile
    }

    internal fun upsertManifestUnit(file: File) {
        val json = loadManifestJson(file)
        val units = json.optJSONArray("units") ?: JSONArray().also { json.put("units", it) }
        val updatedUnits = JSONArray()
        var replaced = false
        for (index in 0 until units.length()) {
            val unit = units.optJSONObject(index)
            if (unit == null) {
                continue
            }
            if (unit.optString("id") == UNIT_ID) {
                updatedUnits.put(buildManifestUnitJson())
                replaced = true
            } else {
                updatedUnits.put(unit)
            }
        }
        if (!replaced) {
            updatedUnits.put(buildManifestUnitJson())
        }
        json.put("units", updatedUnits)
        file.parentFile?.mkdirs()
        file.writeText(json.toString(2))
    }

    internal fun buildManifestUnitJson(): JSONObject {
        return JSONObject()
            .put("id", UNIT_ID)
            .put("displayName", TITLE)
            .put("tier", RuntimeProcessUnitTier.LEASE.name)
            .put("manualKillPolicy", RuntimeProcessUnitManualKillPolicy.RESPECT_USER_KILL.name)
            .put(
                "match",
                JSONObject()
                    .put("runtimeId", RUNTIME_ID)
                    .put("exactCommand", COMMAND)
            )
            .put(
                "lease",
                JSONObject()
                    .put("enabled", true)
                    .put("initialLeaseMs", 300_000L)
                    .put("renewMs", 60_000L)
                    .put("maxTotalLeaseMs", 1_800_000L)
            )
            .put(
                "protection",
                JSONObject()
                    .put("allowReclaim", true)
                    .put("allowKill", false)
                    .put("allowRestart", false)
            )
    }

    private fun loadManifestJson(file: File): JSONObject {
        if (!file.exists() || file.length() <= 0L || file.readText().isBlank()) {
            return JSONObject()
                .put("version", 1)
                .put("authority", "ubuntu_advisory")
                .put("boundary", "declaration_only_android_observes_no_direct_kill_restart_or_quarantine")
                .put("units", JSONArray())
        }
        return runCatching { JSONObject(file.readText()) }.getOrElse {
            JSONObject()
                .put("version", 1)
                .put("authority", "ubuntu_advisory")
                .put("boundary", "declaration_only_android_observes_no_direct_kill_restart_or_quarantine")
                .put("units", JSONArray())
        }
    }
}
