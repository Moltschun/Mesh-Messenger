package com.example.meshmessenger

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageStatus {
    SENDING, SENT, ERROR, RECEIVED
}

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val senderName: String,
    val isSentByMe: Boolean,
    val timestamp: String,
    var status: MessageStatus
)