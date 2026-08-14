package io.legado.app.help.config

import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx

/**
 * Display preferences used by the optional Compose management surfaces.
 *
 * Keeping these keys outside [AppConfig] avoids modifying the upstream
 * configuration object for UI that is implemented separately in this branch.
 */
object ManagementDisplayConfig {

    const val DEFAULT_FAST_SCROLLER_TOUCH_TARGET_DP = 44
    const val MIN_FAST_SCROLLER_TOUCH_TARGET_DP = 32
    const val MAX_FAST_SCROLLER_TOUCH_TARGET_DP = 60

    private const val FAST_SCROLLER_TOUCH_TARGET_KEY = "fastScrollerTouchTargetDp"
    private const val IMMERSIVE_MANAGE_BAR_KEY = "immersiveManageBar"

    val immersiveManageBar: Boolean
        get() = appCtx.getPrefBoolean(IMMERSIVE_MANAGE_BAR_KEY, true)

    var fastScrollerTouchTargetDp: Int
        get() = appCtx.getPrefInt(
            FAST_SCROLLER_TOUCH_TARGET_KEY,
            DEFAULT_FAST_SCROLLER_TOUCH_TARGET_DP
        ).coerceIn(
            MIN_FAST_SCROLLER_TOUCH_TARGET_DP,
            MAX_FAST_SCROLLER_TOUCH_TARGET_DP
        )
        set(value) {
            appCtx.putPrefInt(
                FAST_SCROLLER_TOUCH_TARGET_KEY,
                value.coerceIn(
                    MIN_FAST_SCROLLER_TOUCH_TARGET_DP,
                    MAX_FAST_SCROLLER_TOUCH_TARGET_DP
                )
            )
        }
}
