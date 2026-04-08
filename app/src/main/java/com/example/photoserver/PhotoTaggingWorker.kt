package com.example.photoserver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PhotoTaggingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = PhotoDatabase.getDatabase(applicationContext)
        val repository = PhotoRepository(applicationContext, database)
        val dao = database.photoDao()

        // 1. Sync from MediaStore
        repository.refreshPhotos()

        // 2. Identify untagged photos
        val untaggedIds = dao.getUntaggedPhotoIds()

        // 3. Tag them one by one
        untaggedIds.forEach { id ->
            repository.tagPhoto(id)
        }

        return Result.success()
    }
}
