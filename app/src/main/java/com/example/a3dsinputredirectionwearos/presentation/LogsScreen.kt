package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge

@Composable
fun LogsScreen(bridge: InputRedirectionBridge, onExit: () -> Unit) {
    val logs by bridge.logs.collectAsState()
    val packets by bridge.packetsSent.collectAsState()
    val controllers by bridge.connectedControllers.collectAsState()
    val listState = rememberTransformingLazyColumnState()

    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    ListHeader {
                        Text("Status & Logs", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text(
                        "UDP Packets Sent: $packets",
                        color = AppColors.Primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        if (controllers.isEmpty()) "BT Pads: None" else "BT Pads: ${controllers.joinToString()}",
                        color = AppColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                if (logs.isEmpty()) {
                    item {
                        Text(
                            "No activity yet",
                            color = AppColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                items(logs.size) { index ->
                    Text(
                        logs[index],
                        color = AppColors.OnSurface,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        GlyphButton(onClick = onExit, diameter = 40.dp) { glyphBack(it) }
                    }
                }
            }
        }
    }
}
