package com.kite.app.resources

import com.kite.app.foundation.contracts.UbuntuPortsSourceCatalog
import java.net.URI

/** 用户可排序的资源来源；协议端点集中在这里，不再散落到各张资源卡。 */
data class KiteResourceSourceDefinition(
    val id: String,
    val npmRegistry: String = "",
    val pypiIndex: String = "",
    val ubuntuPortsBaseUrl: String = "",
    val hostSuffixes: Set<String> = emptySet(),
)

data class KiteResourceSourceRoute(
    val sourceId: String,
    val endpoint: String,
)

/** 用户只拥有来源顺序。来源能力、端点和安装事实由资源层持有。 */
data class KiteResourceSourcePreferences(
    val orderedSourceIds: List<String> = KiteResourceSourceCatalog.defaultOrder,
) {
    fun normalized(): KiteResourceSourcePreferences = copy(
        orderedSourceIds = KiteResourceSourceCatalog.normalizeOrder(orderedSourceIds),
    )

    fun move(sourceId: String, offset: Int): KiteResourceSourcePreferences {
        val normalized = normalized().orderedSourceIds.toMutableList()
        val current = normalized.indexOf(sourceId)
        if (current < 0) return KiteResourceSourcePreferences(normalized)
        val target = (current + offset).coerceIn(0, normalized.lastIndex)
        if (target == current) return KiteResourceSourcePreferences(normalized)
        normalized.removeAt(current)
        normalized.add(target, sourceId)
        return KiteResourceSourcePreferences(normalized)
    }

    fun encode(): String = normalized().orderedSourceIds.joinToString(",")

    companion object {
        fun decode(value: String?): KiteResourceSourcePreferences = KiteResourceSourcePreferences(
            value.orEmpty().split(',').map(String::trim).filter(String::isNotBlank),
        ).normalized()

        /** 兼容上一轮尚未发布的三档策略和可能存在的本地调试数据。 */
        fun migrateLegacyMode(value: String?): KiteResourceSourcePreferences = when (value) {
            "official_first" -> KiteResourceSourcePreferences(
                listOf(
                    KiteResourceSourceCatalog.OFFICIAL,
                    KiteResourceSourceCatalog.HUAWEI,
                    KiteResourceSourceCatalog.NPM_MIRROR,
                    KiteResourceSourceCatalog.ALIYUN,
                    KiteResourceSourceCatalog.TUNA,
                    KiteResourceSourceCatalog.GITCODE,
                ),
            ).normalized()
            else -> KiteResourceSourcePreferences().normalized()
        }
    }
}

object KiteResourceSourceCatalog {
    const val HUAWEI = "huawei"
    const val NPM_MIRROR = "npmmirror"
    const val ALIYUN = "aliyun"
    const val TUNA = "tuna"
    const val GITCODE = "gitcode"
    const val OFFICIAL = "official"

    val definitions: List<KiteResourceSourceDefinition> = listOf(
        KiteResourceSourceDefinition(
            id = HUAWEI,
            npmRegistry = "https://repo.huaweicloud.com/repository/npm/",
            pypiIndex = "https://repo.huaweicloud.com/repository/pypi/simple",
            ubuntuPortsBaseUrl = UbuntuPortsSourceCatalog.HUAWEI,
            hostSuffixes = setOf("repo.huaweicloud.com"),
        ),
        KiteResourceSourceDefinition(
            id = NPM_MIRROR,
            npmRegistry = "https://registry.npmmirror.com",
            hostSuffixes = setOf("registry.npmmirror.com", "npmmirror.com"),
        ),
        KiteResourceSourceDefinition(
            id = ALIYUN,
            pypiIndex = "https://mirrors.aliyun.com/pypi/simple/",
            ubuntuPortsBaseUrl = UbuntuPortsSourceCatalog.ALIYUN,
            hostSuffixes = setOf("mirrors.aliyun.com"),
        ),
        KiteResourceSourceDefinition(
            id = TUNA,
            pypiIndex = "https://pypi.tuna.tsinghua.edu.cn/simple",
            ubuntuPortsBaseUrl = UbuntuPortsSourceCatalog.TUNA,
            hostSuffixes = setOf("pypi.tuna.tsinghua.edu.cn", "mirrors.tuna.tsinghua.edu.cn"),
        ),
        KiteResourceSourceDefinition(
            id = GITCODE,
            hostSuffixes = setOf("gitcode.com"),
        ),
        KiteResourceSourceDefinition(
            id = OFFICIAL,
            npmRegistry = "https://registry.npmjs.org",
            pypiIndex = "https://pypi.org/simple",
            ubuntuPortsBaseUrl = UbuntuPortsSourceCatalog.OFFICIAL,
            hostSuffixes = setOf(
                "npmjs.org",
                "pypi.org",
                "pythonhosted.org",
                "ports.ubuntu.com",
                "github.com",
                "githubusercontent.com",
            ),
        ),
    )

