from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = path.read_text(encoding='utf-8')

# Remove imports used only by the legacy ReadBook/appDb fallback path.
for line in [
    'import io.legado.app.data.appDb\n',
    'import io.legado.app.model.ReadBook\n',
]:
    text = text.replace(line, '')

# Remove the legacy constructor that passed primitive identifiers instead of ReviewContext.
legacy_ctor_start = text.find('    constructor(\n        paragraphNum: Int,')
context_ctor_start = text.find('    constructor(\n        reviewContext: ReviewContext,')
if legacy_ctor_start < 0 or context_ctor_start < 0 or context_ctor_start <= legacy_ctor_start:
    raise SystemExit('legacy/context constructor boundaries not found')
text = text[:legacy_ctor_start] + text[context_ctor_start:]

# Remove state only required by the legacy constructor/fallback.
for line in [
    '    private var paragraphNum: Int = 0\n',
    '    private var chapterIndex: Int = 0\n',
    '    private var paragraphData: String = ""\n',
    '    private var bookUrl: String = ""\n',
]:
    text = text.replace(line, '')

# Remove legacy argument reads.
for line in [
    '        paragraphNum = arguments?.getInt(ARG_PARAGRAPH_NUM) ?: 0\n',
    '        chapterIndex = arguments?.getInt(ARG_CHAPTER_INDEX) ?: 0\n',
    '        paragraphData = arguments?.getString(ARG_PARAGRAPH_DATA).orEmpty()\n',
    '        bookUrl = arguments?.getString(ARG_BOOK_URL).orEmpty()\n',
]:
    text = text.replace(line, '')

# The dialog is now always session-backed, so pagination no longer needs paragraphNum.
text = text.replace(
    '                    loadDetailPage(paragraphNum, currentPage + 1, append = true)\n',
    '                    loadDetailPage(currentPage + 1, append = true)\n',
)
text = text.replace(
    '        loadDetailPage(paragraphNum, 1, append = false)\n',
    '        loadDetailPage(1, append = false)\n',
)
text = text.replace(
    '    private fun loadDetailPage(paragraphNum: Int, page: Int, append: Boolean) {\n',
    '    private fun loadDetailPage(page: Int, append: Boolean) {\n',
)

# Replace detail request transport with mandatory session path.
old_detail = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
            val session = ReviewDialogSessionStore.get(reviewSessionId)
            if (session != null) {
                val reviewContext = session.context
                val source = reviewContext.source
                val rule = session.rule ?: source.ruleReview
                return@async ReviewLoader.loadDetail(
                    ReviewLoader.DetailRequest(
                        source = source,
                        book = reviewContext.book,
                        chapter = reviewContext.chapterForAnalyze(),
                        paragraphIndex = reviewContext.paragraphIndexForAnalyze(),
                        paragraphData = reviewContext.paragraphDataForAnalyze(),
                        page = page,
                        ruleHash = rule?.hashCode() ?: ruleHash,
                        nextPageUrl = nextPageUrl,
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
'''
new_detail = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
            val session = ReviewDialogSessionStore.get(reviewSessionId) ?: return@async null
            val reviewContext = session.context
            val source = reviewContext.source
            val rule = session.rule ?: source.ruleReview
            ReviewLoader.loadDetail(
                ReviewLoader.DetailRequest(
                    source = source,
                    book = reviewContext.book,
                    chapter = reviewContext.chapterForAnalyze(),
                    paragraphIndex = reviewContext.paragraphIndexForAnalyze(),
                    paragraphData = reviewContext.paragraphDataForAnalyze(),
                    page = page,
                    ruleHash = rule?.hashCode() ?: ruleHash,
                    nextPageUrl = nextPageUrl,
                    ruleOverride = session.rule,
                ),
                coroutineContext = coroutineContext,
            )
'''
if old_detail not in text:
    raise SystemExit('legacy detail transport block not found')
text = text.replace(old_detail, new_detail, 1)

# Replace reply request transport with mandatory session path.
old_reply = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
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
new_reply = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
            val session = ReviewDialogSessionStore.get(reviewSessionId) ?: return@async null
            val reviewContext = session.context
            val source = reviewContext.source
            val rule = session.rule ?: source.ruleReview
            ReviewLoader.loadReplies(
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
'''
if old_reply not in text:
    raise SystemExit('legacy reply transport block not found')
text = text.replace(old_reply, new_reply, 1)

# Drop constants only used by the removed constructor. Keep source/rule/session-related args.
for line in [
    '        private const val ARG_PARAGRAPH_NUM = "paragraphNum"\n',
    '        private const val ARG_CHAPTER_INDEX = "chapterIndex"\n',
    '        private const val ARG_PARAGRAPH_DATA = "paragraphData"\n',
    '        private const val ARG_BOOK_URL = "bookUrl"\n',
]:
    text = text.replace(line, '')

path.write_text(text, encoding='utf-8')
