package com.kite.app.foundation.workspace

import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.runtime.ProotViewStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * T014g 受管路径写入口审计。
 *
 * 测试证明：普通启动会经过的共享准备逻辑（ensure）和 ProotViewStore.forContainer 都不创建、删除、
 * chmod 或更新环境变化目录 .kf/bin 的任何内容。用哨兵文件证明目录结构和内容完全不变。
 *
 * 这些测试在重新引入 direct write 时必须变红（哨兵被改/删/增）。
 */
class ProotViewWriteIngressAuditTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * ensure 不创建 .kf/bin 目录、不写其中文件、不删除/不改已有内容。
     * 预置带哨兵的 .kf/bin，执行 ensure 后结构和内容完全不变。
     */
    @Test
    fun ensureLeavesEnvBinDirectoryAndContentsCompletelyUntouched() {
        val workspace = Files.createTempDirectory("kite-envbin-audit-").toFile()
        try {
            // 预置 .kf/bin 及哨兵文件（模拟用户/资源安装的命令）。
            val envBin = File(workspace, WorkspaceBuildSupport.HELPER_BIN_DIR_NAME).apply { mkdirs() }
            val sentinel1 = File(envBin, "user-node").apply { writeText("#!/usr/bin/env sh\nexec node\n") }
            val sentinel2 = File(envBin, "kite-open-url").apply { writeText("# user proxy\n") }
            val beforeNames = envBin.walkTopDown().map { it.name }.toSet()
            val beforeContents = envBin.walkTopDown().filter { it.isFile }
                .associate { it.name to it.readBytes().toList() }

            WorkspaceBuildSupport.ensure(workspace)

            // .kf/bin 目录仍存在，但内容完全不变：无新增、无删除、无修改。
            assertTrue(".kf/bin 应仍存在", envBin.isDirectory)
            val afterNames = envBin.walkTopDown().map { it.name }.toSet()
            val afterContents = envBin.walkTopDown().filter { it.isFile }
                .associate { it.name to it.readBytes().toList() }
            assertEquals("ensure 不得改变 .kf/bin 的文件名结构", beforeNames, afterNames)
            assertEquals("ensure 不得改变 .kf/bin 的文件内容", beforeContents, afterContents)
            // 哨兵文件仍在。
            assertTrue("user-node 仍在", sentinel1.exists())
            assertTrue("kite-open-url 仍在", sentinel2.exists())
            // 关键 helper 名不在 .kf/bin（它们在共享 .kf/system/bin）。
            listOf("kf-gradle", "fd", "ss", "proot", "supervisorctl",
                "kf-android-sh", "kf-host", "kf-env", "kf-runtime").forEach { name ->
                assertFalse("Android helper $name 不应在 .kf/bin", File(envBin, name).exists())
            }
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * ensure 不创建 .kf/software 目录。
     */
    @Test
    fun ensureDoesNotCreateEnvSoftwareDirectory() {
        val workspace = Files.createTempDirectory("kite-soft-audit-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)
            assertFalse(".kf/software 不应被 ensure 创建", File(workspace, ".kf/software").exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * ensure 写入的共享目录确属 Android 持有（.kf/system/bin、.kf/system/wrappers），
     * 不在环境变化层。
     */
    @Test
    fun ensureWritesOnlyToSharedSystemDirectories() {
        val workspace = Files.createTempDirectory("kite-shared-audit-").toFile()
        try {
            WorkspaceBuildSupport.ensure(workspace)
            val systemBin = File(workspace, WorkspaceBuildSupport.HELPER_SYSTEM_BIN_DIR_NAME)
            val systemWrappers = File(workspace, WorkspaceBuildSupport.HELPER_SYSTEM_WRAPPERS_DIR_NAME)
            assertTrue("共享 .kf/system/bin 应被创建", systemBin.isDirectory)
            assertTrue("共享 .kf/system/wrappers 应被创建", systemWrappers.isDirectory)
            // Android helper 在共享 system/bin。
            assertTrue("kf-gradle 应在 .kf/system/bin", File(systemBin, "kf-gradle").exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * 工具链 wrapper 落在共享 .kf/system/wrappers，不在 .kf/bin；
     * PATH 让 .kf/bin 优先，用户同名命令不被覆盖。
     */
    @Test
    fun toolchainWrappersLandInSharedWrappersNotEnvBin() {
        val workspace = Files.createTempDirectory("kite-wrapper-audit-").toFile()
        try {
            // 预置 toolchains 目录模拟已安装 node（含一个非 managed 名字的命令 tsc，触发 wrapper 生成）。
            val toolchainBin = File(workspace, "${WorkspaceBuildSupport.HELPER_TOOLCHAIN_DIR_NAME}/node-v20.0.0/bin")
                .apply { mkdirs() }
            File(toolchainBin, "tsc").apply { writeText("#!/usr/bin/env sh\nexit 0\n"); setExecutable(true) }
            // 预置 .kf/bin 用户自定义同名命令。
            val envBin = File(workspace, WorkspaceBuildSupport.HELPER_BIN_DIR_NAME).apply { mkdirs() }
            val userTsc = File(envBin, "tsc").apply { writeText("#!/usr/bin/env sh\necho user-tsc\n") }

            WorkspaceBuildSupport.ensure(workspace)

            // wrapper 落在共享 wrappers，不在 .kf/bin。
            val systemWrappers = File(workspace, WorkspaceBuildSupport.HELPER_SYSTEM_WRAPPERS_DIR_NAME)
            assertTrue("tsc wrapper 应在共享 wrappers", File(systemWrappers, "tsc").exists())
            assertFalse(".kf/bin 不应有生成的 wrapper",
                File(envBin, "tsc").readText().contains("KFShell generated"))
            assertTrue("用户 tsc 命令未被覆盖", userTsc.readText().contains("user-tsc"))
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * forContainer 是无副作用路径构造，不创建 .kf/software 或 .kf/bin。
     */
    @Test
    fun forContainerDoesNotCreateManagedDirectories() {
        val filesRoot = temporaryFolder.newFolder("forContainer-files")
        val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
        val rootfs = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }
        val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L,
        )
        // 调 forContainer 前 .kf/software、.kf/bin 不存在。
        assertFalse("前置：.kf/software 不存在", File(workspace, ".kf/software").exists())
        assertFalse("前置：.kf/bin 不存在", File(workspace, ".kf/bin").exists())

        ProotViewStore.forContainer(container)

        // forContainer 后仍不存在（无副作用）。
        assertFalse("forContainer 不应创建 .kf/software", File(workspace, ".kf/software").exists())
        assertFalse("forContainer 不应创建 .kf/bin", File(workspace, ".kf/bin").exists())
    }

    /**
     * forContainer 不删除/不改已有的受管目录内容。
     */
    @Test
    fun forContainerLeavesExistingManagedDirectoryContentsUntouched() {
        val filesRoot = temporaryFolder.newFolder("forContainer-existing-files")
        val runtimeRoot = File(filesRoot, "runtime").apply { mkdirs() }
        val rootfs = File(runtimeRoot, "containers/ubuntu-main/rootfs").apply { mkdirs() }
        val workspace = File(runtimeRoot, "shared/default").apply { mkdirs() }
        val envBin = File(workspace, ".kf/bin").apply { mkdirs() }
        val sentinel = File(envBin, "preserved-cmd").apply { writeText("keep-me") }
        val container = ContainerRecord(
            id = "ubuntu-main",
            displayName = "Ubuntu",
            imageName = "ubuntu-base",
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath,
            createdAt = 1L,
        )

        ProotViewStore.forContainer(container)

        assertTrue("已有哨兵文件不应被 forContainer 改动", sentinel.readText() == "keep-me")
    }

    /**
     * 模拟已封存 View、system component marker 失效/App 版本变化场景。
     * 即使迁移本应触发（marker 失效），已封存时 .kf/bin 名称/内容/权限完全不变。
     */
    @Test
    fun migrationDoesNotTouchEnvBinWhenSealed() {
        val workspace = Files.createTempDirectory("kite-sealed-migration-").toFile()
        try {
            val envBin = File(workspace, WorkspaceBuildSupport.HELPER_BIN_DIR_NAME).apply { mkdirs() }
            // Kite 遗留文件（内容标记确认）。
            val legacyKfGradle = File(envBin, "kf-gradle").apply { writeText("# legacy kf helper\n") }
            val legacyFd = File(envBin, "fd").apply {
                writeText("#!/usr/bin/env sh\nexec fdfind \"\$@\"\n"); setExecutable(true)
            }
            val legacyWrapper = File(envBin, "old-tool").apply {
                writeText("#!/usr/bin/env sh\n# KFShell generated tool wrapper\nexec old-tool\n")
            }
            // 用户哨兵（不应被删）。
            val userCmd = File(envBin, "my-tool").apply { writeText("# user command\n") }
            val userFd = File(envBin, "ss").apply { writeText("#!/usr/bin/env sh\nexec my-ss\n") }
            val beforeNames = envBin.listFiles()!!.map { it.name }.toSet()
            val beforeContents = envBin.listFiles()!!.associate { it.name to it.readBytes().toList() }

            // sealed=true：已封存，迁移门禁止清理。
            WorkspaceBuildSupport.migrateLegacyEnvBinIfNeeded(workspace, sealed = true)

            // 所有文件（含 Kite 遗留）完全不变。
            val afterNames = envBin.listFiles()!!.map { it.name }.toSet()
            val afterContents = envBin.listFiles()!!.associate { it.name to it.readBytes().toList() }
            assertEquals("已封存时 .kf/bin 文件名不变", beforeNames, afterNames)
            assertEquals("已封存时 .kf/bin 内容不变", beforeContents, afterContents)
            assertTrue("Kite 遗留 kf-gradle 仍在", legacyKfGradle.exists())
            assertTrue("用户命令仍在", userCmd.exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * 未封存时迁移只删除内容标记确认的 Kite 遗留，不删除用户文件/同名命令。
     */
    @Test
    fun migrationRemovesOnlyConfirmedKiteLegacyWhenUnsealed() {
        val workspace = Files.createTempDirectory("kite-unsealed-migration-").toFile()
        try {
            val envBin = File(workspace, WorkspaceBuildSupport.HELPER_BIN_DIR_NAME).apply { mkdirs() }
            // Kite 遗留（应删）。
            File(envBin, "kf-gradle").writeText("# legacy\n")
            File(envBin, "kf-host").writeText("# legacy\n")
            File(envBin, "fd").writeText("#!/usr/bin/env sh\nexec fdfind \"\$@\"\n")
            File(envBin, "proot").writeText("#!/usr/bin/env sh\nKFSHELL_PROOT_SHIM_BEGIN\n")
            File(envBin, "old-tool").writeText("# KFShell generated tool wrapper\nexec x\n")
            // 用户文件（不应删）。
            val userCmd = File(envBin, "my-tool").writeText("# user\n")
            val userSs = File(envBin, "ss").writeText("#!/usr/bin/env sh\nexec my-ss\n")  // 非 KF 标记
            val userFd = File(envBin, "user-fd").writeText("# user fd\n")

            WorkspaceBuildSupport.migrateLegacyEnvBinIfNeeded(workspace, sealed = false)

            // Kite 遗留被删。
            assertFalse("kf-gradle 应被删", File(envBin, "kf-gradle").exists())
            assertFalse("kf-host 应被删", File(envBin, "kf-host").exists())
            assertFalse("KF fd 应被删", File(envBin, "fd").exists())
            assertFalse("KF proot 应被删", File(envBin, "proot").exists())
            assertFalse("KF tool wrapper 应被删", File(envBin, "old-tool").exists())
            // 用户文件保留。
            assertTrue("用户 my-tool 保留", File(envBin, "my-tool").exists())
            assertTrue("用户 ss（非 KF 标记）保留", File(envBin, "ss").exists())
            assertTrue("用户 user-fd 保留", File(envBin, "user-fd").exists())
        } finally {
            workspace.deleteRecursively()
        }
    }
}
