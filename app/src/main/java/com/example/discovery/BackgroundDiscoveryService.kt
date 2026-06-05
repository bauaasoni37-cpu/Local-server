package com.example.discovery

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.database.AppDatabase
import com.example.database.DeviceEntity
import com.example.repository.DeviceRepository
import kotlinx.coroutines.*

class BackgroundDiscoveryService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var repository: DeviceRepository
    private lateinit var discoveryEngine: DiscoveryEngine
    
    private val notifiedServices = mutableSetOf<String>()

    companion object {
        private const val CHANNEL_ID = "local_node_discovery_channel"
        private const val NOTIFICATION_ID = 99
        private const val DISCOVERY_NOTIFICATION_START_ID = 1000

        fun start(context: Context) {
            val intent = Intent(context, BackgroundDiscoveryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundDiscoveryService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(this).deviceDao()
        repository = DeviceRepository(dao)

        createNotificationChannel()
        val notification = createNotification("Searching for services on LAN...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        discoveryEngine = DiscoveryEngine(this) { service ->
            val key = "${service.ipAddress}:${service.port}"
            if (!notifiedServices.contains(key)) {
                notifiedServices.add(key)
                sendNewServiceNotification(service)
                serviceScope.launch {
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
            }
        }

        startScanningLoop()
    }

    private fun startScanningLoop() {
        serviceScope.launch {
            while (isActive) {
                val settings = repository.getSettings()
                if (settings.autoScan) {
                    discoveryEngine.startDiscovery(settings)
                    
                    var elapsed = 0
                    while (discoveryEngine.isScanning.value && elapsed < 15) {
                        delay(1000)
                        elapsed++
                    }
                    discoveryEngine.stopDiscovery()
                }
                
                val intervalMs = (settings.scanIntervalSeconds * 1000L).coerceAtLeast(10000L)
                delay(intervalMs)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LAN Service Discovery",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors local network services in the background."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Local Node LAN monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendNewServiceNotification(service: DiscoveredService) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("url", "${service.protocol}://${service.ipAddress}:${service.port}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, service.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("New Local Server Found")
            .setContentText("Detected ${service.serviceType} at ${service.ipAddress}:${service.port}")
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(DISCOVERY_NOTIFICATION_START_ID + service.hashCode(), notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        discoveryEngine.stopDiscovery()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
