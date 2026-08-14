package io.legado.app.ui.main.explore.modern

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.explore.DiscoverySuiteWidget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetTarget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

/**
 * 套件布局的数据加载控制器。
 *
 * 只编排分类、分页和随机队列；实际书源解析与数据库写入继续委托给
 * ModernDiscoveryDataRepository 中的上游 WebBook/AppDatabase。
 */
internal class ModernDiscoverySuiteLoader(
    private val runtime: ModernDiscoverySuiteRuntime
) {
    private val targetLoadSemaphore = Semaphore(SUITE_TARGET_LOAD_PARALLELISM)

    suspend fun loadWidgetBooks(widget: DiscoverySuiteWidget): List<SearchBook> {
        if (widget.type == DiscoverySuiteWidgetType.HorizontalBooks.value) {
            return loadHorizontalWidgetBooks(widget)
        }
        val bookCount = when (widget.type) {
            DiscoverySuiteWidgetType.WaterfallBooks.value -> WATERFALL_SUITE_BOOK_COUNT
            else -> RANDOM_SUITE_BOOK_COUNT
        }
        val deck = runtime.randomDeck(widget)
        val books = deck.mutex.withLock {
            if (deck.queue.size < bookCount) {
                fillRandomDeckLocked(
                    widget = widget,
                    deck = deck,
                    minSize = maxOf(bookCount, RANDOM_SUITE_PREFETCH_COUNT)
                )
            }
            val result = ArrayList<SearchBook>(bookCount)
            while (result.size < bookCount && deck.queue.isNotEmpty()) {
                result.add(deck.queue.removeFirst())
            }
            if (result.size < bookCount) {
                fillRandomDeckLocked(widget, deck, bookCount)
                while (result.size < bookCount && deck.queue.isNotEmpty()) {
                    result.add(deck.queue.removeFirst())
                }
            }
            result
        }
        ModernDiscoveryDataRepository.persistSearchBooks(books)
        return books
    }

    suspend fun loadRankedListWidgetBooks(
        widget: DiscoverySuiteWidget
    ): Map<String, List<SearchBook>> {
        val result = linkedMapOf<String, List<SearchBook>>()
        val entries = coroutineScope {
            widget.validRandomTargets()
                .take(RANKED_SUITE_TARGET_LIMIT)
                .map { target ->
                    async {
                        val source = appDb.bookSourceDao.getBookSource(target.sourceUrl)
                        val books = if (source == null) {
                            emptyList()
                        } else {
                            loadTargetPage(source, target.tagUrl, 1)
                                .distinctBy { it.suiteDeckKey() }
                        }
                        target to books
                    }
                }
                .awaitAll()
        }
        entries.forEach { (target, books) ->
            val state = runtime.rankedPagingState(widget, target)
            state.nextPage = 2
            state.exhausted = books.isEmpty()
            result[target.deckKey()] = books
        }
        ModernDiscoveryDataRepository.persistSearchBooks(entries.flatMap { it.second })
        return result
    }

    suspend fun fillRandomDeckLocked(
        widget: DiscoverySuiteWidget,
        deck: SuiteRandomDeck,
        minSize: Int
    ) {
        val targets = widget.validRandomTargets()
        if (targets.isEmpty()) return
        var attempts = 0
        var resetSeen = false
        while (deck.queue.size < minSize && attempts < RANDOM_SUITE_MAX_PREFETCH_ATTEMPTS) {
            val requests = mutableListOf<SuiteDeckPageRequest>()
            while (
                requests.size < SUITE_RANDOM_BATCH_PARALLELISM &&
                attempts < RANDOM_SUITE_MAX_PREFETCH_ATTEMPTS
            ) {
                attempts++
                val target = targets[deck.targetIndex % targets.size]
                deck.targetIndex += 1
                val targetKey = target.deckKey()
                val page = deck.nextPageByTarget[targetKey] ?: 1
                deck.nextPageByTarget[targetKey] =
                    if (page >= RANDOM_SUITE_MAX_PAGE) 1 else page + 1
                val source = appDb.bookSourceDao.getBookSource(target.sourceUrl) ?: continue
                val seed = (widget.id + "|" + targetKey + "|" + page + "|" +
                    deck.seenKeys.size).hashCode()
                requests += SuiteDeckPageRequest(
                    tagUrl = target.tagUrl,
                    page = page,
                    source = source,
                    seed = seed
                )
            }
            if (requests.isEmpty()) continue
            val loadedPages = coroutineScope {
                requests.map { request ->
                    async {
                        request to loadTargetPage(
                            request.source,
                            request.tagUrl,
                            request.page
                        ).ifEmpty {
                            if (request.page == 1) {
                                emptyList()
                            } else {
                                loadTargetPage(request.source, request.tagUrl, 1)
                            }
                        }
                    }
                }.awaitAll()
            }
            var added = 0
            loadedPages.forEach { (request, pageBooks) ->
                pageBooks.shuffled(Random(request.seed)).forEach { book ->
                    if (deck.queue.size >= minSize) return@forEach
                    val key = book.suiteDeckKey()
                    if (deck.seenKeys.add(key)) {
                        deck.queue.addLast(book)
                        added += 1
                    }
                }
            }
            if (added == 0 && !resetSeen && deck.queue.isEmpty() && attempts >= targets.size) {
                deck.seenKeys.clear()
                resetSeen = true
            }
        }
    }

    suspend fun loadHorizontalWidgetPage(
        widget: DiscoverySuiteWidget,
        page: Int
    ): List<SearchBook> {
        val target = widget.validRandomTargets().firstOrNull() ?: return emptyList()
        val source = appDb.bookSourceDao.getBookSource(target.sourceUrl) ?: return emptyList()
        val books = loadTargetPage(source, target.tagUrl, page)
            .distinctBy { it.suiteDeckKey() }
            .take(HORIZONTAL_SUITE_PAGE_BOOK_LIMIT)
        ModernDiscoveryDataRepository.persistSearchBooks(books)
        return books
    }

    suspend fun loadRankedWidgetPage(
        target: DiscoverySuiteWidgetTarget,
        page: Int
    ): List<SearchBook> {
        val source = appDb.bookSourceDao.getBookSource(target.sourceUrl) ?: return emptyList()
        val books = loadTargetPage(source, target.tagUrl, page)
            .distinctBy { it.suiteDeckKey() }
        ModernDiscoveryDataRepository.persistSearchBooks(books)
        return books
    }

    private suspend fun loadHorizontalWidgetBooks(
        widget: DiscoverySuiteWidget
    ): List<SearchBook> {
        val state = runtime.horizontalPagingState(widget)
        state.nextPage = 2
        state.exhausted = false
        val books = loadHorizontalWidgetPage(widget, 1)
        state.exhausted = books.isEmpty()
        return books
    }

    private suspend fun loadTargetPage(
        source: BookSource,
        tagUrl: String,
        page: Int
    ): List<SearchBook> {
        return targetLoadSemaphore.withPermit {
            try {
                ModernDiscoveryDataRepository.loadExplorePage(source, tagUrl, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.put("套件发现加载分类失败", e)
                emptyList()
            }
        }
    }

    private companion object {
        const val SUITE_TARGET_LOAD_PARALLELISM = 4
        const val SUITE_RANDOM_BATCH_PARALLELISM = 3
        const val RANDOM_SUITE_BOOK_COUNT = 6
        const val RANDOM_SUITE_PREFETCH_COUNT = 18
        const val RANDOM_SUITE_MAX_PAGE = 5
        const val RANDOM_SUITE_MAX_PREFETCH_ATTEMPTS = 12
        const val HORIZONTAL_SUITE_PAGE_BOOK_LIMIT = 18
        const val RANKED_SUITE_TARGET_LIMIT = 9
        const val WATERFALL_SUITE_BOOK_COUNT = 24
    }
}
