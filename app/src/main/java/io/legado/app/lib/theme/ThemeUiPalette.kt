package io.legado.app.lib.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.CoverDisplayConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import java.io.File

data class ThemeUiPalette(
    @param:ColorInt val cardColor: Int,
    @param:ColorInt val mutedColor: Int,
    @param:ColorInt val searchFieldBackgroundColor: Int,
    @param:ColorInt val tabBackgroundColor: Int,
    @param:ColorInt val shelfColor: Int,
    @param:ColorInt val dividerColor: Int,
    val hasCustomCardColor: Boolean,
    val hasCustomMutedColor: Boolean,
    val signature: String
)

private val themeUiColorKeys = listOf(
    ThemePreferenceKeys.themeCardColor,
    ThemePreferenceKeys.themeCardColorN,
    ThemePreferenceKeys.themeMutedColor,
    ThemePreferenceKeys.themeMutedColorN,
    ThemePreferenceKeys.themeSearchFieldBackgroundColor,
    ThemePreferenceKeys.themeSearchFieldBackgroundColorN,
    ThemePreferenceKeys.themeTabBackgroundColor,
    ThemePreferenceKeys.themeTabBackgroundColorN,
    ThemePreferenceKeys.themeShelfColor,
    ThemePreferenceKeys.themeShelfColorN
)
private val themeUiShapeKeys = listOf(
    ThemePreferenceKeys.panelBorderColor,
    ThemePreferenceKeys.panelBorderColorN,
    ThemePreferenceKeys.panelBorderAlpha,
    ThemePreferenceKeys.panelBorderAlphaN,
    ThemePreferenceKeys.panelBgImage,
    ThemePreferenceKeys.panelBgImageN,
    ThemePreferenceKeys.panelBgScaleType,
    ThemePreferenceKeys.panelBgScaleTypeN,
    ThemePreferenceKeys.uiCornerScale,
    ThemePreferenceKeys.uiCornerScaleN,
    ThemePreferenceKeys.uiCornerSearchFollow,
    ThemePreferenceKeys.uiCornerSearchFollowN,
    ThemePreferenceKeys.uiCornerReplyFollow,
    ThemePreferenceKeys.uiCornerReplyFollowN,
    ThemePreferenceKeys.uiLayoutAlpha,
    ThemePreferenceKeys.uiLayoutAlphaN,
    ThemePreferenceKeys.uiCornerEffectLevel,
    ThemePreferenceKeys.dialogAlpha,
    ThemePreferenceKeys.dialogAlphaN,
    ThemePreferenceKeys.themeCardShadow,
    ThemePreferenceKeys.themeCardShadowN,
    ThemePreferenceKeys.themeCardBackgroundBlur,
    ThemePreferenceKeys.themeCardBackgroundBlurN,
    CoverDisplayConfig.COVER_SHADOW_KEY
)
private val themeUiTypographyKeys = listOf(
    PreferKey.fontScale,
    ThemePreferenceKeys.fontScaleN,
    ThemePreferenceKeys.uiFontPath,
    ThemePreferenceKeys.uiFontPathN,
    ThemePreferenceKeys.titleFontPath,
    ThemePreferenceKeys.titleFontPathN,
    ThemePreferenceKeys.uiFontColor,
    ThemePreferenceKeys.uiFontColorN,
    ThemePreferenceKeys.titleFontColor,
    ThemePreferenceKeys.titleFontColorN
)
private val themeUiDependencyKeySet = (
    themeUiColorKeys + themeUiShapeKeys + themeUiTypographyKeys + listOf(
        PreferKey.themeMode,
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground
    )
).toSet()

fun Context.themeUiPalette(): ThemeUiPalette {
    val customCardColor = themeColorOrNull(ThemePreferenceKeys.themeCardColor)
    val customMutedColor = themeColorOrNull(ThemePreferenceKeys.themeMutedColor)
    return ThemeUiPalette(
        cardColor = customCardColor ?: themeCardColorOrDefault(),
        mutedColor = customMutedColor ?: themeMutedColorOrDefault(),
        searchFieldBackgroundColor = themeSearchFieldBackgroundColorOrDefault(),
        tabBackgroundColor = themeTabBackgroundColorOrDefault(),
        shelfColor = themeShelfColorOrDefault(),
        dividerColor = themeDividerColorOrDefault(),
        hasCustomCardColor = customCardColor != null,
        hasCustomMutedColor = customMutedColor != null,
        signature = themeUiSignature()
    )
}

