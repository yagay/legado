package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * 增强封面组件的显示偏好。
 *
 * 使用原有键名，迁移后继续兼容已经保存的用户设置。
 */
object CoverDisplayConfig {

    private const val LOAD_HIGH_QUALITY_KEY = "loadCoverHighQuality"
    const val COVER_SHADOW_KEY = "bookCoverShadow"

    var loadHighQuality: Boolean
        get() = appCtx.getPrefBoolean(LOAD_HIGH_QUALITY_KEY, false)
        set(value) = appCtx.putPrefBoolean(LOAD_HIGH_QUALITY_KEY, value)

    var shadowEnabled: Boolean
        get() = appCtx.getPrefBoolean(COVER_SHADOW_KEY, true)
        set(value) = appCtx.putPrefBoolean(COVER_SHADOW_KEY, value)
}
