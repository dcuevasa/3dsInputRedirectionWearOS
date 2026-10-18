package com.example.a3dsinputredirectionwearos.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class DiscoveredController(
    val device: BluetoothDevice,
    val name: String,
    val hasResolvedName: Boolean,
    val address: String,
    val isGameController: Boolean,
    val bondState: Int,
    val isHidConnected: Boolean = false,
    val rssi: Int? = null,
    val transportLabel: String = "BT",
    val categoryLabel: String = "Device",
) {
    val isBonded: Boolean get() = bondState == BluetoothDevice.BOND_BONDED
    val isBonding: Boolean get() = bondState == BluetoothDevice.BOND_BONDING
}

class BluetoothControllerManager(private val context: Context) {

    companion object {
        // Hidden Android constant BluetoothProfile.HID_HOST / INPUT_DEVICE = 4
        private const val PROFILE_HID_HOST = 4
        private const val ACTION_HID_CONNECTION_STATE_CHANGED =
            "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED"
        private const val MAX_SCAN_DURATION_SECONDS = 45
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var hidHostProxy: BluetoothProfile? = null
    private var pendingAutoConnectAddress: String? = null
    private var isBleScanning = false
    private var userRequestedScan = false

    private val _isBluetoothSupported = MutableStateFlow(adapter != null)
    val isBluetoothSupported: StateFlow<Boolean> = _isBluetoothSupported.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanElapsedSeconds = MutableStateFlow(0)
    val scanElapsedSeconds: StateFlow<Int> = _scanElapsedSeconds.asStateFlow()

    private val _activeActionAddress = MutableStateFlow<String?>(null)
    val activeActionAddress: StateFlow<String?> = _activeActionAddress.asStateFlow()

    private val _connectedInputControllers = MutableStateFlow<List<String>>(emptyList())
    val connectedInputControllers: StateFlow<List<String>> = _connectedInputControllers.asStateFlow()

    private val _pairedControllers = MutableStateFlow<List<DiscoveredController>>(emptyList())
    val pairedControllers: StateFlow<List<DiscoveredController>> = _pairedControllers.asStateFlow()

    private val _discoveredControllers = MutableStateFlow<List<DiscoveredController>>(emptyList())
    val discoveredControllers: StateFlow<List<DiscoveredController>> = _discoveredControllers.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("Ready to scan")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _detailLog = MutableStateFlow<String>("Tap 'Search All Devices' to scan Classic BT + BLE")
    val detailLog: StateFlow<String> = _detailLog.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _locationEnabled = MutableStateFlow(true)
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    private var isReceiverRegistered = false

    private val scanTimerRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (!userRequestedScan) return
            val nextSec = _scanElapsedSeconds.value + 1
            _scanElapsedSeconds.value = nextSec
            val count = _discoveredControllers.value.size
            _detailLog.value = "Scanning Classic BT + BLE (No filter) • Found $count device(s)"

            if (nextSec >= MAX_SCAN_DURATION_SECONDS) {
                stopScan(reason = "Scan completed ($count device(s) found)")
                return
            }

            // Classic BT discovery stops after ~12s; restart it while userRequestedScan is active
            if (adapter?.isEnabled == true && hasScanPermission() && adapter.isDiscovering == false) {
                runCatching { adapter?.startDiscovery() }
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val recordName = result.scanRecord?.deviceName
            val rssi = result.rssi
            addOrUpdateDiscoveredDevice(
                device = device,
                extraName = recordName,
                rssi = rssi,
                isBle = true,
            )
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { res ->
                addOrUpdateDiscoveredDevice(
                    device = res.device,
                    extraName = res.scanRecord?.deviceName,
                    rssi = res.rssi,
                    isBle = true,
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isBleScanning = false
        }
    }

    private val hidProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == PROFILE_HID_HOST) {
                hidHostProxy = proxy
                refreshDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == PROFILE_HID_HOST) {
                hidHostProxy = null
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = extractDevice(intent) ?: return
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    val rssiRaw = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val rssi = if (rssiRaw != Short.MIN_VALUE.toInt()) rssiRaw else null
                    addOrUpdateDiscoveredDevice(
                        device = device,
                        extraName = extraName,
                        rssi = rssi,
                        isBle = false,
                    )
                }

                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device = extractDevice(intent) ?: return
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    addOrUpdateDiscoveredDevice(
                        device = device,
                        extraName = extraName,
                        rssi = null,
                        isBle = false,
                    )
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    if (userRequestedScan) {
                        _isScanning.value = true
                        _statusMessage.value = "Searching all Bluetooth devices..."
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (userRequestedScan && _scanElapsedSeconds.value < MAX_SCAN_DURATION_SECONDS) {
                        // Keep scanning until user stops or MAX_SCAN_DURATION_SECONDS reached
                        if (adapter?.isEnabled == true && hasScanPermission()) {
                            runCatching { adapter?.startDiscovery() }
                        }
                    } else if (!userRequestedScan) {
                        _isScanning.value = false
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val dev = extractDevice(intent)
                    val devName = dev?.let { safeDeviceName(it) } ?: "Device"
                    updateBondStateInLists(dev, bondState)

                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            _statusMessage.value = "Pairing with $devName..."
                            _detailLog.value = "Waiting for Bluetooth pairing handshake with $devName..."
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            _statusMessage.value = "Paired with $devName! Connecting HID..."
                            _detailLog.value = "Bond succeeded for $devName. Connecting gamepad input profile..."
                            if (dev != null) {
                                connectHidProfile(dev)
                            }
                            refreshDevices()
                        }
                        BluetoothDevice.BOND_NONE -> {
                            _activeActionAddress.value = null
                            if (prevBondState == BluetoothDevice.BOND_BONDING) {
                                _statusMessage.value = "Pairing failed or rejected for $devName"
                                _detailLog.value = "Tip: Put controller in pairing mode (hold Pair 3s) or try Watch BT Settings"
                            } else {
                                _statusMessage.value = "Unpaired $devName"
                                _detailLog.value = "$devName removed from paired devices"
                            }
                            refreshDevices()
                        }
                    }
                }