@Composable
fun rememberThemeUiPalette(): ThemeUiPalette {
    val context = LocalContext.current
    var signature by remember(context) {
        mutableStateOf(context.themeUiSignature())
    }
    DisposableEffect(context) {
        val defaultPrefs = context.defaultSharedPreferences
        val themeStorePrefs = ThemeStore.prefs(context)
        val defaultListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key in themeUiDependencyKeySet) {
                signature = context.themeUiSignature()
            }
        }
        val themeStoreListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ThemeStorePrefKeys.VALUES_CHANGED) {
                signature = context.themeUiSignature()
            }
        }
        defaultPrefs.registerOnSharedPreferenceChangeListener(defaultListener)
        themeStorePrefs.registerOnSharedPreferenceChangeListener(themeStoreListener)
        onDispose {
            defaultPrefs.unregisterOnSharedPreferenceChangeListener(defaultListener)
            themeStorePrefs.unregisterOnSharedPreferenceChangeListener(themeStoreListener)
        }
    }
    return remember(context, signature) {
        context.themeUiPalette()
    }
}

fun Context.themeUiSignature(): String {
    val themeMode = getPrefString(PreferKey.themeMode, "0")
    val rawPrefs = themeUiColorKeys.joinToString("|") { key ->
        "${key}=${getPrefString(key).orEmpty()}"
    }
    val shapePrefs = listOf(
        "border=${getPrefString(ThemePreferenceKeys.panelBorderColor).orEmpty()}",
        "borderN=${getPrefString(ThemePreferenceKeys.panelBorderColorN).orEmpty()}",
        "borderAlpha=${getPrefInt(ThemePreferenceKeys.panelBorderAlpha, 100)}",
        "borderAlphaN=${getPrefInt(ThemePreferenceKeys.panelBorderAlphaN, 100)}",
        "corner=${getPrefString(ThemeRuntimeKeys.uiCornerScale(), "1").orEmpty()}",
        "searchFollow=${ThemeShapeConfig.searchFollowsCorner}",
        "replyFollow=${ThemeShapeConfig.replyFollowsCorner}",
        "layoutAlpha=${ThemeShapeConfig.layoutAlpha}",
        "dialogAlpha=${DialogDisplayConfig.alpha}",
        "cardShadow=${getPrefInt(ThemeRuntimeKeys.themeCardShadow(), -1)}",
        "cardBackgroundBlur=${getPrefInt(ThemeRuntimeKeys.themeCardBackgroundBlur(), -1)}",
        "bookCoverShadow=${CoverDisplayConfig.shadowEnabled}",
        "fontScale=${getPrefInt(ThemeRuntimeKeys.fontScale(), 0)}",
        "uiFont=${getPrefString(ThemeRuntimeKeys.uiFontPath()).orEmpty()}",
        "titleFont=${getPrefString(ThemeRuntimeKeys.titleFontPath()).orEmpty()}",
        "uiFontColor=${getPrefString(ThemeRuntimeKeys.uiFontColor()).orEmpty()}",
        "titleFontColor=${getPrefString(ThemeRuntimeKeys.titleFontColor()).orEmpty()}",
        "panelImage=${themePanelImageSignature()}"
    ).joinToString("|")
    val computedColors = listOf(
        "card=${themeCardColorOrDefault()}",
        "muted=${themeMutedColorOrDefault()}",
        "search=${themeSearchFieldBackgroundColorOrDefault()}",
        "tab=${themeTabBackgroundColorOrDefault()}",
        "shelf=${themeShelfColorOrDefault()}",
        "divider=${themeDividerColorOrDefault()}"
    ).joinToString("|")
    return "mode=$themeMode|night=${AppConfig.isNightTheme}|eInk=${AppConfig.isEInkMode}|$rawPrefs|$shapePrefs|$computedColors"
}

