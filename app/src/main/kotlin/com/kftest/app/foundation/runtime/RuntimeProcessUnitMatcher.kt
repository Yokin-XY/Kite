package com.kftest.app.foundation.runtime

import android.content.Context
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File

enum class RuntimeProcessUnitMatchState {
    MATCHED_EXACT,
    MATCHED_RUNTIME_ID,
    MATCHED_PID_FILE,
    MATCHED_PROCESS_GROUP,
    MATCHED_COMMAND_EXACT,
    MATCHED_COMMAND_CONTAINS,
    MATCH_AMBIGUOUS,
    MATCH_STALE_PID_FILE,
    MATCH_NOT_FOUND,
    UNMANAGED_OBSERVED
}

enum class RuntimeProcessUnitMatchSource {
    BUILT_IN_CORE,
    RUNTIME_ID,
    PID_FILE,
    PROCESS_GROUP,
    COMMAND_EXACT,
    COMMAND_CONTAINS,
    UNMANAGED,
    NONE
}

enum class RuntimeProcessUnitMatchConfidence {
    EXACT,
    STRONG,
    MEDIUM,
    WEAK,
    AMBIGUOUS,
    NONE
}

enum class RuntimeProcessUnitPidFileReadState {
    RESOLVED,
    PATH_NOT_ALLOWED,
    HOST_MAPPING_UNAVAILABLE,
    PATH_NOT_FOUND,
    EMPTY,
    INVALID_CONTENT,
    READ_FAILED
}

object RuntimeProcessUnitPidFilePathPolicy {
    fun isAllowed(path: String?): Boolean {
        val normalized = normalize(path) ?: return false
        return normalized.isSameOrDescendantOf("/workspace") ||
            normalized.isSameOrDescendantOf("/run") ||
            normalized.isSameOrDescendantOf("/tmp")
    }

    fun normalize(path: String?): String? {
        val trimmed = path?.trim()?.replace('\\', '/') ?: return null
        if (!trimmed.startsWith("/")) return null
        val parts = mutableListOf<String>()
        trimmed.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> return null
                else -> parts += segment
            }
        }
        return "/${parts.joinToString("/")}"
    }
}

data class RuntimeProcessUnitPidFileRead(
    val state: RuntimeProcessUnitPidFileReadState,
    val pid: Int? = null,
    val reason: String
)

fun interface RuntimeProcessUnitPidFileReader {
    fun read(path: String): RuntimeProcessUnitPidFileRead

    companion object {
        fun noop(): RuntimeProcessUnitPidFileReader {
            return RuntimeProcessUnitPidFileReader {
                RuntimeProcessUnitPidFileRead(
                    state = RuntimeProcessUnitPidFileReadState.HOST_MAPPING_UNAVAILABLE,
                    reason = "pid_file_reader_unavailable"
                )
            }
        }

        fun fromContext(context: Context): RuntimeProcessUnitPidFileReader {
            val container = WorkSurfaceRuntimeBridge.getSavedContainer(context.applicationContext)
                ?: return noop()
            return SafeContainerPidFileReader(
                rootfsDir = File(container.rootfsPath),
                workspaceDir = File(container.workspacePath)
            )
        }
    }
}

data class RuntimeProcessUnitMatchEvaluation(
    val classification: RuntimeProcessUnitClassification? = null,
    val matchState: RuntimeProcessUnitMatchState = RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED,
    val matchSource: RuntimeProcessUnitMatchSource = RuntimeProcessUnitMatchSource.UNMANAGED,
    val matchConfidence: RuntimeProcessUnitMatchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
    val matchedPid: Int? = null,
    val matchedPgid: Int? = null,
    val matchedSid: Int? = null,
    val conflictUnitIds: List<String> = emptyList(),
    val fallbackReason: String = "none"
)

