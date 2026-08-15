package io.legado.app.enhance.review

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule

/**
 * Runtime-only adapters for legacy sources that expose paragraph comments through their own
 * browser/JS protocol instead of BookSource.ruleReview.
 *
 * Keep detection protocol-based rather than source-name-based so compatible sources can share
 * the native ReviewRuleParser without changing imported source JSON or the database schema.
 */
internal object LegacyParagraphReviewResolver {

    fun resolve(source: BookSource, reviewData: String): ReviewRule? {
        if (source.ruleReview != null || reviewData.isBlank()) return null
        return resolveAggregateParaReview(source, reviewData)
    }

    private fun resolveAggregateParaReview(source: BookSource, reviewData: String): ReviewRule? {
        val js = source.jsLib.orEmpty()
        val supportsProtocol = js.contains("/get_para_review", ignoreCase = true) &&
            js.contains("/para_review?book_id=", ignoreCase = true)
        if (!supportsProtocol) return null

        val apiUrl = reviewData
            .replace("/get_para_review?", "/para_review?", ignoreCase = true)
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        if (!apiUrl.contains("/para_review?", ignoreCase = true)) return null

        val basePageUrl = apiUrl.replace(Regex("([?&])cursor=[^&]*&?", RegexOption.IGNORE_CASE)) { match ->
            if (match.value.endsWith("&")) match.groupValues[1] else ""
        }.trimEnd('?', '&')
        val escapedBase = jsQuote(basePageUrl)

        return ReviewRule(
            enabled = true,
            reviewDetailUrl = apiUrl,
            reviewDetailNextPageUrl = "@js:var d=(typeof result==='string'?JSON.parse(result):result);" +
                "var x=(d&&d.data)||{};var c=String(x.next_cursor||'');" +
                "(x.has_more&&c)?'$escapedBase'+('$escapedBase'.indexOf('?')>=0?'&':'?')+'cursor='+encodeURIComponent(c):''",
            detailListRule = "@js:var d=(typeof result==='string'?JSON.parse(result):result);" +
                "var x=(d&&d.data)||{};var out=[],seen={};" +
                "function add(a){if(!Array.isArray(a))return;for(var i=0;i<a.length;i++){var c=a[i]||{};" +
                "var id=String(c.comment_id||c.id||'');var k=id||('idx:'+out.length+':'+String(c.content||''));" +
                "if(!seen[k]){seen[k]=1;out.push(c);}}}add(x.hot_comments);add(x.comments);out",
            detailIdRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.comment_id||c.id||'')",
            detailAvatarRule = userAvatarRule,
            detailNameRule = userNameRule,
            detailBadgeRule = badgeRule,
            detailContentRule = contentRule,
            // Aggregate APIs commonly embed a first batch of replies directly in each comment.
            // ReviewRuleParser already knows how to parse these when reviewQuoteUrl is absent.
            replyListRule = "$.replies",
            replyIdRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.comment_id||c.id||'')",
            replyAvatarRule = userAvatarRule,
            replyNameRule = replyNameRule,
            replyBadgeRule = badgeRule,
            replyContentRule = replyContentRule,
        )
    }

    private fun jsQuote(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "")
        .replace("\n", "")

    private const val userAvatarRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var u=c.user||c.user_info||{};String(u.user_avatar||u.avatar_url||u.avatar||u.user_avatar_url||'')"

    private const val userNameRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var u=c.user||c.user_info||{};String(u.user_name||u.nickname||u.name||c.user_name||'匿名用户')"

    private const val badgeRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};var b=[];" +
            "if(u.is_author||c.is_author)b.push('作者');if(u.is_vip||c.is_vip)b.push('VIP');" +
            "var tags=c.tags||u.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){var t=tags[i];" +
            "if(t&&typeof t==='object')t=t.name||t.text||t.title;if(t)b.push(String(t));}}JSON.stringify(b)"

    private const val contentRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var img=c.image_url||c.image||'';if(Array.isArray(img))img=img.length?(typeof img[0]==='string'?img[0]:(img[0].url||img[0].image_url||'')):'';" +
            "JSON.stringify({text:String(c.content||c.text||''),img:String(img||''),time:String(c.create_time||c.create_time_str||'')," +
            "likeCount:Number(c.like_count||c.digg_count||0),replyCount:Number(c.reply_count||0)})"

    private const val replyNameRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};" +
            "var n=String(u.user_name||u.nickname||u.name||'匿名');var r=c.reply_to_user||{};" +
            "var rn=String(r.user_name||r.nickname||r.name||'');rn?n+' 回复 '+rn:n"

    private const val replyContentRule =
        "@js:var c=(typeof result==='string'?JSON.parse(result):result);" +
            "var img=c.image_url||c.image||'';if(Array.isArray(img))img=img.length?(typeof img[0]==='string'?img[0]:(img[0].url||img[0].image_url||'')):'';" +
            "JSON.stringify({text:String(c.content||c.text||''),img:String(img||''),time:String(c.create_time||c.create_time_str||'')," +
            "likeCount:Number(c.like_count||c.digg_count||0)})"
}
