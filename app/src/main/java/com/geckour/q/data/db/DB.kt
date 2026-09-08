package com.geckour.q.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geckour.q.data.db.dao.AlbumDao
import com.geckour.q.data.db.dao.ArtistDao
import com.geckour.q.data.db.dao.AudioDeviceEqualizerInfoDao
import com.geckour.q.data.db.dao.EqualizerPresetDao
import com.geckour.q.data.db.dao.LyricDao
import com.geckour.q.data.db.dao.QueueHistoryDao
import com.geckour.q.data.db.dao.SavedQueueDao
import com.geckour.q.data.db.dao.TrackDao
import com.geckour.q.data.db.dao.TrackHistoryDao
import com.geckour.q.data.db.model.Album
import com.geckour.q.data.db.model.Artist
import com.geckour.q.data.db.model.AudioDeviceEqualizerInfo
import com.geckour.q.data.db.model.Bool
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import com.geckour.q.data.db.model.Lyric
import com.geckour.q.data.db.model.LyricLine
import com.geckour.q.data.db.model.QueueHistory
import com.geckour.q.data.db.model.QueueHistoryTrack
import com.geckour.q.data.db.model.SavedQueue
import com.geckour.q.data.db.model.SavedQueueTrack
import com.geckour.q.data.db.model.Track
import com.geckour.q.data.db.model.TrackHistory
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [
        Track::class,
        Album::class,
        Artist::class,
        Lyric::class,
        TrackHistory::class,
        EqualizerPreset::class,
        EqualizerLevelRatio::class,
        AudioDeviceEqualizerInfo::class,
        QueueHistory::class,
        QueueHistoryTrack::class,
        SavedQueue::class,
        SavedQueueTrack::class,
    ],
    version = 13,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
    ]
)
@TypeConverters(BoolConverter::class, LyricLineConverter::class)
abstract class DB : RoomDatabase() {

    companion object {
        private const val DB_NAME = "q.db"

        @Volatile
        private var instance: DB? = null

        private fun migrationFrom11To12(context: Context) = object : Migration(11, 12) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "update track set sourcePath = ? || replace(dropboxPath, '/', '%2F') " +
                            "where sourcePath = '' and dropboxPath is not null",
                    arrayOf("file://${context.dataDir.absolutePath}/audio/")
                )
            }
        }

        fun getInstance(context: Context): DB =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, DB::class.java, DB_NAME)
                    .addMigrations(migrationFrom11To12(context))
                    .build()
                    .apply { instance = this }
            }
    }

    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricDao(): LyricDao
    abstract fun trackHistoryDao(): TrackHistoryDao
    abstract fun queueHistoryDao(): QueueHistoryDao
    abstract fun savedQueueDao(): SavedQueueDao
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