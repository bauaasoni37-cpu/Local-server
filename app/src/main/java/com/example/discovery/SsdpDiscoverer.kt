package com.example.discovery

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.regex.Pattern

object SsdpDiscoverer {
    private const val SSDP_PORT = 1900
    private const val SSDP_IP = "239.255.255.250"
    
    fun discoverDevices(onDeviceFound: (ip: String, port: Int, details: String) -> Unit) {
        val message = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: ssdp:all\r\n\r\n"

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 2000
            
            val sendPacket = DatagramPacket(
                message.toByteArray(),
                message.length,
                InetAddress.getByName(SSDP_IP),
                SSDP_PORT
            )
            socket.send(sendPacket)

            val buffer = ByteArray(2048)
            val receivePacket = DatagramPacket(buffer, buffer.size)

            val endTime = System.currentTimeMillis() + 2500
            while (System.currentTimeMillis() < endTime) {
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    parseSsdpResponse(response, onDeviceFound)
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("SsdpDiscoverer", "SSDP Error: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun parseSsdpResponse(response: String, onDeviceFound: (ip: String, port: Int, details: String) -> Unit) {
        val pattern = Pattern.compile("LOCATION:\\s*(http://|https://)([^/\\r\\n]+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(response)
        if (matcher.find()) {
            val urlPart = matcher.group(2) ?: return
            val parts = urlPart.split(":")
            val ip = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 80 else 80
            onDeviceFound(ip, port, "SSDP Location: $urlPart")
        }
    }
}
