package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class GalleryRepository(private val db: GalleryDatabase) {

    val foldersFlow: Flow<List<ScannedFolder>> = db.folderDao().getAllFoldersFlow()
    val mediaFilesFlow: Flow<List<MediaFile>> = db.mediaFileDao().getAllFilesFlow()
    val customGroupsFlow: Flow<List<CustomGroup>> = db.customGroupDao().getAllGroupsFlow()

    fun getFilesByFolderFlow(folderPath: String): Flow<List<MediaFile>> =
        db.mediaFileDao().getFilesByFolderFlow(folderPath)

    fun getFilesByZipFlow(zipPath: String): Flow<List<MediaFile>> =
        db.mediaFileDao().getFilesByZipFlow(zipPath)

    fun getFilesByCustomGroupFlow(groupId: Long): Flow<List<MediaFile>> =
        db.mediaFileDao().getFilesByCustomGroupFlow(groupId)

    fun getUnassignedNonZipFilesFlow(): Flow<List<MediaFile>> =
        db.mediaFileDao().getUnassignedNonZipFilesFlow()

    suspend fun addFolder(path: String, context: Context) = withContext(Dispatchers.IO) {
        db.folderDao().insertFolder(ScannedFolder(path))
        scanFolder(context, path)
    }

    suspend fun removeFolder(folder: ScannedFolder) = withContext(Dispatchers.IO) {
        db.folderDao().deleteFolder(folder)
        db.mediaFileDao().deleteFilesByFolder(folder.path)
    }

    suspend fun addCustomGroup(name: String): Long = withContext(Dispatchers.IO) {
        db.customGroupDao().insertGroup(CustomGroup(name = name))
    }

    suspend fun deleteCustomGroup(group: CustomGroup) = withContext(Dispatchers.IO) {
        db.mediaFileDao().clearCustomGroupAssociations(group.id)
        db.customGroupDao().deleteGroup(group)
    }

    suspend fun updateFileGroup(filePath: String, groupId: Long?) = withContext(Dispatchers.IO) {
        db.mediaFileDao().updateFileCustomGroup(filePath, groupId)
    }

    suspend fun updateFileTags(filePath: String, tags: String) = withContext(Dispatchers.IO) {
        db.mediaFileDao().updateFileTags(filePath, tags)
    }

    suspend fun scanAllFolders(context: Context) = withContext(Dispatchers.IO) {
        val dbInstance = GalleryDatabase.getDatabase(context)
        val folderDao = dbInstance.folderDao()
        val allFolders = folderDao.getAllFoldersFlow()
        allFolders.first().forEach { folder ->
            scanFolder(context, folder.path)
        }
    }

    suspend fun scanFolder(context: Context, folderPath: String) = withContext(Dispatchers.IO) {
        val folderFile = File(folderPath)
        if (!folderFile.exists() || !folderFile.isDirectory) return@withContext

        val scannedFiles = mutableListOf<MediaFile>()
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
        val rawExtensions = setOf("dng", "cr2", "cr3", "nef", "arw", "orf", "rw2", "pef")

        fun scanRecursive(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanRecursive(file)
                } else {
                    val ext = file.extension.lowercase()
                    if (imageExtensions.contains(ext)) {
                        scannedFiles.add(
                            MediaFile(
                                filePath = file.absolutePath,
                                fileName = file.name,
                                parentFolder = folderPath,
                                zipFilePath = null,
                                isRaw = false,
                                size = file.length(),
                                dateModified = file.lastModified()
                            )
                        )
                    } else if (rawExtensions.contains(ext)) {
                        scannedFiles.add(
                            MediaFile(
                                filePath = file.absolutePath,
                                fileName = file.name,
                                parentFolder = folderPath,
                                zipFilePath = null,
                                isRaw = true,
                                size = file.length(),
                                dateModified = file.lastModified()
                            )
                        )
                    } else if (ext == "zip") {
                        try {
                            ZipFile(file).use { zipFile ->
                                val entries = zipFile.entries()
                                while (entries.hasMoreElements()) {
                                    val entry = entries.nextElement()
                                    if (!entry.isDirectory) {
                                        val entryExt = entry.name.substringAfterLast('.', "").lowercase()
                                        if (imageExtensions.contains(entryExt) || rawExtensions.contains(entryExt)) {
                                            val subFileName = entry.name.substringAfterLast('/')
                                            scannedFiles.add(
                                                MediaFile(
                                                    filePath = "${file.absolutePath}::${entry.name}",
                                                    fileName = subFileName,
                                                    parentFolder = folderPath,
                                                    zipFilePath = file.absolutePath,
                                                    isRaw = rawExtensions.contains(entryExt),
                                                    size = entry.size,
                                                    dateModified = entry.time
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        scanRecursive(folderFile)

        db.mediaFileDao().deleteFilesByFolder(folderPath)
        db.mediaFileDao().insertFiles(scannedFiles)
    }
}


