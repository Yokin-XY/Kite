package com.kite.app.resources

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 一次性提供同一代资源目录文档。
 *
 * APK 内置资源、经过校验的服务器缓存和 Debug 验收资源都实现这个边界；调用方只消费
 * 完整快照，不在读取过程中拼接不同代次的 manifest 与首页布局。
 */
internal data class KiteResourceDefinitionSnapshot(
    val revision: String,
    val manifests: Map<String, String>,
    val homeLayoutJson: String? = null
)

internal interface KiteResourceDefinitionSource {
    fun snapshot(): KiteResourceDefinitionSnapshot

    fun invalidate()
}

/**
 * 按传入顺序合并资源来源。靠前的来源拥有更高优先级，可以覆盖同 id 的低优先级资源；
 * 服务器来源必须在进入这里以前完成下载、签名校验和原子缓存切换。
 */
internal class KiteResourceCompositeDefinitionSource(
    private val sources: List<KiteResourceDefinitionSource>
) : KiteResourceDefinitionSource {
    override fun snapshot(): KiteResourceDefinitionSnapshot {
        val snapshots = sources.map(KiteResourceDefinitionSource::snapshot)
        val manifests = linkedMapOf<String, String>()
        snapshots.forEach { snapshot ->
            snapshot.manifests.forEach { (resourceId, manifestJson) ->
                manifests.putIfAbsent(resourceId, manifestJson)
            }
        }
        return KiteResourceDefinitionSnapshot(
            revision = snapshots.joinToString("|") { it.revision },
            manifests = manifests,
            homeLayoutJson = snapshots.firstNotNullOfOrNull { it.homeLayoutJson }
        )
    }

    override fun invalidate() {
        sources.forEach(KiteResourceDefinitionSource::invalidate)
    }
}

/** APK 内置资源来源。读取结果按快照缓存，Loader invalidate 时才重新装载。 */
internal class KiteResourceAssetDefinitionSource(
    private val context: Context
) : KiteResourceDefinitionSource {
    private val lock = Any()
    private var cached: KiteResourceDefinitionSnapshot? = null

    override fun snapshot(): KiteResourceDefinitionSnapshot = synchronized(lock) {
        cached?.let { return@synchronized it }
        val manifests = linkedMapOf<String, String>()
        resourceIds()
            .forEach { resourceId ->
                readText("$ASSET_ROOT/$resourceId/manifest.json")?.let { json ->
                    manifests[resourceId] = json
                }
            }
        val homeLayout = readText("$ASSET_ROOT/home.json")
        KiteResourceDefinitionSnapshot(
            revision = "asset-${manifests.hashCode().toUInt().toString(16)}-${homeLayout.hashCode().toUInt().toString(16)}",
            manifests = manifests,
            homeLayoutJson = homeLayout
        ).also { cached = it }
    }

    override fun invalidate() {
        synchronized(lock) {
            cached = null
        }
    }

    private fun readText(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.onFailure { error ->
        Log.w(TAG, "Failed to read resource definition: $path", error)
    }.getOrNull()

    private fun resourceIds(): List<String> {
        val indexed = readText("$ASSET_ROOT/index.json")
            ?.let(::JSONObject)
            ?.optJSONArray("resources")
            ?.let { resources ->
                buildList {
                    for (index in 0 until resources.length()) {
                        resources.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            .orEmpty()
        if (indexed.isNotEmpty()) return indexed.distinct().sorted()
        return context.assets.list(ASSET_ROOT).orEmpty()
            .filterNot { it.endsWith(".json", ignoreCase = true) }
            .sorted()
    }

    private companion object {
        const val ASSET_ROOT = "resources"
        const val TAG = "KiteResourceSource"
    }
}
