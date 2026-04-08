package com.example.photoserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import coil.compose.AsyncImage
import com.example.photoserver.ui.theme.PhotoServerTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        schedulePhotoTagging(this)

        setContent {
            PhotoServerTheme {
                val context = LocalContext.current
                var hasPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    hasPermission = isGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }

                if (hasPermission) {
                    PhotoGalleryScreen()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Permission required to access photos")
                    }
                }
            }
        }
    }

    private fun schedulePhotoTagging(context: android.content.Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val taggingRequest = PeriodicWorkRequestBuilder<PhotoTaggingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "PhotoTaggingWork",
            ExistingPeriodicWorkPolicy.KEEP,
            taggingRequest
        )
        
        // Also run once immediately
        val immediateRequest = OneTimeWorkRequestBuilder<PhotoTaggingWorker>().build()
        WorkManager.getInstance(context).enqueue(immediateRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen() {
    val context = LocalContext.current
    val database = remember { PhotoDatabase.getDatabase(context) }
    val photosWithTags by database.photoDao().getAllPhotosWithTags().collectAsState(initial = emptyList())
    var showTags by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Gallery") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text("Show Tags", fontSize = 14.sp)
                        Switch(
                            checked = showTags,
                            onCheckedChange = { showTags = it },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(photosWithTags) { item ->
                PhotoItem(item, showTags)
            }
        }
    }
}

@Composable
fun PhotoItem(item: PhotoWithTags, showTags: Boolean) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box {
            AsyncImage(
                model = item.photo.uri,
                contentDescription = item.photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (showTags && item.tags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp)
                        .fillMaxWidth()
                ) {
                    item.tags.forEach { tag ->
                        Text(
                            text = "${tag.label} (${(tag.confidence * 100).toInt()}%)",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            } else if (showTags && item.tags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Tagging...",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
