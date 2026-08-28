package com.kite.app.foundation.storage

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Publishes a complete directory for many concurrent consumers.
 *
 * A process lock prevents overlapping JVM file locks, while the file lock extends the same
 * destination contract to another app process. Writers stage into attempt-owned directories;
 * readers only receive the destination after the caller's completeness check passes.
 */
internal object AtomicDirectoryPublisher {
    private val processLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun publish(
        destination: File,
        isComplete: (File) -> Boolean,
        stage: (File) -> Unit,
    ): File {
        val target = destination.absoluteFile.normalize()
        val parent = requireNotNull(target.parentFile) { "Artifact destination has no parent" }
        check(parent.mkdirs() || parent.isDirectory) {
            "Unable to create artifact parent: ${parent.absolutePath}"
        }
        val key = target.canonicalPath
        return processLocks.computeIfAbsent(key) { ReentrantLock(true) }.withLock {
            val lockFile = File(parent, ".${target.name}.kite-publish.lock")
            FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use {
                    publishLocked(target, isComplete, stage)
                }
            }
        }
    }

    private fun publishLocked(
        destination: File,
        isComplete: (File) -> Boolean,
        stage: (File) -> Unit,
    ): File {
        val parent = checkNotNull(destination.parentFile)
        val pendingPrefix = ".${destination.name}.kite-pending-"
        val previous = File(parent, ".${destination.name}.kite-previous")

        recoverInterruptedPublication(destination, previous, isComplete)
        parent.listFiles { file -> file.name.startsWith(pendingPrefix) }
            .orEmpty()
            .forEach(::deleteOwnedPath)
        if (isComplete(destination)) return destination

        val pending = File(parent, "$pendingPrefix${UUID.randomUUID()}")
        var displacedPrevious = false
        try {
            stage(pending)
            check(isComplete(pending)) {
                "Staged artifact is incomplete: ${pending.absolutePath}"
            }
            if (pathExists(destination)) {
                check(!pathExists(previous)) {
                    "Artifact recovery path is still occupied: ${previous.absolutePath}"
                }
                move(destination, previous)
                displacedPrevious = true
            }
            move(pending, destination)
            check(isComplete(destination)) {
                "Published artifact is incomplete: ${destination.absolutePath}"
            }
            if (displacedPrevious) deleteOwnedPath(previous)
            return destination
        } catch (error: Throwable) {
            if (displacedPrevious && pathExists(previous)) {
                if (pathExists(destination)) deleteOwnedPath(destination)
                move(previous, destination)
            }
            throw error
        } finally {
            if (pathExists(pending)) deleteOwnedPath(pending)
        }
    }

    private fun recoverInterruptedPublication(
        destination: File,
        previous: File,
        isComplete: (File) -> Boolean,
    ) {
        if (!pathExists(previous)) return
        if (pathExists(destination) && isComplete(destination)) {
            deleteOwnedPath(previous)
            return
        }
        if (pathExists(destination)) deleteOwnedPath(destination)
        move(previous, destination)
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun deleteOwnedPath(path: File) {
        if (!pathExists(path)) return
        path.walkTopDown().forEach { ownedPath -> ownedPath.setWritable(true, true) }
        check(path.deleteRecursively()) { "Unable to remove artifact path: ${path.absolutePath}" }
    }

    private fun pathExists(path: File): Boolean =
        Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)
}
