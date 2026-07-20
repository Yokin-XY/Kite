package com.kite.app.application.runtimebootstrap

import kotlinx.coroutines.flow.StateFlow

interface RuntimeBootstrapGateway {
    val snapshots: StateFlow<RuntimeBootstrapSnapshot>

    fun currentSnapshot(): RuntimeBootstrapSnapshot

    fun refresh()

    fun ensureReady()
}

interface RuntimeBootstrapDependenciesOwner {
    val runtimeBootstrapGateway: RuntimeBootstrapGateway
}
