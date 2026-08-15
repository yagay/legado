from pathlib import Path

# Patch ReviewDetailDialog.kt
path = Path('app/src/main/java/io/legado/app/ui/book/read/ReviewDetailDialog.kt')
text = path.read_text(encoding='utf-8')

imports = [
    'import io.legado.app.enhance.review.ReviewContext\n',
    'import io.legado.app.enhance.review.ReviewDialogSessionStore\n',
    'import io.legado.app.enhance.review.chapterForAnalyze\n',
    'import io.legado.app.enhance.review.paragraphDataForAnalyze\n',
    'import io.legado.app.enhance.review.paragraphIndexForAnalyze\n',
]
anchor = 'import io.legado.app.enhance.review.ReviewLoader\n'
for imp in reversed(imports):
    if imp not in text:
        text = text.replace(anchor, anchor + imp, 1)

ctor_anchor = '    private val binding by viewBinding(DialogRecyclerViewBinding::bind)\n'
if 'constructor(reviewContext: ReviewContext' not in text:
    ctor = '''    constructor(
        reviewContext: ReviewContext,
        rule: io.legado.app.data.entities.rule.ReviewRule? = null,
        totalCount: Int = 0,
    ) : this() {
        val sessionId = ReviewDialogSessionStore.put(reviewContext, rule)
        arguments = Bundle().apply {
            putString("reviewSessionId", sessionId)
            putInt(ARG_TOTAL_COUNT, totalCount)
            putString(ARG_SOURCE_KEY, reviewContext.source.getKey())
            putInt(ARG_RULE_HASH, (rule ?: reviewContext.source.ruleReview)?.hashCode() ?: 0)
        }
    }

'''
    text = text.replace(ctor_anchor, ctor + ctor_anchor, 1)

field_anchor = '    private var ruleHash: Int = 0\n'
if 'private var reviewSessionId: String = ""' not in text:
    text = text.replace(field_anchor, field_anchor + '    private var reviewSessionId: String = ""\n', 1)

init_anchor = '        ruleHash = arguments?.getInt(ARG_RULE_HASH) ?: 0\n'
if 'getString("reviewSessionId")' not in text:
    text = text.replace(init_anchor, init_anchor + '        reviewSessionId = arguments?.getString("reviewSessionId").orEmpty()\n', 1)

start_marker = '    private fun loadDetailPage(paragraphNum: Int, page: Int, append: Boolean) {'
end_marker = '    private fun loadReplies(parentKey: String) {'
start = text.find(start_marker)
end = text.find(end_marker, start)
block = text[start:end]
old = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
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
'''
new = '''        Coroutine.async(lifecycleScope, IO, start = CoroutineStart.LAZY) {
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
        }.onSuccess(Main) { result ->
'''
if old not in block:
    raise SystemExit('ReviewDetailDialog loadDetailPage request block not found')
block = block.replace(old, new, 1)
text = text[:start] + block + text[end:]

destroy_old = '''    override fun onDestroyView() {
        releaseAudioPlayer()
        super.onDestroyView()
    }
'''
destroy_new = '''    override fun onDestroyView() {
        releaseAudioPlayer()
        ReviewDialogSessionStore.remove(reviewSessionId)
        super.onDestroyView()
    }
'''
if destroy_old in text:
    text = text.replace(destroy_old, destroy_new, 1)

path.write_text(text, encoding='utf-8')

# Patch BookReviewEntryView.kt to remove ReadBook mutation and synthetic chapter requirement.
path = Path('app/src/main/java/io/legado/app/enhance/review/BookReviewEntryView.kt')
text = path.read_text(encoding='utf-8')
for imp in [
    'import android.widget.Toast\n',
    'import androidx.fragment.app.Fragment\n',
    'import androidx.fragment.app.FragmentManager\n',
    'import io.legado.app.model.ReadBook\n',
]:
    text = text.replace(imp, '')

start_marker = '    private fun openBookReview() {'
end_marker = '    private tailrec fun Context.findActivity()'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('BookReviewEntryView openBookReview boundaries not found')
new_open = '''    private fun openBookReview() {
        val activity = context.findActivity() ?: return
        val book = boundBook ?: return
        val source = boundSource ?: return
        val rule = boundRule ?: return

        ReviewDetailDialog(
            reviewContext = ReviewContext.BookReview(source, book),
            rule = rule,
            totalCount = 0,
        ).show(activity.supportFragmentManager, TAG)
    }

'''
text = text[:start] + new_open + text[end:]
text = text.replace('        /** Reserved non-paragraph index used only by the enhance BookReview bridge. */\n        private const val BOOK_REVIEW_INDEX = -1\n', '')
text = text.replace(' * ReviewDetailDialog currently resolves its book/source/rule from ReadBook and BookSource. Until\n * its loader is separated from the dialog, this view scopes those values to the dialog lifetime\n * and restores them when the dialog is destroyed. Parsing, paging, replies, image and audio UI are\n * still provided by the existing review implementation.\n', ' * The dialog receives an explicit ReviewContext, so opening book reviews does not mutate ReadBook\n * or the persisted/in-memory BookSource rule. Parsing, paging, image and audio UI stay shared.\n')
path.write_text(text, encoding='utf-8')
