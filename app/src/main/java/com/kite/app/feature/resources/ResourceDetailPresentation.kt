package com.kite.app.feature.resources

import android.content.Context
import com.kite.app.R
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

internal fun ResourceItemUiState.detailPresentation(context: Context): ResourceDetailPresentation {
    val item = presentation(context)
    val manifest = descriptor.manifest
    val badge = manifest?.displayBadge
    val detailBadge = ResourceDetailBadge(
        label = badge?.label.orEmpty().ifBlank { context.getString(R.string.resource_detail_official_badge) },
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
            ResourceDetailPreview(
                context.getString(R.string.resource_detail_preview_workspace),
                context.getString(R.string.resource_detail_preview_workspace_summary),
                item.iconText, item.accent, item.iconAsset, item.iconFit
            ),
            ResourceDetailPreview(
                context.getString(R.string.resource_detail_preview_card),
                context.getString(R.string.resource_detail_preview_card_summary),
                item.iconText, item.accent, item.iconAsset, item.iconFit
            ),
            ResourceDetailPreview(
                context.getString(R.string.resource_detail_preview_launch),
                context.getString(R.string.resource_detail_preview_launch_summary),
                "✓", item.accent, "", ""
            )
        )
    }
    val requirements = manifest?.displayRequirementRows.orEmpty().map {
        ResourceDetailRequirement(it.label, it.value)
    }.ifEmpty {
        listOf(
            ResourceDetailRequirement(context.getString(R.string.resource_detail_requirement_source), item.sourceLabel),
            ResourceDetailRequirement(context.getString(R.string.resource_detail_requirement_space), item.sizeLabel),
            ResourceDetailRequirement(context.getString(R.string.resource_detail_requirement_type), item.category)
        ).filter { it.value.isNotBlank() }
    }
    val includes = (
        manifest?.provides.orEmpty() +
            manifest?.installActions.orEmpty().flatMap { it.managedCommands } +
            manifest?.homeCards.orEmpty().map { it.label }
        ).map(String::trim).filter(String::isNotBlank).distinct()
    val relationNotes = buildList {
        val separator = context.getString(R.string.resource_list_separator)
        manifest?.baseRequirements.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add(context.getString(R.string.resource_detail_relation_base, it.joinToString(separator))) }
        manifest?.defaultRequirements.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add(context.getString(R.string.resource_detail_relation_default, it.joinToString(separator))) }
        manifest?.extensions.orEmpty().takeIf(List<String>::isNotEmpty)
            ?.let { add(context.getString(R.string.resource_detail_relation_extensions, it.joinToString(separator))) }
        when (manifest?.sourceType) {
            "bundled" -> add(context.getString(
                R.string.resource_detail_relation_source,
                context.getString(R.string.resource_detail_source_bundled)
            ))
            "apt" -> add(context.getString(R.string.resource_detail_relation_source, "Ubuntu apt"))
            "npm" -> add(context.getString(R.string.resource_detail_relation_source, "npm"))
            "official_script" -> add(context.getString(
                R.string.resource_detail_relation_source,
                context.getString(R.string.resource_source_official_script_short)
            ))
            "git" -> add(context.getString(
                R.string.resource_detail_relation_source,
                context.getString(R.string.resource_detail_source_git)
            ))
        }
    }
    val steps = manifest?.installActions.orEmpty().mapIndexed { index, action ->
        ResourceDetailStep(
            title = when {
                index > 0 -> context.getString(R.string.resource_detail_step_number, index + 1)
                manifest?.sourceType == "bundled" -> context.getString(R.string.resource_detail_step_install_bundled)
                manifest?.sourceType == "apt" -> context.getString(R.string.resource_detail_step_install_apt)
                manifest?.sourceType == "npm" -> context.getString(R.string.resource_detail_step_install_npm)
                manifest?.sourceType == "official_script" -> context.getString(R.string.resource_detail_step_official_installer)
                manifest?.sourceType == "git" -> context.getString(R.string.resource_detail_step_fetch_source)
                else -> context.getString(R.string.resource_detail_step_install)
            },
            preview = KiteResourceInstallPlanCompiler.preview(action)
        )
    }.ifEmpty { listOf(ResourceDetailStep(context.getString(R.string.resource_detail_step_open), descriptor.name)) }
    val sourceTitle = when (manifest?.sourceType) {
        "bundled" -> context.getString(R.string.resource_detail_source_bundled)
        "apt" -> "Ubuntu apt"
        "official_script" -> context.getString(R.string.resource_detail_source_official)
        "npm", "git", "command" -> context.getString(R.string.resource_detail_source_network)
        else -> item.sourceLabel.ifBlank { context.getString(R.string.resource_detail_source_local) }
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
