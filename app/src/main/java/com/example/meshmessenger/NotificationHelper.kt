package com.example.meshmessenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "mesh_channel_id"
    private const val CHANNEL_NAME = "Mesh Messages"
    private const val NOTIFICATION_ID = 1001

    // 1. Создание канала (Обязательно для Android 8.0+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Уведомления о входящих сообщениях через LoRa/Bluetooth"
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 2. Показ уведомления
    fun showNotification(context: Context, title: String, content: String) {
        // Проверка разрешений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return // Нет прав — нет уведомления
            }
        }

        // Куда перекинуть пилота при нажатии (открываем ChatActivity)
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // Сборка уведомления
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Можешь заменить на свою иконку
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Убрать после нажатия

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}