object RuntimeProcessUnitMatcher {
    fun evaluateRoot(
        manifest: RuntimeProcessUnitManifest,
        root: RuntimeRootSnapshot,
        roots: List<RuntimeRootSnapshot> = listOf(root),
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop()
    ): RuntimeProcessUnitMatchEvaluation {
        RuntimeProcessUnitManifest.builtInClassification(root)?.let { builtIn ->
            return RuntimeProcessUnitMatchEvaluation(
                classification = builtIn,
                matchState = RuntimeProcessUnitMatchState.MATCHED_EXACT,
                matchSource = RuntimeProcessUnitMatchSource.BUILT_IN_CORE,
                matchConfidence = RuntimeProcessUnitMatchConfidence.EXACT,
                matchedPid = root.bestPid(),
                matchedPgid = root.rootProcessGroupId,
                matchedSid = root.rootSessionId,
                fallbackReason = builtIn.reason
            )
        }

        val candidates = manifest.units.mapNotNull { unit ->
            candidateFor(
                unit = unit,
                root = root,
                roots = roots,
                pidFileReader = pidFileReader
            )
        }
        if (candidates.isEmpty()) {
            val pidFileFallback = manifest.units
                .mapNotNull { unit ->
                    unit.match.pidFile
                        ?.takeIf { it.isNotBlank() }
                        ?.let { pidFileFallbackFor(unit, roots, pidFileReader) }
                }
                .firstOrNull { it.matchState != RuntimeProcessUnitMatchState.MATCH_NOT_FOUND }
                ?: manifest.units
                    .firstOrNull { !it.match.pidFile.isNullOrBlank() }
                    ?.let { pidFileFallbackFor(it, roots, pidFileReader) }
            return pidFileFallback ?: RuntimeProcessUnitMatchEvaluation(
                matchState = if (manifest.units.isEmpty()) {
                    RuntimeProcessUnitMatchState.UNMANAGED_OBSERVED
                } else {
                    RuntimeProcessUnitMatchState.MATCH_NOT_FOUND
                },
                matchSource = RuntimeProcessUnitMatchSource.UNMANAGED,
                matchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
                matchedPid = root.bestPid(),
                matchedPgid = root.rootProcessGroupId,
                matchedSid = root.rootSessionId,
                fallbackReason = if (manifest.units.isEmpty()) {
                    "no_runtime_process_manifest_units"
                } else {
                    "no_process_unit_match"
                }
            )
        }

        val bestPriority = candidates.maxOf { it.priority }
        val top = candidates.filter { it.priority == bestPriority }
        val selected = top.first()
        val conflictUnitIds = candidates
            .map { it.unit.id }
            .filter { it != selected.unit.id }
            .distinct()
        val ambiguous = top.size > 1
        return selected.toEvaluation(
            matchState = if (ambiguous) RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS else selected.matchState,
            matchConfidence = if (ambiguous) RuntimeProcessUnitMatchConfidence.AMBIGUOUS else selected.matchConfidence,
            conflictUnitIds = conflictUnitIds,
            fallbackReason = if (ambiguous) {
                appendFallback(selected.fallbackReason, "same_priority_unit_match_conflict")
            } else {
                selected.fallbackReason
            }
        )
    }

    fun applyToRoots(
        manifest: RuntimeProcessUnitManifest,
        roots: List<RuntimeRootSnapshot>,
        pidFileReader: RuntimeProcessUnitPidFileReader = RuntimeProcessUnitPidFileReader.noop()
    ): List<RuntimeRootSnapshot> {
        val initial = roots.map { root ->
            root to evaluateRoot(
                manifest = manifest,
                root = root,
                roots = roots,
                pidFileReader = pidFileReader
            )
        }
        val manifestMatchesByUnit = initial
            .mapNotNull { (_, evaluation) ->
                evaluation.classification
                    ?.takeIf { it.source != "android_builtin" }
                    ?.let { it.unitId to evaluation }
            }
            .groupBy({ it.first }, { it.second })
        val ambiguousUnitIds = manifestMatchesByUnit
            .filter { (_, evaluations) ->
                evaluations.size > 1 && evaluations.any { evaluation ->
                    evaluation.classification?.tier == RuntimeProcessUnitTier.USER_LOCKED ||
                        evaluation.matchSource == RuntimeProcessUnitMatchSource.COMMAND_CONTAINS
                }
            }
            .keys
        return initial.map { (root, evaluation) ->
            val adjusted = if (evaluation.classification?.unitId in ambiguousUnitIds) {
                evaluation.copy(
                    matchState = RuntimeProcessUnitMatchState.MATCH_AMBIGUOUS,
                    matchConfidence = RuntimeProcessUnitMatchConfidence.AMBIGUOUS,
                    fallbackReason = appendFallback(
                        evaluation.fallbackReason,
                        "process_unit_matched_multiple_roots"
                    )
                )
            } else {
                evaluation
            }
            root.withProcessUnitMatch(adjusted)
        }
    }

