package com.example.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title, 
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
                    IconButton(onClick = { webView?.reload() }) {
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
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                title = view?.title ?: "Local Node Node"
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                title = "Error Loading Page"
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
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
