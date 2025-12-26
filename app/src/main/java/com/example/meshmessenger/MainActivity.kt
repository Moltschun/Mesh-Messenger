package com.example.meshmessenger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var statusTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var devicesListView: ListView

    // Bluetooth переменные
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var devicesAdapter: ArrayAdapter<String>

    // Лаунчер для включения Bluetooth (вместо устаревшего startActivityForResult)
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth активирован", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth необходим для работы", Toast.LENGTH_SHORT).show()
        }
    }

    // Лаунчер для запроса разрешений
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (granted) {
            enableBluetooth()
        } else {
            Toast.makeText(this, "Нет доступа к модулям связи. Миссия под угрозой.", Toast.LENGTH_LONG).show()
        }
    }

    // Приемник сигналов обнаружения
    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    // Фильтруем устройства без имени, чтобы не засорять радар
                    if (device != null && device.name != null && !discoveredDevices.any { it.address == device.address }) {
                        discoveredDevices.add(device)
                        devicesAdapter.add("${device.name}\n[${device.address}]")
                        devicesAdapter.notifyDataSetChanged()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    statusTextView.text = "Сканирование завершено. Целей: ${discoveredDevices.size}"
                    scanButton.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация UI
        statusTextView = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        devicesListView = findViewById(R.id.devices_list_view)

        // Адаптер
        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        devicesListView.adapter = devicesAdapter

        // Кнопка сканирования
        scanButton.setOnClickListener {
            if (checkPermissions()) {
                startDiscovery()
            } else {
                requestPermissions()
            }
        }

        // Клик по устройству (заготовка для подключения)
        devicesListView.setOnItemClickListener { _, _, position, _ ->
            val device = discoveredDevices[position]
            Toast.makeText(this, "Выбрана цель: ${device.name}", Toast.LENGTH_SHORT).show()
            // Сюда мы добавим логику подключения в следующем коммите
            // connectToDevice(device)
        }

        // Регистрация ресивера
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(bluetoothReceiver, filter)

        // Первая проверка прав при запуске
        if (!checkPermissions()) {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            val connect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            return scan == PackageManager.PERMISSION_GRANTED && connect == PackageManager.PERMISSION_GRANTED
        } else {
            val loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            return loc == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestBluetoothPermissions.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter == null) return

        enableBluetooth() // Убедимся, что BT включен

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        discoveredDevices.clear()
        devicesAdapter.clear()
        statusTextView.text = "Сканирование эфира..."
        scanButton.isEnabled = false

        bluetoothAdapter.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отключаем радары
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
        unregisterReceiver(bluetoothReceiver)
    }
}