package com.example.blue_esp.bluetooth

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.blue_esp.MainActivity
import com.example.blue_esp.R
import com.example.blue_esp.server.EspDataRepository
import java.util.*

class BluetoothService : Service() {

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "BluetoothService"
        private const val CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_GATT_CONNECTED = "com.example.blue_esp.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.blue_esp.ACTION_GATT_DISCONNECTED"
        const val ACTION_SERVICES_DISCOVERED = "com.example.blue_esp.ACTION_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.blue_esp.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.blue_esp.EXTRA_DATA"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ESP32 Bridge Active")
            .setContentText("Maintaining connection and web server...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun initialize(): Boolean {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter != null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    EspDataRepository.updateConnectionStatus("Connected to ${gatt.device.address}")
                    broadcastUpdate(ACTION_GATT_CONNECTED)
                    if (ActivityCompat.checkSelfPermission(this@BluetoothService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    EspDataRepository.updateConnectionStatus("Disconnected")
                    broadcastUpdate(ACTION_GATT_DISCONNECTED)
                    close()
                }
            } else {
                EspDataRepository.updateConnectionStatus("Error: $status")
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_SERVICES_DISCOVERED)
                enableNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleData(characteristic)
        }
        
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleData(characteristic)
        }
    }

    private fun handleData(characteristic: BluetoothGattCharacteristic) {
        val data = characteristic.value
        if (data != null && data.isNotEmpty()) {
            val stringData = String(data)
            EspDataRepository.updateData(stringData)
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = broadcastDeviceFound(result.device)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { broadcastDeviceFound(it.device) }
    }

    fun startDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        stopDiscovery()
        bluetoothAdapter?.startDiscovery()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
        handler.postDelayed({ stopDiscovery() }, 10000)
    }

    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
    }

    fun connect(device: BluetoothDevice): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        close()
        EspDataRepository.updateConnectionStatus("Connecting...")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        return true
    }

    fun close() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun broadcastDeviceFound(device: BluetoothDevice) {
        val intent = Intent(BluetoothDevice.ACTION_FOUND).apply { putExtra(BluetoothDevice.EXTRA_DEVICE, device) }
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String) = sendBroadcast(Intent(action))

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)
        val data = characteristic.value
        if (data != null && data.isNotEmpty()) intent.putExtra(EXTRA_DATA, String(data))
        sendBroadcast(intent)
    }
}
