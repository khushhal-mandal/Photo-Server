package com.example.photoserver

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context, private val database: PhotoDatabase) {
    private val photoDao = database.photoDao()

    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    suspend fun refreshPhotos() {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val photo = PhotoEntity(id, uri, name, dateAdded)
                    photoDao.insertPhoto(photo)
                }
            }
        }
    }

    suspend fun tagPhoto(photoId: Long) {
        val photo = photoDao.getPhotoById(photoId) ?: return
        val helper = ImageLabelingHelper(context)
        val labels = helper.labelImage(android.net.Uri.parse(photo.uri))
        if (labels.isNotEmpty()) {
            val updatedPhoto = photo.copy(
                tag1 = labels.getOrNull(0)?.text,
                tag1Confidence = labels.getOrNull(0)?.confidence,
                tag2 = labels.getOrNull(1)?.text,
                tag2Confidence = labels.getOrNull(1)?.confidence,
                tag3 = labels.getOrNull(2)?.text,
                tag3Confidence = labels.getOrNull(2)?.confidence,
                tag4 = labels.getOrNull(3)?.text,
                tag4Confidence = labels.getOrNull(3)?.confidence,
                tag5 = labels.getOrNull(4)?.text,
                tag5Confidence = labels.getOrNull(4)?.confidence
            )
            photoDao.updatePhoto(updatedPhoto)
        }
    }
}