    val defaultOrder: List<String> = listOf(
        HUAWEI,
        NPM_MIRROR,
        ALIYUN,
        TUNA,
        GITCODE,
        OFFICIAL,
    )

    private val byId = definitions.associateBy(KiteResourceSourceDefinition::id)

    fun definition(sourceId: String): KiteResourceSourceDefinition? = byId[sourceId]

    fun normalizeOrder(sourceIds: List<String>): List<String> =
        (sourceIds.filter(byId::containsKey) + defaultOrder).distinct()

    fun sourceIdFor(endpoint: String): String {
        val host = runCatching { URI(endpoint.trim()).host.orEmpty().lowercase() }.getOrDefault("")
        return definitions.firstOrNull { definition ->
            definition.id != OFFICIAL && definition.hostSuffixes.any { suffix ->
                host == suffix || host.endsWith(".$suffix")
            }
        }?.id ?: OFFICIAL
    }
}

/** 所有安装共用的来源解析；不探测网速，只执行用户保存的顺序。 */
object KiteResourceSourcePolicy {
    fun apply(
        action: KiteResourceShellAction,
        preferences: KiteResourceSourcePreferences,
    ): KiteResourceShellAction {
        if (action.type != KiteResourceInstallPlanCompiler.ACTION_MANAGED) return action
        val normalized = preferences.normalized()
        return action.copy(
            installSteps = action.installSteps.map { step ->
                when (step.type) {
                    KiteResourceInstallPlanCompiler.STEP_DOWNLOAD -> step.copy(
                        urls = orderDeclaredEndpoints(step.urls, normalized),
                    )
                    KiteResourceInstallPlanCompiler.STEP_GIT -> {
                        val repositories = step.repositories.ifEmpty {
                            listOf(step.repository).filter(String::isNotBlank)
                        }
                        val ordered = orderDeclaredEndpoints(repositories, normalized)
                        step.copy(
                            repository = ordered.firstOrNull().orEmpty(),
                            repositories = ordered,
                        )
                    }
                    KiteResourceInstallPlanCompiler.STEP_NPM -> step.copy(
                        registries = mergeRoutes(
                            configured = step.registries,
                            catalog = npmRoutes(normalized).map(KiteResourceSourceRoute::endpoint),
                            preferences = normalized,
                        ),
                    )
                    else -> step
                }
            },
        )
    }

    fun npmRoutes(preferences: KiteResourceSourcePreferences): List<KiteResourceSourceRoute> =
        routes(preferences) { it.npmRegistry }

    fun pypiRoutes(preferences: KiteResourceSourcePreferences): List<KiteResourceSourceRoute> =
        routes(preferences) { it.pypiIndex }

    fun ubuntuPortsRoutes(preferences: KiteResourceSourcePreferences): List<KiteResourceSourceRoute> =
        routes(preferences) { it.ubuntuPortsBaseUrl }

    fun orderDeclaredEndpoints(
        endpoints: List<String>,
        preferences: KiteResourceSourcePreferences,
    ): List<String> {
        val rank = preferences.normalized().orderedSourceIds.withIndex().associate { it.value to it.index }
        return endpoints.filter(String::isNotBlank).distinct().withIndex()
            .sortedWith(compareBy({ rank[KiteResourceSourceCatalog.sourceIdFor(it.value)] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    private fun routes(
        preferences: KiteResourceSourcePreferences,
        endpoint: (KiteResourceSourceDefinition) -> String,
    ): List<KiteResourceSourceRoute> = preferences.normalized().orderedSourceIds.mapNotNull { sourceId ->
        val value = KiteResourceSourceCatalog.definition(sourceId)?.let(endpoint).orEmpty()
        value.takeIf(String::isNotBlank)?.let { KiteResourceSourceRoute(sourceId, it) }
    }

    private fun mergeRoutes(
        configured: List<String>,
        catalog: List<String>,
        preferences: KiteResourceSourcePreferences,
    ): List<String> = orderDeclaredEndpoints(catalog + configured, preferences)
}
