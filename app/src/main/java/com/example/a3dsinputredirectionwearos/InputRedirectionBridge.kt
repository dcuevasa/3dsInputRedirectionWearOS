package com.example.a3dsinputredirectionwearos

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.a3dsinputredirectionwearos.bluetooth.BluetoothControllerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

data class MappingTarget(
    val configKey: String,
    val displayName: String,
)

enum class MappingPreset(val title: String, val description: String) {
    NINTENDO_DEFAULT("Nintendo Standard", "A/B/X/Y match standard Nintendo layout"),
    XBOX_POSITIONAL("Xbox Positional", "Swaps A<->B & X<->Y so physical positions match 3DS"),
    XBOX_LABELS("Xbox Labels", "Matches letters directly: Xbox A->3DS A, Xbox B->3DS B"),
    PLAYSTATION("PlayStation", "Circle->3DS A, Cross->3DS B, Triangle->3DS X, Square->3DS Y"),
}

class InputRedirectionBridge(context: Context) {

    companion object {
        const val UDP_PORT = 4950
        const val TOUCH_MAX = 0xFFF

        const val SPECIAL_HOME = 1
        const val SPECIAL_POWER = 2
        const val SPECIAL_POWER_LONG = 4

        private const val MAX_LOGS = 100

        val WIZARD_SEQUENCE = listOf(
            MappingTarget("mapA", "3DS A"),
            MappingTarget("mapB", "3DS B"),
            MappingTarget("mapX", "3DS X"),
            MappingTarget("mapY", "3DS Y"),
            MappingTarget("mapL", "3DS L"),
            MappingTarget("mapR", "3DS R"),
            MappingTarget("mapZl", "3DS ZL"),
            MappingTarget("mapZr", "3DS ZR"),
            MappingTarget("mapStart", "Start"),
            MappingTarget("mapSelect", "Select"),
            MappingTarget("mapDpadUp", "DPad Up"),
            MappingTarget("mapDpadDown", "DPad Down"),
            MappingTarget("mapDpadLeft", "DPad Left"),
            MappingTarget("mapDpadRight", "DPad Right"),
        )

        internal data class InputSnapshot(
            val pressedKeys: Set<Int> = emptySet(),
            val leftX: Float = 0f,
            val leftY: Float = 0f,
            val rightX: Float = 0f,
            val rightY: Float = 0f,
            val touchActive: Boolean = false,
            val touchX: Int = 0,
            val touchY: Int = 0,
            val uiSpecialMask: Int = 0,
        )

        internal fun buildPacketBytes(
            cfg: InputRedirectionConfig,
            snapshot: InputSnapshot,
        ): ByteArray {
            val lRawX = snapshot.leftX
            val lRawY = snapshot.leftY
            val rRawX = snapshot.rightX
            val rRawY = snapshot.rightY

            val leftX: Float
            var leftY = -lRawY
            var rightX = rRawX
            var rightY = -rRawY

            var tempLeftX = lRawX
            if (cfg.swapSticks) {
                tempLeftX = rRawX
                leftY = -rRawY
                rightX = lRawX
                rightY = -lRawY
            }
            leftX = tempLeftX

            if (cfg.invertY) leftY = -leftY
            if (cfg.invertCppY) rightY = -rightY

            val pressed = snapshot.pressedKeys.toMutableSet()

            val rightThreshold = 0.5f
            if (cfg.rightStickAsDPad) {
                if (rightX < -rightThreshold) pressed += cfg.mapDpadLeft
                if (rightX > rightThreshold) pressed += cfg.mapDpadRight
                if (rightY < -rightThreshold) pressed += cfg.mapDpadDown
                if (rightY > rightThreshold) pressed += cfg.mapDpadUp
            }

            if (cfg.rightStickAsAbxy) {
                if (rightX < -rightThreshold) pressed += cfg.mapY
                if (rightX > rightThreshold) pressed += cfg.mapA
                if (rightY < -rightThreshold) pressed += cfg.mapB
                if (rightY > rightThreshold) pressed += cfg.mapX
            }

            var smashLeftX = leftX
            var smashLeftY = leftY
            var smashA = false
            if (cfg.rightStickAsSmash) {
                if (abs(rightX) > rightThreshold) {
                    smashLeftX = rightX.sign
                    smashA = true
                }
                if (abs(rightY) > rightThreshold) {
                    smashLeftY = rightY.sign
                    smashA = true
                }
            }
            if (smashA) pressed += cfg.mapA

            var hidPad = 0x00000FFF
            hidPad = applyHidBit(hidPad, pressed, cfg.mapA, 0)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapB, 1)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapSelect, 2)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapStart, 3)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadRight, 4)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadLeft, 5)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadUp, 6)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapDpadDown, 7)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapR, 8)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapL, 9)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapX, 10)
            hidPad = applyHidBit(hidPad, pressed, cfg.mapY, 11)

            val irButtonsState =
                ((if (isPressed(pressed, cfg.mapZr)) 1 else 0) shl 1) or
                    ((if (isPressed(pressed, cfg.mapZl)) 1 else 0) shl 2)

            val touchState = if (snapshot.touchActive) {
                (1 shl 24) or ((snapshot.touchY and 0xFFF) shl 12) or (snapshot.touchX and 0xFFF)
            } else {
                0x02000000
            }

            val circlePadState = if (abs(smashLeftX) > 0.001f || abs(smashLeftY) > 0.001f) {
                val x = ((smashLeftX * cfg.cpadBound) + 0x800).roundToInt().coerceIn(0, 0xFFF)
                val y = ((smashLeftY * cfg.cpadBound) + 0x800).roundToInt().coerceIn(0, 0xFFF)
                ((y and 0xFFF) shl 12) or (x and 0xFFF)
            } else {
                0x007FF7FF
            }

            val cppState = if (
                (!cfg.disableCStick && (abs(rightX) > 0.001f || abs(rightY) > 0.001f)) ||
                irButtonsState != 0
            ) {
                val norm = (1f / sqrt(2f))
                val rotX = norm * (rightX + rightY)
                val rotY = norm * (rightY - rightX)
                val x = ((rotX * cfg.cppBound) + 0x80).roundToInt().coerceIn(0, 0xFF)
                val y = ((rotY * cfg.cppBound) + 0x80).roundToInt().coerceIn(0, 0xFF)
                (y shl 24) or (x shl 16) or ((irButtonsState and 0xFF) shl 8) or 0x81
            } else {
                0x80800081.toInt()
            }

            var special = snapshot.uiSpecialMask
            if (isPressed(pressed, cfg.mapHome)) special = special or SPECIAL_HOME
            if (isPressed(pressed, cfg.mapPower)) special = special or SPECIAL_POWER
            if (isPressed(pressed, cfg.mapPowerLong)) special = special or SPECIAL_POWER_LONG

            return ByteBuffer
                .allocate(20)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(hidPad)
                .putInt(touchState)
                .putInt(circlePadState)
                .putInt(cppState)
                .putInt(special)
                .array()
        }

        private fun applyHidBit(hidPad: Int, pressed: Set<Int>, mappedKey: Int, bit: Int): Int {
            if (!isPressed(pressed, mappedKey)) return hidPad
            return hidPad and (1 shl bit).inv()
        }

        private fun isPressed(pressed: Set<Int>, mappedKey: Int): Boolean {
            if (mappedKey == KeyEvent.KEYCODE_UNKNOWN) return false
            return pressed.contains(mappedKey)
        }
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("input_redirection_wear", Context.MODE_PRIVATE)
    private val inputManager = appContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    val bluetoothControllerManager = BluetoothControllerManager(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    private val pressedKeys = mutableSetOf<Int>()
    private val hatPressedKeys = mutableSetOf<Int>()
    private val triggerPressedKeys = mutableSetOf<Int>()
    private val uiPressedKeys = mutableSetOf<Int>()
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f
    private var touchActive = false
    private var touchX = 0
    private var touchY = 0
    private var uiSpecialMask = 0

    private var sendJob: Job? = null
    private var socket: DatagramSocket? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _configFlow = MutableStateFlow(InputRedirectionConfig.load(prefs))
    val configFlow: StateFlow<InputRedirectionConfig> = _configFlow.asStateFlow()

    private val _connectedControllers = MutableStateFlow<List<String>>(emptyList())
    val connectedControllers: StateFlow<List<String>> = _connectedControllers.asStateFlow()

    private val _packetsSent = MutableStateFlow(0L)
    val packetsSent: StateFlow<Long> = _packetsSent.asStateFlow()

    private val _lastInputSummary = MutableStateFlow("Idle")
    val lastInputSummary: StateFlow<String> = _lastInputSummary.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Interactive button mapping state
    private val _activeMappingTarget = MutableStateFlow<MappingTarget?>(null)
    val activeMappingTarget: StateFlow<MappingTarget?> = _activeMappingTarget.asStateFlow()

    private val _wizardStepIndex = MutableStateFlow<Int>(-1)
    val wizardStepIndex: StateFlow<Int> = _wizardStepIndex.asStateFlow()

    init {
        refreshConnectedControllers()
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                refreshConnectedControllers()
                bluetoothControllerManager.refreshDevices()
                InputDevice.getDevice(deviceId)?.let { dev ->
                    if (isControllerDevice(dev)) log("Controller connected: ${dev.name}")
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                refreshConnectedControllers()
                bluetoothControllerManager.refreshDevices()
                log("Controller removed (id=$deviceId)")
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                refreshConnectedControllers()
                bluetoothControllerManager.refreshDevices()
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun updateConfig(transform: (InputRedirectionConfig) -> InputRedirectionConfig) {
        val updated = transform(_configFlow.value.copy())
        updated.save(prefs)
        _configFlow.value = updated
        log("Saved config (target=${updated.targetIp.ifBlank { "none" }})")
    }

    fun refreshConnectedControllers() {
        val names = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            InputDevice.getDevice(id)?.takeIf { isControllerDevice(it) }?.name
        }.distinct()
        _connectedControllers.value = names
        bluetoothControllerManager.refreshDevices()
    }

    private fun isControllerDevice(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    // --- Interactive Mapping Functions ---

    fun startListeningForMapping(configKey: String, displayName: String) {
        _wizardStepIndex.value = -1
        _activeMappingTarget.value = MappingTarget(configKey, displayName)
        log("Press button for $displayName...")
    }

    fun cancelListeningForMapping() {
        _activeMappingTarget.value = null
        _wizardStepIndex.value = -1
    }

    fun startMappingWizard() {
        _wizardStepIndex.value = 0
        _activeMappingTarget.value = WIZARD_SEQUENCE[0]
        log("Started Wizard: Press button for ${WIZARD_SEQUENCE[0].displayName}")
    }

    fun skipWizardStep() {
        advanceWizard()
    }

    fun cancelMappingWizard() {
        _wizardStepIndex.value = -1
        _activeMappingTarget.value = null
        log("Wizard cancelled")
    }

    private fun advanceWizard() {
        val nextIdx = _wizardStepIndex.value + 1
        if (nextIdx < WIZARD_SEQUENCE.size) {
            _wizardStepIndex.value = nextIdx
            _activeMappingTarget.value = WIZARD_SEQUENCE[nextIdx]
            log("Wizard: Press button for ${WIZARD_SEQUENCE[nextIdx].displayName}")
        } else {
            _wizardStepIndex.value = -1
            _activeMappingTarget.value = null
            log("Wizard finished! All buttons configured.")
        }
    }

    fun assignMapping(configKey: String, keyCode: Int) {
        updateConfig { cfg ->
            when (configKey) {
                "mapA" -> cfg.mapA = keyCode
                "mapB" -> cfg.mapB = keyCode
                "mapX" -> cfg.mapX = keyCode
                "mapY" -> cfg.mapY = keyCode
                "mapL" -> cfg.mapL = keyCode
                "mapR" -> cfg.mapR = keyCode
                "mapZl" -> cfg.mapZl = keyCode
                "mapZr" -> cfg.mapZr = keyCode
                "mapStart" -> cfg.mapStart = keyCode
                "mapSelect" -> cfg.mapSelect = keyCode
                "mapDpadUp" -> cfg.mapDpadUp = keyCode
                "mapDpadDown" -> cfg.mapDpadDown = keyCode
                "mapDpadLeft" -> cfg.mapDpadLeft = keyCode
                "mapDpadRight" -> cfg.mapDpadRight = keyCode
                "mapHome" -> cfg.mapHome = keyCode
                "mapPower" -> cfg.mapPower = keyCode
                "mapPowerLong" -> cfg.mapPowerLong = keyCode
            }
            cfg
        }
    }

    fun applyPreset(preset: MappingPreset) {
        updateConfig { cfg ->
            when (preset) {
                MappingPreset.NINTENDO_DEFAULT, MappingPreset.XBOX_LABELS -> {
                    cfg.mapA = KeyEvent.KEYCODE_BUTTON_A
                    cfg.mapB = KeyEvent.KEYCODE_BUTTON_B
                    cfg.mapX = KeyEvent.KEYCODE_BUTTON_X
                    cfg.mapY = KeyEvent.KEYCODE_BUTTON_Y
                    cfg.mapL = KeyEvent.KEYCODE_BUTTON_L1
                    cfg.mapR = KeyEvent.KEYCODE_BUTTON_R1
                    cfg.mapZl = KeyEvent.KEYCODE_BUTTON_L2
                    cfg.mapZr = KeyEvent.KEYCODE_BUTTON_R2
                    cfg.mapStart = KeyEvent.KEYCODE_BUTTON_START
                    cfg.mapSelect = KeyEvent.KEYCODE_BUTTON_SELECT
                    cfg.mapDpadUp = KeyEvent.KEYCODE_DPAD_UP
                    cfg.mapDpadDown = KeyEvent.KEYCODE_DPAD_DOWN
                    cfg.mapDpadLeft = KeyEvent.KEYCODE_DPAD_LEFT
                    cfg.mapDpadRight = KeyEvent.KEYCODE_DPAD_RIGHT
                }
                MappingPreset.XBOX_POSITIONAL -> {
                    cfg.mapA = KeyEvent.KEYCODE_BUTTON_B // Xbox B is Right
                    cfg.mapB = KeyEvent.KEYCODE_BUTTON_A // Xbox A is Bottom
                    cfg.mapX = KeyEvent.KEYCODE_BUTTON_Y // Xbox Y is Top
                    cfg.mapY = KeyEvent.KEYCODE_BUTTON_X // Xbox X is Left
                    cfg.mapL = KeyEvent.KEYCODE_BUTTON_L1
                    cfg.mapR = KeyEvent.KEYCODE_BUTTON_R1
                    cfg.mapZl = KeyEvent.KEYCODE_BUTTON_L2
                    cfg.mapZr = KeyEvent.KEYCODE_BUTTON_R2
                    cfg.mapStart = KeyEvent.KEYCODE_BUTTON_START
                    cfg.mapSelect = KeyEvent.KEYCODE_BUTTON_SELECT
                    cfg.mapDpadUp = KeyEvent.KEYCODE_DPAD_UP
                    cfg.mapDpadDown = KeyEvent.KEYCODE_DPAD_DOWN
                    cfg.mapDpadLeft = KeyEvent.KEYCODE_DPAD_LEFT
                    cfg.mapDpadRight = KeyEvent.KEYCODE_DPAD_RIGHT
                }
                MappingPreset.PLAYSTATION -> {
                    cfg.mapA = KeyEvent.KEYCODE_BUTTON_B // Circle is Right
                    cfg.mapB = KeyEvent.KEYCODE_BUTTON_A // Cross is Bottom
                    cfg.mapX = KeyEvent.KEYCODE_BUTTON_Y // Triangle is Top
                    cfg.mapY = KeyEvent.KEYCODE_BUTTON_X // Square is Left
                    cfg.mapL = KeyEvent.KEYCODE_BUTTON_L1
                    cfg.mapR = KeyEvent.KEYCODE_BUTTON_R1
                    cfg.mapZl = KeyEvent.KEYCODE_BUTTON_L2
                    cfg.mapZr = KeyEvent.KEYCODE_BUTTON_R2
                    cfg.mapStart = KeyEvent.KEYCODE_BUTTON_START
                    cfg.mapSelect = KeyEvent.KEYCODE_BUTTON_SELECT
                    cfg.mapDpadUp = KeyEvent.KEYCODE_DPAD_UP
                    cfg.mapDpadDown = KeyEvent.KEYCODE_DPAD_DOWN
                    cfg.mapDpadLeft = KeyEvent.KEYCODE_DPAD_LEFT
                    cfg.mapDpadRight = KeyEvent.KEYCODE_DPAD_RIGHT
                }
            }
            cfg
        }
        log("Applied preset: ${preset.title}")
    }

    fun autoDetectAndMapConnectedController(): String {
        val controllers = _connectedControllers.value
        if (controllers.isEmpty()) {
            applyPreset(MappingPreset.NINTENDO_DEFAULT)
            return "No active pad detected. Applied Nintendo default."
        }
        val name = controllers.first().lowercase(Locale.US)
        return if (name.contains("xbox")) {
            applyPreset(MappingPreset.XBOX_POSITIONAL)
            "Detected Xbox Pad: Applied Positional Preset (Xbox B->3DS A, Xbox A->3DS B)."
        } else if (name.contains("dual") || name.contains("playstation") || name.contains("ps")) {
            applyPreset(MappingPreset.PLAYSTATION)
            "Detected PlayStation Pad: Applied Positional Preset (Circle->3DS A, Cross->3DS B)."
        } else {
            applyPreset(MappingPreset.NINTENDO_DEFAULT)
            "Detected ${controllers.first()}: Applied Nintendo Standard Preset."
        }
    }

    // --- Redirection Network Loop ---

    fun start() {
        if (sendJob?.isActive == true) return
        val ip = _configFlow.value.targetIp.trim()
        log("Starting UDP loop -> ${ip.ifBlank { "(no IP set)" }}:$UDP_PORT")
        _isRunning.value = true
        sendJob = scope.launch {
            val udpSocket = DatagramSocket()
            socket = udpSocket
            var cachedIpString = ""
            var cachedAddress: InetAddress? = null
            var lastErrorLogMs = 0L

            try {
                while (isActive) {
                    val cfg = _configFlow.value
                    val target = cfg.targetIp.trim()
                    if (target.isNotEmpty()) {
                        if (target != cachedIpString || cachedAddress == null) {
                            cachedAddress = runCatching { InetAddress.getByName(target) }.getOrNull()
                            cachedIpString = target
                            if (cachedAddress == null) {
                                val now = System.currentTimeMillis()
                                if (now - lastErrorLogMs > 5000L) {
                                    log("Invalid target IP/host: $target")
                                    lastErrorLogMs = now
                                }
                            }
                        }
                        val addr = cachedAddress
                        if (addr != null) {
                            val snapshot = takeSnapshot()
                            val packetData = buildPacketBytes(cfg, snapshot)
                            val datagram = DatagramPacket(packetData, packetData.size, addr, UDP_PORT)
                            runCatching {
                                udpSocket.send(datagram)
                                _packetsSent.value += 1L
                            }.onFailure { err ->
                                val now = System.currentTimeMillis()
                                if (now - lastErrorLogMs > 5000L) {
                                    log("UDP send error: ${err.message}")
                                    lastErrorLogMs = now
                                }
                            }
                        }
                    }
                    delay(20L)
                }
            } finally {
                runCatching { udpSocket.close() }
            }
        }
    }

    fun stop() {
        if (!_isRunning.value && sendJob == null) return
        log("Stopping UDP loop")
        sendJob?.cancel()
        sendJob = null
        socket?.close()
        socket = null
        _isRunning.value = false
        synchronized(stateLock) {
            pressedKeys.clear()
            hatPressedKeys.clear()
            triggerPressedKeys.clear()
            uiPressedKeys.clear()
            leftX = 0f
            leftY = 0f
            rightX = 0f
            rightY = 0f
            touchActive = false
            uiSpecialMask = 0
        }
        _lastInputSummary.value = "Idle"
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepadSource(event.source) || !isGamepadKey(event.keyCode)) return false

        // Check if we are listening for a button press to map
        val target = _activeMappingTarget.value
        if (target != null && event.action == KeyEvent.ACTION_DOWN) {
            assignMapping(target.configKey, event.keyCode)
            log("Mapped ${target.displayName} to ${GamepadButtonOption.fromKeyCode(event.keyCode).label}")
            if (_wizardStepIndex.value >= 0) {
                advanceWizard()
            } else {
                _activeMappingTarget.value = null
            }
            return true
        }

        synchronized(stateLock) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> pressedKeys.add(event.keyCode)
                KeyEvent.ACTION_UP -> pressedKeys.remove(event.keyCode)
            }
            updateSummaryLocked()
        }
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        val src = event.source
        val isJoystickOrDpad =
            (src and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
                (src and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) ||
                (src and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD)
        if (!isJoystickOrDpad || event.action != MotionEvent.ACTION_MOVE) {
            return false
        }

        val dev = event.device
        val lx = centeredAxis(event, dev, MotionEvent.AXIS_X)
        val ly = centeredAxis(event, dev, MotionEvent.AXIS_Y)
        val rx = centeredAxis(event, dev, MotionEvent.AXIS_Z)
        val ry = centeredAxis(event, dev, MotionEvent.AXIS_RZ)

        val hatX = centeredAxis(event, dev, MotionEvent.AXIS_HAT_X)
        val hatY = centeredAxis(event, dev, MotionEvent.AXIS_HAT_Y)

        val lTrigger = maxOf(
            centeredAxis(event, dev, MotionEvent.AXIS_LTRIGGER),
            centeredAxis(event, dev, MotionEvent.AXIS_BRAKE),
        )
        val rTrigger = maxOf(
            centeredAxis(event, dev, MotionEvent.AXIS_RTRIGGER),
            centeredAxis(event, dev, MotionEvent.AXIS_GAS),
        )

        // Check if we are listening for a trigger or hat in button mapping
        val target = _activeMappingTarget.value
        if (target != null) {
            val mappedTrigger = when {
                lTrigger > 0.5f -> KeyEvent.KEYCODE_BUTTON_L2
                rTrigger > 0.5f -> KeyEvent.KEYCODE_BUTTON_R2
                hatX < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                hatX > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                hatY < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                hatY > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> null
            }
            if (mappedTrigger != null) {
                assignMapping(target.configKey, mappedTrigger)
                log("Mapped ${target.displayName} to ${GamepadButtonOption.fromKeyCode(mappedTrigger).label}")
                if (_wizardStepIndex.value >= 0) {
                    advanceWizard()
                } else {
                    _activeMappingTarget.value = null
                }
                return true
            }
        }

        synchronized(stateLock) {
            leftX = lx
            leftY = ly
            rightX = rx
            rightY = ry

            hatPressedKeys.clear()
            if (hatX < -0.5f) hatPressedKeys.add(KeyEvent.KEYCODE_DPAD_LEFT)
            if (hatX > 0.5f) hatPressedKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (hatY < -0.5f) hatPressedKeys.add(KeyEvent.KEYCODE_DPAD_UP)
            if (hatY > 0.5f) hatPressedKeys.add(KeyEvent.KEYCODE_DPAD_DOWN)

            triggerPressedKeys.clear()
            if (lTrigger > 0.5f) triggerPressedKeys.add(KeyEvent.KEYCODE_BUTTON_L2)
            if (rTrigger > 0.5f) triggerPressedKeys.add(KeyEvent.KEYCODE_BUTTON_R2)

            updateSummaryLocked()
        }
        return true
    }

    fun updateTouch(active: Boolean, x: Int, y: Int) {
        synchronized(stateLock) {
            touchActive = active
            touchX = x.coerceIn(0, TOUCH_MAX)
            touchY = y.coerceIn(0, TOUCH_MAX)
            updateSummaryLocked()
        }
    }

    fun setUiSpecial(bitMask: Int, pressed: Boolean) {
        synchronized(stateLock) {
            uiSpecialMask = if (pressed) {
                uiSpecialMask or bitMask
            } else {
                uiSpecialMask and bitMask.inv()
            }
            updateSummaryLocked()
        }
    }

    fun pressHome(pressed: Boolean) = setUiSpecial(SPECIAL_HOME, pressed)
    fun pressPower(pressed: Boolean) = setUiSpecial(SPECIAL_POWER, pressed)
    fun pressPowerLong(pressed: Boolean) = setUiSpecial(SPECIAL_POWER_LONG, pressed)

    fun triggerHomePulse() {
        scope.launch {
            pressHome(true)
            delay(150L)
            pressHome(false)
        }
    }

    fun triggerPowerPulse() {
        scope.launch {
            pressPower(true)
            delay(150L)
            pressPower(false)
        }
    }

    fun triggerPowerLongPulse() {
        scope.launch {
            pressPowerLong(true)
            delay(1500L)
            pressPowerLong(false)
        }
    }

    private fun takeSnapshot(): InputSnapshot = synchronized(stateLock) {
        val allPressed = HashSet<Int>(
            pressedKeys.size + hatPressedKeys.size + triggerPressedKeys.size + uiPressedKeys.size
        )
        allPressed.addAll(pressedKeys)
        allPressed.addAll(hatPressedKeys)
        allPressed.addAll(triggerPressedKeys)
        allPressed.addAll(uiPressedKeys)
        InputSnapshot(
            pressedKeys = allPressed,
            leftX = leftX,
            leftY = leftY,
            rightX = rightX,
            rightY = rightY,
            touchActive = touchActive,
            touchX = touchX,
            touchY = touchY,
            uiSpecialMask = uiSpecialMask,
        )
    }

    private fun updateSummaryLocked() {
        val activeLabels = mutableListOf<String>()
        val combined = pressedKeys + hatPressedKeys + triggerPressedKeys + uiPressedKeys
        combined.forEach { code ->
            val opt = GamepadButtonOption.fromKeyCode(code)
            if (opt != GamepadButtonOption.NONE) activeLabels.add(opt.label)
        }
        if (abs(leftX) > 0.05f || abs(leftY) > 0.05f) {
            activeLabels.add(String.format(Locale.US, "L(%.1f,%.1f)", leftX, -leftY))
        }
        if (abs(rightX) > 0.05f || abs(rightY) > 0.05f) {
            activeLabels.add(String.format(Locale.US, "R(%.1f,%.1f)", rightX, -rightY))
        }
        if (touchActive) {
            activeLabels.add("T($touchX,$touchY)")
        }
        if (uiSpecialMask != 0) {
            activeLabels.add("SPEC($uiSpecialMask)")
        }
        _lastInputSummary.value = if (activeLabels.isEmpty()) "Idle" else activeLabels.joinToString(" ")
    }

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$ts] $msg"
        _logs.value = (_logs.value + line).takeLast(MAX_LOGS)
    }

    private fun centeredAxis(event: MotionEvent, device: InputDevice?, axis: Int): Float {
        val range = device?.getMotionRange(axis, event.source)
            ?: device?.getMotionRange(axis)
            ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat) value else 0f
    }

    private fun isGamepadSource(source: Int): Boolean {
        return (source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
            (source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD)
    }

    private fun isGamepadKey(keyCode: Int): Boolean {
        return keyCode in setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}
