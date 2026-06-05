package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastConnected DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isFavorite = 1 ORDER BY lastConnected DESC")
    fun getFavoriteDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity): Long

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Delete
    suspend fun deleteDevice(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE ipAddress = :ip AND port = :port")
    suspend fun deleteByAddress(ip: String, port: Int)

    // Settings
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}
