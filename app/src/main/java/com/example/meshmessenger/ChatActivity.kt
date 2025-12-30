package com.example.meshmessenger

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    // Секретный код подтверждения
    private val PROTOCOL_ACK = "#ACK"

    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var chatService: BluetoothChatService
    private var deviceAddress: String? = null

    // База данных
    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    // Последнее отправленное сообщение (чтобы обновить его статус при ACK)
    private var lastSentMessage: com.example.meshmessenger.Message? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                BluetoothChatService.MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    val readMessage = String(readBuf, 0, msg.arg1).trim()

                    if (readMessage == PROTOCOL_ACK) {
                        // ПРИШЛО ПОДТВЕРЖДЕНИЕ: Обновляем статус в БАЗЕ
                        lastSentMessage?.let {
                            it.status = MessageStatus.SENT
                            lifecycleScope.launch { messageDao.update(it) }
                        }
                    } else {
                        // ВХОДЯЩЕЕ СООБЩЕНИЕ ОТ ДРУГОГО ПИЛОТА
                        val incomingMsg = com.example.meshmessenger.Message(
                            text = readMessage,
                            isSentByMe = false,
                            timestamp = getCurrentTime(),
                            status = MessageStatus.SENT
                        )
                        lifecycleScope.launch { messageDao.insert(incomingMsg) }
                    }
                }
                BluetoothChatService.MESSAGE_WRITE -> {
                    // Сообщение ушло в буфер Bluetooth.
                    // Ждем ACK, поэтому статус пока не меняем.
                }
                BluetoothChatService.MESSAGE_STATE_CHANGE -> {
                    when (msg.arg1) {
                        BluetoothChatService.STATE_CONNECTED -> {
                            supportActionBar?.subtitle = "Подключено: $deviceAddress"
                        }
                        BluetoothChatService.STATE_CONNECTING -> {
                            supportActionBar?.subtitle = "Соединение..."
                        }
                        BluetoothChatService.STATE_NONE -> {
                            supportActionBar?.subtitle = "Нет соединения"
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Инициализация БД
        database = AppDatabase.getDatabase(this)
        messageDao = database.messageDao()

        // Получаем адрес цели. Если его нет - это сбой.
        deviceAddress = intent.getStringExtra("device_address")
        if (deviceAddress == null) {
            Toast.makeText(this, "Ошибка: Адрес цели не получен", Toast.LENGTH_LONG).show()
            finish() // Закрываем экран, возвращаемся к поиску
            return
        }

        recyclerView = findViewById(R.id.recycler_chat)
        editMessage = findViewById(R.id.edit_message)
        btnSend = findViewById(R.id.btn_send)

        adapter = MessageAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        // Подписка на обновления базы данных
        lifecycleScope.launch {
            messageDao.getAllMessages().collect { messages ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        recyclerView.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        chatService = BluetoothChatService(handler)

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            sendMessage(text)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                lifecycleScope.launch {
                    messageDao.deleteAll()
                    Toast.makeText(applicationContext, "История удалена", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (chatService.getState() == BluetoothChatService.STATE_NONE) {
            chatService.start()
        }
        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED && deviceAddress != null) {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
            chatService.connect(device)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatService.stop()
    }

    private fun sendMessage(text: String) {
        if (text.isEmpty()) return

        val currentTime = getCurrentTime()

        // Создаем сообщение (По умолчанию статус SENDING...)
        val newMessage = com.example.meshmessenger.Message(
            text = text,
            isSentByMe = true,
            timestamp = currentTime,
            status = MessageStatus.SENDING
        )

        // Сразу сохраняем в базу (оно появится на экране)
        lifecycleScope.launch {
            messageDao.insert(newMessage)
            lastSentMessage = newMessage

            // Пытаемся отправить
            if (chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
                // Если связи нет - сразу ставим статус ERROR
                newMessage.status = MessageStatus.ERROR
                messageDao.update(newMessage)
                Toast.makeText(applicationContext, "Нет соединения!", Toast.LENGTH_SHORT).show()
            } else {
                // Если связь есть - отправляем байты
                try {
                    val bytes = text.toByteArray()
                    chatService.write(bytes)
                } catch (e: Exception) {
                    newMessage.status = MessageStatus.ERROR
                    messageDao.update(newMessage)
                }
            }
        }

        editMessage.text.clear()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}