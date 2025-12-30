package com.example.meshmessenger

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

// Статусы остаются без изменений
enum class MessageStatus {
    SENDING, SENT, ERROR
}

// @Entity говорит, что это таблица в базе данных с именем "messages"
@Entity(tableName = "messages")
data class Message(
    // @PrimaryKey - уникальный номер сообщения.
    // autoGenerate = true значит база сама будет считать: 1, 2, 3...
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val text: String,
    val isSentByMe: Boolean,
    val timestamp: String,
    var status: MessageStatus
)

// КОНВЕРТЕР: Учит базу данных сохранять Enum как строку и наоборот
class StatusConverter {
    @TypeConverter
    fun fromStatus(status: MessageStatus): String {
        return status.name // Сохраняем "SENT" как текст
    }

    @TypeConverter
    fun toStatus(data: String): MessageStatus {
        return MessageStatus.valueOf(data) // Превращаем текст "SENT" обратно в Enum
    }
}