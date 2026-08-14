package io.legado.app.lib.theme

import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 增强主题的字体路径与文字颜色配置。
 *
 * ThemeRuntimeKeys 负责日夜键映射，本类只负责读取和写入对应值。
 */
object ThemeTypographyConfig {

    var uiFontColor: String
        get() = appCtx.getPrefString(
            ThemeRuntimeKeys.uiFontColor(AppConfig.isNightTheme)
        ).orEmpty()
        set(value) = appCtx.putPrefString(
            ThemeRuntimeKeys.uiFontColor(AppConfig.isNightTheme),
            value
        )

    var titleFontColor: String
        get() = appCtx.getPrefString(
            ThemeRuntimeKeys.titleFontColor(AppConfig.isNightTheme)
        ).orEmpty()
        set(value) = appCtx.putPrefString(
            ThemeRuntimeKeys.titleFontColor(AppConfig.isNightTheme),
            value
        )

    var uiFontPath: String
        get() = appCtx.getPrefString(
            ThemeRuntimeKeys.uiFontPath(AppConfig.isNightTheme)
        ).orEmpty()
        set(value) = appCtx.putPrefString(
            ThemeRuntimeKeys.uiFontPath(AppConfig.isNightTheme),
            value
        )

    var titleFontPath: String
        get() = appCtx.getPrefString(
            ThemeRuntimeKeys.titleFontPath(AppConfig.isNightTheme)
        ).orEmpty()
        set(value) = appCtx.putPrefString(
            ThemeRuntimeKeys.titleFontPath(AppConfig.isNightTheme),
            value
        )
}
