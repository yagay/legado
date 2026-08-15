package io.legado.app.enhance.review

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.ReviewRuleParser
import io.legado.app.model.jsSource.JsSourceReview
import kotlin.coroutines.CoroutineContext

/**
 * Shared network/parser layer for review summary, detail pages and replies.
 *
 * The UI decides which book/chapter/paragraph context to use; this loader only executes the
 * source review protocol. Keeping that responsibility out of activities/dialogs lets book,
 * chapter and paragraph review entries reuse the same parser and paging behavior.
 */
internal object ReviewLoader {

    data class SummaryRequest(
        val source: BookSource,
        val book: Book,
        val chapter: BookChapter,
        val ruleHash: Int,
        val ruleOverride: ReviewRule? = null,
    )

    data class SummaryResult(
        val summary: ReviewRuleParser.SummaryResult,
        val source: BaseSource,
    )

    data class DetailRequest(
        val source: BookSource,
        val book: Book,
        val chapter: BookChapter,
        val paragraphIndex: Int,
        val paragraphData: String,
        val page: Int,
        val ruleHash: Int,
        val nextPageUrl: String? = null,
        val ruleOverride: ReviewRule? = null,
    )

    data class DetailResult(
        val items: List<ReviewRuleParser.DetailItem>,
        val nextPageUrl: String?,
        val hasNextPageRule: Boolean,
        val hasReplyUrl: Boolean,
        val source: BaseSource,
    )

    data class ReplyRequest(
        val source: BookSource,
        val book: Book,
        val chapter: BookChapter,
        val paragraphIndex: Int,
        val paragraphData: String,
        val reviewId: String,
        val page: Int,
        val ruleHash: Int,
        val ruleOverride: ReviewRule? = null,
    )

    data class ReplyResult(
        val replies: List<ReviewRuleParser.DetailItem>,
        val page: Int,
        val source: BaseSource,
    )

    suspend fun loadSummary(
        request: SummaryRequest,
        coroutineContext: CoroutineContext,
    ): SummaryResult? {
        val source = request.source
        val book = request.book
        val chapter = request.chapter
        if (chapter.isVolume) return null

        if (source.isJsSource() && request.ruleOverride == null) {
            if (source.mainJs.hashCode() != request.ruleHash) return null
            val summary = JsSourceReview.getReviewSummaryAwait(source, book, chapter)
                ?: return null
            return SummaryResult(summary = summary, source = source)
        }

        val rule = request.ruleOverride ?: source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash) return null
        val summaryUrl = rule.configuredSummaryUrl() ?: return null
        val analyzeUrl = AnalyzeUrl(
            summaryUrl,
            baseUrl = chapter.url,
            source = source,
            ruleData = book,
            chapter = chapter,
            coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: return null
        return SummaryResult(
            summary = ReviewRuleParser.parseSummary(
                body,
                rule,
                source,
                book,
                chapter,
                analyzeUrl.url,
                coroutineContext,
            ),
            source = source,
        )
    }

    suspend fun loadDetail(
        request: DetailRequest,
        coroutineContext: CoroutineContext,
    ): DetailResult? {
        val source = request.source
        val book = request.book
        val chapter = request.chapter
        val page = request.page

        // Legacy Douban book reviews are a two-stage protocol: /reviews lists review URLs and
        // each item must then be loaded through the source's existing ruleContent. Keep this
        // runtime-only and distinguish its paging URL from the short-comment fallback.
        val isSyntheticBookContext = request.paragraphIndex == -1 &&
            request.paragraphData.isEmpty() &&
            chapter.bookUrl == book.bookUrl &&
            chapter.url == book.bookUrl
        val legacyDoubanNext = request.nextPageUrl
            ?.takeIf { it.startsWith(LEGACY_DOUBAN_NEXT_PREFIX) }
            ?.removePrefix(LEGACY_DOUBAN_NEXT_PREFIX)
        if (isSyntheticBookContext && (page == 1 || legacyDoubanNext != null)) {
            val legacyPage = LegacyBookReviewLoader.loadDoubanLongReviews(
                source = source,
                book = book,
                page = page,
                nextPageUrl = legacyDoubanNext,
                coroutineContext = coroutineContext,
            )
            if (legacyPage != null && (legacyPage.items.isNotEmpty() || legacyDoubanNext != null)) {
                return DetailResult(
                    items = legacyPage.items,
                    nextPageUrl = legacyPage.nextPageUrl?.let { LEGACY_DOUBAN_NEXT_PREFIX + it },
                    hasNextPageRule = legacyPage.hasNextPageRule,
                    hasReplyUrl = false,
                    source = source,
                )
            }
        }

        if (source.isJsSource() && request.ruleOverride == null) {
            if (source.mainJs.hashCode() != request.ruleHash) return null
            val result = JsSourceReview.getReviewDetailAwait(
                source = source,
                book = book,
                chapter = chapter,
                paragraphIndex = request.paragraphIndex,
                paragraphData = request.paragraphData,
                page = page,
            ) ?: return null
            return DetailResult(
                items = result.items,
                nextPageUrl = result.nextPageUrl,
                hasNextPageRule = true,
                hasReplyUrl = JsSourceReview.hasReviewRepliesCapability(source),
                source = source,
            )
        }

        val rule = request.ruleOverride ?: source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash) return null

