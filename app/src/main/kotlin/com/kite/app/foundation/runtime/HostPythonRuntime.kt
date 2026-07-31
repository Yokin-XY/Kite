package com.kite.app.foundation.runtime

import android.content.Context
import com.kite.app.foundation.contracts.ContainerLaunchConfig
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.NetworkMode
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

internal data class HostPythonProviderContext(
    val androidContext: Context,
    val container: ContainerRecord,
    val workspaceDirectory: File,
)

internal data class HostPythonRuntimeLayout(
    val rootfsDirectory: File,
    val workspaceDirectory: File,
    val workspaceControlDirectory: File,
    val pythonBinary: File,
    val pythonRoot: File,
    val pythonLibraryDirectory: File,
    val glibcLibraryDirectories: List<File>,
    val assets: GlibcHostRuntimeAssets,
) {
    val libraryPath: String
        get() = buildList {
            add(assets.patchedLibc.parentFile)
            add(pythonLibraryDirectory)
            addAll(glibcLibraryDirectories)
        }.distinctBy(File::getAbsolutePath).joinToString(":") { it.absolutePath }

    fun mapContainerPath(path: String?): File? {
        val normalized = path?.trim().orEmpty().ifBlank { RuntimeBoundary.CONTAINER_WORKSPACE_PATH }
        val (root, candidate) = when {
            normalized == "${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf" ->
                workspaceControlDirectory to workspaceControlDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/") ->
                workspaceControlDirectory to File(
                    workspaceControlDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/"),
                )
            normalized == RuntimeBoundary.CONTAINER_WORKSPACE_PATH -> workspaceDirectory to workspaceDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/") ->
                workspaceDirectory to File(
                    workspaceDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/"),
                )
            normalized == "/" -> rootfsDirectory to rootfsDirectory
            normalized.startsWith("/") -> rootfsDirectory to File(rootfsDirectory, normalized.removePrefix("/"))
            else -> null
        } ?: return null
        val normalizedRoot = root.absoluteFile.normalize().toPath()
        val normalizedCandidate = candidate.absoluteFile.normalize()
        return normalizedCandidate.takeIf { it.toPath().startsWith(normalizedRoot) }
    }
}

internal data class HostPythonInvocation(
    val arguments: List<String>,
)

internal sealed interface HostPythonCommandResolution {
    data class Ready(
        val layout: HostPythonRuntimeLayout,
        val invocation: HostPythonInvocation,
    ) : HostPythonCommandResolution
    data class Unsupported(val reason: String) : HostPythonCommandResolution
    data class Blocked(val reason: String) : HostPythonCommandResolution
}

internal object HostPythonCommandResolver {
    private val safePythonCommand = Regex("python(?:3(?:\\.[0-9]+)?)?")

    fun isCandidate(request: RuntimeExecutionRequest): Boolean {
        val payload = request.payload as? RuntimeExecutionPayload.Argv ?: return false
        val executable = payload.executable.trim()
        val commandName = candidateName(executable)?.takeIf(safePythonCommand::matches) ?: return false
        return !executable.contains('/') || executable == "/workspace/.kf/bin/$commandName"
    }

