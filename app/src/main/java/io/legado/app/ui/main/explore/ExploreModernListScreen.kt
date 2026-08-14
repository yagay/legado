package io.legado.app.ui.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.compose.BookListCardSurface
import io.legado.app.ui.main.bookshelf.compose.BookshelfListItemStyle
import io.legado.app.ui.main.bookshelf.compose.BookshelfListPalette
import io.legado.app.ui.main.bookshelf.compose.BookshelfListRenderConfig
import io.legado.app.ui.widget.compose.BookCoverImage
import io.legado.app.ui.widget.compose.ComposeLazyListFastScroller
import io.legado.app.ui.widget.compose.SearchBookListItem
import io.legado.app.ui.widget.compose.SearchBookPreviewOverlay
import io.legado.app.ui.widget.compose.SearchBookPreviewState
import io.legado.app.ui.widget.MainTopBarView
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.ColorUtils
import io.legado.app.ui.main.explore.modern.ModernDiscoveryText
import kotlin.math.roundToInt

@Composable
fun ExploreModernListScreen(
    books: List<SearchBook>,
    layoutMode: Int,
    listItemStyle: Int,
    topPaddingPx: Int,
    scrollToTopSignal: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    filterRows: List<MainTopBarView.DiscoveryFilterRow>,
    onFilterOptionClick: (Int, Int) -> Unit,
    isInBookshelf: (SearchBook) -> Boolean,
    onBookClick: (SearchBook) -> Unit,
    onLoadMore: () -> Unit,
    onCanScrollBackwardChanged: (Boolean) -> Unit,
    onFilterHeaderHiddenChanged: (Boolean) -> Unit,
    fragment: Fragment,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier
) {
    if (layoutMode == 3) {
        ExploreModernGridScreen(
            books = books,
            topPaddingPx = topPaddingPx,
            scrollToTopSignal = scrollToTopSignal,
            isLoading = isLoading,
            hasMore = hasMore,
            filterRows = filterRows,
            onFilterOptionClick = onFilterOptionClick,
            isInBookshelf = isInBookshelf,
            onBookClick = onBookClick,
            onLoadMore = onLoadMore,
            onCanScrollBackwardChanged = onCanScrollBackwardChanged,
            onFilterHeaderHiddenChanged = onFilterHeaderHiddenChanged,
            fragment = fragment,
            lifecycle = lifecycle,
            modifier = modifier
        )
        return
    }
    val listState = rememberLazyListState()
    val topPadding = with(LocalDensity.current) { topPaddingPx.toDp() }
    val shouldLoadMore by remember(books, hasMore, isLoading) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            books.isNotEmpty() && hasMore && !isLoading && lastVisible >= books.lastIndex - 3
        }
    }
    val canScrollBackward by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0
        }
    }
    val filterHeaderHidden by remember(filterRows) {
        derivedStateOf {
            if (filterRows.isEmpty()) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val header = layoutInfo.visibleItemsInfo.firstOrNull {
                    it.key == "discover_filter_header"
                }
                val hasScrolled = listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0
                hasScrolled && (
                    header == null ||
                        header.offset + header.size <= layoutInfo.viewportStartOffset
                    )
            }
        }
    }
    val renderConfig = rememberDiscoveryDefaultRenderConfig()
    var previewState by remember { mutableStateOf<SearchBookPreviewState?>(null) }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    LaunchedEffect(canScrollBackward) {
        onCanScrollBackwardChanged(canScrollBackward)
    }
    LaunchedEffect(filterHeaderHidden) {
        onFilterHeaderHiddenChanged(filterHeaderHidden)
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            if (AppConfig.isEInkMode) {
                listState.scrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .clipToBounds()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 86.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (listItemStyle == BookshelfListItemStyle.RoundedCard) 4.dp else 2.dp)
        ) {
            if (filterRows.isNotEmpty()) {
                item(key = "discover_filter_header", contentType = "filter_header") {
                    DiscoverFilterHeader(
                        rows = filterRows,
                        onOptionClick = onFilterOptionClick,
                        renderConfig = renderConfig,
                        modifier = Modifier.padding(
                            horizontal = (
                                dimensionResource(R.dimen.bookshelf_tag_bar_margin_horizontal) - 8.dp
                            ).coerceAtLeast(0.dp)
                        )
                    )
                }
            }
            lazyColumnItems(
                items = books,
                key = { book -> "${book.origin}|${book.bookUrl}" },
                contentType = { "discover_book_$listItemStyle" }
            ) { book ->
                ExploreBookListItem(
                    book = book,
                    inBookshelf = isInBookshelf(book),
                    listItemStyle = listItemStyle,
                    renderConfig = renderConfig,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onBookClick,
                    onPreview = { bounds ->
                        previewState = SearchBookPreviewState(book, bounds)
                    }
                )
            }
            if (isLoading && books.isNotEmpty()) {
                item(key = "discover_loading_footer", contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = renderConfig.palette.accent
                        )
                    }
                }
            }
        }
        ComposeLazyListFastScroller(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        SearchBookPreviewOverlay(
            state = previewState,
            renderConfig = renderConfig,
            fragment = fragment,
            lifecycle = lifecycle,
            onDismissed = { previewState = null },
            onOpen = { book ->
                previewState = null
                onBookClick(book)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExploreModernGridScreen(
    books: List<SearchBook>,
    topPaddingPx: Int,
    scrollToTopSignal: Int,
    isLoading: Boolean,
    hasMore: Boolean,
    filterRows: List<MainTopBarView.DiscoveryFilterRow>,
    onFilterOptionClick: (Int, Int) -> Unit,
    isInBookshelf: (SearchBook) -> Boolean,
    onBookClick: (SearchBook) -> Unit,
    onLoadMore: () -> Unit,
    onCanScrollBackwardChanged: (Boolean) -> Unit,
    onFilterHeaderHiddenChanged: (Boolean) -> Unit,
    fragment: Fragment,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val topPadding = with(LocalDensity.current) { topPaddingPx.toDp() }
    val shouldLoadMore by remember(books, hasMore, isLoading) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            books.isNotEmpty() && hasMore && !isLoading && lastVisible >= books.lastIndex - 6
        }
    }
    val canScrollBackward by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                    gridState.firstVisibleItemScrollOffset > 0
        }
    }
    val filterHeaderHidden by remember(filterRows) {
        derivedStateOf {
            if (filterRows.isEmpty()) {
                false
            } else {
                val layoutInfo = gridState.layoutInfo
                val header = layoutInfo.visibleItemsInfo.firstOrNull {
                    it.key == "discover_filter_header"
                }
                val hasScrolled = gridState.firstVisibleItemIndex > 0 ||
                    gridState.firstVisibleItemScrollOffset > 0
                hasScrolled && (
                    header == null ||
                        header.offset.y + header.size.height <= layoutInfo.viewportStartOffset
                    )
            }
        }
    }
    val renderConfig = rememberDiscoveryDefaultRenderConfig()
    var previewState by remember { mutableStateOf<SearchBookPreviewState?>(null) }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    LaunchedEffect(canScrollBackward) {
        onCanScrollBackwardChanged(canScrollBackward)
    }
    LaunchedEffect(filterHeaderHidden) {
        onFilterHeaderHiddenChanged(filterHeaderHidden)
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            if (AppConfig.isEInkMode) {
                gridState.scrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .clipToBounds()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 86.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filterRows.isNotEmpty()) {
                item(
                    key = "discover_filter_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "filter_header"
                ) {
                    DiscoverFilterHeader(
                        rows = filterRows,
                        onOptionClick = onFilterOptionClick,
                        renderConfig = renderConfig,
                        modifier = Modifier.padding(
                            horizontal = (
                                dimensionResource(R.dimen.bookshelf_tag_bar_margin_horizontal) - 8.dp
                            ).coerceAtLeast(0.dp)
                        )
                    )
                }
            }
            items(
                items = books,
                key = { book -> "${book.origin}|${book.bookUrl}" },
                contentType = { "discover_grid_book" }
            ) { book ->
                ExploreGridBookItem(
                    book = book,
                    inBookshelf = isInBookshelf(book),
                    renderConfig = renderConfig,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onBookClick,
                    onPreview = { bounds ->
                        previewState = SearchBookPreviewState(book, bounds)
                    }
                )
            }
            if (isLoading && books.isNotEmpty()) {
                item(key = "discover_grid_loading_footer", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = renderConfig.palette.accent
                        )
                    }
                }
            }
        }
        SearchBookPreviewOverlay(
            state = previewState,
            renderConfig = renderConfig,
            fragment = fragment,
            lifecycle = lifecycle,
            onDismissed = { previewState = null },
            onOpen = { book ->
                previewState = null
                onBookClick(book)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreGridBookItem(
    book: SearchBook,
    inBookshelf: Boolean,
    renderConfig: BookshelfListRenderConfig,
    fragment: Fragment,
    lifecycle: Lifecycle,
    onClick: (SearchBook) -> Unit,
    onPreview: (Rect?) -> Unit
) {
    val palette = renderConfig.palette
    var coverBounds by remember(book.bookUrl, book.origin, book.coverUrl) { mutableStateOf<Rect?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(book) },
                onLongClick = { onPreview(coverBounds) }
        )
    ) {
        Box {
            BookCoverImage(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
                style = CoverImageView.CoverStyle.GRID,
                loadOnlyWifi = AppConfig.loadCoverOnlyWifi,
                fragment = fragment,
                lifecycle = lifecycle,
                preferThumb = true,
                fillBounds = true,
                onBoundsChanged = { coverBounds = it }
            )
            if (inBookshelf) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .size(10.dp)
                )
            }
        }
        Text(
            text = book.name,
            color = palette.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = palette.titleFontFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ExploreBookListItem(
    book: SearchBook,
    inBookshelf: Boolean,
    listItemStyle: Int,
    renderConfig: BookshelfListRenderConfig,
    fragment: Fragment,
    lifecycle: Lifecycle,
    onClick: (SearchBook) -> Unit,
    onPreview: (Rect?) -> Unit
) {
    val rounded = listItemStyle == BookshelfListItemStyle.RoundedCard
    SearchBookListItem(
        book = book,
        inBookshelf = inBookshelf,
        rounded = rounded,
        renderConfig = renderConfig,
        fragment = fragment,
        lifecycle = lifecycle,
        onClick = { onClick(book) },
        onPreview = onPreview
    )
}

/**
 * RecyclerView 原始二级列表复用的分类头。书籍项继续由 ExploreShowAdapter 负责，
 * 此处只提供会随列表滚动的现代分类区域。
 */
@Composable
internal fun DiscoverFilterHeaderForRecycler(
    rows: List<MainTopBarView.DiscoveryFilterRow>,
    onOptionClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    DiscoverFilterHeader(
        rows = rows,
        onOptionClick = onOptionClick,
        renderConfig = rememberDiscoveryDefaultRenderConfig(),
        modifier = modifier
    )
}

/**
 * 现代发现页分类筛选列表头:随列表滚动,向下滑动时自然向上滚出屏幕,回到顶部时恢复。
 */
@Composable
private fun DiscoverFilterHeader(
    rows: List<MainTopBarView.DiscoveryFilterRow>,
    onOptionClick: (Int, Int) -> Unit,
    renderConfig: BookshelfListRenderConfig,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            DiscoverFilterRowView(
                rowIndex = rowIndex,
                row = row,
                onOptionClick = onOptionClick,
                renderConfig = renderConfig
            )
        }
    }
}

