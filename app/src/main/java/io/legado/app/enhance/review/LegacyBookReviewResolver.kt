package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Conservative bridge for legacy sources that already expose whole-book reviews through
 * ruleBookInfo/ruleToc/ruleContent instead of ruleReview.
 *
 * Keep adapters protocol-specific and high-confidence. Do not infer review capability from
 * generic words such as "review", "comment" or "书评", because those commonly occur in CSS,
 * ranking URLs and directory exclusion rules.
 */
internal object LegacyBookReviewResolver {

    fun resolve(source: BookSource, book: Book): ReviewRule? {
        return resolveYousuu(source, book)
            ?: resolveDoubanShortComments(source, book)
            ?: resolveQqDetailCommentList(source, book)
    }

    private fun resolveYousuu(source: BookSource, book: Book): ReviewRule? {
        if (!isYousuuCommentProtocol(source)) return null

        val bookApiUrl = book.bookUrl
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        if (!bookApiUrl.contains("/api/book/")) return null

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = "$bookApiUrl/comment?type=latest&page=1",
            detailListRule = "$.data.comments",
            detailNameRule = "$.createrId.userName",
            detailBadgeRule = "$.score",
            detailContentRule = "$.content",
        )
    }

    /**
     * Legacy Douban sources model long reviews as fake chapters: /reviews -> review detail URL.
     * ReviewDetailDialog currently has no per-item second request, therefore mapping that path
     * would show incomplete items. The native bridge deliberately uses the book-level short
     * comment list (/comments/) first; each row already contains the displayable comment body and
     * can be parsed by the existing ReviewRuleParser in one request.
     *
     * Long-review drill-down can be added later after the review loader is separated from the UI.
     */
    private fun resolveDoubanShortComments(source: BookSource, book: Book): ReviewRule? {
        if (!isLegacyDoubanReviewProtocol(source)) return null

        val bookUrl = book.bookUrl
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        if (!bookUrl.contains("douban.com/subject/")) return null

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = "$bookUrl/comments/",
            reviewDetailNextPageUrl = "text.后一页@href||class.next@tag.a@href",
            detailListRule = "class.comment-item||class.grid_view@tag.ul@tag.li||class.comment@tag.li",
            detailNameRule = "class.comment-info@tag.a.0@text||tag.a.0@text",
            detailBadgeRule = "class.rating@title||class.rating@class",
            detailContentRule = "class.short@text||class.comment-content@text||class.comment@tag.p@text",
        )
    }

    /**
     * Some QQ Reader legacy sources already receive a small whole-book commentlist in the normal
     * book-detail JSON and append only commentlist.content to the introduction. Re-requesting the
     * same book-detail URL lets the native review UI show those items without introducing another
     * endpoint or changing the source JSON.
     *
     * This is intentionally a preview adapter: the legacy source does not expose a paged full
     * review endpoint, so no next-page rule is fabricated here.
     */
    private fun resolveQqDetailCommentList(source: BookSource, book: Book): ReviewRule? {
        if (!isQqDetailCommentListProtocol(source)) return null

        val detailUrl = book.bookUrl.substringBefore('#')
        if (!detailUrl.contains("detailadr.reader.qq.com/") || !detailUrl.contains("bid=")) {
            return null
        }

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = detailUrl,
            detailListRule = "$..commentlist[*]",
            detailContentRule = "$.content",
        )
    }

    private fun isYousuuCommentProtocol(source: BookSource): Boolean {
        val sourceUrl = source.bookSourceUrl.substringBefore('#').trimEnd('/')
        if (sourceUrl != "https://api.yousuu.com") return false

        val tocRule = source.ruleBookInfo?.tocUrl.orEmpty()
        val legacyListRule = source.ruleToc?.chapterList.orEmpty()
        val legacyContentRule = source.ruleContent?.content.orEmpty()

        return tocRule.contains("/api/book/") &&
            tocRule.contains("/comment") &&
            legacyListRule.contains("data.comments") &&
            legacyContentRule.contains("createrId.userName") &&
            legacyContentRule.contains("createdAt") &&
            legacyContentRule.contains("score") &&
            legacyContentRule.contains("content")
    }

    private fun isLegacyDoubanReviewProtocol(source: BookSource): Boolean {
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val chapterList = source.ruleToc?.chapterList.orEmpty()
        val chapterUrl = source.ruleToc?.chapterUrl.orEmpty()
        val content = source.ruleContent?.content.orEmpty()

        return tocUrl.contains("baseUrl+'reviews'") &&
            tocUrl.contains("comments/") &&
            chapterList.contains("review-list") &&
            chapterUrl.contains("href") &&
            content.contains("review-content") &&
            content.contains("class.comment")
    }

    private fun isQqDetailCommentListProtocol(source: BookSource): Boolean {
        val infoIntro = source.ruleBookInfo?.intro.orEmpty()
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val searchBookUrl = source.ruleSearch?.bookUrl.orEmpty()
        val exploreBookUrl = source.ruleExplore?.bookUrl.orEmpty()

        return infoIntro.contains("commentlist..content") &&
            tocUrl.contains("ubook.reader.qq.com/api/book/chapter-list") &&
            (searchBookUrl.contains("detailadr.reader.qq.com/") ||
                exploreBookUrl.contains("detailadr.reader.qq.com/"))
    }
}
