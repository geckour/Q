package com.geckour.q.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
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
import com.geckour.q.data.db.dao.SpotifyContentEntryDao
import com.geckour.q.data.db.dao.SpotifyTrackDao
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
import com.geckour.q.data.db.model.SpotifyContentEntry
import com.geckour.q.data.db.model.SpotifyTrack
import com.geckour.q.data.db.model.Track
import com.geckour.q.data.db.model.TrackHistory
import com.geckour.q.data.db.model.spotifyMatchKeyOf
import com.geckour.q.data.db.model.toSearchText
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
        SpotifyTrack::class,
        SpotifyContentEntry::class,
    ],
    version = 25,
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
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24, spec = DB.LinkSpotifyTracksSpec::class),
        AutoMigration(from = 24, to = 25, spec = DB.FillLyricSearchTextsSpec::class),
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

        private val migrationFrom13To14 = object : Migration(13, 14) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "create index if not exists index_Track_dropboxPath on Track (dropboxPath)"
                )
            }
        }

        private val migrationFrom14To15 = object : Migration(14, 15) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "create index if not exists index_Track_mediaId on Track (mediaId)"
                )
            }
        }

        private fun migrationFrom15To16(context: Context) = object : Migration(15, 16) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "create index if not exists index_Album_title_artistId " +
                            "on Album (title, artistId)"
                )
                db.execSQL(
                    "create index if not exists index_Album_artistId on Album (artistId)"
                )
                db.execSQL(
                    "create index if not exists index_Artist_title on Artist (title)"
                )
                db.execSQL(
                    "create table if not exists `_new_SavedQueue` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "insert into `_new_SavedQueue` (id, title, createdAt, updatedAt) " +
                            "select id, coalesce(title, replace(?, '%d', id)), createdAt, updatedAt " +
                            "from `SavedQueue`",
                    arrayOf(context.getString(R.string.saved_queue_default_title))
                )
                db.execSQL("drop table `SavedQueue`")
                db.execSQL("alter table `_new_SavedQueue` rename to `SavedQueue`")
                db.execSQL(
                    "create index if not exists `index_SavedQueue_updatedAt` " +
                            "on `SavedQueue` (`updatedAt`)"
                )
            }
        }

        private val migrationFrom17To18 = object : Migration(17, 18) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "create index if not exists index_Track_albumId on Track (albumId)"
                )
                db.execSQL(
                    "update album set totalDuration = " +
                            "(select coalesce(sum(track.duration), 0) from track " +
                            "where track.albumId = album.id)"
                )
                db.execSQL(
                    "update artist set totalDuration = " +
                            "(select coalesce(sum(track.duration), 0) from track " +
                            "inner join album on track.albumId = album.id " +
                            "where album.artistId = artist.id)"
                )
            }
        }

        fun getInstance(context: Context): DB =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, DB::class.java, DB_NAME)
                    .addMigrations(
                        migrationFrom11To12(context),
                        migrationFrom13To14,
                        migrationFrom14To15,
                        migrationFrom15To16(context),
                        migrationFrom17To18
                    )
                    .build()
                    .apply { instance = this }
            }
    }

    /**
     * Fills the match keys of the tracks stored so far, and links the local tracks to the Spotify
     * tracks of the same song by them.
     */
    class LinkSpotifyTracksSpec : AutoMigrationSpec {

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val trackKeys = db.query(
                "select track.id, track.title, album.title, artist.title from track " +
                        "inner join album on album.id = track.albumId " +
                        "inner join artist on artist.id = track.artistId"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val key = spotifyMatchKeyOf(
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                        ) ?: continue
                        add(cursor.getLong(0) to key)
                    }
                }
            }
            db.compileStatement("update track set matchKey = ? where id = ?").use { statement ->
                trackKeys.forEach { (id, key) ->
                    statement.bindString(1, key)
                    statement.bindLong(2, id)
                    statement.executeUpdateDelete()
                }
            }

            val spotifyTrackKeys = db.query(
                "select uri, title, albumName, artistName from spotifytrack"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val key = spotifyMatchKeyOf(
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                        ) ?: continue
                        add(cursor.getString(0) to key)
                    }
                }
            }
            db.compileStatement("update spotifytrack set matchKey = ? where uri = ?")
                .use { statement ->
                    spotifyTrackKeys.forEach { (uri, key) ->
                        statement.bindString(1, key)
                        statement.bindString(2, uri)
                        statement.executeUpdateDelete()
                    }
                }

            db.execSQL(SpotifyTrackDao.LINK_ALL_LOCAL_TRACKS)
        }
    }

    /**
     * Fills the search texts of the lyrics stored so far.
     */
    class FillLyricSearchTextsSpec : AutoMigrationSpec {

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val converter = LyricLineConverter()
            val searchTexts = db.query("select id, lines from lyric").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val lines = converter.toLyricLineList(cursor.getString(1))
                        add(cursor.getLong(0) to lines.toSearchText())
                    }
                }
            }
            db.compileStatement("update lyric set searchText = ? where id = ?").use { statement ->
                searchTexts.forEach { (id, searchText) ->
                    statement.bindString(1, searchText)
                    statement.bindLong(2, id)
                    statement.executeUpdateDelete()
                }
            }
        }
    }

    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricDao(): LyricDao
    abstract fun trackHistoryDao(): TrackHistoryDao
    abstract fun queueHistoryDao(): QueueHistoryDao
    abstract fun savedQueueDao(): SavedQueueDao
    abstract fun spotifyTrackDao(): SpotifyTrackDao
    abstract fun spotifyContentEntryDao(): SpotifyContentEntryDao
    abstract fun equalizerPresetDao(): EqualizerPresetDao
    abstract fun audioDeviceEqualizerInfoDao(): AudioDeviceEqualizerInfoDao
}

class BoolConverter {
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