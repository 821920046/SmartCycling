package com.honglian.smartcycling

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.honglian.smartcycling.core.Container

/**
 * 应用入口。持有全局依赖容器(轻量 DI),并创建前台服务通知渠道。
 */
class SmartCyclingApp : Application() {

    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        initAmapPrivacy()
        container = Container(this)
        createRideChannel()
    }

    /** 高德 SDK 合规:使用前必须声明已展示并同意隐私政策,否则地图/定位不工作。 */
    private fun initAmapPrivacy() {
        runCatching {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
        }
    }

    private fun createRideChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RIDE_CHANNEL_ID,
                getString(R.string.ride_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val RIDE_CHANNEL_ID = "ride_channel"
    }
}
