package com.example.meshmessenger

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Инициализация UI
        recyclerView = findViewById(R.id.recycler_chat)
        editMessage = findViewById(R.id.edit_message)
        btnSend = findViewById(R.id.btn_send)

        // Настройка списка
        adapter = MessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Тестовое сообщение (проверка интерфейса)
        adapter.addMessage(Message("Системы связи активны. Ожидание ввода.", false))

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                // Пока просто добавляем в список (имитация отправки)
                adapter.addMessage(Message(text, true))
                editMessage.text.clear()
                // Прокрутка вниз
                recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}