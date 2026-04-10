package com.example.photoserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement?,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String
)

class McpServerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private lateinit var database: PhotoDatabase

    override fun onCreate() {
        super.onCreate()
        database = PhotoDatabase.getDatabase(this)
        startForegroundService()
        startMcpServer()
    }

    private fun startForegroundService() {
        val channelId = "mcp_server_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MCP Server Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MCP Photo Server Running")
            .setContentText("Listening for photo search requests...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()

        startForeground(1, notification)
    }

    private fun startMcpServer() {
        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json()
            }
            install(CORS) {
                anyHost()
                allowHeader("Content-Type")
            }
            routing {
                post("/mcp") {
                    val request = call.receive<McpRequest>()
                    val response = handleMcpRequest(request)
                    call.respond(response)
                }
                
                // Discovery endpoint for convenience
                get("/info") {
                    call.respond(mapOf("status" to "running", "server" to "PhotoServer MCP"))
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return when (request.method) {
            "initialize" -> McpResponse(
                id = request.id,
                result = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject {})
                    })
                    put("serverInfo", buildJsonObject {
                        put("name", "PhotoServer")
                        put("version", "1.0.0")
                    })
                }
            )
            "tools/list" -> McpResponse(
                id = request.id,
                result = buildJsonObject {
                    putJsonArray("tools") {
                        addJsonObject {
                            put("name", "search_photos")
                            put("description", "Search for photos in the gallery based on AI-generated tags (e.g., 'birthday', 'dog', 'mountain').")
                            put("inputSchema", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    putJsonObject("tag") {
                                        put("type", "string")
                                        put("description", "The tag or keyword to search for.")
                                    }
                                })
                                putJsonArray("required") { add("tag") }
                            })
                        }
                    }
                }
            )
            "tools/call" -> {
                val params = request.params
                val toolName = params?.get("name")?.jsonPrimitive?.content
                if (toolName == "search_photos") {
                    val args = params?.get("arguments")?.jsonObject
                    val tag = args?.get("tag")?.jsonPrimitive?.content ?: ""
                    val photos = database.photoDao().searchPhotosByTag(tag)
                    
                    McpResponse(
                        id = request.id,
                        result = buildJsonObject {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", "Found ${photos.size} photos for tag '$tag'.")
                                }
                                photos.forEach { photo ->
                                    val tagsStr = listOfNotNull(photo.tag1, photo.tag2, photo.tag3, photo.tag4, photo.tag5).joinToString(", ")
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", "Photo: ${photo.name}, Tags: [$tagsStr], Date: ${java.util.Date(photo.dateAdded * 1000)}, URI: ${photo.uri}")
                                    }
                                }
                            }
                        }
                    )
                } else {
                    McpResponse(
                        id = request.id,
                        error = McpError(-32601, "Tool not found")
                    )
                }
            }
            else -> McpResponse(
                id = request.id,
                error = McpError(-32601, "Method not found")
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 2000)
        serviceScope.cancel()
    }
}
