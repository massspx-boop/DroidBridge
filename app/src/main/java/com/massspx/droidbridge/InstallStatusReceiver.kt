package com.massspx.droidbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Toast.makeText(context, "Install confirmation လိုအပ်သည်", Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "App installation အောင်မြင်သည်", Toast.LENGTH_LONG).show()
            else -> {
                val raw = message.ifBlank { "PackageInstaller status=$status" }
                AiDiagnosisEngine.diagnose(context, raw) { diagnosis ->
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Install failed: $diagnosis", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