    fun resolve(
        executable: String,
        arguments: List<String>,
        rootfsDirectory: File,
        workspaceDirectory: File,
        workspaceControlDirectory: File = File(workspaceDirectory, ".kf"),
        assets: GlibcHostRuntimeAssets,
        linkTargetReader: (File) -> String? = ::readLinkTarget,
    ): HostPythonCommandResolution {
        val commandName = candidateName(executable)
            ?.takeIf(safePythonCommand::matches)
            ?: return unsupported("managed_command_not_python")
        val initial = when {
            executable == commandName -> File(workspaceControlDirectory, "bin/$commandName")
            executable.startsWith('/') -> mapContainerPath(
                executable,
                rootfsDirectory,
                workspaceDirectory,
                workspaceControlDirectory,
            )
            else -> null
        } ?: return unsupported("python_command_missing")
        val pythonBinary = followLinks(
            initial = initial,
            rootfsDirectory = rootfsDirectory,
            workspaceDirectory = workspaceDirectory,
            workspaceControlDirectory = workspaceControlDirectory,
            linkTargetReader = linkTargetReader,
        ) ?: return blocked("python_command_link_invalid")
        if (!pythonBinary.isFile || !pythonBinary.canExecute()) return unsupported("python_binary_missing")
        if (!isArm64Elf(pythonBinary)) return blocked("python_binary_abi_mismatch")
        val managedSoftwareDirectory = File(workspaceControlDirectory, "software").absoluteFile.normalize()
        val pythonRoot = pythonBinary.parentFile?.parentFile
            ?.takeIf {
                it.isDirectory &&
                    it.parentFile?.parentFile?.absoluteFile?.normalize() == managedSoftwareDirectory
            }
            ?: return blocked("python_root_invalid")
        val pythonLibraryDirectory = File(pythonRoot, "lib")
        if (!pythonLibraryDirectory.isDirectory) return unsupported("python_libraries_missing")
        val stdlibDirectory = pythonLibraryDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("python3\\.[0-9]+")) }
            .sortedByDescending(File::getName)
            .firstOrNull { File(it, "os.py").isFile }
            ?: return unsupported("python_stdlib_missing")
        val version = stdlibDirectory.name.removePrefix("python")
        if (!File(pythonLibraryDirectory, "libpython$version.so.1.0").isFile) {
            return unsupported("python_shared_library_missing")
        }
        val glibcDirectories = listOf(
            File(rootfsDirectory, "usr/lib/aarch64-linux-gnu"),
            File(rootfsDirectory, "lib/aarch64-linux-gnu"),
        ).filter(File::isDirectory)
        if (glibcDirectories.isEmpty()) return unsupported("glibc_libraries_missing")
        val mappedArguments = mapInterpreterArguments(
            arguments,
            rootfsDirectory,
            workspaceDirectory,
            workspaceControlDirectory,
        ) ?: return blocked("python_entry_path_invalid")
        return HostPythonCommandResolution.Ready(
            layout = HostPythonRuntimeLayout(
                rootfsDirectory = rootfsDirectory.absoluteFile.normalize(),
                workspaceDirectory = workspaceDirectory.absoluteFile.normalize(),
                workspaceControlDirectory = workspaceControlDirectory.absoluteFile.normalize(),
                pythonBinary = pythonBinary.absoluteFile.normalize(),
                pythonRoot = pythonRoot.absoluteFile.normalize(),
                pythonLibraryDirectory = pythonLibraryDirectory.absoluteFile.normalize(),
                glibcLibraryDirectories = glibcDirectories.map { it.absoluteFile.normalize() },
                assets = assets,
            ),
            invocation = HostPythonInvocation(mappedArguments),
        )
    }

    private fun candidateName(executable: String): String? = executable.trim()
        .takeIf(String::isNotBlank)
        ?.substringAfterLast('/')

    private fun mapInterpreterArguments(
        arguments: List<String>,
        rootfsDirectory: File,
        workspaceDirectory: File,
        workspaceControlDirectory: File,
    ): List<String>? {
        if (arguments.isEmpty()) return arguments
        if (arguments.first() == "-c") return arguments
        val literalPrefix = if (arguments.first() == "-m") 2 else 0
        return arguments.mapIndexed { index, argument ->
            if (index < literalPrefix || !argument.startsWith('/')) {
                argument
            } else {
                mapContainerPath(
                    argument,
                    rootfsDirectory,
                    workspaceDirectory,
                    workspaceControlDirectory,
                )?.absolutePath ?: return null
            }
        }
    }

    private fun followLinks(
        initial: File,
        rootfsDirectory: File,
        workspaceDirectory: File,
        workspaceControlDirectory: File,
        linkTargetReader: (File) -> String?,
    ): File? {
        var current = initial.absoluteFile.normalize()
        repeat(12) {
            if (!withinRuntimeRoots(current, rootfsDirectory, workspaceDirectory)) return null
            val target = linkTargetReader(current)
            if (target == null) return current.takeIf(File::isFile)
            current = if (target.startsWith('/')) {
                mapContainerPath(target, rootfsDirectory, workspaceDirectory, workspaceControlDirectory)
                    ?: return null
            } else {
                File(current.parentFile, target).absoluteFile.normalize()
            }
        }
        return null
    }

    private fun readLinkTarget(file: File): String? = runCatching {
        file.toPath().takeIf(Files::isSymbolicLink)?.let(Files::readSymbolicLink)?.toString()
    }.getOrNull()

    private fun withinRuntimeRoots(file: File, rootfsDirectory: File, workspaceDirectory: File): Boolean {
        val path = file.toPath().normalize()
        return path.startsWith(rootfsDirectory.toPath().normalize()) ||
            path.startsWith(workspaceDirectory.toPath().normalize())
    }

    private fun mapContainerPath(
        path: String,
        rootfsDirectory: File,
        workspaceDirectory: File,
        workspaceControlDirectory: File,
    ): File? {
        val normalized = path.trim()
        val (root, candidate) = when {
            normalized == "${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf" ->
                workspaceControlDirectory to workspaceControlDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/") ->
                workspaceControlDirectory to File(
                    workspaceControlDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/.kf/"),
                )
            normalized == RuntimeBoundary.CONTAINER_WORKSPACE_PATH -> workspaceDirectory to workspaceDirectory
            normalized.startsWith("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/") ->
                workspaceDirectory to File(
                    workspaceDirectory,
                    normalized.removePrefix("${RuntimeBoundary.CONTAINER_WORKSPACE_PATH}/"),
                )
            normalized == "/" -> rootfsDirectory to rootfsDirectory
            normalized.startsWith('/') -> rootfsDirectory to File(rootfsDirectory, normalized.removePrefix("/"))
            else -> null
        } ?: return null
        val normalizedRoot = root.absoluteFile.normalize().toPath()
        val normalizedCandidate = candidate.absoluteFile.normalize()
        return normalizedCandidate.takeIf { it.toPath().startsWith(normalizedRoot) }
    }

    private fun isArm64Elf(file: File): Boolean = runCatching {
        val header = file.inputStream().use { input ->
            ByteArray(20).also { bytes -> if (input.read(bytes) != bytes.size) return@runCatching false }
        }
        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()
    }.getOrDefault(false)

    private fun unsupported(reason: String) = HostPythonCommandResolution.Unsupported(reason)
    private fun blocked(reason: String) = HostPythonCommandResolution.Blocked(reason)

}

