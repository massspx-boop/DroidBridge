package com.massspx.droidbridge

import android.content.Context
import android.net.wifi.WifiManager
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec

object TransferEngine {
    const val PORT = 45821
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024 * 1024
    private const val PBKDF2_ROUNDS = 120_000
    @Volatile private var server: ServerSocket? = null
    @Volatile private var activePin: String? = null
    private val random = SecureRandom()

    fun localIp(context: Context): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo.ipAddress
            listOf(ip and 255, ip shr 8 and 255, ip shr 16 and 255, ip shr 24 and 255).joinToString(".")
        } catch (_: Exception) { "Unknown" }
    }

    fun receive(context: Context, onStatus: (String) -> Unit): String {
        if (server?.isClosed == false) return activePin ?: "already-running"
        val pin = (10_000_000 + random.nextInt(90_000_000)).toString()
        activePin = pin
        Thread {
            try {
                val socketServer = ServerSocket(PORT, 8, InetAddress.getByName("0.0.0.0"))
                server = socketServer
                onStatus("Encrypted receiver ready • Port $PORT")
                while (!socketServer.isClosed) {
                    val client = socketServer.accept()
                    Thread { receiveOne(context, client, onStatus) }.start()
                }
            } catch (e: Exception) { onStatus("Receiver stopped: ${e.message}") }
        }.start()
        return pin
    }

    private fun receiveOne(context: Context, socket: Socket, onStatus: (String) -> Unit) {
        socket.use { s ->
            var temp: File? = null
            try {
                s.soTimeout = 120_000
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val salt = ByteArray(16).also { random.nextBytes(it) }
                val challenge = ByteArray(32).also { random.nextBytes(it) }
                output.write(salt)
                output.write(challenge)
                output.flush()
                val proof = ByteArray(32)
                input.readFully(proof)
                val pin = activePin ?: throw SecurityException("Receiver PIN မရှိတော့ပါ")
                val keys = deriveKeys(pin, salt)
                val expectedProof = hmac(keys.second, challenge)
                val authenticated = MessageDigest.isEqual(proof, expectedProof)
                output.writeBoolean(authenticated)
                output.flush()
                if (!authenticated) throw SecurityException("Pairing PIN မမှန်ပါ")

                val name = safeName(input.readUTF())
                val size = input.readLong()
                val expectedHash = input.readUTF()
                if (size < 0 || size > MAX_FILE_BYTES) throw IOException("ဖိုင်အရွယ်အစား ကန့်သတ်ချက်ကျော်နေသည်")
                val iv = ByteArray(16)
                input.readFully(iv)
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.first, "AES"), IvParameterSpec(iv))
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(keys.second, "HmacSHA256"))
                mac.update(iv)

                val dir = File(context.getExternalFilesDir(null), "DroidBridge-Received").apply { mkdirs() }
                temp = File(dir, ".${name}.part")
                val digest = MessageDigest.getInstance("SHA-256")
                var remaining = size
                FileOutputStream(temp).use { fileOut ->
                    val buffer = ByteArray(64 * 1024)
                    while (remaining > 0) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) throw EOFException("Transfer interrupted")
                        mac.update(buffer, 0, n)
                        val plain = cipher.update(buffer, 0, n)
                        if (plain != null && plain.isNotEmpty()) {
                            fileOut.write(plain)
                            digest.update(plain)
                        }
                        remaining -= n
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes.isNotEmpty()) {
                        fileOut.write(finalBytes)
                        digest.update(finalBytes)
                    }
                    fileOut.fd.sync()
                }
                val receivedMac = ByteArray(32)
                input.readFully(receivedMac)
                if (!MessageDigest.isEqual(receivedMac, mac.doFinal())) throw SecurityException("Encrypted transfer authentication failed")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, true)) throw IOException("SHA-256 verification failed")
                val dest = File(dir, name)
                if (dest.exists()) dest.delete()
                if (!temp.renameTo(dest)) throw IOException("ဖိုင်သိမ်းဆည်းမရပါ")
                temp = null
                output.writeUTF("OK:$actualHash")
                output.flush()
                onStatus("Encrypted transfer received and verified: ${dest.name}")
            } catch (e: Exception) {
                temp?.delete()
                try {
                    val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    output.writeUTF("ERROR:${e.message}")
                    output.flush()
                } catch (_: Exception) { }
                onStatus("Receive error: ${e.message}")
            }
        }
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) { }
        server = null
        activePin = null
    }

    fun send(file: File, hostAndPin: String, onStatus: (String) -> Unit) {
        Thread {
            try {
                val parts = hostAndPin.split('|', limit = 2)
                require(parts.size == 2 && parts[0].isNotBlank() && parts[1].matches(Regex("\\d{8}"))) {
                    "IP|PIN ပုံစံဖြင့် ထည့်ပါ (ဥပမာ 192.168.1.12|12345678)"
                }
                require(file.isFile && file.length() <= MAX_FILE_BYTES) { "ဖိုင်မရှိပါ သို့မဟုတ် အရွယ်အစားကန့်သတ်ချက်ကျော်နေသည်" }
                val host = parts[0].trim()
                val pin = parts[1].trim()
                val hash = sha256(file)
                Socket(host, PORT).use { socket ->
                    socket.soTimeout = 120_000
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                    val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val salt = ByteArray(16)
                    val challenge = ByteArray(32)
                    input.readFully(salt)
                    input.readFully(challenge)
                    val keys = deriveKeys(pin, salt)
                    output.write(hmac(keys.second, challenge))
                    output.flush()
                    if (!input.readBoolean()) throw SecurityException("Pairing PIN မမှန်ပါ")

                    output.writeUTF(safeName(file.name))
                    output.writeLong(file.length())
                    output.writeUTF(hash)
                    val iv = ByteArray(16).also { random.nextBytes(it) }
                    output.write(iv)
                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.first, "AES"), IvParameterSpec(iv))
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(keys.second, "HmacSHA256"))
                    mac.update(iv)
                    FileInputStream(file).use { fileIn ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = fileIn.read(buffer)
                            if (n < 0) break
                            val encrypted = cipher.update(buffer, 0, n)
                            if (encrypted != null && encrypted.isNotEmpty()) {
                                output.write(encrypted)
                                mac.update(encrypted)
                            }
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes.isNotEmpty()) {
                        output.write(finalBytes)
                        mac.update(finalBytes)
                    }
                    output.write(mac.doFinal())
                    output.flush()
                    val response = input.readUTF()
                    if (!response.startsWith("OK:$hash")) throw IOException(response.removePrefix("ERROR:"))
                }
                onStatus("Encrypted transfer sent and SHA-256 confirmed: ${file.name}")
            } catch (e: Exception) { onStatus("Send failed: ${e.message}") }
        }.start()
    }

    private fun deriveKeys(pin: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ROUNDS, 512)
        val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Pair(material.copyOfRange(0, 32), material.copyOfRange(32, 64))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
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
