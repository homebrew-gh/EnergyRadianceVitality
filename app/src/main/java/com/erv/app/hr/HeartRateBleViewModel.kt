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
import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

private val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
private val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
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

    /** Live BPM from the sensor (UI); updated whenever connected. */
    private val _displayBpm = MutableStateFlow<Int?>(null)
    val displayBpm: StateFlow<Int?> = _displayBpm.asStateFlow()

    private val _connectedLabel = MutableStateFlow<String?>(null)
    val connectedLabel: StateFlow<String?> = _connectedLabel.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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

    val bleHardwareAvailable: Boolean
        get() = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun bluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    init {
        viewModelScope.launch {
            val saved = userPreferences.bleHeartRateDeviceAddress.first()
            if (!saved.isNullOrBlank() && bleHardwareAvailable && bluetoothEnabled()) {
                if (hasConnectPermission()) {
                    connectToAddress(saved, auto = true)
                }
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
            userPreferences.setBleHeartRateDeviceAddress(row.address)
            connectToAddress(row.address, auto = false, label = row.name)
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
            _connectionState.value = HeartRateBleConnectionState.Error
            _statusMessage.value = "Could not connect to the sensor."
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectUser() {
        viewModelScope.launch {
            userPreferences.setBleHeartRateDeviceAddress(null)
        }
        stopScanInternal()
        disconnectGatt()
        _displayBpm.value = null
        _connectedLabel.value = null
        _connectionState.value = HeartRateBleConnectionState.Idle
        _statusMessage.value = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
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
                    _connectionState.value = HeartRateBleConnectionState.Error
                    _statusMessage.value = "Could not read services from the sensor."
                }
                return
            }
            val svc = gatt.getService(HR_SERVICE_UUID)
            val ch = svc?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (ch == null) {
                viewModelScope.launch(Dispatchers.Main) {
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
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid != HR_MEASUREMENT_UUID) return
            applyHeartRateSample(parseHeartRateBpm(characteristic.value) ?: return)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid != HR_MEASUREMENT_UUID) return
            applyHeartRateSample(parseHeartRateBpm(value) ?: return)
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
            }
        }
    }

    override fun onCleared() {
        stopScanInternal()
        disconnectGatt()
        super.onCleared()
    }
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
