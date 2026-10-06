package com.honglian.smartcycling.offline

import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.InputStream

/**
 * 坐标系重映射瓦片源。
 *
 * 第一性原理 —— 为什么用"重映射瓦片"而不是"改投影系统":
 *
 * 高德/腾讯(GCJ-02)、百度(BD-09)的瓦片网格与 GPS(WGS-84)不是同一套网格。
 * 一个自然的想法是给 osmdroid 换一个"偏移过的 TileSystem",但 GCJ 偏移量是
 * **经纬度二元耦合**的(`dLat` 与 `dLon` 都同时依赖 lat 与 lon),
 * 而 TileSystem 的接口 `getX01FromLongitude(lon)` / `getY01FromLatitude(lat)`
 * 是**一维可分离**的 —— 强行套用只能取常数近似,误差可达数十米。
 *
 * 正确做法:让 osmdroid 保持在标准 WGS-84 网格上(相机、覆盖物、GPS 全部原生自洽),
 * 只在"取哪一块瓦片图片"这一步做一次精确换算:
 *   请求的 WGS 瓦片中心 → 转成包坐标系经纬度 → 反查包内该处的瓦片 → 返回它的图片。
 *
 * 残余误差 = 偏移场在一张瓦片范围内的变化量。z=18 时瓦片约 150m 宽,
 * 而 GCJ 偏移场在 150m 尺度上的变化是**亚米级**,实际不可见 —— 精度远优于任何常数近似。
 */
class CrsRemapArchiveFile(
    private val delegate: IArchiveFile,
    private val crs: MapCrs,
) : IArchiveFile {

    override fun init(pFile: File) = delegate.init(pFile)

    override fun getInputStream(tileSource: ITileSource, pMapTileIndex: Long): InputStream? {
        if (crs == MapCrs.WGS84) return delegate.getInputStream(tileSource, pMapTileIndex)

        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val limit = TileMath.limit(zoom)
        if (zoom < 0 || zoom > 24 || x < 0 || y < 0 || x >= limit || y >= limit) return null

        val center = TileMath.tileCenter(zoom, x, y)
        val shifted = GeoTransform.convert(center[0], center[1], MapCrs.WGS84, crs)
        val tx = TileMath.lonToTileX(shifted[1], zoom)
        val ty = TileMath.latToTileY(shifted[0], zoom)
        if (tx < 0 || ty < 0 || tx >= limit || ty >= limit) return null

        if (tx == x && ty == y) return delegate.getInputStream(tileSource, pMapTileIndex)
        return delegate.getInputStream(tileSource, MapTileIndex.getTileIndex(zoom, tx, ty))
    }

    override fun close() = delegate.close()

    override fun getTileSources(): MutableSet<String> =
        delegate.tileSources?.toMutableSet() ?: mutableSetOf()

    override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) =
        delegate.setIgnoreTileSource(pIgnoreTileSource)
}

/**
 * 把"解压好的瓦片目录"包装成 osmdroid 可读的归档。
 *
 * osmdroid 自带 ZIP / MBTiles / SQLite 三种归档驱动,唯独不支持**散装目录**。
 * 而"解压后直接丢进手机"恰恰是最常见的用法,所以这里补上这一环。
 *
 * 结构兼容两种常见排布:
 *   `<root>/<z>/<x>/<y>.<ext>`
 *   `<root>/<包裹目录>/<z>/<x>/<y>.<ext>`
 */
class DirectoryArchiveFile(private var root: File) : IArchiveFile {

    /** 预先算好候选根目录,避免每取一块瓦片都列一次目录。 */
    private val bases: List<File> by lazy {
        val list = mutableListOf(root)
        runCatching {
            root.listFiles { f -> f.isDirectory && f.name.toIntOrNull() == null }
                ?.take(4)
                ?.forEach { list += it }
        }
        list
    }

    override fun init(pFile: File) {
        if (pFile.isDirectory) root = pFile
    }

    override fun getInputStream(tileSource: ITileSource, pMapTileIndex: Long): InputStream? {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        if (z < 0 || x < 0 || y < 0) return null
        for (base in bases) {
            for (ext in TILE_EXTENSIONS) {
                val f = File(base, "$z/$x/$y.$ext")
                if (f.isFile) {
                    return runCatching { f.inputStream() }.getOrNull()
                }
            }
        }
        return null
    }

    override fun close() = Unit

    override fun getTileSources(): MutableSet<String> = mutableSetOf(root.name)

    /**
     * 散装目录没有"顶层目录名 = 瓦片源名"的约定,因此**始终忽略**瓦片源名。
     * 保留该方法只为满足 [IArchiveFile] 契约。
     */
    override fun setIgnoreTileSource(pIgnoreTileSource: Boolean) = Unit
}
