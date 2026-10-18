package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge

@Composable
fun IpEditorScreen(
    bridge: InputRedirectionBridge,
    onDone: () -> Unit,
) {
    val initialIp = remember { bridge.configFlow.value.targetIp }
    var ipText by remember { mutableStateOf(initialIp) }
    val listState = rememberTransformingLazyColumnState()

    fun saveAndExit() {
        val clean = ipText.trim()
        bridge.updateConfig { it.copy(targetIp = clean) }
        onDone()
    }

    fun appendChar(c: Char) {
        if (ipText.length < 15) {
            ipText += c
            // Autosave as typed
            bridge.updateConfig { it.copy(targetIp = ipText.trim()) }
        }
    }

    fun backspace() {
        if (ipText.isNotEmpty()) {
            ipText = ipText.dropLast(1)
            // Autosave as typed
            bridge.updateConfig { it.copy(targetIp = ipText.trim()) }
        }
    }

    fun setQuick(prefix: String) {
        ipText = prefix
        bridge.updateConfig { it.copy(targetIp = ipText.trim()) }
    }

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    ListHeader {
                        Text("3DS IP Address", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                // Live Preview Card (Always visible at top of keypad)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (ipText.isEmpty()) "Tap numbers below" else ipText,
                            color = if (ipText.isEmpty()) AppColors.Muted else AppColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Quick Prefix Shortcuts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { setQuick("192.168.") },
                            modifier = Modifier.weight(1f).height(30.dp),
                        ) {
                            Text("192.168.", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { setQuick("10.0.0.") },
                            modifier = Modifier.weight(1f).height(30.dp),
                        ) {
                            Text("10.0.0.", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                ipText = ""
                                bridge.updateConfig { it.copy(targetIp = "") }
                            },
                            modifier = Modifier.weight(0.8f).height(30.dp),
                        ) {
                            Text("Clear", fontSize = 10.sp)
                        }
                    }
                }

                // On-screen Numpad Row 1: 1, 2, 3
                item {
                    NumpadRow(
                        left = "1", onLeft = { appendChar('1') },
                        mid = "2", onMid = { appendChar('2') },
                        right = "3", onRight = { appendChar('3') },
                    )
                }

                // On-screen Numpad Row 2: 4, 5, 6
                item {
                    NumpadRow(
                        left = "4", onLeft = { appendChar('4') },
                        mid = "5", onMid = { appendChar('5') },
                        right = "6", onRight = { appendChar('6') },
                    )
                }

                // On-screen Numpad Row 3: 7, 8, 9
                item {
                    NumpadRow(
                        left = "7", onLeft = { appendChar('7') },
                        mid = "8", onMid = { appendChar('8') },
                        right = "9", onRight = { appendChar('9') },
                    )
                }

                // On-screen Numpad Row 4: ., 0, ⌫
                item {
                    NumpadRow(
                        left = ".", onLeft = { appendChar('.') },
                        mid = "0", onMid = { appendChar('0') },
                        right = "⌫", onRight = { backspace() },
                    )
                }

                // Done Button
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { saveAndExit() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(38.dp),
                    ) {
                        Text("Save & Return", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadRow(
    left: String, onLeft: () -> Unit,
    mid: String, onMid: () -> Unit,
    right: String, onRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeypadButton(text = left, onClick = onLeft, modifier = Modifier.weight(1f))
        KeypadButton(text = mid, onClick = onMid, modifier = Modifier.weight(1f))
        KeypadButton(text = right, onClick = onRight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        colors = ButtonDefaults.filledTonalButtonColors(),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
