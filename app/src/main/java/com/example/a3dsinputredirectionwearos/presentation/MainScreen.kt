package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge

@Composable
fun MainScreen(
    bridge: InputRedirectionBridge,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onOpenIpEditor: () -> Unit,
    onControllers: () -> Unit,
    onTouchpad: () -> Unit,
    onSpecial: () -> Unit,
    onSettings: () -> Unit,
) {
    val running by bridge.isRunning.collectAsState()
    val config by bridge.configFlow.collectAsState()
    val controllers by bridge.connectedControllers.collectAsState()
    val inputSummary by bridge.lastInputSummary.collectAsState()

    val ipLabel = if (config.targetIp.isBlank()) "Set 3DS IP" else "${config.targetIp}:4950"
    val controllerLabel = if (controllers.isEmpty()) "🎮 No Gamepad" else "🎮 ${controllers.first()}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Top Nav Row: Settings (Left) & Controllers (Right)
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(onClick = onSettings, diameter = AppDimens.SmallIcon) { glyphTune(it) }
                GlyphButton(onClick = onControllers, diameter = AppDimens.SmallIcon) { glyphGamepad(it) }
            }

            // Compact IP & Gamepad Status (Clicking IP opens IP editor directly)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = ipLabel,
                    color = if (config.targetIp.isBlank()) AppColors.Stop else AppColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenIpEditor)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    text = controllerLabel,
                    color = if (controllers.isEmpty()) AppColors.Muted else AppColors.Start,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onControllers)
                        .padding(horizontal = 4.dp),
                )
            }

            // Sleek, compact Play/Stop button (Proportionate, not oversized!)
            HeroButton(
                running = running,
                diameter = AppDimens.Hero,
                onClick = { if (running) onStopBridge() else onStartBridge() },
            )

            // Bottom Nav Row: Touchpad & 3DS Keys
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(onClick = onTouchpad, diameter = AppDimens.NavIcon) { glyphTouchpad(it) }
                GlyphButton(onClick = onSpecial, diameter = AppDimens.NavIcon) { glyphSpecial(it) }
            }

            // Minimal input telemetry status
            Text(
                text = inputSummary,
                color = AppColors.Muted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
