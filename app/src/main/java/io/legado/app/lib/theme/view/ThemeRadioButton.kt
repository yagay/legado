package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.help.config.AppConfig

/**
 * @author Aidan Follestad (afollestad)
 */
class ThemeRadioButton(context: Context, attrs: AttributeSet?) : AppCompatRadioButton(context, attrs) {

    init {
        if (!isInEditMode) {
            applyOxygenTint()
        }
    }

    private fun applyOxygenTint() {
        val isDark = AppConfig.isNightTheme
        val normal = ContextCompat.getColor(
            context,
            if (isDark) R.color.ate_control_normal_dark else R.color.ate_control_normal_light
        )
        val disabled = ContextCompat.getColor(
            context,
            if (isDark) R.color.ate_control_disabled_dark else R.color.ate_control_disabled_light
        )
        buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            ),
            intArrayOf(disabled, normal, context.accentColor)
        )
    }
}