@Composable
private fun DiscoverFilterRowView(
    rowIndex: Int,
    row: MainTopBarView.DiscoveryFilterRow,
    onOptionClick: (Int, Int) -> Unit,
    renderConfig: BookshelfListRenderConfig
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        val palette = renderConfig.palette
        var expanded by remember(row.title, row.options) { mutableStateOf(false) }
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // 整行内容宽度:行宽扣除标题列(62dp)。先按完整宽度测量第一排能放几个,
        // 全部放得下则不显示展开标志;放不下再扣除标志宽度(34dp)重新测量,
        // 保证默认第一排数量始终按实际宽度动态适配,不固定也不多扣空间。
        val contentWidthPx = with(density) { (maxWidth - 62.dp).toPx() }.roundToInt()
        val markerWidthPx = with(density) { 34.dp.toPx() }.roundToInt()
        // 动态计算第一排可容纳的选项数与展示顺序:按实际可用宽度逐项累加测量,能放下几个就显示几个,
        // 不再固定每排最多4个;放不下的选项默认折叠,由展开标志(﹀)展开。
        val chipStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        // 测量时任何选项(含第一个)超宽都不计入第一排,避免 FlowRow 换行后被 maxLines=1 裁剪成空白行;
        // 选中项在折叠区时尝试前置,若前置到第一位放不下则放弃前置,保证第一排始终有正常内容。
        val layout = remember(
            row.options,
            row.selectedIndex,
            contentWidthPx,
            markerWidthPx,
            textMeasurer,
            chipStyle,
            density
        ) {
            fun countFor(indices: List<Int>, widthPx: Int): Int {
                val chipPaddingPx = with(density) { 4.dp.toPx() }.roundToInt()
                val spacingPx = with(density) { 6.dp.toPx() }.roundToInt()
                var usedWidth = 0
                var count = 0
                for (i in indices) {
                    // 测量与显示都使用去掉首尾符号后的文本,保证容量计算与实际渲染宽度一致。
                    val textWidth = textMeasurer.measure(
                        AnnotatedString(ModernDiscoveryText.stripWrapSymbols(row.options[i])),
                        chipStyle
                    ).size.width
                    // 间距只加在相邻选项之间(第一个不加),与 FlowRow 的 spacedBy 布局完全一致,
                    // 避免因多算尾部间距导致实际放得下的选项被错误折叠。
                    val chipWidth = textWidth + chipPaddingPx * 2 + (if (count > 0) spacingPx else 0)
                    // 无条件检查宽度(含第一个):超宽选项不进第一排,由展开标志折叠显示。
                    if (usedWidth + chipWidth > widthPx) break
                    usedWidth += chipWidth
                    count++
                }
                return count.coerceAtMost(row.options.size)
            }
            val naturalIndices = row.options.indices.toList()
            // 先按完整宽度测量:全部放得下则无需展开标志;放不下再扣除标志宽度重新计算第一排数量。
            val naturalCount = countFor(naturalIndices, contentWidthPx)
            val needMarker = naturalCount < row.options.size
            val actualWidthPx = contentWidthPx - if (needMarker) markerWidthPx else 0
            val selected = row.selectedIndex
            if (selected in row.options.indices && selected >= naturalCount) {
                val candidate = listOf(selected) + row.options.indices.filter { it != selected }
                val candidateCount = countFor(candidate, actualWidthPx)
                // 前置项超宽放不下时放弃前置,避免第一排被超宽项挤空。
                if (candidateCount > 0) candidate to candidateCount
                else naturalIndices to countFor(naturalIndices, actualWidthPx)
            } else {
                naturalIndices to countFor(naturalIndices, actualWidthPx)
            }
        }
        val ordered = layout.first
        val firstLineCount = layout.second
        val expandable = ordered.size > firstLineCount
        // 重组时旧组合片段可能携带过期 optionIndex,而 row.options 已换成新列表,
        // 必须按当前列表范围过滤,避免 DiscoverFilterOptionChip 越界崩溃。
        val validRange = row.options.indices
        val firstLine = ordered.take(firstLineCount).filter { it in validRange }
        val restLine = ordered.drop(firstLineCount).filter { it in validRange }
        // 点击选项后自动收起展开状态。
        val optionClick: (Int, Int) -> Unit = { rowIdx, optionIdx ->
            expanded = false
            onOptionClick(rowIdx, optionIdx)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.title,
                        color = palette.primaryText,
                        fontSize = 15.sp,
                        fontFamily = palette.titleFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(62.dp)
                    )
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        // 收起时限制单行:即使测量与实际布局有微小偏差,第一排也不会溢出换行,
                        // 放不下的选项一律折叠,由展开标志(﹀)展开;展开时显示全部,避免选项丢失。
                        maxLines = if (expanded) Int.MAX_VALUE else 1
                    ) {
                        firstLine.forEach { optionIndex ->
                            DiscoverFilterOptionChip(rowIndex, row, optionIndex, optionClick, palette)
                        }
                    }
                    // 展开标志固定在第一排最右侧,不随选项换行。
                    if (expandable) {
                        Text(
                            text = if (expanded) "︿" else "﹀",
                            color = palette.accent,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // 展开后的其余选项单独成排。
                if (expanded && expandable) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        restLine.forEach { optionIndex ->
                            DiscoverFilterOptionChip(rowIndex, row, optionIndex, optionClick, palette)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverFilterOptionChip(
    rowIndex: Int,
    row: MainTopBarView.DiscoveryFilterRow,
    optionIndex: Int,
    onOptionClick: (Int, Int) -> Unit,
    palette: BookshelfListPalette
) {
    // 防御:重组时可能以旧 optionIndex 携带新 row 重放,越界时直接跳过该选项。
    val optionText = row.options.getOrNull(optionIndex) ?: return
    val selected = optionIndex == row.selectedIndex
    val textColor = if (selected) {
        if (ColorUtils.isColorLight(palette.accent.toArgb())) Color.Black else Color.White
    } else {
        palette.primaryText
    }
    Text(
        // 选中项复用书籍 LabelsBar/AccentBgTextView 的视觉规则：
        // 主题强调色实心背景、按背景亮度自动选择黑白文字、2dp 圆角与 3dp 横向内边距。
        text = ModernDiscoveryText.stripWrapSymbols(optionText),
        color = textColor,
        fontSize = 14.sp,
        fontFamily = palette.bodyFontFamily,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) palette.accent else Color.Transparent)
            .clickable { onOptionClick(rowIndex, optionIndex) }
            .padding(horizontal = 3.dp, vertical = 0.dp)
    )
}
