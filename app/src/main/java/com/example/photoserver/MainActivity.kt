package com.example.photoserver

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.work.*
import coil.compose.AsyncImage
import com.example.photoserver.ui.theme.PhotoServerTheme
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        schedulePhotoTagging(this)
        startMcpService()

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

    private fun startMcpService() {
        val intent = Intent(this, McpServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
    val photos by database.photoDao().getAllPhotos().collectAsState(initial = emptyList())
    var showTags by remember { mutableStateOf(true) }
    var selectedPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

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
            items(photos) { photo ->
                PhotoItem(
                    photo = photo,
                    showTags = showTags,
                    onClick = { selectedPhoto = photo }
                )
            }
        }
    }

    selectedPhoto?.let { photo ->
        FullScreenImageOverlay(
            photo = photo,
            onDismiss = { selectedPhoto = null }
        )
    }
}

@Composable
fun PhotoItem(photo: PhotoEntity, showTags: Boolean, onClick: () -> Unit) {
    val tags = listOfNotNull(photo.tag1, photo.tag2, photo.tag3, photo.tag4, photo.tag5)
    val isTagged = tags.isNotEmpty()

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (showTags) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp)
                        .fillMaxWidth()
                ) {
                    if (isTagged) {
                        tags.take(2).forEach { tag ->
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                        if (tags.size > 2) {
                            Text(
                                text = "+${tags.size - 2} more",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    } else {
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
}

@Composable
fun FullScreenImageOverlay(photo: PhotoEntity, onDismiss: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() },
                contentScale = ContentScale.Fit
            )

            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Info Button
            IconButton(
                onClick = { showInfo = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
            }
        }
    }

    if (showInfo) {
        ImageInfoDialog(
            photo = photo,
            onDismiss = { showInfo = false }
        )
    }
}

@Composable
fun ImageInfoDialog(photo: PhotoEntity, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(photo.dateAdded * 1000))
    val tags = listOfNotNull(photo.tag1, photo.tag2, photo.tag3, photo.tag4, photo.tag5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "Name", value = photo.name)
                InfoRow(label = "Date", value = dateString)
                InfoRow(label = "URI", value = photo.uri)
                
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tags:", fontWeight = FontWeight.Bold)
                    tags.forEach { tag ->
                        Text("- $tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
