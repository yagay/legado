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
}
