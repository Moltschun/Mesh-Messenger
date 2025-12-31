package com.example.meshmessenger

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothChatService(private val handler: Handler) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var state: Int = STATE_NONE

    companion object {
        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3

        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_TOAST = 5

        private const val NAME = "MeshMessenger"
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "BluetoothChat"
    }

    @Synchronized
    fun getState(): Int {
        return state
    }

    @Synchronized
    private fun setState(state: Int) {
        this.state = state
        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
    }

    @Synchronized
    fun start() {
        cancelConnectThread()
        cancelConnectedThread()

        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread?.start()
        }
        setState(STATE_LISTEN)
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (state == STATE_CONNECTING) {
            cancelConnectThread()
        }
        cancelConnectedThread()

        connectThread = ConnectThread(device)
        connectThread?.start()
        setState(STATE_CONNECTING)
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        cancelConnectThread()
        cancelConnectedThread()
        cancelAcceptThread()

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        setState(STATE_CONNECTED)
    }

    @Synchronized
    fun stop() {
        cancelConnectThread()
        cancelConnectedThread()
        cancelAcceptThread()
        setState(STATE_NONE)
    }

    fun write(out: ByteArray) {
        val r: ConnectedThread?
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = connectedThread
        }
        r?.write(out)
    }

    private fun connectionFailed() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "Не удалось подключиться")
        msg.data = bundle
        handler.sendMessage(msg)

        setState(STATE_NONE)
        start()
    }

    private fun connectionLost() {
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "Соединение разорвано")
        msg.data = bundle
        handler.sendMessage(msg)

        setState(STATE_NONE)
        start()
    }

    private fun cancelConnectThread() {
        connectThread?.cancel()
        connectThread = null
    }

    private fun cancelConnectedThread() {
        connectedThread?.cancel()
        connectedThread = null
    }

    private fun cancelAcceptThread() {
        acceptThread?.cancel()
        acceptThread = null
    }

    // 1. Поток ожидания (Сервер)
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy {
            try {
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "Socket listen() failed", e)
                null
            }
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                socket?.also {
                    synchronized(this@BluetoothChatService) {
                        // ИСПРАВЛЕНИЕ: Используем this@BluetoothChatService.state
                        when (this@BluetoothChatService.state) {
                            STATE_LISTEN, STATE_CONNECTING -> connected(it, it.remoteDevice)
                            STATE_NONE, STATE_CONNECTED -> {
                                try { it.close() } catch (e: IOException) {}
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try { mmServerSocket?.close() } catch (e: IOException) {}
        }
    }

    // 2. Поток подключения (Клиент)
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                null
            }
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {}
                connectionFailed()
                return
            }

            synchronized(this@BluetoothChatService) {
                connectThread = null
            }

            mmSocket?.let { connected(it, device) }
        }

        fun cancel() {
            try { mmSocket?.close() } catch (e: IOException) {}
        }
    }

    // 3. Поток обмена данными
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            // ИСПРАВЛЕНИЕ: Используем this@BluetoothChatService.state
            while (this@BluetoothChatService.state == STATE_CONNECTED) {
                try {
                    val bytes = mmInStream.read(mmBuffer)
                    handler.obtainMessage(MESSAGE_READ, bytes, -1, mmBuffer).sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error writing", e)
            }
        }

        fun cancel() {
            try { mmSocket?.close() } catch (e: IOException) {}
        }
    }
}