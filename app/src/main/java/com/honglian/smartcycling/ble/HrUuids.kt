package com.honglian.smartcycling.ble

import java.util.UUID

/** Heart Rate Service(HRS)标准 UUID。 */
object HrUuids {
    /** Heart Rate Service 0x180D */
    val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Measurement 0x2A37(Notify) */
    val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor 0x2902 */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
