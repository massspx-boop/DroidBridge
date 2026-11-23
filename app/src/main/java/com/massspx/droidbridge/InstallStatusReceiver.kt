package com.massspx.droidbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val text = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Install confirmation လိုအပ်သည်"
            PackageInstaller.STATUS_SUCCESS -> "App installation အောင်မြင်သည်"
            else -> "Install မအောင်မြင်ပါ: $message"
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirm != null) context.startActivity(confirm)
        }
    }
}
