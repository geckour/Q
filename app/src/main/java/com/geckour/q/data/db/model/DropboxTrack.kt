package com.geckour.q.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dropbox.core.v2.files.FileMetadata

@Entity
data class DropboxTrack(
    @PrimaryKey val id: String,
    val lastModified: Long,
    val sourcePath: String,
    val title: String,
)

fun FileMetadata.toDropboxTrack() = DropboxTrack(id, serverModified.time, pathLower, name)
