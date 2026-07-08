package com.honglian.smartcycling.ble

import java.util.UUID

/** Cycling Speed and Cadence Service(CSCS)标准 UUID。 */
object CscUuids {
    /** CSC Service 0x1816 */
    val SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    /** CSC Measurement 0x2A5B(Notify) */
    val MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor 0x2902 */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
