package com.example.a3dsinputredirectionwearos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge
import com.example.a3dsinputredirectionwearos.RedirectionHolder
import com.example.a3dsinputredirectionwearos.presentation.theme.WearAppTheme
import com.example.a3dsinputredirectionwearos.service.RedirectionService

private sealed interface Screen {
    data object Main : Screen
    data object Controllers : Screen
    data object PairController : Screen
    data object Mapping : Screen
    data object Touchpad : Screen
    data object Special : Screen
    data object Settings : Screen
    data object IpEditor : Screen
    data object Logs : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var bridge: InputRedirectionBridge
    private var shouldStartScanAfterPermission = false

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bridge.bluetoothControllerManager.refreshDevices()
        if (shouldStartScanAfterPermission) {
            shouldStartScanAfterPermission = false
            bridge.bluetoothControllerManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        bridge = RedirectionHolder.get(this)
        requestRuntimePermissions(startScanWhenGranted = false)

        setContent {
            WearAppTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Main) }
                BackHandler(enabled = screen != Screen.Main) {
                    screen = when (screen) {
                        Screen.PairController -> Screen.Controllers
                        Screen.IpEditor -> Screen.Settings
                        Screen.Logs -> Screen.Settings
                        else -> Screen.Main
                    }
                }
                when (screen) {
                    Screen.IpEditor -> IpEditorScreen(
                        bridge = bridge,
                        onDone = { screen = Screen.Settings },
                    )
                    Screen.PairController -> PairControllerScreen(
                        bridge = bridge,
                        onRequestPermissions = { requestRuntimePermissions(startScanWhenGranted = true) },
                        onOpenMapping = { screen = Screen.Mapping },
                        onBack = { screen = Screen.Controllers },
                    )
                    Screen.Controllers -> ControllersScreen(
                        bridge = bridge,
                        onOpenPairScreen = { screen = Screen.PairController },
                        onOpenMapping = { screen = Screen.Mapping },
                        onExit = { screen = Screen.Main },
                    )
                    Screen.Mapping -> MappingScreen(
                        bridge = bridge,
                        onExit = { screen = Screen.Main },
                    )
                    Screen.Settings -> SettingsScreen(
                        bridge = bridge,
                        onOpenIpEditor = { screen = Screen.IpEditor },
                        onOpenControllers = { screen = Screen.Controllers },
                        onOpenMapping = { screen = Screen.Mapping },
                        onOpenLogs = { screen = Screen.Logs },
                        onBack = { screen = Screen.Main },
                    )
                    Screen.Touchpad -> TouchpadScreen(bridge) {
                        screen = Screen.Main
                    }
                    Screen.Special -> SpecialButtonsScreen(bridge) {
                        screen = Screen.Main
                    }
                    Screen.Logs -> LogsScreen(bridge) {
                        screen = Screen.Settings
                    }
                    Screen.Main -> MainScreen(
                        bridge = bridge,
                        onStartBridge = { startBridge() },
                        onStopBridge = { stopBridge() },
                        onOpenIpEditor = { screen = Screen.IpEditor },
                        onControllers = { screen = Screen.Controllers },
                        onTouchpad = {
                            if (!bridge.isRunning.value) startBridge()
                            screen = Screen.Touchpad
                        },
                        onSpecial = {
                            if (!bridge.isRunning.value) startBridge()
                            screen = Screen.Special
                        },
                        onSettings = { screen = Screen.Settings },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bridge.refreshConnectedControllers()
    }

    override fun onDestroy() {
        bridge.bluetoothControllerManager.unregister()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (bridge.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (bridge.onMotionEvent(ev)) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun startBridge() {
        ContextCompat.startForegroundService(this, RedirectionService.startIntent(this))
    }

    private fun stopBridge() {
        startService(RedirectionService.stopIntent(this))
    }

    private fun requestRuntimePermissions(startScanWhenGranted: Boolean) {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            shouldStartScanAfterPermission = startScanWhenGranted
            permissionRequest.launch(permissions.toTypedArray())
        } else if (startScanWhenGranted) {
            bridge.bluetoothControllerManager.startScan()
        }
    }
}
