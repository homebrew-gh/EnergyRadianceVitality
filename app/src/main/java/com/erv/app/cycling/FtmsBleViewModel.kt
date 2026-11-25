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
import java.util.concurrent.ConcurrentHashMap

enum class FtmsBleConnectionState { Idle, Scanning, Connecting, Connected, Error }

data class FtmsScanRow(val address: String, val name: String?)

data class FtmsWorkoutSummary(
    val machineType: FtmsMachineType?,
    val distanceMeters: Double?,
    val avgPowerWatts: Int?,
    val maxPowerWatts: Int?,
    val avgCadenceRpm: Int?,
    val maxCadenceRpm: Int?,
)

/**
 * Live data from a Bluetooth smart trainer or treadmill on the standard Fitness Machine Service
 * (Wahoo, Tacx, Zwift Hub, Elite, most newer treadmills): speed, cadence, power, incline and
 * distance. Read only; the app never sets resistance or speed on the machine.
 */
class FtmsBleViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "FtmsBle"
        private const val SCAN_TIMEOUT_MS = 25_000L
    }

    private val userPreferences = UserPreferences(application)
    private val bluetoothAdapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    @Volatile
    private var gatt: BluetoothGatt? = null
    private val scanSeen = ConcurrentHashMap<String, FtmsScanRow>()
    private var scanJob: Job? = null

    private val _connectionState = MutableStateFlow(FtmsBleConnectionState.Idle)
    val connectionState: StateFlow<FtmsBleConnectionState> = _connectionState.asStateFlow()

    private val _scanRows = MutableStateFlow<List<FtmsScanRow>>(emptyList())
    val scanRows: StateFlow<List<FtmsScanRow>> = _scanRows.asStateFlow()

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

    private val _machineType = MutableStateFlow<FtmsMachineType?>(null)
    val machineType: StateFlow<FtmsMachineType?> = _machineType.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow<Double?>(null)
    val currentSpeedKmh: StateFlow<Double?> = _currentSpeedKmh.asStateFlow()

    private val _currentCadenceRpm = MutableStateFlow<Int?>(null)
    val currentCadenceRpm: StateFlow<Int?> = _currentCadenceRpm.asStateFlow()

    private val _currentPowerWatts = MutableStateFlow<Int?>(null)
    val currentPowerWatts: StateFlow<Int?> = _currentPowerWatts.asStateFlow()

    private val _currentInclinePercent = MutableStateFlow<Double?>(null)
    val currentInclinePercent: StateFlow<Double?> = _currentInclinePercent.asStateFlow()

    private val _workoutDistanceMeters = MutableStateFlow<Double?>(null)
    val workoutDistanceMeters: StateFlow<Double?> = _workoutDistanceMeters.asStateFlow()

    private val lock = Any()
    private val distance = FtmsDistanceTracker()
    private var liveWorkoutRecording = false
    private var recordingPaused = false
    private var powerSum = 0L
    private var powerCount = 0
    private var maxPower = 0
    private var cadenceSum = 0L
    private var cadenceCount = 0
    private var maxCadence = 0

    val bleHardwareAvailable: Boolean
        get() = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun bluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    init {
        viewModelScope.launch {
            userPreferences.savedBleDevices.collect { devices ->
                _savedDevices.value = devices.filter { it.kind == SavedBluetoothDeviceKind.FTMS_MACHINE }
            }
        }
        viewModelScope.launch {
            userPreferences.bleFtmsDeviceAddress.collect { _preferredDeviceAddress.value = it }
        }
        viewModelScope.launch {
            val saved = userPreferences.bleFtmsDeviceAddress.first()
            if (!saved.isNullOrBlank() && bleHardwareAvailable && bluetoothEnabled() && hasConnectPermission()) {
                connectToAddress(saved, auto = true)
            }
        }
    }

    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** One reconnect try to the preferred machine, without scanning. */
    fun tryPreferredDeviceReconnectOnce() {
        val saved = _preferredDeviceAddress.value
        if (saved.isNullOrBlank()) return
        if (!bleHardwareAvailable || !bluetoothEnabled() || !hasConnectPermission()) return
        if (_connectionState.value != FtmsBleConnectionState.Idle && _connectionState.value != FtmsBleConnectionState.Error) return
        connectToAddress(saved, auto = true)
    }

    fun resetWorkoutRecordingOnLiveStart() {
        synchronized(lock) {
            liveWorkoutRecording = true
            recordingPaused = false
            clearTotals()
        }
        _workoutDistanceMeters.value = 0.0
    }

    /** A paused session step: readings keep the baseline moving but add no distance or samples. */
    fun setRecordingPaused(paused: Boolean) {
        synchronized(lock) { recordingPaused = paused }
    }

    fun discardWorkoutRecording() {
        synchronized(lock) {
            liveWorkoutRecording = false
            clearTotals()
        }
        _workoutDistanceMeters.value = null
    }

    fun takeWorkoutSummary(): FtmsWorkoutSummary? {
        val summary = synchronized(lock) {
            liveWorkoutRecording = false
            val result = FtmsWorkoutSummary(
                machineType = _machineType.value,
                distanceMeters = distance.meters.takeIf { it > 0.0 },
                avgPowerWatts = if (powerCount > 0) (powerSum / powerCount).toInt().takeIf { it > 0 } else null,
                maxPowerWatts = maxPower.takeIf { it > 0 },
                avgCadenceRpm = if (cadenceCount > 0) (cadenceSum / cadenceCount).toInt().takeIf { it > 0 } else null,
                maxCadenceRpm = maxCadence.takeIf { it > 0 },
            )
            clearTotals()
            result
        }
        _workoutDistanceMeters.value = null
        val empty = summary.distanceMeters == null && summary.avgPowerWatts == null && summary.avgCadenceRpm == null
        return if (empty) null else summary
    }

    private fun clearTotals() {
        distance.reset()
        powerSum = 0
        powerCount = 0
        maxPower = 0
        cadenceSum = 0
        cadenceCount = 0
        maxCadence = 0
    }

    @SuppressLint("MissingPermission")
    fun startScanForMachines() {
        if (!bleHardwareAvailable) {
            _statusMessage.value = "This device does not support Bluetooth Low Energy."
            return
        }
        if (!bluetoothEnabled()) {
            _statusMessage.value = "Turn on Bluetooth to scan for a trainer or treadmill."
            return
        }
        if (!hasScanPermission()) {
            _statusMessage.value = "Allow Bluetooth (and location on older Android) to scan."
            return
        }
        stopScanInternal()
        scanSeen.clear()
        _scanRows.value = emptyList()
        _connectionState.value = FtmsBleConnectionState.Scanning
        _statusMessage.value = null
        val le = scanner ?: run {
            _connectionState.value = FtmsBleConnectionState.Error
            _statusMessage.value = "Bluetooth scanner unavailable."
            return
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            le.startScan(null, settings, scanCallback)
        } catch (t: Throwable) {
            Log.e(TAG, "startScan failed", t)
            _connectionState.value = FtmsBleConnectionState.Error
            _statusMessage.value = "Could not start Bluetooth scan."
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value == FtmsBleConnectionState.Scanning) {
                stopScanInternal()
                _connectionState.value = FtmsBleConnectionState.Idle
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
        if (_connectionState.value == FtmsBleConnectionState.Scanning) {
            _connectionState.value = FtmsBleConnectionState.Idle
        }
    }

    fun connectToScannedRow(row: FtmsScanRow) {
        stopScanInternal()
        viewModelScope.launch {
            rememberMachine(row.address, row.name)
            userPreferences.setBleFtmsDeviceAddress(row.address)
            connectToAddress(row.address, auto = false, label = row.name)
        }
    }

    fun connectToSavedDevice(device: SavedBluetoothDevice) {
        stopScanInternal()
        viewModelScope.launch {
            rememberMachine(device.address, device.name)
            userPreferences.setBleFtmsDeviceAddress(device.address)
            connectToAddress(device.address, auto = false, label = device.name)
        }
    }

    fun forgetSavedDevice(address: String) {
        viewModelScope.launch {
            val normalized = address.trim().uppercase()
            userPreferences.removeSavedBleDevice(normalized)
            if (_preferredDeviceAddress.value == normalized) userPreferences.setBleFtmsDeviceAddress(null)
            if (_activeDeviceAddress.value == normalized) disconnectUser()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String, auto: Boolean = false, label: String? = null) {
        if (!bleHardwareAvailable || !bluetoothEnabled()) return
        if (!hasConnectPermission()) {
            if (!auto) _statusMessage.value = "Allow Bluetooth connect permission to use the trainer."
            return
        }
        stopScanInternal()
        disconnectGatt()
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address) ?: return
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad BLE address", e)
            return
        }
        _activeDeviceAddress.value = device.address.trim().uppercase()
        _connectionState.value = FtmsBleConnectionState.Connecting
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
            _connectionState.value = FtmsBleConnectionState.Error
            _statusMessage.value = "Could not connect to the trainer."
        }
    }

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
            val advertised = result.scanRecord?.serviceUuids.orEmpty()
            if (advertised.none { it.uuid == FtmsUuids.SERVICE }) return
            @SuppressLint("MissingPermission")
            val name = result.scanRecord?.deviceName ?: device.name
            scanSeen[address] = FtmsScanRow(address = address, name = name)
            _scanRows.value = scanSeen.values.sortedBy { it.name?.lowercase() ?: it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed $errorCode")
            _connectionState.value = FtmsBleConnectionState.Error
            _statusMessage.value = "Bluetooth scan failed ($errorCode)."
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        _connectionState.value = FtmsBleConnectionState.Connected
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (this@FtmsBleViewModel.gatt === gatt) clearConnectionUiState()
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Disconnected from the trainer."
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(FtmsUuids.SERVICE) else null
            // A trainer that also lists a treadmill characteristic is still a bike.
            val bike = service?.getCharacteristic(FtmsUuids.INDOOR_BIKE_DATA)
            val treadmill = service?.getCharacteristic(FtmsUuids.TREADMILL_DATA)
            val data = bike ?: treadmill
            if (data == null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _activeDeviceAddress.value = null
                    _connectionState.value = FtmsBleConnectionState.Error
                    _statusMessage.value = "This machine does not send bike or treadmill data over FTMS."
                }
                return
            }
            _machineType.value = if (bike != null) FtmsMachineType.INDOOR_BIKE else FtmsMachineType.TREADMILL
            gatt.setCharacteristicNotification(data, true)
            val cccd = data.getDescriptor(FtmsUuids.CCCD)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }
            viewModelScope.launch(Dispatchers.Main) {
                _statusMessage.value = if (cccd != null) {
                    null
                } else {
                    "Connected, but the machine did not offer live updates."
                }
            }
        }

        @Deprecated("Android 12 and older")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            applyData(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            applyData(characteristic.uuid, value)
        }
    }

    private fun applyData(uuid: java.util.UUID, value: ByteArray?) {
        val data = value ?: return
        val reading = when (uuid) {
            FtmsUuids.INDOOR_BIKE_DATA -> FtmsParser.indoorBike(data)
            FtmsUuids.TREADMILL_DATA -> FtmsParser.treadmill(data)
            else -> null
        } ?: return
        reading.speedKmh?.let { _currentSpeedKmh.value = it }
        reading.cadenceRpm?.let { _currentCadenceRpm.value = it }
        reading.powerWatts?.let { _currentPowerWatts.value = it }
        reading.inclinePercent?.let { _currentInclinePercent.value = it }
        synchronized(lock) {
            if (!liveWorkoutRecording) return
            distance.add(reading, System.currentTimeMillis(), paused = recordingPaused)
            if (!recordingPaused) {
                reading.powerWatts?.takeIf { it > 0 }?.let {
                    powerSum += it
                    powerCount++
                    if (it > maxPower) maxPower = it
                }
                reading.cadenceRpm?.takeIf { it > 0 }?.let {
                    cadenceSum += it
                    cadenceCount++
                    if (it > maxCadence) maxCadence = it
                }
            }
            _workoutDistanceMeters.value = distance.meters
        }
    }

    private suspend fun rememberMachine(address: String, name: String?) {
        userPreferences.upsertSavedBleDevice(
            SavedBluetoothDevice(
                address = address,
                name = name,
                kind = SavedBluetoothDeviceKind.FTMS_MACHINE,
                lastConnectedEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun clearConnectionUiState() {
        _currentSpeedKmh.value = null
        _currentCadenceRpm.value = null
        _currentPowerWatts.value = null
        _currentInclinePercent.value = null
        _machineType.value = null
        _connectedLabel.value = null
        _activeDeviceAddress.value = null
        _connectionState.value = FtmsBleConnectionState.Idle
        _statusMessage.value = null
        // The workout's distance and power so far stay: a dropped connection mid-ride keeps them.
    }

    override fun onCleared() {
        stopScanInternal()
        disconnectGatt()
        super.onCleared()
    }
}
