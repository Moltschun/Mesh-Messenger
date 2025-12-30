package com.example.meshmessenger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Получить все сообщения
    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<Message>>

    // Сохранить сообщение
    @Insert
    suspend fun insert(message: Message)

    @Update
    suspend fun update(message: Message)

    // Очистить чат
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}