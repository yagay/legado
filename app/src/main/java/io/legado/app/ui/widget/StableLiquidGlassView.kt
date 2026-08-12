package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Compatibility fallback when the optional liquid-glass library is unavailable. */
class StableLiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    fun setCornerRadius(value: Float) = Unit
    fun setBlurRadius(value: Float) = Unit
    fun setTintAlpha(value: Float) = Unit
    fun setDispersion(value: Float) = Unit
    fun setRefractionHeight(value: Float) = Unit
    fun setRefractionOffset(value: Float) = Unit
    fun bind(source: ViewGroup?) = Unit
}
