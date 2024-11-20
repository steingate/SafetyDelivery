// MainActivity.kt
package com.example.safetydelivery

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.safetydelivery.ui.theme.SafetyDeliveryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafetyDeliveryTheme {
                // 检查权限并请求
                if (!checkBluetoothPermissions()) {
                    requestBluetoothPermissions()
                }

                // 初始状态
                var serviceStatus by remember { mutableStateOf("Inactive") }

                // 监听 BLEService 的状态更新
                LaunchedEffect(Unit) {
                    lifecycleScope.launch {
                        BLEService.serviceStatus.collect { status ->
                            serviceStatus = status
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Service Status: $serviceStatus")

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (checkBluetoothPermissions()) {
                                val serviceIntent = Intent(this@MainActivity, BLEService::class.java)
                                startService(serviceIntent)
                            } else {
                                requestBluetoothPermissions()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Start BLE Service")
                    }
                }
            }
        }
    }

    // 检查权限
    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 请求权限
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // 权限结果回调
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }
}

class BLEService : Service() {

    companion object {
        private const val MAC_ADDRESS = "A4:43:31:57:7C:B5"
        private const val SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
        private const val CHARACTERISTIC_UUID = "12345678-1234-5678-1234-56789abcdef1"

        val serviceStatus = MutableStateFlow("Inactive")
    }

    private lateinit var bluetoothGatt: BluetoothGatt

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkBluetoothPermissions()) {
            Log.i("BLE","Permission Get")
            serviceStatus.value = "Connecting"
            connectToDevice()
        } else {
            Log.i("BLE","Require Permission")
            serviceStatus.value = "Permission Required"
            stopSelf()
        }
        return START_STICKY
    }

    // 检查蓝牙相关权限
    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 与设备连接
    private fun connectToDevice() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(MAC_ADDRESS)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.i("BLE","Ready to Connect")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i("BLE","Connected")
                serviceStatus.value = "Connected"
                if (ActivityCompat.checkSelfPermission(
                        this@BLEService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i("BLE","Disconnected")
                serviceStatus.value = "Disconnected"
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService? = gatt.getService(SERVICE_UUID.toUUID())
            val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID.toUUID())
            characteristic?.let {
                if (ActivityCompat.checkSelfPermission(
                    this@BLEService,
                    Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                Log.i("BLE","Service Discovered")

                gatt.setCharacteristicNotification(characteristic, true)

                // 向特征的描述符 (Descriptor) 中写入通知或指示的值
                for (dp in characteristic.getDescriptors()) {
                    Log.i("BLE",dp.uuid.toString())
                    dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    gatt.writeDescriptor(dp)
                }

            }
        }

        @Deprecated("Use the new API if available")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.i("BLE","Get Characteristic")
            val data = characteristic.getStringValue(0)
            handleBluetoothCommand(data)
        }
    }

    private fun handleBluetoothCommand(command: String) {
        when (command) {
            "tel" -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            "msg" -> {
                val it = Intent()
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                it.setClassName("com.sankuai.meituan.takeoutnew", "com.sankuai.meituan.takeoutnew.ui.page.boot.WelcomeActivity")
                startActivity(it)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothGatt.close()
        super.onDestroy()
    }
}

fun String.toUUID(): UUID {
    return UUID.fromString(this)
}