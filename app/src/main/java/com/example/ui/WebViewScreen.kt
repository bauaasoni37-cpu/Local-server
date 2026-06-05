package com.example.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.discovery.ServiceFingerprinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

fun parseUrl(urlStr: String): Pair<String, Int> {
    return try {
        val uri = java.net.URI(urlStr)
        val host = uri.host ?: "127.0.0.1"
        var port = uri.port
        if (port == -1) {
            port = if (urlStr.startsWith("https", ignoreCase = true)) 443 else 80
        }
        Pair(host, port)
    } catch (e: Exception) {
        var clean = urlStr.removePrefix("http://").removePrefix("https://").removePrefix("tcp://")
        clean = clean.substringBefore("/")
        val parts = clean.split(":")
        val host = parts.getOrNull(0) ?: "127.0.0.1"
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        Pair(host, port)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("Loading...") }
    var webView: WebView? = null
    var progress by remember { mutableStateOf(0) }

    var hasError by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf(0) }
    var errorDescription by remember { mutableStateOf("") }
    var showDiagnosticsOverlay by remember { mutableStateOf(false) }

    // Diagnostic states
    var diagLoading by remember { mutableStateOf(false) }
    var diagTcpReachable by remember { mutableStateOf<Boolean?>(null) }
    var diagHttpReachable by remember { mutableStateOf<Boolean?>(null) }
    var diagResponseCode by remember { mutableStateOf<Int?>(null) }
    var diagServerHeader by remember { mutableStateOf<String?>(null) }
    var diagDetectedFramework by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val displayUrl = remember(url) {
        var sanitized = url
        if (url.startsWith("tcp://", ignoreCase = true)) {
            sanitized = "http://" + url.substring(6)
        } else if (url.startsWith("ws://", ignoreCase = true)) {
            sanitized = "http://" + url.substring(5)
        } else if (url.startsWith("wss://", ignoreCase = true)) {
            sanitized = "https://" + url.substring(6)
        }
        if (!sanitized.startsWith("http://", ignoreCase = true) && !sanitized.startsWith("https://", ignoreCase = true)) {
            sanitized = "http://$sanitized"
        }
        sanitized
    }

    val runDiagnostics: () -> Unit = {
        diagLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val parsed = parseUrl(displayUrl)
                    val host = parsed.first
                    val port = parsed.second

                    // 1. TCP Check
                    val tcpOk = try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(host, port), 1200)
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                    withContext(Dispatchers.Main) { diagTcpReachable = tcpOk }

                    // 2. HTTP Check
                    var httpOk = false
                    var respCode: Int? = null
                    var sHeader: String? = null
                    var framework = "Unknown"

                    if (tcpOk) {
                        try {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                                .readTimeout(1500, TimeUnit.MILLISECONDS)
                                .followRedirects(true)
                                .build()

                            val request = Request.Builder()
                                .url(displayUrl)
                                .header("User-Agent", "Mozilla/5.0 (Android; LAN Diagnostics)")
                                .build()

                            client.newCall(request).execute().use { response ->
                                httpOk = true
                                respCode = response.code
                                sHeader = response.header("Server") ?: ""
                                val powered = response.header("X-Powered-By") ?: ""
                                val bodyText = response.body?.string()?.take(4000) ?: ""

                                val frameworkPair = ServiceFingerprinter.determineFramework(host, port, sHeader ?: "", powered, bodyText)
                                framework = frameworkPair.second
                            }
                        } catch (e: Exception) {
                            // Fallback raw HTTP GET
                            try {
                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(host, port), 1000)
                                    val out = socket.getOutputStream()
                                    out.write("GET / HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.flush()

                                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                                    val statusLine = reader.readLine() ?: ""
                                    if (statusLine.uppercase().startsWith("HTTP/")) {
                                        httpOk = true
                                        val parts = statusLine.split(" ")
                                        if (parts.size >= 2) {
                                            respCode = parts[1].toIntOrNull()
                                        }
                                        var line: String?
                                        var count = 0
                                        while (reader.readLine().also { line = it } != null && count < 20) {
                                            count++
                                            if (line.isNullOrBlank()) break
                                            if (line!!.startsWith("Server:", ignoreCase = true)) {
                                                sHeader = line!!.substringAfter(":").trim()
                                            }
                                        }
                                        val frameworkPair = ServiceFingerprinter.determineFramework(host, port, sHeader ?: "", "", "")
                                        framework = frameworkPair.second
                                    }
                                }
                            } catch (ex: Exception) {
                                // Ignore
                            }
                        }
                    } else {
                        framework = "Unreachable"
                    }

                    withContext(Dispatchers.Main) {
                        diagHttpReachable = httpOk
                        diagResponseCode = respCode
                        diagServerHeader = sHeader
                        diagDetectedFramework = framework
                        diagLoading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        diagLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(displayUrl) {
        runDiagnostics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (hasError) "Connection Diagnostics" else title,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp
                        )
                        Text(
                            text = displayUrl,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showDiagnosticsOverlay = !showDiagnosticsOverlay
                            if (showDiagnosticsOverlay) runDiagnostics()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Show Diagnostics",
                            tint = if (showDiagnosticsOverlay) Color(0xFF2563EB) else Color(0xFF0F172A)
                        )
                    }

                    IconButton(onClick = {
                        hasError = false
                        runDiagnostics()
                        webView?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Main Content Area: WebView or Full Failure Screen
            if (!hasError) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, webUrl: String?) {
                                    if (!hasError) {
                                        title = view?.title ?: "Web Page"
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // intercept only main frame errors
                                    if (request?.isForMainFrame == true) {
                                        hasError = true
                                        errorCode = error?.errorCode ?: -1
                                        errorDescription = error?.description?.toString() ?: "Connection timed out"
                                        runDiagnostics()
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }

                            loadUrl(displayUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }
            } else {
                // Connection Failure Screen which features diagnostics
                ConnectionDiagnosticsLayout(
                    url = displayUrl,
                    errorCode = errorCode,
                    errorDescription = errorDescription,
                    diagLoading = diagLoading,
                    tcpOk = diagTcpReachable,
                    httpOk = diagHttpReachable,
                    responseCode = diagResponseCode,
                    server = diagServerHeader,
                    framework = diagDetectedFramework,
                    onRetry = {
                        hasError = false
                        runDiagnostics()
                        webView?.reload()
                    }
                )
            }

            // Expandable floating info overlay
            AnimatedVisibility(
                visible = showDiagnosticsOverlay && !hasError,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Live Server Diagnostics",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { showDiagnosticsOverlay = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DiagnosticsMetricsList(
                            diagLoading = diagLoading,
                            tcpOk = diagTcpReachable,
                            httpOk = diagHttpReachable,
                            responseCode = diagResponseCode,
                            server = diagServerHeader,
                            framework = diagDetectedFramework
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { runDiagnostics() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-diagnose Node", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionDiagnosticsLayout(
    url: String,
    errorCode: Int,
    errorDescription: String,
    diagLoading: Boolean,
    tcpOk: Boolean?,
    httpOk: Boolean?,
    responseCode: Int?,
    server: String?,
    framework: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFFFE4E6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFE11D48),
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Node Unreachable",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Error Code: $errorCode • $errorDescription",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "DIAGNOSTIC TELEMETRY REPORT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                DiagnosticsMetricsList(
                    diagLoading = diagLoading,
                    tcpOk = tcpOk,
                    httpOk = httpOk,
                    responseCode = responseCode,
                    server = server,
                    framework = framework
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry & Refresh", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Simple tips log
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "💡 Quick Tips for Flask & LAN Node Servers:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF1D4ED8)
                )
                Text(
                    text = "• Verify that Flask is running and bound to 0.0.0.0 (not 127.0.0.1)\n" +
                            "• Ensure both Android device and the host machine are on the same Wi-Fi\n" +
                            "• Check Termux execution logs or desktop local firewalls",
                    fontSize = 11.sp,
                    color = Color(0xFF1E3A8A),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun DiagnosticsMetricsList(
    diagLoading: Boolean,
    tcpOk: Boolean?,
    httpOk: Boolean?,
    responseCode: Int?,
    server: String?,
    framework: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiagnosticRowItem(
            label = "TCP Reachable",
            statusText = when {
                diagLoading -> "Testing..."
                tcpOk == true -> "Yes (Socket connection open)"
                tcpOk == false -> "No (Port closed / filtered)"
                else -> "Pending"
            },
            statusState = when {
                diagLoading -> 0
                tcpOk == true -> 1
                tcpOk == false -> -1
                else -> 2
            }
        )

        DiagnosticRowItem(
            label = "HTTP Reachable",
            statusText = when {
                diagLoading -> "Testing..."
                httpOk == true -> "Yes (Valid handshake)"
                httpOk == false -> "No (Server did not respond)"
                else -> "Pending"
            },
            statusState = when {
                diagLoading -> 0
                httpOk == true -> 1
                httpOk == false -> -1
                else -> 2
            }
        )

        DiagnosticRowItem(
            label = "Response Code",
            statusText = when {
                diagLoading -> "Testing..."
                responseCode != null -> "$responseCode"
                else -> "-"
            },
            statusState = when {
                diagLoading -> 0
                responseCode != null && responseCode in 200..399 -> 1
                responseCode != null -> -1
                else -> 2
            }
        )

        DiagnosticRowItem(
            label = "Server Signature",
            statusText = when {
                diagLoading -> "Testing..."
                !server.isNullOrBlank() -> server
                else -> "-"
            },
            statusState = when {
                diagLoading -> 0
                !server.isNullOrBlank() -> 1
                else -> 2
            }
        )

        DiagnosticRowItem(
            label = "Detected Framework",
            statusText = when {
                diagLoading -> "Profiling..."
                !framework.isNullOrBlank() -> framework
                else -> "-"
            },
            statusState = when {
                diagLoading -> 0
                !framework.isNullOrBlank() && framework != "Unknown" -> 1
                else -> 2
            }
        )
    }
}

@Composable
fun DiagnosticRowItem(
    label: String,
    statusText: String,
    statusState: Int // 0: loading, 1: ok, -1: error, 2: grey info
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = when (statusState) {
                    1 -> Color(0xFF16A34A)
                    -1 -> Color(0xFFDC2626)
                    0 -> Color(0xFFD97706)
                    else -> Color(0xFF64748B)
                },
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = when (statusState) {
                        1 -> Color(0xFFDCFCE7)
                        -1 -> Color(0xFFFEE2E2)
                        0 -> Color(0xFFFEF3C7)
                        else -> Color(0xFFF1F5F9)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (statusState) {
                1 -> Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                -1 -> Icon(Icons.Default.Warning, contentDescription = "Failed", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                0 -> CircularProgressIndicator(color = Color(0xFFD97706), strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                else -> Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
            }
        }
    }
}
