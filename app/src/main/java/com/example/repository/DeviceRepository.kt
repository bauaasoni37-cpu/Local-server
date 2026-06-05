package com.example.repository

import com.example.database.AppSettingsEntity
import com.example.database.DeviceDao
import com.example.database.DeviceEntity
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    
    val allHistory: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()
    val favorites: Flow<List<DeviceEntity>> = deviceDao.getFavoriteDevices()
    
    suspend fun insertOrUpdateDevice(device: DeviceEntity): Long {
        return deviceDao.insertDevice(device)
    }

    suspend fun deleteDevice(device: DeviceEntity) {
        deviceDao.deleteDevice(device)
    }

    suspend fun deleteByAddress(ip: String, port: Int) {
        deviceDao.deleteByAddress(ip, port)
    }

    val settingsFlow: Flow<AppSettingsEntity?> = deviceDao.getSettingsFlow()

    suspend fun getSettings(): AppSettingsEntity {
        var settings = deviceDao.getSettings()
        if (settings == null) {
            settings = AppSettingsEntity()
            deviceDao.saveSettings(settings)
        }
        return settings
    }

    suspend fun saveSettings(settings: AppSettingsEntity) {
        deviceDao.saveSettings(settings)
    }
}
