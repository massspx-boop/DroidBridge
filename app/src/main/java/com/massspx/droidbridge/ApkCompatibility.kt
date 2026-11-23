package com.massspx.droidbridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ApkReport(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val supportedAbis: List<String>,
    val signerSha256: List<String>,
    val compatible: Boolean,
    val notes: List<String>
)

object ApkCompatibility {
    @Suppress("DEPRECATION")
    fun inspect(context: Context, apk: File): ApkReport {
        require(apk.isFile && apk.length() > 0) { "APK ဖိုင်မရှိပါ သို့မဟုတ် အလွတ်ဖြစ်နေသည်" }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalArgumentException("APK ပျက်နေခြင်း သို့မဟုတ် ဖတ်မရခြင်း")
        val app = info.applicationInfo ?: throw IllegalArgumentException("Application metadata မရှိပါ")
        app.sourceDir = apk.absolutePath
        app.publicSourceDir = apk.absolutePath
        val minSdk = if (Build.VERSION.SDK_INT >= 24) app.minSdkVersion else 1
        val target = app.targetSdkVersion
        val notes = mutableListOf<String>()
        var compatible = Build.VERSION.SDK_INT >= minSdk
        if (!compatible) notes += "ဒီဖုန်း၏ Android version သည် app ၏ minimum SDK ထက်နိမ့်နေသည်"
        val deviceAbis = Build.SUPPORTED_ABIS.toList()
        val apkAbis = ZipFile(apk).use { zip ->
            val found = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("lib/")) {
                    val abi = name.removePrefix("lib/").substringBefore('/')
                    if (abi.isNotBlank()) found += abi
                }
            }
            found.toList()
        }
        if (apkAbis.isNotEmpty() && apkAbis.none { it in deviceAbis }) {
            compatible = false
            notes += "APK native CPU ABI များ (${apkAbis.joinToString()} ) သည် ဖုန်း ABI (${deviceAbis.joinToString()} ) နှင့် မကိုက်ညီပါ"
        }
        if (target > Build.VERSION.SDK_INT + 10) notes += "Target SDK သည် ဖုန်း OS ထက် အလွန်မြင့်နိုင်သည်"
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
        val hashes = certs.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
        }
        if (hashes.isEmpty()) notes += "Signing certificate ကို metadata မှ မရရှိပါ"
        try {
            val installed = pm.getPackageInfo(info.packageName, flags)
            val installedCerts = if (Build.VERSION.SDK_INT >= 28) {
                installed.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
            } else {
                installed.signatures?.map { it.toByteArray() } ?: emptyList()
            }
            val installedHashes = installedCerts.map { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
            }
            if (hashes.isNotEmpty() && installedHashes.isNotEmpty() && hashes.toSet() != installedHashes.toSet()) {
                compatible = false
                notes += "ဖုန်းထဲရှိ App နှင့် APK signing certificate မတူပါ။ Update အဖြစ်တင်၍မရနိုင်ပါ"
            }
            val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            val newVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            if (newVersion < installedVersion) notes += "ဖုန်းထဲရှိ version ထက် APK version နိမ့်နေသည် (downgrade)"
        } catch (_: PackageManager.NameNotFoundException) { }
        return ApkReport(
            info.packageName, info.versionName ?: "unknown",
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            minSdk, target, apkAbis, hashes, compatible, notes
        )
    }
}
