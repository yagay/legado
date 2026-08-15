package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

/**
 * Loads an exact whole-book review count only when the source exposes enough information to do so
 * reliably. Returning null means "unknown", which is deliberately different from a confirmed 0.
 */
internal object BookReviewCountLoader {

    suspend fun loadExactCount(
        source: BookSource,
        book: Book,
        rule: ReviewRule,
        coroutineContext: CoroutineContext,
    ): Int? {
        extractEmbeddedCount(book)?.let { return it }
        if (LegacyBookReviewResolver.isFanqieAggregateCommentProtocol(source)) {
            loadFanqieAggregateCount(source, book, coroutineContext)?.let { return it }
        }
        return loadPagedRuleCount(source, book, rule, coroutineContext)
    }

    private fun extractEmbeddedCount(book: Book): Int? {
        val intro = book.intro.orEmpty()
        val patterns = listOf(
            Regex("(?:评论|书评)\\s*[：:]\\s*(\\d+)"),
            Regex("(?:评论|书评)\\s*(?:数|数量)?\\s*[：:]?\\s*(\\d+)"),
            Regex("(\\d+)\\s*条(?:评论|书评)"),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(intro)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private suspend fun loadFanqieAggregateCount(
        source: BookSource,
        book: Book,
        coroutineContext: CoroutineContext,
    ): Int? {
        val bookId = LegacyBookReviewResolver.fanqieAggregateBookId(book) ?: return null
        val sourceBase = source.bookSourceUrl
            .substringBefore('#')
            .trimEnd('/')
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null

        var offset = 0
        var total = 0
        var pageCount = 0
        while (pageCount < MAX_COUNT_PAGES) {
            val url = "$sourceBase/api/comment?book_id=$bookId&count=$PAGE_SIZE&offset=$offset"
            val body = AnalyzeUrl(
                url,
                baseUrl = book.bookUrl,
                source = source,
                ruleData = book,
                coroutineContext = coroutineContext,
            ).getStrResponseAwait(useWebView = false).body ?: return null

            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val payload = root.optJSONObject("data")?.let { data ->
                data.optJSONObject("data") ?: data
            } ?: root
            val comments = payload.optJSONArray("comment")
                ?: payload.optJSONArray("comments")
            val count = comments?.length() ?: 0

            // Prefer a server-provided total when present; it is both cheaper and more accurate.
            for (key in arrayOf("total", "total_count", "comment_count", "count")) {
                if (payload.has(key)) {
                    val serverTotal = payload.optInt(key, -1)
                    if (serverTotal >= 0) return serverTotal
                }
            }

            total += count
            pageCount++
            val hasMore = payload.optBoolean("has_more", false)
            if (!hasMore || count == 0) return total
            offset += PAGE_SIZE
        }

        // Do not report a capped value as the exact total.
        return null
    }

    /**
     * Generic fallback for adapters that expose a real next-page chain. It deliberately does not
     * claim that a one-page preview (QQ/Yousuu style) is the total.
     */
    private suspend fun loadPagedRuleCount(
        source: BookSource,
        book: Book,
        rule: ReviewRule,
        coroutineContext: CoroutineContext,
    ): Int? {
        val hasPagingRule = !rule.reviewDetailNextPageUrl.isNullOrBlank()
        if (!hasPagingRule) return null

        val chapter = BookChapter(
            url = book.bookUrl,
            baseUrl = book.bookUrl,
            bookUrl = book.bookUrl,
            index = 0,
        )
        var page = 1
        var nextPageUrl: String? = null
        var total = 0
        val ids = hashSetOf<String>()

        while (page <= MAX_COUNT_PAGES) {
            val result = ReviewLoader.loadDetail(
                ReviewLoader.DetailRequest(
                    source = source,
                    book = book,
                    chapter = chapter,
                    paragraphIndex = -1,
                    paragraphData = "",
                    page = page,
                    ruleHash = rule.hashCode(),
                    nextPageUrl = nextPageUrl,
                    ruleOverride = rule,
                ),
                coroutineContext = coroutineContext,
            ) ?: return null

            result.items.forEach { item ->
                val id = item.id?.takeIf { it.isNotBlank() }
                if (id == null || ids.add(id)) total++
            }
            nextPageUrl = result.nextPageUrl?.takeIf { it.isNotBlank() }
            if (nextPageUrl == null) return total
            page++
        }
        return null
    }

    private const val PAGE_SIZE = 50
    private const val MAX_COUNT_PAGES = 200
}
