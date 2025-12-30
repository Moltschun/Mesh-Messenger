package com.example.meshmessenger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Перечисляем все таблицы (у нас одна - Message) и версию базы
@Database(entities = [Message::class], version = 1, exportSchema = false)
@TypeConverters(StatusConverter::class) // Подключаем конвертер для Enum статусов
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Стандартный код для создания базы (Singleton)
        // Гарантирует, что база данных открыта в единственном экземпляре
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mesh_messenger_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}