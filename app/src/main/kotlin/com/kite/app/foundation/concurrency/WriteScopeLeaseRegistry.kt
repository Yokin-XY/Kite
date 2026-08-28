package com.kite.app.foundation.concurrency

internal data class WriteScopeConflict(
    val scope: String,
    val ownerId: String,
)

/** Exact semantic write scopes shared by schedulers and long-running resource mutations. */
internal class WriteScopeLeaseRegistry {
    private val scopesByOwner = mutableMapOf<String, Set<String>>()
    private val ownerByScope = mutableMapOf<String, String>()

    @Synchronized
    fun tryAcquire(ownerId: String, writeScopes: Set<String>): WriteScopeConflict? {
        require(ownerId.isNotBlank()) { "write_scope_owner_missing" }
        require(ownerId !in scopesByOwner) { "write_scope_owner_already_active:$ownerId" }
        val normalized = normalize(writeScopes)
        normalized.firstNotNullOfOrNull { scope ->
            ownerByScope[scope]?.let { currentOwner -> WriteScopeConflict(scope, currentOwner) }
        }?.let { return it }
        scopesByOwner[ownerId] = normalized
        normalized.forEach { scope -> ownerByScope[scope] = ownerId }
        return null
    }

    @Synchronized
    fun release(ownerId: String) {
        scopesByOwner.remove(ownerId).orEmpty().forEach { scope ->
            if (ownerByScope[scope] == ownerId) ownerByScope.remove(scope)
        }
    }

    private fun normalize(writeScopes: Set<String>): Set<String> = writeScopes
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(String::lowercase)
        .toCollection(linkedSetOf())
}
