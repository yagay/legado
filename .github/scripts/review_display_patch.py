from pathlib import Path

resolver = Path('app/src/main/java/io/legado/app/enhance/review/LegacyBookReviewResolver.kt')
text = resolver.read_text()
old = '''            "if(u.is_author||c.is_author)b.push('作者');" +
            "var tags=c.tags||u.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){" +'''
new = '''            "if(u.is_author||c.is_author)b.push('作者');" +
            "if(u.is_vip||c.is_vip)b.push('VIP');" +
            "var tags=c.tags||u.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){" +'''
if old not in text:
    raise SystemExit('resolver badge target not found')
text = text.replace(old, new, 1)

old = '''            detailBadgeRule = badgeRule,
            detailContentRule = contentRule,
        )'''
new = '''            detailBadgeRule = badgeRule,
            detailContentRule = contentRule,
            replyListRule = "$.replies",
            replyIdRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);String(c.comment_id||c.id||'')",
            replyAvatarRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};String(u.user_avatar||u.avatar_url||u.avatar||u.user_avatar_url||'')",
            replyNameRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};var n=String(u.user_name||u.nickname||u.name||'匿名');var r=c.reply_to_user||{};var rn=String(r.user_name||r.nickname||r.name||'');rn?n+' 回复 '+rn:n",
            replyBadgeRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var u=c.user||c.user_info||{};var b=[];if(u.is_author||c.is_author)b.push('作者');if(u.is_vip||c.is_vip)b.push('VIP');var tags=u.tags||c.tags||[];if(Array.isArray(tags)){for(var i=0;i<tags.length;i++){var t=tags[i];if(t&&typeof t==='object')t=t.name||t.text||t.title;if(t)b.push(String(t));}}JSON.stringify(b)",
            replyContentRule = "@js:var c=(typeof result==='string'?JSON.parse(result):result);var t=String(c.content||c.text||'');var tm=c.create_time||c.create_time_str||c.publish_time||c.comment_time||c.created_at||c.createdAt||'';if(!tm&&c.create_timestamp){var n=Number(c.create_timestamp);if(n<1000000000000)n*=1000;try{tm=new Date(n).toLocaleString();}catch(x){tm=String(c.create_timestamp);}}var img=String(c.image_url||c.image||'');JSON.stringify({text:t,img:img,time:String(tm||''),likeCount:Number(c.like_count||c.digg_count||0)})",
        )'''
if old not in text:
    raise SystemExit('resolver return target not found')
text = text.replace(old, new, 1)
resolver.write_text(text)

dialog = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = dialog.read_text()
old = '''            if (item.avatar.isNullOrBlank()) {
                binding.ivAvatar.gone()
            } else {
                binding.ivAvatar.visible()
                ImageLoader.load(context, item.avatar)
                    .apply(sourceImageOptions)
                    .circleCrop()
                    .into(binding.ivAvatar)
            }'''
new = '''            if (item.avatar.isNullOrBlank()) {
                // Match the source web review UI: keep an avatar placeholder instead of
                // collapsing the avatar column when a user has no public avatar.
                binding.ivAvatar.visible()
                Glide.with(binding.ivAvatar).clear(binding.ivAvatar)
                binding.ivAvatar.setImageResource(R.drawable.ic_author)
                binding.ivAvatar.setColorFilter(secondaryColor)
                binding.ivAvatar.setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            } else {
                binding.ivAvatar.visible()
                binding.ivAvatar.clearColorFilter()
                binding.ivAvatar.setPadding(0, 0, 0, 0)
                ImageLoader.load(context, item.avatar)
                    .apply(sourceImageOptions)
                    .circleCrop()
                    .placeholder(R.drawable.ic_author)
                    .error(R.drawable.ic_author)
                    .into(binding.ivAvatar)
            }'''
if old not in text:
    raise SystemExit('dialog avatar target not found')
# secondaryColor is declared just after old block; move declaration before it.
old_decl = '''            val avatarSize = if (item.isReply) 28.dpToPx() else 36.dpToPx()
            binding.ivAvatar.updateLayoutParams<ViewGroup.LayoutParams> {
                width = avatarSize
                height = avatarSize
            }

'''
new_decl = '''            val avatarSize = if (item.isReply) 28.dpToPx() else 36.dpToPx()
            binding.ivAvatar.updateLayoutParams<ViewGroup.LayoutParams> {
                width = avatarSize
                height = avatarSize
            }
            val secondaryColor = context.getCompatColor(R.color.secondaryText)

'''
if old_decl not in text:
    raise SystemExit('dialog avatar declaration target not found')
text = text.replace(old_decl, new_decl, 1)
text = text.replace(old, new, 1)
# Remove the later duplicate secondaryColor declaration.
dupe = '''            val primaryColor = context.getCompatColor(R.color.primaryText)
            val secondaryColor = context.getCompatColor(R.color.secondaryText)
            val contentColor = context.getCompatColor(R.color.reviewContentText)'''
replacement = '''            val primaryColor = context.getCompatColor(R.color.primaryText)
            val contentColor = context.getCompatColor(R.color.reviewContentText)'''
if dupe not in text:
    raise SystemExit('dialog secondary color target not found')
text = text.replace(dupe, replacement, 1)
dialog.write_text(text)
