package io.legado.app.enhance.review

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ReviewRule
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
 * Capability resolution is centralized in ReviewCapabilityResolver. BookInfoActivity can bind the
 * current book/source directly after refresh or source changes; the attach-time DB lookup remains
 * only as a compatibility fallback for callers that have not bound explicitly yet.
 */
class BookReviewEntryView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var boundBook: Book? = null
    private var boundSource: BookSource? = null
    private var boundRule: ReviewRule? = null
    private var explicitlyBound = false

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
        if (!explicitlyBound) resolveBookAndSource()
    }

    fun bind(book: Book, source: BookSource?) {
        explicitlyBound = true
        val effectiveRule = ReviewCapabilityResolver.resolveBookReview(source, book)

        if (source == null || effectiveRule == null) {
            boundBook = null
            boundSource = null
            boundRule = null
            visibility = View.GONE
            return
        }

        boundBook = book
        boundSource = source
        boundRule = effectiveRule
        visibility = View.VISIBLE
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

            val source = appDb.bookSourceDao.getBookSource(book.origin)
            withContext(Main) {
                if (!explicitlyBound) bind(book, source)
            }
        }
    }

    private fun openBookReview() {
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

    private tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
        is AppCompatActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    companion object {
        private const val TAG = "book_review_detail"
    }
}