    private fun candidateFor(
        unit: RuntimeProcessUnitDefinition,
        root: RuntimeRootSnapshot,
        roots: List<RuntimeRootSnapshot>,
        pidFileReader: RuntimeProcessUnitPidFileReader
    ): MatchCandidate? {
        unit.match.runtimeId
            ?.takeIf { it.isNotBlank() }
            ?.let { expected ->
                if (root.ownerKind == RuntimeRootOwnerKind.BACKGROUND_RUNTIME && root.ownerId == expected) {
                    return MatchCandidate(
                        unit = unit,
                        priority = 600,
                        matchState = RuntimeProcessUnitMatchState.MATCHED_RUNTIME_ID,
                        matchSource = RuntimeProcessUnitMatchSource.RUNTIME_ID,
                        matchConfidence = RuntimeProcessUnitMatchConfidence.EXACT,
                        matchedPid = root.bestPid(),
                        matchedPgid = root.rootProcessGroupId,
                        matchedSid = root.rootSessionId,
                        fallbackReason = "matched_background_runtime_registry_runtime_id"
                    )
                }
            }

        val pidFallback = unit.match.pidFile
            ?.takeIf { it.isNotBlank() }
            ?.let {
                pidFileCandidateFor(
                    unit = unit,
                    root = root,
                    roots = roots,
                    pidFileReader = pidFileReader
                )
            }
        if (pidFallback is PidFileCandidate.Match) {
            return pidFallback.candidate
        }
        val fallbackReason = (pidFallback as? PidFileCandidate.Fallback)?.reason

        unit.match.processGroupId
            ?.takeIf { it > 0 }
            ?.let { expected ->
                if (root.rootProcessGroupId == expected) {
                    return MatchCandidate(
                        unit = unit,
                        priority = 400,
                        matchState = RuntimeProcessUnitMatchState.MATCHED_PROCESS_GROUP,
                        matchSource = RuntimeProcessUnitMatchSource.PROCESS_GROUP,
                        matchConfidence = RuntimeProcessUnitMatchConfidence.MEDIUM,
                        matchedPid = root.bestPid(),
                        matchedPgid = root.rootProcessGroupId,
                        matchedSid = root.rootSessionId,
                        fallbackReason = appendFallback(fallbackReason, "matched_process_group")
                    )
                }
            }

        unit.match.exactCommand
            ?.takeIf { it.isNotBlank() }
            ?.let { expected ->
                if (root.commandLine == expected || root.title == expected) {
                    return MatchCandidate(
                        unit = unit,
                        priority = 300,
                        matchState = RuntimeProcessUnitMatchState.MATCHED_COMMAND_EXACT,
                        matchSource = RuntimeProcessUnitMatchSource.COMMAND_EXACT,
                        matchConfidence = RuntimeProcessUnitMatchConfidence.STRONG,
                        matchedPid = root.bestPid(),
                        matchedPgid = root.rootProcessGroupId,
                        matchedSid = root.rootSessionId,
                        fallbackReason = appendFallback(fallbackReason, "matched_exact_command")
                    )
                }
            }

        val text = root.commandSearchText()
        if (unit.match.commandContains.any { needle -> needle.lowercase() in text }) {
            return MatchCandidate(
                unit = unit,
                priority = 200,
                matchState = RuntimeProcessUnitMatchState.MATCHED_COMMAND_CONTAINS,
                matchSource = RuntimeProcessUnitMatchSource.COMMAND_CONTAINS,
                matchConfidence = RuntimeProcessUnitMatchConfidence.WEAK,
                matchedPid = root.bestPid(),
                matchedPgid = root.rootProcessGroupId,
                matchedSid = root.rootSessionId,
                fallbackReason = appendFallback(fallbackReason, "matched_command_contains")
            )
        }
        return null
    }

