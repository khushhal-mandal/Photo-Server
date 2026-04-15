package com.example.photoserver

import android.content.ContentUris
import android.content.Context
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

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
        val uri = Uri.parse(photo.uri)
        
        // 1. Get Location Name and Time metadata
        val exif = getExifMetadata(uri)
        val locationName = if (exif.hasLocation) {
            getCityName(exif.lat!!, exif.lng!!)
        } else null

        val hour = Calendar.getInstance().apply { timeInMillis = photo.dateAdded * 1000 }.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..19 -> "evening"
            else -> "night"
        }

        // 2. Get AI Labels
        val helper = ImageLabelingHelper(context)
        val labels = helper.labelImage(uri)
        
        // Update the photo regardless of whether labels or location are found to mark it as "processed" 
        // in our logic (by setting at least timeOfDay)
        val updatedPhoto = photo.copy(
            location = locationName,
            timeOfDay = timeOfDay,
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

    private suspend fun getCityName(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            val address = addresses.firstOrNull()
                            continuation.resume(address?.locality ?: address?.subAdminArea ?: address?.adminArea)
                        }
                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val address = addresses?.firstOrNull()
                address?.locality ?: address?.subAdminArea ?: address?.adminArea
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifMetadata(uri: Uri): ExifData {
        val photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.setRequireOriginal(uri)
            } catch (e: Exception) {
                uri
            }
        } else {
            uri
        }
        return try {
            context.contentResolver.openInputStream(photoUri)?.use { input ->
                val exif = ExifInterface(input)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    ExifData(latLong[0].toDouble(), latLong[1].toDouble(), true)
                } else {
                    ExifData(null, null, false)
                }
            } ?: ExifData(null, null, false)
        } catch (e: Exception) {
            ExifData(null, null, false)
        }
    }

    private data class ExifData(val lat: Double?, val lng: Double?, val hasLocation: Boolean)
}
