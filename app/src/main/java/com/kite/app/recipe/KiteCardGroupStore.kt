package com.kite.app.recipe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

data class KiteCardGroup(
    val id: String,
    val name: String
)

class KiteCardGroupStore(context: Context) {
    private val file = File(context.filesDir, "card-groups.json")
    private var cache: List<KiteCardGroup>? = null

    @Synchronized
    fun groups(): List<KiteCardGroup> =
        cache ?: readGroups().also { cache = it }

    @Synchronized
    fun create(name: String): KiteCardGroup {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "group_name_blank" }
        groups().firstOrNull { it.name.equals(cleanName, ignoreCase = true) }?.let { return it }
        val next = KiteCardGroup(uniqueId(cleanName, groups()), cleanName)
        cache = groups() + next
        writeGroups(cache.orEmpty())
        return next
    }

    private fun readGroups(): List<KiteCardGroup> =
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val array = JSONObject(file.readText()).optJSONArray("groups") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isNotBlank() && name.isNotBlank()) add(KiteCardGroup(id, name))
                }
            }
        }.getOrDefault(emptyList())

    private fun writeGroups(groups: List<KiteCardGroup>) {
        file.writeText(JSONObject()
            .put("groups", JSONArray().apply {
                groups.forEach { group ->
                    put(JSONObject()
                        .put("id", group.id)
                        .put("name", group.name)
                    )
                }
            })
            .toString(2)
        )
    }

    private fun uniqueId(name: String, groups: List<KiteCardGroup>): String {
        val base = slug(name).ifBlank { "group" }
        val used = groups.map { it.id }.toSet()
        var candidate = base
        var suffix = 2
        while (candidate in used) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }

    private fun slug(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.US), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
            .trim('-')
            .take(48)
}
