package com.massspx.droidbridge

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

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
        if (Build.VERSION.SDK_INT < minSdk) notes += "ဒီဖုန်း၏ Android version သည် app ၏ minimum SDK ထက်နိမ့်နေသည်"
        if (target > Build.VERSION.SDK_INT + 10) notes += "Target SDK သည် ဖုန်း OS ထက် အလွန်မြင့်နိုင်သည်"
        val abis = Build.SUPPORTED_ABIS.toList()
        val apkAbis = abis.filter { abi -> apk.name.contains(abi, true) }
        if (apk.name.contains("arm64", true) && abis.none { it == "arm64-v8a" }) notes += "ARM64 APK ဖြစ်ပြီး ဖုန်းတွင် ARM64 ABI မတွေ့ပါ"
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
        val hashes = certs.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
        }
        if (hashes.isEmpty()) notes += "Signing certificate ကို metadata မှ မရရှိပါ"
        return ApkReport(
            info.packageName,
            info.versionName ?: "unknown",
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            minSdk, target, abis, hashes,
            Build.VERSION.SDK_INT >= minSdk,
            notes
        )
    }
}
