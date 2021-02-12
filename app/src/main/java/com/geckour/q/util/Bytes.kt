package com.geckour.q.util

import android.content.Context
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.FileOutputStream

/**
 * @return Uri String which points stored artwork
 */
fun ByteArray.storeArtwork(context: Context): String {
    val hex = String(Hex.encodeHex(DigestUtils.md5(this)))
    val dirName = "images"
    val dir = File(context.externalMediaDirs[0], dirName)
    if (dir.exists().not()) dir.mkdir()
    val imgFile = File(dir, hex)
    FileOutputStream(imgFile).use {
        it.write(this)
        it.flush()
    }

    return imgFile.path
}