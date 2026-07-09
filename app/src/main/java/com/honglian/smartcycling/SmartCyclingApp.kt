package com.honglian.smartcycling

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.navi.NaviSetting
import com.amap.api.services.core.ServiceSettings
import com.honglian.smartcycling.core.Container
import com.honglian.smartcycling.core.CrashHandler

/**
 * 应用入口。持有全局依赖容器(轻量 DI),并创建前台服务通知渠道。
 */
class SmartCyclingApp : Application() {

    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        // 全局崩溃兜底:捕获未处理异常并落盘,下次启动展示
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
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
            // 导航 SDK 有独立的隐私合规开关:不设置会导致创建导航视图(AMapNaviView)时闪退
            NaviSetting.updatePrivacyShow(this, true, true)
            NaviSetting.updatePrivacyAgree(this, true)
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
