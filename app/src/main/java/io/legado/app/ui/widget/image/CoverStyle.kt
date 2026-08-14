package io.legado.app.ui.widget.image

/**
 * 增强布局使用的封面展示参数。
 *
 * 与上游 CoverImageView 分离，使图片加载控件可以保持上游实现。
 */
enum class CoverStyle(
    val radiusDp: Float,
    val elevationDp: Float,
    val strokeWidthDp: Float = 0f,
    val strokeAlpha: Float = 0f
) {
    FLAT(8f, 0f),
    COMPACT(7f, 1f),
    LIST(8f, 1.5f),
    GRID(8f, 2f),
    DETAIL(12f, 5f),
    PREVIEW(10f, 6f)
}

fun CoverImageView.setCoverStyle(style: CoverStyle) {
    elevation = style.elevationDp * resources.displayMetrics.density
    invalidateOutline()
}
