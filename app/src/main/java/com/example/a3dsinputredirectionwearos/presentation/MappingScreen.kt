package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.a3dsinputredirectionwearos.GamepadButtonOption
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge
import com.example.a3dsinputredirectionwearos.MappingPreset

@Composable
fun MappingScreen(
    bridge: InputRedirectionBridge,
    onExit: () -> Unit,
) {
    val config by bridge.configFlow.collectAsState()
    val activeTarget by bridge.activeMappingTarget.collectAsState()
    val wizardStep by bridge.wizardStepIndex.collectAsState()
    val lastInput by bridge.lastInputSummary.collectAsState()
    val listState = rememberTransformingLazyColumnState()

    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    // If waiting for button press, show modal listening screen
    if (activeTarget != null) {
        val target = activeTarget!!
        val isWizard = wizardStep >= 0
        val totalSteps = InputRedirectionBridge.WIZARD_SEQUENCE.size

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWizard) {
                    Text(
                        text = "Wizard: Step ${wizardStep + 1}/$totalSteps",
                        color = AppColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = "Press Gamepad Button For:",
                    color = AppColors.Muted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = target.displayName,
                    color = AppColors.Start,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Press any button, trigger, or D-Pad direction on your controller",
                    color = AppColors.OnSurface,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isWizard) {
                        Button(
                            onClick = { bridge.skipWizardStep() },
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text("Skip", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (isWizard) bridge.cancelMappingWizard() else bridge.cancelListeningForMapping()
                        },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text("Cancel", fontSize = 11.sp)
                    }
                }
            }
        }
        return
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
                        Text("Controller Mapping", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                feedbackMessage?.let { msg ->
                    item {
                        Text(
                            text = msg,
                            color = AppColors.Primary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }

                // --- 1. AUTOMAP SECTION ---
                item {
                    ListHeader {
                        Text("Auto-Map Presets", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Button(
                        onClick = {
                            val msg = bridge.autoDetectAndMapConnectedController()
                            feedbackMessage = msg
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(44.dp),
                    ) {
                        Text("✨ Auto-Detect Pad & Map")
                    }
                }
                item {
                    PresetButton(
                        title = MappingPreset.NINTENDO_DEFAULT.title,
                        subtitle = MappingPreset.NINTENDO_DEFAULT.description,
                    ) {
                        bridge.applyPreset(MappingPreset.NINTENDO_DEFAULT)
                        feedbackMessage = "Applied Nintendo Default"
                    }
                }
                item {
                    PresetButton(
                        title = MappingPreset.XBOX_POSITIONAL.title,
                        subtitle = MappingPreset.XBOX_POSITIONAL.description,
                    ) {
                        bridge.applyPreset(MappingPreset.XBOX_POSITIONAL)
                        feedbackMessage = "Applied Xbox Positional (A<->B, X<->Y)"
                    }
                }
                item {
                    PresetButton(
                        title = MappingPreset.PLAYSTATION.title,
                        subtitle = MappingPreset.PLAYSTATION.description,
                    ) {
                        bridge.applyPreset(MappingPreset.PLAYSTATION)
                        feedbackMessage = "Applied PlayStation Layout"
                    }
                }
                item {
                    PresetButton(
                        title = MappingPreset.XBOX_LABELS.title,
                        subtitle = MappingPreset.XBOX_LABELS.description,
                    ) {
                        bridge.applyPreset(MappingPreset.XBOX_LABELS)
                        feedbackMessage = "Applied Xbox Labels (A=A, B=B)"
                    }
                }

                // --- 2. GUIDED WIZARD ---
                item {
                    ListHeader {
                        Text("Guided Wizard", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Button(
                        onClick = { bridge.startMappingWizard() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(44.dp),
                    ) {
                        Text("Start Step-by-Step Remap")
                    }
                }

                // --- 3. INDIVIDUAL BUTTON MAPPING ---
                item {
                    ListHeader {
                        Text("Press Button to Map", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                val buttonList = listOf(
                    Triple("mapA", "3DS A", config.mapA),
                    Triple("mapB", "3DS B", config.mapB),
                    Triple("mapX", "3DS X", config.mapX),
                    Triple("mapY", "3DS Y", config.mapY),
                    Triple("mapL", "3DS L", config.mapL),
                    Triple("mapR", "3DS R", config.mapR),
                    Triple("mapZl", "3DS ZL", config.mapZl),
                    Triple("mapZr", "3DS ZR", config.mapZr),
                    Triple("mapStart", "Start", config.mapStart),
                    Triple("mapSelect", "Select", config.mapSelect),
                    Triple("mapDpadUp", "DPad Up", config.mapDpadUp),
                    Triple("mapDpadDown", "DPad Down", config.mapDpadDown),
                    Triple("mapDpadLeft", "DPad Left", config.mapDpadLeft),
                    Triple("mapDpadRight", "DPad Right", config.mapDpadRight),
                    Triple("mapHome", "Home", config.mapHome),
                    Triple("mapPower", "Power", config.mapPower),
                    Triple("mapPowerLong", "Power Long", config.mapPowerLong),
                )

                items(buttonList.size) { index ->
                    val (key, label, currentCode) = buttonList[index]
                    val mappedName = GamepadButtonOption.fromKeyCode(currentCode).label

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.Surface)
                            .clickable {
                                bridge.startListeningForMapping(key, label)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = AppColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tap & press pad button", color = AppColors.Muted, fontSize = 9.sp)
                        }
                        Text(
                            text = mappedName,
                            color = AppColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
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

@Composable
private fun PresetButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(title, color = AppColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = AppColors.Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
