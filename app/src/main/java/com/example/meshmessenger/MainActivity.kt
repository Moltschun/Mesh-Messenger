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
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var statusTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var devicesListView: ListView

    // Адаптер Bluetooth и список устройств
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var devicesAdapter: ArrayAdapter<String>

    // Сервис Bluetooth
    private lateinit var chatService: BluetoothChatService

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: android.os.Message) {
            when (msg.what) {
                BluetoothChatService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothChatService.STATE_CONNECTED -> {
                            statusTextView.text = "Подключено"
                            devicesAdapter.clear()
                        }
                        BluetoothChatService.STATE_CONNECTING -> statusTextView.text = "Соединение..."
                        BluetoothChatService.STATE_LISTEN, BluetoothChatService.STATE_NONE -> statusTextView.text = "Готов к поиску"
                    }
                }
                BluetoothChatService.MESSAGE_READ -> {
                    val readBuff = msg.obj as ByteArray
                    val readMessage = String(readBuff, 0, msg.arg1)
                    Toast.makeText(applicationContext, "Получено: $readMessage", Toast.LENGTH_LONG).show()
                }
                BluetoothChatService.MESSAGE_WRITE -> { }
                BluetoothChatService.MESSAGE_TOAST -> {
                    msg.data.getString("toast")?.let {
                        Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Проверяем, дали ли ВСЕ запрошенные права
        val granted = permissions.entries.all { it.value }
        if (granted) {
            checkSystemAndStartDiscovery()
        } else {
            Toast.makeText(this, "Не хватает прав для поиска!", Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        // Логируем каждое найденное устройство
                        Log.d("Scan", "Found: ${device.name} - ${device.address}")

                        if (!discoveredDevices.any { it.address == device.address }) {
                            discoveredDevices.add(device)
                            val name = device.name ?: "Unknown"
                            devicesAdapter.add("$name\n${device.address}")
                            devicesAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    statusTextView.text = "Поиск завершен"
                    scanButton.isEnabled = true
                    if (discoveredDevices.isEmpty()) {
                        Toast.makeText(context, "Ничего нового не найдено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        devicesListView = findViewById(R.id.devices_list_view)

        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        devicesListView.adapter = devicesAdapter

        devicesListView.setOnItemClickListener { _, _, position, _ ->
            if (checkPermissionsGranted()) {
                val deviceToConnect = discoveredDevices[position]
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter.cancelDiscovery()
                }
                connectDevice(deviceToConnect)
            } else {
                checkAndRequestPermissions()
            }
        }

        scanButton.setOnClickListener {
            if (checkPermissionsGranted()) {
                checkSystemAndStartDiscovery()
            } else {
                checkAndRequestPermissions()
            }
        }

        chatService = BluetoothChatService.getInstance(handler)

        // Сразу проверяем права при запуске
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissionsGranted()) {
            // ВАЖНО: Когда возвращаемся на главный экран, забираем управление себе
            chatService.updateHandler(handler)

            if (chatService.getState() == BluetoothChatService.STATE_NONE) {
                chatService.start()
            }
        }
    }

    // --- ИСПРАВЛЕННАЯ ПРОВЕРКА ПРАВ ---
    private fun checkPermissionsGranted(): Boolean {
        // 1. Сначала проверяем Геолокацию (нужна ВСЕМ версиям Android, если не используется спец. флаг)
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // 2. Для новых Android (12+) проверяем еще и Bluetooth права
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return locationGranted && scanGranted && connectGranted
        }

        // Для старых Android достаточно геолокации
        return locationGranted
    }

    private fun checkAndRequestPermissions() {
        if (!checkPermissionsGranted()) {
            val permissions = mutableListOf<String>()

            // Обязательно добавляем Location для всех
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            requestBluetoothPermissions.launch(permissions.toTypedArray())
        } else {
            // Если права уже есть, просто обновляем статус, но не запускаем скан автоматически, чтобы не спамить
            statusTextView.text = "Готов к поиску"
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkSystemAndStartDiscovery() {
        if (bluetoothAdapter == null) return

        // 1. Проверяем Bluetooth
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Включите Bluetooth!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }

        // 2. Проверяем GPS
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            Toast.makeText(this, "Включите GPS!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        // 3. Запускаем сканер
        try { unregisterReceiver(bluetoothReceiver) } catch (e: Exception) {}

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(bluetoothReceiver, filter)

        startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }

        discoveredDevices.clear()
        devicesAdapter.clear()

        // Показываем сохраненные
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            discoveredDevices.add(device)
            val name = device.name ?: "Unknown"
            devicesAdapter.add("$name (Сохранено)\n${device.address}")
        }

        statusTextView.text = "Идет поиск..."
        scanButton.isEnabled = false

        val started = bluetoothAdapter?.startDiscovery()
        if (started != true) {
            Toast.makeText(this, "Ошибка запуска сканера", Toast.LENGTH_SHORT).show()
            scanButton.isEnabled = true
        }
    }

    private fun connectDevice(device: BluetoothDevice) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("device_address", device.address)
        startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        try { unregisterReceiver(bluetoothReceiver) } catch (e: Exception) {}
        chatService.stop()
    }
}