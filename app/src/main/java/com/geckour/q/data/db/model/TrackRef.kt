package com.geckour.q.data.db.model

data class TrackRef(val trackId: Long, val spotifyUri: String? = null) {

    companion object {

        const val SPOTIFY_TRACK_ID = -1L

        fun ofSpotify(uri: String): TrackRef = TrackRef(SPOTIFY_TRACK_ID, uri)
    }
}
