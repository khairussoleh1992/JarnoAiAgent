package com.example.jarnoai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class JarnoForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "jarno_call_channel"
        val channel = NotificationChannel(channelId, "Jarno Call Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarno AI Aktif")
            .setContentText("Panggilan suara dan aksesibilitas sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
