package com.kite.app.feature.resources

import com.kite.app.resources.KiteResourceInstallPlanCompiler

internal data class ResourceDetailBadge(
    val label: String,
    val iconText: String,
    val accent: String
)

internal data class ResourceDetailPreview(
    val title: String,
    val subtitle: String,
    val symbol: String,
    val accent: String,
    val iconAsset: String,
    val iconFit: String
)

internal data class ResourceDetailRequirement(val label: String, val value: String)

internal data class ResourceDetailRecommendation(val resourceId: String, val label: String)

internal data class ResourceDetailStep(val title: String, val preview: String)

internal data class ResourceDetailPresentation(
    val item: ResourceItemPresentation,
    val longDescription: String,
    val badge: ResourceDetailBadge,
    val mediaAsset: String,
    val mediaDescription: String,
    val previews: List<ResourceDetailPreview>,
    val requirements: List<ResourceDetailRequirement>,
    val recommendations: List<ResourceDetailRecommendation>,
    val includes: List<String>,
    val notes: List<String>,
    val steps: List<ResourceDetailStep>,
    val sourceTitle: String,
    val sourceSubtitle: String,
    val staticSignature: String
)

internal fun ResourceItemUiState.detailPresentation(): ResourceDetailPresentation {
    val item = presentation()
    val manifest = descriptor.manifest
    val badge = manifest?.displayBadge
    val detailBadge = ResourceDetailBadge(
        label = badge?.label.orEmpty().ifBlank { "Kite 官方资源" },
        iconText = badge?.iconText.orEmpty().ifBlank { "✓" },
        accent = badge?.accent.orEmpty().ifBlank { item.accent }
    )
    val previews = manifest?.displayPreviewCards.orEmpty().map { preview ->
        ResourceDetailPreview(
            title = preview.title,
            subtitle = preview.subtitle,
            symbol = preview.symbol.ifBlank { item.iconText },
            accent = preview.accent.ifBlank { item.accent },
            iconAsset = preview.iconAsset.ifBlank { item.iconAsset },
            iconFit = preview.iconFit.ifBlank { item.iconFit }
        )
    }.ifEmpty {
        listOf(
            ResourceDetailPreview("工作台", "管理模型、对话和提示词", item.iconText, item.accent, item.iconAsset, item.iconFit),
            ResourceDetailPreview("资源卡片", "一键部署，快速启动", item.iconText, item.accent, item.iconAsset, item.iconFit),
            ResourceDetailPreview("启动访问", "配置完成后直接打开", "✓", item.accent, "", "")
        )
    }
    val requirements = manifest?.displayRequirementRows.orEmpty().map {
        ResourceDetailRequirement(it.label, it.value)
    }.ifEmpty {
        listOf(
            ResourceDetailRequirement("获取来源", item.sourceLabel),
            ResourceDetailRequirement("占用空间", item.sizeLabel),
            ResourceDetailRequirement("资源类型", item.category)
        ).filter { it.value.isNotBlank() }
    }
    val includes = (
        manifest?.provides.orEmpty() +
            manifest?.installActions.orEmpty().flatMap { it.managedCommands } +
            manifest?.homeCards.orEmpty().map { it.label }
        ).map(String::trim).filter(String::isNotBlank).distinct()
    val relationNotes = buildList {
        manifest?.baseRequirements.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add("基础层：${it.joinToString("、")}") }
        manifest?.defaultRequirements.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add("默认层：${it.joinToString("、")}") }
        manifest?.extensions.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add("扩展层：${it.joinToString("、")}") }
        when (manifest?.sourceType) {
            "bundled" -> add("来源：内置资源包")
            "apt" -> add("来源：Ubuntu apt")
            "npm" -> add("来源：npm")
            "official_script" -> add("来源：官方安装脚本")
            "git" -> add("来源：Git 仓库")
        }
    }
    val steps = manifest?.installActions.orEmpty().mapIndexed { index, action ->
        ResourceDetailStep(
            title = when {
                index > 0 -> "执行获取步骤 ${index + 1}"
                manifest?.sourceType == "bundled" -> "安装内置资源"
                manifest?.sourceType == "apt" -> "安装 apt 包"
                manifest?.sourceType == "npm" -> "安装 npm 包"
                manifest?.sourceType == "official_script" -> "执行官方安装器"
                manifest?.sourceType == "git" -> "获取源码"
                else -> "执行获取步骤"
            },
            preview = KiteResourceInstallPlanCompiler.preview(action)
        )
    }.ifEmpty { listOf(ResourceDetailStep("打开资源", descriptor.name)) }
    val sourceTitle = when {
        item.sourceLabel.contains("内置") || item.sizeLabel.contains("内置") -> "内置资源包"
        item.sourceLabel.equals("apt", ignoreCase = true) -> "Ubuntu apt"
        item.sourceLabel.contains("官方") -> "官方来源"
        item.sourceLabel.contains("网络") || item.sizeLabel.contains("网络") -> "网络下载"
        else -> item.sourceLabel.ifBlank { "本地定义" }
    }
    val sourceSubtitle = listOf(item.sourceLabel, item.version, item.sizeLabel)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(" · ")
    val recommendations = manifest?.displayRecommendations.orEmpty().map {
        ResourceDetailRecommendation(it.resourceId, it.label)
    }
    return ResourceDetailPresentation(
        item = item,
        longDescription = manifest?.displayLongDescription.orEmpty()
            .ifBlank { manifest?.description.orEmpty().ifBlank { item.description } },
        badge = detailBadge,
        mediaAsset = manifest?.displayMedia?.asset.orEmpty(),
        mediaDescription = manifest?.displayMedia?.contentDescription.orEmpty(),
        previews = previews,
        requirements = requirements,
        recommendations = recommendations,
        includes = includes,
        notes = relationNotes.distinct(),
        steps = steps,
        sourceTitle = sourceTitle,
        sourceSubtitle = sourceSubtitle,
        staticSignature = listOf(
            item.id,
            item.name,
            item.description,
            item.version,
            item.iconAsset,
            detailBadge.toString(),
            manifest?.displayMedia.toString(),
            previews.toString(),
            requirements.toString(),
            recommendations.toString(),
            steps.toString()
        ).joinToString("|")
    )
}
