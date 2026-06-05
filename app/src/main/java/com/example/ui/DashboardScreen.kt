package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.AppSettingsEntity
import com.example.database.DeviceEntity
import com.example.discovery.DiscoveredService
import com.example.network.NetworkUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isScanning: Boolean,
    scanProgress: Float,
    discoveredServices: List<DiscoveredService>,
    history: List<DeviceEntity>,
    favorites: List<DeviceEntity>,
    settings: AppSettingsEntity,
    onStartScan: (customPort: Int?) -> Unit,
    onStopScan: () -> Unit,
    onConnectManual: (ip: String, port: Int) -> Unit,
    onConnectService: (url: String) -> Unit,
    onToggleFavorite: (DeviceEntity) -> Unit,
    onDeleteDevice: (DeviceEntity) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedPortTab by remember { mutableStateOf("All Ports") }
    var selectedSinglePort by remember { mutableStateOf<Int?>(null) }
    var customPortText by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var manualExpanded by remember { mutableStateOf(false) }

    val localIp = remember(isScanning) { NetworkUtils.getLocalIpAddress(context) ?: "Offline" }
    var activeTab by remember { mutableStateOf(0) }

    val preferredPorts = listOf(80, 443, 8080, 5000, 3000, 8000, 9000, 11434, 1883, 8888)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Node", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E293B)
                )
            )
        },
        bottomBar = {
            CustomBottomNavigation(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                discoveredCount = discoveredServices.size,
                favoritesCount = favorites.size,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusCard(
                    isScanning = isScanning,
                    connectedCount = discoveredServices.size,
                    localIp = localIp,
                    lastConnectedService = history.firstOrNull()
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LAN Scan Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                "My IP: $localIp",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tabs = listOf("All Ports", "Single Port", "Custom Port")
                            tabs.forEach { tab ->
                                CustomChip(
                                    selected = selectedPortTab == tab,
                                    onClick = { selectedPortTab = tab },
                                    label = tab
                                )
                            }
                        }

                        when (selectedPortTab) {
                            "Single Port" -> {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(preferredPorts) { port ->
                                        CustomChip(
                                            selected = selectedSinglePort == port,
                                            onClick = { selectedSinglePort = if (selectedSinglePort == port) null else port },
                                            label = port.toString()
                                        )
                                    }
                                }
                            }
                            "Custom Port" -> {
                                OutlinedTextField(
                                    value = customPortText,
                                    onValueChange = { customPortText = it },
                                    label = { Text("Custom Port Number") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        if (isScanning) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { scanProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color = Color(0xFF2563EB),
                                    trackColor = Color(0xFFE2E8F0)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Scanning Subnet...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${(scanProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                            Button(
                                onClick = onStopScan,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop Scan")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Discovery Scan", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val targetPort = when (selectedPortTab) {
                                        "Single Port" -> selectedSinglePort
                                        "Custom Port" -> customPortText.toIntOrNull()
                                        else -> null
                                    }
                                    onStartScan(targetPort)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Scan")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedPortTab == "All Ports") "Scan Subnet (Preferred Ports)" else "Run Single Port Scan",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.clickable { manualExpanded = !manualExpanded }.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, contentDescription = "Manual", tint = Color(0xFF2563EB))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Manual Node Connection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E293B))
                            }
                            Icon(
                                if (manualExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color(0xFF64748B)
                            )
                        }

                        AnimatedVisibility(
                            visible = manualExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualIp,
                                    onValueChange = { manualIp = it },
                                    label = { Text("IP Address (e.g. 192.168.1.5)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = manualPort,
                                    onValueChange = { manualPort = it },
                                    label = { Text("Port (e.g. 5000)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val portInt = manualPort.toIntOrNull()
                                        if (manualIp.isNotBlank() && portInt != null) {
                                            onConnectManual(manualIp, portInt)
                                        } else {
                                            Toast.makeText(context, "Please enter a valid IP and Port", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Connect")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Establish Direct Connection", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Beautiful header specifying the list view contents for current active tab
            item {
                Text(
                    text = when (activeTab) {
                        0 -> "DISCOVERED SERVICES (${discoveredServices.size})"
                        1 -> "FAVORITE SERVICES (${favorites.size})"
                        else -> "CONNECTION HISTORY"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            when (activeTab) {
                0 -> {
                    if (discoveredServices.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(
                                text = "No live LAN services found. Click scan above to scan loopbacks and subnet.",
                                isSearching = isScanning
                            )
                        }
                    } else {
                        items(discoveredServices) { service ->
                            DiscoveredServiceCard(
                                service = service,
                                historyList = history,
                                onConnect = { onConnectService("${service.protocol}://${service.ipAddress}:${service.port}") },
                                onCopy = {
                                    val url = "${service.protocol}://${service.ipAddress}:${service.port}"
                                    clipboardManager.setText(AnnotatedString(url))
                                    Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenBrowser = {
                                    val url = "${service.protocol}://${service.ipAddress}:${service.port}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onFavoriteToggle = {
                                    val match = history.firstOrNull { it.ipAddress == service.ipAddress && it.port == service.port }
                                    if (match != null) {
                                        onToggleFavorite(match)
                                    } else {
                                        onToggleFavorite(
                                            DeviceEntity(
                                                name = service.name,
                                                serviceType = service.serviceType,
                                                ipAddress = service.ipAddress,
                                                port = service.port,
                                                protocol = service.protocol,
                                                latencyMs = service.latencyMs,
                                                isFavorite = true
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                1 -> {
                    if (favorites.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(text = "Mark services as favorite to quickly access them offline.", isSearching = false)
                        }
                    } else {
                        items(favorites) { favorite ->
                            DeviceHistoryCard(
                                device = favorite,
                                onConnect = { onConnectService("${favorite.protocol}://${favorite.ipAddress}:${favorite.port}") },
                                onCopy = {
                                    val url = "${favorite.protocol}://${favorite.ipAddress}:${favorite.port}"
                                    clipboardManager.setText(AnnotatedString(url))
                                    Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenBrowser = {
                                    val url = "${favorite.protocol}://${favorite.ipAddress}:${favorite.port}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onFavoriteToggle = { onToggleFavorite(favorite) },
                                onDelete = { onDeleteDevice(favorite) }
                            )
                        }
                    }
                }
                2 -> {
                    if (history.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(text = "Historical connections will appear here.", isSearching = false)
                        }
                    } else {
                        items(history) { record ->
                            DeviceHistoryCard(
                                device = record,
                                onConnect = { onConnectService("${record.protocol}://${record.ipAddress}:${record.port}") },
                                onCopy = {
                                    val url = "${record.protocol}://${record.ipAddress}:${record.port}"
                                    clipboardManager.setText(AnnotatedString(url))
                                    Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenBrowser = {
                                    val url = "${record.protocol}://${record.ipAddress}:${record.port}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onFavoriteToggle = { onToggleFavorite(record) },
                                onDelete = { onDeleteDevice(record) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun StatusCard(
    isScanning: Boolean,
    connectedCount: Int,
    localIp: String,
    lastConnectedService: DeviceEntity?
) {
    val statusText = when {
        isScanning -> "Scanning..."
        else -> "Active Discovery"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD3E4FF)),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ACTIVE DISCOVERY",
                            color = Color(0xFF001C3B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            statusText,
                            color = Color(0xFF001C3B),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color(0xFF2563EB),
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF2563EB), CircleShape)
                    )
                    Text(
                        text = if (lastConnectedService != null) {
                            "Last: ${lastConnectedService.name} on ${lastConnectedService.ipAddress}:${lastConnectedService.port}"
                        } else {
                            "Monitoring $localIp"
                        },
                        color = Color(0xFF001C3B),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CustomChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF2563EB) else Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF475569),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DiscoveredServiceCard(
    service: DiscoveredService,
    historyList: List<DeviceEntity>,
    onConnect: () -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val matchedHistory = historyList.firstOrNull { it.ipAddress == service.ipAddress && it.port == service.port }
    val isFav = matchedHistory?.isFavorite ?: false

    val (iconColor, bgColor, iconVec) = when {
        service.serviceType.contains("Flask", ignoreCase = true) -> Triple(Color(0xFFEA580C), Color(0xFFFFEDD5), Icons.Default.Build)
        service.serviceType.contains("FastAPI", ignoreCase = true) -> Triple(Color(0xFF0D9488), Color(0xFFCCFBF1), Icons.Default.Build)
        service.serviceType.contains("Ollama", ignoreCase = true) -> Triple(Color(0xFF9333EA), Color(0xFFF3E8FF), Icons.Default.Info)
        service.serviceType.contains("Web", ignoreCase = true) || service.serviceType.contains("Node", ignoreCase = true) -> Triple(Color(0xFF16A34A), Color(0xFFDCFCE7), Icons.Default.Build)
        else -> Triple(Color(0xFF2563EB), Color(0xFFDBEAFE), Icons.Default.Info)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(bgColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = service.serviceType,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = service.name,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (service.ipAddress == "127.0.0.1") "TERMUX" else "LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${service.ipAddress}:${service.port} • ${service.latencyMs}ms",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${service.protocol.uppercase()} Server • ${service.serviceType}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy Link",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Open External",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceHistoryCard(
    device: DeviceEntity,
    onConnect: () -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val (iconColor, bgColor, iconVec) = when {
        device.serviceType.contains("Flask", ignoreCase = true) -> Triple(Color(0xFFEA580C), Color(0xFFFFEDD5), Icons.Default.Build)
        device.serviceType.contains("FastAPI", ignoreCase = true) -> Triple(Color(0xFF0D9488), Color(0xFFCCFBF1), Icons.Default.Build)
        device.serviceType.contains("Ollama", ignoreCase = true) -> Triple(Color(0xFF9333EA), Color(0xFFF3E8FF), Icons.Default.Info)
        device.serviceType.contains("Web", ignoreCase = true) || device.serviceType.contains("Node", ignoreCase = true) -> Triple(Color(0xFF16A34A), Color(0xFFDCFCE7), Icons.Default.Build)
        else -> Triple(Color(0xFF2563EB), Color(0xFFDBEAFE), Icons.Default.Info)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(bgColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = device.serviceType,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = device.name,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            if (device.isFavorite) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFFE4E6), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "FAV",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${device.ipAddress}:${device.port}",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${device.protocol.uppercase()} Server • ${device.serviceType}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (device.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (device.isFavorite) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy Link",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Open External",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFDA4AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePlaceholder(text: String, isSearching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.Refresh else Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF2563EB).copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun CustomBottomNavigation(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    discoveredCount: Int,
    favoritesCount: Int,
    onNavigateToSettings: () -> Unit
) {
    Surface(
        color = Color(0xFFF1F5F9), // Matching Slate-100 bg style
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    selected = activeTab == 0,
                    onClick = { onTabSelected(0) },
                    iconSelected = Icons.Default.Search,
                    iconUnselected = Icons.Default.Search,
                    label = "Discover",
                    badgeCount = discoveredCount
                )

                BottomNavItem(
                    selected = activeTab == 1,
                    onClick = { onTabSelected(1) },
                    iconSelected = Icons.Default.Favorite,
                    iconUnselected = Icons.Default.FavoriteBorder,
                    label = "Favorites",
                    badgeCount = favoritesCount
                )

                BottomNavItem(
                    selected = activeTab == 2,
                    onClick = { onTabSelected(2) },
                    iconSelected = Icons.Default.Refresh,
                    iconUnselected = Icons.Default.Refresh,
                    label = "History",
                    badgeCount = 0
                )

                BottomNavItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    iconSelected = Icons.Default.Settings,
                    iconUnselected = Icons.Default.Settings,
                    label = "Settings",
                    badgeCount = 0
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    iconUnselected: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int = 0
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Color(0xFFD3E4FF) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White,
                        ) {
                            Text(badgeCount.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) iconSelected else iconUnselected,
                    contentDescription = label,
                    tint = if (selected) Color(0xFF1D4ED8) else Color(0xFF64748B),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF1D4ED8) else Color(0xFF64748B)
        )
    }
}

