package com.erv.app.cycling

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.SavedBluetoothDeviceKind
import com.erv.app.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Concept2 PM5 proprietary BLE protocol (BikeErg, RowErg, SkiErg).
 *
 * Byte layouts come from the official "PM5 Bluetooth Smart Interface Definition" (rev 1.30):
 *  - 0x0031 General Status: distance (bytes 3..5, 0.1 m LSB)
 *  - 0x0032 Additional Status 1: speed (3..4, 0.001 m/s), stroke rate (5), heart rate (6),
 *           total average power (16..17, watts) when firmware supplies it
 *  - 0x0036 Additional Stroke Data: stroke power (3..4, watts)
 *
 * On a BikeErg the PM reports "stroke rate" as crank cadence (RPM) and the standard distance/speed
 * fields, so we surface the same live metrics the rest of the cardio flow already understands plus
 * power, which the CSC sensor path cannot provide.
 */
private const val C2_BASE_SUFFIX = "-43E5-11E4-916C-0800200C9A66"
private fun c2Uuid(short: String): UUID = UUID.fromString("CE06$short$C2_BASE_SUFFIX")

private val C2_ROWING_SERVICE_UUID: UUID = c2Uuid("0030")
private val C2_GENERAL_STATUS_UUID: UUID = c2Uuid("0031")
private val C2_ADDITIONAL_STATUS1_UUID: UUID = c2Uuid("0032")
private val C2_ADDITIONAL_STROKE_DATA_UUID: UUID = c2Uuid("0036")
private val C2_CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class Concept2BleConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Error
}

data class Concept2ScanRow(
    val address: String,
    val name: String?
)

data class Concept2ErgWorkoutSummary(
    val distanceMeters: Double?,
    val avgPowerWatts: Int?,
    val maxPowerWatts: Int?,
    val avgCadenceRpm: Int?,
    val maxCadenceRpm: Int?,
)

