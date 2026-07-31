package com.kite.app.recipe

import org.json.JSONArray
import org.json.JSONObject

data class KiteRecipe(
    val schemaVersion: Int = PROTOCOL_VERSION,
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val category: String = "",
    val groupId: String = "",
    val defaultUrl: String,
    val shortcut: Boolean,
    val icon: KiteRecipeIcon = KiteRecipeIcon.defaultForType(type),
    val card: KiteRecipeCard = KiteRecipeCard.defaultForType(type),
    val launch: KiteLaunchConfig = KiteLaunchConfig(),
    val execution: KiteExecution = KiteExecution.steps(emptyList()),
    val actions: Map<String, KiteRecipeAction> = emptyMap(),
    val expected: KiteExpectedResult? = null,
    val taskLabel: String = "",
    val taskMode: String = "",
    val runtimeSource: String = SOURCE_ASSETS
) {
    val steps: List<KiteRecipeStep>
        get() = actions[ACTION_START]?.steps?.takeIf { it.isNotEmpty() } ?: execution.steps

    val status: String
        get() = "unknown"

    fun action(name: String): KiteRecipeAction? =
        actions[name] ?: legacyAction(name)

    fun actionSteps(name: String = ACTION_START): List<KiteRecipeStep> =
        action(name)?.steps.orEmpty()

    fun hasShellStep(actionName: String = ACTION_START): Boolean =
        actionSteps(actionName).any { it.type == STEP_SHELL && !it.cmd.isNullOrBlank() }

    fun hasUbuntuStep(actionName: String = ACTION_START): Boolean =
        actionSteps(actionName).any {
            it.type == STEP_SHELL || it.type == STEP_TERMINAL || it.type == STEP_X11 || it.type == STEP_AGENT
        }

    fun openWebUrl(actionName: String = ACTION_START): String =
        openWebUrlFromSteps(actionSteps(actionName)).ifBlank { defaultUrl }

    fun firstShellStep(actionName: String = ACTION_START): KiteRecipeStep? =
        actionSteps(actionName).firstOrNull { it.type == STEP_SHELL }

    fun asExecutionRecipe(actionName: String = ACTION_START): KiteRecipe {
        val action = action(actionName) ?: return this
        return copy(
            execution = KiteExecution.steps(action.steps),
            actions = linkedMapOf(ACTION_START to action.copy(id = ACTION_START)),
            expected = action.expected ?: expected
        )
    }

    fun toJson(includeLocalIdentity: Boolean = false): JSONObject = JSONObject()
        .put(
            "base",
            JSONObject()
                .put("id", if (includeLocalIdentity) id else "")
                .put("name", name)
                .put("description", description)
                .apply {
                    if (includeLocalIdentity) {
                        put("category", normalizeCategory(category))
                        put("groupId", normalizeGroupId(groupId))
                    }
                }
                .put("icon", icon.toJson())
        )
        .put("card", card.toJson())
        .put("launch", launch.toJson())
        .put("recipe", JSONArray().apply {
            mainRecipeSteps().forEach { put(it.toJson()) }
        })

    private fun effectiveActions(): Map<String, KiteRecipeAction> =
        actions.ifEmpty { defaultActionsFor(execution.steps, defaultUrl) }

    private fun mainRecipeSteps(): List<KiteRecipeStep> =
        effectiveActions()[ACTION_START]?.steps
            ?: execution.steps

    private fun legacyAction(name: String): KiteRecipeAction? = when (name) {
        ACTION_START -> KiteRecipeAction(
            id = ACTION_START,
            steps = execution.steps,
            expected = expected
        ).takeIf { it.steps.isNotEmpty() || defaultUrl.isNotBlank() }

        ACTION_OPEN -> openWebUrlFromSteps(execution.steps).ifBlank { defaultUrl }
            .takeIf { it.isNotBlank() }
            ?.let { url ->
                KiteRecipeAction(
                    id = ACTION_OPEN,
                    steps = listOf(KiteRecipeStep(id = "step_open_$id", type = STEP_OPEN_WEB, url = url))
                )
            }

        else -> null
    }

    private fun openWebUrlFromSteps(candidateSteps: List<KiteRecipeStep>): String =
        candidateSteps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url.orEmpty()

    companion object {
        const val PROTOCOL_VERSION = 1

        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_OPEN = "open"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESTART = "restart"

        const val TYPE_OPEN_URL = "open_url"
        const val TYPE_START_SERVICE = "start_service"
        const val TYPE_COMMAND_WEB = "command_web"
        const val TYPE_SCRIPT_WEB = "script_web"
        const val TYPE_AGENT = "agent"
        const val TYPE_TEMPLATE = "template"
        const val CATEGORY_UNCATEGORIZED = "uncategorized"

        const val EXECUTION_STEPS = "steps"
        const val EXECUTION_SCRIPT = "script"
        const val EXECUTION_ANDROID_ACTION = "android_action"

        const val STEP_OPEN_WEB = "open_web"
        const val STEP_SHELL = "shell"
        const val STEP_TERMINAL = "terminal"
        const val STEP_X11 = "x11"
        const val STEP_AGENT = "agent"
        const val STEP_ANDROID_ACTION = "android_action"
        const val STEP_NATIVE_CAPABILITY = "native_capability"

        const val ANDROID_ACTION_PREPARE_AI_ENV = "prepare_ai_env"
        const val ANDROID_ACTION_TOOLCHAIN_DOCTOR = "toolchain_doctor"
        const val ANDROID_ACTION_INSTALL_APK = "install_apk"

        const val RUN_MODE_ATTACHED = "attached"
        const val RUN_MODE_WAIT = "wait"
        const val RUN_MODE_DETACHED = "detached"
        const val RUN_MODE_BACKGROUND = "background"

        const val SURFACE_MODE_AUTO = "auto"
        const val SURFACE_MODE_PANEL = "panel"
        const val SURFACE_MODE_SILENT = "silent"

        const val OUTPUT_LAST_MEANINGFUL = "lastMeaningfulOutput"

        const val SOURCE_ASSETS = "assets"
        const val SOURCE_SHARED = "shared"
        const val SOURCE_USER = "user"
        const val SOURCE_IMPORTED = "imported"
        const val SOURCE_DROPZONE = "dropzone"
        const val SOURCE_REMOTE = "remote"

        fun fromJson(json: JSONObject, runtimeSource: String): KiteRecipe {
            val base = json.optJSONObject("base")
            val header = base ?: json.optJSONObject("header")
            val legacyType = json.optString("type")
            val parsedActions = parseRecipe(json.optJSONArray("recipe")).ifEmpty {
                parseActions(json.optJSONObject("actions"))
            }
            val execution = parseExecution(json, parsedActions)
            val type = legacyType.ifBlank { inferTypeFromSteps(parsedActions[ACTION_START]?.steps ?: execution.steps) }
            val expected = json.optJSONObject("expected")?.let { KiteExpectedResult.fromJson(it) }
            val icon = parseIcon(json, header, type)
            val card = parseCard(json, icon, type)
            val actions = parsedActions.ifEmpty { defaultActionsFor(execution.steps, json.optString("defaultUrl")) }
            val defaultUrl = json.optString("defaultUrl").ifBlank {
                openWebUrlFromActions(actions).ifBlank { firstOpenWebUrl(execution.steps) }
            }
            return KiteRecipe(
                schemaVersion = json.optInt("schemaVersion", PROTOCOL_VERSION),
                id = base?.optString("id")?.ifBlank { null }
                    ?: json.optString("id").ifBlank {
                    header?.optString("name")?.ifBlank { null }
                        ?: json.optString("name").ifBlank { "card" }
                },
                name = header?.optString("name")?.ifBlank { null }
                    ?: json.optString("name").ifBlank { json.getString("id") },
                description = header?.optString("description") ?: json.optString("description"),
                type = type,
                category = normalizeCategory(header?.optString("category") ?: json.optString("category")),
                groupId = normalizeGroupId(header?.optString("groupId") ?: json.optString("groupId")),
                defaultUrl = defaultUrl,
                shortcut = header?.optBoolean("shortcut") ?: json.optBoolean("shortcut", false),
                icon = icon,
                card = card,
                launch = KiteLaunchConfig.fromJson(json.optJSONObject("launch")),
                execution = execution,
                actions = actions,
                expected = expected,
                taskLabel = json.optString("taskLabel"),
                taskMode = json.optString("taskMode"),
                runtimeSource = normalizeSource(runtimeSource)
            )
        }

        private fun parseExecution(
            json: JSONObject,
            actions: Map<String, KiteRecipeAction>
        ): KiteExecution {
            val executionJson = json.optJSONObject("execution")
            if (executionJson != null) return KiteExecution.fromJson(executionJson)
            actions[ACTION_START]?.steps?.takeIf { it.isNotEmpty() }?.let { return KiteExecution.steps(it) }

            val legacySteps = parseSteps(json.optJSONArray("steps") ?: JSONArray())
            return KiteExecution(mode = EXECUTION_STEPS, steps = legacySteps)
        }

        private fun parseRecipe(recipeJson: JSONArray?): Map<String, KiteRecipeAction> {
            if (recipeJson == null) return emptyMap()
            val steps = parseSteps(recipeJson)
            return if (steps.isEmpty()) {
                emptyMap()
            } else {
                linkedMapOf(ACTION_START to KiteRecipeAction(id = ACTION_START, steps = steps))
            }
        }

        private fun parseIcon(json: JSONObject, header: JSONObject?, type: String): KiteRecipeIcon {
            val iconJson = header?.optJSONObject("icon") ?: json.optJSONObject("icon")
            if (iconJson != null) return KiteRecipeIcon.fromJson(iconJson, type)

            val legacyIcon = json.optString("icon")
            return if (legacyIcon.isNotBlank()) {
                KiteRecipeIcon(type = "builtin", name = KiteRecipeIcon.normalizeName(legacyIcon, type))
            } else {
                KiteRecipeIcon.defaultForType(type)
            }
        }

        private fun parseCard(json: JSONObject, icon: KiteRecipeIcon, type: String): KiteRecipeCard {
            val cardJson = json.optJSONObject("card")
            if (cardJson != null) return KiteRecipeCard.fromJson(cardJson, type, icon.name)
            val legacyCardJson = JSONObject().apply {
                if (json.has("accent")) put("accent", json.optString("accent"))
                if (json.has("status")) put("status", json.optString("status"))
            }
            return if (legacyCardJson.length() > 0) {
                KiteRecipeCard.fromJson(legacyCardJson, type, icon.name)
            } else {
                KiteRecipeCard.defaultForType(type)
            }
        }

        internal fun parseSteps(stepsJson: JSONArray): List<KiteRecipeStep> = buildList {
            for (index in 0 until stepsJson.length()) {
                add(KiteRecipeStep.fromJson(stepsJson.getJSONObject(index), index))
            }
        }

        private fun parseActions(actionsJson: JSONObject?): Map<String, KiteRecipeAction> {
            if (actionsJson == null) return emptyMap()
            val actions = linkedMapOf<String, KiteRecipeAction>()
            val keys = actionsJson.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                when (val raw = actionsJson.opt(name)) {
                    is JSONObject -> actions[name] = KiteRecipeAction.fromJson(name, raw)
                    is JSONArray -> actions[name] = KiteRecipeAction(id = name, steps = parseSteps(raw))
                }
            }
            return actions
        }

        fun defaultActionsFor(steps: List<KiteRecipeStep>, defaultUrl: String): Map<String, KiteRecipeAction> {
            val actions = linkedMapOf<String, KiteRecipeAction>()
            if (steps.isNotEmpty()) {
                actions[ACTION_START] = KiteRecipeAction(id = ACTION_START, steps = steps)
            }
            val openUrl = steps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url
                ?: defaultUrl
            if (!openUrl.isNullOrBlank()) {
                actions[ACTION_OPEN] = KiteRecipeAction(
                    id = ACTION_OPEN,
                    steps = listOf(KiteRecipeStep(id = "step_open", type = STEP_OPEN_WEB, url = openUrl))
                )
            }
            return actions
        }

        private fun openWebUrlFromActions(actions: Map<String, KiteRecipeAction>): String =
            firstOpenWebUrl(actions[ACTION_OPEN]?.steps.orEmpty()).ifBlank {
                firstOpenWebUrl(actions[ACTION_START]?.steps.orEmpty())
            }

        private fun firstOpenWebUrl(steps: List<KiteRecipeStep>): String =
            steps.firstOrNull { it.type == STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url.orEmpty()

        private fun inferTypeFromSteps(steps: List<KiteRecipeStep>): String {
            val hasAgent = steps.any { it.type == STEP_AGENT }
            val hasCommand = steps.any {
                it.type == STEP_SHELL || it.type == STEP_TERMINAL || it.type == STEP_X11
            }
            val hasOpenWeb = steps.any { it.type == STEP_OPEN_WEB }
            return when {
                hasAgent && !hasCommand && !hasOpenWeb -> TYPE_AGENT
                hasCommand && hasOpenWeb -> TYPE_COMMAND_WEB
                hasCommand || hasAgent -> TYPE_START_SERVICE
                hasOpenWeb -> TYPE_OPEN_URL
                else -> TYPE_TEMPLATE
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

        fun normalizeSurfaceMode(surfaceMode: String?): String =
            when (surfaceMode?.trim()?.lowercase()) {
                SURFACE_MODE_PANEL -> SURFACE_MODE_PANEL
                SURFACE_MODE_SILENT -> SURFACE_MODE_SILENT
                else -> SURFACE_MODE_AUTO
            }

        private fun normalizeSource(source: String): String = when (source) {
            "asset" -> SOURCE_ASSETS
            SOURCE_ASSETS, SOURCE_SHARED, SOURCE_USER, SOURCE_IMPORTED, SOURCE_DROPZONE, SOURCE_REMOTE -> source
            else -> SOURCE_USER
        }

        fun normalizeCategory(category: String?): String {
            val normalized = category?.trim().orEmpty()
            return if (normalized.equals(CATEGORY_UNCATEGORIZED, ignoreCase = true)) "" else normalized
        }

        fun normalizeGroupId(groupId: String?): String =
            groupId?.trim().orEmpty()
    }
}

data class KiteRecipeIcon(
    val type: String = "builtin",
    val name: String = ICON_DEFAULT,
    val source: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("name", name)
        .apply {
            if (source.isNotBlank()) put("source", source)
        }

    companion object {
        const val TYPE_BUILTIN = "builtin"
        const val TYPE_IMAGE = "image"

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

        fun fromJson(json: JSONObject, recipeType: String): KiteRecipeIcon {
            val iconType = json.optString("type", TYPE_BUILTIN).trim().lowercase()
            if (iconType == TYPE_IMAGE || iconType == "local") {
                return KiteRecipeIcon(
                    type = TYPE_IMAGE,
                    name = json.optString("name").ifBlank { "custom" },
                    source = json.optString("source").ifBlank { json.optString("path") }
                )
            }
            return KiteRecipeIcon(
                type = TYPE_BUILTIN,
                name = normalizeName(json.optString("name"), recipeType)
            )
        }

        fun defaultForType(recipeType: String): KiteRecipeIcon = KiteRecipeIcon(
            type = TYPE_BUILTIN,
            name = defaultNameForType(recipeType)
        )

        fun defaultNameForType(recipeType: String): String = when (recipeType) {
            KiteRecipe.TYPE_OPEN_URL -> ICON_WEB
            KiteRecipe.TYPE_SCRIPT_WEB, KiteRecipe.TYPE_COMMAND_WEB -> ICON_TERMINAL
            KiteRecipe.TYPE_AGENT -> ICON_BOT
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
    val accent: String = "primary",
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

        @Suppress("UNUSED_PARAMETER")
        fun defaultAccentForType(recipeType: String): String = ACCENT_PRIMARY

        @Suppress("UNUSED_PARAMETER")
        fun defaultAccentForIcon(iconName: String, recipeType: String): String = ACCENT_PRIMARY

        fun resolvedAccentFor(iconName: String, recipeType: String, storedAccent: String?): String {
            val recommended = defaultAccentForIcon(iconName, recipeType)
            val normalized = normalizeAccent(storedAccent)
            if (normalized.isBlank()) return recommended
            return if (normalized in SUPPORTED_ACCENTS) normalized else recommended
        }

        private const val ACCENT_PRIMARY = "primary"
        private val SUPPORTED_ACCENTS = setOf(
            ACCENT_PRIMARY,
            "theme",
            "workflow",
            "green",
            "blue",
            "purple",
            "orange",
            "teal",
            "cyan",
            "mint"
        )

        private fun normalizeAccent(accent: String?): String =
            accent?.trim()?.lowercase().orEmpty()

    }
}

data class KiteLaunchConfig(
    val openInstance: Boolean = true,
    val keepFinishedNotification: Boolean = false
) {
    fun isDefault(): Boolean = openInstance && !keepFinishedNotification

    fun toJson(): JSONObject = JSONObject()
        .put("openInstance", openInstance)
        .put("keepFinishedNotification", keepFinishedNotification)

    companion object {
        fun fromJson(json: JSONObject?): KiteLaunchConfig =
            KiteLaunchConfig(
                openInstance = json?.optBoolean("openInstance", true) ?: true,
                keepFinishedNotification = json?.optBoolean("keepFinishedNotification", false) ?: false
            )
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

data class KiteRecipeAction(
    val id: String,
    val label: String = "",
    val steps: List<KiteRecipeStep> = emptyList(),
    val expected: KiteExpectedResult? = null
) {
    fun toStepsJson(): JSONArray = JSONArray().apply { steps.forEach { put(it.toJson()) } }

    fun toJson(): JSONObject = JSONObject()
        .apply {
            if (label.isNotBlank()) put("label", label)
            put("steps", toStepsJson())
        }

    companion object {
        fun fromJson(id: String, json: JSONObject): KiteRecipeAction =
            KiteRecipeAction(
                id = id,
                label = json.optString("label"),
                steps = KiteRecipe.parseSteps(json.optJSONArray("steps") ?: JSONArray()),
                expected = json.optJSONObject("expected")?.let { KiteExpectedResult.fromJson(it) }
            )
    }
}

data class KiteRecipeStep(
    val id: String,
    val type: String,
    val cmd: String? = null,
    val action: String? = null,
    val params: JSONObject? = null,
    val text: String? = null,
    val session: String? = null,
    val runMode: String? = null,
    val surfaceMode: String = KiteRecipe.SURFACE_MODE_AUTO,
    val workdir: String? = null,
    val timeoutMs: Long? = null,
    val delayAfterMs: Long? = null,
    val expected: KiteExpectedResult? = null,
    val outputPolicy: KiteOutputPolicy? = null,
    val url: String? = null,
    val agentId: String? = null,
    val providerId: String? = null,
    val wait: Boolean? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .apply {
            if (!cmd.isNullOrBlank()) put("cmd", cmd)
            if (!action.isNullOrBlank()) put("action", action)
            if (params != null) put("params", params)
            if (!text.isNullOrBlank()) put("text", text)
            if (!session.isNullOrBlank()) put("session", session)
            if (!runMode.isNullOrBlank()) put("runMode", runMode)
            if (surfaceMode != KiteRecipe.SURFACE_MODE_AUTO) put("surfaceMode", surfaceMode)
            if (!workdir.isNullOrBlank()) put("workdir", workdir)
            if (timeoutMs != null) put("timeoutMs", timeoutMs)
            if (delayAfterMs != null) put("delayAfterMs", delayAfterMs)
            if (!url.isNullOrBlank()) put("url", url)
            if (!agentId.isNullOrBlank()) {
                put("agentId", agentId)
            } else if (!providerId.isNullOrBlank()) {
                // 旧卡只读兼容；任何新写入都必须使用 agentId。
                put("providerId", providerId)
            }
        }

    companion object {
        fun fromJson(json: JSONObject, index: Int = 0): KiteRecipeStep {
            val type = json.getString("type")
            val legacyWait = if (json.has("wait")) json.optBoolean("wait") else null
            val runMode = KiteRecipe.normalizeRunMode(json.optString("runMode"), legacyWait)
            val surfaceMode = KiteRecipe.normalizeSurfaceMode(json.optString("surfaceMode"))
            return KiteRecipeStep(
                id = json.optString("id").ifBlank { "step_${index + 1}_$type" },
                type = type,
                cmd = json.optString("cmd").ifBlank { null },
                action = json.optString("action").ifBlank { null },
                params = json.optJSONObject("params"),
                text = json.optString("text").ifBlank { null },
                session = json.optString("session").ifBlank { null },
                runMode = runMode,
                surfaceMode = surfaceMode,
                workdir = json.optString("workdir").ifBlank { null },
                timeoutMs = if (json.has("timeoutMs")) json.optLong("timeoutMs") else null,
                delayAfterMs = if (json.has("delayAfterMs")) json.optLong("delayAfterMs") else null,
                expected = json.optJSONObject("expected")?.let { KiteExpectedResult.fromJson(it) },
                outputPolicy = json.optJSONObject("outputPolicy")?.let { KiteOutputPolicy.fromJson(it) }
                    ?: if (type == KiteRecipe.STEP_SHELL) KiteOutputPolicy() else null,
                url = json.optString("url").ifBlank { null },
                agentId = json.optString("agentId").ifBlank { null },
                providerId = json.optString("providerId").ifBlank { null },
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
    val rootPid: String? = null,
    val processGroupId: String? = null,
    val systemSessionId: String? = null,
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
            if (!rootPid.isNullOrBlank()) put("rootPid", rootPid)
            if (!processGroupId.isNullOrBlank()) put("processGroupId", processGroupId)
            if (!systemSessionId.isNullOrBlank()) put("systemSessionId", systemSessionId)
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
                rootPid = json.optString("rootPid").ifBlank { null },
                processGroupId = json.optString("processGroupId").ifBlank { null },
                systemSessionId = json.optString("systemSessionId").ifBlank { null },
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
