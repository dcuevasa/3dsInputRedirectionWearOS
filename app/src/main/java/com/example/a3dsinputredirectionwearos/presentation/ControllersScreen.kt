package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
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

@Composable
fun ControllersScreen(
    bridge: InputRedirectionBridge,
    onOpenPairScreen: () -> Unit,
    onOpenMapping: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val manager = bridge.bluetoothControllerManager
    val listState = rememberTransformingLazyColumnState()

    val statusMsg by manager.statusMessage.collectAsState()
    val connectedInputDevices by manager.connectedInputControllers.collectAsState()
    val pairedDevices by manager.pairedControllers.collectAsState()
    val activeActionAddr by manager.activeActionAddress.collectAsState()

    LaunchedEffect(Unit) {
        manager.refreshDevices()
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
                        Text("Gamepads", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                // Active controller status card at the very top
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (connectedInputDevices.isNotEmpty()) Color(0xFF143625) else AppColors.Surface
                            )
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (connectedInputDevices.isEmpty()) {
                            Text(
                                text = "No Active Gamepad",
                                color = AppColors.Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Tap 'Pair Controller' below to search & connect",
                                color = AppColors.Primary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                text = "✅ Connected & Active",
                                color = AppColors.Start,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            connectedInputDevices.forEach { name ->
                                Text(
                                    text = "🎮 $name",
                                    color = AppColors.OnSurface,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Primary Action #1: Dedicated Pair & Search All Devices Screen
                item {
                    Button(
                        onClick = onOpenPairScreen,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(46.dp),
                    ) {
                        Text(
                            text = "🔍 Pair Controller (All BT)",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Primary Action #2: Map Controller Inputs
                item {
                    Button(
                        onClick = onOpenMapping,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(44.dp),
                    ) {
                        Text(
                            text = "⚡ Map Controller Inputs",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Paired Bluetooth Devices quick list
                if (pairedDevices.isNotEmpty()) {
                    item {
                        ListHeader {
                            Text("Paired on Watch", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Text(
                            text = statusMsg,
                            color = AppColors.Primary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                    items(pairedDevices.size) { index ->
                        val item = pairedDevices[index]
                        val isBusy = activeActionAddr == item.address || item.isBonding
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (item.isHidConnected) Color(0xFF143625) else AppColors.Surface)
                                .padding(8.dp),
                        ) {
                            val icon = if (item.isGameController) "🎮" else "📡"
                            Text(
                                text = "$icon ${item.name}",
                                color = if (item.isHidConnected) AppColors.Start else AppColors.OnSurface,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (item.isHidConnected) "✅ Connected" else "Paired (${item.address})",
                                color = AppColors.Muted,
                                fontSize = 9.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Button(
                                    onClick = { manager.pairAndConnect(item) },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                ) {
                                    Text(
                                        text = if (isBusy) "⏳..." else if (item.isHidConnected) "Reconnect" else "Connect",
                                        fontSize = 11.sp,
                                    )
                                }
                                Button(
                                    onClick = { manager.unpair(item) },
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Text("Unpair", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Direct launcher to system Bluetooth Settings
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
                        GlyphButton(onClick = onExit, diameter = 40.dp) { glyphBack(it) }
                    }
                }
            }
        }
    }
}
