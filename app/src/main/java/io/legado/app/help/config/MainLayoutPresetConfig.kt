package io.legado.app.help.config

/** Minimal compatibility for discovery top-bar defaults. */
object MainLayoutPresetConfig {
    const val PRESET_DEFAULT = "default"
    const val PRESET_REGULAR = "regular"
    const val PRESET_SIDEBAR = "sidebar"
    fun currentPreset(): String = PRESET_DEFAULT
    fun defaultBottomLayoutMode(): String = "floating"
    fun defaultTopBarStyle(): String = TopBarConfig.STYLE_DEFAULT
    fun defaultTopBarShowSearch(): Boolean = false
    fun floatingBottomBarHideSearch(): Boolean = false
}
