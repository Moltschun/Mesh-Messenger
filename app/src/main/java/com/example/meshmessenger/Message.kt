package com.example.meshmessenger

data class Message(
    val text: String,
    val isSentByMe: Boolean, // true = справа (мое), false = слева (чужое)
    val timestamp: Long = System.currentTimeMillis()
)