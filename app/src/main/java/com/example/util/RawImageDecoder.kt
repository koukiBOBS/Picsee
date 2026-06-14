package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object RawImageDecoder {
    fun getCachedFileForRaw(context: Context, rawFilePath: String): File {
        val cacheDir = File(context.cacheDir, "raw_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(rawFilePath)
        val fileName = "${file.absolutePath.hashCode()}_${file.lastModified()}.jpg"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        try {
            // Attempt decoding embedded JPEG thumbnail with ExifInterface
            val exif = ExifInterface(rawFilePath)
            if (exif.hasThumbnail()) {
                val thumbBytes = exif.thumbnailBytes
                if (thumbBytes != null) {
                    FileOutputStream(cachedFile).use { fos ->
                        fos.write(thumbBytes)
                    }
                    return cachedFile
                }
            }

            // Fallback: decode directly (BitmapFactory supports native formats e.g. DNG on SDK 24+)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2 // scale down for quick thumbnail
            }
            val bitmap = BitmapFactory.decodeFile(rawFilePath, options)
            if (bitmap != null) {
                FileOutputStream(cachedFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                bitmap.recycle()
                return cachedFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return original if decoding fails
        return file
    }

    fun clearRawCache(context: Context) {
        val cacheDir = File(context.cacheDir, "raw_images")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}
