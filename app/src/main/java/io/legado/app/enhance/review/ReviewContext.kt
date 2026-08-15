package io.legado.app.enhance.review

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource

/**
 * Runtime-only review scope. This deliberately stays outside BookSource/ReviewRule so importing
 * existing sources and merging upstream entity/schema changes remain unaffected.
 */
sealed interface ReviewContext {

    val source: BookSource
    val book: Book

    data class BookReview(
        override val source: BookSource,
        override val book: Book,
    ) : ReviewContext

    data class ChapterReview(
        override val source: BookSource,
        override val book: Book,
        val chapter: BookChapter,
        val reviewData: String = "",
    ) : ReviewContext

    data class ParagraphReview(
        override val source: BookSource,
        override val book: Book,
        val chapter: BookChapter,
        val paragraphIndex: Int,
        val paragraphData: String,
    ) : ReviewContext
}

internal fun ReviewContext.chapterForAnalyze(): BookChapter {
    return when (this) {
        is ReviewContext.BookReview -> BookChapter(
            url = book.bookUrl,
            baseUrl = book.bookUrl,
            bookUrl = book.bookUrl,
            index = 0,
        )

        is ReviewContext.ChapterReview -> chapter
        is ReviewContext.ParagraphReview -> chapter
    }
}

internal fun ReviewContext.paragraphIndexForAnalyze(): Int {
    return when (this) {
        is ReviewContext.BookReview -> -1
        is ReviewContext.ChapterReview -> -1
        is ReviewContext.ParagraphReview -> paragraphIndex
    }
}

internal fun ReviewContext.paragraphDataForAnalyze(): String {
    return when (this) {
        is ReviewContext.ChapterReview -> reviewData
        is ReviewContext.ParagraphReview -> paragraphData
        else -> ""
    }
}
