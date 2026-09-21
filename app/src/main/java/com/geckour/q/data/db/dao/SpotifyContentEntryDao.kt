package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geckour.q.data.db.model.SpotifyContentEntry

@Dao
interface SpotifyContentEntryDao {

    @Upsert
    suspend fun upsertAll(entries: List<SpotifyContentEntry>)

    @Query("select * from spotifycontententry where parentUri = :parentUri order by position")
    suspend fun getChildren(parentUri: String): List<SpotifyContentEntry>

    @Query("delete from spotifycontententry where parentUri = :parentUri")
    suspend fun deleteChildren(parentUri: String)

    @Query("delete from spotifycontententry where updatedAt < :updatedAt")
    suspend fun deleteStalerThan(updatedAt: Long)

    @Query(
        "select * from spotifycontententry where parentUri != '' and title like :title " +
                "group by uri order by title limit :limit"
    )
    suspend fun searchByTitle(title: String, limit: Int): List<SpotifyContentEntry>

    @Query("select max(updatedAt) from spotifycontententry")
    suspend fun latestUpdatedAt(): Long?

    @Transaction
    suspend fun replaceChildren(parentUri: String, entries: List<SpotifyContentEntry>) {
        deleteChildren(parentUri)
        upsertAll(entries)
    }
}
