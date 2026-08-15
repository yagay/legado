from pathlib import Path

# 1) Route paragraphNum == -1 through explicit ChapterReview context.
path = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt')
text = path.read_text(encoding='utf-8')
import_line = 'import io.legado.app.enhance.review.ReviewContext\n'
anchor = 'import io.legado.app.exception.NoStackTraceException\n'
if import_line not in text:
    text = text.replace(anchor, import_line + anchor, 1)

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

        if (source.isJsSource()) {
            if (paragraphNum == -1) {
                lifecycleScope.launch {
                    val chapter = withContext(IO) {
                        appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                    } ?: return@launch
                    showDialogFragment(
                        ReviewDetailDialog(
                            reviewContext = ReviewContext.ChapterReview(
                                source = source,
                                book = book,
                                chapter = chapter,
                                reviewData = reviewData,
                            ),
                            totalCount = count,
                        )
                    )
                }
            } else {
                showDialogFragment(
                    ReviewDetailDialog(
                        paragraphNum = paragraphNum,
                        totalCount = count,
                        chapterIndex = chapterIndex,
                        paragraphData = reviewData,
                        bookUrl = book.bookUrl,
                        sourceKey = source.getKey(),
                        ruleHash = source.mainJs.hashCode(),
                    )
                )
            }
            return
        }
        val rule = source.ruleReview ?: run {
            toastOnUi(R.string.review_rule_missing)
            return
        }
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

        if (paragraphNum == -1) {
            lifecycleScope.launch {
                val chapter = withContext(IO) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                } ?: return@launch
                showDialogFragment(
                    ReviewDetailDialog(
                        reviewContext = ReviewContext.ChapterReview(
                            source = source,
                            book = book,
                            chapter = chapter,
                            reviewData = reviewData,
                        ),
                        rule = rule,
                        totalCount = count,
                    )
                )
            }
            return
        }

        showDialogFragment(
            ReviewDetailDialog(
                paragraphNum = paragraphNum,
                totalCount = count,
                chapterIndex = chapterIndex,
                paragraphData = reviewData,
                bookUrl = book.bookUrl,
                sourceKey = source.getKey(),
                ruleHash = rule.hashCode()
            )
        )
    }

'''
text = text[:start] + new_func + text[end:]
path.write_text(text, encoding='utf-8')

# 2) Explicit review sessions need JS mainJs hash when no native rule exists.
path = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = path.read_text(encoding='utf-8')
old = '            putInt(ARG_RULE_HASH, (rule ?: reviewContext.source.ruleReview)?.hashCode() ?: 0)\n'
new = '''            putInt(
                ARG_RULE_HASH,
                when {
                    rule != null -> rule.hashCode()
                    reviewContext.source.isJsSource() -> reviewContext.source.mainJs.hashCode()
                    else -> reviewContext.source.ruleReview?.hashCode() ?: 0
                }
            )
'''
if old not in text:
    raise SystemExit('ReviewDetailDialog session hash line not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
