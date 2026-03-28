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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
private val CSC_MEASUREMENT_UUID: UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")
private val CSC_CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class CyclingCscBleConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Error
}

data class CyclingCscScanRow(
    val address: String,
    val name: String?
)

data class CyclingCscWorkoutSummary(
    val distanceMeters: Double?,
)

class CyclingCscBleViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "CyclingCscBle"
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

    private val scanSeen = ConcurrentHashMap<String, CyclingCscScanRow>()
    private var scanJob: Job? = null

    private val _connectionState = MutableStateFlow(CyclingCscBleConnectionState.Idle)
    val connectionState: StateFlow<CyclingCscBleConnectionState> = _connectionState.asStateFlow()

    private val _scanRows = MutableStateFlow<List<CyclingCscScanRow>>(emptyList())
    val scanRows: StateFlow<List<CyclingCscScanRow>> = _scanRows.asStateFlow()

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

    private val _currentSpeedKmh = MutableStateFlow<Double?>(null)
    val currentSpeedKmh: StateFlow<Double?> = _currentSpeedKmh.asStateFlow()

    private val _currentCadenceRpm = MutableStateFlow<Int?>(null)
    val currentCadenceRpm: StateFlow<Int?> = _currentCadenceRpm.asStateFlow()

    private val _workoutDistanceMeters = MutableStateFlow<Double?>(null)
    val workoutDistanceMeters: StateFlow<Double?> = _workoutDistanceMeters.asStateFlow()

    @Volatile
    private var wheelCircumferenceMeters: Double = 2.105

    @Volatile
    private var liveWorkoutRecording = false

    @Volatile
    private var latestCumulativeWheelRevolutions: Long? = null

    @Volatile
    private var workoutBaselineWheelRevolutions: Long? = null

    @Volatile
    private var previousWheelRevolutions: Long? = null

    @Volatile
    private var previousWheelEventTime: Int? = null

    @Volatile
    private var previousCrankRevolutions: Int? = null

    @Volatile
    private var previousCrankEventTime: Int? = null

    val bleHardwareAvailable: Boolean
        get() = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun bluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    init {
        viewModelScope.launch {
            userPreferences.savedBleDevices.collect { devices ->
                _savedDevices.value = devices.filter {
                    it.kind == SavedBluetoothDeviceKind.CYCLING_SPEED_CADENCE_SENSOR
                }
            }
        }
        viewModelScope.launch {
            userPreferences.bleCscDeviceAddress.collect { address ->
                _preferredDeviceAddress.value = address
            }
        }
        viewModelScope.launch {
            userPreferences.cyclingWheelCircumferenceMm.collect { mm ->
                wheelCircumferenceMeters = mm / 1000.0
            }
        }
        viewModelScope.launch {
            val saved = userPreferences.bleCscDeviceAddress.first()
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

    fun resetWorkoutRecordingOnLiveStart() {
        liveWorkoutRecording = true
        workoutBaselineWheelRevolutions = latestCumulativeWheelRevolutions
        _workoutDistanceMeters.value = 0.0
    }

    fun discardWorkoutRecording() {
        liveWorkoutRecording = false
        workoutBaselineWheelRevolutions = latestCumulativeWheelRevolutions
        _workoutDistanceMeters.value = null
    }

    fun takeWorkoutSummary(): CyclingCscWorkoutSummary? {
        liveWorkoutRecording = false
        val distance = _workoutDistanceMeters.value?.takeIf { it > 0.0 }
        workoutBaselineWheelRevolutions = latestCumulativeWheelRevolutions
        _workoutDistanceMeters.value = null
        return if (distance != null) CyclingCscWorkoutSummary(distance) else null
    }

    @SuppressLint("MissingPermission")
    fun startScanForSensors() {
        if (!bleHardwareAvailable) {
            _statusMessage.value = "This device does not support Bluetooth Low Energy cycling sensors."
            return
        }
        if (!bluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth to scan for a speed/cadence sensor."
            return
        }
        if (!hasScanPermission()) {
            _statusMessage.value = "Allow Bluetooth (and location on older Android) to scan."
            return
        }
        stopScanInternal()
        scanSeen.clear()
        _scanRows.value = emptyList()
        _connectionState.value = CyclingCscBleConnectionState.Scanning
        _statusMessage.value = null
        val le = scanner ?: run {
            _connectionState.value = CyclingCscBleConnectionState.Error
            _statusMessage.value = "Bluetooth scanner unavailable."
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(CSC_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            le.startScan(listOf(filter), settings, scanCallback)
        } catch (t: Throwable) {
            Log.e(TAG, "startScan failed", t)
            _connectionState.value = CyclingCscBleConnectionState.Error
            _statusMessage.value = "Could not start Bluetooth scan."
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value == CyclingCscBleConnectionState.Scanning) {
                stopScanInternal()
                _connectionState.value = CyclingCscBleConnectionState.Idle
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
        if (_connectionState.value == CyclingCscBleConnectionState.Scanning) {
            _connectionState.value = CyclingCscBleConnectionState.Idle
        }
    }

    fun connectToScannedRow(row: CyclingCscScanRow) {
        stopScanInternal()
        viewModelScope.launch {
            rememberCyclingSensor(row.address, row.name)
            userPreferences.setBleCscDeviceAddress(row.address)
            connectToAddress(row.address, auto = false, label = row.name)
        }
    }

    fun connectToSavedDevice(device: SavedBluetoothDevice) {
        stopScanInternal()
        viewModelScope.launch {
            rememberCyclingSensor(device.address, device.name)
            userPreferences.setBleCscDeviceAddress(device.address)
            connectToAddress(device.address, auto = false, label = device.name)
        }
    }

    fun forgetSavedDevice(address: String) {
        viewModelScope.launch {
            val normalizedAddress = normalizeBleAddress(address)
            userPreferences.removeSavedBleDevice(normalizedAddress)
            if (_preferredDeviceAddress.value == normalizedAddress) {
                userPreferences.setBleCscDeviceAddress(null)
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
                _statusMessage.value = "Allow Bluetooth connect permission to use the cycling sensor."
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
        _connectionState.value = CyclingCscBleConnectionState.Connecting
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
            _connectionState.value = CyclingCscBleConnectionState.Error
            _statusMessage.value = "Could not connect to the cycling sensor."
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
            scanSeen[address] = CyclingCscScanRow(address = address, name = name)
            _scanRows.value = scanSeen.values.sortedBy { it.name?.lowercase() ?: it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed $errorCode")
            _connectionState.value = CyclingCscBleConnectionState.Error
            _statusMessage.value = "Bluetooth scan failed ($errorCode)."
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = CyclingCscBleConnectionState.Connected
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (this@CyclingCscBleViewModel.gatt === gatt) {
                            clearConnectionUiState()
                        }
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Disconnected from cycling sensor."
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = CyclingCscBleConnectionState.Error
                    _statusMessage.value = "Could not read services from the cycling sensor."
                }
                return
            }
            val service = gatt.getService(CSC_SERVICE_UUID)
            val measurement = service?.getCharacteristic(CSC_MEASUREMENT_UUID)
            if (measurement == null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = CyclingCscBleConnectionState.Error
                    _statusMessage.value = "This device does not expose standard CSC data."
                }
                return
            }
            gatt.setCharacteristicNotification(measurement, true)
            val cccd = measurement.getDescriptor(CSC_CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid == CSC_MEASUREMENT_UUID) {
                applyCscMeasurement(characteristic.value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid == CSC_MEASUREMENT_UUID) {
                applyCscMeasurement(value)
            }
        }
    }

    private fun applyCscMeasurement(value: ByteArray?) {
        val data = value ?: return
        if (data.isEmpty()) return
        val flags = data[0].toInt() and 0xFF
        var index = 1
        val wheelPresent = (flags and 0x01) != 0
        val crankPresent = (flags and 0x02) != 0

        if (wheelPresent) {
            if (data.size < index + 6) return
            val cumulativeWheelRevs = readUInt32Le(data, index)
            index += 4
            val lastWheelEventTime = readUInt16Le(data, index)
            index += 2
            applyWheelMeasurement(cumulativeWheelRevs, lastWheelEventTime)
        }

        if (crankPresent) {
            if (data.size < index + 4) return
            val cumulativeCrankRevs = readUInt16Le(data, index)
            index += 2
            val lastCrankEventTime = readUInt16Le(data, index)
            applyCrankMeasurement(cumulativeCrankRevs, lastCrankEventTime)
        }
    }

    private fun applyWheelMeasurement(cumulativeWheelRevs: Long, lastWheelEventTime: Int) {
        val prevRevs = previousWheelRevolutions
        val prevEvent = previousWheelEventTime
        latestCumulativeWheelRevolutions = cumulativeWheelRevs
        if (liveWorkoutRecording) {
            if (workoutBaselineWheelRevolutions == null) {
                workoutBaselineWheelRevolutions = cumulativeWheelRevs
            }
            val baseline = workoutBaselineWheelRevolutions ?: cumulativeWheelRevs
            _workoutDistanceMeters.value =
                ((cumulativeWheelRevs - baseline).coerceAtLeast(0) * wheelCircumferenceMeters)
        }
        if (prevRevs != null && prevEvent != null) {
            val revDelta = cumulativeWheelRevs - prevRevs
            val eventDelta = deltaUInt16(lastWheelEventTime, prevEvent)
            if (revDelta > 0 && eventDelta > 0) {
                val meters = revDelta * wheelCircumferenceMeters
                val seconds = eventDelta / 1024.0
                _currentSpeedKmh.value = (meters / seconds) * 3.6
            }
        }
        previousWheelRevolutions = cumulativeWheelRevs
        previousWheelEventTime = lastWheelEventTime
    }

    private fun applyCrankMeasurement(cumulativeCrankRevs: Int, lastCrankEventTime: Int) {
        val prevRevs = previousCrankRevolutions
        val prevEvent = previousCrankEventTime
        if (prevRevs != null && prevEvent != null) {
            val revDelta = deltaUInt16(cumulativeCrankRevs, prevRevs)
            val eventDelta = deltaUInt16(lastCrankEventTime, prevEvent)
            if (revDelta > 0 && eventDelta > 0) {
                _currentCadenceRpm.value = ((revDelta * 60.0 * 1024.0) / eventDelta).roundToInt()
            }
        }
        previousCrankRevolutions = cumulativeCrankRevs
        previousCrankEventTime = lastCrankEventTime
    }

    private suspend fun rememberCyclingSensor(address: String, name: String?) {
        userPreferences.upsertSavedBleDevice(
            SavedBluetoothDevice(
                address = address,
                name = name,
                kind = SavedBluetoothDeviceKind.CYCLING_SPEED_CADENCE_SENSOR,
                lastConnectedEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun clearConnectionUiState() {
        _currentSpeedKmh.value = null
        _currentCadenceRpm.value = null
        _workoutDistanceMeters.value = null
        _connectedLabel.value = null
        _activeDeviceAddress.value = null
        _connectionState.value = CyclingCscBleConnectionState.Idle
        _statusMessage.value = null
        latestCumulativeWheelRevolutions = null
        workoutBaselineWheelRevolutions = null
        previousWheelRevolutions = null
        previousWheelEventTime = null
        previousCrankRevolutions = null
        previousCrankEventTime = null
    }

    private fun normalizeBleAddress(address: String): String = address.trim().uppercase()

    override fun onCleared() {
        stopScanInternal()
        disconnectGatt()
        super.onCleared()
    }
}

private fun readUInt16Le(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

private fun readUInt32Le(data: ByteArray, offset: Int): Long =
    (data[offset].toLong() and 0xFFL) or
        ((data[offset + 1].toLong() and 0xFFL) shl 8) or
        ((data[offset + 2].toLong() and 0xFFL) shl 16) or
        ((data[offset + 3].toLong() and 0xFFL) shl 24)

private fun deltaUInt16(current: Int, previous: Int): Int =
    if (current >= previous) current - previous else (0x10000 - previous) + current