private fun Context.themePanelImageSignature(): String {
    val imageKey = if (AppConfig.isNightTheme) ThemePreferenceKeys.panelBgImageN else ThemePreferenceKeys.panelBgImage
    val scaleKey = if (AppConfig.isNightTheme) ThemePreferenceKeys.panelBgScaleTypeN else ThemePreferenceKeys.panelBgScaleType
    val path = getPrefString(imageKey).orEmpty()
    val mode = getPrefString(scaleKey).orEmpty()
    val fileKey = path.takeUnless { it.isBlank() || it.startsWith("http", ignoreCase = true) }
        ?.let(::File)
        ?.takeIf { it.exists() }
        ?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
        ?: path
    return "$fileKey|$mode|${UiCorner.layoutAlpha()}"
}

@ColorInt
fun Context.themeCardColorOrDefault(): Int {
    return themeColorOrDefault(ThemePreferenceKeys.themeCardColor, R.color.background_card)
}

@ColorInt
fun Context.themeMutedColorOrDefault(): Int {
    return themeColorOrDefault(ThemePreferenceKeys.themeMutedColor, R.color.background_menu)
}

@ColorInt
fun Context.themeSearchFieldBackgroundColorOrDefault(): Int {
    return themeColorOrDefault(ThemePreferenceKeys.themeSearchFieldBackgroundColor, R.color.background_menu)
}

@ColorInt
fun Context.themeSearchFieldBackgroundColorOrNull(): Int? {
    return themeColorOrNull(ThemePreferenceKeys.themeSearchFieldBackgroundColor)
}

@ColorInt
fun Context.themeTabBackgroundColorOrDefault(): Int {
    return themeColorOrDefault(ThemePreferenceKeys.themeTabBackgroundColor, R.color.background_menu)
}

@ColorInt
fun Context.themeShelfColorOrDefault(): Int {
    return themeColorOrNull(ThemePreferenceKeys.themeShelfColor) ?: backgroundColor
}

@ColorInt
fun Context.themeDividerColorOrDefault(): Int {
    if (!hasCustomThemeSurfaceColors()) {
        return ContextCompat.getColor(this, R.color.bg_divider_line)
    }
    val surface = themeCardColorOrDefault()
    val edge = if (ColorUtils.isColorLight(surface)) Color.BLACK else Color.WHITE
    return ColorUtils.withAlpha(edge, if (ColorUtils.isColorLight(surface)) 0.10f else 0.16f)
}

fun Context.hasCustomThemeSurfaceColors(): Boolean {
    return themeColorOrNull(ThemePreferenceKeys.themeCardColor) != null ||
        themeColorOrNull(ThemePreferenceKeys.themeMutedColor) != null
}

@ColorInt
fun Context.themeColorOrDefault(key: String, @ColorRes defaultColor: Int): Int {
    return themeColorOrNull(key) ?: ContextCompat.getColor(this, defaultColor)
}

@ColorInt
fun Context.themeColorOrNull(key: String): Int? {
    return getPrefString(ThemeRuntimeKeys.activeColorKey(key)).toThemeColorIntOrNull()
}

@ColorInt
private fun String?.toThemeColorIntOrNull(): Int? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = raw.normalizedThemeColorString() ?: return null
    return runCatching {
        normalized.toColorInt()
    }.getOrNull()?.let {
        Color.rgb(Color.red(it), Color.green(it), Color.blue(it))
    }
}

private fun String.normalizedThemeColorString(): String? {
    val value = trim()
    val withoutPrefix = value
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    val isHex = withoutPrefix.isNotEmpty() && withoutPrefix.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (!isHex) {
        return value
    }
    val argb = when (withoutPrefix.length) {
        3 -> "FF" + withoutPrefix.map { "$it$it" }.joinToString("")
        4 -> withoutPrefix.map { "$it$it" }.joinToString("")
        6 -> "FF$withoutPrefix"
        8 -> withoutPrefix
        else -> return null
    }
    return "#${argb.uppercase()}"
}
