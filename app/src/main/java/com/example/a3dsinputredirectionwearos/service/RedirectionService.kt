package com.example.a3dsinputredirectionwearos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.a3dsinputredirectionwearos.R
import com.example.a3dsinputredirectionwearos.RedirectionHolder
import com.example.a3dsinputredirectionwearos.presentation.MainActivity

class RedirectionService : Service() {

    private val bridge by lazy { RedirectionHolder.get(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForegroundBridge()
        return START_STICKY
    }

    private fun startForegroundBridge() {
        acquireLocks()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        bridge.start()
    }

    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun stopEverything() {
        bridge.stop()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running in background when activity task is cleared or swiped away
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        bridge.stop()
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "3DSInputWear:bridge").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiLock = wifi?.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "3DSInputWear:wifi"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "3DS Input Redirection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Keeps 3DS UDP input redirection active" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val ip = bridge.configFlow.value.targetIp.ifBlank { "no IP" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("3DS Input Redirection")
            .setContentText("Sending UDP to $ip:4950")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.a3dsinputredirectionwearos.action.START"
        const val ACTION_STOP = "com.example.a3dsinputredirectionwearos.action.STOP"
        private const val CHANNEL_ID = "input_redirection_service"
        private const val NOTIF_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, RedirectionService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, RedirectionService::class.java).setAction(ACTION_STOP)
    }
}
