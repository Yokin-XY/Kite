package com.kite.app.application.runtimemanagement

import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceRuntimeTopologyBuilderTest {
    @Test
    fun `assigns exact structured owners and inherits only through real parent pid`() {
        val run = run("root", owner = "card:root@100/step/0-shell/attempt/1", rootPid = "41")
        val rootProcess = process("root-process", 41, ownerId = run.runtimeOwnerId)
        val childProcess = process("child-process", 52, parentPid = 41)

        val topology = InstanceRuntimeTopologyBuilder.build(
            runs = listOf(run),
            terminals = emptyList(),
            processes = listOf(rootProcess, childProcess)
        )

        assertEquals(listOf("root-process", "child-process"), topology.node("root")?.processIds)
        assertTrue(topology.unassignedProcessIds.isEmpty())
        assertTrue(topology.ambiguousProcessIds.isEmpty())
    }

    @Test
    fun `keeps recursive child and grandchild relationships in one root subtree`() {
        val root = run("root", owner = "card:root@100")
        val child = run("child", owner = "card:child@101", parentInstanceId = root.instanceId)
        val grandchild = run("grandchild", owner = "card:grandchild@102", parentInstanceId = child.instanceId)

        val topology = InstanceRuntimeTopologyBuilder.build(
            runs = listOf(root, child, grandchild),
            terminals = emptyList(),
            processes = emptyList()
        )

        assertEquals(listOf("root"), topology.rootInstanceIds)
        assertEquals(listOf("root", "child", "grandchild"), topology.subtree("root").map { it.identity.instanceId })
        assertEquals(listOf("child", "grandchild"), topology.descendants("root").map { it.identity.instanceId })
    }

    @Test
    fun `rejects ambiguous owner instead of choosing a run by score`() {
        val sharedOwner = "resource:shared@100/operation/install/attempt/1"
        val first = run("first", owner = sharedOwner)
        val second = run("second", owner = sharedOwner)
        val process = process("ambiguous", 61, ownerId = sharedOwner)

        val topology = InstanceRuntimeTopologyBuilder.build(
            runs = listOf(first, second),
            terminals = emptyList(),
            processes = listOf(process)
        )

        assertEquals(listOf("ambiguous"), topology.ambiguousProcessIds)
        assertTrue(topology.nodesByInstanceId.values.all { it.processIds.isEmpty() })
    }

    @Test
    fun `does not reconstruct legacy owner guesses from instance id`() {
        val run = run("root", owner = "card:root@100/step/0-shell/attempt/1")
        val legacyGuess = process("legacy", 71, ownerId = "card:root")

        val topology = InstanceRuntimeTopologyBuilder.build(
            runs = listOf(run),
            terminals = emptyList(),
            processes = listOf(legacyGuess)
        )

        assertEquals(listOf("legacy"), topology.unassignedProcessIds)
        assertTrue(topology.node("root")?.processIds.orEmpty().isEmpty())
    }

    private fun run(
        instanceId: String,
        owner: String,
        parentInstanceId: String? = null,
        rootPid: String? = null
    ): CardRunState = CardRunState(
        instanceId = instanceId,
        recipeId = "recipe-$instanceId",
        parentInstanceId = parentInstanceId,
        status = CardRunStatus.Running,
        runtimeRootOwnerId = owner.substringBefore("/step/"),
        runtimeOwnerId = owner,
        runtimeUnitId = owner,
        ownedRuntimeOwnerIds = listOf(owner),
        rootPid = rootPid,
        createdAt = instanceId.hashCode().toLong().let { if (it == 0L) 1L else it },
        updatedAt = 1L
    )

    private fun process(
        id: String,
        pid: Int,
        parentPid: Int = 0,
        ownerId: String? = null
    ): RuntimeManagedProcess = RuntimeManagedProcess(
        id = id,
        pid = pid,
        parentPid = parentPid,
        title = id,
        stateLabel = "运行中",
        ownerKind = RuntimeManagedOwnerKind.Card,
        ownerId = ownerId
    )
}
