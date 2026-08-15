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

    private fun resolveFanqieAggregateComments(source: BookSource, book: Book): ReviewRule? {
        if (!isFanqieAggregateCommentProtocol(source)) return null

        val bookId = fanqieAggregateBookId(book) ?: return null
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
        val avatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var u=c.user_info||c.user||{};" +
            "String(u.user_avatar||u.avatar_url||u.avatar||u.user_avatar_url||u.avatar_uri||'')"
        val nameRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var u=c.user_info||c.user||{};" +
            "String(u.user_name||u.nickname||u.name||c.user_name||'匿名用户')"
        val badgeRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var u=c.user_info||c.user||{};var b=[];" +
            "if(c.score!==undefined&&c.score!==null&&String(c.score)!=='')b.push('⭐ '+c.score);" +
            "if(u.is_author||c.is_author)b.push('作者');" +
            "if(u.is_vip||c.is_vip)b.push('VIP');" +
            "var tags=c.tags||u.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){" +
            "var t=tags[i];if(t&&typeof t==='object')t=t.name||t.text||t.title;if(t)b.push(String(t));}}" +
            "JSON.stringify(b)"
        val contentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var t=String(c.text||c.content||'');" +
            "var e={'[微笑]':'🙂','[偷笑]':'🤭','[笑]':'😄','[什么]':'❓','[害羞]':'😊','[爱慕]':'😍'," +
            "'[飞吻]':'😘','[奸笑]':'😏','[尬笑]':'😅','[思考]':'🤔','[撇嘴]':'😒','[做鬼脸]':'😜'," +
            "'[酷]':'😎','[翻白眼]':'🙄','[惊呆]':'😲','[震惊]':'😱','[送心]':'💗','[委屈]':'🥺','[快哭了]':'😢'};" +
            "for(var k in e)t=t.split(k).join(e[k]);" +
            "var tm=c.create_time||c.create_time_str||c.publish_time||c.comment_time||c.created_at||c.createdAt||'';" +
            "if(!tm&&c.create_timestamp){var n=Number(c.create_timestamp);if(n<1000000000000)n*=1000;" +
            "try{tm=new Date(n).toLocaleString();}catch(x){tm=String(c.create_timestamp);}}" +
            "var img='';var im=c.images||c.image_list||c.pictures||c.image_url||c.image;" +
            "if(Array.isArray(im)&&im.length){var p=im[0];img=typeof p==='string'?p:(p.url||p.image_url||p.src||'');}" +
            "else if(typeof im==='string')img=im;else if(im&&typeof im==='object')img=im.url||im.image_url||im.src||'';" +
            "JSON.stringify({text:t,img:img,time:String(tm||''),likeCount:Number(c.digg_count||c.like_count||0)," +
            "replyCount:Number(c.reply_count||0)})"

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = firstUrl,
            reviewDetailNextPageUrl = nextUrlRule,
            detailListRule = "$.data.data.comment",
            detailIdRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.comment_id||c.id||'')",
            detailAvatarRule = avatarRule,
            detailNameRule = nameRule,
            detailBadgeRule = badgeRule,
            detailContentRule = contentRule,
            replyListRule = "$.replies",
            replyIdRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.comment_id||c.id||'')",
            replyAvatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};String(u.user_avatar||u.avatar_url||u.avatar||u.user_avatar_url||'')",
            replyNameRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};var n=String(u.user_name||u.nickname||u.name||'匿名');var r=c.reply_to_user||{};var rn=String(r.user_name||r.nickname||r.name||'');rn?n+' 回复 '+rn:n",
            replyBadgeRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};var b=[];if(u.is_author||c.is_author)b.push('作者');if(u.is_vip||c.is_vip)b.push('VIP');var tags=u.tags||c.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){var t=tags[i];if(t&&typeof t==='object')t=t.name||t.text||t.title;if(t)b.push(String(t));}}JSON.stringify(b)",
            replyContentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var t=String(c.content||c.text||'');var tm=c.create_time||c.create_time_str||c.publish_time||c.comment_time||c.created_at||c.createdAt||'';if(!tm&&c.create_timestamp){var n=Number(c.create_timestamp);if(n<1000000000000)n*=1000;try{tm=new Date(n).toLocaleString();}catch(x){tm=String(c.create_timestamp);}}var img=String(c.image_url||c.image||'');JSON.stringify({text:t,img:img,time:String(tm||''),likeCount:Number(c.like_count||c.digg_count||0)})",
        )
    }

    private fun resolveYousuu(source: BookSource, book: Book): ReviewRule? {
        if (!isYousuuCommentProtocol(source)) return null

        val bookUrl = book.bookUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        if (!bookUrl.contains("/book/")) return null
        val detailUrl = if (bookUrl.contains("/api/book/")) {
            "$bookUrl/comment?type=latest&page=1"
        } else {
            "$bookUrl/comment"
        }
        val contentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "JSON.stringify({text:String(c.content||''),time:String(c.createdAt||c.createTime||'')," +
            "likeCount:Number(c.praiseCount||c.likeCount||0)})"

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = detailUrl,
            detailListRule = "$.data.comments",
            detailIdRule = "$.id",
            detailAvatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.createrId||c.creator||{};String(u.avatar||u.avatarUrl||u.headImg||'')",
            detailNameRule = "$.createrId.userName",
            detailBadgeRule = "$.score",
            detailContentRule = contentRule,
        )
    }

    private fun resolveDoubanShortComments(source: BookSource, book: Book): ReviewRule? {
        if (!isLegacyDoubanReviewProtocol(source)) return null

        val bookUrl = book.bookUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        if (!bookUrl.contains("douban.com/subject/")) return null

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = "$bookUrl/comments/",
            reviewDetailNextPageUrl = "text.后一页@href||class.next@tag.a@href",
            detailListRule = "class.comment-item||class.grid_view@tag.ul@tag.li||class.comment@tag.li",
            detailAvatarRule = "class.avatar@tag.img@src||tag.img.0@src",
            detailNameRule = "class.comment-info@tag.a.0@text||tag.a.0@text",
            detailBadgeRule = "class.rating@title||class.rating@class",
            detailContentRule = "class.short@text||class.comment-content@text||class.comment@tag.p@text",
        )
    }

    private fun resolveQqDetailCommentList(source: BookSource, book: Book): ReviewRule? {
        if (!isQqDetailCommentListProtocol(source)) return null

        val detailUrl = book.bookUrl.substringBefore('#')
        if (!detailUrl.contains("detailadr.reader.qq.com/") || !detailUrl.contains("bid=")) return null
        val contentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "JSON.stringify({text:String(c.content||c.text||''),time:String(c.time||c.create_time||c.created_at||'')," +
            "likeCount:Number(c.like_count||c.digg_count||0),replyCount:Number(c.reply_count||0)})"

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = detailUrl,
            detailListRule = "$..commentlist[*]",
            detailIdRule = "$.id",
            detailAvatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.userInfo||{};String(c.avatar||u.avatar||u.avatarUrl||'')",
            detailNameRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.userInfo||{};String(c.nick||c.nickname||u.nick||u.nickname||u.name||'')",
            detailContentRule = contentRule,
        )
    }

    private fun resolveJjwxcBookComments(source: BookSource, book: Book): ReviewRule? {
        if (!isJjwxcBookCommentProtocol(source)) return null

        val novelId = Regex("(?:novelId=|/book\\d?/)(\\d+)")
            .find(book.bookUrl)?.groupValues?.getOrNull(1) ?: return null
        val contentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "JSON.stringify({text:String(c.commentBody||''),time:String(c.commentDate||c.postTime||'')})"

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = "https://android.jjwxc.net/comment/getCommentList?versionCode=268&novelId=$novelId&limit=5",
            detailListRule = "$.data.commentList",
            detailIdRule = "$.commentId",
            detailAvatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.avatar||c.userAvatar||c.commentAvatar||'')",
            detailNameRule = "$.commentAuthor",
            detailBadgeRule = "$.ip_pos",
            detailContentRule = contentRule,
        )
    }

    internal fun fanqieAggregateBookId(book: Book): String? {
        return Regex("[?&]book_id=(\\d+)").find(book.bookUrl)?.groupValues?.getOrNull(1)
    }

    internal fun isFanqieAggregateCommentProtocol(source: BookSource): Boolean {
        val content = source.ruleContent?.content.orEmpty()
        val searchBookUrl = source.ruleSearch?.bookUrl.orEmpty()
        val exploreBookUrl = source.ruleExplore?.bookUrl.orEmpty()

        return content.contains("/api/comment?book_id=") &&
            content.contains("data.data.comment") &&
            content.contains("user_info") &&
            content.contains("user_name") &&
            content.contains("digg_count") &&
            content.contains("reply_count") &&
            (searchBookUrl.contains("/api/detail?book_id=") || exploreBookUrl.contains("/api/detail?book_id="))
    }

    private fun isYousuuCommentProtocol(source: BookSource): Boolean {
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val chapterList = source.ruleToc?.chapterList.orEmpty()
        val chapterUrl = source.ruleToc?.chapterUrl.orEmpty()
        val content = source.ruleContent?.content.orEmpty()

        val hasCommentEndpoint = tocUrl.contains("/comment") || chapterUrl.contains("/comment") || chapterList.contains("/comment")
        val hasReviewList = chapterList.contains("data.comments") || chapterList.contains("书评")
        val hasReviewFields = content.contains("createrId.userName") && content.contains("score") && content.contains("content") &&
            (content.contains("createdAt") || content.contains("praiseCount"))

        return hasCommentEndpoint && hasReviewList && hasReviewFields
    }

    internal fun isLegacyDoubanReviewProtocol(source: BookSource): Boolean {
        val sourceUrl = source.bookSourceUrl.substringBefore('#')
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val chapterList = source.ruleToc?.chapterList.orEmpty()
        val chapterUrl = source.ruleToc?.chapterUrl.orEmpty()
        val content = source.ruleContent?.content.orEmpty()

        val hasReviewList = chapterList.contains("review-list") || chapterList.contains("review-item")
        val hasReviewContent = content.contains("review-content")
        val hasReviewUrl = tocUrl.contains("reviews") || sourceUrl.contains("douban.com")

        return hasReviewUrl && hasReviewList && chapterUrl.contains("href") && hasReviewContent
    }

    private fun isQqDetailCommentListProtocol(source: BookSource): Boolean {
        val infoIntro = source.ruleBookInfo?.intro.orEmpty()
        val tocUrl = source.ruleBookInfo?.tocUrl.orEmpty()
        val searchBookUrl = source.ruleSearch?.bookUrl.orEmpty()
        val exploreBookUrl = source.ruleExplore?.bookUrl.orEmpty()

        return infoIntro.contains("commentlist..content") &&
            tocUrl.contains("ubook.reader.qq.com/api/book/chapter-list") &&
            (searchBookUrl.contains("detailadr.reader.qq.com/") || exploreBookUrl.contains("detailadr.reader.qq.com/"))
    }

    internal fun isJjwxcBookCommentProtocol(source: BookSource): Boolean {
        val infoIntro = source.ruleBookInfo?.intro.orEmpty()

        return infoIntro.contains("comment/getCommentList?versionCode=268&novelId=") &&
            infoIntro.contains("A.data.commentList") &&
            infoIntro.contains("commentAuthor") &&
            infoIntro.contains("commentBody") &&
            infoIntro.contains("commentDate") &&
            !infoIntro.contains("chapterId=")
    }
}
