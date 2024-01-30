package com.geckour.q.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.geckour.q.data.db.dao.AlbumDao
import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.data.db.dao.AudioDeviceEqualizerInfoDao
import com.geckour.q.data.db.dao.EqualizerPresetDao
import com.geckour.q.data.db.dao.LyricDao
import com.geckour.q.data.db.dao.TrackDao
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import com.geckour.q.data.db.model.Bool
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.data.db.model.Track
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [
        Track::class,
        Album::class,
        Artist::class,
        Lyric::class,
        EqualizerPreset::class,
        EqualizerLevelRatio::class,
        AudioDeviceEqualizerInfo::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ]
)
@TypeConverters(BoolConverter::class, LyricLineConverter::class)
abstract class DB : RoomDatabase() {

    companion object {
        private const val DB_NAME = "q.db"

        @Volatile
        private var instance: DB? = null

        fun getInstance(context: Context): DB =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, DB::class.java, DB_NAME)
                    .build()
                    .apply { instance = this }
            }
    }

    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricDao(): LyricDao
    abstract fun equalizerPresetDao(): EqualizerPresetDao
    abstract fun audioDeviceEqualizerInfoDao(): AudioDeviceEqualizerInfoDao
}

internal class BoolConverter {
    @TypeConverter
    fun fromBool(bool: Bool): Int = when (bool) {
        Bool.TRUE -> 1
        Bool.FALSE -> 0
        Bool.UNDEFINED -> -1
    }

    @TypeConverter
    fun toBool(value: Int): Bool = when (value) {
        1 -> Bool.TRUE
        0 -> Bool.FALSE
        else -> Bool.UNDEFINED
    }

    fun toBoolean(value: Int) = toBoolean(toBool(value))

    fun toBoolean(bool: Bool): Boolean? = when (bool) {
        Bool.TRUE -> true
        Bool.FALSE -> false
        Bool.UNDEFINED -> null
    }

    fun fromBoolean(boolean: Boolean?): Bool = when (boolean) {
        true -> Bool.TRUE
        false -> Bool.FALSE
        null -> Bool.UNDEFINED
    }
}

internal class LyricLineConverter {
    @TypeConverter
    fun fromLyricLine(lyricLine: LyricLine): String {
        return Json.encodeToString(lyricLine)
    }

    @TypeConverter
    fun toLyricLine(json: String): LyricLine {
        return Json.decodeFromString(json)
    }

    @TypeConverter
    fun fromLyricLineList(lyricLines: List<LyricLine>): String {
        return Json.encodeToString(lyricLines)
    }

    @TypeConverter
    fun toLyricLineList(json: String): List<LyricLine> {
        return Json.decodeFromString(json)
    }
}