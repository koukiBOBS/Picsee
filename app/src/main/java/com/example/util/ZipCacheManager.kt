package com.example.util

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

object ZipCacheManager {
    fun getCachedFileForZipEntry(context: Context, zipFilePath: String, entryName: String): File {
        val cacheDir = File(context.cacheDir, "zip_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // Create a unique filename for the cached entry
        val extension = entryName.substringAfterLast('.', "jpg")
        val fileName = "${zipFilePath.hashCode()}_${entryName.hashCode()}.$extension"
        val cachedFile = File(cacheDir, fileName)

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        // Extract the target file from the ZIP archive
        try {
            ZipFile(File(zipFilePath)).use { zipFile ->
                val entry = zipFile.getEntry(entryName)
                if (entry != null) {
                    zipFile.getInputStream(entry).use { inputStream ->
                        cachedFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cachedFile
    }

    fun clearZipCache(context: Context) {
        val cacheDir = File(context.cacheDir, "zip_images")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}