    private fun pidFileCandidateFor(
        unit: RuntimeProcessUnitDefinition,
        root: RuntimeRootSnapshot,
        roots: List<RuntimeRootSnapshot>,
        pidFileReader: RuntimeProcessUnitPidFileReader
    ): PidFileCandidate {
        val pidFile = unit.match.pidFile ?: return PidFileCandidate.Fallback("pid_file_not_declared")
        val read = pidFileReader.read(pidFile)
        if (read.state != RuntimeProcessUnitPidFileReadState.RESOLVED || read.pid == null) {
            return PidFileCandidate.Fallback("pid_file_${read.state.name.lowercase()}:${read.reason}")
        }
        val pointedRoot = roots.firstOrNull { it.matchesPid(read.pid) && it.isRunning }
            ?: return PidFileCandidate.Fallback("pid_file_stale_pid_not_observed:${read.pid}")
        if (pointedRoot.ownershipKey != root.ownershipKey) {
            return PidFileCandidate.Fallback("pid_file_points_to_other_root:${read.pid}")
        }
        if (!unit.commandExpectationMatches(root)) {
            return PidFileCandidate.Fallback("pid_file_command_mismatch:${read.pid}")
        }
        return PidFileCandidate.Match(
            MatchCandidate(
                unit = unit,
                priority = 500,
                matchState = RuntimeProcessUnitMatchState.MATCHED_PID_FILE,
                matchSource = RuntimeProcessUnitMatchSource.PID_FILE,
                matchConfidence = RuntimeProcessUnitMatchConfidence.STRONG,
                matchedPid = read.pid,
                matchedPgid = root.rootProcessGroupId,
                matchedSid = root.rootSessionId,
                fallbackReason = "matched_pid_file"
            )
        )
    }

    private fun pidFileFallbackFor(
        unit: RuntimeProcessUnitDefinition,
        roots: List<RuntimeRootSnapshot>,
        pidFileReader: RuntimeProcessUnitPidFileReader
    ): RuntimeProcessUnitMatchEvaluation {
        val pidFile = unit.match.pidFile.orEmpty()
        val read = pidFileReader.read(pidFile)
        if (read.state != RuntimeProcessUnitPidFileReadState.RESOLVED || read.pid == null) {
            return RuntimeProcessUnitMatchEvaluation(
                matchState = RuntimeProcessUnitMatchState.MATCH_NOT_FOUND,
                matchSource = RuntimeProcessUnitMatchSource.PID_FILE,
                matchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
                fallbackReason = "pid_file_${read.state.name.lowercase()}:${read.reason}"
            )
        }
        val pointedRoot = roots.firstOrNull { it.matchesPid(read.pid) && it.isRunning }
        if (pointedRoot != null && !unit.commandExpectationMatches(pointedRoot)) {
            return RuntimeProcessUnitMatchEvaluation(
                matchState = RuntimeProcessUnitMatchState.MATCH_STALE_PID_FILE,
                matchSource = RuntimeProcessUnitMatchSource.PID_FILE,
                matchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
                matchedPid = read.pid,
                matchedPgid = pointedRoot.rootProcessGroupId,
                matchedSid = pointedRoot.rootSessionId,
                fallbackReason = "pid_file_command_mismatch:${read.pid}"
            )
        }
        return RuntimeProcessUnitMatchEvaluation(
            matchState = if (pointedRoot == null) {
                RuntimeProcessUnitMatchState.MATCH_STALE_PID_FILE
            } else {
                RuntimeProcessUnitMatchState.MATCH_NOT_FOUND
            },
            matchSource = RuntimeProcessUnitMatchSource.PID_FILE,
            matchConfidence = RuntimeProcessUnitMatchConfidence.NONE,
            matchedPid = read.pid,
            matchedPgid = pointedRoot?.rootProcessGroupId,
            matchedSid = pointedRoot?.rootSessionId,
            fallbackReason = if (pointedRoot == null) {
                "pid_file_stale_pid_not_observed:${read.pid}"
            } else {
                "pid_file_points_to_unmatched_root:${read.pid}"
            }
        )
    }

