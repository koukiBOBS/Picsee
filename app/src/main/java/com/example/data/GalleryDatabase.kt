package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scanned_folders")
data class ScannedFolder(
    @PrimaryKey val path: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_files")
data class MediaFile(
    @PrimaryKey val filePath: String, // "/path/to/pic.jpg" or "/path/to/archive.zip::subfolder/pic.jpg"
    val fileName: String,
    val parentFolder: String,
    val zipFilePath: String?, // If inside a zip, this refers to "/path/to/archive.zip"
    val isRaw: Boolean,
    val size: Long,
    val dateModified: Long,
    val customGroupId: Long? = null,
    val tags: String = ""
)

@Entity(tableName = "custom_groups")
data class CustomGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM scanned_folders ORDER BY addedAt DESC")
    fun getAllFoldersFlow(): Flow<List<ScannedFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ScannedFolder)

    @Delete
    suspend fun deleteFolder(folder: ScannedFolder)
}

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files ORDER BY dateModified DESC")
    fun getAllFilesFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE parentFolder = :folderPath ORDER BY dateModified DESC")
    fun getFilesByFolderFlow(folderPath: String): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE zipFilePath = :zipPath ORDER BY dateModified DESC")
    fun getFilesByZipFlow(zipPath: String): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE customGroupId = :groupId ORDER BY dateModified DESC")
    fun getFilesByCustomGroupFlow(groupId: Long): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE customGroupId IS NULL AND zipFilePath IS NULL ORDER BY dateModified DESC")
    fun getUnassignedNonZipFilesFlow(): Flow<List<MediaFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<MediaFile>)

    @Query("DELETE FROM media_files WHERE parentFolder = :folderPath")
    suspend fun deleteFilesByFolder(folderPath: String)

    @Query("UPDATE media_files SET customGroupId = :groupId WHERE filePath = :filePath")
    suspend fun updateFileCustomGroup(filePath: String, groupId: Long?)

    @Query("UPDATE media_files SET tags = :tags WHERE filePath = :filePath")
    suspend fun updateFileTags(filePath: String, tags: String)

    @Query("UPDATE media_files SET customGroupId = NULL WHERE customGroupId = :groupId")
    suspend fun clearCustomGroupAssociations(groupId: Long)
    
    @Query("DELETE FROM media_files WHERE filePath = :filePath")
    suspend fun deleteFileByPath(filePath: String)
}

@Dao
interface CustomGroupDao {
    @Query("SELECT * FROM custom_groups ORDER BY createdAt DESC")
    fun getAllGroupsFlow(): Flow<List<CustomGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CustomGroup): Long

    @Delete
    suspend fun deleteGroup(group: CustomGroup)
}

@Database(entities = [ScannedFolder::class, MediaFile::class, CustomGroup::class], version = 2, exportSchema = false)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun customGroupDao(): CustomGroupDao

    companion object {
        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getDatabase(context: android.content.Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    "gallery_explorer_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
