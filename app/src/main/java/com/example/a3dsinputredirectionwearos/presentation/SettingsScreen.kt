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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge

@Composable
fun SettingsScreen(
    bridge: InputRedirectionBridge,
    onOpenIpEditor: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenMapping: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val config by bridge.configFlow.collectAsState()

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    ListHeader {
                        Text("Settings", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                // 3DS IP Address (Taps directly into the dedicated Numpad IP Editor!)
                item {
                    ListHeader {
                        Text("3DS Network", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Surface)
                            .clickable(onClick = onOpenIpEditor)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("3DS IP Address", color = AppColors.Muted, fontSize = 10.sp)
                        Text(
                            text = if (config.targetIp.isBlank()) "Tap to set IP" else "${config.targetIp}:4950",
                            color = if (config.targetIp.isBlank()) AppColors.Stop else AppColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Tap to edit with numpad", color = AppColors.Muted, fontSize = 9.sp)
                    }
                }

                // Controller & Mapping Hub
                item {
                    ListHeader {
                        Text("Gamepads & Controls", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Button(
                        onClick = onOpenControllers,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(42.dp),
                    ) {
                        Text("🎮 Bluetooth Gamepads", fontSize = 11.sp)
                    }
                }
                item {
                    Button(
                        onClick = onOpenMapping,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(42.dp),
                    ) {
                        Text("⚡ Button Mapping", fontSize = 11.sp)
                    }
                }

                // Sticks & Inversion (Autosaves immediately upon change!)
                item {
                    ListHeader {
                        Text("Stick Options", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    SwitchButton(
                        checked = config.invertY,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(invertY = chk) } },
                        label = { Text("Invert Y", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.invertCppY,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(invertCppY = chk) } },
                        label = { Text("Invert C-Stick Y", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.swapSticks,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(swapSticks = chk) } },
                        label = { Text("Swap Sticks", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.disableCStick,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(disableCStick = chk) } },
                        label = { Text("Disable C-Stick", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.rightStickAsDPad,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(rightStickAsDPad = chk) } },
                        label = { Text("R-Stick as D-Pad", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.rightStickAsAbxy,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(rightStickAsAbxy = chk) } },
                        label = { Text("R-Stick as ABXY", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    SwitchButton(
                        checked = config.rightStickAsSmash,
                        onCheckedChange = { chk -> bridge.updateConfig { it.copy(rightStickAsSmash = chk) } },
                        label = { Text("R-Stick as Smash", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }

                // Bounds with Steppers
                item {
                    ListHeader {
                        Text("Bounds & Travel", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    BoundStepperRow(
                        title = "Circle Pad Bound",
                        current = config.cpadBound,
                        step = 100,
                        min = 100,
                        max = 4095,
                        onChanged = { nv -> bridge.updateConfig { it.copy(cpadBound = nv) } },
                    )
                }
                item {
                    BoundStepperRow(
                        title = "C-Stick Bound",
                        current = config.cppBound,
                        step = 10,
                        min = 10,
                        max = 255,
                        onChanged = { nv -> bridge.updateConfig { it.copy(cppBound = nv) } },
                    )
                }

                // Logs section (Moved inside Settings as requested!)
                item {
                    ListHeader {
                        Text("Diagnostics", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Button(
                        onClick = onOpenLogs,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(42.dp),
                    ) {
                        Text("📋 View Redirection Logs", fontSize = 11.sp)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
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
private fun BoundStepperRow(
    title: String,
    current: Int,
    step: Int,
    min: Int,
    max: Int,
    onChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "$title: $current", fontSize = 11.sp, color = AppColors.OnSurface)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onChanged((current - step).coerceAtLeast(min)) },
                modifier = Modifier.height(30.dp),
            ) {
                Text("-$step", fontSize = 10.sp)
            }
            Button(
                onClick = { onChanged(if (title.contains("Circle")) 1488 else 127) },
                modifier = Modifier.height(30.dp),
            ) {
                Text("Reset", fontSize = 10.sp)
            }
            Button(
                onClick = { onChanged((current + step).coerceAtMost(max)) },
                modifier = Modifier.height(30.dp),
            ) {
                Text("+$step", fontSize = 10.sp)
            }
        }
    }
}