    private data class MatchCandidate(
        val unit: RuntimeProcessUnitDefinition,
        val priority: Int,
        val matchState: RuntimeProcessUnitMatchState,
        val matchSource: RuntimeProcessUnitMatchSource,
        val matchConfidence: RuntimeProcessUnitMatchConfidence,
        val matchedPid: Int?,
        val matchedPgid: Int?,
        val matchedSid: Int?,
        val fallbackReason: String
    ) {
        fun toEvaluation(
            matchState: RuntimeProcessUnitMatchState = this.matchState,
            matchConfidence: RuntimeProcessUnitMatchConfidence = this.matchConfidence,
            conflictUnitIds: List<String> = emptyList(),
            fallbackReason: String = this.fallbackReason
        ): RuntimeProcessUnitMatchEvaluation {
            return RuntimeProcessUnitMatchEvaluation(
                classification = unit.toClassification("matched_runtime_process_manifest_unit"),
                matchState = matchState,
                matchSource = matchSource,
                matchConfidence = matchConfidence,
                matchedPid = matchedPid,
                matchedPgid = matchedPgid,
                matchedSid = matchedSid,
                conflictUnitIds = conflictUnitIds,
                fallbackReason = fallbackReason
            )
        }
    }

    private sealed class PidFileCandidate {
        data class Match(val candidate: MatchCandidate) : PidFileCandidate()
        data class Fallback(val reason: String) : PidFileCandidate()
    }
}

private class SafeContainerPidFileReader(
    private val rootfsDir: File,
    private val workspaceDir: File
) : RuntimeProcessUnitPidFileReader {
    override fun read(path: String): RuntimeProcessUnitPidFileRead {
        val normalized = RuntimeProcessUnitPidFilePathPolicy.normalize(path)
            ?: return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.PATH_NOT_ALLOWED,
                reason = "pid_file_path_must_be_absolute_safe_container_path"
            )
        val baseAndRelative = when {
            normalized.isSameOrDescendantOf("/workspace") ->
                workspaceDir to normalized.removePrefix("/workspace").trimStart('/')
            normalized.isSameOrDescendantOf("/run") ->
                rootfsDir to normalized.trimStart('/')
            normalized.isSameOrDescendantOf("/tmp") ->
                rootfsDir to normalized.trimStart('/')
            else -> return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.PATH_NOT_ALLOWED,
                reason = "pid_file_path_outside_allowed_prefixes"
            )
        }
        val base = baseAndRelative.first
        val relativePath = baseAndRelative.second
        if (base.path.isBlank() || relativePath.isBlank()) {
            return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.PATH_NOT_ALLOWED,
                reason = "pid_file_path_missing_host_mapping"
            )
        }
        val target = File(base, relativePath)
        val safeTarget = runCatching { target.canonicalFile }.getOrNull()
            ?: return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.READ_FAILED,
                reason = "pid_file_canonicalization_failed"
            )
        val safeBase = runCatching { base.canonicalFile }.getOrNull()
            ?: return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.HOST_MAPPING_UNAVAILABLE,
                reason = "pid_file_base_unavailable"
            )
        if (!safeTarget.isSameOrDescendantOf(safeBase)) {
            return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.PATH_NOT_ALLOWED,
                reason = "pid_file_host_path_escape_blocked"
            )
        }
        if (!safeTarget.exists() || !safeTarget.isFile) {
            return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.PATH_NOT_FOUND,
                reason = "pid_file_not_found"
            )
        }
        val text = runCatching { safeTarget.readText().trim() }.getOrElse { error ->
            return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.READ_FAILED,
                reason = error.message ?: "pid_file_read_failed"
            )
        }
        if (text.isBlank()) {
            return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.EMPTY,
                reason = "pid_file_empty"
            )
        }
        val pid = text.lineSequence().firstOrNull()?.trim()?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return RuntimeProcessUnitPidFileRead(
                state = RuntimeProcessUnitPidFileReadState.INVALID_CONTENT,
                reason = "pid_file_content_not_positive_integer"
            )
        return RuntimeProcessUnitPidFileRead(
            state = RuntimeProcessUnitPidFileReadState.RESOLVED,
            pid = pid,
            reason = "pid_file_resolved"
        )
    }
}

