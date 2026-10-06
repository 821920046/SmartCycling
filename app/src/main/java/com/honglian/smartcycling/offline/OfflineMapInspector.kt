package com.honglian.smartcycling.offline

import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import kotlin.math.pow

/** 探测结果:一个离线包的关键元数据。 */
data class OfflineMapProbe(
    val format: OfflineMapFormat,
    val minZoom: Int,
    val maxZoom: Int,
    val tileSize: Int,
    val tileCount: Long,
    /** `south,west,north,east`,基于瓦片网格(Web Mercator)推算,未知为 null。 */
    val bounds: String?,
    /** 从容器内读到的自述名称(如 MBTiles metadata.name)。 */
    val declaredName: String?,
    /** 致命问题(无法渲染),非 null 表示导入应被拒绝并提示用户。 */
    val fatal: String? = null,
    /** 非致命提醒(如坐标系需要确认、格式需转换)。 */
    val warning: String? = null,
)

/**
 * 离线地图包识别与元数据探测。
 *
 * 设计原则:
 *  - **只读**:探测过程绝不修改用户文件;SQLite 一律以 OPEN_READONLY 打开。
 *  - **不加载全量数据**:只读 SQLite 头部 / ZIP 中央目录 / 文件头,数 GB 的包也能秒级探测。
 *  - **失败即降级**:任何解析异常都不抛出,而是返回一个带 [OfflineMapProbe.warning] 的保守结果,
 *    由上层决定是否放行。
 */
object OfflineMapInspector {

    private const val MAX_ZOOM_SCAN = 24

    /** 解码校验的字节上限:超过该值直接跳过,避免为"体检"读取超大 blob。 */
    private const val MAX_DECODE_PROBE_BYTES = 8 * 1024 * 1024

    /** 识别并探测一个文件或目录。 */
    fun inspect(file: File): OfflineMapProbe {
        if (file.isDirectory) return inspectFolder(file)
        val format = detectFormat(file)
        return when (format) {
            OfflineMapFormat.MBTILES -> inspectSqlite(file, mbtiles = true)
            OfflineMapFormat.SQLITE -> inspectSqlite(file, mbtiles = false)
            OfflineMapFormat.GEOPACKAGE -> inspectGeoPackage(file)
            OfflineMapFormat.ZIP -> inspectZip(file)
            OfflineMapFormat.PMTILES -> OfflineMapProbe(
                format = format, minZoom = 0, maxZoom = 0, tileSize = 256, tileCount = 0, bounds = null,
                declaredName = null,
                fatal = "PMTiles 需要先转换为 MBTiles 才能渲染",
                warning = "可用 pmtiles convert 或 QGIS 导出为 .mbtiles 后重新导入",
            )
            OfflineMapFormat.UNKNOWN -> OfflineMapProbe(
                format = format, minZoom = 0, maxZoom = 0, tileSize = 256, tileCount = 0, bounds = null,
                declaredName = null,
                fatal = "无法识别的离线地图格式",
                warning = "支持 MBTiles / ZIP / 瓦片文件夹 / osmdroid SQLite / GeoPackage",
            )
        }
    }

    // ---------------------------------------------------------------- 格式识别

    /** 依据"魔数 + 扩展名"判定格式。魔数优先,扩展名兜底。 */
    fun detectFormat(file: File): OfflineMapFormat {
        val ext = file.extension.lowercase()
        val head = readHead(file, 16)
        // 1) SQLite 家族
        if (head.size >= 16 && String(head, 0, 15, Charsets.US_ASCII) == "SQLite format 3") {
            return when (ext) {
                "gpkg" -> OfflineMapFormat.GEOPACKAGE
                else -> when (sqliteKind(file)) {
                    SqliteKind.MBTILES -> OfflineMapFormat.MBTILES
                    SqliteKind.OSMDROID -> OfflineMapFormat.SQLITE
                    SqliteKind.GEOPACKAGE -> OfflineMapFormat.GEOPACKAGE
                    SqliteKind.NONE -> OfflineMapFormat.UNKNOWN
                }
            }
        }
        // 2) PMTiles 魔数
        if (head.size >= 7 && String(head, 0, 7, Charsets.US_ASCII) == "PMTiles") {
            return OfflineMapFormat.PMTILES
        }
        // 3) ZIP 魔数 PK\x03\x04
        if (head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()) {
            return OfflineMapFormat.ZIP
        }
        return when (ext) {
            "mbtiles" -> OfflineMapFormat.MBTILES
            "sqlite", "db" -> OfflineMapFormat.SQLITE
            "zip" -> OfflineMapFormat.ZIP
            "gpkg" -> OfflineMapFormat.GEOPACKAGE
            "pmtiles" -> OfflineMapFormat.PMTILES
            else -> OfflineMapFormat.UNKNOWN
        }
    }

