package com.massspx.droidbridge

import android.content.Context
import android.net.wifi.WifiManager
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

object TransferEngine {
    const val PORT = 45821
    @Volatile private var server: ServerSocket? = null

    fun localIp(context: Context): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo.ipAddress
            listOf(ip and 255, ip shr 8 and 255, ip shr 16 and 255, ip shr 24 and 255).joinToString(".")
        } catch (_: Exception) { "Unknown" }
    }

    fun receive(context: Context, onStatus: (String) -> Unit) {
        if (server?.isClosed == false) { onStatus("Receiver already running"); return }
        Thread {
            try {
                val socketServer = ServerSocket(PORT, 8, InetAddress.getByName("0.0.0.0"))
                server = socketServer
                onStatus("Receiving mode ON • Port $PORT")
                while (!socketServer.isClosed) {
                    val client = socketServer.accept()
                    Thread {
                        client.use { s ->
                            try {
                                s.soTimeout = 120_000
                                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                                val name = safeName(input.readUTF())
                                val size = input.readLong()
                                val expectedHash = input.readUTF()
                                if (size < 0 || size > 8L * 1024 * 1024 * 1024) throw IOException("Invalid file size")
                                val dir = File(context.getExternalFilesDir(null), "DroidBridge-Received").apply { mkdirs() }
                                val temp = File(dir, ".$name.part")
                                val digest = MessageDigest.getInstance("SHA-256")
                                var remaining = size
                                FileOutputStream(temp).use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (remaining > 0) {
                                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                        if (n < 0) throw EOFException("Transfer interrupted")
                                        digest.update(buffer, 0, n)
                                        out.write(buffer, 0, n)
                                        remaining -= n
                                    }
                                    out.fd.sync()
                                }
                                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                                if (!actual.equals(expectedHash, true)) {
                                    temp.delete()
                                    throw IOException("SHA-256 verification failed")
                                }
                                val dest = File(dir, name)
                                if (dest.exists()) dest.delete()
                                if (!temp.renameTo(dest)) throw IOException("Could not save received file")
                                onStatus("Received and verified: ${dest.name}")
                            } catch (e: Exception) { onStatus("Receive error: ${e.message}") }
                        }
                    }.start()
                }
            } catch (e: Exception) { onStatus("Receiver stopped: ${e.message}") }
        }.start()
    }

    fun stop() { try { server?.close() } catch (_: Exception) {}; server = null }

    fun send(file: File, host: String, onStatus: (String) -> Unit) {
        Thread {
            try {
                val hash = sha256(file)
                Socket(host, PORT).use { socket ->
                    socket.soTimeout = 120_000
                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    out.writeUTF(safeName(file.name))
                    out.writeLong(file.length())
                    out.writeUTF(hash)
                    FileInputStream(file).use { it.copyTo(out, 64 * 1024) }
                    out.flush()
                }
                onStatus("Sent and verified by receiver hash: ${file.name}")
            } catch (e: Exception) { onStatus("Send failed: ${e.message}") }
        }.start()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeName(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return clean.take(120).ifBlank { "received_file.bin" }
    }
}
