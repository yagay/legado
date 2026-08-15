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
            ?: resolveJjwxcBookComments(source, book)
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

    /**
     * Older JJWXC sources already request a whole-book preview list with only novelId and parse
     * data.commentList. Keep that scope separate from chapter comments such as comment_json.php,
     * which also require chapterId and therefore belong to chapter review rather than BookReview.
     */
    private fun resolveJjwxcBookComments(source: BookSource, book: Book): ReviewRule? {
        if (!isJjwxcBookCommentProtocol(source)) return null

        val novelId = Regex("(?:novelId=|/book\\d?/)(\\d+)")
            .find(book.bookUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = "https://android.jjwxc.net/comment/getCommentList?versionCode=268&novelId=$novelId&limit=5",
            detailListRule = "$.data.commentList",
            detailNameRule = "$.commentAuthor",
            detailBadgeRule = "$.ip_pos",
            detailContentRule = "$.commentBody",
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

    internal fun isLegacyDoubanReviewProtocol(source: BookSource): Boolean {
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

    private fun isJjwxcBookCommentProtocol(source: BookSource): Boolean {
        val infoIntro = source.ruleBookInfo?.intro.orEmpty()

        return infoIntro.contains("comment/getCommentList?versionCode=268&novelId=") &&
            infoIntro.contains("A.data.commentList") &&
            infoIntro.contains("commentAuthor") &&
            infoIntro.contains("commentBody") &&
            infoIntro.contains("commentDate") &&
            !infoIntro.contains("chapterId=")
    }
}
