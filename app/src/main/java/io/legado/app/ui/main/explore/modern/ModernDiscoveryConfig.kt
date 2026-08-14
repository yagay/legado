package io.legado.app.ui.main.explore.modern

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import splitties.init.appCtx

/**
 * 现代发现页私有配置。
 *
 * 偏好键保持旧值，升级后无需迁移；上游 AppConfig 与 PreferKey 不再感知现代布局。
 */
internal object ModernDiscoveryConfig {

    const val MODE_LEGACY = "legacy"
    const val MODE_MODERN = "modern"
    const val MODE_SUITE = "suite"

    const val KEY_PAGE_MODE = "discoveryPageMode"

    private const val KEY_LEGACY_MODERN_PAGE = "modernDiscoveryPage"
    private const val KEY_LAYOUT = "discoveryPageLayout"
    private const val KEY_SOURCE_URL = "modernDiscoverySourceUrl"
    private const val KEY_TAG_URLS = "modernDiscoveryTagUrls"
    private const val KEY_TREE_SELECTIONS = "modernDiscoveryTreeSelections"

    var pageMode: String
        get() = when (val value = appCtx.getPrefString(KEY_PAGE_MODE)) {
            MODE_LEGACY, MODE_MODERN -> value
            MODE_SUITE -> MODE_MODERN
            else -> MODE_LEGACY
        }
        set(value) {
            val normalized = value.takeIf {
                it == MODE_LEGACY || it == MODE_MODERN
            } ?: MODE_LEGACY
            appCtx.putPrefString(KEY_PAGE_MODE, normalized)
            // 保留旧版本布尔键，允许降级安装时继续识别当前模式。
            appCtx.putPrefBoolean(KEY_LEGACY_MODERN_PAGE, normalized != MODE_LEGACY)
        }

    var layout: Int
        get() = appCtx.getPrefInt(KEY_LAYOUT, 1).coerceIn(1, 3)
        set(value) = appCtx.putPrefInt(KEY_LAYOUT, value.coerceIn(1, 3))

    var sourceUrl: String?
        get() = appCtx.getPrefString(KEY_SOURCE_URL)
        set(value) {
            if (value.isNullOrBlank()) appCtx.removePref(KEY_SOURCE_URL)
            else appCtx.putPrefString(KEY_SOURCE_URL, value)
        }

    fun tagUrl(sourceUrl: String?): String? {
        val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return null
        return tagUrlMap()[key]?.takeIf { it.isNotBlank() }
    }

    fun rememberTagUrl(sourceUrl: String?, tagUrl: String?) {
        val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return
        val values = tagUrlMap().toMutableMap()
        tagUrl?.takeIf { it.isNotBlank() }?.let { values[key] = it } ?: values.remove(key)
        if (values.isEmpty()) appCtx.removePref(KEY_TAG_URLS)
        else appCtx.putPrefString(KEY_TAG_URLS, GSON.toJson(values))
    }

    private fun tagUrlMap(): Map<String, String> =
        GSON.fromJsonObject<Map<String, String>>(
            appCtx.getPrefString(KEY_TAG_URLS)
        ).getOrDefault(emptyMap())
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotBlank() }

    fun treeSelections(sourceUrl: String?): List<String> {
        val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return emptyList()
        return treeSelectionMap()[key].orEmpty().filter { it.isNotBlank() }
    }

    fun rememberTreeSelections(sourceUrl: String?, selections: List<String>) {
        val key = sourceUrl?.takeIf { it.isNotBlank() } ?: return
        val values = treeSelectionMap().toMutableMap()
        selections.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.let { values[key] = it }
            ?: values.remove(key)
        if (values.isEmpty()) appCtx.removePref(KEY_TREE_SELECTIONS)
        else appCtx.putPrefString(KEY_TREE_SELECTIONS, GSON.toJson(values))
    }

    private fun treeSelectionMap(): Map<String, List<String>> =
        GSON.fromJsonObject<Map<String, List<String>>>(
            appCtx.getPrefString(KEY_TREE_SELECTIONS)
        ).getOrDefault(emptyMap())
            .filterKeys { it.isNotBlank() }
}
