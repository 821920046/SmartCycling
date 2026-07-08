package com.honglian.smartcycling.ble

/** 一帧 CSC 原始解析结果。字段为 null 表示本帧不含该类数据。 */
data class CscData(
    val wheelRevs: Long? = null,
    val wheelTime: Int? = null,
    val crankRevs: Int? = null,
    val crankTime: Int? = null,
)

/**
 * 传感器可用读数。单个 S314 同一时刻只会上报速度或踏频之一,
 * 因此两个字段都是可空的。
 */
data class SensorReading(
    val speedKmh: Double? = null,
    val cadenceRpm: Double? = null,
    /** 本帧新增的轮转圈数(用于传感器测距回退) */
    val wheelDeltaRevs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
)
