package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.MyraBiometricAuthenticator
import com.example.ui.MyraViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class QuickControlItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun PhoneControlScreen(
    viewModel: MyraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var appSearch by remember { mutableStateOf("") }
    var showCallDialog by remember { mutableStateOf(false) }
    var showNavDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, appSearch) {
        if (appSearch.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(appSearch, ignoreCase = true) }
    }

    if (showCallDialog) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCallDialog = false },
            containerColor = GlassCardBg,
            title = { Text("Confirm Phone Call", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number to dial") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val requiresBiometric = settings.biometricLockEnabled && settings.biometricRequireForSensitiveActions
                        val activity = context as? FragmentActivity
                        if (requiresBiometric && activity != null) {
                            MyraBiometricAuthenticator.authenticate(
                                activity = activity,
                                title = "Confirm Phone Call",
                                subtitle = "Verify fingerprint or face before dialing $number",
                                onSuccess = {
                                    viewModel.confirmPhoneCall(number)
                                    showCallDialog = false
                                },
                                onError = { err -> viewModel.showMessage(err) }
                            )
                        } else {
                            viewModel.confirmPhoneCall(number)
                            showCallDialog = false
                        }
                    },
                    enabled = number.isNotBlank()
                ) { Text("Confirm & Dial") }
            },
            dismissButton = {
                TextButton(onClick = { showCallDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNavDialog) {
        var destination by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNavDialog = false },
            containerColor = GlassCardBg,
            title = { Text("Launch Navigation", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination (e.g., India Gate)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.launchNav(destination)
                        showNavDialog = false
                    }
                ) { Text("Start Navigation") }
            },
            dismissButton = {
                TextButton(onClick = { showNavDialog = false }) { Text("Cancel") }
            }
        )
    }

    val quickActions = listOf(
        QuickControlItem("Camera", "Open camera", Icons.Default.CameraAlt, LuminousCyan, "ctrl_camera") {
            viewModel.openCamera()
        },
        QuickControlItem("Flashlight", "Toggle torch", Icons.Default.FlashlightOn, AmberGlow, "ctrl_torch") {
            viewModel.toggleFlashlight()
        },
        QuickControlItem("Wi-Fi", "Wi-Fi panel", Icons.Default.Wifi, CyanSoft, "ctrl_wifi") {
            viewModel.openWifi()
        },
        QuickControlItem("Bluetooth", "Pair devices", Icons.Default.Bluetooth, ElectricViolet, "ctrl_bt") {
            viewModel.openBluetooth()
        },
        QuickControlItem("WhatsApp", "Open chats", Icons.Default.Chat, EmeraldPulse, "ctrl_whatsapp") {
            viewModel.openWhatsApp()
        },
        QuickControlItem("Prepare SMS", "Compose SMS", Icons.Default.Sms, LuminousCyan, "ctrl_sms") {
            viewModel.prepareSms()
        },
        QuickControlItem("Phone Call", "With confirm", Icons.Default.Call, EmeraldPulse, "ctrl_call") {
            showCallDialog = true
        },
        QuickControlItem("Set Alarm", "07:00 AM", Icons.Default.Alarm, AmberGlow, "ctrl_alarm") {
            viewModel.setQuickAlarm(7, 0)
        },
        QuickControlItem("Navigate", "Maps route", Icons.Default.Navigation, ElectricViolet, "ctrl_nav") {
            showNavDialog = true
        },
        QuickControlItem("Settings", "System prefs", Icons.Default.Settings, TextSecondary, "ctrl_settings") {
            viewModel.openSettings()
        },
        QuickControlItem("Home Shortcut", "1-tap Voice", Icons.Default.AddToHomeScreen, LuminousCyan, "ctrl_shortcut") {
            viewModel.pinHomeShortcut()
        },
        QuickControlItem("Full-Body Widget", "Phone Home", Icons.Default.AddToHomeScreen, EmeraldPulse, "ctrl_fullbody_widget") {
            viewModel.pinFullBodyHomeWidget()
        }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 92.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Android Phone Control", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Real Android Intents, Hardware & Installed Apps", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                IconButton(onClick = { viewModel.refreshDeviceState() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry", tint = LuminousCyan)
                }
            }
        }

        // Live Battery Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Status",
                            tint = if (battery.levelPercent > 25) EmeraldPulse else AmberGlow,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Battery ${battery.levelPercent}% • ${battery.statusLabel}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Temp: ${battery.temperatureCelsius}°C • Voltage: ${battery.voltageMillivolts} mV",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Quick Controls Grid
        item {
            Text("System Controls", style = MaterialTheme.typography.titleMedium, color = CyanSoft)
            Spacer(modifier = Modifier.height(6.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickActions, key = { it.tag }) { action ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = action.onClick)
                            .testTag(action.tag),
                        shape = RoundedCornerShape(16.dp),
                        color = GlassCardBg,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(action.icon, contentDescription = action.title, tint = action.tint, modifier = Modifier.size(22.dp))
                            Text(action.title, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(action.subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Installed Apps Discovery & Launcher
        item {
            Text(
                text = "Installed Apps (${filteredApps.size})",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricViolet
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = appSearch,
                onValueChange = { appSearch = it },
                placeholder = { Text("Search installed apps to open...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanSoft) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LuminousCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassCardBg,
                    unfocusedContainerColor = GlassCardBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth().testTag("app_search_input")
            )
        }

        items(count = filteredApps.size, key = { filteredApps[it].packageName }) { idx ->
            val app = filteredApps[idx]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.launchApp(app.packageName) }
                    .testTag("installed_app_${app.packageName}"),
                shape = RoundedCornerShape(14.dp),
                color = RoyalPurpleDeep.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = LuminousCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(app.appName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("Open", style = MaterialTheme.typography.labelMedium, color = EmeraldPulse, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
