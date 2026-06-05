package com.example.discovery

import android.content.Context
import com.example.database.AppSettingsEntity
import com.example.network.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DiscoveryEngine(
    private val context: Context,
    private val onDeviceDiscovered: (DiscoveredService) -> Unit
) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()

    private var scanJob: Job? = null
    private var jmdnsDiscoverer: JmDnsDiscoverer? = null

    private val discoveredKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        jmdnsDiscoverer = JmDnsDiscoverer(context)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startDiscovery(settings: AppSettingsEntity, customPort: Int? = null) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f
        discoveredKeys.clear()

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val ports = if (customPort != null) {
                    listOf(customPort)
                } else {
                    settings.preferredPorts.split(",").mapNotNull { it.trim().toIntOrNull() }
                }

                // 1. Loopback IP Tests
                val loopbacks = listOf("127.0.0.1", "localhost")
                for (ip in loopbacks) {
                    for (port in ports) {
                        if (!isActive) break
                        
                        // Favor device IP if it has the port open
                        var targetIp = ip
                        val deviceIp = NetworkUtils.getLocalIpAddress(context)
                        if (deviceIp != null && deviceIp != "127.0.0.1" && deviceIp != "localhost") {
                            val isOpenOnDeviceIp = try {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(deviceIp, port), 100)
                                    true
                                }
                            } catch (e: Exception) {
                                false
                            }
                            if (isOpenOnDeviceIp) {
                                targetIp = deviceIp
                            }
                        }

                        val service = ServiceFingerprinter.checkAndFingerprint(targetIp, port)
                        if (service != null) {
                            addDiscoveredDevice(service)
                        }
                    }
                }

                // 2. Device IP
                val deviceIp = NetworkUtils.getLocalIpAddress(context)
                if (deviceIp != null) {
                    for (port in ports) {
                        if (!isActive) break
                        val service = ServiceFingerprinter.checkAndFingerprint(deviceIp, port)
                        if (service != null) {
                            addDiscoveredDevice(service)
                        }
                    }
                }

                // 3. mDNS Service Discovery (asynchronous JmDNS)
                jmdnsDiscoverer?.startDiscovery { ip, port, name ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val service = ServiceFingerprinter.checkAndFingerprint(ip, port)
                        if (service != null) {
                            addDiscoveredDevice(service)
                        }
                    }
                }

                // 4. SSDP Service Discovery (asynchronous SSDP)
                launch {
                    SsdpDiscoverer.discoverDevices { ip, port, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val service = ServiceFingerprinter.checkAndFingerprint(ip, port)
                            if (service != null) {
                                addDiscoveredDevice(service)
                            }
                        }
                    }
                }

                // 5. Subnet Network Local Scan
                if (deviceIp != null) {
                    val subnetPrefix = NetworkUtils.getSubnetPrefix(deviceIp)
                    if (subnetPrefix != null) {
                        val totalTasks = 254 * ports.size
                        val completedTasks = AtomicInteger(0)
                        
                        // Limit pool for system stability
                        val dispatcher = Dispatchers.IO.limitedParallelism(60)
                        val jobs = mutableListOf<Deferred<Unit>>()

                        for (i in 1..254) {
                            val targetIp = "$subnetPrefix.$i"
                            if (targetIp == deviceIp) continue

                            for (port in ports) {
                                if (!isActive) break
                                val d = async(dispatcher) {
                                    try {
                                        val isPortOpen = Socket().use { socket ->
                                            socket.connect(InetSocketAddress(targetIp, port), 200)
                                            true
                                        }
                                        if (isPortOpen) {
                                            val service = ServiceFingerprinter.checkAndFingerprint(targetIp, port)
                                            if (service != null) {
                                                addDiscoveredDevice(service)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // offline
                                    } finally {
                                        val completed = completedTasks.incrementAndGet()
                                        _scanProgress.value = completed.toFloat() / totalTasks.toFloat()
                                    }
                                }
                                jobs.add(d)
                            }
                        }
                        jobs.awaitAll()
                    }
                }

                _scanProgress.value = 1.0f
            } catch (e: CancellationException) {
                // Cancelled
            } finally {
                _isScanning.value = false
                jmdnsDiscoverer?.stopDiscovery()
            }
        }
    }

    private fun addDiscoveredDevice(service: DiscoveredService) {
        val key = "${service.ipAddress}:${service.port}"
        if (!discoveredKeys.contains(key)) {
            discoveredKeys.add(key)
            onDeviceDiscovered(service)
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        _isScanning.value = false
        jmdnsDiscoverer?.stopDiscovery()
    }
}
