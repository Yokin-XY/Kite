package com.kite.app.recipe

import org.json.JSONArray
import org.json.JSONObject

data class KiteRecipe(
    val schemaVersion: Int = PROTOCOL_VERSION,
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val defaultUrl: String,
    val shortcut: Boolean,
    val status: String,
    val icon: String = "",
    val taskLabel: String = "",
    val taskMode: String = "",
    val source: String = SOURCE_ASSETS,
    val steps: List<KiteRecipeStep> = emptyList()
) {
    fun hasShellStep(): Boolean = steps.any { it.type == STEP_SHELL && !it.cmd.isNullOrBlank() }

    fun openWebUrl(): String = steps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
        ?: defaultUrl

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("type", type)
        .put("defaultUrl", defaultUrl)
        .put("shortcut", shortcut)
        .put("status", status)
        .put("icon", icon)
        .put("taskLabel", taskLabel)
        .put("taskMode", taskMode)
        .put("source", source)
        .put("steps", JSONArray().apply {
            steps.forEach { put(it.toJson()) }
        })

    companion object {
        const val PROTOCOL_VERSION = 1

        const val TYPE_OPEN_URL = "open_url"
        const val TYPE_START_SERVICE = "start_service"
        const val TYPE_COMMAND_WEB = "command_web"
        const val TYPE_TEMPLATE = "template"

        const val STEP_OPEN_WEB = "open_web"
        const val STEP_SHELL = "shell"

        const val RUN_MODE_WAIT = "wait"
        const val RUN_MODE_DETACHED = "detached"

        const val OUTPUT_LAST_MEANINGFUL = "lastMeaningfulOutput"

        const val SOURCE_ASSETS = "assets"
        const val SOURCE_USER = "user"
        const val SOURCE_IMPORTED = "imported"
        const val SOURCE_REMOTE = "remote"

        fun fromJson(json: JSONObject, source: String): KiteRecipe {
            val stepsJson = json.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    add(KiteRecipeStep.fromJson(stepsJson.getJSONObject(index), index))
                }
            }
            return KiteRecipe(
                schemaVersion = json.optInt("schemaVersion", PROTOCOL_VERSION),
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description"),
                type = json.optString("type", TYPE_OPEN_URL),
                defaultUrl = json.getString("defaultUrl"),
                shortcut = json.optBoolean("shortcut", false),
                status = json.optString("status", "unknown"),
                icon = json.optString("icon"),
                taskLabel = json.optString("taskLabel"),
                taskMode = json.optString("taskMode"),
                source = json.optString("source").ifBlank { normalizeSource(source) },
                steps = steps
            )
        }

        private fun normalizeSource(source: String): String = when (source) {
            "asset" -> SOURCE_ASSETS
            else -> source
        }
    }
}

data class KiteRecipeStep(
    val id: String,
    val type: String,
    val cmd: String? = null,
    val runMode: String? = null,
    val expected: KiteExpectedResult? = null,
    val outputPolicy: KiteOutputPolicy? = null,
    val url: String? = null,
    val wait: Boolean? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .apply {
            if (!cmd.isNullOrBlank()) put("cmd", cmd)
            if (!runMode.isNullOrBlank()) put("runMode", runMode)
            if (expected != null) put("expected", expected.toJson())
            if (outputPolicy != null) put("outputPolicy", outputPolicy.toJson())
            if (!url.isNullOrBlank()) put("url", url)
        }

    companion object {
        fun fromJson(json: JSONObject, index: Int = 0): KiteRecipeStep {
            val type = json.getString("type")
            val legacyWait = if (json.has("wait")) json.optBoolean("wait") else null
            val runMode = json.optString("runMode").ifBlank {
                if (legacyWait == true) KiteRecipe.RUN_MODE_WAIT else null
            }
            return KiteRecipeStep(
                id = json.optString("id").ifBlank { "step_${index + 1}_$type" },
                type = type,
                cmd = json.optString("cmd").ifBlank { null },
                runMode = runMode,
                expected = json.optJSONObject("expected")?.let { KiteExpectedResult.fromJson(it) },
                outputPolicy = json.optJSONObject("outputPolicy")?.let { KiteOutputPolicy.fromJson(it) }
                    ?: if (type == KiteRecipe.STEP_SHELL) KiteOutputPolicy() else null,
                url = json.optString("url").ifBlank { null },
                wait = legacyWait
            )
        }
    }
}

data class KiteExpectedResult(
    val mode: String = "contains",
    val text: String,
    val source: String = KiteRecipe.OUTPUT_LAST_MEANINGFUL
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("text", text)
        .put("source", source)

    companion object {
        fun fromJson(json: JSONObject): KiteExpectedResult = KiteExpectedResult(
            mode = json.optString("mode", "contains"),
            text = json.optString("text"),
            source = json.optString("source", KiteRecipe.OUTPUT_LAST_MEANINGFUL)
        )
    }
}

