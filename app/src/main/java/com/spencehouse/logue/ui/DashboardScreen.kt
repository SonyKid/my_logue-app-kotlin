package com.spencehouse.logue.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spencehouse.logue.ui.model.DashboardViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    var showPinDialog by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var showChargeDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { expanded = true }
                        ) {
                            Text(
                                text = uiState.vehicleName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Vehicle",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            uiState.vehicles.forEach { vehicle ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${vehicle.modelYear} ${vehicle.divisionName} ${vehicle.modelCode}",
                                            color = if (vehicle.vin == uiState.selectedVin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.onVehicleChange(vehicle.vin)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { viewModel.refreshData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(uiState.statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Last Updated: ${uiState.lastUpdated}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Vehicle Status Section
                item {
                    VehicleStatusCard(
                        percentage = uiState.batteryPercentage ?: 0,
                        range = uiState.range ?: 0,
                        odometer = uiState.odometer ?: 0,
                        targetLimit = uiState.targetChargeLevel,
                        isEv = uiState.isEv,
                        useMetric = uiState.useMetric,
                        chargeStatus = uiState.chargeStatus,
                        chargeVoltage = uiState.chargeVoltage,
                        isPluggedIn = uiState.isPluggedIn,
                        onSettingsClick = { showChargeDialog = true }
                    )
                }

                // Remote Commands
                item {
                    RemoteCommands(
                        onLock = { showPinDialog = "Lock Doors" to { pin -> viewModel.lockDoors(pin) } },
                        onUnlock = { showPinDialog = "Unlock Doors" to { pin -> viewModel.unlockDoors(pin) } },
                        onLights = { showPinDialog = "Flash Lights" to { pin -> viewModel.flashLights(pin) } },
                        onHorn = { showPinDialog = "Sound Horn" to { pin -> viewModel.soundHorn(pin) } }
                    )
                }

                // Climate Section
                item {
                    ClimateControl(
                        status = uiState.climateStatus,
                        useMetric = uiState.useMetric,
                        onStart = { temp ->
                            showPinDialog = "Start Climate" to { pin -> viewModel.startClimate(pin, temp) }
                        },
                        onStop = {
                            showPinDialog = "Stop Climate" to { pin -> viewModel.stopClimate(pin) }
                        }
                    )
                }

                // Tire Pressure
                item {
                    TirePressureSection(uiState.tirePressures, uiState.useMetric)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs
    showPinDialog?.let { (name, onConfirm) ->
        PinDialog(
            title = "Confirm $name",
            onDismiss = { showPinDialog = null },
            onConfirm = { pin ->
                onConfirm(pin)
                showPinDialog = null
            }
        )
    }

    if (showChargeDialog) {
        ChargeLimitDialog(
            currentLimit = uiState.targetChargeLevel,
            onDismiss = { showChargeDialog = false },
            onConfirm = { limit ->
                viewModel.setTargetChargeLevel(limit)
                showChargeDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            useMetric = uiState.useMetric,
            onUseMetricChange = { viewModel.toggleUnits() },
            onLogout = {
                viewModel.logout()
                onLogout()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VehicleStatusCard(
    percentage: Int,
    range: Int,
    odometer: Int,
    targetLimit: Int,
    isEv: Boolean,
    useMetric: Boolean,
    chargeStatus: String,
    chargeVoltage: String?,
    isPluggedIn: Boolean,
    onSettingsClick: () -> Unit
) {
    val batteryColor = when {
        percentage > 70 -> MaterialTheme.colorScheme.primary
        percentage > 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val chargeColor = if (isPluggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isEv) Icons.Default.ElectricCar else Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    if(isEv) {
                        Text("Battery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$percentage% · ${if (useMetric) "${(range * 1.609).toInt()} km" else "$range miles"} range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Odometer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (useMetric) "${(odometer * 1.609).toInt()} km" else "$odometer miles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if(isEv) {
                Spacer(modifier = Modifier.height(16.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = batteryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    val markerOffset = (maxWidth.value * (targetLimit / 100f))
                    val coercedMarkerOffset = min(markerOffset, maxWidth.value - 24.dp.value)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = coercedMarkerOffset.dp)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Charge Target",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Power, contentDescription = null, tint = chargeColor)
                        Text(chargeStatus, style = MaterialTheme.typography.bodyMedium, color = chargeColor, fontWeight = FontWeight.SemiBold)
                        if (chargeVoltage != null) {
                            Text(chargeVoltage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Target: $targetLimit%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = onSettingsClick, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Charge Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteCommands(
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onLights: () -> Unit,
    onHorn: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("REMOTE COMMANDS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton("Lock", Icons.Default.Lock, Modifier.weight(1f)) { onLock() }
                CommandButton("Unlock", Icons.Default.LockOpen, Modifier.weight(1f)) { onUnlock() }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton("Lights", Icons.Default.Highlight, Modifier.weight(1f)) { onLights() }
                CommandButton("Horn", Icons.Default.Campaign, Modifier.weight(1f)) { onHorn() }
            }
        }
    }
}

@Composable
fun CommandButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ClimateControl(status: String, useMetric: Boolean, onStart: (Int) -> Unit, onStop: () -> Unit) {
    var temp by remember { mutableIntStateOf(if (useMetric) 22 else 72) }
    val isOn = status != "OFF"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CLIMATE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { temp-- }) { Icon(Icons.Default.Remove, contentDescription = "Decrease temperature") }
                Text("$temp${if (useMetric) "°C" else "°F"}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { temp++ }) { Icon(Icons.Default.Add, contentDescription = "Increase temperature") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { if (isOn) onStop() else onStart(temp) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isOn) "STOP CLIMATE" else "START CLIMATE")
            }
        }
    }
}

@Composable
fun TirePressureSection(pressures: Map<String, Double?>, useMetric: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TIRE PRESSURE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            val labels = mapOf(
                "frontLeft" to "Front Left",
                "frontRight" to "Front Right",
                "rearLeft" to "Rear Left",
                "rearRight" to "Rear Right"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("front" to listOf("frontLeft", "frontRight"), "rear" to listOf("rearLeft", "rearRight")).forEach { (_, positions) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        positions.forEach { pos ->
                            val pressure = pressures[pos]
                            OutlinedCard(
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(labels[pos] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val displayPressure = if (pressure != null) {
                                        if (useMetric) "${pressure.toInt()} kPa" else "${(pressure * 0.145038).toInt()} PSI"
                                    } else "--"
                                    Text(displayPressure, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                 }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4) pin = it },
                label = { Text("Enter PIN") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }) { Text("Execute") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChargeLimitDialog(currentLimit: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var limit by remember { mutableFloatStateOf(currentLimit.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Charge Limit", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("${limit.toInt()}%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(
                    value = limit,
                    onValueChange = { limit = it },
                    valueRange = 50f..100f,
                    steps = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(limit.toInt()) }) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsDialog(
    useMetric: Boolean,
    onUseMetricChange: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Info") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use Metric Units")
                    Switch(checked = useMetric, onCheckedChange = { onUseMetricChange() })
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text("Logue App", style = MaterialTheme.typography.titleMedium)
                Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Logue is an open-source alternative client for Honda and Acura connected vehicles.")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Developed by mcspencehouse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { 
                        uriHandler.openUri("https://github.com/mcspencehouse")
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}