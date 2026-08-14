package io.legado.app.ui.main.explore.modern

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.utils.GSON

/**
 * 现代发现页自己的分类树节点。
 *
 * 上游 ExploreKind 保持扁平数据模型；只有现代布局在需要多级分类时才保存 children。
 */
internal data class ModernExploreNode(
    val kind: ExploreKind,
    val children: List<ModernExploreNode> = emptyList()
) {
    val title: String get() = kind.title

    companion object {
        fun leaves(kinds: List<ExploreKind>): List<ModernExploreNode> =
            kinds.map { ModernExploreNode(it) }
    }
}

internal object ModernDiscoveryCategoryTree {

    fun parse(json: String): List<ModernExploreNode> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java)
                .mapNotNull(::parseNode)
        }.getOrDefault(emptyList())
    }

    private fun parseNode(element: JsonElement): ModernExploreNode? {
        if (!element.isJsonObject) return null
        val objectValue = element.asJsonObject
        val kind = GSON.fromJson(objectValue, ExploreKind::class.java) ?: return null
        val children = objectValue.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapNotNull(::parseNode)
            .orEmpty()
        return ModernExploreNode(kind, children)
    }
}
