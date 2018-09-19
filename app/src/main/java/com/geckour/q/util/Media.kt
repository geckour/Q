package com.geckour.q.util

import android.content.Context
import android.provider.MediaStore
import com.geckour.q.data.db.DB
import com.geckour.q.data.db.model.Track
import com.geckour.q.domain.model.Genre
import com.geckour.q.domain.model.Playlist
import com.geckour.q.domain.model.Song
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async

fun getSongListFromTrackList(db: DB, dbTrackList: List<Track>): Deferred<List<Song>> =
        async {
            dbTrackList.mapNotNull { getSong(db, it).await() }
        }

fun getSongListFromTrackId(db: DB,
                           dbTrackIdList: List<Long>,
                           genreId: Long? = null,
                           playlistId: Long? = null): Deferred<List<Song>> =
        async {
            dbTrackIdList.mapNotNull { getSong(db, it, genreId, playlistId).await() }
        }

fun getSong(db: DB, trackId: Long,
            genreId: Long? = null, playlistId: Long? = null): Deferred<Song?> =
        async {
            db.trackDao().get(trackId)?.let {
                getSong(db, it, genreId, playlistId).await()
            }
        }

fun getSong(db: DB, track: Track, genreId: Long? = null, playlistId: Long? = null): Deferred<Song?> =
        async {
            val artist = db.artistDao().get(track.artistId) ?: return@async null
            Song(track.id, track.albumId, track.title, artist.title, track.duration,
                    track.trackNum, track.trackTotal, track.discNum, track.discTotal,
                    genreId, playlistId, track.sourcePath)
        }

fun Genre.getTrackIds(context: Context): List<Long> {
    val trackIdList: ArrayList<Long> = ArrayList()

    context.contentResolver.query(
            MediaStore.Audio.Genres.Members.getContentUri("external", this.id),
            arrayOf(
                    MediaStore.Audio.Genres.Members._ID),
            null,
            null,
            null)?.use {
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Genres.Members._ID))
            trackIdList.add(id)
        }
    }

    return trackIdList
}

fun Playlist.getTrackIds(context: Context): List<Long> {
    val trackIdList: ArrayList<Long> = ArrayList()

    context.contentResolver.query(
            MediaStore.Audio.Playlists.Members.getContentUri("external", this.id),
            arrayOf(
                    MediaStore.Audio.Playlists.Members.AUDIO_ID),
            null,
            null,
            null)?.use {
        while (it.moveToNext()) {
            val id = it.getLong(it.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID))
            trackIdList.add(id)
        }
    }

    return trackIdList
}