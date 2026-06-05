package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.discovery.BackgroundDiscoveryService
import com.example.ui.DashboardScreen
import com.example.ui.SettingsScreen
import com.example.ui.WebViewScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("LocalNodeMulticastLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        requestNotificationPermission()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            
            val isDark = when {
                settings.darkMode -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    val startUrl = intent.getStringExtra("url")
                    LaunchedEffect(startUrl) {
                        if (startUrl != null) {
                            val encoded = URLEncoder.encode(startUrl, "UTF-8")
                            navController.navigate("webview?url=$encoded")
                        }
                    }

                    LaunchedEffect(viewModel.navigationEvent) {
                        viewModel.navigationEvent.collect { targetUrl ->
                            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
                            navController.navigate("webview?url=$encoded")
                        }
                    }

                    LaunchedEffect(settings.autoScan) {
                        if (settings.autoScan) {
                            BackgroundDiscoveryService.start(this@MainActivity)
                        } else {
                            BackgroundDiscoveryService.stop(this@MainActivity)
                        }
                    }

                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                            val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
                            val discovered by viewModel.discoveredServices.collectAsStateWithLifecycle()
                            val history by viewModel.history.collectAsStateWithLifecycle()
                            val favorites by viewModel.favorites.collectAsStateWithLifecycle()

                            DashboardScreen(
                                isScanning = isScanning,
                                scanProgress = scanProgress,
                                discoveredServices = discovered,
                                history = history,
                                favorites = favorites,
                                settings = settings,
                                onStartScan = { customPort -> viewModel.startScan(customPort) },
                                onStopScan = { viewModel.stopScan() },
                                onConnectManual = { ip, port -> viewModel.connectManually(ip, port) },
                                onConnectService = { url -> 
                                    val encoded = URLEncoder.encode(url, "UTF-8")
                                    navController.navigate("webview?url=$encoded")
                                },
                                onToggleFavorite = { device -> viewModel.toggleFavorite(device) },
                                onDeleteDevice = { device -> viewModel.deleteDevice(device) },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable(
                            "webview?url={url}",
                            arguments = listOf(navArgument("url") { defaultValue = "" })
                        ) { backStackEntry ->
                            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                            val url = URLDecoder.decode(encodedUrl, "UTF-8")
                            WebViewScreen(
                                url = url,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                currentSettings = settings,
                                onBack = { navController.popBackStack() },
                                onSave = { dark, autoC, autoS, interval, ports ->
                                    viewModel.saveSettings(dark, autoC, autoS, interval, ports)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}

@Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Text("Hello $name!", modifier = modifier)
}
