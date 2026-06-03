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
    val icon: KiteRecipeIcon = KiteRecipeIcon.defaultForType(type),
    val card: KiteRecipeCard = KiteRecipeCard.defaultForType(type),
    val execution: KiteExecution = KiteExecution.steps(emptyList()),
    val expected: KiteExpectedResult? = null,
    val taskLabel: String = "",
    val taskMode: String = "",
    val runtimeSource: String = SOURCE_ASSETS
) {
    val steps: List<KiteRecipeStep>
        get() = execution.steps

    val status: String
        get() = card.status

    fun hasShellStep(): Boolean = steps.any { it.type == STEP_SHELL && !it.cmd.isNullOrBlank() }

    fun openWebUrl(): String = steps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
        ?: defaultUrl

    fun firstShellStep(): KiteRecipeStep? = steps.firstOrNull { it.type == STEP_SHELL }

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("type", type)
        .put("defaultUrl", defaultUrl)
        .put("shortcut", shortcut)
        .put("icon", icon.toJson())
        .put("card", card.toJson())
        .put("execution", execution.toJson())
        .apply {
            if (expected != null) put("expected", expected.toJson())
            if (taskLabel.isNotBlank()) put("taskLabel", taskLabel)
            if (taskMode.isNotBlank()) put("taskMode", taskMode)
        }

    companion object {
        const val PROTOCOL_VERSION = 1

        const val TYPE_OPEN_URL = "open_url"
        const val TYPE_START_SERVICE = "start_service"
        const val TYPE_COMMAND_WEB = "command_web"
        const val TYPE_SCRIPT_WEB = "script_web"
        const val TYPE_TEMPLATE = "template"

        const val EXECUTION_STEPS = "steps"
        const val EXECUTION_SCRIPT = "script"
        const val EXECUTION_ANDROID_ACTION = "android_action"

        const val STEP_OPEN_WEB = "open_web"
        const val STEP_SHELL = "shell"

        const val RUN_MODE_ATTACHED = "attached"
        const val RUN_MODE_WAIT = "wait"
        const val RUN_MODE_DETACHED = "detached"
        const val RUN_MODE_BACKGROUND = "background"

        const val OUTPUT_LAST_MEANINGFUL = "lastMeaningfulOutput"

        const val SOURCE_ASSETS = "assets"
        const val SOURCE_USER = "user"
        const val SOURCE_IMPORTED = "imported"
        const val SOURCE_DROPZONE = "dropzone"
        const val SOURCE_REMOTE = "remote"

        fun fromJson(json: JSONObject, runtimeSource: String): KiteRecipe {
            val type = json.optString("type", TYPE_OPEN_URL)
            val execution = parseExecution(json)
            val icon = parseIcon(json, type)
            val card = parseCard(json, type, icon.name)
            return KiteRecipe(
                schemaVersion = json.optInt("schemaVersion", PROTOCOL_VERSION),
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description"),
                type = type,
                defaultUrl = json.optString("defaultUrl"),
                shortcut = json.optBoolean("shortcut", false),
                icon = icon,
                card = card,
                execution = execution,
                expected = json.optJSONObject("expected")?.let { KiteExpectedResult.fromJson(it) },
                taskLabel = json.optString("taskLabel"),
                taskMode = json.optString("taskMode"),
                runtimeSource = normalizeSource(runtimeSource)
            )
        }

        private fun parseExecution(json: JSONObject): KiteExecution {
            val executionJson = json.optJSONObject("execution")
            if (executionJson != null) return KiteExecution.fromJson(executionJson)

            val legacySteps = parseSteps(json.optJSONArray("steps") ?: JSONArray())
            return KiteExecution(mode = EXECUTION_STEPS, steps = legacySteps)
        }

        private fun parseIcon(json: JSONObject, type: String): KiteRecipeIcon {
            val iconJson = json.optJSONObject("icon")
            if (iconJson != null) return KiteRecipeIcon.fromJson(iconJson, type)

            val legacyIcon = json.optString("icon")
            return if (legacyIcon.isNotBlank()) {
                KiteRecipeIcon(type = "builtin", name = KiteRecipeIcon.normalizeName(legacyIcon, type))
            } else {
                KiteRecipeIcon.defaultForType(type)
            }
        }

        private fun parseCard(json: JSONObject, type: String, iconName: String): KiteRecipeCard {
            val cardJson = json.optJSONObject("card")
            if (cardJson != null) return KiteRecipeCard.fromJson(cardJson, type, iconName)

            return KiteRecipeCard(
                accent = KiteRecipeCard.resolvedAccentFor(iconName, type, json.optString("accent")),
                status = json.optString("status", "unknown")
            )
        }

        internal fun parseSteps(stepsJson: JSONArray): List<KiteRecipeStep> = buildList {
            for (index in 0 until stepsJson.length()) {
                add(KiteRecipeStep.fromJson(stepsJson.getJSONObject(index), index))
            }
        }

        fun normalizeRunMode(runMode: String?, legacyWait: Boolean? = null): String? {
            val normalized = runMode?.trim()?.lowercase().orEmpty()
            return when {
                normalized == RUN_MODE_ATTACHED || normalized == RUN_MODE_WAIT -> RUN_MODE_ATTACHED
                normalized == RUN_MODE_DETACHED || normalized == RUN_MODE_BACKGROUND -> RUN_MODE_DETACHED
                legacyWait == true -> RUN_MODE_ATTACHED
                else -> null
            }
        }

        private fun normalizeSource(source: String): String = when (source) {
            "asset" -> SOURCE_ASSETS
            SOURCE_ASSETS, SOURCE_USER, SOURCE_IMPORTED, SOURCE_DROPZONE, SOURCE_REMOTE -> source
            else -> SOURCE_USER
        }
    }
}