                ACTION_HID_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val dev = extractDevice(intent)
                    val devName = dev?.let { safeDeviceName(it) } ?: "Controller"
                    when (state) {
                        BluetoothProfile.STATE_CONNECTING -> {
                            _statusMessage.value = "Connecting input profile: $devName..."
                            _detailLog.value = "Establishing HID Host connection to $devName..."
                        }
                        BluetoothProfile.STATE_CONNECTED -> {
                            _activeActionAddress.value = null
                            _statusMessage.value = "✅ Connected to $devName!"
                            _detailLog.value = "$devName is now active and ready for 3DS input!"
                            refreshDevices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (_activeActionAddress.value == dev?.address) {
                                _activeActionAddress.value = null
                            }
                            refreshDevices()
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    mainHandler.postDelayed({ refreshDevices() }, 400L)
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    refreshDevices()
                }
            }
        }
    }

    init {
        registerReceiver()
        initHidProfileProxy()
        refreshDevices()
    }

    private fun extractDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun initHidProfileProxy() {
        runCatching {
            adapter?.getProfileProxy(context, hidProfileListener, PROFILE_HID_HOST)
        }
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(ACTION_HID_CONNECTION_STATE_CHANGED)
        }
        // IMPORTANT: Must use RECEIVER_EXPORTED so com.android.bluetooth (UID 1002) broadcasts are received on Android 14+!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        isReceiverRegistered = true
    }

    fun unregister() {
        stopScan()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            isReceiverRegistered = false
        }
        hidHostProxy?.let { proxy ->
            runCatching { adapter?.closeProfileProxy(PROFILE_HID_HOST, proxy) }
            hidHostProxy = null
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun checkPermissionsAndLocationState() {
        _permissionsGranted.value = hasConnectPermission() && hasScanPermission() && hasLocationPermission()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        _locationEnabled.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled != false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        checkPermissionsAndLocationState()

        // 1. Refresh active InputDevices (connected controllers ready for gameplay)
        val connectedNames = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            InputDevice.getDevice(id)?.takeIf { isInputController(it) }?.name
        }.distinct()
        _connectedInputControllers.value = connectedNames

        // 2. Query connected HID Host devices if proxy is available
        val hidConnectedAddresses = getHidConnectedAddresses()

        // 3. Refresh paired Bluetooth devices (ALL paired devices, no hiding)
        val a = adapter
        if (a != null && a.isEnabled && hasConnectPermission()) {
            runCatching {
                val paired = a.bondedDevices?.map { dev ->
                    val rawName = safeDeviceName(dev)
                    val isHid = hidConnectedAddresses.contains(dev.address) ||
                        connectedNames.any { it.equals(rawName, ignoreCase = true) }
                    DiscoveredController(
                        device = dev,
                        name = rawName,
                        hasResolvedName = !dev.name.isNullOrBlank(),
                        address = dev.address,
                        isGameController = isLikelyGameController(dev, rawName),
                        bondState = dev.bondState,
                        isHidConnected = isHid,
                        transportLabel = getDeviceTypeLabel(dev),
                        categoryLabel = getCategoryLabel(dev, rawName),
                    )
                }?.sortedWith(
                    compareByDescending<DiscoveredController> { it.isHidConnected }
                        .thenByDescending { it.isGameController }
                        .thenBy { it.name }
                )
                _pairedControllers.value = paired ?: emptyList()
            }.onFailure {
                _pairedControllers.value = emptyList()
            }
        } else {
            _pairedControllers.value = emptyList()
        }

        // Also update bond & HID state of already-discovered devices
        val currentDiscovered = _discoveredControllers.value
        if (currentDiscovered.isNotEmpty() && hasConnectPermission()) {
            _discoveredControllers.value = currentDiscovered.map { item ->
                val bond = runCatching { item.device.bondState }.getOrDefault(item.bondState)
                val isHid = hidConnectedAddresses.contains(item.address) ||
                    connectedNames.any { it.equals(item.name, ignoreCase = true) }
                item.copy(
                    bondState = bond,
                    isHidConnected = isHid,
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        checkPermissionsAndLocationState()
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth is turned OFF"
            _detailLog.value = "Please enable Bluetooth on your watch first"
            return false
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            _statusMessage.value = "Bluetooth permissions needed"
            _detailLog.value = "Tap 'Grant Permissions' above so the watch allows Bluetooth scanning"
            return false
        }

        registerReceiver()
        refreshDevices()

        // Clear old unbonded list and start fresh
        _discoveredControllers.value = emptyList()
        _scanElapsedSeconds.value = 0
        userRequestedScan = true
        _isScanning.value = true

        // 1. Start Classic Bluetooth Discovery (BR/EDR inquiry for 8BitDo D-Input, Switch, DS4, etc.)
        if (adapter.isDiscovering) {
            runCatching { adapter?.cancelDiscovery() }
        }
        val classicStarted = runCatching { adapter?.startDiscovery() == true }.getOrDefault(false)

        // 2. Simultaneously start Unfiltered BLE Scan (for BLE HID gamepads & dual-mode controllers)
        startBleScanInternal()

        mainHandler.removeCallbacks(scanTimerRunnable)
        mainHandler.postDelayed(scanTimerRunnable, 1000L)

        _statusMessage.value = "Searching ALL Bluetooth devices..."
        _detailLog.value = if (classicStarted || isBleScanning) {
            "Scanning Classic BT + BLE (No filter) • Hold Pair button on your gamepad"
        } else {
            "Scan requested — if 0 devices appear, verify Location is ON"
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanInternal() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (isBleScanning) {
            runCatching { scanner.stopScan(leScanCallback) }
            isBleScanning = false
        }
        runCatching {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            // Pass null filters so ZERO BLE devices are filtered out!
            scanner.startScan(null, settings, leScanCallback)
            isBleScanning = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanInternal() {
        if (isBleScanning) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(leScanCallback) }
            isBleScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(reason: String? = null) {
        userRequestedScan = false
        mainHandler.removeCallbacks(scanTimerRunnable)
        if (adapter?.isDiscovering == true && hasScanPermission()) {
            runCatching { adapter?.cancelDiscovery() }
        }
        stopBleScanInternal()
        _isScanning.value = false
        if (reason != null) {
            _statusMessage.value = reason
        } else {
            val count = _discoveredControllers.value.size
            _statusMessage.value = "Search stopped ($count device(s) listed)"
        }
    }

    @SuppressLint("MissingPermission")
    fun pairAndConnect(item: DiscoveredController): Boolean {
        if (!hasConnectPermission()) {
            _statusMessage.value = "Bluetooth Connect permission required"
            return false
        }
        // Always stop scanning before pairing/connecting so the radio isn't busy with inquiry
        stopScan()

        val dev = item.device
        _activeActionAddress.value = dev.address
        pendingAutoConnectAddress = dev.address

        val currentBondState = runCatching { dev.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        if (currentBondState == BluetoothDevice.BOND_BONDED) {
            _statusMessage.value = "Connecting to ${item.name}..."
            _detailLog.value = "Device is already paired. Connecting HID input profile..."
            val connected = connectHidProfile(dev)
            runCatching { dev.fetchUuidsWithSdp() }
            if (!connected) {
                _detailLog.value = "Sent connection request to ${item.name}. Press a button on the controller to wake it."
            }
            mainHandler.postDelayed({
                if (_activeActionAddress.value == dev.address) {
                    _activeActionAddress.value = null
                }
                refreshDevices()
            }, 4000L)
            return true
        }

        _statusMessage.value = "Pairing with ${item.name}..."
        _detailLog.value = "Requesting Bluetooth bond with ${item.name} (${item.address})..."
        val bondStarted = runCatching { dev.createBond() }.getOrDefault(false)
        if (!bondStarted) {
            // Fallback: trigger SDP and HID connect
            runCatching { dev.fetchUuidsWithSdp() }
            val hidStarted = connectHidProfile(dev)
            if (!hidStarted) {
                _activeActionAddress.value = null
                _statusMessage.value = "Could not start pairing with ${item.name}"
                _detailLog.value = "Try tapping 'Open Watch BT Settings' below to pair via system menu"
            }
        }
        return bondStarted
    }

    @SuppressLint("MissingPermission")
    fun connectHidProfile(device: BluetoothDevice): Boolean {
        val proxy = hidHostProxy
        if (proxy == null) {
            initHidProfileProxy()
            return false
        }
        return runCatching {
            val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
            connectMethod.isAccessible = true
            val result = connectMethod.invoke(proxy, device) as? Boolean ?: false
            result
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun unpair(item: DiscoveredController): Boolean {
        if (!hasConnectPermission()) return false
        stopScan()
        return runCatching {
            val removeBondMethod = item.device.javaClass.getMethod("removeBond")
            val result = removeBondMethod.invoke(item.device) as? Boolean ?: false
            if (result) {
                _statusMessage.value = "Unpairing ${item.name}..."
                _detailLog.value = "Removing existing bond so you can re-pair cleanly"
            }
            mainHandler.postDelayed({ refreshDevices() }, 600L)
            result
        }.getOrDefault(false)
    }

    private fun getHidConnectedAddresses(): Set<String> {
        val proxy = hidHostProxy ?: return emptySet()
        return runCatching {
            proxy.connectedDevices.map { it.address }.toSet()
        }.getOrDefault(emptySet())
    }

    fun openBluetoothSettings(ctx: Context) {
        stopScan()
        val candidates = listOf(
            Intent("com.google.android.clockwork.settings.BLUETOOTH_SETTINGS"),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                runCatching { ctx.startActivity(intent) }
                return
            }
        }
        try {
            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            _statusMessage.value = "Could not open Bluetooth settings"
        }
    }

    fun openLocationSettings(ctx: Context) {
        stopScan()
        try {
            ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            openBluetoothSettings(ctx)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice, extraName: String? = null): String {
        val fromDevice = if (hasConnectPermission()) {
            runCatching { device.name }.getOrNull()
        } else {
            null
        }
        val candidate = when {
            !fromDevice.isNullOrBlank() -> fromDevice.trim()
            !extraName.isNullOrBlank() -> extraName.trim()
            else -> null
        }
        return candidate ?: "Device (${device.address.takeLast(8)})"
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceTypeLabel(device: BluetoothDevice, isBle: Boolean = false): String {
        if (!hasConnectPermission()) return if (isBle) "BLE" else "BT"
        return when (runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic BT"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual BT+BLE"
            else -> if (isBle) "BLE" else "BT"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCategoryLabel(device: BluetoothDevice, resolvedName: String): String {
        if (isLikelyGameController(device, resolvedName)) return "Gamepad"
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ?: return "Bluetooth"
        return when (major) {
            BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Media"
            BluetoothClass.Device.Major.PHONE -> "Phone"
            BluetoothClass.Device.Major.COMPUTER -> "Computer"
            BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            else -> "Bluetooth"
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateBondStateInLists(device: BluetoothDevice?, newBondState: Int) {
        if (device == null) return
        _discoveredControllers.value = _discoveredControllers.value.map {
            if (it.address == device.address) it.copy(bondState = newBondState) else it
        }
    }

    @SuppressLint("MissingPermission")
    private fun addOrUpdateDiscoveredDevice(
        device: BluetoothDevice,
        extraName: String?,
        rssi: Int?,
        isBle: Boolean,
    ) {
        val current = _discoveredControllers.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.address == device.address }
        val existing = if (existingIndex >= 0) current[existingIndex] else null

        val rawDeviceName = if (hasConnectPermission()) runCatching { device.name }.getOrNull() else null
        val hasResolved = !rawDeviceName.isNullOrBlank() || !extraName.isNullOrBlank() || (existing?.hasResolvedName == true)

        val displayName = when {
            !rawDeviceName.isNullOrBlank() -> rawDeviceName.trim()
            !extraName.isNullOrBlank() -> extraName.trim()
            existing != null && existing.hasResolvedName -> existing.name
            else -> "Unnamed (${device.address})"
        }

        val isController = isLikelyGameController(device, displayName) || (existing?.isGameController == true)
        val bondState = if (hasConnectPermission()) {
            runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        } else {
            BluetoothDevice.BOND_NONE
        }
        val hidConnected = getHidConnectedAddresses().contains(device.address) ||
            _connectedInputControllers.value.any { it.equals(displayName, ignoreCase = true) }

        val updatedItem = DiscoveredController(
            device = device,
            name = displayName,
            hasResolvedName = hasResolved,
            address = device.address,
            isGameController = isController,
            bondState = bondState,
            isHidConnected = hidConnected,
            rssi = rssi ?: existing?.rssi,
            transportLabel = getDeviceTypeLabel(device, isBle),
            categoryLabel = getCategoryLabel(device, displayName),
        )

        if (existingIndex >= 0) {
            // Update in place so items don't jump around while user is trying to tap!
            current[existingIndex] = updatedItem
        } else {
            // Insert game controllers or named devices before unnamed devices, preserving stable order within groups
            val insertIdx = when {
                updatedItem.isGameController -> {
                    val lastControllerIdx = current.indexOfLast { it.isGameController }
                    if (lastControllerIdx >= 0) lastControllerIdx + 1 else 0
                }
                updatedItem.hasResolvedName -> {
                    val firstUnnamedIdx = current.indexOfFirst { !it.hasResolvedName && !it.isGameController }
                    if (firstUnnamedIdx >= 0) firstUnnamedIdx else current.size
                }
                else -> current.size
            }
            current.add(insertIdx, updatedItem)
        }

        _discoveredControllers.value = current
        val total = current.size
        if (userRequestedScan) {
            _statusMessage.value = "Searching... ($total device(s) found)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun isLikelyGameController(device: BluetoothDevice, candidateName: String? = null): Boolean {
        if (hasConnectPermission()) {
            val devClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull() ?: 0
            // Standard Bluetooth HID Gamepad / Joystick / Keyboard-Gamepad combo classes
            if (devClass == 0x0504 || devClass == 0x0508 || devClass == 0x0540 || devClass == 0x05C0) return true
            val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ?: 0
            if (major == BluetoothClass.Device.Major.PERIPHERAL) return true
        }

        val name = (candidateName ?: if (hasConnectPermission()) runCatching { device.name }.getOrNull() else null)
            ?.lowercase(Locale.US)
            .orEmpty()
        if (name.isBlank()) return false

        val keywords = listOf(
            "8bitdo", "sn30", "sf30", "pro 2", "ultimate", "zero 2", "micro",
            "controller", "gamepad", "joystick", "joypad", "xbox", "wireless",
            "dualsense", "dualshock", "playstation", "switch", "pro con",
            "joy-con", "stadia", "luna", "moga", "shield", "stratus",
            "gamesir", "backbone", "razer", "kishi", "flydigi", "gulikit",
            "retro", "arcade", "pad"
        )
        return keywords.any { name.contains(it) }
    }

    private fun isInputController(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }
}
