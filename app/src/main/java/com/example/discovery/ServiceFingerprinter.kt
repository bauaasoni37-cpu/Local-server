package com.example.discovery

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class DiscoveredService(
    val name: String,
    val serviceType: String,
    val ipAddress: String,
    val port: Int,
    val protocol: String, // http, https, ws, wss
    val latencyMs: Long
)

object ServiceFingerprinter {
    private val client = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    fun checkAndFingerprint(ipAddress: String, port: Int): DiscoveredService? {
        val startTime = System.currentTimeMillis()
        
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 400)
            }
        } catch (e: Exception) {
            return null
        }
        
        val latency = System.currentTimeMillis() - startTime

        if (isWebSocketServer(ipAddress, port)) {
            val isSocketIO = isSocketIOServer(ipAddress, port)
            return DiscoveredService(
                name = if (isSocketIO) "Socket.IO Server" else "WebSocket Server",
                serviceType = if (isSocketIO) "Socket.IO" else "WebSocket",
                ipAddress = ipAddress,
                port = port,
                protocol = "ws",
                latencyMs = latency
            )
        }

        val protocolsToTry = listOf("http", "https")
        for (protocol in protocolsToTry) {
            val url = "$protocol://$ipAddress:$port/"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; LAN Discovery)")
                    .build()

                client.newCall(request).execute().use { response ->
                    val serverHeader = response.header("Server") ?: ""
                    val poweredBy = response.header("X-Powered-By") ?: ""
                    val bodyText = response.body?.string()?.take(4000) ?: ""

                    val classification = classify(ipAddress, port, serverHeader, poweredBy, bodyText)
                    return DiscoveredService(
                        name = classification.name,
                        serviceType = classification.type,
                        ipAddress = ipAddress,
                        port = port,
                        protocol = protocol,
                        latencyMs = latency
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return DiscoveredService(
            name = "TCP Server",
            serviceType = "Generic TCP Server",
            ipAddress = ipAddress,
            port = port,
            protocol = "tcp",
            latencyMs = latency
        )
    }

    private fun isWebSocketServer(ipAddress: String, port: Int): Boolean {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 600)
                val out: OutputStream = socket.getOutputStream()
                val request = "GET / HTTP/1.1\r\n" +
                        "Host: $ipAddress:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                out.write(request.toByteArray())
                out.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val statusLine = reader.readLine() ?: ""
                if (statusLine.contains("101")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    private fun isSocketIOServer(ipAddress: String, port: Int): Boolean {
        val url = "http://$ipAddress:$port/socket.io/?EIO=4&transport=polling"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 400) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("sid") || body.contains("pingInterval")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    private data class ClassifiedResult(val name: String, val type: String)

    private fun classify(
        ipAddress: String,
        port: Int,
        serverHeader: String,
        poweredByHeader: String,
        body: String
    ): ClassifiedResult {
        val serverLower = serverHeader.lowercase()
        val poweredLower = poweredByHeader.lowercase()
        val bodyLower = body.lowercase()

        if (port == 11434 || body.contains("Ollama is running")) {
            return ClassifiedResult("Ollama Node", "Ollama")
        }

        if (bodyLower.contains("open webui") || bodyLower.contains("open-webui")) {
            return ClassifiedResult("OpenWebUI Hub", "OpenWebUI")
        }

        if (port == 1234 || bodyLower.contains("lm_studio") || bodyLower.contains("lm-studio") || bodyLower.contains("lm studio")) {
            return ClassifiedResult("LM Studio AI", "LM Studio")
        }

        if (serverLower.contains("werkzeug") || serverLower.contains("gunicorn") || bodyLower.contains("flask") || poweredLower.contains("flask")) {
            return ClassifiedResult("Flask App", "Flask")
        }

        if (bodyLower.contains("fastapi") || (bodyLower.contains("swagger ui") && bodyLower.contains("openapi")) || serverLower.contains("uvicorn")) {
            return ClassifiedResult("FastAPI Microservice", "FastAPI")
        }

        if (bodyLower.contains("django") || poweredLower.contains("django") || serverLower.contains("django")) {
            return ClassifiedResult("Django Enterprise", "Django")
        }

        if (poweredLower.contains("express") || serverLower.contains("express") || bodyLower.contains("nestjs")) {
            if (bodyLower.contains("nestjs")) {
                return ClassifiedResult("NestJS Server", "NestJS")
            }
            return ClassifiedResult("Express Server", "Node.js Express")
        }

        if (bodyLower.contains("whitelabel error page") || bodyLower.contains("spring boot") || serverLower.contains("jetty") || serverLower.contains("tomcat")) {
            return ClassifiedResult("Spring Boot API", "Spring Boot")
        }

        if (port == 8123 || bodyLower.contains("home assistant") || bodyLower.contains("/lovelace") || bodyLower.contains("home-assistant")) {
            return ClassifiedResult("Home Assistant Hub", "Home Assistant")
        }

        if (serverLower.contains("nginx")) {
            if (poweredLower.contains("php") || bodyLower.contains("php")) {
                return ClassifiedResult("PHP Nginx App", "PHP Apache")
            }
            return ClassifiedResult("Nginx Server", "Nginx")
        }

        if (serverLower.contains("apache")) {
            if (poweredLower.contains("php") || bodyLower.contains("php")) {
                return ClassifiedResult("PHP Apache App", "PHP Apache")
            }
            return ClassifiedResult("Apache Server", "PHP Apache")
        }

        if (serverLower.contains("microsoft") || serverLower.contains("iis") || poweredLower.contains("asp.net") || poweredLower.contains("dotnet")) {
            return ClassifiedResult("ASP.NET Site", "ASP.NET")
        }

        if (serverLower.contains("simplehttp") || serverLower.contains("python")) {
            return ClassifiedResult("Python Directory Server", "Python HTTP Server")
        }

        if (bodyLower.contains("node.js") || bodyLower.contains("npm")) {
            return ClassifiedResult("Node.js Dev Node", "Node.js Express")
        }

        return ClassifiedResult("HTTP Server", "Generic HTTP")
    }
}
