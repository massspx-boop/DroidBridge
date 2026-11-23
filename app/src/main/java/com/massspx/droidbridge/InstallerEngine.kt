package com.massspx.droidbridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object InstallerEngine {
    private const val MAX_BUNDLE_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_BUNDLE_ENTRIES = 256

    fun installApk(context: Context, apk: File, status: (String) -> Unit) {
        if (!apk.isFile || !apk.name.endsWith(".apk", true)) {
            status("APK file မဟုတ်ပါ")
            return
        }
        try {
            installSet(context, listOf(apk), null, status)
        } catch (e: Exception) { status("Install error: ${e.message}") }
    }

    fun installBundle(context: Context, zip: File, status: (String) -> Unit) {
        Thread {
            try {
                require(zip.isFile && zip.length() > 0) { "ZIP ဖိုင်မရှိပါ" }
                val root = File(context.cacheDir, "bundle_${System.currentTimeMillis()}").apply { mkdirs() }
                val groups = linkedMapOf<String, MutableList<File>>()
                var totalBytes = 0L
                var entryCount = 0
                ZipFile(zip).use { archive ->
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        entryCount++
                        require(entryCount <= MAX_BUNDLE_ENTRIES) { "ZIP entry အရေအတွက်များလွန်းနေသည်" }
                        val normalized = entry.name.replace('\\', '/')
                        require(!normalized.startsWith("/") && normalized.split('/').none { it == ".." || it == "." }) {
                            "ZIP path traversal ကိုပိတ်ထားသည်"
                        }
                        if (!normalized.endsWith(".apk", true)) continue
                        val parts = normalized.split('/')
                        require(parts.size == 2) { "APK များသည် package folder တစ်ခုအောက်တွင် ရှိရမည်" }
                        val pkg = parts[0]
                        require(pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) && !pkg.contains("..")) {
                            "Package folder name မမှန်ပါ"
                        }
                        val fileName = parts[1]
                        require(fileName.matches(Regex("[A-Za-z0-9_.-]+"))) { "APK filename မမှန်ပါ" }
                        if (entry.size > 0) {
                            totalBytes += entry.size
                            require(totalBytes <= MAX_BUNDLE_BYTES) { "APK bundle အရွယ်အစား 2GB ကျော်နေသည်" }
                        }
                        val dest = File(root, "$pkg/$fileName")
                        dest.parentFile?.mkdirs()
                        archive.getInputStream(entry).use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        groups.getOrPut(pkg) { mutableListOf() }.add(dest)
                    }
                }
                if (groups.isEmpty()) throw IllegalArgumentException("ZIP ထဲတွင် package folder အောက်က APK မတွေ့ပါ")
                groups.forEach { (pkg, files) -> installSet(context, files, pkg, status) }
            } catch (e: Exception) { status("Bundle install error: ${e.message}") }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun installSet(context: Context, apks: List<File>, expectedPackage: String?, status: (String) -> Unit) {
        require(apks.isNotEmpty()) { "APK set ဗလာဖြစ်နေသည်" }
        require(apks.all { it.isFile && it.length() > 0 }) { "APK split ပျက်နေသည် သို့မဟုတ် အလွတ်ဖြစ်နေသည်" }
        val base = apks.firstOrNull { it.name.equals("base.apk", true) } ?: apks.first()
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val parsed = apks.map { file ->
            pm.getPackageArchiveInfo(file.absolutePath, flags)
                ?: throw IllegalArgumentException("APK metadata ဖတ်မရပါ: ${file.name}")
        }
        val packageName = parsed.first().packageName
        if (expectedPackage != null && expectedPackage != packageName) {
            throw SecurityException("ZIP folder နှင့် APK package name မကိုက်ညီပါ")
        }
        if (parsed.any { it.packageName != packageName }) throw SecurityException("APK set ထဲတွင် package name မတူသောဖိုင် ပါဝင်နေသည်")
        val signerSets = parsed.map { info ->
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
            } else {
                info.signatures?.map { it.toByteArray() } ?: emptyList()
            }
            signatures.map { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02X".format(b) } }.toSet()
        }
        val baseIndex = apks.indexOf(base)
        val baseSigners = signerSets[baseIndex]
        if (baseSigners.isEmpty() || signerSets.any { it != baseSigners }) {
            throw SecurityException("APK split များ၏ signing certificate မတူညီပါ")
        }
        if (apks.size > 1 && apks.none { it.name.equals("base.apk", true) }) {
            throw IllegalArgumentException("Split APK set တွင် base.apk မပါဝင်ပါ")
        }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apks.sumOf { it.length() })
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val installer = pm.packageInstaller
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apks.forEachIndexed { index, file ->
                    val entryName = if (file.name.equals("base.apk", true)) "base.apk" else "split_${index}.apk"
                    session.openWrite(entryName, 0, file.length()).use { out ->
                        FileInputStream(file).use { it.copyTo(out) }
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                    action = "com.massspx.droidbridge.INSTALL_STATUS"
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }
                val flagsPending = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flagsPending)
                session.commit(pending.intentSender)
            }
            status("Android installer ကိုဖွင့်နေသည်: $packageName • User confirmation လိုအပ်နိုင်သည်")
        } catch (e: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) { }
            throw e
        }
    }
}
