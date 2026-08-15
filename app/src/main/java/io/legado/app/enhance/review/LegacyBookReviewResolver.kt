package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Conservative bridge for legacy sources that already expose whole-book reviews through
 * ruleBookInfo/ruleToc/ruleContent instead of ruleReview.
 *
 * Adapters are detected from protocol structure rather than source display names. Generic words
 * such as "review", "comment" or "书评" are intentionally insufficient because they frequently
 * occur in CSS, rankings and replacement rules that have nothing to do with review capability.
 */
internal object LegacyBookReviewResolver {

    fun resolve(source: BookSource, book: Book): ReviewRule? {
        return resolveFanqieAggregateComments(source, book)
            ?: resolveYousuu(source, book)
            ?: resolveDoubanShortComments(source, book)
            ?: resolveQqDetailCommentList(source, book)
            ?: resolveJjwxcBookComments(source, book)
    }

    /**
     * Third-party Fanqie aggregate APIs that historically append whole-book comments from
     * /api/comment into every chapter body. Move that protocol into the shared review UI instead.
     */
    private fun resolveFanqieAggregateComments(source: BookSource, book: Book): ReviewRule? {
        if (!isFanqieAggregateCommentProtocol(source)) return null

        val bookId = Regex("[?&]book_id=(\\d+)")
            .find(book.bookUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val sourceBase = source.bookSourceUrl
            .substringBefore('#')
            .trimEnd('/')
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        val firstUrl = "$sourceBase/api/comment?book_id=$bookId&count=50&offset=0"
        val nextUrlRule = "@js:var d=JSON.parse(result);" +
            "var x=d&&d.data&&d.data.data;" +
            "x&&x.has_more?'$sourceBase/api/comment?book_id=$bookId&count=50&offset='+" +
            "(parseInt(page)*50):''"
        val contentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "JSON.stringify({text:String(c.text||''),likeCount:Number(c.digg_count||0)," +
            "replyCount:Number(c.reply_count||0)})"

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = firstUrl,
            reviewDetailNextPageUrl = nextUrlRule,
            detailListRule = "$.data.data.comment",
            detailNameRule = "$.user_info.user_name",
            detailBadgeRule = "$.score",
            detailContentRule = contentRule,
        )
    }

    private fun resolveYousuu(source: BookSource, book: Book): ReviewRule? {
        if (!isYousuuCommentProtocol(source)) return null

        val bookUrl = book.bookUrl
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        if (!bookUrl.contains("/book/")) return null
        val detailUrl = if (bookUrl.contains("/api/book/")) {
            "$bookUrl/comment?type=latest&page=1"
        } else {
            "$bookUrl/comment"
        }

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = detailUrl,
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

        // Long reviews are attempted first by LegacyBookReviewLoader. This rule is also the
        // compatible short-comment fallback used by sources that switch to /comments/ when no
        // long reviews exist.
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

    private fun isFanqieAggregateCommentProtocol(source: BookSource): Boolean {
        val content = source.ruleContent?.content.orEmpty()
        val searchBookUrl = source.ruleSearch?.bookUrl.orEmpty()
        val exploreBookUrl = source.ruleExplore?.bookUrl.orEmpty()

        return content.contains("/api/comment?book_id=") &&
            content.contains("data.data.comment") &&
            content.contains("user_info") &&
            content.contains("user_name") &&
            content.contains("digg_count") &&
            content.contains("reply_count") &&
            (searchBookUrl.contains("/api/detail?book_id=") ||
                exploreBookUrl.contains("/api/detail?book_id="))
    }

    private fun isYousuuCommentProtocol(source: BookSource): Boolean {
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val chapterList = source.ruleToc?.chapterList.orEmpty()
        val chapterUrl = source.ruleToc?.chapterUrl.orEmpty()
        val content = source.ruleContent?.content.orEmpty()

        val hasCommentEndpoint = tocUrl.contains("/comment") ||
            chapterUrl.contains("/comment") || chapterList.contains("/comment")
        val hasReviewList = chapterList.contains("data.comments") ||
            chapterList.contains("书评")
        val hasReviewFields = content.contains("createrId.userName") &&
            content.contains("score") && content.contains("content") &&
            (content.contains("createdAt") || content.contains("praiseCount"))

        return hasCommentEndpoint && hasReviewList && hasReviewFields
    }

    internal fun isLegacyDoubanReviewProtocol(source: BookSource): Boolean {
        val sourceUrl = source.bookSourceUrl.substringBefore('#')
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val chapterList = source.ruleToc?.chapterList.orEmpty()
        val chapterUrl = source.ruleToc?.chapterUrl.orEmpty()
        val content = source.ruleContent?.content.orEmpty()

        val hasReviewList = chapterList.contains("review-list") ||
            chapterList.contains("review-item")
        val hasReviewContent = content.contains("review-content")
        val hasReviewUrl = tocUrl.contains("reviews") || sourceUrl.contains("douban.com")

        return hasReviewUrl && hasReviewList &&
            chapterUrl.contains("href") && hasReviewContent
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
