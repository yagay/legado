package io.legado.app.ui.main.explore.modern

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.lib.theme.getToolbarTextColor
import io.legado.app.lib.theme.transparentNavBar

/**
 * 现代发现页与上游 TitleBar/Toolbar 之间的主题桥接。
 *
 * 上游 Toolbar 通过 ?attr/actionBarStyle 获取 ThemeOverlay.AppCompat.Light/Dark，
 * ActionMenu 的标题和图标前景色最终来自该 overlay 的 colorControlNormal。
 * 现代布局只读取同一个属性，不维护自己的黑白色判断。
 */
internal object ModernDiscoveryUpstreamBridge {

    @ColorInt
    fun toolbarForegroundColor(context: Context): Int {
        val actionBarStyle = context.obtainStyledAttributes(
            intArrayOf(androidx.appcompat.R.attr.actionBarStyle)
        ).use { it.getResourceId(0, 0) }
        val toolbarContext = if (actionBarStyle != 0) {
            ContextThemeWrapper(context, actionBarStyle)
        } else {
            context
        }
        return ThemeUtils.resolveColor(
            toolbarContext,
            androidx.appcompat.R.attr.colorControlNormal,
            context.getToolbarTextColor(context.transparentNavBar)
        )
    }
}
