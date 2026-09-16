package com.geckour.q.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.JoinedAlbum
import com.geckour.q.data.db.model.JoinedTrack
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.dailyRandom
import com.geckour.q.util.escapeSql
import com.geckour.q.util.getMediaItem
import com.geckour.q.util.orderModified
import kotlinx.coroutines.flow.first

internal object MediaLibraryTree {

    const val EXTRA_MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

    private const val SCHEME = "qbrowse://"

    const val MEDIA_ID_ROOT = "${SCHEME}root"

    private const val MEDIA_ID_ARTISTS = "${SCHEME}artists"
    private const val MEDIA_ID_ALBUMS = "${SCHEME}albums"
    private const val MEDIA_ID_TRACKS = "${SCHEME}tracks"
    private const val MEDIA_ID_GENRES = "${SCHEME}genres"
    private const val MEDIA_ID_FAVORITES = "${SCHEME}favorites"

    private const val MEDIA_ID_PREFIX_ARTIST = "${SCHEME}artist/"
    private const val MEDIA_ID_PREFIX_ALBUM = "${SCHEME}album/"
    private const val MEDIA_ID_PREFIX_GENRE = "${SCHEME}genre/"

    private const val SEARCH_RESULT_LIMIT_PER_CATEGORY = 20

    fun rootMediaItem(context: Context): MediaItem = browsableMediaItem(
        mediaId = MEDIA_ID_ROOT,
        title = context.getString(R.string.app_name),
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    )

    suspend fun getItem(context: Context, mediaId: String): MediaItem? {
        val db = DB.getInstance(context)

        return when {
            mediaId == MEDIA_ID_ROOT -> rootMediaItem(context)

            mediaId in rootCategoryIds -> rootCategories(context).firstOrNull {
                it.mediaId == mediaId
            }

            mediaId.startsWith(MEDIA_ID_PREFIX_ARTIST) -> {
                mediaId.artistId
                    ?.let { db.artistDao().get(it) }
                    ?.toMediaItem()
            }

            mediaId.startsWith(MEDIA_ID_PREFIX_ALBUM) -> {
                mediaId.albumId
                    ?.let { db.albumDao().get(it) }
                    ?.toMediaItem()
            }

            mediaId.startsWith(MEDIA_ID_PREFIX_GENRE) -> {
                mediaId.genreName?.toGenreMediaItem()
            }

            else -> db.trackDao().getBySourcePath(mediaId)?.getMediaItem()
        }
    }

    suspend fun getChildren(
        context: Context,
        parentId: String,
        page: Int,
        pageSize: Int
    ): List<MediaItem>? {
        val db = DB.getInstance(context)
        val offset = page * pageSize

        return when {
            parentId == MEDIA_ID_ROOT -> rootCategories(context).paged(offset, pageSize)

            parentId == MEDIA_ID_ARTISTS -> db.artistDao()
                .getAllOrientedAlbum()
                .paged(offset, pageSize)
                .map { it.toMediaItem() }

            parentId == MEDIA_ID_ALBUMS -> db.albumDao()
                .getAll()
                .paged(offset, pageSize)
                .map { it.toMediaItem() }

            parentId == MEDIA_ID_TRACKS -> db.trackDao()
                .getAllPaged(limit = pageSize, offset = offset)
                .map { it.getMediaItem() }

            parentId == MEDIA_ID_GENRES -> db.trackDao()
                .getAllGenreAsFlow()
                .first()
                .sorted()
                .paged(offset, pageSize)
                .map { it.toGenreMediaItem() }

            parentId == MEDIA_ID_FAVORITES -> db.trackDao()
                .getAllWithFavorite()
                .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
                .paged(offset, pageSize)
                .map { it.getMediaItem() }

            parentId.startsWith(MEDIA_ID_PREFIX_ARTIST) -> {
                val artistId = parentId.artistId ?: return null
                db.albumDao()
                    .getAllByArtistId(artistId)
                    .sortedBy { it.album.titleSort }
                    .paged(offset, pageSize)
                    .map { it.toMediaItem() }
            }

            parentId.startsWith(MEDIA_ID_PREFIX_ALBUM) ||
                    parentId.startsWith(MEDIA_ID_PREFIX_GENRE) -> {
                resolveToTracks(context, parentId)
                    .paged(offset, pageSize)
                    .map { it.getMediaItem() }
            }

            else -> null
        }
    }

    suspend fun search(context: Context, query: String): List<MediaItem> {
        val db = DB.getInstance(context)
        val escaped = query.escapeSql

        val artists = db.artistDao()
            .findAllByTitle(escaped)
            .take(SEARCH_RESULT_LIMIT_PER_CATEGORY)
            .map { it.toMediaItem() }
        val albums = db.albumDao()
            .findAllByTitle(escaped)
            .take(SEARCH_RESULT_LIMIT_PER_CATEGORY)
            .map { it.toMediaItem() }
        val tracks = db.trackDao()
            .getAllByTitle(escaped)
            .take(SEARCH_RESULT_LIMIT_PER_CATEGORY)
            .map { it.getMediaItem() }
        val genres = db.trackDao()
            .findAllByName(escaped)
            .take(SEARCH_RESULT_LIMIT_PER_CATEGORY)
            .map { it.toGenreMediaItem() }

        return artists + albums + tracks + genres
    }

