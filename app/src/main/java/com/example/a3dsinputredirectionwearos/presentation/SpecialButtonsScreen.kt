package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpecialButtonsScreen(
    bridge: InputRedirectionBridge,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Tap to send key to 3DS") }

    fun showFeedback(msg: String) {
        scope.launch {
            statusText = msg
            delay(1200L)
            statusText = "Tap to send key to 3DS"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "3DS System Keys",
                    color = AppColors.OnSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = statusText,
                    color = AppColors.Primary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = {
                    bridge.triggerHomePulse()
                    showFeedback("Sent HOME Key")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) {
                Text(
                    text = "HOME",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = {
                    bridge.triggerPowerPulse()
                    showFeedback("Sent POWER Tap")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) {
                Text(
                    text = "POWER (Tap)",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = {
                    bridge.triggerPowerLongPulse()
                    showFeedback("Sent POWER (Hold 1.5s)")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) {
                Text(
                    text = "POWER (Hold 1.5s)",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }

            GlyphButton(onClick = onExit, diameter = 32.dp) { glyphBack(it) }
        }
    }
}
