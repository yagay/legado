package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint

/**
 * @author Aidan Follestad (afollestad)
 */
class ThemeSwitch(context: Context, attrs: AttributeSet) : SwitchCompat(context, attrs) {

    private var isUserAction = false

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
            applyOxygenOsTint()
        }
    }

    private fun applyOxygenOsTint() {
        val isDark = AppConfig.isNightTheme
        val accent = context.accentColor
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        )

        thumbTintList = ColorStateList(
            states,
            intArrayOf(
                ContextCompat.getColor(
                    context,
                    if (isDark) R.color.ate_switch_thumb_disabled_dark else R.color.ate_switch_thumb_disabled_light
                ),
                ContextCompat.getColor(
                    context,
                    if (isDark) R.color.ate_switch_thumb_normal_dark else R.color.ate_switch_thumb_normal_light
                ),
                Color.WHITE
            )
        )

        trackTintList = ColorStateList(
            states,
            intArrayOf(
                ContextCompat.getColor(
                    context,
                    if (isDark) R.color.ate_switch_track_disabled_dark else R.color.ate_switch_track_disabled_light
                ),
                ContextCompat.getColor(
                    context,
                    if (isDark) R.color.ate_switch_track_normal_dark else R.color.ate_switch_track_normal_light
                ),
                accent
            )
        )
    }

    override fun performClick(): Boolean {
        isUserAction = true
        val result = super.performClick()
        isUserAction = false
        return result
    }

    fun setOnUserCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        if (listener == null) {
            return super.setOnCheckedChangeListener(null)
        }
        super.setOnCheckedChangeListener { _, isChecked ->
            if (isUserAction) {
                listener.invoke(isChecked)
            }
        }
    }

}
