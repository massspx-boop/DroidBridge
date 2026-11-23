package com.massspx.droidbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var hostInput: EditText
    private val selected = linkedSetOf<String>()
    private val apps = mutableListOf<PackageInfo>()
    private val pickSend = 41
    private val pickInstall = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadApps()
        handleIncoming(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 22, 18, 12)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "DroidBridge"
            textSize = 28f
            setTextColor(0xFF17243A.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "SMART APP • APK • FILE TRANSFER"
            textSize = 12f
            setTextColor(0xFF52627A.toInt())
        })
        status = TextView(this).apply {
            text = "Ready"
            setPadding(0, 12, 0, 10)
            setTextColor(0xFF26364D.toInt())
        }
        root.addView(status)

        val networkTitle = TextView(this).apply {
            text = "ဖုန်းချင်း Wi-Fi Transfer"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF17243A.toInt())
        }
        root.addView(networkTitle)
        root.addView(TextView(this).apply {
            text = "လက်ခံမည့်ဖုန်းတွင် Start Receiver နှိပ်ပါ။ ပို့မည့်ဖုန်းတွင် IP ထည့်ပြီး Send File ကိုနှိပ်ပါ။ ဖုန်းနှစ်လုံး Wi-Fi တစ်ခုတည်းတွင် ရှိရပါမည်။"
            textSize = 12f
            setTextColor(0xFF52627A.toInt())
        })
        hostInput = EditText(this).apply {
            hint = "လက်ခံမည့်ဖုန်း IP|PIN ဥပမာ 192.168.1.12|12345678"
            setSingleLine(true)
            textSize = 14f
        }
        root.addView(hostInput)
        root.addView(actionButton("Start Receiver • ဒီဖုန်း IP: ${TransferEngine.localIp(this)}") {
            val pin = TransferEngine.receive(this) { msg -> runOnUiThread { status.text = msg } }
            status.text = "RECEIVER READY\nIP: ${TransferEngine.localIp(this)}\nPAIR PIN: $pin\nPort: ${TransferEngine.PORT}"
        })
        root.addView(actionButton("Stop Receiver") { TransferEngine.stop(); status.text = "Receiver stopped" })
        root.addView(actionButton("Send File") { pickSendFile() })
        root.addView(actionButton("Choose APK / ZIP to install") { pickInstallFile() })
        root.addView(actionButton("Enable APK installation permission") { requestInstallPermission() })
        root.addView(actionButton("Hybrid AI Diagnosis • API Settings") { showAiSettings() })

        root.addView(TextView(this).apply {
            text = "Installed apps • APK Export"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF17243A.toInt())
            setPadding(0, 16, 0, 4)
        })
        val appActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        appActions.addView(actionButton("Refresh") { loadApps() }, LinearLayout.LayoutParams(0, -2, 1f))
        appActions.addView(actionButton("Export selected") { exportSelected() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(appActions)
        root.addView(actionButton("Share latest APK bundle") { shareExports() })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        list.removeAllViews()
        apps.clear()
        selected.clear()
        val pm = packageManager
        val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        apps.addAll(installed.filter { it.applicationInfo != null && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it.applicationInfo!!).toString().lowercase() })
        for (info in apps) {
            val appInfo = info.applicationInfo ?: continue
            list.addView(CheckBox(this).apply {
                text = "${pm.getApplicationLabel(appInfo)}\n${info.packageName}"
                textSize = 13f
                setTextColor(0xFF1E293B.toInt())
                setPadding(4, 6, 4, 6)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(info.packageName) else selected.remove(info.packageName)
                }
            })
        }
        status.text = "Installed apps found: ${apps.size} • Selected: ${selected.size}"
    }

    private fun exportSelected() {
        if (selected.isEmpty()) { toast("အနည်းဆုံး App တစ်ခုရွေးပါ"); return }
        try {
            val outDir = File(getExternalFilesDir(null), "DroidBridge-Exports").apply { mkdirs() }
            val output = File(outDir, "DroidBridge_APKs_${System.currentTimeMillis()}.zip")
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                for (pkg in selected) {
                    val info = packageManager.getPackageInfo(pkg, 0)
                    val ai = info.applicationInfo ?: continue
                    val paths = mutableListOf(ai.sourceDir)
                    ai.splitSourceDirs?.let { paths.addAll(it) }
                    paths.distinct().forEachIndexed { index, path ->
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val name = if (index == 0) "base.apk" else "split_${index}.apk"
                            zip.putNextEntry(ZipEntry("$pkg/$name"))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            status.text = "APK bundle ready • ${output.length() / 1024} KB • SHA-256 ${TransferEngine.sha256(output).take(16)}…"
            toast("APK bundle ထုတ်ယူပြီးပါပြီ")
        } catch (e: Exception) {
            status.text = "Export failed: ${e.message}"
            toast("APK ထုတ်ယူမရပါ")
        }
    }

    private fun shareExports() {
        val dir = File(getExternalFilesDir(null), "DroidBridge-Exports")
        val file = dir.listFiles()?.maxByOrNull { it.lastModified() }
        if (file == null) { toast("အရင် APK Export လုပ်ပါ"); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share APK bundle"))
    }

    private fun pickSendFile() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(i, pickSend)
    }

    private fun pickInstallFile() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/zip", "application/x-zip-compressed"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, pickInstall)
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } else toast("Install permission ရရှိပြီးပါပြီ")
    }

    @Deprecated("Legacy picker callback supports Android 8+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            pickSend -> sendUri(uri)
            pickInstall -> installUri(uri)
        }
    }

    private fun sendUri(uri: Uri) {
        try {
            val name = queryName(uri) ?: "shared_file"
            val file = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("ဖိုင်ဖတ်မရပါ")
            val host = hostInput.text.toString().trim()
            if (host.isBlank()) { toast("လက်ခံမည့်ဖုန်း IP ထည့်ပါ"); return }
            TransferEngine.send(file, host) { msg -> runOnUiThread { status.text = msg } }
            status.text = "Sending: $name"
        } catch (e: Exception) { status.text = "File error: ${e.message}" }
    }

    private fun installUri(uri: Uri) {
        try {
            val name = queryName(uri) ?: "selected_file"
            val file = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("ဖိုင်ဖတ်မရပါ")
            if (name.endsWith(".zip", true)) {
                InstallerEngine.installBundle(this, file) { msg -> runOnUiThread { status.text = msg } }
            } else if (name.endsWith(".apk", true)) {
                val report = ApkCompatibility.inspect(this, file)
                status.text = "Package: ${report.packageName}\nVersion: ${report.versionName}\nminSdk: ${report.minSdk} • targetSdk: ${report.targetSdk}\nCompatible: ${report.compatible}\n${report.notes.joinToString("; ")}"
                if (report.compatible) InstallerEngine.installApk(this, file) { msg -> runOnUiThread { status.append("\n$msg") } }
            } else toast("APK သို့မဟုတ် APK bundle ZIP ကိုရွေးပါ")
        } catch (e: Exception) {
            val message = e.message ?: "unknown"
            status.text = "Install error: $message\nRunning diagnosis…"
            AiDiagnosisEngine.diagnose(this, message) { result ->
                runOnUiThread { status.text = "Install error: $message\n$result" }
            }
        }
    }

    private fun showAiSettings() {
        val input = EditText(this).apply {
            hint = "OpenRouter API key (sk-or-...)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Hybrid AI Diagnosis")
            .setMessage("Optional. API key ကို Android Keystore ဖြင့် encrypt လုပ်ပြီး ဒီဖုန်းထဲမှာသာ သိမ်းမည်။ Key မထည့်လျှင် Offline diagnosis ကို သုံးမည်။ API အသုံးပြုခမှာ သင့် provider account မှ ကျသင့်နိုင်သည်။")
            .setView(input)
            .setPositiveButton("Save key") { _, _ ->
                try {
                    AiDiagnosisEngine.saveApiKey(this, input.text.toString().trim())
                    toast("Encrypted API key saved")
                } catch (e: Exception) { toast("Key error: ${e.message}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) status.text = "Shared file received via Android Sharesheet. Use Install APK / ZIP to process it."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep receiver alive until the user explicitly stops it or Android closes the process.
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
