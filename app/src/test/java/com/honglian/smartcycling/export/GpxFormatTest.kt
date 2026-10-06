package com.honglian.smartcycling.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GPX 读写器的纯逻辑测试(无需设备,直接 JVM 运行)。
 * 覆盖:往返一致性、XML 转义、路线文件(rtept)、时区偏移解析、多轨迹、空输入。
 */
class GpxFormatTest {

    private fun sampleTrack() = GpxTrack(
        name = "骑行 & <测试>",
        points = listOf(
            GpxPoint(39.907500, 116.397230, 43.5, 1_700_000_000_000L),
            GpxPoint(39.908000, 116.398000, null, null),
        ),
        startTimeMs = 1_700_000_000_000L,
    )

    @Test
    fun write_then_parse_round_trip() {
        val xml = GpxFormat.write(sampleTrack())

        assertTrue(xml.contains("version=\"1.1\""))
        assertTrue(xml.contains("<trkpt lat=\"39.907500\" lon=\"116.397230\">"))

        val parsed = GpxFormat.parse(xml)
        assertEquals(2, parsed.points.size)
        assertEquals(39.9075, parsed.points[0].latitude, 1e-9)
        assertEquals(116.39723, parsed.points[0].longitude, 1e-9)
        assertEquals(43.5, parsed.points[0].elevationM!!, 1e-9)
        assertEquals(1_700_000_000_000L, parsed.points[0].timeMs)
        assertNull(parsed.points[1].elevationM)
        assertNull(parsed.points[1].timeMs)
    }

    @Test
    fun write_escapes_xml_special_chars() {
        val xml = GpxFormat.write(GpxTrack(name = "a & b <c> \"d\"", points = emptyList()))
        assertTrue(xml.contains("a &amp; b &lt;c&gt; &quot;d&quot;"))
    }

    @Test
    fun parse_supports_route_points_and_timezone_offset() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <rte>
                <name>路线A</name>
                <rtept lat="31.230400" lon="121.473700"><ele>4.0</ele><time>2024-01-02T03:04:05+08:00</time></rtept>
                <rtept lat="31.231000" lon="121.474000"/>
              </rte>
            </gpx>
        """.trimIndent()

        val t = GpxFormat.parse(xml)
        assertEquals(2, t.points.size)
        assertEquals("路线A", t.name)
        assertEquals(31.2304, t.points[0].latitude, 1e-9)
        assertNotNull(t.points[0].timeMs)
        assertEquals(4.0, t.points[0].elevationM!!, 1e-9)
    }

    @Test
    fun parse_prefers_track_points_over_waypoints() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="1.0" lon="1.0"/>
              <trk><name>T</name><trkseg>
                <trkpt lat="2.0" lon="2.0"/>
                <trkpt lat="3.0" lon="3.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val t = GpxFormat.parse(xml)
        assertEquals(2, t.points.size)
        assertEquals(2.0, t.points[0].latitude, 1e-9)
    }

    @Test
    fun write_multiple_tracks() {
        val xml = GpxFormat.write(listOf(sampleTrack(), sampleTrack()))
        assertEquals(2, Regex("<trk>").findAll(xml).count())
        assertEquals(1, Regex("<metadata>").findAll(xml).count())
    }

    @Test
    fun parse_skips_points_without_lat_lon() {
        val xml = """
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="2.0" lon="2.0"/>
                <trkpt><ele>9.0</ele></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        assertEquals(1, GpxFormat.parse(xml).points.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_blank_input() {
        GpxFormat.parse("   ")
    }
}
