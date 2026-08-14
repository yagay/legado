package io.legado.app.model.webBook

import com.google.gson.JsonArray
import io.legado.app.utils.GSON

/**
 * Compatibility parser for a small set of legacy discovery APIs returning
 * [{"content":[...]}].
 */
internal object ExploreContentCompatibility {

    fun extractArray(body: String, isSearch: Boolean): JsonArray? {
        if (isSearch) return null
        val root = runCatching { GSON.fromJson(body, JsonArray::class.java) }.getOrNull()
            ?: return null
        if (root.size() != 1 || !root.first().isJsonObject) return null
        val content = root.first().asJsonObject.get("content") ?: return null
        return content.takeIf { it.isJsonArray }?.asJsonArray
    }
}
