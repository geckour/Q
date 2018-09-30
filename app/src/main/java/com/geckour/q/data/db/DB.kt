package com.geckour.q.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.geckour.q.data.db.dao.AlbumDao
import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.data.db.dao.TrackDao
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.Track

@Database(entities = [Track::class, Album::class, Artist::class], version = 1)
abstract class DB : RoomDatabase() {

    companion object {
        private const val DB_NAME = "q.db"

        @Volatile
        private var instance: DB? = null

        fun getInstance(context: Context): DB =
                instance ?: synchronized(this) {
                    Room.databaseBuilder(context, DB::class.java, DB_NAME).build().apply {
                        instance = this
                    }
                }
    }

    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
}