    suspend fun resolveToTracks(context: Context, mediaId: String): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return when {
            mediaId == MEDIA_ID_ROOT ||
                    mediaId == MEDIA_ID_TRACKS -> db.trackDao().getAll()

            mediaId == MEDIA_ID_ARTISTS ||
                    mediaId == MEDIA_ID_ALBUMS ||
                    mediaId == MEDIA_ID_GENRES -> emptyList()

            mediaId == MEDIA_ID_FAVORITES -> db.trackDao()
                .getAllWithFavorite()
                .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)

            mediaId.startsWith(MEDIA_ID_PREFIX_ARTIST) -> {
                mediaId.artistId
                    ?.let { db.trackDao().getAllByArtist(it) }
                    ?.orderModified(OrientedClassType.ARTIST, InsertActionType.OVERRIDE)
                    .orEmpty()
            }

            mediaId.startsWith(MEDIA_ID_PREFIX_ALBUM) -> {
                mediaId.albumId
                    ?.let { db.trackDao().getAllByAlbum(it) }
                    ?.orderModified(OrientedClassType.ALBUM, InsertActionType.OVERRIDE)
                    .orEmpty()
            }

            mediaId.startsWith(MEDIA_ID_PREFIX_GENRE) -> {
                mediaId.genreName
                    ?.let { db.trackDao().getAllByGenreName(it) }
                    ?.orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
                    .orEmpty()
            }

