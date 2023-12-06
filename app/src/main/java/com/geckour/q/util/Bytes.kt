package com.geckour.q.util

import android.content.Context
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection

/**
 * @return Uri String which points stored artwork
 */
fun ByteArray.storeArtwork(context: Context): String? = catchAsNull {
    val hex = String(Hex.encodeHex(DigestUtils.md5(this)))
    val ext = URLConnection.guessContentTypeFromStream(ByteArrayInputStream(this))
        ?.replace(Regex(".+/(.+)"), ".$1")
        ?: ""
    val dirName = "images"
    val dir = File(context.dataDir, dirName)
    if (dir.exists().not()) dir.mkdir()
    val imgFile = File(dir, "$hex$ext")
    FileOutputStream(imgFile).use {
        it.write(this)
        it.flush()
    }

    return@catchAsNull imgFile.path
}