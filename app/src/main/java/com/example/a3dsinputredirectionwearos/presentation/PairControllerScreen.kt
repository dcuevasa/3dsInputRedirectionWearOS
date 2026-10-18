package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge
import com.example.a3dsinputredirectionwearos.bluetooth.DiscoveredController

@Composable
fun PairControllerScreen(
    bridge: InputRedirectionBridge,
    onRequestPermissions: () -> Unit,
    onOpenMapping: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = bridge.bluetoothControllerManager
    val listState = rememberTransformingLazyColumnState()

    val isBtEnabled by manager.isBluetoothEnabled.collectAsState()
    val isScanning by manager.isScanning.collectAsState()
    val elapsedSec by manager.scanElapsedSeconds.collectAsState()
    val statusMsg by manager.statusMessage.collectAsState()
    val detailLog by manager.detailLog.collectAsState()
    val activeActionAddr by manager.activeActionAddress.collectAsState()
    val permissionsGranted by manager.permissionsGranted.collectAsState()
    val locationEnabled by manager.locationEnabled.collectAsState()

    val connectedInputDevices by manager.connectedInputControllers.collectAsState()
    val discoveredDevices by manager.discoveredControllers.collectAsState()
    val pairedDevices by manager.pairedControllers.collectAsState()

    // Automatically refresh & start scanning when opening this screen
    LaunchedEffect(Unit) {
        manager.refreshDevices()
        if (manager.hasScanPermission() && manager.hasConnectPermission()) {
            manager.startScan()
        } else {
            onRequestPermissions()
        }
    }

    // Stop scanning when leaving this screen to save watch battery & radio bandwidth
    DisposableEffect(Unit) {
        onDispose {
            manager.stopScan()
        }
    }

    val pulseDots = when (elapsedSec % 3) {
        0 -> "● ○ ○"
        1 -> "○ ● ○"
        else -> "○ ○ ●"
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    ListHeader {
                        Text(
                            text = "Pair Controller",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // UNIFIED TOP STATUS & SCAN CONTROL BOX (Text + Button right together!)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Surface)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1. Unmistakable Live Search Indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isScanning) Color(0xFF1B4332) else Color(0xFF2B2D42)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = if (isScanning) {
                                    "$pulseDots SEARCHING (${elapsedSec}s)"
                                } else {
                                    "⏸ NOT SEARCHING"
                                },
                                color = if (isScanning) AppColors.Start else AppColors.Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 2. Primary Status What Is Happening Now
                        Text(
                            text = statusMsg,
                            color = AppColors.OnSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(2.dp))

                        // 3. Detailed Step Log
                        Text(
                            text = detailLog,
                            color = AppColors.Primary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        // 4. Start / Stop Scan Button RIGHT NEXT TO THE STATUS TEXT
                        Button(
                            onClick = {
                                if (!permissionsGranted) {
                                    onRequestPermissions()
                                } else if (isScanning) {
                                    manager.stopScan()
                                } else {
                                    manager.startScan()
                                }
                            },
                            enabled = isBtEnabled,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                        ) {
                            Text(
                                text = if (isScanning) {
                                    "⏹ Stop Search"
                                } else {
                                    "🔍 Search All Devices"
                                },
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Permission or Location Warning Banner if needed
                if (!permissionsGranted) {
                    item {
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(42.dp),
                        ) {
                            Text("⚠️ Grant BT & Location Perms", fontSize = 11.sp)
                        }
                    }
                }

                if (!locationEnabled) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF3D2817))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "⚠️ Watch Location is OFF",
                                color = AppColors.Stop,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Android requires Location ON to discover Classic Bluetooth controllers.",
                                color = AppColors.OnSurface,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { manager.openLocationSettings(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                            ) {
                                Text("Turn On Location", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Active Connected Controller Banner (if already connected!)
                if (connectedInputDevices.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF143625))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "✅ Active Gamepad Ready",
                                color = AppColors.Start,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            connectedInputDevices.forEach { name ->
                                Text(
                                    text = "🎮 $name",
                                    color = AppColors.OnSurface,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = onOpenMapping,
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                            ) {
                                Text("⚡ Map Buttons", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // ALL DISCOVERED DEVICES SECTION (Unfiltered!)
                item {
                    ListHeader {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "All Nearby Devices (${discoveredDevices.size})",
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "No filter • Tap any device to connect",
                                color = AppColors.Muted,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                if (discoveredDevices.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.Surface)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (isScanning) {
                                    "Listening for all Bluetooth devices..."
                                } else {
                                    "No nearby devices found yet."
                                },
                                color = AppColors.OnSurface,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Pairing tip:\nPut your Bluetooth gamepad into pairing mode (refer to your controller's manual, usually holding the Sync or Pair button).",
                                color = AppColors.Muted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(discoveredDevices.size) { index ->
                        val item = discoveredDevices[index]
                        DeviceCard(
                            item = item,
                            isBusy = activeActionAddr == item.address || item.isBonding,
                            onPairOrConnect = { manager.pairAndConnect(item) },
                            onUnpair = if (item.isBonded) ({ manager.unpair(item) }) else null,
                        )
                    }
                }

                // ALREADY PAIRED IN WATCH SECTION
                item {
                    ListHeader {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Paired in Watch (${pairedDevices.size})",
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Tap Connect or Unpair to reset bond",
                                color = AppColors.Muted,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                if (pairedDevices.isEmpty()) {
                    item {
                        Text(
                            text = "No paired Bluetooth devices",
                            color = AppColors.Muted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                } else {
                    items(pairedDevices.size) { index ->
                        val item = pairedDevices[index]
                        DeviceCard(
                            item = item,
                            isBusy = activeActionAddr == item.address || item.isBonding,
                            onPairOrConnect = { manager.pairAndConnect(item) },
                            onUnpair = { manager.unpair(item) },
                        )
                    }
                }

                // Fallback to System Bluetooth Settings
                item {
                    ListHeader {
                        Text("System Fallback", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Button(
                        onClick = { manager.openBluetoothSettings(context) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(42.dp),
                    ) {
                        Text("⚙️ Watch BT Settings", fontSize = 11.sp)
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GlyphButton(onClick = onBack, diameter = 40.dp) { glyphBack(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    item: DiscoveredController,
    isBusy: Boolean,
    onPairOrConnect: () -> Unit,
    onUnpair: (() -> Unit)?,
) {
    val icon = if (item.isGameController) "🎮" else "📡"
    val rssiText = item.rssi?.let { " • ${it}dBm" }.orEmpty()
    val stateText = when {
        item.isHidConnected -> "✅ Connected"
        isBusy -> "⏳ Working..."
        item.isBonded -> "Paired"
        else -> "Available"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.isHidConnected) Color(0xFF143625) else AppColors.Surface)
            .clickable(onClick = onPairOrConnect)
            .padding(8.dp),
    ) {
        Text(
            text = "$icon ${item.name}",
            color = when {
                item.isHidConnected -> AppColors.Start
                item.isGameController -> AppColors.Primary
                else -> AppColors.OnSurface
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.address} • ${item.transportLabel}$rssiText",
            color = AppColors.Muted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.categoryLabel} • $stateText",
            color = if (item.isHidConnected) AppColors.Start else AppColors.Primary,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onPairOrConnect,
                modifier = Modifier.weight(1f).height(34.dp),
            ) {
                val actionLabel = when {
                    isBusy -> "⏳ Working..."
                    item.isHidConnected -> "Reconnect"
                    item.isBonded -> "Connect"
                    else -> "Pair & Connect"
                }
                Text(
                    text = actionLabel,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (onUnpair != null) {
                Button(
                    onClick = onUnpair,
                    modifier = Modifier.height(34.dp),
                ) {
                    Text("Unpair", fontSize = 10.sp)
                }
            }
        }
    }
}
