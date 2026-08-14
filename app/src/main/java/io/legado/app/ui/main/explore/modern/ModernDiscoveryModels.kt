package io.legado.app.ui.main.explore.modern

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.main.explore.DiscoveryCachePolicy
import io.legado.app.ui.main.explore.DiscoverySuite
import io.legado.app.ui.main.explore.DiscoverySuiteWidget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetTarget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * 现代发现页专用状态与纯转换逻辑。
 *
 * 从上游 ExploreFragment 中隔离，避免现代布局的数据模型继续扩大上游页面文件。
 */
internal enum class DiscoverClassificationMode {
    FLAT,
    SECTION,
    TREE
}

internal data class DiscoverMatrixChannel(
    val header: ExploreKind,
    val categories: MutableList<DiscoverMatrixCategory>
)

internal data class DiscoverMatrixCategory(
    val header: ExploreKind,
    val leaves: MutableList<ExploreKind>
)

internal data class SuiteRandomDeck(
    val signature: String,
    val mutex: Mutex = Mutex(),
    val queue: ArrayDeque<SearchBook> = ArrayDeque(),
    val seenKeys: LinkedHashSet<String> = linkedSetOf(),
    val nextPageByTarget: MutableMap<String, Int> = linkedMapOf(),
    var targetIndex: Int = 0,
    // Main 线程读、IO 线程写，加 @Volatile 保证可见性，避免重复预取。
    @Volatile var prefetching: Boolean = false
)

internal data class SuitePreparedBatch(
    val signature: String,
    val books: List<SearchBook>
)

internal data class SuiteDeckPageRequest(
    val tagUrl: String,
    val page: Int,
    val source: BookSource,
    val seed: Int
)

internal data class SuiteHorizontalPagingState(
    val signature: String,
    // nextPage/loading/exhausted 会被 Main(loadMore) 与 IO(首页加载) 访问，加 @Volatile 保证可见性。
    @Volatile var nextPage: Int = 2,
    @Volatile var loading: Boolean = false,
    @Volatile var exhausted: Boolean = false
)

internal data class SuiteRankedPagingState(
    val signature: String,
    // nextPage/loading/exhausted 会被 Main(loadMore) 与 IO(首页加载) 访问，加 @Volatile 保证可见性。
    @Volatile var nextPage: Int = 2,
    @Volatile var loading: Boolean = false,
    @Volatile var exhausted: Boolean = false
)

internal data class ModernDiscoverResultCache(
    val sourceUrl: String = "",
    val tagUrl: String = "",
    val books: List<SearchBook> = emptyList(),
    val nextPage: Int = 2,
    val hasMore: Boolean = true,
    val savedAt: Long = 0L
)

internal data class DiscoverySuitePageSnapshot(
    val suiteId: String,
    val signature: String,
    val widgetBooks: Map<String, List<SearchBook>>,
    val rankedWidgetBooks: Map<String, Map<String, List<SearchBook>>>,
    val widgetSignatures: Map<String, String>
)

internal fun DiscoverySuitePageSnapshot.compactForCache(): DiscoverySuitePageSnapshot {
    val incompleteWidgetIds = hashSetOf<String>()
    val compactWidgetBooks = linkedMapOf<String, List<SearchBook>>()
    widgetBooks.forEach { (widgetId, books) ->
        val compactBooks = books.mapNotNull(DiscoveryCachePolicy::compact)
        if (compactBooks.size != books.size) incompleteWidgetIds += widgetId
        if (compactBooks.isNotEmpty()) compactWidgetBooks[widgetId] = compactBooks
    }
    val compactRankedWidgetBooks = linkedMapOf<String, Map<String, List<SearchBook>>>()
    rankedWidgetBooks.forEach { (widgetId, rankedBooks) ->
        val compactRankedBooks = linkedMapOf<String, List<SearchBook>>()
        rankedBooks.forEach { (rank, books) ->
            val compactBooks = books.mapNotNull(DiscoveryCachePolicy::compact)
            if (compactBooks.size != books.size) incompleteWidgetIds += widgetId
            if (compactBooks.isNotEmpty()) compactRankedBooks[rank] = compactBooks
        }
        if (compactRankedBooks.isNotEmpty()) {
            compactRankedWidgetBooks[widgetId] = compactRankedBooks
        }
    }
    return copy(
        widgetBooks = compactWidgetBooks,
        rankedWidgetBooks = compactRankedWidgetBooks,
        widgetSignatures = widgetSignatures.filterKeys { it !in incompleteWidgetIds }
    )
}

internal fun DiscoverySuitePageSnapshot.hasBooks(): Boolean {
    return widgetBooks.isNotEmpty() || rankedWidgetBooks.isNotEmpty()
}

