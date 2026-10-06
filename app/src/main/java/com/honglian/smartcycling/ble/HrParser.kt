package com.honglian.smartcycling.ble

/**
 * 解析 Heart Rate Measurement(0x2A37)数据帧。
 *
 * 帧格式(小端,蓝牙 SIG 规范):
 * - byte0: flags。**bit0 = 心率值位宽**:0 → uint8,1 → uint16
 *   (bit1 能量消耗、bit2 RR 间期等标志位与本应用无关,直接忽略)
 * - 紧随其后即为心率值(单位 bpm)
 *
 * 注意:不少廉价心率带默认用 uint16 上报,只按 uint8 读会把数值读成随机字节,
 * 因此必须严格按 flags 判断位宽。
 */
object HrParser {

    /** 解析出心率(bpm);帧长不足或明显非法时返回 null。 */
    fun parse(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        val isUint16 = flags and 0x01 != 0
        val bpm = if (isUint16) {
            if (data.size < 3) return null
            u16(data, 1)
        } else {
            if (data.size < 2) return null
            data[1].toInt() and 0xFF
        }
        // 生理上限保护:>250 视为噪声/误帧。
        return bpm.takeIf { it in 1..250 }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
}
