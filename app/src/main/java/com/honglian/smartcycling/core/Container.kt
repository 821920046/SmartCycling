package com.honglian.smartcycling.core

import android.content.Context
import com.honglian.smartcycling.ble.S314Manager
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

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val rideRepository: RideRepository by lazy { RideRepository(database.rideDao()) }
    val sensorManager: S314Manager by lazy { S314Manager(appContext) }
    val pairingRepository: PairingRepository by lazy { PairingRepository(appContext) }
    val locationTracker: LocationTracker by lazy { LocationTracker(appContext) }
}