internal object HostPythonRuntimeProvider :
    RuntimeExecutionProvider<HostPythonProviderContext, ContainerLaunchConfig> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.MANAGED_RUNTIME

    override fun prepare(
        context: HostPythonProviderContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<ContainerLaunchConfig> {
        val payload = request.payload as? RuntimeExecutionPayload.Argv
            ?: return unsupported("python_structured_argv_required")
        if (!HostPythonCommandResolver.isCandidate(request)) return unsupported("managed_command_not_python")
        val rejectedRequirement = listOf(
            RuntimeExecutionRequirement.FULL_LINUX,
            RuntimeExecutionRequirement.ANDROID_NATIVE,
            RuntimeExecutionRequirement.INTERACTIVE_PTY,
            RuntimeExecutionRequirement.FILESYSTEM_VIEW,
            RuntimeExecutionRequirement.CHILD_PROCESS,
            RuntimeExecutionRequirement.UNVERIFIED_NATIVE_EXTENSION,
        ).firstOrNull(request.requirements::contains)
        if (rejectedRequirement != null) {
            return unsupported("python_${rejectedRequirement.name.lowercase()}_required")
        }
        if (payload.arguments.take(2) == listOf("-m", "pip")) {
            return unsupported("python_package_lifecycle_requires_proot")
        }
        if (payload.arguments.take(2) == listOf("-m", "venv")) {
            return unsupported("python_venv_requires_proot")
        }
        if (!request.environment["VIRTUAL_ENV"].isNullOrBlank()) {
            return unsupported("python_virtual_environment_requires_proot")
        }
        if (RuntimeExecutionGuarantee.NO_CHILD_PROCESS !in request.guarantees) {
            return unsupported("python_no_child_process_guarantee_missing")
        }
        if (RuntimeExecutionGuarantee.VERIFIED_NATIVE_IMPORTS !in request.guarantees) {
            return unsupported("python_verified_native_imports_guarantee_missing")
        }
        if (context.container.networkMode != NetworkMode.HOST) {
            return unsupported("network_mode_requires_proot")
        }
        val workspaceControlDirectory = File(context.workspaceDirectory, ".kf")
        val assets = when (val prepared = GlibcHostRuntimePreparer.prepare(
            context = context.androidContext,
            container = context.container,
            workspaceDirectory = context.workspaceDirectory,
            workspaceControlDirectory = workspaceControlDirectory,
        )) {
            is GlibcHostRuntimePreparation.Ready -> prepared.assets
            is GlibcHostRuntimePreparation.Unsupported -> return unsupported(prepared.reason)
        }
        val resolution = HostPythonCommandResolver.resolve(
            executable = payload.executable,
            arguments = payload.arguments,
            rootfsDirectory = File(context.container.rootfsPath),
            workspaceDirectory = context.workspaceDirectory,
            workspaceControlDirectory = workspaceControlDirectory,
            assets = assets,
        )
        val ready = when (resolution) {
            is HostPythonCommandResolution.Ready -> resolution
            is HostPythonCommandResolution.Unsupported -> return unsupported(resolution.reason)
            is HostPythonCommandResolution.Blocked -> return blocked(resolution.reason)
        }
        val workingDirectory = ready.layout.mapContainerPath(request.workingDirectory)
            ?.takeIf(File::isDirectory)
            ?: return unsupported("working_directory_invalid")
        val mappedEnvironment = mapEnvironment(request.environment, ready.layout)
            ?: return unsupported("python_environment_path_invalid")
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = buildConfig(
                context.container,
                ready.layout,
                ready.invocation,
                workingDirectory,
                mappedEnvironment,
            ),
            reason = "host_python_ready",
        )
    }

    internal fun buildConfig(
        container: ContainerRecord,
        layout: HostPythonRuntimeLayout,
        invocation: HostPythonInvocation,
        workingDirectory: File,
        additionalEnvironment: Map<String, String> = emptyMap(),
    ): ContainerLaunchConfig {
        val runtimeRoot = layout.assets.launcher.parentFile
        val tmpDirectory = File(runtimeRoot, "tmp").also(File::mkdirs)
        val certificateFile = File(layout.rootfsDirectory, "etc/ssl/certs/ca-certificates.crt")
        val environment = linkedMapOf(
            "HOME" to File(layout.rootfsDirectory, "root").absolutePath,
            "USER" to "root",
            "LOGNAME" to "root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "TZ" to TimeZone.getDefault().id,
            "TMPDIR" to tmpDirectory.absolutePath,
            "TMP" to tmpDirectory.absolutePath,
            "TEMP" to tmpDirectory.absolutePath,
            "PWD" to workingDirectory.absolutePath,
            "PATH" to containerPath(),
            "PYTHONUNBUFFERED" to "1",
            "GLIBC_TUNABLES" to "glibc.pthread.rseq=0",
        )
        additionalEnvironment.forEach { (key, value) ->
            if (ENVIRONMENT_NAME.matches(key)) environment[key] = value
        }
        environment.putAll(linkedMapOf(
            "PYTHONHOME" to layout.pythonRoot.absolutePath,
            "KITE_GLIBC_HOST_LANE" to "direct_glibc_v1",
            "KITE_GLIBC_HOST_LOADER" to layout.assets.patchedLoader.absolutePath,
            "KITE_GLIBC_HOST_LIBRARY_PATH" to layout.libraryPath,
            "KITE_GLIBC_HOST_COMPAT_LIBRARY" to layout.assets.compatLibrary.absolutePath,
            "KITE_GLIBC_HOST_TARGET" to layout.pythonBinary.absolutePath,
            "KITE_GLIBC_HOST_RESOLV_CONF" to layout.assets.resolvConf.absolutePath,
        ))
        if (certificateFile.isFile) environment["SSL_CERT_FILE"] = certificateFile.absolutePath
        return ContainerLaunchConfig(
            container = container,
            executablePath = layout.assets.launcher.absolutePath,
            workingDirectory = workingDirectory.absolutePath,
            args = (listOf(layout.assets.launcher.absolutePath) + invocation.arguments).toTypedArray(),
            env = environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
        )
    }

    internal fun mapEnvironment(
        environment: Map<String, String>,
        layout: HostPythonRuntimeLayout,
    ): Map<String, String>? {
        val mapped = linkedMapOf<String, String>()
        environment.forEach { (key, value) ->
            mapped[key] = when (key) {
                "PYTHONPATH" -> {
                    val entries = mutableListOf<String>()
                    for (entry in value.split(':')) {
                        entries += if (entry.isBlank() || !entry.startsWith('/')) {
                            entry
                        } else {
                            layout.mapContainerPath(entry)?.absolutePath ?: return null
                        }
                    }
                    entries.joinToString(":")
                }
                "PYTHONSTARTUP" -> if (!value.startsWith('/')) {
                    value
                } else {
                    layout.mapContainerPath(value)?.absolutePath ?: return null
                }
                else -> value
            }
        }
        return mapped
    }

    private fun unsupported(reason: String) = RuntimeProviderDecision.Unsupported(kind, reason)
    private fun blocked(reason: String) = RuntimeProviderDecision.Blocked(kind, reason)

    private fun containerPath(): String = listOf(
        "/workspace/.kf/bin",
        "/workspace/.kf/system/bin",
        "/root/.local/bin",
        "/usr/local/sbin",
        "/usr/local/bin",
        "/usr/sbin",
        "/usr/bin",
        "/sbin",
        "/bin",
    ).joinToString(":")

    private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
