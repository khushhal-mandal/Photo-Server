package com.example.photoserver

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val name: String,
    val dateAdded: Long
)

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("photoId")]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    val label: String,
    val confidence: Float
)

data class PhotoWithTags(
    @Embedded val photo: PhotoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "photoId"
    )
    val tags: List<TagEntity>
)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Transaction
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    fun getAllPhotosWithTags(): Flow<List<PhotoWithTags>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Query("SELECT id FROM photos WHERE id NOT IN (SELECT DISTINCT photoId FROM tags)")
    suspend fun getUntaggedPhotoIds(): List<Long>
}

@Database(entities = [PhotoEntity::class, TagEntity::class], version = 1)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
