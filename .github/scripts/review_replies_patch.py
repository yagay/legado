from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = path.read_text(encoding='utf-8')

start_marker = '    private fun loadReplies(parentKey: String) {'
end_marker = '    private fun buildDetailItemKey(item: ReviewDetailItem, isReply: Boolean): String {'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('loadReplies boundaries not found')
block = text[start:end]

old_start = '        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {\n'
on_success = '        }.onSuccess(Main) { result ->\n'
request_start = block.find(old_start)
request_end = block.find(on_success, request_start)
if request_start < 0 or request_end < 0:
    raise SystemExit('loadReplies request block not found')

new_request = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
            val session = ReviewDialogSessionStore.get(reviewSessionId)
            if (session != null) {
                val reviewContext = session.context
                val source = reviewContext.source
                val rule = session.rule ?: source.ruleReview
                return@async ReviewLoader.loadReplies(
                    ReviewLoader.ReplyRequest(
                        source = source,
                        book = reviewContext.book,
                        chapter = reviewContext.chapterForAnalyze(),
                        paragraphIndex = reviewContext.paragraphIndexForAnalyze(),
                        paragraphData = reviewContext.paragraphDataForAnalyze(),
                        reviewId = reviewId,
                        page = page,
                        ruleHash = rule?.hashCode() ?: ruleHash,
                        ruleOverride = session.rule,
                    ),
                    coroutineContext = coroutineContext,
                )
            }

            val source = ReadBook.bookSource ?: return@async null
            if (source.getKey() != sourceKey) return@async null
            val book = ReadBook.book ?: return@async null
            if (book.bookUrl != bookUrl) return@async null
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                ?: return@async null
            ReviewLoader.loadReplies(
                ReviewLoader.ReplyRequest(
                    source = source,
                    book = book,
                    chapter = chapter,
                    paragraphIndex = paragraphNum,
                    paragraphData = paragraphData,
                    reviewId = reviewId,
                    page = page,
                    ruleHash = ruleHash,
                ),
                coroutineContext = coroutineContext,
            )
'''
block = block[:request_start] + new_request + block[request_end:]
text = text[:start] + block + text[end:]

# Remove the dialog-local transport result; ReviewLoader owns it now.
local_result = '''    private data class ReplyResult(
        val replies: List<ReviewDetailItem>,
        val page: Int,
        val source: BaseSource,
    )

'''
text = text.replace(local_result, '', 1)

# These imports were only used by the old inline reply transport.
text = text.replace('import io.legado.app.model.jsSource.JsSourceReview\n', '')

path.write_text(text, encoding='utf-8')
