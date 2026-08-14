package io.legado.app.lib.theme

import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx

/** 增强 Compose 对话框的日夜透明度配置。 */
object DialogDisplayConfig {

    var alpha: Int
        get() = appCtx.getPrefInt(
            ThemeRuntimeKeys.dialogAlpha(AppConfig.isNightTheme),
            100
        ).coerceIn(0, 100)
        set(value) = appCtx.putPrefInt(
            ThemeRuntimeKeys.dialogAlpha(AppConfig.isNightTheme),
            value.coerceIn(0, 100)
        )
}
