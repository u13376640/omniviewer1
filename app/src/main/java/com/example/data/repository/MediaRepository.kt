package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.data.jellyfin.JellyfinAuthResult
import com.example.data.jellyfin.JellyfinClient
import com.example.data.jellyfin.JellyfinLibraryItem
import com.example.data.jellyfin.JellyfinMediaItem
import com.example.data.jellyfin.JellyfinServerInfo
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.JellyfinServerEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.model.MediaFormat
import com.example.data.model.MediaItem
import com.example.data.model.MediaSource
import com.example.data.model.MediaType
import com.example.data.pdf.PdfEngine
import com.example.data.sample.SampleMediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val jellyfinClient: JellyfinClient = JellyfinClient(),
    private val pdfEngine: PdfEngine = PdfEngine(context)
) {
    private val mediaDao = database.mediaDao()
    private val jellyfinDao = database.jellyfinDao()
    private val bookmarkDao = database.bookmarkDao()

    val allMedia: Flow<List<MediaItem>> = mediaDao.getAllMedia().map { entities ->
        entities.map { it.toDomain() }
    }

    val favorites: Flow<List<MediaItem>> = mediaDao.getFavorites().map { entities ->
        entities.map { it.toDomain() }
    }

    val recentMedia: Flow<List<MediaItem>> = mediaDao.getRecentMedia().map { entities ->
        entities.map { it.toDomain() }
    }

    val activeJellyfinServer: Flow<JellyfinServerEntity?> = jellyfinDao.getActiveServer()

    suspend fun clearPlaceholderMedia() = withContext(Dispatchers.IO) {
        mediaDao.clearDemoMedia()
        jellyfinDao.clearDemoServers()
    }

    suspend fun removeMediaFromFolder(folderId: String, folderUriString: String) = withContext(Dispatchers.IO) {
        mediaDao.deleteMediaByIdPrefix(folderId)
        if (folderUriString.isNotBlank()) {
            mediaDao.deleteMediaByUriPrefix(folderUriString)
            val docSegment = runCatching { Uri.parse(folderUriString).lastPathSegment }.getOrNull()
            if (!docSegment.isNullOrBlank()) {
                mediaDao.deleteMediaByUriContains(docSegment)
            }
        }
    }

    fun getUriDisplayName(uri: Uri): String {
        var fileName = "Local Media"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileName
    }

    suspend fun addLocalMediaUri(uri: Uri, mimeType: String? = null): MediaItem = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        var fileName = "Local Media"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val resolvedMime = mimeType ?: context.contentResolver.getType(uri)
        val format = MediaFormat.fromExtensionOrMime(fileName, resolvedMime)
        val type = when (format) {
            MediaFormat.MP4 -> MediaType.VIDEO
            MediaFormat.PDF -> MediaType.PDF
            MediaFormat.JPG, MediaFormat.PNG -> {
                if (fileName.lowercase().contains("manga") || fileName.lowercase().contains("comic") || fileName.lowercase().contains("ch")) {
                    MediaType.MANGA
                } else {
                    MediaType.PHOTO
                }
            }
            MediaFormat.UNKNOWN -> MediaType.PHOTO
        }

        val pagesCount = if (format == MediaFormat.PDF) {
            pdfEngine.getPageCount(uriString).coerceAtLeast(1)
        } else 1

        val formattedSize = if (fileSize > 0) {
            val mb = fileSize / (1024.0 * 1024.0)
            if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%d KB", fileSize / 1024)
        } else ""

        val mediaItem = MediaItem(
            id = "local_${UUID.randomUUID()}",
            title = fileName.substringBeforeLast("."),
            subtitle = "Local ${format.name} File",
            uriString = uriString,
            mediaType = type,
            mediaFormat = format,
            mediaSource = MediaSource.LOCAL,
            totalPages = pagesCount,
            fileSizeFormatted = formattedSize
        )

        mediaDao.insertMedia(MediaItemEntity.fromDomain(mediaItem))
        mediaItem
    }

    suspend fun scanFolderUri(
        treeUri: Uri,
        onProgress: ((status: String, fileName: String, count: Int) -> Unit)? = null
    ): Pair<String, List<MediaItem>> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        var folderName = "Media Folder"
        val folderId = "folder_${treeUri.hashCode()}"

        // Ensure persistable permission is active
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: Exception) {
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root != null) {
                folderName = root.name ?: "Media Folder"
                onProgress?.invoke("Scanning directory...", folderName, 0)
                scanDirectoryRecursively(
                    directory = root,
                    folderId = folderId,
                    rootFolderName = folderName,
                    relativeSubfolderPath = "",
                    items = items,
                    maxDepth = 6,
                    onProgress = onProgress
                )
                if (items.isNotEmpty()) {
                    onProgress?.invoke("Saving media to database...", "Indexed ${items.size} files", items.size)
                    mediaDao.insertMediaList(items.map { MediaItemEntity.fromDomain(it) })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        folderName to items
    }

    private fun scanDirectoryRecursively(
        directory: DocumentFile,
        folderId: String,
        rootFolderName: String,
        relativeSubfolderPath: String,
        items: MutableList<MediaItem>,
        maxDepth: Int,
        onProgress: ((status: String, fileName: String, count: Int) -> Unit)? = null
    ) {
        if (maxDepth <= 0) return
        val files = directory.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                val subDirName = file.name ?: "Subfolder"
                val nextSubfolderPath = if (relativeSubfolderPath.isEmpty()) subDirName else "$relativeSubfolderPath/$subDirName"
                onProgress?.invoke("Traversing $subDirName", subDirName, items.size)
                scanDirectoryRecursively(
                    directory = file,
                    folderId = folderId,
                    rootFolderName = rootFolderName,
                    relativeSubfolderPath = nextSubfolderPath,
                    items = items,
                    maxDepth = maxDepth - 1,
                    onProgress = onProgress
                )
            } else {
                val fileName = file.name ?: continue
                val mime = file.type
                val format = MediaFormat.fromExtensionOrMime(fileName, mime)
                if (format == MediaFormat.UNKNOWN) continue

                val lower = fileName.lowercase()
                val pathLower = relativeSubfolderPath.lowercase()
                val type = when (format) {
                    MediaFormat.MP4 -> MediaType.VIDEO
                    MediaFormat.PDF -> MediaType.PDF
                    MediaFormat.JPG, MediaFormat.PNG -> {
                        if (lower.contains("manga") || lower.contains("comic") || lower.contains("ch") ||
                            pathLower.contains("manga") || pathLower.contains("comic") || pathLower.contains("chapter")) {
                            MediaType.MANGA
                        } else {
                            MediaType.PHOTO
                        }
                    }
                    MediaFormat.UNKNOWN -> MediaType.PHOTO
                }

                val size = file.length()
                val formattedSize = if (size > 0) {
                    val mb = size / (1024.0 * 1024.0)
                    if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%d KB", size / 1024)
                } else ""

                val subtitle = if (relativeSubfolderPath.isNotEmpty()) {
                    "$rootFolderName / $relativeSubfolderPath • ${format.name}"
                } else {
                    "$rootFolderName • ${format.name}"
                }

                val immediateParent = if (relativeSubfolderPath.isNotEmpty()) {
                    relativeSubfolderPath.substringAfterLast("/")
                } else {
                    rootFolderName
                }

                val item = MediaItem(
                    id = "file_${folderId}_${file.uri.toString().hashCode()}",
                    title = fileName.substringBeforeLast("."),
                    subtitle = subtitle,
                    uriString = file.uri.toString(),
                    mediaType = type,
                    mediaFormat = format,
                    mediaSource = MediaSource.LOCAL,
                    totalPages = 1,
                    fileSizeFormatted = formattedSize,
                    folderId = folderId,
                    folderName = rootFolderName,
                    subfolderPath = relativeSubfolderPath,
                    immediateParentFolder = immediateParent
                )
                items.add(item)
                onProgress?.invoke("Indexed $fileName", fileName, items.size)
            }
        }
    }

    suspend fun addCloudMedia(
        title: String,
        url: String,
        mediaType: MediaType,
        mediaFormat: MediaFormat
    ): MediaItem = withContext(Dispatchers.IO) {
        val mediaItem = MediaItem(
            id = "cloud_${UUID.randomUUID()}",
            title = title.ifBlank { "Cloud Stream" },
            subtitle = "Cloud ${mediaFormat.name} Stream",
            uriString = url,
            mediaType = mediaType,
            mediaFormat = mediaFormat,
            mediaSource = MediaSource.CLOUD_URL,
            fileSizeFormatted = "Streaming"
        )
        mediaDao.insertMedia(MediaItemEntity.fromDomain(mediaItem))
        mediaItem
    }

    suspend fun updateReadingProgress(id: String, page: Int, totalPages: Int) = withContext(Dispatchers.IO) {
        mediaDao.updateReadingProgress(id, page, totalPages)
    }

    suspend fun updatePlaybackProgress(id: String, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        mediaDao.updatePlaybackProgress(id, positionMs, durationMs)
    }

    suspend fun toggleFavorite(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val existing = mediaDao.getMediaByIdDirect(item.id)
        val newFav = if (existing != null) !existing.isFavorite else !item.isFavorite
        if (existing != null) {
            mediaDao.toggleFavorite(item.id, newFav)
        } else {
            mediaDao.insertMedia(MediaItemEntity.fromDomain(item.copy(isFavorite = newFav)))
        }
        newFav
    }

    suspend fun toggleFavorite(id: String, currentFav: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.toggleFavorite(id, !currentFav)
    }

    suspend fun deleteMedia(id: String) = withContext(Dispatchers.IO) {
        mediaDao.deleteMedia(id)
    }

    fun getBookmarks(mediaId: String): Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarks(mediaId)

    suspend fun addBookmark(mediaId: String, pageIndex: Int, title: String = "Page ${pageIndex + 1}") = withContext(Dispatchers.IO) {
        bookmarkDao.insertBookmark(BookmarkEntity(mediaId = mediaId, pageIndex = pageIndex, title = title))
    }

    suspend fun removeBookmark(id: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmark(id)
    }

    // --- Jellyfin Operations ---

    suspend fun testJellyfinConnection(serverUrl: String): Result<JellyfinServerInfo> {
        return jellyfinClient.testServerConnection(serverUrl)
    }

    suspend fun authenticateJellyfin(
        serverUrl: String,
        username: String,
        password: String
    ): Result<JellyfinServerEntity> = withContext(Dispatchers.IO) {
        val authResult = jellyfinClient.authenticate(serverUrl, username, password)
        if (authResult.isSuccess) {
            val res = authResult.getOrThrow()
            val serverEntity = JellyfinServerEntity(
                serverName = res.serverName.ifBlank { "Home Server" },
                serverUrl = serverUrl.trim().removeSuffix("/"),
                username = res.userName,
                accessToken = res.accessToken,
                userId = res.userId,
                isActive = true,
                lastConnected = System.currentTimeMillis()
            )
            val id = jellyfinDao.insertServer(serverEntity)
            jellyfinDao.setActiveServer(id)
            Result.success(serverEntity.copy(id = id))
        } else {
            Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
        }
    }

    suspend fun getJellyfinLibraries(server: JellyfinServerEntity): Result<List<JellyfinLibraryItem>> {
        return jellyfinClient.getLibraries(server.serverUrl, server.userId, server.accessToken)
    }

    suspend fun getJellyfinItems(
        server: JellyfinServerEntity,
        parentId: String?
    ): Result<List<JellyfinMediaItem>> {
        return jellyfinClient.getItemsInLibrary(server.serverUrl, server.userId, server.accessToken, parentId)
    }

    fun convertJellyfinItemToMediaItem(
        jellyfinItem: JellyfinMediaItem,
        server: JellyfinServerEntity
    ): MediaItem {
        val (mediaType, mediaFormat, streamUri, thumb) = when (jellyfinItem.type.lowercase()) {
            "book" -> {
                val stream = jellyfinClient.getImageUrl(server.serverUrl, jellyfinItem.id, width = 1200)
                val format = if (jellyfinItem.container.equals("pdf", ignoreCase = true)) MediaFormat.PDF else MediaFormat.JPG
                val type = if (format == MediaFormat.PDF) MediaType.PDF else MediaType.MANGA
                Quadruple(type, format, stream, stream)
            }
            "photo" -> {
                val stream = jellyfinClient.getImageUrl(server.serverUrl, jellyfinItem.id, width = 1920)
                Quadruple(MediaType.PHOTO, MediaFormat.JPG, stream, stream)
            }
            else -> { // Movie, Video, Episode
                val stream = jellyfinClient.getVideoDirectStreamUrl(server.serverUrl, jellyfinItem.id, server.accessToken)
                val thumbUrl = jellyfinClient.getImageUrl(server.serverUrl, jellyfinItem.id, width = 400)
                Quadruple(MediaType.VIDEO, MediaFormat.MP4, stream, thumbUrl)
            }
        }

        return MediaItem(
            id = "jf_${jellyfinItem.id}",
            title = jellyfinItem.name,
            subtitle = "Jellyfin • ${jellyfinItem.type}",
            uriString = streamUri,
            mediaType = mediaType,
            mediaFormat = mediaFormat,
            mediaSource = MediaSource.JELLYFIN,
            thumbnailUrl = thumb,
            drawableResId = null,
            durationMs = jellyfinItem.durationMs,
            jellyfinItemId = jellyfinItem.id,
            jellyfinServerId = server.id,
            totalPages = if (mediaType == MediaType.MANGA || mediaType == MediaType.PDF) 1 else 1,
            pagesDrawableResIds = emptyList()
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
