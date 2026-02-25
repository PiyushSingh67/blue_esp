package com.example.blue_esp.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import com.example.blue_esp.bluetooth.BluetoothService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    Disconnected,
    Scanning,
    Connected,
    Failed
}

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _permissionsGranted = MutableStateFlow(true)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private var bluetoothService: BluetoothService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bluetoothService = (service as BluetoothService.LocalBinder).getService()
            bluetoothService?.initialize()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        val currentDevices = _discoveredDevices.value.toMutableList()
                        if (!currentDevices.any { it.address == device.address }) {
                            currentDevices.add(device)
                            _discoveredDevices.value = currentDevices
                        }
                    }
                }
                BluetoothService.ACTION_GATT_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected
                }
                BluetoothService.ACTION_GATT_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    init {
        val serviceIntent = Intent(application, BluetoothService::class.java)
        application.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        val intentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothService.ACTION_GATT_CONNECTED)
            addAction(BluetoothService.ACTION_GATT_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(bluetoothReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(bluetoothReceiver, intentFilter)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _permissionsGranted.value = granted
    }

    fun startDiscovery() {
        if (permissionsGranted.value) {
            _discoveredDevices.value = emptyList()
            _connectionState.value = ConnectionState.Scanning
            bluetoothService?.startDiscovery()
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        bluetoothService?.connect(device)
    }

    fun retryConnection() {
        startDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(serviceConnection)
        getApplication<Application>().unregisterReceiver(bluetoothReceiver)
    }
}
