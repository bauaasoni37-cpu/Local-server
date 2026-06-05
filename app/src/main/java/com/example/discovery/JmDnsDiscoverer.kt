package com.example.discovery

import android.content.Context
import android.util.Log
import com.example.network.NetworkUtils
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import java.net.InetAddress

class JmDnsDiscoverer(private val context: Context) {
    private var jmdns: JmDNS? = null
    
    fun startDiscovery(onDeviceFound: (ip: String, port: Int, name: String) -> Unit) {
        val localIp = NetworkUtils.getLocalIpAddress(context) ?: return
        try {
            val inetAddress = InetAddress.getByName(localIp)
            jmdns = JmDNS.create(inetAddress, "LocalNode")

            val serviceTypes = listOf(
                "_http._tcp.local.",
                "_https._tcp.local.",
                "_ws._tcp.local.",
                "_flask._tcp.local.",
                "_ollama._tcp.local."
            )

            val listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    jmdns?.requestServiceInfo(event.type, event.name, 1)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    // Ignore
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info
                    val ip = info.hostAddress ?: ""
                    val port = info.port
                    if (ip.isNotEmpty() && port > 0) {
                        onDeviceFound(ip, port, info.name)
                    }
                }
            }

            for (type in serviceTypes) {
                jmdns?.addServiceListener(type, listener)
            }
        } catch (e: Exception) {
            Log.e("JmDnsDiscoverer", "JmDNS Setup Error: ${e.message}")
        }
    }

    fun stopDiscovery() {
        try {
            jmdns?.close()
        } catch (e: Exception) {
            // Ignore
        }
        jmdns = null
    }
}
