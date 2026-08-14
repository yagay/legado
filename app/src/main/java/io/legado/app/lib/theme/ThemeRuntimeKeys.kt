package io.legado.app.lib.theme

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.defaultSharedPreferences

object ThemeRuntimeKeys {

    private const val legacyNightMigratedKey = "themeNightExtMigrated"

    // 日夜键拆分前，这些字段日夜共用旧键。首次升级时把旧值复制到夜间键，
    // 否则夜间模式的字号/字体/颜色/透明度等会全部回落默认值。
    private val legacyNightPairs = listOf(
        PreferKey.fontScale to ThemePreferenceKeys.fontScaleN,
        ThemePreferenceKeys.uiFontPath to ThemePreferenceKeys.uiFontPathN,
        ThemePreferenceKeys.titleFontPath to ThemePreferenceKeys.titleFontPathN,
        ThemePreferenceKeys.uiFontColor to ThemePreferenceKeys.uiFontColorN,
        ThemePreferenceKeys.titleFontColor to ThemePreferenceKeys.titleFontColorN,
        ThemePreferenceKeys.uiCornerScale to ThemePreferenceKeys.uiCornerScaleN,
        ThemePreferenceKeys.uiLayoutAlpha to ThemePreferenceKeys.uiLayoutAlphaN,
        ThemePreferenceKeys.dialogAlpha to ThemePreferenceKeys.dialogAlphaN,
        ThemePreferenceKeys.uiCornerSearchFollow to ThemePreferenceKeys.uiCornerSearchFollowN,
        ThemePreferenceKeys.uiCornerReplyFollow to ThemePreferenceKeys.uiCornerReplyFollowN,
        ThemePreferenceKeys.themeCardColor to ThemePreferenceKeys.themeCardColorN,
        ThemePreferenceKeys.themeMutedColor to ThemePreferenceKeys.themeMutedColorN,
        ThemePreferenceKeys.themeSearchFieldBackgroundColor to ThemePreferenceKeys.themeSearchFieldBackgroundColorN,
        ThemePreferenceKeys.themeTabBackgroundColor to ThemePreferenceKeys.themeTabBackgroundColorN,
        ThemePreferenceKeys.themeShelfColor to ThemePreferenceKeys.themeShelfColorN,
        ThemePreferenceKeys.themeCardShadow to ThemePreferenceKeys.themeCardShadowN,
        ThemePreferenceKeys.themeCardBackgroundBlur to ThemePreferenceKeys.themeCardBackgroundBlurN
    )

    fun migrateLegacyNightValues(context: Context) {
        val prefs = context.defaultSharedPreferences
        if (prefs.getBoolean(legacyNightMigratedKey, false)) return
        val all = prefs.all
        val editor = prefs.edit()
        legacyNightPairs.forEach { (dayKey, nightKey) ->
            if (!all.containsKey(nightKey)) {
                when (val value = all[dayKey]) {
                    is Int -> editor.putInt(nightKey, value)
                    is Boolean -> editor.putBoolean(nightKey, value)
                    is String -> editor.putString(nightKey, value)
                    is Float -> editor.putFloat(nightKey, value)
                    is Long -> editor.putLong(nightKey, value)
                }
            }
        }
        editor.putBoolean(legacyNightMigratedKey, true)
        editor.commit()
    }