data class KiteRecipeIcon(
    val type: String = "builtin",
    val name: String = ICON_DEFAULT
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("name", name)

    companion object {
        const val ICON_TERMINAL = "terminal"
        const val ICON_WEB = "web"
        const val ICON_BOT = "bot"
        const val ICON_FILE = "file"
        const val ICON_MUSIC = "music"
        const val ICON_SHOPPING = "shopping"
        const val ICON_LOGS = "logs"
        const val ICON_TOOLS = "tools"
        const val ICON_CODE = "code"
        const val ICON_SERVER = "server"
        const val ICON_MORE = "more"
        const val ICON_DEFAULT = "default"

        val BUILTIN = listOf(
            ICON_TERMINAL,
            ICON_WEB,
            ICON_BOT,
            ICON_FILE,
            ICON_MUSIC,
            ICON_SHOPPING,
            ICON_LOGS,
            ICON_TOOLS,
            ICON_CODE,
            ICON_SERVER,
            ICON_MORE,
            ICON_DEFAULT
        )

        fun fromJson(json: JSONObject, recipeType: String): KiteRecipeIcon = KiteRecipeIcon(
            type = json.optString("type", "builtin"),
            name = normalizeName(json.optString("name"), recipeType)
        )

        fun defaultForType(recipeType: String): KiteRecipeIcon = KiteRecipeIcon(
            type = "builtin",
            name = defaultNameForType(recipeType)
        )

        fun defaultNameForType(recipeType: String): String = when (recipeType) {
            KiteRecipe.TYPE_OPEN_URL -> ICON_WEB
            KiteRecipe.TYPE_SCRIPT_WEB, KiteRecipe.TYPE_COMMAND_WEB -> ICON_TERMINAL
            KiteRecipe.TYPE_START_SERVICE -> ICON_SERVER
            KiteRecipe.TYPE_TEMPLATE -> ICON_TOOLS
            else -> ICON_DEFAULT
        }

        fun normalizeName(name: String, recipeType: String): String {
            val normalized = when (name.trim().lowercase()) {
                "hermes" -> ICON_TERMINAL
                ">_", "terminal", "cmd", "shell" -> ICON_TERMINAL
                "web", "globe", "link" -> ICON_WEB
                "folder", "files" -> ICON_FILE
                "log" -> ICON_LOGS
                "service", "play" -> ICON_SERVER
                "more" -> ICON_MORE
                "", "占位", "默认" -> defaultNameForType(recipeType)
                else -> name.trim().lowercase()
            }
            return if (normalized in BUILTIN) normalized else defaultNameForType(recipeType)
        }
    }
}

