package io.legado.app.ui.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.titleTextColor
import io.legado.app.lib.theme.titleTypeface
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.main.bookshelf.compose.BookshelfListPalette
import io.legado.app.ui.main.bookshelf.compose.BookshelfListRenderConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * 发现页只把上游 View 主题转换成 Compose 需要的类型。
 * 不读取自定义卡片色、面板图、边框、透明度或全局圆角缩放。
 */
@Composable
internal fun rememberDiscoveryDefaultRenderConfig(): BookshelfListRenderConfig {
    val context = LocalContext.current
    val rowColor = ContextCompat.getColor(context, R.color.background_card)
    val accent = context.accentColor
    val rowPressedColor = ColorUtils.blendColors(rowColor, accent, 0.08f)
    val primaryText = Color(context.titleTextColor)
    val secondaryText = Color(context.secondaryTextColor)
    val accentColor = Color(accent)
    val titleFontFamily = FontFamily(context.titleTypeface())
    val bodyFontFamily = FontFamily(context.uiTypeface())
    val panelRadiusPx = 3.dpToPx().toFloat()
    val signature = remember(
        rowColor,
        rowPressedColor,
        primaryText,
        secondaryText,
        accentColor,
        titleFontFamily,
        bodyFontFamily,
        AppConfig.isNightTheme,
        AppConfig.isEInkMode
    ) {
        listOf(
            rowColor,
            rowPressedColor,
            primaryText.value,
            secondaryText.value,
            accentColor.value,
            AppConfig.isNightTheme,
            AppConfig.isEInkMode
        ).joinToString("|")
    }
    val palette = remember(
        rowColor,
        rowPressedColor,
        primaryText,
        secondaryText,
        accentColor,
        panelRadiusPx,
        titleFontFamily,
        bodyFontFamily,
        signature
    ) {
        BookshelfListPalette(
            primaryText = primaryText,
            secondaryText = secondaryText,
            accent = accentColor,
            rowColor = rowColor,
            rowPressedColor = rowPressedColor,
            borderColor = null,
            panelRadiusPx = panelRadiusPx,
            panelRadius = 3.dp,
            actionRadius = 3.dp,
            titleFontFamily = titleFontFamily,
            bodyFontFamily = bodyFontFamily,
            themeSignature = signature
        )
    }
    return remember(palette) {
        BookshelfListRenderConfig(
            palette = palette,
            panelImage = null
        )
    }
}
