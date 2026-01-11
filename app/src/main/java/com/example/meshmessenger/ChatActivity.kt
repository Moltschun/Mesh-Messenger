package com.example.meshmessenger

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnClear: ImageButton // Кнопка очистки
    private lateinit var headerTitle: TextView

    private lateinit var chatService: BluetoothChatService
    private var deviceAddress: String? = null
    private var deviceName: String = "Unknown Device" // Имя собеседника

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothChatService.MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // Преобразуем байты в строку и убираем пробелы по краям
                    val readMessage = String(readBuf, 0, msg.arg1).trim()

                    // --- ЛОГИКА "СВОЙ-ЧУЖОЙ" (Обновленная) ---
                    // Проверяем точную фразу, которую присылает твоя ESP32
                    if (readMessage.equals("ESP32 получила", ignoreCase = true)) {

                        // ЭТО ПОДТВЕРЖДЕНИЕ ДОСТАВКИ (ACK)
                        lifecycleScope.launch {
                            // Находим последнее сообщение со статусом SENDING и ставим SENT (Галочка)
                            messageDao.markLastAsSent()
                        }

                    } else {
                        // ЭТО ОБЫЧНОЕ СООБЩЕНИЕ ОТ СОЮЗНИКА
                        val incomingMsg = com.example.meshmessenger.Message(
                            text = readMessage,
                            senderName = deviceName,
                            isSentByMe = false,
                            timestamp = getCurrentTime(),
                            status = MessageStatus.RECEIVED
                        )
                        lifecycleScope.launch { messageDao.insert(incomingMsg) }

                        // Вызов уведомления (чтобы ты увидел сообщение в шторке)
                        NotificationHelper.showNotification(
                            applicationContext,
                            "Новое сообщение",
                            "$deviceName: $readMessage"
                        )
                    }
                }
                BluetoothChatService.MESSAGE_TOAST -> {
                    val text = msg.data.getString("toast")
                    if (text != null) Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        database = AppDatabase.getDatabase(this)
        messageDao = database.messageDao()

        // Получаем адрес и имя устройства
        deviceAddress = intent.getStringExtra("device_address")
        if (deviceAddress != null) {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
            deviceName = device.name ?: deviceAddress ?: "Unknown"
        }

        recyclerView = findViewById(R.id.recycler_chat)
        editMessage = findViewById(R.id.edit_message)
        btnSend = findViewById(R.id.btn_send)
        btnClear = findViewById(R.id.btn_clear_chat)
        headerTitle = findViewById(R.id.header_title)

        headerTitle.text = deviceName // Ставим имя в заголовок

        adapter = MessageAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            messageDao.getAllMessages().collect { messages ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        chatService = BluetoothChatService.getInstance(handler)

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            sendMessage(text)
        }

        // Логика кнопки очистки
        btnClear.setOnClickListener {
            showClearDialog()
        }
    }

    private fun showClearDialog() {
        AlertDialog.Builder(this)
            .setTitle("Очистить чат?")
            .setMessage("Все сообщения будут удалены безвозвратно.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    messageDao.deleteAll()
                    Toast.makeText(this@ChatActivity, "Чат очищен", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        chatService.updateHandler(handler)
        if (chatService.getState() == BluetoothChatService.STATE_NONE) chatService.start()

        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED && deviceAddress != null) {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
            chatService.connect(device)
        }
    }

    private fun sendMessage(text: String) {
        if (text.isEmpty()) return

        val newMessage = com.example.meshmessenger.Message(
            text = text,
            senderName = "Вы",
            isSentByMe = true,
            timestamp = getCurrentTime(),
            status = MessageStatus.SENDING
        )

        lifecycleScope.launch {
            messageDao.insert(newMessage)
            try {
                // Добавляем перенос строки \n, так как твой код ESP32 ждет readStringUntil('\n')
                chatService.write((text + "\n").toByteArray())

            } catch (e: Exception) {
                newMessage.status = MessageStatus.ERROR
                messageDao.update(newMessage)
                Toast.makeText(applicationContext, "Ошибка отправки", Toast.LENGTH_SHORT).show()
            }
        }
        editMessage.text.clear()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}