package io.legado.app.ui.main.explore.modern

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook

/**
 * 现代发现页的数据入口。
 *
 * 网络解析继续直接调用上游 WebBook，搜索结果缓存继续使用上游 AppDatabase；
 * 现代页面不复制书源解析、请求或数据库实现。
 */
internal object ModernDiscoveryDataRepository {

    suspend fun loadExplorePage(
        source: BookSource,
        tagUrl: String,
        page: Int
    ): List<SearchBook> {
        return WebBook.exploreBookAwait(source, tagUrl, page)
    }

    suspend fun persistSearchBooks(books: List<SearchBook>) {
        if (books.isEmpty()) return
        appDb.searchBookDao.insert(*books.toTypedArray())
    }
}
