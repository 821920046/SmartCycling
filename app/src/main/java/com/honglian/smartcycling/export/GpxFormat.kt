package com.honglian.smartcycling.export

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * GPX 轨迹中的一个点。**始终为 WGS-84**(见 [RideGpx.fromRide] 的坐标纠偏说明)。
 */
data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double? = null,
    val timeMs: Long? = null,
)

/** 一条 GPX 轨迹(或路线)。 */
data class GpxTrack(
    val name: String,
    val points: List<GpxPoint>,
    val startTimeMs: Long? = null,
)

/**
 * GPX 1.1 读写器。
 *
 * 设计取舍(参考 OpenTracks / Trackbook / OSMBonusPack 的做法):
 *  - **只依赖 JDK/Android 自带的 DOM 解析器**,不引入第三方 XML 库 → 零体积代价,且纯 JVM 可单测。
 *  - 写出时**显式声明 GPX 1.1 命名空间与 schemaLocation**,保证 Strava / Komoot / Google Earth
 *    等第三方工具能正确识别,而不是当成未知 XML。
 *  - 读取时同时兼容 `<trkpt>`(轨迹)、`<rtept>`(路线)、`<wpt>`(航点)三种点标签 ——
 *    用户从 Garmin / 行者 / 黑鸟 等导出的文件命名习惯各不相同,只认 trkpt 会大面积导入失败。
 *  - 时间统一用 ISO-8601 UTC;解析时兼容 `Z` 结尾与带时区偏移(`+08:00`)两种写法。
 *
 * 安全性:解析前关闭 DTD / 外部实体(XML 外部实体注入,XXE),避免恶意 GPX 读取本地文件。
 */
object GpxFormat {

    const val CREATOR = "SmartCycling"
    private const val NS = "http://www.topografix.com/GPX/1/1"

    /** 单条轨迹写出。 */
    fun write(track: GpxTrack): String = write(listOf(track))

    /** 多条轨迹写出(用于"一键导出全部骑行")。 */
    fun write(tracks: List<GpxTrack>): String {
        val sb = StringBuilder(2048)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"").append(CREATOR).append('"')
        sb.append(" xmlns=\"").append(NS).append('"')
        sb.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        sb.append(" xsi:schemaLocation=\"").append(NS).append(' ').append(NS).append("/gpx.xsd\">\n")

        tracks.firstOrNull()?.let { t ->
            sb.append("  <metadata>\n")
            sb.append("    <name>").append(escape(t.name)).append("</name>\n")
            t.startTimeMs?.let { sb.append("    <time>").append(iso8601(it)).append("</time>\n") }
            sb.append("  </metadata>\n")
        }

        tracks.forEach { t ->
            sb.append("  <trk>\n")
            sb.append("    <name>").append(escape(t.name)).append("</name>\n")
            sb.append("    <type>cycling</type>\n")
            sb.append("    <trkseg>\n")
            t.points.forEach { p ->
                sb.append("      <trkpt lat=\"").append(coord(p.latitude))
                    .append("\" lon=\"").append(coord(p.longitude)).append("\">\n")
                p.elevationM?.let { sb.append("        <ele>").append(oneDecimal(it)).append("</ele>\n") }
                p.timeMs?.let { sb.append("        <time>").append(iso8601(it)).append("</time>\n") }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * 解析 GPX 文本。
     *
     * @throws IllegalArgumentException 内容为空或无法解析为合法 XML 时抛出。
     */
    fun parse(xml: String): GpxTrack {
        require(xml.isNotBlank()) { "GPX 内容为空" }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        // 关闭外部实体 / DTD / XInclude —— 防 XXE,失败也不影响主流程(部分实现不支持这些 feature)。
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }

        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val points = mutableListOf<GpxPoint>()
        for (tag in POINT_TAGS) {
            val list = doc.getElementsByTagName(tag)
            for (i in 0 until list.length) {
                val el = list.item(i) as? Element ?: continue
                val lat = el.getAttribute("lat").trim().toDoubleOrNull() ?: continue
                val lon = el.getAttribute("lon").trim().toDoubleOrNull() ?: continue
                points += GpxPoint(
                    latitude = lat,
                    longitude = lon,
                    elevationM = childText(el, "ele")?.toDoubleOrNull(),
                    timeMs = childText(el, "time")?.let { parseIso8601(it) },
                )
            }
            // 优先用轨迹点;没有 trkpt 才退回 rtept / wpt。
            if (points.isNotEmpty()) break
        }

        val name = firstText(doc, "name")?.takeIf { it.isNotBlank() } ?: "GPX"
        return GpxTrack(
            name = name,
            points = points,
            startTimeMs = points.firstNotNullOfOrNull { it.timeMs },
        )
    }

    private val POINT_TAGS = listOf("trkpt", "rtept", "wpt")

    /** 取某元素的指定子元素的文本(大小写不敏感)。 */
    private fun childText(parent: Element, tag: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val n: Node = children.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && n.nodeName.equals(tag, ignoreCase = true)) {
                return n.textContent?.trim()
            }
        }
        return null
    }

    /** 取文档中第一个指定标签的文本。 */
    private fun firstText(doc: org.w3c.dom.Document, tag: String): String? {
        val list = doc.getElementsByTagName(tag)
        for (i in 0 until list.length) {
            val t = list.item(i).textContent?.trim()
            if (!t.isNullOrBlank()) return t
        }
        return null
    }

    private fun iso8601(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private fun parseIso8601(raw: String): Long? {
        val t = raw.trim()
        return runCatching { Instant.parse(t).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun escape(s: String): String = buildString(s.length + 8) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
