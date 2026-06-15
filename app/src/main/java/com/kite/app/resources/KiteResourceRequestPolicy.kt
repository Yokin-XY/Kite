package com.kite.app.resources

object KiteResourceRequestPolicy {
    const val SURFACE_STORE_HOME = "store_home"
    const val SURFACE_STORE_SEARCH = "store_search"
    const val SURFACE_RESOURCE_DETAIL = "resource_detail"
    const val SURFACE_RESOURCE_MEDIA = "resource_media"
    const val SURFACE_INSTALL_PLAN = "install_plan"
    const val SURFACE_EXECUTION_MANIFEST = "execution_manifest"
    const val SURFACE_PROVIDER_LOOKUP = "provider_lookup"

    const val STORE_PAGE_CACHE_MS = 10L * 60L * 1000L
    const val DETAIL_PAGE_CACHE_MS = 30L * 60L * 1000L
    const val MEDIA_CACHE_MS = 24L * 60L * 60L * 1000L
    const val INSTALL_PLAN_CACHE_MS = 5L * 60L * 1000L
    const val EXECUTION_MANIFEST_CACHE_MS = 24L * 60L * 60L * 1000L

    fun storeListKey(query: String): String {
        val cleanQuery = query.trim()
        val surface = if (cleanQuery.isBlank()) SURFACE_STORE_HOME else SURFACE_STORE_SEARCH
        return "$surface:${cleanQuery.lowercase()}"
    }

    fun resourceDetailKey(resourceId: String): String =
        "$SURFACE_RESOURCE_DETAIL:${safeKey(resourceId)}"

    fun resourceMediaKey(resourceId: String): String =
        "$SURFACE_RESOURCE_MEDIA:${safeKey(resourceId)}"

    fun installPlanKey(resourceId: String): String =
        "$SURFACE_INSTALL_PLAN:${safeKey(resourceId)}"

    fun executionManifestKey(resourceId: String): String =
        "$SURFACE_EXECUTION_MANIFEST:${safeKey(resourceId)}"

    fun providerLookupKey(requirement: String): String =
        "$SURFACE_PROVIDER_LOOKUP:${safeKey(requirement)}"

    private fun safeKey(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9._>=<-]+"), "-").ifBlank { "resource" }
}
