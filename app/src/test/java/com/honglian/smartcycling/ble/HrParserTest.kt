package com.honglian.smartcycling.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 心率帧解析测试。
 *
 * 重点覆盖"位宽由 flags.bit0 决定"这一最容易踩坑的规范细节:
 * 很多心率带默认用 uint16 上报,若一律按 uint8 读会得到随机数值。
 */
class HrParserTest {

    @Test
    fun parses_uint8_value() {
        // flags=0x00 → uint8;72 bpm
        assertEquals(72, HrParser.parse(byteArrayOf(0x00, 72)))
    }

    @Test
    fun parses_uint16_value() {
        // flags=0x01 → uint16 小端;180 bpm = 0x00B4
        assertEquals(180, HrParser.parse(byteArrayOf(0x01, 0xB4.toByte(), 0x00)))
    }

    @Test
    fun ignores_extra_flags_and_trailing_bytes() {
        // flags=0x1F(能量消耗/RR 间期等标志全开)→ 仍应先读 uint16;140 bpm
        assertEquals(140, HrParser.parse(byteArrayOf(0x1F, 0x8C.toByte(), 0x00, 0x00, 0x00)))
    }

    @Test
    fun rejects_truncated_frames() {
        assertNull(HrParser.parse(ByteArray(0)))
        assertNull(HrParser.parse(byteArrayOf(0x00)))
        assertNull(HrParser.parse(byteArrayOf(0x01, 0x00)))
    }

    @Test
    fun rejects_implausible_values() {
        assertNull(HrParser.parse(byteArrayOf(0x00, 0)))          // 0 bpm
        assertNull(HrParser.parse(byteArrayOf(0x00, 0xFF.toByte()))) // 255 bpm > 生理上限
    }
}
