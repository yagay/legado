package io.legado.app.ui.main.bookshelf.compose

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import kotlin.math.max

/**
 * Compose 书架专用投影模型。
 *
 * 不放入上游 BookDao，避免界面模型扩大数据库接口。
 */
data class BookShelfDisplay(
    val bookUrl: String,
    val origin: String,
    val originName: String,
    val name: String,
    val author: String,
    val intro: String?,
    val customIntro: String?,
    val customTag: String?,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val type: Int,
    val group: Long,
    val latestChapterTitle: String?,
    val latestChapterTime: Long,
    val lastCheckCount: Int,
    val totalChapterNum: Int,
    val durChapterTitle: String?,
    val durChapterIndex: Int,
    val durChapterTime: Long,
    val canUpdate: Boolean,
    val order: Int,
    val readConfig: Book.ReadConfig?
) {
    val isLocal: Boolean get() = type and BookType.local > 0
    val isAudio: Boolean get() = type and BookType.audio > 0
    val isVideo: Boolean get() = type and BookType.video > 0
    val isImage: Boolean get() = type and BookType.image > 0

    fun getDisplayCover(): String? =
        customCoverUrl?.takeIf { it.isNotEmpty() } ?: coverUrl

    fun getDisplayIntro(): String? =
        customIntro?.takeIf { it.isNotEmpty() } ?: intro

    fun getUnreadChapterNum(): Int =
        max(totalChapterNum - durChapterIndex - 1, 0)
}
