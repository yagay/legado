package io.legado.app.ui.main.explore.modern

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.CoverDisplayResolver
import io.legado.app.help.CoverThumbnailCache
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.ui.main.explore.DiscoverySuiteWidget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetType
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * 现代套件封面预加载器。
 *
 * 封面路径解析、网络加载与缩略图缓存继续复用上游实现；这里只负责现代套件
 * 的预取数量、并发与超时策略。
 */
internal object ModernDiscoveryCoverPreloader {

    private val visibleCoverSemaphore = Semaphore(SUITE_COVER_PRELOAD_PARALLELISM)

    fun prefetch(context: Context, books: List<SearchBook>) {
        if (books.isEmpty()) return
        val appContext = context.applicationContext
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .disallowHardwareConfig()
            .set(OkHttpModelLoader.loadOnlyWifiOption, AppConfig.loadCoverOnlyWifi)
        books.asSequence()
            .distinctBy {
                it.origin + "|" + it.coverUrl.orEmpty() + "|" + it.name + "|" + it.author
            }
            .take(RANDOM_SUITE_COVER_PREFETCH_COUNT)
            .forEach { book ->
                val display = CoverDisplayResolver.resolve(book)
                if (AppConfig.useDefaultCover && !display.forcePath) return@forEach
                val path = display.path?.takeIf { it.isNotBlank() } ?: return@forEach
                val requestOptions = options.clone()
                display.sourceOrigin?.let { origin ->
                    requestOptions.set(OkHttpModelLoader.sourceOriginOption, origin)
                }
                ImageLoader.load(appContext, path)
                    .apply(requestOptions)
                    .priority(Priority.LOW)
                    .override(SUITE_COVER_THUMB_WIDTH, SUITE_COVER_THUMB_HEIGHT)
                    .centerCrop()
                    .preload(SUITE_COVER_THUMB_WIDTH, SUITE_COVER_THUMB_HEIGHT)
            }
    }

    suspend fun preloadVisible(
        context: Context,
        widget: DiscoverySuiteWidget,
        books: List<SearchBook>
    ) {
        if (books.isEmpty()) return
        val visibleCount = when (widget.type) {
            DiscoverySuiteWidgetType.HorizontalBooks.value -> HORIZONTAL_SUITE_VISIBLE_COVER_COUNT
            DiscoverySuiteWidgetType.RankedList.value -> RANKED_SUITE_VISIBLE_COVER_COUNT
            DiscoverySuiteWidgetType.WaterfallBooks.value -> WATERFALL_SUITE_VISIBLE_COVER_COUNT
            else -> RANDOM_SUITE_BOOK_COUNT
        }
        withTimeoutOrNull(SUITE_VISIBLE_COVER_PRELOAD_TIMEOUT_MS) {
            coroutineScope {
                books.take(visibleCount).map { book ->
                    async(IO) {
                        visibleCoverSemaphore.withPermit {
                            preloadBlocking(context, book)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun preloadBlocking(context: Context, book: SearchBook) {
        val display = CoverDisplayResolver.resolve(book)
        if (AppConfig.useDefaultCover && !display.forcePath) return
        val path = display.path?.takeIf { it.isNotBlank() } ?: return
        val cleanName = display.name?.replace(AppPattern.bdRegex, "")?.trim()
        val cleanAuthor = display.author?.replace(AppPattern.bdRegex, "")?.trim()
        val thumbKey = display.sourceOrigin + "|" + path + "|" + cleanName + "|" + cleanAuthor
        val thumbFile = CoverThumbnailCache.existing(context, thumbKey)
        var options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .disallowHardwareConfig()
            .set(OkHttpModelLoader.loadOnlyWifiOption, AppConfig.loadCoverOnlyWifi)
        display.sourceOrigin?.let { origin ->
            options = options.set(OkHttpModelLoader.sourceOriginOption, origin)
        }
        val request = if (thumbFile != null) {
            ImageLoader.load(context, thumbFile)
        } else {
            ImageLoader.load(context, path)
        }
        val target = request
            .apply(options)
            .priority(Priority.HIGH)
            .override(SUITE_COVER_THUMB_WIDTH, SUITE_COVER_THUMB_HEIGHT)
            .centerCrop()
            .submit(SUITE_COVER_THUMB_WIDTH, SUITE_COVER_THUMB_HEIGHT)
        try {
            val drawable = target.get(
                SUITE_SINGLE_COVER_PRELOAD_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
            if (thumbFile == null) {
                CoverThumbnailCache.saveBlocking(context, thumbKey, drawable)
            }
        } catch (_: Throwable) {
            // Network or malformed cover failures should not block the widget refresh.
        } finally {
            runCatching { Glide.with(context).clear(target) }
        }
    }

    private const val SUITE_COVER_PRELOAD_PARALLELISM = 4
    private const val RANDOM_SUITE_BOOK_COUNT = 6
    private const val RANDOM_SUITE_COVER_PREFETCH_COUNT = 18
    private const val HORIZONTAL_SUITE_VISIBLE_COVER_COUNT = 3
    private const val RANKED_SUITE_VISIBLE_COVER_COUNT = 12
    private const val WATERFALL_SUITE_VISIBLE_COVER_COUNT = 8
    private const val SUITE_COVER_THUMB_WIDTH = 240
    private const val SUITE_COVER_THUMB_HEIGHT = 320
    private const val SUITE_SINGLE_COVER_PRELOAD_TIMEOUT_MS = 1000L
    private const val SUITE_VISIBLE_COVER_PRELOAD_TIMEOUT_MS = 1800L
}
