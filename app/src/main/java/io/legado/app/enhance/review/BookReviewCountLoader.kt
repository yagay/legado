package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
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
        coroutineContext: CoroutineContext,
    ): Int? {
        extractJjwxcEmbeddedCount(source, book)?.let { return it }
        if (LegacyBookReviewResolver.isFanqieAggregateCommentProtocol(source)) {
            return loadFanqieAggregateCount(source, book, coroutineContext)
        }
        return null
    }

    private fun extractJjwxcEmbeddedCount(source: BookSource, book: Book): Int? {
        if (!LegacyBookReviewResolver.isJjwxcBookCommentProtocol(source)) return null
        val intro = book.intro.orEmpty()
        return Regex("(?:评论|书评)\\s*[：:]\\s*(\\d+)")
            .find(intro)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
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

            val payload = runCatching {
                JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONObject("data")
            }.getOrNull() ?: return null
            val comments = payload.optJSONArray("comment")
            val count = comments?.length() ?: 0
            total += count
            pageCount++

            val hasMore = payload.optBoolean("has_more", false)
            if (!hasMore || count == 0) return total
            offset += PAGE_SIZE
        }

        // Do not report a capped value as the exact total.
        return null
    }

    private const val PAGE_SIZE = 50
    private const val MAX_COUNT_PAGES = 200
}
