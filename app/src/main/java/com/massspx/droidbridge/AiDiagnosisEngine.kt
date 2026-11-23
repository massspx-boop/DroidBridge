package com.massspx.droidbridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AiDiagnosisEngine {
    private const val alias = "droidbridge_ai_key_v1"
    private const val prefs = "secure_ai_settings"

    fun saveApiKey(context: Context, apiKey: String) {
        require(apiKey.startsWith("sk-or-") || apiKey.startsWith("sk-")) { "API key ပုံစံမမှန်ပါ" }
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, payload, cipher.iv.size, encrypted.size)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .putString("encrypted_api_key", Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun hasApiKey(context: Context): Boolean =
        !context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("encrypted_api_key", null).isNullOrBlank()

    fun diagnose(context: Context, error: String, callback: (String) -> Unit) {
        val local = DiagnosisEngine.explain(error)
        val key = try { readApiKey(context) } catch (_: Exception) { null }
        if (key.isNullOrBlank()) {
            callback("Offline diagnosis:\n$local\n\nAI model သုံးရန် OpenRouter API key ကို Settings ထဲတွင် ထည့်နိုင်သည်။")
            return
        }
        Thread {
            var result = local
            try {
                val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", "https://github.com/massspx-boop/DroidBridge")
                    setRequestProperty("X-Title", "DroidBridge")
                }
                val body = JSONObject().apply {
                    put("model", "openai/gpt-4o-mini")
                    put("temperature", 0.2)
                    put("max_tokens", 350)
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You diagnose Android APK installation errors. Be concise, Burmese language, distinguish confirmed facts from possibilities. Never recommend bypassing Android security, signatures, licensing, or malware checks.")
                    }).put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Explain this Android install error and safe next steps: $error")
                    }))
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("AI service HTTP $code")
                val json = JSONObject(response)
                result = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                connection.disconnect()
            } catch (e: Exception) {
                result = "$local\n\nAI service မရရှိပါ: ${e.message}"
            }
            callback("Hybrid diagnosis:\n$result")
        }.start()
    }

    private fun readApiKey(context: Context): String? {
        val encoded = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("encrypted_api_key", null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12) { "Encrypted API key data ပျက်နေသည်" }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
