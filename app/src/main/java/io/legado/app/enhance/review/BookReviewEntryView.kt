package io.legado.app.enhance.review

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReviewDetailDialog
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Book detail entry for book-level reviews.
 *
 * This view intentionally lives in enhance/ so BookInfoActivity and ReviewRule stay as close to
 * upstream as possible. A book-level review rule is distinguished from paragraph reviews by not
 * declaring summaryParagraphIndexRule. Existing paragraph-review rules therefore keep their
 * current reader-only behaviour.
 *
 * ReviewDetailDialog currently resolves its book/source from ReadBook. Until its loader is
 * separated from the dialog, this view scopes those values to the lifetime of the dialog and
 * restores the previous values when the dialog is destroyed. The actual review parsing, paging,
 * replies, image and audio UI remain fully reused from upstream.
 */
class BookReviewEntryView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var boundBook: Book? = null
    private var boundSource: BookSource? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        setPadding(0, 3.dpToPx(), 0, 3.dpToPx())

        addView(ImageView(context).apply {
            layoutParams = LayoutParams(18.dpToPx(), 18.dpToPx()).apply {
                marginEnd = 6.dpToPx()
            }
            setImageResource(R.drawable.ic_book_review)
            contentDescription = context.getString(R.string.book_review)
            setColorFilter(context.getCompatColor(R.color.tv_text_summary), PorterDuff.Mode.SRC_IN)
        })

        addView(TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6.dpToPx()
            }
            text = context.getString(R.string.book_review)
            setTextColor(context.getCompatColor(R.color.tv_text_summary))
            textSize = 13f
            includeFontPadding = false
            maxLines = 1
        })

        addView(AccentBgTextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 8.dpToPx()
            }
            setPadding(5.dpToPx(), 0, 5.dpToPx(), 0)
            text = context.getString(R.string.view_book_review)
            textSize = 13f
            setRadius(2)
            setOnClickListener { openBookReview() }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resolveBookAndSource()
    }

    private fun resolveBookAndSource() {
        val activity = context.findActivity() ?: return
        activity.lifecycleScope.launch(IO) {
            val intent = activity.intent
            val name = intent.getStringExtra("name").orEmpty()
            val author = intent.getStringExtra("author").orEmpty()
            val bookUrl = intent.getStringExtra("bookUrl").orEmpty()

            val book = appDb.bookDao.getBook(name, author)
                ?: bookUrl.takeIf { it.isNotBlank() }?.let { appDb.bookDao.getBook(it) }
                ?: bookUrl.takeIf { it.isNotBlank() }
                    ?.let { appDb.searchBookDao.getSearchBook(it)?.toBook() }
                ?: appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()
                ?: return@launch

            val source = if (book.isLocal) null else appDb.bookSourceDao.getBookSource(book.origin)
            withContext(Main) {
                bind(book, source)
            }
        }
    }

    private fun bind(book: Book, source: BookSource?) {
        val rule = source?.ruleReview
        val hasBookReview = rule?.enabled == true &&
            !rule.reviewDetailUrl.isNullOrBlank() &&
            !rule.detailListRule.isNullOrBlank() &&
            !rule.detailContentRule.isNullOrBlank() &&
            rule.summaryParagraphIndexRule.isNullOrBlank()

        if (!hasBookReview) {
            boundBook = null
            boundSource = null
            visibility = View.GONE
            return
        }

        boundBook = book
        boundSource = source
        visibility = View.VISIBLE
    }

    private fun openBookReview() {
        val activity = context.findActivity() ?: return
        val book = boundBook ?: return
        val source = boundSource ?: return
        val rule = source.ruleReview ?: return

        activity.lifecycleScope.launch(IO) {
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, 0)
            withContext(Main) {
                if (chapter == null) {
                    Toast.makeText(context, R.string.loading, Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val previousBook = ReadBook.book
                val previousSource = ReadBook.bookSource
                ReadBook.book = book
                ReadBook.bookSource = source

                val dialog = ReviewDetailDialog(
                    paragraphNum = BOOK_REVIEW_INDEX,
                    totalCount = 0,
                    chapterIndex = chapter.index,
                    paragraphData = "",
                    bookUrl = book.bookUrl,
                    sourceKey = source.getKey(),
                    ruleHash = rule.hashCode(),
                )
                val manager = activity.supportFragmentManager
                val callback = object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                        if (f !== dialog) return
                        if (ReadBook.book === book) ReadBook.book = previousBook
                        if (ReadBook.bookSource === source) ReadBook.bookSource = previousSource
                        fm.unregisterFragmentLifecycleCallbacks(this)
                    }
                }
                manager.registerFragmentLifecycleCallbacks(callback, false)
                runCatching {
                    dialog.show(manager, TAG)
                }.onFailure {
                    manager.unregisterFragmentLifecycleCallbacks(callback)
                    if (ReadBook.book === book) ReadBook.book = previousBook
                    if (ReadBook.bookSource === source) ReadBook.bookSource = previousSource
                    throw it
                }
            }
        }
    }

    private tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
        is AppCompatActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    companion object {
        /** Reserved non-paragraph index used only by the enhance BookReview bridge. */
        private const val BOOK_REVIEW_INDEX = -1
        private const val TAG = "book_review_detail"
    }
}
