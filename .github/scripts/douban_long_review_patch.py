from pathlib import Path

path = Path('app/src/main/java/io/legado/app/enhance/review/ReviewLoader.kt')
text = path.read_text(encoding='utf-8')
marker = '''        val chapter = request.chapter
        val page = request.page

        if (source.isJsSource() && request.ruleOverride == null) {
'''
insert = '''        val chapter = request.chapter
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
'''
if marker not in text:
    raise SystemExit('loadDetail insertion marker not found')
text = text.replace(marker, insert, 1)

end_marker = '\n}\n'
pos = text.rfind(end_marker)
if pos < 0:
    raise SystemExit('object end marker not found')
text = text[:pos] + '''\n    private const val LEGACY_DOUBAN_NEXT_PREFIX = "legacy-douban:"\n''' + text[pos:]
path.write_text(text, encoding='utf-8')