data class KiteOutputPolicy(
    val mode: String = KiteRecipe.OUTPUT_LAST_MEANINGFUL,
    val tailChars: Int = 2000
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("tailChars", tailChars)

    companion object {
        fun fromJson(json: JSONObject): KiteOutputPolicy = KiteOutputPolicy(
            mode = json.optString("mode", KiteRecipe.OUTPUT_LAST_MEANINGFUL),
            tailChars = json.optInt("tailChars", 2000)
        )
    }
}

data class KiteRunReport(
    val protocolVersion: Int,
    val requestId: String,
    val runId: String,
    val recipeId: String,
    val status: String,
    val ok: Boolean,
    val steps: List<KiteStepReport> = emptyList(),
    val nextAction: KiteNextAction? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("protocolVersion", protocolVersion)
        .put("requestId", requestId)
        .put("runId", runId)
        .put("recipeId", recipeId)
        .put("status", status)
        .put("ok", ok)
        .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
        .apply {
            if (nextAction != null) put("nextAction", nextAction.toJson())
        }

    fun openWebUrlIfFinished(): String? =
        nextAction?.takeIf { status == STATUS_FINISHED && ok && it.type == KiteRecipe.STEP_OPEN_WEB }?.url

    fun hasMismatch(): Boolean = steps.any { it.matchResult?.enabled == true && it.matchResult.matched == false }

    companion object {
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_RUNNING = "running"
        const val STATUS_FINISHED = "finished"
        const val STATUS_FAILED = "failed"
        const val STATUS_BRIDGE_UNAVAILABLE = "bridge_unavailable"

        fun fromJsonOrNull(raw: String): KiteRunReport? = runCatching {
            fromJson(JSONObject(raw))
        }.getOrNull()

        fun fromJson(json: JSONObject): KiteRunReport {
            val stepsJson = json.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    add(KiteStepReport.fromJson(stepsJson.getJSONObject(index)))
                }
            }
            return KiteRunReport(
                protocolVersion = json.optInt("protocolVersion", KiteRecipe.PROTOCOL_VERSION),
                requestId = json.optString("requestId"),
                runId = json.optString("runId").ifBlank { json.optString("requestId") },
                recipeId = json.optString("recipeId"),
                status = json.optString("status", STATUS_FAILED),
                ok = json.optBoolean("ok", false),
                steps = steps,
                nextAction = json.optJSONObject("nextAction")?.let { KiteNextAction.fromJson(it) }
            )
        }
    }
}

data class KiteStepReport(
    val stepId: String,
    val type: String,
    val status: String,
    val exitCode: Int? = null,
    val lastMeaningfulOutput: String = "",
    val stdoutTail: String = "",
    val stderrTail: String = "",
    val matchResult: KiteMatchResult? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("stepId", stepId)
        .put("type", type)
        .put("status", status)
        .put("lastMeaningfulOutput", lastMeaningfulOutput)
        .put("stdoutTail", stdoutTail)
        .put("stderrTail", stderrTail)
        .apply {
            if (exitCode != null) put("exitCode", exitCode)
            if (matchResult != null) put("matchResult", matchResult.toJson())
        }

    companion object {
        fun fromJson(json: JSONObject): KiteStepReport = KiteStepReport(
            stepId = json.optString("stepId"),
            type = json.optString("type"),
            status = json.optString("status"),
            exitCode = if (json.has("exitCode")) json.optInt("exitCode") else null,
            lastMeaningfulOutput = json.optString("lastMeaningfulOutput"),
            stdoutTail = json.optString("stdoutTail"),
            stderrTail = json.optString("stderrTail"),
            matchResult = json.optJSONObject("matchResult")?.let { KiteMatchResult.fromJson(it) }
        )
    }
}

data class KiteMatchResult(
    val enabled: Boolean,
    val matched: Boolean,
    val mode: String = "contains",
    val text: String = "",
    val source: String = KiteRecipe.OUTPUT_LAST_MEANINGFUL
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("matched", matched)
        .put("mode", mode)
        .put("text", text)
        .put("source", source)

    companion object {
        fun fromJson(json: JSONObject): KiteMatchResult = KiteMatchResult(
            enabled = json.optBoolean("enabled", false),
            matched = json.optBoolean("matched", false),
            mode = json.optString("mode", "contains"),
            text = json.optString("text"),
            source = json.optString("source", KiteRecipe.OUTPUT_LAST_MEANINGFUL)
        )
    }
}

data class KiteNextAction(
    val type: String,
    val url: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .apply {
            if (!url.isNullOrBlank()) put("url", url)
        }

    companion object {
        fun fromJson(json: JSONObject): KiteNextAction = KiteNextAction(
            type = json.optString("type"),
            url = json.optString("url").ifBlank { null }
        )
    }
}
