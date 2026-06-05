package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.AppSettingsEntity
import com.example.database.DeviceEntity
import com.example.discovery.DiscoveryEngine
import com.example.discovery.DiscoveredService
import com.example.repository.DeviceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DeviceRepository
    
    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices = _discoveredServices.asStateFlow()

    val history: StateFlow<List<DeviceEntity>>
    val favorites: StateFlow<List<DeviceEntity>>
    val settings: StateFlow<AppSettingsEntity>

    private val discoveryEngine: DiscoveryEngine

    val isScanning: StateFlow<Boolean>
    val scanProgress: StateFlow<Float>

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var highestAutoConnectedPriority = 999

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.deviceDao()
        repository = DeviceRepository(dao)

        history = repository.allHistory.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        favorites = repository.favorites.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val initialSettings = AppSettingsEntity()
        val mutableSettings = MutableStateFlow(initialSettings)
        settings = mutableSettings.asStateFlow()
        
        viewModelScope.launch {
            repository.settingsFlow.collect { savedSettings ->
                if (savedSettings != null) {
                    mutableSettings.value = savedSettings
                } else {
                    val defaultSettings = repository.getSettings()
                    mutableSettings.value = defaultSettings
                }
            }
        }

        discoveryEngine = DiscoveryEngine(application) { service ->
            addDiscoveredService(service)
        }

        isScanning = discoveryEngine.isScanning
        scanProgress = discoveryEngine.scanProgress
    }

    private fun addDiscoveredService(service: DiscoveredService) {
        val currentList = _discoveredServices.value
        if (!currentList.any { it.ipAddress == service.ipAddress && it.port == service.port }) {
            val newList = currentList + service
            _discoveredServices.value = newList

            viewModelScope.launch {
                repository.insertOrUpdateDevice(
                    DeviceEntity(
                        name = service.name,
                        serviceType = service.serviceType,
                        ipAddress = service.ipAddress,
                        port = service.port,
                        protocol = service.protocol,
                        latencyMs = service.latencyMs
                    )
                )
            }

            val currentSettings = settings.value
            if (currentSettings.autoConnect) {
                val priority = getPriorityRank(service.serviceType)
                if (priority < highestAutoConnectedPriority) {
                    highestAutoConnectedPriority = priority
                    val url = "${service.protocol}://${service.ipAddress}:${service.port}"
                    viewModelScope.launch {
                        _navigationEvent.emit(url)
                    }
                }
            }
        }
    }

    fun startScan(customPort: Int? = null) {
        _discoveredServices.value = emptyList()
        highestAutoConnectedPriority = 999
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            discoveryEngine.startDiscovery(currentSettings, customPort)
        }
    }

    fun stopScan() {
        discoveryEngine.stopDiscovery()
    }

    fun connectManually(ip: String, port: Int) {
        viewModelScope.launch {
            val url = "http://$ip:$port"
            val service = with(kotlinx.coroutines.Dispatchers.IO) {
                com.example.discovery.ServiceFingerprinter.checkAndFingerprint(ip, port)
            }
            if (service != null) {
                repository.insertOrUpdateDevice(
                    DeviceEntity(
                        name = service.name,
                        serviceType = service.serviceType,
                        ipAddress = service.ipAddress,
                        port = service.port,
                        protocol = service.protocol,
                        latencyMs = service.latencyMs
                    )
                )
                val finalUrl = "${service.protocol}://${service.ipAddress}:${service.port}"
                _navigationEvent.emit(finalUrl)
            } else {
                repository.insertOrUpdateDevice(
                    DeviceEntity(
                        name = "Manual Node",
                        serviceType = "Generic HTTP",
                        ipAddress = ip,
                        port = port,
                        protocol = "http",
                        latencyMs = 0
                    )
                )
                _navigationEvent.emit(url)
            }
        }
    }

    fun toggleFavorite(device: DeviceEntity) {
        viewModelScope.launch {
            val updated = device.copy(isFavorite = !device.isFavorite)
            repository.insertOrUpdateDevice(updated)
        }
    }

    fun deleteDevice(device: DeviceEntity) {
        viewModelScope.launch {
            repository.deleteDevice(device)
        }
    }

    fun saveSettings(dark: Boolean, autoC: Boolean, autoS: Boolean, interval: Int, ports: String) {
        viewModelScope.launch {
            val update = AppSettingsEntity(
                darkMode = dark,
                autoConnect = autoC,
                autoScan = autoS,
                scanIntervalSeconds = interval,
                preferredPorts = ports
            )
            repository.saveSettings(update)
        }
    }

    private fun getPriorityRank(serviceType: String): Int {
        return when (serviceType) {
            "Flask" -> 1
            "FastAPI" -> 2
            "Ollama" -> 3
            "WebSocket", "Socket.IO" -> 4
            "Node.js Express", "NestJS" -> 5
            "Generic HTTP" -> 6
            else -> 7
        }
    }
}
