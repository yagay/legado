from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt')
text = path.read_text(encoding='utf-8')

start_marker = '    override fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int) {'
end_marker = '    private fun loadReviewSummaryIfNeeded() {'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('onReviewClick boundaries not found')

new_func = '''    override fun onReviewClick(paragraphNum: Int, count: Int, chapterIndex: Int) {
        if (paragraphNum != -1 && paragraphNum <= 0) return
        if (count <= 0) {
            toastOnUi(R.string.review_empty)
            return
        }
        val source = ReadBook.bookSource ?: return
        val book = ReadBook.book ?: return
        val reviewData = ChapterProvider.getReviewKeyById(paragraphNum, chapterIndex).orEmpty()

        val rule = if (source.isJsSource()) {
            null
        } else {
            source.ruleReview ?: run {
                toastOnUi(R.string.review_rule_missing)
                return
            }
        }
        if (rule != null) {
            if (!rule.enabled) {
                toastOnUi(R.string.review_rule_missing)
                return
            }
            if (rule.reviewDetailUrl.isNullOrBlank()) {
                toastOnUi(R.string.review_detail_url_missing)
                return
            }
            if (rule.detailListRule.isNullOrBlank() || rule.detailContentRule.isNullOrBlank()) {
                toastOnUi(R.string.review_detail_rule_missing)
                return
            }
        }

        lifecycleScope.launch {
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            } ?: return@launch
            val reviewContext = if (paragraphNum == -1) {
                ReviewContext.ChapterReview(
                    source = source,
                    book = book,
                    chapter = chapter,
                    reviewData = reviewData,
                )
            } else {
                ReviewContext.ParagraphReview(
                    source = source,
                    book = book,
                    chapter = chapter,
                    paragraphIndex = paragraphNum,
                    paragraphData = reviewData,
                )
            }
            showDialogFragment(
                ReviewDetailDialog(
                    reviewContext = reviewContext,
                    rule = rule,
                    totalCount = count,
                )
            )
        }
    }

'''
text = text[:start] + new_func + text[end:]
path.write_text(text, encoding='utf-8')
