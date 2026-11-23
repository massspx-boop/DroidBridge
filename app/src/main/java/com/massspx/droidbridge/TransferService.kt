package com.massspx.droidbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class TransferService : Service() {
    companion object {
        const val ACTION_START = "com.massspx.droidbridge.TRANSFER_START"
        const val ACTION_STOP = "com.massspx.droidbridge.TRANSFER_STOP"
        private const val CHANNEL_ID = "droidbridge_transfer"
        private const val NOTIFICATION_ID = 7301
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "DroidBridge transfer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TransferEngine.stop()
            getSharedPreferences("transfer_state", MODE_PRIVATE).edit().remove("receiver_pin").apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        val pin = TransferEngine.receive(this) { message ->
            getSharedPreferences("transfer_state", MODE_PRIVATE).edit().putString("receiver_status", message).apply()
        }
        getSharedPreferences("transfer_state", MODE_PRIVATE).edit().putString("receiver_pin", pin).apply()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 7301, open,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("DroidBridge receiver active")
                .setContentText("Waiting for a paired device on the local Wi-Fi network")
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("DroidBridge receiver active")
                .setContentText("Waiting for a paired device")
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        TransferEngine.stop()
        getSharedPreferences("transfer_state", MODE_PRIVATE).edit().remove("receiver_pin").apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
