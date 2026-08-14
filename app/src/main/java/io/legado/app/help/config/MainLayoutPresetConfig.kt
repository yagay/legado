package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import splitties.init.appCtx

/** Minimal compatibility for discovery top-bar defaults. */
object MainLayoutPresetConfig {
    const val PRESET_DEFAULT = "default"
    const val PRESET_REGULAR = "regular"
    const val PRESET_SIDEBAR = "sidebar"

    private const val BOTTOM_BAR_LAYOUT_MODE_KEY = "bottomBarLayoutMode"
    private const val FLOATING_BOTTOM_BAR_HIDE_SEARCH_KEY = "floatingBottomBarHideSearch"

    fun currentPreset(): String = PRESET_DEFAULT

    fun defaultBottomLayoutMode(): String = "floating"

    fun bottomLayoutMode(): String {
        return appCtx.getPrefString(BOTTOM_BAR_LAYOUT_MODE_KEY, defaultBottomLayoutMode())
            ?.takeIf { it == "floating" || it == "sidebar" || it == "standard" }
            ?: defaultBottomLayoutMode()
    }

    fun defaultTopBarStyle(): String = TopBarConfig.STYLE_DEFAULT

    fun defaultTopBarShowSearch(): Boolean = false

    fun floatingBottomBarHideSearch(): Boolean {
        return appCtx.getPrefBoolean(FLOATING_BOTTOM_BAR_HIDE_SEARCH_KEY, false)
    }
}
