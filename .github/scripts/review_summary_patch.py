from pathlib import Path

path = Path('app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt')
text = path.read_text(encoding='utf-8')

import_line = 'import io.legado.app.enhance.review.ReviewLoader\n'
anchor = 'import io.legado.app.enhance.review.ReviewContext\n'
if import_line not in text:
    text = text.replace(anchor, anchor + import_line, 1)

start_marker = '    private fun loadReviewSummaryIfNeeded() {'
end_marker = '    /**\n     * 朗读按钮\n     */'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('review summary block boundaries not found')

new_block = '''    private fun loadReviewSummaryIfNeeded() {
        val source = ReadBook.bookSource ?: run {
            clearReviewSummaryProviders()
            return
        }
        val book = ReadBook.book ?: run {
            clearReviewSummaryProviders()
            return
        }
        val chapterIndex = ReadBook.durChapterIndex
        val textChapter = ReadBook.curTextChapter
        if (textChapter != null &&
            textChapter.chapter.index == chapterIndex &&
            !textChapter.hasBodyContent
        ) {
            clearReviewSummaryProviders()
            return
        }

        val reviewHash = reviewSummaryHash(source) ?: run {
            clearReviewSummaryProviders()
            return
        }
        val key = buildReviewSummaryKey(book, source, reviewHash, chapterIndex)
        if (reviewSummaryAppliedKey == key || reviewSummaryLoadingKey == key) return
        synchronized(reviewSummaryCache) { reviewSummaryCache[key] }?.let { cached ->
            applyReviewSummary(key, chapterIndex, cached)
            prefetchAdjacentReviewSummary(book, source, reviewHash, chapterIndex)
            return
        }

        reviewSummaryLoadingKey = key
        val requestToken = ++reviewSummaryRequestToken
        if (reviewSummaryAppliedKey != key) {
            ChapterProvider.clearReviewProviders()
        }
        Coroutine.async(lifecycleScope, IO) {
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                ?: return@async null
            ReviewLoader.loadSummary(
                ReviewLoader.SummaryRequest(
                    source = source,
                    book = book,
                    chapter = chapter,
                    ruleHash = reviewHash,
                ),
                coroutineContext = coroutineContext,
            )?.summary
        }.onSuccess(Main) { result ->
            releaseReviewSummaryLoadingKey(key)
            if (requestToken != reviewSummaryRequestToken) return@onSuccess
            val currentBook = ReadBook.book ?: return@onSuccess
            val currentSource = ReadBook.bookSource ?: return@onSuccess
            val currentHash = reviewSummaryHash(currentSource) ?: return@onSuccess
            val currentKey = buildReviewSummaryKey(
                currentBook,
                currentSource,
                currentHash,
                ReadBook.durChapterIndex,
            )
            if (currentKey != key) return@onSuccess
            if (result == null) {
                reviewSummaryAppliedKey = key
                ChapterProvider.clearReviewProviders()
                return@onSuccess
            }
            synchronized(reviewSummaryCache) {
                reviewSummaryCache[key] = result
            }
            applyReviewSummary(key, chapterIndex, result)
            prefetchAdjacentReviewSummary(book, source, reviewHash, chapterIndex)
        }.onError {
            releaseReviewSummaryLoadingKey(key)
            if (requestToken != reviewSummaryRequestToken) return@onError
            val currentBook = ReadBook.book ?: return@onError
            val currentSource = ReadBook.bookSource ?: return@onError
            val currentHash = reviewSummaryHash(currentSource) ?: return@onError
            if (buildReviewSummaryKey(
                    currentBook,
                    currentSource,
                    currentHash,
                    ReadBook.durChapterIndex,
                ) != key
            ) return@onError
            ChapterProvider.clearReviewProviders()
            AppLog.put("加载评论统计出错\\n${it.localizedMessage}", it)
        }
    }

    private fun reviewSummaryHash(source: BookSource): Int? {
        if (source.isJsSource()) return source.mainJs.hashCode()
        val rule = source.ruleReview ?: return null
        if (!rule.enabled || rule.configuredSummaryUrl() == null) return null
        return rule.hashCode()
    }

    private fun clearReviewSummaryProviders() {
        reviewSummaryRequestToken++
        reviewSummaryAppliedKey = null
        reviewSummaryLoadingKey = null
        ChapterProvider.clearReviewProviders()
    }

    private fun applyReviewSummary(
        key: String,
        chapterIndex: Int,
        result: ReviewRuleParser.SummaryResult
    ) {
        ChapterProvider.setReviewProviders(
            countProvider = { targetChapterIndex, reviewId ->
                if (targetChapterIndex == chapterIndex) result.counts[reviewId] ?: 0 else 0
            },
            keyProvider = { targetChapterIndex, reviewId ->
                if (targetChapterIndex == chapterIndex) result.keys[reviewId] else null
            },
            chapterIndex = chapterIndex,
        )
        reviewSummaryAppliedKey = key
        binding.readView.upContent(relativePosition = 0, resetPageOffset = false)
    }

    private fun prefetchAdjacentReviewSummary(
        book: Book,
        source: BookSource,
        reviewHash: Int,
        chapterIndex: Int,
    ) {
        val maxIndex = if (ReadBook.simulatedChapterSize > 0) {
            ReadBook.simulatedChapterSize
        } else {
            ReadBook.chapterSize
        }
        if (maxIndex <= 0) return

        val requestToken = reviewSummaryRequestToken
        for (targetIndex in intArrayOf(chapterIndex - 1, chapterIndex + 1)) {
            if (targetIndex !in 0 until maxIndex) continue
            val loadedChapter = sequenceOf(
                ReadBook.prevTextChapter,
                ReadBook.curTextChapter,
                ReadBook.nextTextChapter,
            ).filterNotNull().firstOrNull { it.chapter.index == targetIndex }
            if (loadedChapter == null || !loadedChapter.hasBodyContent) continue

            val key = buildReviewSummaryKey(book, source, reviewHash, targetIndex)
            if (reviewSummaryLoadingKey == key) continue
            if (synchronized(reviewSummaryCache) { reviewSummaryCache.containsKey(key) }) continue
            val shouldPrefetch = synchronized(reviewSummaryPrefetchingKeys) {
                reviewSummaryPrefetchingKeys.add(key)
            }
            if (!shouldPrefetch) continue

            Coroutine.async(lifecycleScope, IO) {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, targetIndex)
                    ?: return@async null
                ReviewLoader.loadSummary(
                    ReviewLoader.SummaryRequest(
                        source = source,
                        book = book,
                        chapter = chapter,
                        ruleHash = reviewHash,
                    ),
                    coroutineContext = coroutineContext,
                )?.summary
            }.onSuccess(Main) { result ->
                synchronized(reviewSummaryPrefetchingKeys) {
                    reviewSummaryPrefetchingKeys.remove(key)
                }
                if (requestToken != reviewSummaryRequestToken || result == null) return@onSuccess
                synchronized(reviewSummaryCache) {
                    reviewSummaryCache[key] = result
                }
            }.onError {
                synchronized(reviewSummaryPrefetchingKeys) {
                    reviewSummaryPrefetchingKeys.remove(key)
                }
            }
        }
    }

    private fun buildReviewSummaryKey(
        book: Book,
        source: BaseSource,
        reviewHash: Int,
        chapterIndex: Int
    ): String = "${source.getKey()}|${book.bookUrl}|$reviewHash#$chapterIndex"

    private fun releaseReviewSummaryLoadingKey(key: String) {
        if (reviewSummaryLoadingKey == key) {
            reviewSummaryLoadingKey = null
        }
    }


'''
text = text[:start] + new_block + text[end:]
path.write_text(text, encoding='utf-8')
