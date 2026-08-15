from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = path.read_text(encoding='utf-8')

import_line = 'import io.legado.app.enhance.review.ReviewLoader\n'
if import_line not in text:
    anchor = 'import io.legado.app.help.coroutine.Coroutine\n'
    if anchor not in text:
        raise SystemExit('Coroutine import anchor not found')
    text = text.replace(anchor, import_line + anchor, 1)

start_marker = '    private fun loadDetailPage(paragraphNum: Int, page: Int, append: Boolean) {'
end_marker = '    private fun loadReplies(parentKey: String) {'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('loadDetailPage boundaries not found')

old = text[start:end]
if 'ReviewLoader.loadDetail(' not in old:
    new = '''    private fun loadDetailPage(paragraphNum: Int, page: Int, append: Boolean) {
        if (isLoading) return
        if (!append) {
            binding.rotateLoading.visible()
            binding.tvMsg.gone()
            adapter.setItems(emptyList())
            currentPage = 1
            hasMore = true
            nextPageUrl = null
            mainItemIndexByKey.clear()
            detailItems.clear()
            expandedReplyParentKeys.clear()
            replyLoadingParentKeys.clear()
            replyExhaustedParentKeys.clear()
            replyPageByParentKey.clear()
            hasReplyUrl = false
        }
        if (!hasMore) return
        isLoading = true
        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
            val source = ReadBook.bookSource ?: return@async null
            if (source.getKey() != sourceKey) return@async null
            val book = ReadBook.book ?: return@async null
            if (book.bookUrl != bookUrl) return@async null
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                ?: return@async null
            ReviewLoader.loadDetail(
                ReviewLoader.DetailRequest(
                    source = source,
                    book = book,
                    chapter = chapter,
                    paragraphIndex = paragraphNum,
                    paragraphData = paragraphData,
                    page = page,
                    ruleHash = ruleHash,
                    nextPageUrl = nextPageUrl,
                ),
                coroutineContext = coroutineContext,
            )
        }.onSuccess(Main) { result ->
            if (!append) {
                binding.rotateLoading.gone()
            }
            result?.source?.let { reviewSource = it }
            result?.let { hasReplyUrl = it.hasReplyUrl }
            val items = result?.items.orEmpty()
            val nextUrlFromRule = result?.nextPageUrl
            if (result?.hasNextPageRule == true) {
                nextPageUrl = nextUrlFromRule
                if (nextUrlFromRule.isNullOrBlank()) {
                    hasMore = false
                }
            }
            if (items.isEmpty() && !append) {
                hasMore = false
                binding.tvMsg.text = getString(R.string.content_empty)
                binding.tvMsg.visible()
                isLoading = false
                return@onSuccess
            }
            if (items.isEmpty()) {
                hasMore = false
                isLoading = false
                return@onSuccess
            }
            val mergedCount = mergeDetailItems(items)
            if (mergedCount == 0 && append) {
                hasMore = false
                isLoading = false
                return@onSuccess
            }
            currentPage = page
            renderUiItems()
            isLoading = false
        }.onError {
            isLoading = false
            if (!append) {
                binding.rotateLoading.gone()
                binding.tvMsg.text = it.localizedMessage ?: getString(R.string.content_empty)
                binding.tvMsg.visible()
            }
        }.start()
    }

'''
    text = text[:start] + new + text[end:]

path.write_text(text, encoding='utf-8')