    private enum class SqliteKind { MBTILES, OSMDROID, GEOPACKAGE, NONE }

    /** 通过表名区分 SQLite 家族:MBTiles 有 metadata 表;osmdroid 的 tiles 表带 provider 列。 */
    private fun sqliteKind(file: File): SqliteKind = runCatching {
        openReadOnly(file)?.use { db ->
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0).lowercase()
            }
            if ("gpkg_contents" in tables) return@use SqliteKind.GEOPACKAGE
            if ("tiles" !in tables) return@use SqliteKind.NONE
            val cols = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(tiles)", null).use { c ->
                while (c.moveToNext()) cols += c.getString(1).lowercase()
            }
            when {
                "tile_data" in cols -> SqliteKind.MBTILES
                "provider" in cols -> SqliteKind.OSMDROID
                else -> SqliteKind.MBTILES
            }
        } ?: SqliteKind.NONE
    }.getOrDefault(SqliteKind.NONE)

    // ---------------------------------------------------------------- 探测实现

    private fun inspectSqlite(file: File, mbtiles: Boolean): OfflineMapProbe = runCatching {
        val db = openReadOnly(file) ?: return@runCatching broken(file, if (mbtiles) OfflineMapFormat.MBTILES else OfflineMapFormat.SQLITE, "SQLite 打开失败")
        db.use { d ->
            var declared: String? = null
            var declaredFormat: String? = null
            var minZ = 0
            var maxZ = 0
            var count = 0L
            var bounds: String? = null

            if (mbtiles) {
                runCatching {
                    d.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                        while (c.moveToNext()) {
                            when (c.getString(0)?.lowercase()) {
                                "name" -> declared = c.getString(1)
                                "format" -> declaredFormat = c.getString(1)
                                "minzoom" -> minZ = c.getString(1)?.toDoubleOrNull()?.toInt() ?: minZ
                                "maxzoom" -> maxZ = c.getString(1)?.toDoubleOrNull()?.toInt() ?: maxZ
                                "bounds" -> bounds = c.getString(1)
                            }
                        }
                    }
                }
                runCatching {
                    d.rawQuery(
                        "SELECT MIN(zoom_level), MAX(zoom_level), COUNT(*) FROM tiles",
                        null,
                    ).use { c ->
                        if (c.moveToFirst()) {
                            minZ = if (c.isNull(0)) minZ else c.getInt(0)
                            maxZ = if (c.isNull(1)) maxZ else c.getInt(1)
                            count = c.getLong(2)
                        }
                    }
                }
            } else {
                // osmdroid: key = ((z << z) + x) << z + y,可反推 zoom 区间
                runCatching {
                    d.rawQuery("SELECT MIN(key), MAX(key), COUNT(*) FROM tiles", null).use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) {
                            val lo = c.getLong(0)
                            val hi = c.getLong(1)
                            count = c.getLong(2)
                            minZ = zoomOfKey(lo)
                            maxZ = zoomOfKey(hi)
                        }
                    }
                }
            }

            // 矢量瓦片(pbf/mvt)是 protobuf,osmdroid 的栅格管线无法解码,直接拒绝比"渲染出空白"友好。
            val isVector = declaredFormat?.lowercase()?.let { it in VECTOR_FORMATS } == true

            val firstTile = firstTileBytes(d)
            val tileSize = firstTile?.let { imageSize(it) } ?: 256
            val decodable = canDecode(firstTile)
            OfflineMapProbe(
                format = if (mbtiles) OfflineMapFormat.MBTILES else OfflineMapFormat.SQLITE,
                minZoom = minZ, maxZoom = maxZ, tileSize = tileSize,
                tileCount = count, bounds = normalizeBounds(bounds), declaredName = declared,
                fatal = when {
                    count == 0L -> "该瓦片包内没有任何瓦片数据"
                    isVector -> "这是矢量瓦片包(format=${declaredFormat}),无法渲染"
                    else -> null
                },
                warning = when {
                    isVector -> "请重新导出为 PNG/JPEG 栅格瓦片"
                    !decodable -> "首张瓦片无法解码,该包可能已损坏"
                    else -> null
                },
            )
        }
    }.getOrElse { broken(file, if (mbtiles) OfflineMapFormat.MBTILES else OfflineMapFormat.SQLITE, it.message) }

    private fun inspectGeoPackage(file: File): OfflineMapProbe = runCatching {
        openReadOnly(file)?.use { d ->
            var minZ = 0
            var maxZ = 0
            var bounds: String? = null
            runCatching {
                d.rawQuery("SELECT min_zoom, max_zoom FROM gpkg_tile_matrix", null).use { c ->
                    while (c.moveToNext()) {
                        minZ = if (c.getInt(0) < minZ) c.getInt(0) else minZ
                        maxZ = if (c.getInt(1) > maxZ) c.getInt(1) else maxZ
                    }
                }
            }
            runCatching {
                d.rawQuery(
                    "SELECT min_x, min_y, max_x, max_y FROM gpkg_contents WHERE data_type='tiles'",
                    null,
                ).use { c ->
                    if (c.moveToFirst()) bounds = "${c.getDouble(1)},${c.getDouble(0)},${c.getDouble(3)},${c.getDouble(2)}"
                }
            }
            OfflineMapProbe(
                format = OfflineMapFormat.GEOPACKAGE,
                minZoom = minZ, maxZoom = maxZ, tileSize = 256,
                tileCount = 0, bounds = bounds, declaredName = null,
                fatal = "GeoPackage 栅格暂未接入渲染",
                warning = "可在 QGIS 中导出为 MBTiles 后导入",
            )
        } ?: broken(file, OfflineMapFormat.GEOPACKAGE, "SQLite 打开失败")
    }.getOrElse { broken(file, OfflineMapFormat.GEOPACKAGE, it.message) }

    private fun inspectZip(file: File): OfflineMapProbe = runCatching {
        val zooms = sortedSetOf<Int>()
        var count = 0L
        var ext: String? = null
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        var topZoomForBounds = 0
        var firstTileData: ByteArray? = null

        ZipFile(file).use { zip ->
            val e = zip.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                val parsed = parseTilePath(entry.name) ?: continue
                if (firstTileData == null) {
                    firstTileData = runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull()
                }
                count++
                zooms += parsed.z
                if (ext == null) ext = parsed.ext
                if (parsed.z >= topZoomForBounds) {
                    if (parsed.z > topZoomForBounds) {
                        minX = Int.MAX_VALUE; maxX = Int.MIN_VALUE; minY = Int.MAX_VALUE; maxY = Int.MIN_VALUE
                        topZoomForBounds = parsed.z
                    }
                    if (parsed.x < minX) minX = parsed.x
                    if (parsed.x > maxX) maxX = parsed.x
                    if (parsed.y < minY) minY = parsed.y
                    if (parsed.y > maxY) maxY = parsed.y
                }
            }
        }
        if (count == 0L) {
            return@runCatching broken(file, OfflineMapFormat.ZIP, "ZIP 内未找到 <z>/<x>/<y> 结构的瓦片")
        }
        // osmdroid 的 ZipFileArchive 内部把瓦片路径硬编码为 `.png`,
        // 因此 ZIP 内若是 jpg/webp,即使关闭瓦片源名过滤也取不到图。这里提前拦下,避免"导入成功但一片空白"。
        if (ext != null && ext != "png") {
            return@runCatching OfflineMapProbe(
                format = OfflineMapFormat.ZIP,
                minZoom = zooms.first(), maxZoom = zooms.last(), tileSize = 256,
                tileCount = count, bounds = null, declaredName = null,
                fatal = "ZIP 内瓦片为 .$ext,osmdroid 的 ZIP 驱动仅识别 .png",
                warning = "请先解压,再用「导入文件夹」导入(文件夹模式支持 png/jpg/webp)",
            )
        }
        OfflineMapProbe(
            format = OfflineMapFormat.ZIP,
            minZoom = zooms.first(), maxZoom = zooms.last(), tileSize = 256,
            tileCount = count,
            bounds = boundsFromTiles(minX, maxX, minY, maxY, topZoomForBounds),
            declaredName = null,
            warning = if (!canDecode(firstTileData)) "首张瓦片无法解码,该 ZIP 可能已损坏" else null,
        )
    }.getOrElse { broken(file, OfflineMapFormat.ZIP, it.message) }

    private fun inspectFolder(dir: File): OfflineMapProbe = runCatching {
        val zooms = sortedSetOf<Int>()
        var count = 0L
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        var topZoom = 0
        var firstTileFile: File? = null

        dir.walkTopDown()
            .maxDepth(4) // <root>/[可选一层包裹目录]/<z>/<x>/<y>.<ext>
            .filter { it.isFile }
            .forEach { f ->
                val rel = f.relativeTo(dir).path.replace('\\', '/')
                val parsed = parseTilePath(rel) ?: return@forEach
                count++
                if (firstTileFile == null) firstTileFile = f
                zooms += parsed.z
                if (parsed.z >= topZoom) {
                    if (parsed.z > topZoom) {
                        minX = Int.MAX_VALUE; maxX = Int.MIN_VALUE; minY = Int.MAX_VALUE; maxY = Int.MIN_VALUE
                        topZoom = parsed.z
                    }
                    if (parsed.x < minX) minX = parsed.x
                    if (parsed.x > maxX) maxX = parsed.x
                    if (parsed.y < minY) minY = parsed.y
                    if (parsed.y > maxY) maxY = parsed.y
                }
            }

        if (count == 0L) {
            return@runCatching broken(dir, OfflineMapFormat.FOLDER, "文件夹内未找到 <z>/<x>/<y> 结构的瓦片")
        }
        val decodable = canDecode(firstTileFile?.let { runCatching { it.readBytes() }.getOrNull() })
        OfflineMapProbe(
            format = OfflineMapFormat.FOLDER,
            minZoom = zooms.first(), maxZoom = zooms.last(), tileSize = 256,
            tileCount = count,
            bounds = boundsFromTiles(minX, maxX, minY, maxY, topZoom),
            declaredName = dir.name,
            warning = if (!decodable) "首张瓦片无法解码,该目录可能不是有效的瓦片目录" else null,
        )
    }.getOrElse { broken(dir, OfflineMapFormat.FOLDER, it.message) }

    // ---------------------------------------------------------------- 工具

    private data class TilePath(val z: Int, val x: Int, val y: Int, val ext: String)

    /** 从形如 `any/prefix/<z>/<x>/<y>.<ext>` 的路径解析瓦片坐标;不是瓦片则返回 null。 */
    private fun parseTilePath(path: String): TilePath? {
        val parts = path.replace('\\', '/').split('/')
        if (parts.size < 3) return null
        val yRaw = parts[parts.size - 1]
        val xRaw = parts[parts.size - 2]
        val zRaw = parts[parts.size - 3]
        val dot = yRaw.lastIndexOf('.')
        if (dot <= 0) return null
        val ext = yRaw.substring(dot + 1).lowercase()
        if (ext !in TILE_EXTENSIONS) return null
        val y = yRaw.substring(0, dot).toIntOrNull() ?: return null
        val x = xRaw.toIntOrNull() ?: return null
        val z = zRaw.toIntOrNull() ?: return null
        if (z < 0 || z > MAX_ZOOM_SCAN || x < 0 || y < 0) return null
        return TilePath(z, x, y, ext)
    }

    /** 由瓦片行列号反推经纬度包围盒(Web Mercator)。 */
    private fun boundsFromTiles(minX: Int, maxX: Int, minY: Int, maxY: Int, z: Int): String? {
        if (minX > maxX || minY > maxY || z <= 0) return null
        val n = 2.0.pow(z)
        val west = minX / n * 360.0 - 180.0
        val east = (maxX + 1) / n * 360.0 - 180.0
        val north = tileYToLat(minY.toDouble(), n)
        val south = tileYToLat(maxY + 1.0, n)
        return "%.6f,%.6f,%.6f,%.6f".format(south, west, north, east)
    }

    private fun tileYToLat(y: Double, n: Double): Double {
        val t = Math.PI * (1 - 2 * y / n)
        return Math.toDegrees(Math.atan(Math.sinh(t)))
    }

    /** osmdroid key → zoom。key = ((z << z) + x) << z + y,x/y ∈ [0, 2^z)。 */
    private fun zoomOfKey(key: Long): Int {
        for (z in MAX_ZOOM_SCAN downTo 0) {
            val size = 1L shl (2 * z)
            val base = z.toLong() * size
            if (key >= base && key < base + size) return z
        }
        return 0
    }

    /** 读取首个瓦片的二进制内容(MBTiles 用 `tile_data`,osmdroid SQLite 用 `tile`)。 */
    private fun firstTileBytes(db: SQLiteDatabase): ByteArray? {
        var bytes: ByteArray? = null
        runCatching {
            db.rawQuery("SELECT tile_data FROM tiles LIMIT 1", null).use { c ->
                if (c.moveToFirst()) bytes = c.getBlob(0)
            }
        }
        if (bytes == null) {
            runCatching {
                db.rawQuery("SELECT tile FROM tiles LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) bytes = c.getBlob(0)
                }
            }
        }
        return bytes
    }

    /**
     * 用 BitmapFactory 做一次"只读头部"的解码校验(`inJustDecodeBounds`,不分配像素内存),
     * 判断瓦片是否**真的能被渲染** —— 格式对了但数据损坏时,这一步能提前发现,
     * 而不是让用户导入后对着一片空白发愣。
     */
    private fun canDecode(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_DECODE_PROBE_BYTES) return false
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            opts.outWidth > 0 && opts.outHeight > 0
        }.getOrDefault(false)
    }

    private fun imageSize(b: ByteArray): Int? {
        // PNG: 89 50 4E 47 0D 0A 1A 0A | len(4) | "IHDR" | width(4) | height(4)
        if (b.size > 24 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[3] == 0x47.toByte()) {
            return beInt(b, 16)
        }
        // JPEG: 扫描 SOFn 段
        if (b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) {
            var i = 2
            while (i + 9 < b.size) {
                if (b[i] != 0xFF.toByte()) { i++; continue }
                val marker = b[i + 1].toInt() and 0xFF
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                    return h
                }
                val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
                if (len <= 0) break
                i += 2 + len
            }
        }
        return null
    }

    private fun beInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun openReadOnly(file: File): SQLiteDatabase? = runCatching {
        SQLiteDatabase.openDatabase(
            file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
    }.getOrNull()

    private fun readHead(file: File, n: Int): ByteArray = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(n)
            val read = raf.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))

    private fun normalizeBounds(raw: String?): String? {
        val parts = raw?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
        if (parts.size != 4) return null
        return "%.6f,%.6f,%.6f,%.6f".format(parts[0], parts[1], parts[2], parts[3])
    }

    private fun broken(file: File, format: OfflineMapFormat, msg: String?) = OfflineMapProbe(
        format = format, minZoom = 0, maxZoom = 0, tileSize = 256, tileCount = 0, bounds = null,
        declaredName = null,
        fatal = "解析失败:${msg ?: "文件可能已损坏或不是有效的离线地图包"}",
    )
}
