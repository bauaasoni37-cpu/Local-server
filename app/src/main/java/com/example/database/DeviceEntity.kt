package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val serviceType: String, // e.g. Flask, FastAPI, Ollama, WebSocket, Express, Generic
    val ipAddress: String,
    val port: Int,
    val protocol: String, // http, https, ws, wss
    val latencyMs: Long,
    val isFavorite: Boolean = false,
    val lastConnected: Long = System.currentTimeMillis()
)
