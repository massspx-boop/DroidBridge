package com.massspx.droidbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val selected = linkedSetOf<String>()
    private val apps = mutableListOf<PackageInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "DroidBridge"
            textSize = 27f
            setTextColor(0xFF17243A.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Smart APK & File Transfer"
            textSize = 14f
            setTextColor(0xFF52627A.toInt())
        })
        status = TextView(this).apply {
            text = "Ready • Android ${android.os.Build.VERSION.RELEASE}"
            setPadding(0, 14, 0, 14)
            setTextColor(0xFF334155.toInt())
        }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Share file") { pickFile() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(actionButton("Refresh apps") { loadApps() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        root.addView(actionButton("Export selected APKs") { exportSelected() })
        root.addView(actionButton("Send exported APKs") { shareExports() })
        root.addView(TextView(this).apply {
            text = "Installed apps (select one or more)"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF17243A.toInt())
            setPadding(0, 18, 0, 8)
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadApps()
        handleIncoming(intent)
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
            val row = CheckBox(this).apply {
                text = "${pm.getApplicationLabel(appInfo)}\n${info.packageName}"
                textSize = 14f
                setTextColor(0xFF1E293B.toInt())
                setPadding(4, 8, 4, 8)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(info.packageName) else selected.remove(info.packageName)
                }
            }
            list.addView(row)
        }
        status.text = "Found ${apps.size} apps • Select apps to export"
    }

    private fun exportSelected() {
        if (selected.isEmpty()) { toast("Select at least one app"); return }
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
                            val prefix = if (index == 0) "base" else "split_${index}"
                            zip.putNextEntry(ZipEntry("$pkg/$prefix.apk"))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            status.text = "Export complete: ${output.length() / 1024} KB"
            toast("APK bundle saved")
        } catch (e: Exception) {
            status.text = "Export failed: ${e.message}"
            toast("Could not export APKs")
        }
    }

    private fun shareExports() {
        val dir = File(getExternalFilesDir(null), "DroidBridge-Exports")
        val file = dir.listFiles()?.maxByOrNull { it.lastModified() }
        if (file == null) { toast("Export APKs first"); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send APK bundle"))
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(i, 41)
    }

    @Deprecated("Legacy callback retained for Android 8 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Share with"))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) status.text = "Incoming shared file detected"
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
