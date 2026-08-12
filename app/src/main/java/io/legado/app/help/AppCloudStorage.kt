package io.legado.app.help

import java.io.File

/**
 * Minimal compatibility surface for the discovery UI's optional asset packages.
 * The official upstream baseline has no Rimchars cloud-storage subsystem.
 */
object AppCloudStorage {
    data class RemoteFile(val displayName: String, val lastModify: Long = 0L)

    val type: String = "disabled"

    suspend fun listTopBarPackages(
        isNight: Boolean,
        containerId: String? = null,
        scope: String? = null
    ): List<RemoteFile> = emptyList()

    suspend fun listCoverCollectionPackages(
        isNight: Boolean,
        containerId: String? = null,
        scope: String? = null
    ): List<RemoteFile> = emptyList()

    suspend fun uploadTopBarPackage(
        isNight: Boolean, dirName: String, file: File,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    suspend fun downloadTopBarPackage(
        isNight: Boolean, dirName: String, file: File,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    suspend fun deleteTopBarPackage(
        isNight: Boolean, dirName: String,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    suspend fun uploadCoverCollectionPackage(
        isNight: Boolean, dirName: String, file: File,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    suspend fun downloadCoverCollectionPackage(
        isNight: Boolean, dirName: String, file: File,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    suspend fun deleteCoverCollectionPackage(
        isNight: Boolean, dirName: String,
        containerId: String? = null, scope: String? = null
    ): Nothing = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Remote discovery asset packages are unavailable")
}
