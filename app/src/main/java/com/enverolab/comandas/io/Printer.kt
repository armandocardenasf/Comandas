package com.enverolab.comandas.io

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Printer(private val context: Context) {
    private val preferences = context.getSharedPreferences("printer", Context.MODE_PRIVATE)
    private var socket: BluetoothSocket? = null

    fun devices(): List<Pair<String, String>> {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "Conceda permiso Bluetooth"
        }
        val adapter =
            context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: error("Bluetooth no disponible")
        check(adapter.isEnabled) { "Active Bluetooth y empareje la impresora en Ajustes" }
        return adapter.bondedDevices.map { (it.name ?: "Impresora") to it.address }
    }

    @Synchronized
    fun print(text: String, address: String? = null) {
        devices()
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        )
            throw SecurityException("Permiso Bluetooth denegado")
        val mac = address ?: preferences.getString("address", null) ?: error("Seleccione impresora")
        socket?.close()
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val connection =
            adapter
                .getRemoteDevice(mac)
                .createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
        socket = connection
        try {
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    executor.submit {
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ) != PackageManager.PERMISSION_GRANTED
                        )
                            throw SecurityException("Permiso Bluetooth revocado")
                        connection.connect()
                    }
                pending.get(15, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
            preferences.edit { putString("address", mac) }
            val out = connection.outputStream
            out.write(byteArrayOf(0x1b, 0x40, 0x1b, 0x4d, 0x00, 0x1b, 0x74, 0x02))
            val path =
                com.enverolab.comandas.data.PosDatabase(context).use { db ->
                    db.rows("SELECT logo FROM Empresa LIMIT 1")
                        .firstOrNull()
                        ?.get("logo")
                        ?.toString()
                }
            path
                ?.let { logoImage(it) }
                ?.let { image ->
                    val maxWidth = if (preferences.getInt("width", 32) == 32) 384 else 576
                    val width = minOf(image.width, maxWidth)
                    val height =
                        (image.height.toLong() * width / image.width).toInt().coerceIn(1, 512)
                    val bitmap =
                        android.graphics.Bitmap.createScaledBitmap(image, width, height, true)
                    val stride = (width + 7) / 8
                    out.write(byteArrayOf(27, 97, 1))
                    for (top in 0 until height step 24) {
                        val count = minOf(24, height - top)
                        out.write(
                            byteArrayOf(
                                29,
                                118,
                                48,
                                0,
                                stride.toByte(),
                                (stride shr 8).toByte(),
                                count.toByte(),
                                0,
                            )
                        )
                        val bytes = ByteArray(stride * count)
                        for (y in 0 until count) for (x in 0 until width) {
                            val pixel = bitmap.getPixel(x, y + top)
                            val alpha = android.graphics.Color.alpha(pixel)
                            val lum =
                                (299 * android.graphics.Color.red(pixel) +
                                    587 * android.graphics.Color.green(pixel) +
                                    114 * android.graphics.Color.blue(pixel)) / 1000
                            if (alpha > 128 && lum < 160) {
                                val i = y * stride + x / 8
                                bytes[i] = (bytes[i].toInt() or (128 shr (x % 8))).toByte()
                            }
                        }
                        out.write(bytes)
                    }
                    out.write(byteArrayOf(10, 27, 97, 0))
                }
            val width = preferences.getInt("width", 32)
            val wrapped = com.enverolab.comandas.domain.Ticket.render(text, width) + "\n\n\n"
            out.write(wrapped.toByteArray(Charset.forName("IBM850")))
            out.flush()
            if (preferences.getBoolean("cut", false)) {
                out.write(byteArrayOf(0x1d, 0x56, 0x00))
                out.flush()
            }
        } finally {
            connection.close()
            socket = null
        }
    }

    fun columns(): Int = preferences.getInt("width", 32)

    fun setOptions(width: Int, cut: Boolean) {
        require(width in listOf(32, 42, 48))
        preferences.edit {
            putInt("width", width)
            putBoolean("cut", cut)
        }
    }
}
