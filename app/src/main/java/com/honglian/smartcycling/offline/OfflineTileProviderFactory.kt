package com.honglian.smartcycling.offline

import android.content.Context
import org.osmdroid.tileprovider.IRegisterReceiver
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.DatabaseFileArchive
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.modules.ZipFileArchive
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.File

/** 一层离线底图所需的最小描述。 */
data class OfflineLayerSpec(
    val path: File,
    val isDirectory: Boolean,
    val format: OfflineMapFormat,
    val crs: MapCrs,
    val minZoom: Int,
    val maxZoom: Int,
    val tileSize: Int,
    val name: String,
    /** 包自身坐标系下的覆盖范围 `south,west,north,east`,可为 null。 */
    val bounds: String? = null,
)

/**
 * 离线瓦片提供器工厂。
 *
 * 与 osmdroid 自带的 `OfflineTileProvider` 相比,这里做了三件它做不到的事:
 *  1. **显式构造归档驱动**,不再依赖 `ArchiveFileFactory` 的"扩展名 → 驱动"映射
 *     (导入时文件名可能被改写,靠扩展名判断会误判)。
 *  2. 对 ZIP / osmdroid-SQLite 调用 `setIgnoreTileSource(true)` —— 否则 osmdroid 会要求
 *     ZIP 内部顶层目录名 / SQLite 的 provider 列**必须等于**文件名,用户自己打包的
 *     瓦片包几乎不可能满足,会静默显示空白。
 *  3. 外面再套一层 [CrsRemapArchiveFile] 做坐标系纠偏。
 */
object OfflineTileProviderFactory {

    fun create(context: Context, spec: OfflineLayerSpec): MapTileProviderBase? = runCatching {
        val archive = openArchive(spec) ?: return@runCatching null
        val remapped = CrsRemapArchiveFile(archive, spec.crs)
        val tileSource = buildTileSource(spec)
        ArchiveTileProvider(SimpleRegisterReceiver(context), tileSource, arrayOf(remapped))
    }.getOrNull()

    private fun openArchive(spec: OfflineLayerSpec): IArchiveFile? = runCatching {
        if (spec.isDirectory || spec.format == OfflineMapFormat.FOLDER) {
            return@runCatching DirectoryArchiveFile(spec.path)
        }
        when (spec.format) {
            OfflineMapFormat.MBTILES -> MBTilesFileArchive().also { it.init(spec.path) }
            OfflineMapFormat.SQLITE -> DatabaseFileArchive().also {
                it.init(spec.path)
                it.setIgnoreTileSource(true)
            }
            OfflineMapFormat.ZIP -> ZipFileArchive().also {
                it.init(spec.path)
                it.setIgnoreTileSource(true)
            }
            else -> null
        }
    }.getOrNull()

    private fun buildTileSource(spec: OfflineLayerSpec): ITileSource {
        val safeName = spec.name
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .ifBlank { "offline" }
            .take(40)
        return XYTileSource(
            safeName,
            spec.minZoom.coerceAtLeast(0),
            spec.maxZoom.coerceAtMost(22).coerceAtLeast(spec.minZoom.coerceAtLeast(0)),
            spec.tileSize.coerceIn(128, 1024),
            ".png",
            arrayOf("http://localhost"),
        )
    }
}

/**
 * 只读归档的瓦片提供器:按顺序尝试归档 → 相邻层级近似,不做任何网络请求。
 * `isDowngradedMode = true` 表示"缺瓦片时就显示空白,不要回退到低分辨率补洞",
 * 避免离线场景下出现模糊的错位底图。
 */
private class ArchiveTileProvider(
    receiver: IRegisterReceiver,
    tileSource: ITileSource,
    archives: Array<IArchiveFile>,
) : MapTileProviderArray(tileSource, receiver, buildChain(receiver, tileSource, archives)) {

    override fun isDowngradedMode(pMapTileIndex: Long): Boolean = true
}

private fun buildChain(
    receiver: IRegisterReceiver,
    tileSource: ITileSource,
    archives: Array<IArchiveFile>,
): Array<MapTileModuleProviderBase> {
    val archiveProvider = MapTileFileArchiveProvider(receiver, tileSource, archives)
    val approximater = MapTileApproximater().apply { addProvider(archiveProvider) }
    return arrayOf(archiveProvider, approximater)
}
