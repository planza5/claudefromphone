package com.example.runpodmanager.data.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.runpodmanager.MainActivity
import com.example.runpodmanager.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "SshForegroundService"
const val SSH_NOTIFICATION_CHANNEL_ID = "ssh_connection_channel"
const val SSH_NOTIFICATION_ID = 1001

@AndroidEntryPoint
class SshForegroundService : Service() {

    @Inject
    lateinit var sshManager: SshManager

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): SshForegroundService = this@SshForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SSH_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SSH_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            SSH_NOTIFICATION_CHANNEL_ID,
            "Conexion SSH",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene la conexion SSH activa"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return buildNotification("Conexion SSH activa")
    }

    fun updateNotification(host: String) {
        val notification = buildNotification("Conectado a $host")
        getSystemService(NotificationManager::class.java).notify(SSH_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SSH_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SSH Conectado")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RunpodManager::SshWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hora max
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
}