internal object DiscoverySuitePageSnapshotStore {
    private const val MAX_SNAPSHOTS = 2
    private val snapshots = object : LinkedHashMap<String, DiscoverySuitePageSnapshot>(
        MAX_SNAPSHOTS,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, DiscoverySuitePageSnapshot>
        ): Boolean {
            return size > MAX_SNAPSHOTS
        }
    }

    @Synchronized
    fun get(suiteId: String, signature: String): DiscoverySuitePageSnapshot? {
        return snapshots[suiteId]?.takeIf { it.signature == signature }
    }

    @Synchronized
    fun put(snapshot: DiscoverySuitePageSnapshot) {
        if (snapshot.suiteId.isBlank()) return
        snapshots[snapshot.suiteId] = snapshot
    }

}

internal object DiscoveryCacheWriteScope {
    private val scope = CoroutineScope(SupervisorJob() + IO)
    private var suiteSaveJob: Job? = null
    private var suiteSaveVersion = 0L

    @Synchronized
    fun launchLatest(block: suspend CoroutineScope.(Long) -> Unit) {
        val saveVersion = ++suiteSaveVersion
        suiteSaveJob?.cancel()
        suiteSaveJob = scope.launch { block(saveVersion) }
    }

    @Synchronized
    fun isLatest(saveVersion: Long): Boolean = suiteSaveVersion == saveVersion
}

internal const val DISCOVERY_SUITE_SNAPSHOT_RANDOM_LIMIT = 36
internal const val DISCOVERY_SUITE_SNAPSHOT_HORIZONTAL_LIMIT = 72
internal const val DISCOVERY_SUITE_SNAPSHOT_WATERFALL_LIMIT = 24
internal const val DISCOVERY_SUITE_SNAPSHOT_RANKED_TOTAL_LIMIT = 72
internal const val RANKED_SUITE_SNAPSHOT_BOOK_LIMIT = 24
internal const val DISCOVERY_SUITE_SNAPSHOT_WIDGET_LIMIT = 20
internal const val DISCOVERY_MODERN_CACHE_BOOK_LIMIT = 80
internal const val DISCOVERY_CACHE_TTL_MS = 72L * 60L * 60L * 1000L
internal const val DISCOVERY_CLASSIC_FLOW_COALESCE_DELAY_MS = 120L
internal const val DISCOVERY_MODERN_CACHE_PREFIX = "discovery_modern_result_"
internal const val DISCOVERY_SUITE_CACHE_PREFIX = "discovery_suite_snapshot_"

internal fun String.limitDiscoverText(max: Int): String {
    return if (length <= max) this else "${take(max.coerceAtLeast(2) - 1)}..."
}

internal fun DiscoverySuiteWidget.validRandomTargets(): List<DiscoverySuiteWidgetTarget> {
    return targets.filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }
}

internal fun DiscoverySuite.cacheSignature(): String {
    return widgets.joinToString(separator = "\u001D") { widget ->
        "${widget.order}\u001C${widget.cacheSignature()}"
    }
}

internal fun DiscoverySuiteWidget.cacheSignature(): String {
    return listOf(
        id,
        type,
        displayLimit.toString(),
        validRandomTargets().joinToString(separator = "\u001C") { it.deckKey() }
    ).joinToString(separator = "\u001D")
}

internal fun DiscoverySuiteWidget.snapshotBookLimit(): Int {
    return when (type) {
        DiscoverySuiteWidgetType.HorizontalBooks.value -> DISCOVERY_SUITE_SNAPSHOT_HORIZONTAL_LIMIT
        DiscoverySuiteWidgetType.WaterfallBooks.value -> DISCOVERY_SUITE_SNAPSHOT_WATERFALL_LIMIT
        DiscoverySuiteWidgetType.RankedList.value -> DISCOVERY_SUITE_SNAPSHOT_RANKED_TOTAL_LIMIT
        else -> DISCOVERY_SUITE_SNAPSHOT_RANDOM_LIMIT
    }
}

internal fun DiscoverySuiteWidget.deckSignature(): String {
    return validRandomTargets().joinToString("|") { it.deckKey() } + "|$displayLimit|$type"
}

internal fun DiscoverySuiteWidget.horizontalPagingSignature(): String {
    return validRandomTargets().firstOrNull()?.deckKey().orEmpty() + "|$type"
}

internal fun DiscoverySuiteWidget.rankedPagingKey(target: DiscoverySuiteWidgetTarget): String {
    return "$id\n${target.deckKey()}"
}

internal fun DiscoverySuiteWidget.rankedPagingSignature(target: DiscoverySuiteWidgetTarget): String {
    return "${target.deckKey()}|$type|$displayLimit"
}

internal fun DiscoverySuiteWidgetTarget.deckKey(): String {
    return "$sourceUrl\n$tagUrl"
}

internal fun DiscoverySuiteWidget.isSuiteButtonOnlyWidget(): Boolean {
    return type == DiscoverySuiteWidgetType.TagBar.value ||
        type == DiscoverySuiteWidgetType.RankButtons.value
}

internal fun SearchBook.suiteDeckKey(): String {
    return when {
        bookUrl.isNotBlank() -> "$origin|$bookUrl"
        author.isNotBlank() -> "$origin|$name|$author"
        else -> "$origin|$name"
    }
}
