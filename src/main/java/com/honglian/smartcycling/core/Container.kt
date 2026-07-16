package com.honglian.smartcycling.core

import android.content.Context
import com.honglian.smartcycling.ble.S314Manager
import com.honglian.smartcycling.cloud.CloudSyncRepository
import com.honglian.smartcycling.data.AppDatabase
import com.honglian.smartcycling.data.RideRepository
import com.honglian.smartcycling.location.LocationTracker
import com.honglian.smartcycling.pairing.PairingRepository

/**
 * 轻量级依赖容器。避免引入 Hilt 等重型框架,保持工程可直接编译。
 * 所有单例均为进程级,生命周期跟随 Application。
 */
class Container(context: Context) {
    private val appContext = context.applicationContext

    val settings: Settings by lazy { Settings(appContext) }
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val rideRepository: RideRepository by lazy { RideRepository(database.rideDao()) }
    val cloudSyncRepository: CloudSyncRepository by lazy { CloudSyncRepository() }

    /** 传感器创建时应用已保存的车轮周长。 */
    val sensorManager: S314Manager by lazy {
        S314Manager(appContext).apply { wheelCircumferenceM = settings.wheelCircumferenceM }
    }
    val pairingRepository: PairingRepository by lazy { PairingRepository(appContext) }
    val locationTracker: LocationTracker by lazy { LocationTracker(appContext) }
}
