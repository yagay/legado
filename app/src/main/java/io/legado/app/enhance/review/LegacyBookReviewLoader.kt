package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.ReviewRuleParser
import io.legado.app.model.webBook.WebBook
import kotlin.coroutines.CoroutineContext

/**
 * Loads legacy whole-book review protocols that cannot be represented by one ReviewRule request.
 *
 * The first supported shape is old Douban sources: a /reviews list contains links to individual
 * review pages, while ruleContent parses the full review body. We deliberately reuse the source's
 * existing ruleToc/ruleContent through AnalyzeRule/WebBook instead of introducing a second HTML
 * parser or changing the persisted ReviewRule schema.
 */
internal object LegacyBookReviewLoader {

    data class DetailPage(
        val items: List<ReviewRuleParser.DetailItem>,
        val nextPageUrl: String?,
        val hasNextPageRule: Boolean,
    )

    suspend fun loadDoubanLongReviews(
        source: BookSource,
        book: Book,
        page: Int,
        nextPageUrl: String?,
        coroutineContext: CoroutineContext,
    ): DetailPage? {
        if (!LegacyBookReviewResolver.isLegacyDoubanReviewProtocol(source)) return null

        val bookUrl = book.bookUrl
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        if (!bookUrl.contains("douban.com/subject/")) return null

        val listUrl = if (page > 1) {
            nextPageUrl?.takeIf { it.isNotBlank() } ?: return null
        } else {
            "$bookUrl/reviews"
        }

        val response = AnalyzeUrl(
            listUrl,
            baseUrl = bookUrl,
            source = source,
            ruleData = book,
            coroutineContext = coroutineContext,
        ).getStrResponseAwait(useWebView = false)
        val body = response.body ?: return null
        val redirectUrl = response.url

        val tocRule = source.ruleToc ?: return null
        val listRule = tocRule.chapterList?.takeIf { it.isNotBlank() } ?: return null
        val urlRule = tocRule.chapterUrl?.takeIf { it.isNotBlank() } ?: return null
        val nameRule = tocRule.chapterName.orEmpty()
        val timeRule = tocRule.updateTime.orEmpty()

        val listAnalyzer = AnalyzeRule(book, source)
            .setCoroutineContext(coroutineContext)
            .setContent(body, redirectUrl)
        val elements = listAnalyzer.getElements(listRule)
        if (elements.isEmpty()) {
            return DetailPage(
                items = emptyList(),
                nextPageUrl = null,
                hasNextPageRule = !tocRule.nextTocUrl.isNullOrBlank(),
            )
        }

        val itemAnalyzer = AnalyzeRule(book, source)
            .setCoroutineContext(coroutineContext)
        val items = ArrayList<ReviewRuleParser.DetailItem>()
        elements.take(MAX_REVIEWS_PER_PAGE).forEachIndexed { index, element ->
            itemAnalyzer.setContent(element, redirectUrl)
            val reviewUrl = itemAnalyzer.getString(urlRule, isUrl = true)
                .takeIf { it.isNotBlank() }
                ?: return@forEachIndexed
            val title = itemAnalyzer.getString(nameRule).takeIf { it.isNotBlank() }
            val meta = itemAnalyzer.getString(timeRule).takeIf { it.isNotBlank() }
            val chapter = BookChapter(
                url = reviewUrl,
                title = title.orEmpty(),
                baseUrl = redirectUrl,
                bookUrl = book.bookUrl,
                index = index,
            )
            val content = runCatching {
                WebBook.getContentAwait(
                    bookSource = source,
                    book = book,
                    bookChapter = chapter,
                    needSave = false,
                )
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed

            items.add(
                ReviewRuleParser.DetailItem(
                    id = reviewUrl,
                    avatar = null,
                    name = title,
                    badges = listOfNotNull(meta),
                    content = content,
                    imageUrl = null,
                    audioUrl = null,
                    time = null,
                    likeCount = null,
                    replyCount = null,
                    replies = emptyList(),
                )
            )
        }

        val nextRule = tocRule.nextTocUrl?.takeIf { it.isNotBlank() }
        val resolvedNextUrl = nextRule?.let {
            listAnalyzer.getString(it, isUrl = true).takeIf(String::isNotBlank)
        }
        return DetailPage(
            items = items,
            nextPageUrl = resolvedNextUrl,
            hasNextPageRule = nextRule != null,
        )
    }

    private const val MAX_REVIEWS_PER_PAGE = 20
}