private fun RuntimeRootSnapshot.withProcessUnitMatch(
    evaluation: RuntimeProcessUnitMatchEvaluation
): RuntimeRootSnapshot {
    val classification = evaluation.classification ?: return copy(
        processUnitMatchSource = evaluation.matchSource,
        processUnitMatchConfidence = evaluation.matchConfidence,
        processUnitMatchState = evaluation.matchState,
        processUnitMatchedPid = evaluation.matchedPid,
        processUnitMatchedPgid = evaluation.matchedPgid,
        processUnitMatchedSid = evaluation.matchedSid,
        processUnitConflictUnitIds = evaluation.conflictUnitIds,
        processUnitFallbackReason = evaluation.fallbackReason
    )
    val classified = copy(
        processUnitId = classification.unitId,
        processUnitDisplayName = classification.displayName,
        processUnitTier = classification.tier,
        processUnitSource = classification.source,
        processUnitExpectedMemoryLimitKb = classification.expectedMemoryLimitKb,
        processUnitUnlimitedMemory = classification.unlimitedMemory,
        processUnitWarningThresholdRatio = classification.warningThresholdRatio,
        processUnitRestartThresholdRatio = classification.restartThresholdRatio,
        processUnitQuarantineAfterFailures = classification.quarantineAfterFailures,
        processUnitManualKillPolicy = classification.manualKillPolicy,
        processUnitUserEditable = classification.userEditable,
        processUnitAllowReclaim = classification.allowReclaim,
        processUnitAllowKill = classification.allowKill,
        processUnitAllowRestart = classification.allowRestart,
        processUnitRequiresMemoryAdmission = classification.requiresMemoryAdmission,
        processUnitReason = classification.reason,
        processUnitMatchSource = evaluation.matchSource,
        processUnitMatchConfidence = evaluation.matchConfidence,
        processUnitMatchState = evaluation.matchState,
        processUnitMatchedPid = evaluation.matchedPid,
        processUnitMatchedPgid = evaluation.matchedPgid,
        processUnitMatchedSid = evaluation.matchedSid,
        processUnitConflictUnitIds = evaluation.conflictUnitIds,
        processUnitFallbackReason = evaluation.fallbackReason
    )
    return classified.copy(
        processUnitObservedState = RuntimeProcessUnitManifest.evaluateObservationState(classified)
    )
}

private fun RuntimeProcessUnitDefinition.commandExpectationMatches(root: RuntimeRootSnapshot): Boolean {
    match.exactCommand
        ?.takeIf { it.isNotBlank() }
        ?.let { expected ->
            return root.commandLine == expected || root.title == expected
        }
    if (match.commandContains.isNotEmpty()) {
        val text = root.commandSearchText()
        return match.commandContains.any { needle -> needle.lowercase() in text }
    }
    return true
}

private fun RuntimeRootSnapshot.bestPid(): Int? {
    return observedPid ?: rootPid ?: expectedPid
}

private fun RuntimeRootSnapshot.matchesPid(pid: Int): Boolean {
    return observedPid == pid || rootPid == pid || expectedPid == pid
}

private fun RuntimeRootSnapshot.commandSearchText(): String {
    return "$title $commandLine".lowercase()
}

private fun appendFallback(existing: String?, next: String): String {
    val cleanExisting = existing?.takeIf { it.isNotBlank() && it != "none" }
    return if (cleanExisting == null) next else "$cleanExisting,$next"
}

private fun String.isSameOrDescendantOf(root: String): Boolean {
    val normalizedRoot = root.trimEnd('/')
    return this == normalizedRoot || startsWith("$normalizedRoot/")
}

private fun File.isSameOrDescendantOf(root: File): Boolean {
    val targetPath = absoluteFile.toPath().normalize()
    val rootPath = root.absoluteFile.toPath().normalize()
    return targetPath == rootPath || targetPath.startsWith(rootPath)
}
