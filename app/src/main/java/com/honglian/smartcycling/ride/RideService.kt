package com.honglian.smartcycling.ride

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.honglian.smartcycling.R
import com.honglian.smartcycling.SmartCyclingApp

/**
 * 前台服务:骑行中锁屏也能持续采集 GPS 与维持 BLE 连接。
 * 使用 LifecycleService 以便在服务内安全收集协程流。
 */
class RideService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, SmartCyclingApp.RIDE_CHANNEL_ID)
            .setContentTitle(getString(R.string.ride_notification_title))
            .setContentText(getString(R.string.ride_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
    }
}
