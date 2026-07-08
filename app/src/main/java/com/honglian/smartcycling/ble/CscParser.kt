package com.honglian.smartcycling.ble

/**
 * 解析 CSC Measurement(0x2A5B)数据帧。
 *
 * 帧格式(小端):
 * - byte0: flags。bit0=包含轮转(速度);bit1=包含曲柄(踏频)
 * - 若 bit0: uint32 累计轮转 + uint16 最后轮转时间(1/1024s)
 * - 若 bit1: uint16 累计曲柄 + uint16 最后曲柄时间(1/1024s)
 */
object CscParser {

    fun parse(data: ByteArray): CscData {
        require(data.isNotEmpty()) { "CSC frame is empty" }
        val flags = data[0].toInt() and 0xFF
        var offset = 1

        var wheelRevs: Long? = null
        var wheelTime: Int? = null
        var crankRevs: Int? = null
        var crankTime: Int? = null

        if (flags and 0x01 != 0) {
            require(data.size >= offset + 6) { "CSC wheel data truncated" }
            wheelRevs = u32(data, offset); offset += 4
            wheelTime = u16(data, offset); offset += 2
        }
        if (flags and 0x02 != 0) {
            require(data.size >= offset + 4) { "CSC crank data truncated" }
            crankRevs = u16(data, offset); offset += 2
            crankTime = u16(data, offset); offset += 2
        }
        return CscData(wheelRevs, wheelTime, crankRevs, crankTime)
    }

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, o: Int): Long =
        u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
}
