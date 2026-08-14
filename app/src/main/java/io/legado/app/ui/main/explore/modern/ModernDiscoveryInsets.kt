package io.legado.app.ui.main.explore.modern

import android.graphics.Rect
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.ui.widget.MainTopBarView
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import splitties.views.bottomPadding

/**
 * 现代发现页覆盖式顶栏的状态栏处理。
 *
 * MainTopBarView 将 inset 应用到内部内容层，而不是给外层 View 增加 padding。
 */
internal fun MainTopBarView.applyModernStatusBarPadding() {
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val insetTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        onStatusBarInsetChanged(insetTop)
        windowInsets
    }
}

/**
 * 发现页内容需要避开主页面底栏；RecyclerView 使用末项装饰，避免改变所有条目的布局。
 */
internal fun View.applyMainBottomBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) bottomPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val bottomSpace = windowInsets.navigationBarHeight +
            resources.getDimensionPixelSize(R.dimen.main_content_bottom_bar_padding)
        if (this is RecyclerView) {
            bottomPadding = initialPadding
            updateMainBottomBarSpaceDecoration(bottomSpace)
        } else {
            bottomPadding = initialPadding + bottomSpace
        }
        windowInsets
    }
}

private fun RecyclerView.updateMainBottomBarSpaceDecoration(bottomSpace: Int) {
    (getTag(R.id.main_bottom_bar_space_decoration) as? MainBottomBarSpaceDecoration)?.let {
        if (it.bottomSpace != bottomSpace) {
            it.bottomSpace = bottomSpace
            invalidateItemDecorations()
        }
        return
    }
    val decoration = MainBottomBarSpaceDecoration(bottomSpace)
    addItemDecoration(decoration)
    setTag(R.id.main_bottom_bar_space_decoration, decoration)
}

private class MainBottomBarSpaceDecoration(var bottomSpace: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION && position == state.itemCount - 1) {
            outRect.bottom = bottomSpace
        }
    }
}
