package com.example.photoserver

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

class ImageLabelingHelper(private val context: Context) {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun labelImage(uri: Uri): List<TagEntity> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val labels = labeler.process(image).await()
            labels.take(5).map { label ->
                TagEntity(
                    photoId = 0, // To be set by the caller
                    label = label.text,
                    confidence = label.confidence
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
