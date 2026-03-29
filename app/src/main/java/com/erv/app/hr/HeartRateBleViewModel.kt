package com.erv.app.hr

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
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
import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
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
import kotlin.math.max
import kotlin.math.min

private val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
private val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
private val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class HeartRateBleConnectionState {
    Idle,
    Scanning,
    Connecting,
    Connected,
    Error
}

data class HeartRateBleScanRow(
    val address: String,
    val name: String?
)

class HeartRateBleViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "HeartRateBle"
        private const val SCAN_TIMEOUT_MS = 25_000L
    }

    private val userPreferences = UserPreferences(application)
    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    @Volatile
    private var gatt: BluetoothGatt? = null

    private val scanSeen = ConcurrentHashMap<String, HeartRateBleScanRow>()
    private var scanJob: Job? = null

    private val _connectionState = MutableStateFlow(HeartRateBleConnectionState.Idle)
    val connectionState: StateFlow<HeartRateBleConnectionState> = _connectionState.asStateFlow()

    private val _scanRows = MutableStateFlow<List<HeartRateBleScanRow>>(emptyList())
    val scanRows: StateFlow<List<HeartRateBleScanRow>> = _scanRows.asStateFlow()

    private val _savedDevices = MutableStateFlow<List<SavedBluetoothDevice>>(emptyList())
    val savedDevices: StateFlow<List<SavedBluetoothDevice>> = _savedDevices.asStateFlow()

    private val _preferredDeviceAddress = MutableStateFlow<String?>(null)
    val preferredDeviceAddress: StateFlow<String?> = _preferredDeviceAddress.asStateFlow()

    private val _activeDeviceAddress = MutableStateFlow<String?>(null)
    val activeDeviceAddress: StateFlow<String?> = _activeDeviceAddress.asStateFlow()

    /** Live BPM from the sensor (UI); updated whenever connected. */
    private val _displayBpm = MutableStateFlow<Int?>(null)
    val displayBpm: StateFlow<Int?> = _displayBpm.asStateFlow()

    /** Sensor battery 0–100 when the device exposes standard BLE Battery Service; null if unknown. */
    private val _displayBatteryPercent = MutableStateFlow<Int?>(null)
    val displayBatteryPercent: StateFlow<Int?> = _displayBatteryPercent.asStateFlow()

    private val _connectedLabel = MutableStateFlow<String?>(null)
    val connectedLabel: StateFlow<String?> = _connectedLabel.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    private var batteryNotifySetupDone = false

    @Volatile
    private var liveWorkoutRecording = false

    @Volatile
    private var workoutSamples = 0

    @Volatile
    private var workoutBpmSum = 0L

    @Volatile
    private var workoutMinBpm: Int? = null

    @Volatile
    private var workoutMaxBpm: Int? = null

    private val workoutHrSamples = mutableListOf<CardioHrSample>()

    @Volatile
    private var unifiedWorkoutRecording = false

    @Volatile
    private var unifiedWorkoutSamples = 0

    @Volatile
    private var unifiedWorkoutBpmSum = 0L

    @Volatile
    private var unifiedWorkoutMinBpm: Int? = null

    @Volatile
    private var unifiedWorkoutMaxBpm: Int? = null

    private val unifiedWorkoutHrSamples = mutableListOf<CardioHrSample>()

    val bleHardwareAvailable: Boolean
        get() = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun bluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    init {
        viewModelScope.launch {
            userPreferences.savedBleDevices.collect { saved ->
                _savedDevices.value = saved.filter {
                    it.kind == SavedBluetoothDeviceKind.HEART_RATE_MONITOR
                }
            }
        }
        viewModelScope.launch {
            userPreferences.bleHeartRateDeviceAddress.collect { address ->
                _preferredDeviceAddress.value = address
            }
        }
        viewModelScope.launch {
            val saved = userPreferences.bleHeartRateDeviceAddress.first()
            if (!saved.isNullOrBlank() && bleHardwareAvailable && bluetoothEnabled()) {
                if (hasConnectPermission()) {
                    connectToAddress(saved, auto = true)
                }
            }
        }
    }

    /**
     * One-shot reconnect attempt for the preferred saved device when UI exposes the HR monitor later
     * in the app session. Does not start scanning; it only retries the known saved address.
     */
    fun tryPreferredDeviceReconnectOnce() {
        val saved = _preferredDeviceAddress.value
        if (saved.isNullOrBlank()) return
        if (!bleHardwareAvailable || !bluetoothEnabled() || !hasConnectPermission()) return
        if (_connectionState.value == HeartRateBleConnectionState.Connected ||
            _connectionState.value == HeartRateBleConnectionState.Connecting ||
            _connectionState.value == HeartRateBleConnectionState.Scanning
        ) {
            return
        }
        connectToAddress(saved, auto = true)
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

    /**
     * Call when a cardio or weight **live** session becomes active (timer / live lift started).
     * Clears in-memory workout HR stats so only samples during that session are summarized.
     */
    fun resetWorkoutRecordingOnLiveStart() {
        liveWorkoutRecording = true
        workoutSamples = 0
        workoutBpmSum = 0L
        workoutMinBpm = null
        workoutMaxBpm = null
        workoutHrSamples.clear()
    }

    /**
     * Drop buffered workout samples (e.g. user cancelled a live session) without attaching to a saved session.
     */
    fun discardWorkoutRecording() {
        liveWorkoutRecording = false
        workoutSamples = 0
        workoutBpmSum = 0L
        workoutMinBpm = null
        workoutMaxBpm = null
        workoutHrSamples.clear()
    }

    fun startUnifiedWorkoutRecording() {
        if (unifiedWorkoutRecording) return
        unifiedWorkoutRecording = true
        unifiedWorkoutSamples = 0
        unifiedWorkoutBpmSum = 0L
        unifiedWorkoutMinBpm = null
        unifiedWorkoutMaxBpm = null
        unifiedWorkoutHrSamples.clear()
    }

    fun discardUnifiedWorkoutRecording() {
        unifiedWorkoutRecording = false
        unifiedWorkoutSamples = 0
        unifiedWorkoutBpmSum = 0L
        unifiedWorkoutMinBpm = null
        unifiedWorkoutMaxBpm = null
        unifiedWorkoutHrSamples.clear()
    }

    fun takeUnifiedWorkoutHeartRateSummary(): CardioHrScaffolding? {
        unifiedWorkoutRecording = false
        val n = unifiedWorkoutSamples
        val sum = unifiedWorkoutBpmSum
        val minB = unifiedWorkoutMinBpm
        val maxB = unifiedWorkoutMaxBpm
        val samplesCopy = unifiedWorkoutHrSamples.toList()
        unifiedWorkoutSamples = 0
        unifiedWorkoutBpmSum = 0L
        unifiedWorkoutMinBpm = null
        unifiedWorkoutMaxBpm = null
        unifiedWorkoutHrSamples.clear()
        if (n <= 0 || minB == null || maxB == null) return null
        return CardioHrScaffolding(
            avgBpm = (sum / n).toInt(),
            minBpm = minB,
            maxBpm = maxB,
            samples = samplesCopy
        )
    }

    /**
     * Returns avg/min/max for the last live workout and clears the workout buffer.
     * Does not disconnect the sensor.
     */
    fun takeWorkoutHeartRateSummary(): CardioHrScaffolding? {
        liveWorkoutRecording = false
        val n = workoutSamples
        val sum = workoutBpmSum
        val minB = workoutMinBpm
        val maxB = workoutMaxBpm
        val samplesCopy = workoutHrSamples.toList()
        workoutSamples = 0
        workoutBpmSum = 0L
        workoutMinBpm = null
        workoutMaxBpm = null
        workoutHrSamples.clear()
        if (n <= 0 || minB == null || maxB == null) return null
        return CardioHrScaffolding(
            avgBpm = (sum / n).toInt(),
            minBpm = minB,
            maxBpm = maxB,
            samples = samplesCopy
        )
    }

    @SuppressLint("MissingPermission")
    fun startScanForSensors() {
        if (!bleHardwareAvailable) {
            _statusMessage.value = "This device does not support Bluetooth Low Energy heart rate sensors."
            return
        }
        if (!bluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth to scan for a heart rate monitor."
            return
        }
        if (!hasScanPermission()) {
            _statusMessage.value = "Allow Bluetooth (and location on older Android) to scan."
            return
        }
        stopScanInternal()
        disconnectGatt()
        scanSeen.clear()
        _scanRows.value = emptyList()
        _connectionState.value = HeartRateBleConnectionState.Scanning
        _statusMessage.value = null
        val le = scanner ?: run {
            _connectionState.value = HeartRateBleConnectionState.Error
            _statusMessage.value = "Bluetooth scanner unavailable."
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            le.startScan(listOf(filter), settings, scanCallback)
        } catch (t: Throwable) {
            Log.e(TAG, "startScan failed", t)
            _connectionState.value = HeartRateBleConnectionState.Error
            _statusMessage.value = "Could not start Bluetooth scan."
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value == HeartRateBleConnectionState.Scanning) {
                stopScanInternal()
                _connectionState.value = HeartRateBleConnectionState.Idle
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
            } catch (_: Throwable) { /* ignore */ }
        }
        if (_connectionState.value == HeartRateBleConnectionState.Scanning) {
            _connectionState.value = HeartRateBleConnectionState.Idle
        }
    }

    fun connectToScannedRow(row: HeartRateBleScanRow) {
        stopScanInternal()
        viewModelScope.launch {
            rememberHeartRateDevice(row.address, row.name)
            userPreferences.setBleHeartRateDeviceAddress(row.address)
            connectToAddress(row.address, auto = false, label = row.name)
        }
    }

    fun connectToSavedDevice(device: SavedBluetoothDevice) {
        stopScanInternal()
        viewModelScope.launch {
            rememberHeartRateDevice(device.address, device.name)
            userPreferences.setBleHeartRateDeviceAddress(device.address)
            connectToAddress(device.address, auto = false, label = device.name)
        }
    }

    fun forgetSavedDevice(address: String) {
        viewModelScope.launch {
            val normalizedAddress = normalizeBleAddress(address)
            userPreferences.removeSavedBleDevice(normalizedAddress)
            if (_preferredDeviceAddress.value == normalizedAddress) {
                userPreferences.setBleHeartRateDeviceAddress(null)
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
                _statusMessage.value = "Allow Bluetooth connect permission to use the sensor."
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
        _connectionState.value = HeartRateBleConnectionState.Connecting
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
            _connectionState.value = HeartRateBleConnectionState.Error
            _statusMessage.value = "Could not connect to the sensor."
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
        batteryNotifySetupDone = false
        _displayBatteryPercent.value = null
        val g = gatt ?: return
        gatt = null
        try {
            g.disconnect()
        } catch (_: Throwable) { /* ignore */ }
        try {
            g.close()
        } catch (_: Throwable) { /* ignore */ }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val addr = dev.address ?: return
            val name = result.scanRecord?.deviceName ?: dev.name
            scanSeen[addr] = HeartRateBleScanRow(address = addr, name = name)
            _scanRows.value = scanSeen.values.sortedBy { it.name?.lowercase() ?: it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed $errorCode")
            _connectionState.value = HeartRateBleConnectionState.Error
            _statusMessage.value = "Bluetooth scan failed ($errorCode)."
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestBatteryLevelRead(gatt: BluetoothGatt) {
        if (this.gatt !== gatt) return
        val batSvc = gatt.getService(BATTERY_SERVICE_UUID) ?: return
        val batChar = batSvc.getCharacteristic(BATTERY_LEVEL_UUID) ?: return
        val props = batChar.properties
        val canRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
        val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        if (canRead) {
            if (!gatt.readCharacteristic(batChar) && canNotify) {
                subscribeBatteryNotifications(gatt, batChar)
            }
        } else if (canNotify) {
            subscribeBatteryNotifications(gatt, batChar)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeBatteryNotifications(gatt: BluetoothGatt, batChar: BluetoothGattCharacteristic) {
        if (this.gatt !== gatt || batteryNotifySetupDone) return
        val d = batChar.getDescriptor(CCCD_UUID) ?: return
        batteryNotifySetupDone = true
        gatt.setCharacteristicNotification(batChar, true)
        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(d)
    }

    private fun handleBatteryCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?,
        status: Int
    ) {
        if (this.gatt !== gatt) return
        if (characteristic.uuid != BATTERY_LEVEL_UUID) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && !batteryNotifySetupDone) {
                subscribeBatteryNotifications(gatt, characteristic)
            }
            return
        }
        val pct = parseBatteryPercent(value) ?: return
        viewModelScope.launch(Dispatchers.Main) {
            _displayBatteryPercent.value = pct
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && !batteryNotifySetupDone) {
            subscribeBatteryNotifications(gatt, characteristic)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = HeartRateBleConnectionState.Connected
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (this@HeartRateBleViewModel.gatt === gatt) {
                            _connectionState.value = HeartRateBleConnectionState.Idle
                            _displayBpm.value = null
                            _displayBatteryPercent.value = null
                            _connectedLabel.value = null
                            _activeDeviceAddress.value = null
                        }
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Disconnected from heart rate sensor."
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = HeartRateBleConnectionState.Error
                    _statusMessage.value = "Could not read services from the sensor."
                }
                return
            }
            val svc = gatt.getService(HR_SERVICE_UUID)
            val ch = svc?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (ch == null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = HeartRateBleConnectionState.Error
                    _statusMessage.value = "This device does not expose standard heart rate data."
                }
                return
            }
            gatt.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            } else {
                requestBatteryLevelRead(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (this@HeartRateBleViewModel.gatt !== gatt) return
            val ch = descriptor.characteristic ?: return
            if (descriptor.uuid == CCCD_UUID && ch.uuid == HR_MEASUREMENT_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                requestBatteryLevelRead(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            handleBatteryCharacteristicRead(gatt, characteristic, value, status)
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            handleBatteryCharacteristicRead(gatt, characteristic, characteristic.value, status)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            when (characteristic.uuid) {
                HR_MEASUREMENT_UUID ->
                    applyHeartRateSample(parseHeartRateBpm(characteristic.value) ?: return)
                BATTERY_LEVEL_UUID ->
                    applyBatteryPercent(parseBatteryPercent(characteristic.value) ?: return)
                else -> Unit
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            when (characteristic.uuid) {
                HR_MEASUREMENT_UUID ->
                    applyHeartRateSample(parseHeartRateBpm(value) ?: return)
                BATTERY_LEVEL_UUID ->
                    applyBatteryPercent(parseBatteryPercent(value) ?: return)
                else -> Unit
            }
        }

        private fun applyBatteryPercent(percent: Int) {
            viewModelScope.launch(Dispatchers.Main) {
                _displayBatteryPercent.value = percent
            }
        }

        private fun applyHeartRateSample(bpm: Int) {
            viewModelScope.launch(Dispatchers.Main) {
                _displayBpm.value = bpm
                if (liveWorkoutRecording) {
                    workoutSamples++
                    workoutBpmSum += bpm
                    workoutMinBpm = workoutMinBpm?.let { min(it, bpm) } ?: bpm
                    workoutMaxBpm = workoutMaxBpm?.let { max(it, bpm) } ?: bpm
                    workoutHrSamples.add(
                        CardioHrSample(epochSeconds = System.currentTimeMillis() / 1000, bpm = bpm)
                    )
                }
                if (unifiedWorkoutRecording) {
                    unifiedWorkoutSamples++
                    unifiedWorkoutBpmSum += bpm
                    unifiedWorkoutMinBpm = unifiedWorkoutMinBpm?.let { min(it, bpm) } ?: bpm
                    unifiedWorkoutMaxBpm = unifiedWorkoutMaxBpm?.let { max(it, bpm) } ?: bpm
                    unifiedWorkoutHrSamples.add(
                        CardioHrSample(epochSeconds = System.currentTimeMillis() / 1000, bpm = bpm)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopScanInternal()
        disconnectGatt()
        super.onCleared()
    }

    private suspend fun rememberHeartRateDevice(address: String, name: String?) {
        userPreferences.upsertSavedBleDevice(
            SavedBluetoothDevice(
                address = address,
                name = name,
                kind = SavedBluetoothDeviceKind.HEART_RATE_MONITOR,
                lastConnectedEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    private fun clearConnectionUiState() {
        _displayBpm.value = null
        _displayBatteryPercent.value = null
        _connectedLabel.value = null
        _activeDeviceAddress.value = null
        _connectionState.value = HeartRateBleConnectionState.Idle
        _statusMessage.value = null
    }

    private fun normalizeBleAddress(address: String): String = address.trim().uppercase()
}

internal fun parseHeartRateBpm(value: ByteArray?): Int? {
    if (value == null || value.isEmpty()) return null
    val flags = value[0].toInt() and 0xFF
    val uint16 = (flags and 0x01) != 0
    return if (uint16) {
        if (value.size < 3) return null
        ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
    } else {
        if (value.size < 2) return null
        value[1].toInt() and 0xFF
    }
}

internal fun parseBatteryPercent(value: ByteArray?): Int? {
    if (value == null || value.isEmpty()) return null
    val p = value[0].toInt() and 0xFF
    return p.takeIf { it in 0..100 }
}