data class KiteRecipeCard(
    val accent: String = "blue",
    val status: String = "unknown"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accent", accent)
        .put("status", status)

    companion object {
        fun fromJson(json: JSONObject, recipeType: String, iconName: String): KiteRecipeCard = KiteRecipeCard(
            accent = resolvedAccentFor(iconName, recipeType, json.optString("accent")),
            status = json.optString("status", "unknown")
        )

        fun defaultForType(recipeType: String): KiteRecipeCard = KiteRecipeCard(
            accent = defaultAccentForIcon(KiteRecipeIcon.defaultNameForType(recipeType), recipeType),
            status = "unknown"
        )

        fun defaultAccentForType(recipeType: String): String = when (recipeType) {
            KiteRecipe.TYPE_COMMAND_WEB, KiteRecipe.TYPE_SCRIPT_WEB, KiteRecipe.TYPE_START_SERVICE -> "green"
            KiteRecipe.TYPE_TEMPLATE -> "purple"
            else -> "blue"
        }

        fun defaultAccentForIcon(iconName: String, recipeType: String): String = when (KiteRecipeIcon.normalizeName(iconName, recipeType)) {
            KiteRecipeIcon.ICON_TERMINAL,
            KiteRecipeIcon.ICON_SERVER,
            KiteRecipeIcon.ICON_CODE,
            KiteRecipeIcon.ICON_BOT -> "green"
            KiteRecipeIcon.ICON_LOGS,
            KiteRecipeIcon.ICON_MUSIC,
            KiteRecipeIcon.ICON_SHOPPING -> "orange"
            KiteRecipeIcon.ICON_TOOLS,
            KiteRecipeIcon.ICON_MORE,
            KiteRecipeIcon.ICON_DEFAULT -> "purple"
            KiteRecipeIcon.ICON_WEB,
            KiteRecipeIcon.ICON_FILE -> "blue"
            else -> defaultAccentForType(recipeType)
        }

        fun resolvedAccentFor(iconName: String, recipeType: String, storedAccent: String?): String {
            val recommended = defaultAccentForIcon(iconName, recipeType)
            val normalized = normalizeAccent(storedAccent)
            if (normalized.isBlank()) return recommended
            if (isWorkflowRecipe(recipeType) && normalized in LEGACY_WORKFLOW_ACCENTS) return recommended
            return if (normalized in SUPPORTED_ACCENTS) normalized else recommended
        }

        private val SUPPORTED_ACCENTS = setOf("green", "blue", "purple", "orange")
        private val LEGACY_WORKFLOW_ACCENTS = setOf("blue", "teal", "cyan")

        private fun normalizeAccent(accent: String?): String =
            accent?.trim()?.lowercase().orEmpty()

        private fun isWorkflowRecipe(recipeType: String): Boolean =
            recipeType == KiteRecipe.TYPE_COMMAND_WEB ||
                recipeType == KiteRecipe.TYPE_SCRIPT_WEB ||
                recipeType == KiteRecipe.TYPE_START_SERVICE
    }
}