        val firstPageUrlRule = rule.reviewDetailUrl?.takeIf { it.isNotBlank() } ?: return null
        val nextPageUrlRule = rule.reviewDetailNextPageUrl?.takeIf { it.isNotBlank() }
        val effectiveNextUrl = request.nextPageUrl?.takeIf { it.isNotBlank() }
        if (page > 1 && effectiveNextUrl == null && nextPageUrlRule == null) return null

        if (rule.detailListRule.isNullOrBlank() || rule.detailContentRule.isNullOrBlank()) {
            return null
        }

        val detailUrlRule = when {
            page > 1 && !effectiveNextUrl.isNullOrBlank() -> effectiveNextUrl
            page > 1 -> nextPageUrlRule ?: firstPageUrlRule
            else -> firstPageUrlRule
        }
        val paraIndex = request.paragraphIndex.toString()
        val paraData = request.paragraphData
        val analyzeUrl = AnalyzeUrl(
            detailUrlRule,
            page = page,
            extraParams = mapOf(
                "paraIndex" to paraIndex,
                "paraData" to paraData,
                "page" to page.toString(),
            ),
            baseUrl = chapter.url,
            source = source,
            ruleData = book,
            chapter = chapter,
            coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body ?: ""
        val result = ReviewRuleParser.parseDetailPage(
            body = body,
            rule = rule,
            nextPageRule = nextPageUrlRule,
            baseUrl = analyzeUrl.url,
            source = source,
            book = book,
            chapter = chapter,
            context = coroutineContext,
            paraIndex = paraIndex,
            paraData = paraData,
            page = page.toString(),
        )
        return DetailResult(
            items = result.items,
            nextPageUrl = result.nextPageUrl,
            hasNextPageRule = nextPageUrlRule != null,
            hasReplyUrl = !rule.reviewQuoteUrl.isNullOrBlank() &&
                !rule.replyListRule.isNullOrBlank() &&
                !rule.replyContentRule.isNullOrBlank(),
            source = source,
        )
    }

    suspend fun loadReplies(
        request: ReplyRequest,
        coroutineContext: CoroutineContext,
    ): ReplyResult? {
        val source = request.source
        val book = request.book
        val chapter = request.chapter

        if (source.isJsSource() && request.ruleOverride == null) {
            if (source.mainJs.hashCode() != request.ruleHash) return null
            val replies = JsSourceReview.getReviewRepliesAwait(
                source = source,
                book = book,
                chapter = chapter,
                paragraphIndex = request.paragraphIndex,
                paragraphData = request.paragraphData,
                reviewId = request.reviewId,
                page = request.page,
            ) ?: return null
            return ReplyResult(
                replies = replies,
                page = request.page,
                source = source,
            )
        }

        val rule = request.ruleOverride ?: source.ruleReview ?: return null
        if (!rule.enabled || rule.hashCode() != request.ruleHash) return null
        val replyUrlRule = rule.reviewQuoteUrl?.takeIf { it.isNotBlank() } ?: return null
        if (rule.replyListRule.isNullOrBlank() || rule.replyContentRule.isNullOrBlank()) {
            return null
        }

        val paraIndex = request.paragraphIndex.toString()
        val analyzeUrl = AnalyzeUrl(
            replyUrlRule,
            page = request.page,
            extraParams = mapOf(
                "paraIndex" to paraIndex,
                "paraData" to request.paragraphData,
                "reviewId" to request.reviewId,
                "page" to request.page.toString(),
            ),
            baseUrl = chapter.url,
            source = source,
            ruleData = book,
            chapter = chapter,
            coroutineContext = coroutineContext,
        )
        val body = analyzeUrl.getStrResponseAwait(useWebView = false).body
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return ReplyResult(
            replies = ReviewRuleParser.parseReplyPage(
                body = body,
                rule = rule,
                baseUrl = analyzeUrl.url,
                source = source,
                book = book,
                chapter = chapter,
                context = coroutineContext,
                paraIndex = paraIndex,
                paraData = request.paragraphData,
                page = request.page.toString(),
            ),
            page = request.page,
            source = source,
        )
    }
    private const val LEGACY_DOUBAN_NEXT_PREFIX = "legacy-douban:"

}
