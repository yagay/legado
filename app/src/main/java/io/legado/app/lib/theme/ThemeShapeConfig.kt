package io.legado.app.lib.theme

import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 增强主题的圆角、布局透明度及跟随规则。
 */
object ThemeShapeConfig {

    var cornerScale: Float
        get() = appCtx.getPrefString(
            ThemeRuntimeKeys.uiCornerScale(AppConfig.isNightTheme),
            "1"
        )?.toFloatOrNull()?.coerceIn(0f, 3f) ?: 1f
        set(value) = appCtx.putPrefString(
            ThemeRuntimeKeys.uiCornerScale(AppConfig.isNightTheme),
            value.coerceIn(0f, 3f).toString()
        )

    var layoutAlpha: Int
        get() = appCtx.getPrefInt(
            ThemeRuntimeKeys.uiLayoutAlpha(AppConfig.isNightTheme),
            100
        ).coerceIn(0, 100)
        set(value) = appCtx.putPrefInt(
            ThemeRuntimeKeys.uiLayoutAlpha(AppConfig.isNightTheme),
            value.coerceIn(0, 100)
        )

    var searchFollowsCorner: Boolean
        get() = appCtx.getPrefBoolean(
            ThemeRuntimeKeys.uiCornerSearchFollow(AppConfig.isNightTheme),
            false
        )
        set(value) = appCtx.putPrefBoolean(
            ThemeRuntimeKeys.uiCornerSearchFollow(AppConfig.isNightTheme),
            value
        )

    var replyFollowsCorner: Boolean
        get() = appCtx.getPrefBoolean(
            ThemeRuntimeKeys.uiCornerReplyFollow(AppConfig.isNightTheme),
            false
        )
        set(value) = appCtx.putPrefBoolean(
            ThemeRuntimeKeys.uiCornerReplyFollow(AppConfig.isNightTheme),
            value
        )
}