data class KiteExecution(
    val mode: String,
    val steps: List<KiteRecipeStep> = emptyList(),
    val script: String? = null,
    val workdir: String? = null,
    val timeoutMs: Long? = null,
    val action: String? = null,
    val params: JSONObject? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .apply {
            if (steps.isNotEmpty()) {
                put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
            }
            if (!script.isNullOrBlank()) put("script", script)
            if (!workdir.isNullOrBlank()) put("workdir", workdir)
            if (timeoutMs != null) put("timeoutMs", timeoutMs)
            if (!action.isNullOrBlank()) put("action", action)
            if (params != null) put("params", params)
        }

    companion object {
        fun steps(steps: List<KiteRecipeStep>): KiteExecution =
            KiteExecution(mode = KiteRecipe.EXECUTION_STEPS, steps = steps)

        fun fromJson(json: JSONObject): KiteExecution = KiteExecution(
            mode = json.optString("mode", KiteRecipe.EXECUTION_STEPS),
            steps = KiteRecipe.parseSteps(json.optJSONArray("steps") ?: JSONArray()),
            script = json.optString("script").ifBlank { null },
            workdir = json.optString("workdir").ifBlank { null },
            timeoutMs = if (json.has("timeoutMs")) json.optLong("timeoutMs") else null,
            action = json.optString("action").ifBlank { null },
            params = json.optJSONObject("params")
        )
    }
}

data class KiteRecipeStep(
    val id: String,
    val type: String,
    val cmd: String? = null,
    val runMode: String? = null,
    val workdir: String? = null,
    val timeoutMs: Long? = null,
    val delayAfterMs: Long? = null,
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
            KiteRecipe.normalizeRunMode(runMode)?.let { put("runMode", it) }
            if (!workdir.isNullOrBlank()) put("workdir", workdir)
            if (timeoutMs != null) put("timeoutMs", timeoutMs)
            if (delayAfterMs != null) put("delayAfterMs", delayAfterMs)
            if (expected != null) put("expected", expected.toJson())
            if (outputPolicy != null) put("outputPolicy", outputPolicy.toJson())
            if (!url.isNullOrBlank()) put("url", url)
        }

    companion object {
        fun fromJson(json: JSONObject, index: Int = 0): KiteRecipeStep {
            val type = json.getString("type")
            val legacyWait = if (json.has("wait")) json.optBoolean("wait") else null
            val runMode = KiteRecipe.normalizeRunMode(json.optString("runMode"), legacyWait)
            return KiteRecipeStep(
                id = json.optString("id").ifBlank { "step_${index + 1}_$type" },
                type = type,
                cmd = json.optString("cmd").ifBlank { null },
                runMode = runMode,
                workdir = json.optString("workdir").ifBlank { null },
                timeoutMs = if (json.has("timeoutMs")) json.optLong("timeoutMs") else null,
                delayAfterMs = if (json.has("delayAfterMs")) json.optLong("delayAfterMs") else null,
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
    val pid: String? = null,
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
            if (!pid.isNullOrBlank()) put("pid", pid)
            if (nextAction != null) put("nextAction", nextAction.toJson())
        }

    fun openWebUrlIfFinished(): String? =
        nextAction?.takeIf { status == STATUS_FINISHED && ok && it.type == KiteRecipe.STEP_OPEN_WEB }?.url

    fun openWebUrlIfPresent(): String? =
        nextAction?.takeIf { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url

    fun hasMismatch(): Boolean = steps.any { it.matchResult?.enabled == true && it.matchResult.matched == false }

    fun lastMeaningfulOutput(): String? =
        steps.asReversed().firstNotNullOfOrNull { step ->
            step.lastMeaningfulOutput.ifBlank {
                step.stderrTail.ifBlank { step.stdoutTail }
            }.ifBlank { null }
        }

    companion object {
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_RUNNING = "running"
        const val STATUS_ALREADY_RUNNING = "already_running"
        const val STATUS_FINISHED = "finished"
        const val STATUS_FAILED = "failed"
        const val STATUS_STOPPED = "stopped"
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
                pid = json.optString("pid").ifBlank { null },
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
