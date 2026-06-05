package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.AppSettingsEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettingsEntity,
    onBack: () -> Unit,
    onSave: (dark: Boolean, autoC: Boolean, autoS: Boolean, interval: Int, ports: String) -> Unit
) {
    var darkMode by remember { mutableStateOf(currentSettings.darkMode) }
    var autoConnect by remember { mutableStateOf(currentSettings.autoConnect) }
    var autoScan by remember { mutableStateOf(currentSettings.autoScan) }
    var scanIntervalText by remember { mutableStateOf(currentSettings.scanIntervalSeconds.toString()) }
    var preferredPortsText by remember { mutableStateOf(currentSettings.preferredPorts) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Settings", 
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 18.sp
                    ) 
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("General Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                    Text("Apply dark colors to user interface", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = darkMode,
                    onCheckedChange = { darkMode = it }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto Connect", style = MaterialTheme.typography.bodyLarge)
                    Text("Open highest priority server on discovery", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Background Active Scan", style = MaterialTheme.typography.bodyLarge)
                    Text("Periodically scan local networks", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = autoScan,
                    onCheckedChange = { autoScan = it }
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = scanIntervalText,
                onValueChange = { scanIntervalText = it },
                label = { Text("Scan Interval (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = preferredPortsText,
                onValueChange = { preferredPortsText = it },
                label = { Text("Preferred Discovery Ports (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val interval = scanIntervalText.toIntOrNull() ?: 30
                    onSave(darkMode, autoConnect, autoScan, interval, preferredPortsText)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save and Close", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
