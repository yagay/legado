package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import io.legado.app.utils.dpToPx

/**
 * 增强界面对上游主题 API 的兼容层。
 *
 * 公共 MaterialValueHelper 保持上游原样；新增界面只通过这里取得上游
 * primaryTextColor/backgroundColor 等结果，不改变上游页面的主题行为。
 */
val Context.titleTextColor: Int
    get() = primaryTextColor

@ColorInt
fun String?.toThemeTextColorOrNull(): Int? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val withoutPrefix = raw
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    val candidate = if (
        withoutPrefix.length in setOf(6, 8) &&
        withoutPrefix.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    ) {
        "#$withoutPrefix"
    } else {
        raw
    }
    return runCatching { candidate.toColorInt() }.getOrNull()
}

@ColorInt
fun defaultThemeTextColor(isNightTheme: Boolean): Int {
    return if (isNightTheme) Color.WHITE else Color.BLACK
}

fun defaultThemeTextColorHex(isNightTheme: Boolean): String {
    return if (isNightTheme) "#FFFFFF" else "#000000"
}

val Context.dialogSurfaceBackground: GradientDrawable
    get() = filletBackground

fun Context.filletTopBackground(@ColorInt color: Int): GradientDrawable {
    val radius = 3f.dpToPx()
    return GradientDrawable().apply {
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        setColor(color)
    }
}
