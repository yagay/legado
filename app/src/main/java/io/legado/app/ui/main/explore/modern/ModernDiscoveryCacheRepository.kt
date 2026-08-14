package io.legado.app.ui.main.explore.modern

import android.database.sqlite.SQLiteBlobTooBigException
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Cache
import io.legado.app.ui.main.explore.DiscoveryCachePolicy
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject

/**
 * 现代发现页独立缓存仓库。
 *
 * Fragment 只负责生命周期与应用缓存结果；数据库边界、大小限制、序列化和缓存键
 * 全部留在现代模块，避免继续侵入上游 ExploreFragment。
 */
internal object ModernDiscoveryCacheRepository {

    fun readSuiteSnapshot(
        suiteId: String,
        signature: String
    ): DiscoverySuitePageSnapshot? {
        val key = suiteSnapshotKey(suiteId, signature)
        val raw = readBounded(key, "套件发现缓存") ?: return null
        val snapshot = GSON.fromJsonObject<DiscoverySuitePageSnapshot>(raw)
            .getOrNull()
            ?.takeIf { it.suiteId == suiteId && it.signature == signature }
            ?.compactForCache()
            ?.takeIf { it.hasBooks() }
        if (snapshot == null) {
            appDb.cacheDao.delete(key)
            AppLog.put("套件发现缓存内容无效，已清理并重新加载")
        }
        return snapshot
    }

    fun saveSuiteSnapshotAsync(snapshot: DiscoverySuitePageSnapshot) {
        if (snapshot.widgetBooks.isEmpty() && snapshot.rankedWidgetBooks.isEmpty()) return
        DiscoveryCacheWriteScope.launchLatest { saveVersion ->
            runCatching {
                val compactSnapshot = snapshot.compactForCache()
                if (!compactSnapshot.hasBooks()) return@runCatching
                val value = DiscoveryCachePolicy.toBoundedJson(compactSnapshot)
                if (value == null) {
                    AppLog.put("套件发现缓存超过安全大小，跳过内存和磁盘快照")
                    return@runCatching
                }
                if (!DiscoveryCacheWriteScope.isLatest(saveVersion)) return@runCatching
                DiscoverySuitePageSnapshotStore.put(compactSnapshot)
                writeBounded(
                    key = suiteSnapshotKey(
                        compactSnapshot.suiteId,
                        compactSnapshot.signature
                    ),
                    value = value,
                    cacheName = "套件发现缓存"
                )
            }.onFailure {
                AppLog.put("套件发现缓存写入失败", it)
            }
        }
    }

    fun readModernResult(
        sourceUrl: String,
        tagUrl: String
    ): ModernDiscoverResultCache? {
        val key = modernResultKey(sourceUrl, tagUrl)
        val raw = readBounded(key, "发现页面缓存") ?: return null
        val cache = GSON.fromJsonObject<ModernDiscoverResultCache>(raw)
            .getOrNull()
            ?.takeIf { it.sourceUrl == sourceUrl && it.tagUrl == tagUrl }
            ?.takeIf { it.books.isNotEmpty() }
            ?.let { parsed ->
                parsed.copy(books = parsed.books.mapNotNull(DiscoveryCachePolicy::compact))
            }
            ?.takeIf { it.books.isNotEmpty() }
        if (cache == null) {
            appDb.cacheDao.delete(key)
            AppLog.put("发现页面缓存内容无效，已清理并重新加载")
        }
        return cache
    }

    fun writeModernResult(cache: ModernDiscoverResultCache) {
        val value = DiscoveryCachePolicy.toBoundedJson(cache)
        if (value == null) {
            AppLog.put("发现页面缓存超过安全大小，跳过磁盘缓存")
            return
        }
        writeBounded(
            key = modernResultKey(cache.sourceUrl, cache.tagUrl),
            value = value,
            cacheName = "发现页面缓存"
        )
    }

    private fun suiteSnapshotKey(suiteId: String, signature: String): String {
        return DISCOVERY_SUITE_CACHE_PREFIX +
            MD5Utils.md5Encode16(suiteId + "\u001F" + signature)
    }

    private fun modernResultKey(sourceUrl: String, tagUrl: String): String {
        return DISCOVERY_MODERN_CACHE_PREFIX +
            MD5Utils.md5Encode16(sourceUrl + "\u001F" + tagUrl)
    }

    private fun readBounded(key: String, cacheName: String): String? {
        val now = System.currentTimeMillis()
        return try {
            val cache = appDb.cacheDao.get(key) ?: return null
            if (cache.deadline > 0 && cache.deadline <= now) {
                appDb.cacheDao.delete(key)
                return null
            }
            val value = cache.value
            if (value == null) {
                appDb.cacheDao.delete(key)
                AppLog.put(cacheName + " 内容为空，已清理并重新加载")
                return null
            }
            val byteCount = value.toByteArray(Charsets.UTF_8).size.toLong()
            if (!DiscoveryCachePolicy.canRead(byteCount)) {
                appDb.cacheDao.delete(key)
                AppLog.put(cacheName + " 已超过安全大小并清理：" + byteCount + " 字节")
                return null
            }
            value
        } catch (e: SQLiteBlobTooBigException) {
            runCatching { appDb.cacheDao.delete(key) }
            AppLog.put(cacheName + " 行过大，已清理并重新加载", e)
            null
        }
    }

    private fun writeBounded(key: String, value: String, cacheName: String) {
        if (!DiscoveryCachePolicy.canStore(value)) {
            AppLog.put(cacheName + " 超过安全大小，跳过磁盘缓存")
            return
        }
        appDb.cacheDao.insert(
            Cache(
                key = key,
                value = value,
                deadline = System.currentTimeMillis() + DISCOVERY_CACHE_TTL_MS
            )
        )
    }
}
