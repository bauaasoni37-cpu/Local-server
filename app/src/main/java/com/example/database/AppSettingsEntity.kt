package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val darkMode: Boolean = false,
    val autoConnect: Boolean = true,
    val autoScan: Boolean = true,
    val scanIntervalSeconds: Int = 30,
    val preferredPorts: String = "80,443,3000,5000,8000,8080,9000,11434,1883,8888"
)
