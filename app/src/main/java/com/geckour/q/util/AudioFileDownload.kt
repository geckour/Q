package com.geckour.q.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val AUDIO_DIR_NAME = "audio"

private const val DOWNLOAD_BUFFER_SIZE = 16 * 1024

private const val DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 30L

private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L

private val downloadClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

fun saveTempAudioFileFromUrl(
    context: Context,
    id: String,
    pathLower: String,
    url: String,
): Flow<Pair<File, Long?>> = saveFileFromUrl(tempAudioFile(context, id, pathLower), url)

fun saveAudioFileFromUrl(
    context: Context,
    id: String,
    pathLower: String,
    url: String,
): Flow<Pair<File, Long?>> = saveFileFromUrl(audioFile(context, id, pathLower), url)

private fun tempAudioFile(context: Context, id: String, pathLower: String): File {
    val dir = File(context.cacheDir, AUDIO_DIR_NAME)
    val file = File(dir, "temp_$id.${pathLower.getExtension()}")

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    return file
}

private fun audioFile(context: Context, id: String, pathLower: String): File {
    val dir = File(context.dataDir, AUDIO_DIR_NAME)
    val file = File(dir, "$id.${pathLower.getExtension()}")

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    return file
}

class DownloadFailedException(val code: Int) : IOException("Failed to download: $code")

private fun saveFileFromUrl(
    file: File,
    url: String,
): Flow<Pair<File, Long?>> {
    return callbackFlow {
        downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful.not()) {
                throw DownloadFailedException(response.code)
            }

            response.body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var processed = 0L
                    while (true) {
                        ensureActive()

                        val read = input.read(buffer)
                        if (read < 0) break

                        output.write(buffer, 0, read)
                        processed += read
                        trySend(file to processed)
                    }
                }
            }
        }
        trySend(file to null)
        channel.close()
    }.flowOn(Dispatchers.IO)
}
