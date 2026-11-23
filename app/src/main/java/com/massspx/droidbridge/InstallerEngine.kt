package com.massspx.droidbridge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

object InstallerEngine {
    fun installApk(context: Context, apk: File, status: (String) -> Unit) {
        if (!apk.isFile || !apk.name.endsWith(".apk", true)) {
            status("APK file မဟုတ်ပါ"); return
        }
        try {
            installSet(context, apk.parentFile ?: context.cacheDir, listOf(apk), null, status)
        } catch (e: Exception) { status("Install error: ${e.message}") }
    }

    fun installBundle(context: Context, zip: File, status: (String) -> Unit) {
        Thread {
            try {
                val root = File(context.cacheDir, "bundle_${System.currentTimeMillis()}").apply { mkdirs() }
                val groups = linkedMapOf<String, MutableList<File>>()
                ZipFile(zip).use { archive ->
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory || !entry.name.endsWith(".apk", true)) continue
                        val parts = entry.name.split('/')
                        if (parts.size < 2) continue
                        val pkg = parts.first()
                        if (!pkg.matches(Regex("[A-Za-z0-9_.]+"))) continue
                        val dest = File(root, pkg + "/" + parts.last())
                        dest.parentFile?.mkdirs()
                        archive.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
                        groups.getOrPut(pkg) { mutableListOf() }.add(dest)
                    }
                }
                if (groups.isEmpty()) throw IllegalArgumentException("ZIP ထဲတွင် package folder အောက်က APK မတွေ့ပါ")
                groups.forEach { (pkg, files) -> installSet(context, root, files, pkg, status) }
            } catch (e: Exception) { status("Bundle install error: ${e.message}") }
        }.start()
    }

    private fun installSet(context: Context, base: File, apks: List<File>, expectedPackage: String?, status: (String) -> Unit) {
        val pm = context.packageManager
        val first = apks.firstOrNull { it.name.equals("base.apk", true) } ?: apks.first()
        @Suppress("DEPRECATION")
        val parsed = pm.getPackageArchiveInfo(first.absolutePath, 0)
            ?: throw IllegalArgumentException("APK metadata ဖတ်မရပါ")
        val packageName = parsed.packageName
        if (expectedPackage != null && expectedPackage != packageName) throw SecurityException("Package folder နှင့် APK package name မကိုက်ညီပါ")
        if (apks.any { !it.isFile || it.length() == 0L }) throw IllegalArgumentException("APK split ပျက်နေသည်")
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val installer = pm.packageInstaller
        val sessionId = installer.createSession(params)
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
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            )
            session.commit(pending.intentSender)
        }
        status("Android installer ကိုဖွင့်နေသည်: $packageName • User confirmation လိုအပ်နိုင်သည်")
    }
}