class Concept2Pm5BleViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "Concept2Pm5Ble"
        private const val SCAN_TIMEOUT_MS = 25_000L
    }

    private val userPreferences = UserPreferences(application)
    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    @Volatile
    private var gatt: BluetoothGatt? = null

    /** Characteristics waiting to have notifications enabled (one descriptor write at a time). */
    private val pendingNotifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

    private val scanSeen = ConcurrentHashMap<String, Concept2ScanRow>()
    private var scanJob: Job? = null

    private val _connectionState = MutableStateFlow(Concept2BleConnectionState.Idle)
    val connectionState: StateFlow<Concept2BleConnectionState> = _connectionState.asStateFlow()

    private val _scanRows = MutableStateFlow<List<Concept2ScanRow>>(emptyList())
    val scanRows: StateFlow<List<Concept2ScanRow>> = _scanRows.asStateFlow()

    private val _savedDevices = MutableStateFlow<List<SavedBluetoothDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedBluetoothDevice>> = _savedDevices.asStateFlow()

    private val _preferredDeviceAddress = MutableStateFlow<String?>(null)
    val preferredDeviceAddress: StateFlow<String?> = _preferredDeviceAddress.asStateFlow()

    private val _activeDeviceAddress = MutableStateFlow<String?>(null)
    val activeDeviceAddress: StateFlow<String?> = _activeDeviceAddress.asStateFlow()

    private val _connectedLabel = MutableStateFlow<String?>(null)
    val connectedLabel: StateFlow<String?> = _connectedLabel.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _currentPowerWatts = MutableStateFlow<Int?>(null)
    val currentPowerWatts: StateFlow<Int?> = _currentPowerWatts.asStateFlow()

    private val _currentCadenceRpm = MutableStateFlow<Int?>(null)
    val currentCadenceRpm: StateFlow<Int?> = _currentCadenceRpm.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow<Double?>(null)
    val currentSpeedKmh: StateFlow<Double?> = _currentSpeedKmh.asStateFlow()

    private val _workoutDistanceMeters = MutableStateFlow<Double?>(null)
    val workoutDistanceMeters: StateFlow<Double?> = _workoutDistanceMeters.asStateFlow()

    @Volatile
    private var liveWorkoutRecording = false

    // Distance accumulation across PM "piece" resets (monitor distance can restart at 0).
    @Volatile
    private var accumulatedDistanceMeters = 0.0

    @Volatile
    private var lastRawDistanceMeters: Double? = null

    // Power / cadence aggregates for the workout summary.
    @Volatile
    private var powerSampleSum = 0L

    @Volatile
    private var powerSampleCount = 0L

    @Volatile
    private var maxPowerWatts = 0

    @Volatile
    private var cadenceSampleSum = 0L

    @Volatile
    private var cadenceSampleCount = 0L

    @Volatile
    private var maxCadenceRpm = 0

    // Most recent "total average power" reported by the monitor (preferred over our sample mean).
    @Volatile
    private var monitorAvgPowerWatts: Int? = null

    val bleHardwareAvailable: Boolean
        get() = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun bluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    init {
        viewModelScope.launch {
            userPreferences.savedBleDevices.collect { devices ->
                _savedDevices.value = devices.filter {
                    it.kind == SavedBluetoothDeviceKind.CONCEPT2_PM
                }
            }
        }
        viewModelScope.launch {
            userPreferences.bleConcept2DeviceAddress.collect { address ->
                _preferredDeviceAddress.value = address
            }
        }
        viewModelScope.launch {
            val saved = userPreferences.bleConcept2DeviceAddress.first()
            if (!saved.isNullOrBlank() && bleHardwareAvailable && bluetoothEnabled() && hasConnectPermission()) {
                connectToAddress(saved, auto = true)
            }
        }
    }

    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    /** Retry the preferred saved erg once when a cycling workout begins later in the session. */
    fun tryPreferredDeviceReconnectOnce() {
        val saved = _preferredDeviceAddress.value
        if (saved.isNullOrBlank()) return
        if (!bleHardwareAvailable || !bluetoothEnabled() || !hasConnectPermission()) return
        if (_connectionState.value == Concept2BleConnectionState.Connected ||
            _connectionState.value == Concept2BleConnectionState.Connecting ||
            _connectionState.value == Concept2BleConnectionState.Scanning
        ) {
            return
        }
        connectToAddress(saved, auto = true)
    }

    fun resetWorkoutRecordingOnLiveStart() {
        liveWorkoutRecording = true
        accumulatedDistanceMeters = 0.0
        lastRawDistanceMeters = null
        powerSampleSum = 0L
        powerSampleCount = 0L
        maxPowerWatts = 0
        cadenceSampleSum = 0L
        cadenceSampleCount = 0L
        maxCadenceRpm = 0
        monitorAvgPowerWatts = null
        _workoutDistanceMeters.value = 0.0
    }

    fun discardWorkoutRecording() {
        liveWorkoutRecording = false
        accumulatedDistanceMeters = 0.0
        lastRawDistanceMeters = null
        _workoutDistanceMeters.value = null
    }

    fun takeWorkoutSummary(): Concept2ErgWorkoutSummary? {
        liveWorkoutRecording = false
        val distance = _workoutDistanceMeters.value?.takeIf { it > 0.0 }
        val avgPower = monitorAvgPowerWatts?.takeIf { it > 0 }
            ?: (if (powerSampleCount > 0) (powerSampleSum / powerSampleCount).toInt() else null)?.takeIf { it > 0 }
        val maxPower = maxPowerWatts.takeIf { it > 0 }
        val avgCadence = (if (cadenceSampleCount > 0) (cadenceSampleSum / cadenceSampleCount).toInt() else null)
            ?.takeIf { it > 0 }
        val maxCadence = maxCadenceRpm.takeIf { it > 0 }
        accumulatedDistanceMeters = 0.0
        lastRawDistanceMeters = null
        _workoutDistanceMeters.value = null
        if (distance == null && avgPower == null && maxPower == null && avgCadence == null) return null
        return Concept2ErgWorkoutSummary(
            distanceMeters = distance,
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPower,
            avgCadenceRpm = avgCadence,
            maxCadenceRpm = maxCadence,
        )
    }

    @SuppressLint("MissingPermission")
    fun startScanForSensors() {
        if (!bleHardwareAvailable) {
            _statusMessage.value = "This device does not support Bluetooth Low Energy."
            return
        }
        if (!bluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth to scan for a Concept2 PM monitor."
            return
        }
        if (!hasScanPermission()) {
            _statusMessage.value = "Allow Bluetooth (and location on older Android) to scan."
            return
        }
        stopScanInternal()
        scanSeen.clear()
        _scanRows.value = emptyList()
        _connectionState.value = Concept2BleConnectionState.Scanning
        _statusMessage.value = null
        val le = scanner ?: run {
            _connectionState.value = Concept2BleConnectionState.Error
            _statusMessage.value = "Bluetooth scanner unavailable."
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            le.startScan(null, settings, scanCallback)
        } catch (t: Throwable) {
            Log.e(TAG, "startScan failed", t)
            _connectionState.value = Concept2BleConnectionState.Error
            _statusMessage.value = "Could not start Bluetooth scan."
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value == Concept2BleConnectionState.Scanning) {
                stopScanInternal()
                _connectionState.value = Concept2BleConnectionState.Idle
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanInternal() {
        scanJob?.cancel()
        scanJob = null
        if (hasScanPermission()) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: Throwable) {
            }
        }
        if (_connectionState.value == Concept2BleConnectionState.Scanning) {
            _connectionState.value = Concept2BleConnectionState.Idle
        }
    }

    fun connectToScannedRow(row: Concept2ScanRow) {
        stopScanInternal()
        viewModelScope.launch {
            rememberErg(row.address, row.name)
            userPreferences.setBleConcept2DeviceAddress(row.address)
            connectToAddress(row.address, auto = false, label = row.name)
        }
    }

    fun connectToSavedDevice(device: SavedBluetoothDevice) {
        stopScanInternal()
        viewModelScope.launch {
            rememberErg(device.address, device.name)
            userPreferences.setBleConcept2DeviceAddress(device.address)
            connectToAddress(device.address, auto = false, label = device.name)
        }
    }

    fun forgetSavedDevice(address: String) {
        viewModelScope.launch {
            val normalizedAddress = normalizeBleAddress(address)
            userPreferences.removeSavedBleDevice(normalizedAddress)
            if (_preferredDeviceAddress.value == normalizedAddress) {
                userPreferences.setBleConcept2DeviceAddress(null)
            }
            if (_activeDeviceAddress.value == normalizedAddress) {
                disconnectUser()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String, auto: Boolean = false, label: String? = null) {
        if (!bleHardwareAvailable || !bluetoothEnabled()) return
        if (!hasConnectPermission()) {
            if (!auto) {
                _statusMessage.value = "Allow Bluetooth connect permission to use the Concept2 monitor."
            }
            return
        }
        stopScanInternal()
        disconnectGatt()
        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad BLE address", e)
            return
        }
        _activeDeviceAddress.value = normalizeBleAddress(device.address)
        _connectionState.value = Concept2BleConnectionState.Connecting
        _statusMessage.value = null
        _connectedLabel.value = label ?: device.name ?: address
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(getApplication(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(getApplication(), false, gattCallback)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "connectGatt failed", t)
            _activeDeviceAddress.value = null
            _connectionState.value = Concept2BleConnectionState.Error
            _statusMessage.value = "Could not connect to the Concept2 monitor."
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectUser() {
        stopScanInternal()
        disconnectGatt()
        clearConnectionUiState()
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        val g = gatt ?: return
        gatt = null
        pendingNotifyQueue.clear()
        try {
            g.disconnect()
        } catch (_: Throwable) {
        }
        try {
            g.close()
        } catch (_: Throwable) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val name = result.scanRecord?.deviceName ?: device.name
            val advertisesC2 = result.scanRecord?.serviceUuids.orEmpty()
                .any { it.uuid == C2_ROWING_SERVICE_UUID }
            if (!advertisesC2 && !looksLikeConcept2(name)) return
            scanSeen[address] = Concept2ScanRow(address = address, name = name)
            _scanRows.value = scanSeen.values.sortedBy { it.name?.lowercase() ?: it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed $errorCode")
            _connectionState.value = Concept2BleConnectionState.Error
            _statusMessage.value = "Bluetooth scan failed ($errorCode)."
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = Concept2BleConnectionState.Connected
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (this@Concept2Pm5BleViewModel.gatt === gatt) {
                            clearConnectionUiState()
                        }
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Disconnected from Concept2 monitor."
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = Concept2BleConnectionState.Error
                    _statusMessage.value = "Could not read services from the Concept2 monitor."
                }
                return
            }
            val service = gatt.getService(C2_ROWING_SERVICE_UUID)
            if (service == null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = Concept2BleConnectionState.Error
                    _statusMessage.value = "This device does not expose the Concept2 rowing service."
                }
                return
            }
            pendingNotifyQueue.clear()
            listOf(
                C2_GENERAL_STATUS_UUID,
                C2_ADDITIONAL_STATUS1_UUID,
                C2_ADDITIONAL_STROKE_DATA_UUID
            ).forEach { uuid ->
                service.getCharacteristic(uuid)?.let { pendingNotifyQueue.add(it) }
            }
            if (pendingNotifyQueue.isEmpty()) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = Concept2BleConnectionState.Error
                    _statusMessage.value = "Concept2 monitor did not expose live data characteristics."
                }
                return
            }
            viewModelScope.launch(Dispatchers.Main) {
                _statusMessage.value = "Connected. Start a piece on the monitor to stream live metrics."
            }
            enableNextNotification(gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            enableNextNotification(gatt)
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            dispatchCharacteristic(characteristic.uuid, characteristic.value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            dispatchCharacteristic(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNextNotification(gatt: BluetoothGatt) {
        val characteristic = pendingNotifyQueue.poll() ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(C2_CCCD_UUID)
        if (cccd == null) {
            enableNextNotification(gatt)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun dispatchCharacteristic(uuid: UUID, value: ByteArray?) {
        val data = value ?: return
        when (uuid) {
            C2_GENERAL_STATUS_UUID -> applyGeneralStatus(data)
            C2_ADDITIONAL_STATUS1_UUID -> applyAdditionalStatus1(data)
            C2_ADDITIONAL_STROKE_DATA_UUID -> applyAdditionalStrokeData(data)
        }
    }

    /** 0x0031 General Status: distance at bytes 3..5 (uint24, 0.1 m). */
    private fun applyGeneralStatus(data: ByteArray) {
        if (data.size < 6) return
        _statusMessage.value = null
        val rawDistanceMeters = readUInt24Le(data, 3) / 10.0
        if (!liveWorkoutRecording) {
            lastRawDistanceMeters = rawDistanceMeters
            return
        }
        val previous = lastRawDistanceMeters
        if (previous == null) {
            lastRawDistanceMeters = rawDistanceMeters
        } else if (rawDistanceMeters >= previous) {
            accumulatedDistanceMeters += (rawDistanceMeters - previous)
            lastRawDistanceMeters = rawDistanceMeters
        } else {
            // Monitor distance restarted (new piece / reset); count the fresh distance forward.
            accumulatedDistanceMeters += rawDistanceMeters
            lastRawDistanceMeters = rawDistanceMeters
        }
        _workoutDistanceMeters.value = accumulatedDistanceMeters
    }

    /** 0x0032 Additional Status 1: speed (3..4, 0.001 m/s), stroke rate (5), avg power (16..17). */
    private fun applyAdditionalStatus1(data: ByteArray) {
        if (data.size < 6) return
        val speedMps = readUInt16Le(data, 3) / 1000.0
        _currentSpeedKmh.value = if (speedMps > 0.0) speedMps * 3.6 else 0.0
        val strokeRate = data[5].toInt() and 0xFF
        if (strokeRate > 0) {
            _currentCadenceRpm.value = strokeRate
            if (liveWorkoutRecording) {
                cadenceSampleSum += strokeRate
                cadenceSampleCount++
                if (strokeRate > maxCadenceRpm) maxCadenceRpm = strokeRate
            }
        } else {
            _currentCadenceRpm.value = 0
        }
        if (data.size >= 18) {
            val avgPower = readUInt16Le(data, 16)
            if (avgPower in 1..3000) monitorAvgPowerWatts = avgPower
        }
    }

    /** 0x0036 Additional Stroke Data: instantaneous stroke power (3..4, watts). */
    private fun applyAdditionalStrokeData(data: ByteArray) {
        if (data.size < 5) return
        val power = readUInt16Le(data, 3)
        if (power in 0..3000) {
            _currentPowerWatts.value = power
            if (liveWorkoutRecording && power > 0) {
                powerSampleSum += power
                powerSampleCount++
                if (power > maxPowerWatts) maxPowerWatts = power
            }
        }
    }

    private suspend fun rememberErg(address: String, name: String?) {
        userPreferences.upsertSavedBleDevice(
            SavedBluetoothDevice(
                address = address,
                name = name,
                kind = SavedBluetoothDeviceKind.CONCEPT2_PM,
                lastConnectedEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun clearConnectionUiState() {
        _currentPowerWatts.value = null
        _currentCadenceRpm.value = null
        _currentSpeedKmh.value = null
        _workoutDistanceMeters.value = null
        _connectedLabel.value = null
        _activeDeviceAddress.value = null
        _connectionState.value = Concept2BleConnectionState.Idle
        _statusMessage.value = null
        lastRawDistanceMeters = null
        pendingNotifyQueue.clear()
    }

    private fun normalizeBleAddress(address: String): String = address.trim().uppercase()

    override fun onCleared() {
        stopScanInternal()
        disconnectGatt()
        super.onCleared()
    }
}

private fun looksLikeConcept2(name: String?): Boolean {
    val n = name?.trim()?.lowercase() ?: return false
    return n.startsWith("pm5") || n.startsWith("pm4") || n.startsWith("pm3") || n.contains("concept2")
}

private fun readUInt16Le(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

private fun readUInt24Le(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16)