            else -> listOfNotNull(db.trackDao().getBySourcePath(mediaId))
        }
    }

    suspend fun resolveQueryToTracks(
        context: Context,
        query: String,
        extras: Bundle?
    ): List<JoinedTrack> {
        if (query.isBlank()) return randomQueue(context)

        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        val artistName = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
        val albumName = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
        val trackName = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)
        val genreName = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)

        val focused = when (focus) {
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE ->
                artistName?.let { findTracksByArtistName(context, it) }

            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE ->
                albumName?.let { findTracksByAlbumName(context, it, artistName) }

            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE ->
                trackName?.let { findTracksByTrackName(context, it, albumName, artistName) }

            MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE ->
                genreName?.let { findTracksByGenreName(context, it) }

            else -> null
        }
        if (focused != null && focused.isNotEmpty()) return focused

        return findTracksByFreeQuery(context, query)
    }

    suspend fun randomQueue(context: Context): List<JoinedTrack> {
        val db = DB.getInstance(context)
        val originTrack = db.trackDao().getByRandom(db, dailyRandom) ?: return emptyList()

        val sourcePaths = db.queueHistoryDao().generateQueue(originTrack.track.id)
        val tracks = db.trackDao()
            .getAllBySourcePaths(sourcePaths)
            .associateBy { it.track.sourcePath }

        return sourcePaths.mapNotNull { tracks[it] }.ifEmpty { listOf(originTrack) }
    }

    private suspend fun findTracksByFreeQuery(
        context: Context,
        query: String
    ): List<JoinedTrack> {
        val db = DB.getInstance(context)
        val escaped = query.escapeSql

        db.artistDao().getAllByTitle(query).let { artists ->
            if (artists.isNotEmpty()) return artists.tracks(context)
        }

        val albums = db.albumDao().findAllByTitle(escaped)
        albums.filter { it.album.title.equals(query, ignoreCase = true) }.let { exact ->
            if (exact.isNotEmpty()) return exact.tracks(context)
        }

        val tracks = db.trackDao().getAllByTitle(escaped)
        tracks.filter { it.track.title.equals(query, ignoreCase = true) }.let { exact ->
            if (exact.isNotEmpty()) {
                return exact.orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
            }
        }

        db.artistDao().findAllByTitle(escaped).let { artists ->
            if (artists.isNotEmpty()) return artists.tracks(context)
        }
        if (albums.isNotEmpty()) return albums.tracks(context)
        if (tracks.isNotEmpty()) {
            return tracks.orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
        }

        db.trackDao().findAllByName(escaped).let { genres ->
            if (genres.isNotEmpty()) {
                return genres.flatMap { db.trackDao().getAllByGenreName(it) }
                    .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
            }
        }

        return db.trackDao().findAllByLyricKeyword(escaped)
            .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
    }

    private suspend fun findTracksByArtistName(
        context: Context,
        name: String
    ): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return db.artistDao().getAllByTitle(name)
            .ifEmpty { db.artistDao().findAllByTitle(name.escapeSql) }
            .tracks(context)
    }

    private suspend fun findTracksByAlbumName(
        context: Context,
        name: String,
        artistName: String?
    ): List<JoinedTrack> {
        val db = DB.getInstance(context)
        val albums = db.albumDao().findAllByTitle(name.escapeSql)
            .let { candidates ->
                val exact = candidates.filter { it.album.title.equals(name, ignoreCase = true) }
                exact.ifEmpty { candidates }
            }
            .let { candidates ->
                artistName ?: return@let candidates
                val narrowed = candidates.filter {
                    it.artist.title.contains(artistName, ignoreCase = true)
                }
                narrowed.ifEmpty { candidates }
            }

        return albums.tracks(context)
    }

    private suspend fun findTracksByTrackName(
        context: Context,
        name: String,
        albumName: String?,
        artistName: String?
    ): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return db.trackDao().getAllByTitle(name.escapeSql)
            .let { candidates ->
                val exact = candidates.filter { it.track.title.equals(name, ignoreCase = true) }
                exact.ifEmpty { candidates }
            }
            .let { candidates ->
                albumName ?: return@let candidates
                val narrowed = candidates.filter {
                    it.album.title.contains(albumName, ignoreCase = true)
                }
                narrowed.ifEmpty { candidates }
            }
            .let { candidates ->
                artistName ?: return@let candidates
                val narrowed = candidates.filter {
                    it.artist.title.contains(artistName, ignoreCase = true) ||
                            it.albumArtist?.title?.contains(artistName, ignoreCase = true) == true
                }
                narrowed.ifEmpty { candidates }
            }
            .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
    }

    private suspend fun findTracksByGenreName(
        context: Context,
        name: String
    ): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return db.trackDao().findAllByName(name.escapeSql)
            .flatMap { db.trackDao().getAllByGenreName(it) }
            .orderModified(OrientedClassType.TRACK, InsertActionType.OVERRIDE)
    }

    @JvmName("tracksOfArtists")
    private suspend fun List<Artist>.tracks(context: Context): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return flatMap { db.trackDao().getAllByArtist(it.id) }
            .orderModified(OrientedClassType.ARTIST, InsertActionType.OVERRIDE)
    }

    @JvmName("tracksOfAlbums")
    private suspend fun List<JoinedAlbum>.tracks(context: Context): List<JoinedTrack> {
        val db = DB.getInstance(context)

        return flatMap { db.trackDao().getAllByAlbum(it.album.id) }
            .orderModified(OrientedClassType.ALBUM, InsertActionType.OVERRIDE)
    }

    private val rootCategoryIds = listOf(
        MEDIA_ID_ARTISTS,
        MEDIA_ID_ALBUMS,
        MEDIA_ID_TRACKS,
        MEDIA_ID_GENRES,
        MEDIA_ID_FAVORITES
    )

    private fun rootCategories(context: Context): List<MediaItem> = listOf(
        browsableMediaItem(
            mediaId = MEDIA_ID_ARTISTS,
            title = context.getString(R.string.search_category_artist),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
        ),
        browsableMediaItem(
            mediaId = MEDIA_ID_ALBUMS,
            title = context.getString(R.string.search_category_album),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
        ),
        browsableMediaItem(
            mediaId = MEDIA_ID_TRACKS,
            title = context.getString(R.string.search_category_track),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            playable = true
        ),
        browsableMediaItem(
            mediaId = MEDIA_ID_GENRES,
            title = context.getString(R.string.search_category_genre),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
        ),
        browsableMediaItem(
            mediaId = MEDIA_ID_FAVORITES,
            title = context.getString(R.string.browse_category_favorite),
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
            playable = true
        )
    )

    private fun Artist.toMediaItem(): MediaItem = browsableMediaItem(
        mediaId = MEDIA_ID_PREFIX_ARTIST + id,
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
        artworkUriString = artworkUriString,
        playable = true
    )

    private fun JoinedAlbum.toMediaItem(): MediaItem = browsableMediaItem(
        mediaId = MEDIA_ID_PREFIX_ALBUM + album.id,
        title = album.title,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        subtitle = artist.title,
        artworkUriString = album.artworkUriString,
        playable = true
    )

    private fun String.toGenreMediaItem(): MediaItem = browsableMediaItem(
        mediaId = MEDIA_ID_PREFIX_GENRE + Uri.encode(this),
        title = this,
        mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
        playable = true
    )

    private fun browsableMediaItem(
        mediaId: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
        artworkUriString: String? = null,
        playable: Boolean = false
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artworkUriString?.toUri())
                .setIsBrowsable(true)
                .setIsPlayable(playable)
                .setMediaType(mediaType)
                .build()
        )
        .build()

    private val String.artistId: Long?
        get() = removePrefix(MEDIA_ID_PREFIX_ARTIST).toLongOrNull()

    private val String.albumId: Long?
        get() = removePrefix(MEDIA_ID_PREFIX_ALBUM).toLongOrNull()

    private val String.genreName: String?
        get() = Uri.decode(removePrefix(MEDIA_ID_PREFIX_GENRE)).takeIf { it.isNotBlank() }

    private fun <T> List<T>.paged(offset: Int, pageSize: Int): List<T> =
        if (offset >= size) emptyList() else drop(offset).take(pageSize)
}