    fun fontScale(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.fontScaleN else PreferKey.fontScale

    fun uiFontPath(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiFontPathN else ThemePreferenceKeys.uiFontPath

    fun titleFontPath(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.titleFontPathN else ThemePreferenceKeys.titleFontPath

    fun uiFontColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiFontColorN else ThemePreferenceKeys.uiFontColor

    fun titleFontColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.titleFontColorN else ThemePreferenceKeys.titleFontColor

    fun uiCornerScale(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiCornerScaleN else ThemePreferenceKeys.uiCornerScale

    fun uiLayoutAlpha(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiLayoutAlphaN else ThemePreferenceKeys.uiLayoutAlpha

    fun dialogAlpha(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.dialogAlphaN else ThemePreferenceKeys.dialogAlpha

    fun uiCornerSearchFollow(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiCornerSearchFollowN else ThemePreferenceKeys.uiCornerSearchFollow

    fun uiCornerReplyFollow(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.uiCornerReplyFollowN else ThemePreferenceKeys.uiCornerReplyFollow

    fun themeCardColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeCardColorN else ThemePreferenceKeys.themeCardColor

    fun themeMutedColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeMutedColorN else ThemePreferenceKeys.themeMutedColor

    fun themeSearchFieldBackgroundColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeSearchFieldBackgroundColorN else ThemePreferenceKeys.themeSearchFieldBackgroundColor

    fun themeTabBackgroundColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeTabBackgroundColorN else ThemePreferenceKeys.themeTabBackgroundColor

    fun themeShelfColor(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeShelfColorN else ThemePreferenceKeys.themeShelfColor

    fun themeCardShadow(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeCardShadowN else ThemePreferenceKeys.themeCardShadow

    fun themeCardBackgroundBlur(isNight: Boolean = AppConfig.isNightTheme): String =
        if (isNight) ThemePreferenceKeys.themeCardBackgroundBlurN else ThemePreferenceKeys.themeCardBackgroundBlur

    fun activeColorKey(key: String, isNight: Boolean = AppConfig.isNightTheme): String {
        return when (key) {
            ThemePreferenceKeys.themeCardColor, ThemePreferenceKeys.themeCardColorN -> themeCardColor(isNight)
            ThemePreferenceKeys.themeMutedColor, ThemePreferenceKeys.themeMutedColorN -> themeMutedColor(isNight)
            ThemePreferenceKeys.themeSearchFieldBackgroundColor,
            ThemePreferenceKeys.themeSearchFieldBackgroundColorN -> themeSearchFieldBackgroundColor(isNight)
            ThemePreferenceKeys.themeTabBackgroundColor, ThemePreferenceKeys.themeTabBackgroundColorN -> themeTabBackgroundColor(isNight)
            ThemePreferenceKeys.themeShelfColor, ThemePreferenceKeys.themeShelfColorN -> themeShelfColor(isNight)
            else -> key
        }
    }

    fun allKeys(): Set<String> = setOf(
        PreferKey.fontScale,
        ThemePreferenceKeys.fontScaleN,
        ThemePreferenceKeys.uiFontPath,
        ThemePreferenceKeys.uiFontPathN,
        ThemePreferenceKeys.titleFontPath,
        ThemePreferenceKeys.titleFontPathN,
        ThemePreferenceKeys.uiFontColor,
        ThemePreferenceKeys.uiFontColorN,
        ThemePreferenceKeys.titleFontColor,
        ThemePreferenceKeys.titleFontColorN,
        ThemePreferenceKeys.uiCornerScale,
        ThemePreferenceKeys.uiCornerScaleN,
        ThemePreferenceKeys.uiLayoutAlpha,
        ThemePreferenceKeys.uiLayoutAlphaN,
        ThemePreferenceKeys.dialogAlpha,
        ThemePreferenceKeys.dialogAlphaN,
        ThemePreferenceKeys.uiCornerSearchFollow,
        ThemePreferenceKeys.uiCornerSearchFollowN,
        ThemePreferenceKeys.uiCornerReplyFollow,
        ThemePreferenceKeys.uiCornerReplyFollowN,
        ThemePreferenceKeys.themeCardColor,
        ThemePreferenceKeys.themeCardColorN,
        ThemePreferenceKeys.themeMutedColor,
        ThemePreferenceKeys.themeMutedColorN,
        ThemePreferenceKeys.themeSearchFieldBackgroundColor,
        ThemePreferenceKeys.themeSearchFieldBackgroundColorN,
        ThemePreferenceKeys.themeTabBackgroundColor,
        ThemePreferenceKeys.themeTabBackgroundColorN,
        ThemePreferenceKeys.themeShelfColor,
        ThemePreferenceKeys.themeShelfColorN,
        ThemePreferenceKeys.themeCardShadow,
        ThemePreferenceKeys.themeCardShadowN,
        ThemePreferenceKeys.themeCardBackgroundBlur,
        ThemePreferenceKeys.themeCardBackgroundBlurN
    )
}
