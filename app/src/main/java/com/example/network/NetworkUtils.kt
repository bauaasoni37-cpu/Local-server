package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null && wifiManager.isWifiEnabled) {
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        }
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getSubnetPrefix(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}"
        }
        return null
    }
}
