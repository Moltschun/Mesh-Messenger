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
import android.os.Handler
import android.os.Looper
import android.os.Message
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

    private lateinit var statusTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var devicesListView: ListView

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var devicesAdapter: ArrayAdapter<String>

    private lateinit var chatService: BluetoothChatService
    private var connectedDeviceName: String? = null

    // Handler: мост между сервисом и UI
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothChatService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothChatService.STATE_CONNECTED -> {
                            statusTextView.text = "Подключено к: $connectedDeviceName"
                            devicesAdapter.clear()
                        }
                        BluetoothChatService.STATE_CONNECTING -> statusTextView.text = "Соединение..."
                        BluetoothChatService.STATE_NONE -> statusTextView.text = "Нет соединения"
                    }
                }
                BluetoothChatService.MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    val readMessage = String(readBuf, 0, msg.arg1)
                    Toast.makeText(applicationContext, "ESP32: $readMessage", Toast.LENGTH_SHORT).show()
                }
                BluetoothChatService.MESSAGE_TOAST -> {
                    msg.data.getString("toast")?.let {
                        Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) Toast.makeText(this, "Bluetooth ВКЛ", Toast.LENGTH_SHORT).show()
    }

    private val requestBluetoothPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true && permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        if (granted) {
            enableBluetooth()
            setupChatService()
        } else {
            Toast.makeText(this, "Нет прав Bluetooth", Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.name != null && !discoveredDevices.any { it.address == device.address }) {
                        discoveredDevices.add(device)
                        devicesAdapter.add("${device.name}\n[${device.address}]")
                        devicesAdapter.notifyDataSetChanged()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    statusTextView.text = "Поиск завершен"
                    scanButton.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        devicesListView = findViewById(R.id.devices_list_view)

        devicesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        devicesListView.adapter = devicesAdapter

        scanButton.setOnClickListener {
            if (checkPermissions()) startDiscovery() else requestPermissions()
        }

        devicesListView.setOnItemClickListener { _, _, position, _ ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.cancelDiscovery()
            }
            val device = discoveredDevices[position]
            connectedDeviceName = device.name
            connectDevice(device)
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onStart() {
        super.onStart()
        if (checkPermissions()) setupChatService()
    }

    override fun onResume() {
        super.onResume()
        if (::chatService.isInitialized && chatService.getState() == BluetoothChatService.STATE_NONE) {
            chatService.start()
        }
    }

    private fun setupChatService() {
        if (!::chatService.isInitialized) chatService = BluetoothChatService(handler)
    }

    private fun connectDevice(device: BluetoothDevice) {
        statusTextView.text = "Подключение..."
        chatService.connect(device)
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
        if (bluetoothAdapter?.isEnabled == false) enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter == null) return
        enableBluetooth()
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        discoveredDevices.clear()
        devicesAdapter.clear()
        statusTextView.text = "Поиск целей..."
        scanButton.isEnabled = false
        bluetoothAdapter.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.cancelDiscovery()
        }
        unregisterReceiver(bluetoothReceiver)
        if (::chatService.isInitialized) chatService.stop()
    }
}