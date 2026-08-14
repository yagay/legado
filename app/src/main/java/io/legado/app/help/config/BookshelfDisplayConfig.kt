package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import splitties.init.appCtx

/**
 * 增强书架及复用书架列表组件的显示配置。
 *
 * 保持原键名与 JSON 结构，兼容已保存的分组标签设置。
 */
object BookshelfDisplayConfig {

    private const val LIST_ITEM_STYLE_KEY = "bookshelfListItemStyle"
    private const val HIDDEN_TAGS_KEY = "bookshelfHiddenTags"
    private const val GROUP_TAGS_KEY = "bookshelfGroupTags"

    var listItemStyle: Int
        get() = appCtx.getPrefInt(LIST_ITEM_STYLE_KEY, 0).coerceIn(0, 1)
        set(value) = appCtx.putPrefInt(LIST_ITEM_STYLE_KEY, value.coerceIn(0, 1))

    var hiddenTags: Map<Long, Set<String>>
        get() = GSON.fromJsonObject<Map<String, List<String>>>(
            appCtx.getPrefString(HIDDEN_TAGS_KEY)
        ).getOrDefault(emptyMap()).mapNotNull { (key, value) ->
            key.toLongOrNull()?.let {
                it to value.filter(String::isNotBlank).toSet()
            }
        }.toMap()
        set(value) {
            val normalized = value
                .filterValues { it.isNotEmpty() }
                .mapKeys { it.key.toString() }
            if (normalized.isEmpty()) {
                appCtx.removePref(HIDDEN_TAGS_KEY)
            } else {
                appCtx.putPrefString(HIDDEN_TAGS_KEY, GSON.toJson(normalized))
            }
        }

    var groupTags: Map<Long, List<String>>
        get() = GSON.fromJsonObject<Map<String, List<String>>>(
            appCtx.getPrefString(GROUP_TAGS_KEY)
        ).getOrDefault(emptyMap()).mapNotNull { (key, value) ->
            key.toLongOrNull()?.let {
                it to value.filter(String::isNotBlank).distinct()
            }
        }.toMap()
        set(value) {
            val normalized = value
                .filterValues { it.isNotEmpty() }
                .mapKeys { it.key.toString() }
            if (normalized.isEmpty()) {
                appCtx.removePref(GROUP_TAGS_KEY)
            } else {
                appCtx.putPrefString(GROUP_TAGS_KEY, GSON.toJson(normalized))
            }
        }
}
