package com.example.meshmessenger

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Сервис для управления Bluetooth-соединениями.
 * Реализует клиентскую часть архитектуры: подключение к удаленному устройству (ESP32)
 * и двусторонний обмен данными через RFCOMM сокет.
 */
class BluetoothChatService(private val handler: Handler) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var state: Int = STATE_NONE

    // Тег для логирования (Logcat), чтобы фильтровать сообщения проекта
    private val TAG = "MeshBTService"

    // Потоки управления соединением
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    // Стандартный UUID для профиля SPP (Serial Port Profile).
    // Необходим для совместимости с модулями HC-05, HC-06 и библиотекой BluetoothSerial на ESP32.
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        // Константы текущего состояния соединения
        const val STATE_NONE = 0       // Нет активных действий
        const val STATE_CONNECTING = 1 // Процесс установки соединения
        const val STATE_CONNECTED = 2  // Соединение установлено

        // Типы сообщений для передачи в UI через Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_TOAST = 4
    }

    /**
     * Устанавливает текущее состояние соединения и сообщает об этом UI.
     * @param newState новое состояние (STATE_NONE, STATE_CONNECTING, STATE_CONNECTED)
     */
    @Synchronized
    private fun setState(newState: Int) {
        state = newState
        // Отправляем сообщение в главный поток для обновления интерфейса
        handler.obtainMessage(MESSAGE_STATE_CHANGE, newState, -1).sendToTarget()
        Log.d(TAG, "Смена состояния: $state -> $newState")
    }

    /**
     * Возвращает текущее состояние соединения.
     */
    @Synchronized
    fun getState(): Int {
        return state
    }

    /**
     * Запускает сервис (сбрасывает предыдущие соединения).
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "Запуск сервиса (start)")

        // Отменяем любые потоки, пытающиеся установить соединение
        connectThread?.cancel()
        connectThread = null

        // Отменяем любые потоки, удерживающие соединение
        connectedThread?.cancel()
        connectedThread = null

        setState(STATE_NONE)
    }

    /**
     * Инициирует процесс подключения к удаленному устройству.
     * @param device BluetoothDevice, к которому нужно подключиться.
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "Попытка подключения к: ${device.address}")

        // Если уже идет процесс подключения, отменяем его
        if (state == STATE_CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }

        // Если есть активное соединение, разрываем его
        connectedThread?.cancel()
        connectedThread = null

        // Запускаем поток для установки нового соединения
        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    /**
     * Метод вызывается внутренним потоком ConnectThread при успешном подключении.
     */
    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "Соединение установлено (connected)")

        // Поток подключения выполнил свою задачу, сбрасываем его
        connectThread = null

        // На всякий случай сбрасываем поток обмена данными
        connectedThread?.cancel()
        connectedThread = null

        // Запускаем поток для управления входящими/исходящими данными
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        setState(STATE_CONNECTED)
    }

    /**
     * Полная остановка всех потоков соединения.
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "Остановка сервиса (stop)")
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        setState(STATE_NONE)
    }

    /**
     * Неблокирующий метод для отправки данных.
     * @param out Массив байт для отправки.
     */
    fun write(out: ByteArray) {
        // Создаем временную ссылку на объект потока, чтобы избежать коллизий
        val r: ConnectedThread?
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread
        }
        // Выполняем запись асинхронно
        r?.write(out)
    }

    // -------------------------------------------------------------------------
    // Внутренний класс: Поток подключения (ConnectThread)
    // Отвечает за создание RFCOMM сокета и вызов connect().
    // -------------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            // Всегда отключаем поиск устройств перед соединением (экономит ресурсы)
            bluetoothAdapter?.cancelDiscovery()

            try {
                // Блокирующий вызов: ждем успешного подключения или исключения
                mmSocket?.connect()
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка подключения сокета", e)
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "Не удалось закрыть сокет после сбоя", e2)
                }
                connectionFailed()
                return
            }

            // Обнуляем ссылку на поток, так как задача выполнена
            synchronized(this@BluetoothChatService) {
                connectThread = null
            }

            // Запускаем процесс обмена данными
            mmSocket?.let { connected(it, device) }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка при закрытии сокета подключения", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Внутренний класс: Поток обмена данными (ConnectedThread)
    // Отвечает за чтение (InputStream) и запись (OutputStream).
    // -------------------------------------------------------------------------
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream

        override fun run() {
            Log.i(TAG, "Запущен поток чтения данных")
            val buffer = ByteArray(1024) // Буфер для входящих данных
            var bytes: Int

            // Слушаем входящие данные, пока состояние CONNECTED
            while (this@BluetoothChatService.getState() == STATE_CONNECTED) {
                try {
                    // Читаем из потока (блокирующий вызов)
                    bytes = mmInStream.read(buffer)

                    // Отправляем полученные байты в UI поток
                    handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "Разрыв соединения при чтении", e)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Запись данных в исходящий поток.
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream.write(buffer)
                // Опционально: сообщаем UI, что сообщение отправлено
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка при записи данных", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка при закрытии сокета данных", e)
            }
        }
    }

    // Обработка ошибки при попытке подключения
    private fun connectionFailed() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle().apply { putString("toast", "Не удалось подключиться к устройству") }
        msg.data = bundle
        handler.sendMessage(msg)
        setState(STATE_NONE)
    }

    // Обработка разрыва соединения
    private fun connectionLost() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle().apply { putString("toast", "Связь с устройством потеряна") }
        msg.data = bundle
        handler.sendMessage(msg)
        setState(STATE_NONE)
    }
}