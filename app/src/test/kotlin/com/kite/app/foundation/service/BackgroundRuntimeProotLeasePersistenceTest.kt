package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.LongLivedProotLeasePhase
import java.nio.file.Files
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundRuntimeProotLeasePersistenceTest {
    @Test
    fun `lease checkpoint round trips while legacy json remains absent`() {
        val persisted = record().copy(
            lastLaunchLane = "proot_shell",
            longLivedProotLeaseGeneration = 3L,
            longLivedProotLeasePhase = LongLivedProotLeasePhase.STARTING.name,
            longLivedProotLeaseUpdatedAt = 20L,
        )

        val restored = BackgroundRuntimeRecord.fromJson(persisted.toJson())
        val state = BackgroundRuntimeProotLeaseCheckpointPolicy.inspect(restored)
        assertTrue(state is BackgroundRuntimeProotLeaseCheckpointState.Ready)
        val checkpoint = (state as BackgroundRuntimeProotLeaseCheckpointState.Ready).checkpoint
        assertEquals(3L, checkpoint.generation)
        assertEquals(LongLivedProotLeasePhase.STARTING, checkpoint.phase)
        assertEquals(20L, checkpoint.updatedAtMs)

        val legacy = persisted.toJson().apply {
            remove("longLivedProotLeaseGeneration")
            remove("longLivedProotLeasePhase")
            remove("longLivedProotLeaseUpdatedAt")
        }
        val restoredLegacy = BackgroundRuntimeRecord.fromJson(legacy)
        assertEquals(
            BackgroundRuntimeProotLeaseCheckpointState.Absent,
            BackgroundRuntimeProotLeaseCheckpointPolicy.inspect(restoredLegacy),
        )
    }

    @Test
    fun `partial unknown and route conflicting checkpoints fail closed`() {
        val partial = record().copy(longLivedProotLeaseGeneration = 1L)
        val unknown = record().copy(
            lastLaunchLane = "proot_shell",
            longLivedProotLeaseGeneration = 1L,
            longLivedProotLeasePhase = "UNKNOWN_FUTURE_PHASE",
            longLivedProotLeaseUpdatedAt = 10L,
        )
        val routeConflict = record().copy(
            lastLaunchLane = "host_node",
            longLivedProotLeaseGeneration = 1L,
            longLivedProotLeasePhase = LongLivedProotLeasePhase.RUNNING.name,
            longLivedProotLeaseUpdatedAt = 10L,
        )

        listOf(partial, unknown, routeConflict).map { malformed ->
            BackgroundRuntimeRecord.fromJson(malformed.toJson())
        }.forEach { malformed ->
            assertTrue(
                BackgroundRuntimeProotLeaseCheckpointPolicy.inspect(malformed) is
                    BackgroundRuntimeProotLeaseCheckpointState.Malformed
            )
            assertFalse(
                BackgroundRuntimeProotLeaseCheckpointPolicy.beginStarting(
                    malformed,
                    generation = 2L,
                    launchReason = "test",
                    updatedAtMs = 20L,
                ).accepted
            )
        }
    }

    @Test
    fun `registry persists route and starting lease in one record write`() {
        val root = Files.createTempDirectory("kite-rf920-").toFile()
        try {
            val target = root.resolve("background-runtimes.json")
            target.writeText(JSONArray().put(record().toJson()).toString())

            val mutation = BackgroundRuntimeRegistry.beginLongLivedProotLease(
                runtimeRoot = root,
                runtimeId = RUNTIME_ID,
                generation = 1L,
                launchReason = "unified_capacity_admitted",
                updatedAtMs = 10L,
            )

            assertTrue(mutation.accepted)
            assertTrue(mutation.changed)
            val restored = BackgroundRuntimeRecord.fromJson(
                JSONArray(target.readText()).getJSONObject(0)
            )
            assertEquals("proot_shell", restored.lastLaunchLane)
            assertEquals("unified_capacity_admitted", restored.lastLaunchReason)
            assertEquals(1L, restored.longLivedProotLeaseGeneration)
            assertEquals(LongLivedProotLeasePhase.STARTING.name, restored.longLivedProotLeasePhase)
            assertEquals(10L, restored.longLivedProotLeaseUpdatedAt)
            assertNull(restored.pid)
            assertNull(restored.processBootId)
            assertNull(restored.processStartTicks)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `stale transition and reused generation are rejected while next generation follows release`() {
        val starting = accepted(
            BackgroundRuntimeProotLeaseCheckpointPolicy.beginStarting(
                record(),
                generation = 4L,
                launchReason = "admitted",
                updatedAtMs = 10L,
            )
        )
        val running = accepted(
            BackgroundRuntimeProotLeaseCheckpointPolicy.transition(
                starting,
                expectedGeneration = 4L,
                expectedPhase = LongLivedProotLeasePhase.STARTING,
                nextPhase = LongLivedProotLeasePhase.RUNNING,
                updatedAtMs = 11L,
            )
        )

        assertFalse(
            BackgroundRuntimeProotLeaseCheckpointPolicy.transition(
                running,
                expectedGeneration = 3L,
                expectedPhase = LongLivedProotLeasePhase.RUNNING,
                nextPhase = LongLivedProotLeasePhase.STOPPING,
                updatedAtMs = 12L,
            ).accepted
        )
        val stopping = accepted(
            BackgroundRuntimeProotLeaseCheckpointPolicy.transition(
                running,
                expectedGeneration = 4L,
                expectedPhase = LongLivedProotLeasePhase.RUNNING,
                nextPhase = LongLivedProotLeasePhase.STOPPING,
                updatedAtMs = 12L,
            )
        )
        val released = accepted(
            BackgroundRuntimeProotLeaseCheckpointPolicy.transition(
                stopping,
                expectedGeneration = 4L,
                expectedPhase = LongLivedProotLeasePhase.STOPPING,
                nextPhase = LongLivedProotLeasePhase.RELEASED,
                updatedAtMs = 13L,
            )
        )
        assertFalse(
            BackgroundRuntimeProotLeaseCheckpointPolicy.beginStarting(
                released,
                generation = 4L,
                launchReason = "reused",
                updatedAtMs = 14L,
            ).accepted
        )
        val next = BackgroundRuntimeProotLeaseCheckpointPolicy.beginStarting(
            released,
            generation = 5L,
            launchReason = "next",
            updatedAtMs = 14L,
        )
        assertTrue(next.accepted)
        assertEquals(5L, next.record?.longLivedProotLeaseGeneration)
    }

    @Test
    fun `definition refresh sources preserve the same lease fields`() {
        val source = java.io.File(
            "src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeRegistry.kt"
        ).readText()

        assertEquals(2, "longLivedProotLeaseGeneration =".toRegex().findAll(source).count())
        assertEquals(2, "longLivedProotLeasePhase =".toRegex().findAll(source).count())
        assertEquals(2, "longLivedProotLeaseUpdatedAt =".toRegex().findAll(source).count())
        assertFalse(source.contains("LongLivedProotAdmissionSimulator"))
    }

    private fun accepted(mutation: BackgroundRuntimeProotLeaseMutation): BackgroundRuntimeRecord {
        check(mutation.accepted) { mutation.rejectionReason ?: "rf920_transition_rejected" }
        return requireNotNull(mutation.record)
    }

    private fun record() = BackgroundRuntimeRecord(
        id = RUNTIME_ID,
        spaceId = "space-rf920",
        kind = BackgroundRuntimeKind.CUSTOM,
        mode = BackgroundRuntimeMode.PROCESS,
        title = "rf920",
        workingDirectory = "/workspace",
        startCommand = "exec /bin/sleep 30",
        logPath = "/tmp/rf920.log",
        createdAt = 1L,
    )

    companion object {
        private const val RUNTIME_ID = "background-rf920"
    }
}
