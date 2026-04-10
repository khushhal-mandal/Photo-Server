package com.example.photoserver

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val name: String,
    val dateAdded: Long,
    val tag1: String? = null,
    val tag2: String? = null,
    val tag3: String? = null,
    val tag4: String? = null,
    val tag5: String? = null,
    val metadata: String? = null
)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE tag1 IS NULL AND tag2 IS NULL AND tag3 IS NULL AND tag4 IS NULL AND tag5 IS NULL")
    suspend fun getUntaggedPhotos(): List<PhotoEntity>

    @Query("""
        SELECT * FROM photos 
        WHERE tag1 LIKE '%' || :query || '%' 
           OR tag2 LIKE '%' || :query || '%' 
           OR tag3 LIKE '%' || :query || '%' 
           OR tag4 LIKE '%' || :query || '%' 
           OR tag5 LIKE '%' || :query || '%'
        ORDER BY dateAdded DESC
    """)
    suspend fun searchPhotosByTag(query: String): List<PhotoEntity>
}

@Database(entities = [PhotoEntity::class], version = 2, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        fun getDatabase(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo_database"
                )
                .fallbackToDestructiveMigration() // Simplified for this refactoring
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
