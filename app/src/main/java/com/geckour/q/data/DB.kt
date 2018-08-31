package com.geckour.q.data

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import com.geckour.q.data.dao.AlbumDao
import com.geckour.q.data.dao.ArtistDao
import com.geckour.q.data.dao.TrackDao
import com.geckour.q.data.model.Album
import com.geckour.q.data.model.Artist
import com.geckour.q.data.model.Track

@Database(entities = [Track::class, Album::class, Artist::class], version = 1)
abstract class DB : RoomDatabase() {

    companion object {
        private const val DB_NAME = "q.db"

        @Volatile
        private var instance: DB? = null

        fun getInstance(context: Context): DB =
                instance ?: synchronized(this) {
                    Room.databaseBuilder(context, DB::class.java, DB_NAME).build()
                }
    }

    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
}