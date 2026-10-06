package com.honglian.smartcycling.offline

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.honglian.smartcycling.data.OfflineMapDao
import com.honglian.smartcycling.data.OfflineMapEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** 导入结果。 */
sealed interface ImportResult {
    /**
     * 导入成功。
     * @param warning 非致命提醒(如"首张瓦片无法解码,包可能已损坏"),需要在 UI 上透出,
     *                否则探测阶段算出来的风险提示会被静默丢弃。
     */
    data class Success(val entity: OfflineMapEntity, val warning: String? = null) : ImportResult
    /** 探测成功但格式/内容不可用(例如 PMTiles、空包)。 */
    data class Rejected(val reason: String, val hint: String? = null) : ImportResult
    /** IO / 权限等硬失败。 */
    data class Failed(val message: String) : ImportResult
}

/**
 * 离线地图仓库:负责"把用户从系统文件选择器挑中的东西变成可渲染的本地地图包"。
 *
 * 第一性原理:SAF 返回的 `content://` URI 是**短期授权**,进程重启或授权失效后就读不到了,
 * 而地图包需要长期稳定可读。因此导入时一律**复制进应用私有目录**
 * (`filesDir/offline_maps/`),这样既拿到稳定的 `File`(osmdroid 的瓦片读取全走 File API),
 * 又不需要任何存储权限。
 *
 * 代价是导入期间会有一次磁盘拷贝 —— 因此拷贝带进度回调、支持取消,并在开始前做空间预检。
 */
class OfflineMapRepository(
    private val context: Context,
    private val dao: OfflineMapDao,
) {

    val root: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun observeAll(): Flow<List<OfflineMapEntity>> = dao.observeAll()

    /**
     * 清理上次导入中断留下的临时文件。
     * 导入过程中进程被杀会残留 `.import-*.tmp`,它们既占空间又会被误认为地图包,因此启动时扫一遍。
     */
    suspend fun cleanupStaleImports() = withContext(Dispatchers.IO) {
        runCatching {
            root.listFiles { f -> f.isFile && f.name.startsWith(TEMP_PREFIX) }?.forEach { it.delete() }
        }
    }

    suspend fun findById(id: Long): OfflineMapEntity? = dao.findById(id)

    /** 解析实体对应的真实文件/目录。 */
    fun fileOf(entity: OfflineMapEntity): File = File(root, entity.relativePath)

    /** 把注册表记录转成渲染层需要的描述。 */
    fun layerSpecOf(entity: OfflineMapEntity): OfflineLayerSpec = OfflineLayerSpec(
        path = fileOf(entity),
        isDirectory = entity.isDirectory,
        format = OfflineMapFormat.entries.firstOrNull { it.name == entity.format }
            ?: OfflineMapFormat.UNKNOWN,
        crs = MapCrs.fromName(entity.crs),
        minZoom = entity.minZoom,
        maxZoom = entity.maxZoom,
        tileSize = entity.tileSize,
        name = entity.name,
        bounds = entity.bounds,
    )

    /** 包是否存在且可读(用于"文件被外部清理"后的自愈提示)。 */
    fun exists(entity: OfflineMapEntity): Boolean = fileOf(entity).exists()

    // ------------------------------------------------------------------ 导入

    /**
     * 从 SAF URI 导入一个**文件型**地图包(MBTiles / ZIP / SQLite / GeoPackage / PMTiles)。
     * @param onProgress 0f..1f,仅在复制阶段回调;探测阶段可能长时间停在 1f。
     */
    suspend fun importFile(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "offline_map"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val temp = File(root, "$TEMP_PREFIX${System.currentTimeMillis()}.tmp")
        var target: File? = null

        try {
            val declaredSize = querySize(uri)
            if (declaredSize > 0 && declaredSize > freeSpace() - SAFETY_MARGIN) {
                return@withContext ImportResult.Failed(
                    "磁盘空间不足:需要约 ${formatSize(declaredSize)},可用 ${formatSize(freeSpace())}",
                )
            }
            copyUriToFile(uri, temp, declaredSize, onProgress)

            val probe = OfflineMapInspector.inspect(temp)
            probe.fatal?.let {
                temp.delete()
                return@withContext ImportResult.Rejected(it, probe.warning)
            }

            val finalExt = if (probe.format == OfflineMapFormat.UNKNOWN) ext else probe.format.extension
            val dest = uniqueTarget(displayName.substringBeforeLast('.'), finalExt)
            target = dest
            if (!temp.renameTo(dest)) {
                // rename 跨分区可能失败 → 退化为拷贝
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }

            ImportResult.Success(
                entity = persist(dest, probe, isDirectory = false, sourceName = displayName),
                warning = probe.warning,
            )
        } catch (ce: CancellationException) {
            // 用户主动取消:清掉半成品后原样抛出,交由上层静默处理(绝不能当成"导入失败")。
            temp.delete()
            target?.delete()
            throw ce
        } catch (t: Throwable) {
            temp.delete()
            target?.delete()
            ImportResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * 从 SAF 目录 URI 导入**文件夹型**瓦片包。
     * 递归复制整棵目录树到应用私有目录。
     */
    suspend fun importFolder(
        treeUri: Uri,
        onProgress: (Float) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.IO) {
        var target: File? = null
        try {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext ImportResult.Failed("无法访问所选文件夹")
            val baseName = doc.name?.takeIf { it.isNotBlank() } ?: "tiles"
            val dest = uniqueTarget(baseName, "")
            target = dest

            val files = doc.listFiles()
            if (files.isEmpty()) return@withContext ImportResult.Rejected("所选文件夹为空")
            if (!dest.mkdirs() && !dest.isDirectory) {
                return@withContext ImportResult.Failed("无法创建目标目录")
            }

            // 递归统计文件总数:进度条按"已拷文件/总文件"平滑推进,而非按顶层条目跳变。
            val total = countFiles(doc).coerceAtLeast(1)
            val progress = CopyProgress()
            for ((index, child) in files.withIndex()) {
                copyDocument(child, File(dest, child.name ?: "item-$index"), progress, total, onProgress)
            }

            val probe = OfflineMapInspector.inspect(dest)
            probe.fatal?.let {
                dest.deleteRecursively()
                return@withContext ImportResult.Rejected(it, probe.warning)
            }
            ImportResult.Success(
                entity = persist(dest, probe, isDirectory = true, sourceName = baseName),
                warning = probe.warning,
            )
        } catch (ce: CancellationException) {
            target?.deleteRecursively()
            throw ce
        } catch (t: Throwable) {
            target?.deleteRecursively()
            ImportResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------ 维护

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.findById(id) ?: return@withContext false
        val file = fileOf(entity)
        val removed = runCatching {
            if (entity.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
        dao.delete(id)
        removed
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        if (trimmed.isNotBlank()) dao.rename(id, trimmed)
    }

    suspend fun updateCrs(id: Long, crs: MapCrs) = withContext(Dispatchers.IO) {
        dao.updateCrs(id, crs.name)
    }

    // ------------------------------------------------------------------ 内部

    private suspend fun persist(
        target: File,
        probe: OfflineMapProbe,
        isDirectory: Boolean,
        sourceName: String,
    ): OfflineMapEntity {
        val entity = OfflineMapEntity(
            name = probe.declaredName?.takeIf { it.isNotBlank() } ?: target.nameWithoutExtension,
            relativePath = target.name,
            isDirectory = isDirectory,
            format = probe.format.name,
            // 默认给最保守的 WGS-84,导入后在管理页由用户确认(GCJ/BD 包需手动改)。
            crs = MapCrs.WGS84.name,
            minZoom = probe.minZoom,
            maxZoom = probe.maxZoom,
            tileSize = probe.tileSize,
            tileCount = probe.tileCount,
            sizeBytes = if (isDirectory) dirSize(target) else target.length(),
            bounds = probe.bounds,
            sourceName = sourceName,
            importedAt = System.currentTimeMillis(),
        )
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    private suspend fun copyUriToFile(uri: Uri, dest: File, declaredSize: Long, onProgress: (Float) -> Unit) {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取所选文件")
        input.use { ins ->
            dest.outputStream().buffered(BUFFER).use { outs ->
                val buf = ByteArray(BUFFER)
                var copied = 0L
                var lastPct = -1
                while (true) {
                    // 每个缓冲区检查一次取消:几 GB 的包也能即时响应"取消导入"。
                    currentCoroutineContext().ensureActive()
                    val n = ins.read(buf)
                    if (n <= 0) break
                    outs.write(buf, 0, n)
                    copied += n
                    if (declaredSize > 0) {
                        val pct = ((copied * 100) / declaredSize).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress((copied.toFloat() / declaredSize).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        onProgress(1f)
    }

    /** 递归统计目录树内的文件数(用于进度条)。 */
    private fun countFiles(doc: DocumentFile): Int {
        if (!doc.isDirectory) return 1
        var n = 0
        for (child in doc.listFiles()) n += countFiles(child)
        return n
    }

    /** 拷贝进度计数器。 */
    private class CopyProgress(var done: Int = 0)

    private suspend fun copyDocument(
        doc: DocumentFile,
        dest: File,
        progress: CopyProgress,
        total: Int,
        onProgress: (Float) -> Unit,
    ) {
        // 每个条目开始前检查取消:大目录也能及时中止。
        currentCoroutineContext().ensureActive()
        if (doc.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            for (child in doc.listFiles()) {
                copyDocument(child, File(dest, child.name ?: "item"), progress, total, onProgress)
            }
            return
        }
        val input = context.contentResolver.openInputStream(doc.uri) ?: return
        input.use { ins ->
            dest.outputStream().buffered(BUFFER).use { outs -> ins.copyTo(outs, BUFFER) }
        }
        progress.done++
        onProgress(progress.done.toFloat() / total)
    }

    /** 生成不冲突的目标文件;`base` 已被清洗为安全字符。 */
    private fun uniqueTarget(base: String, ext: String): File {
        val safe = sanitize(base).ifBlank { "offline_map" }.take(48)
        val suffix = if (ext.isBlank()) "" else ".$ext"
        var candidate = File(root, "$safe$suffix")
        var i = 1
        while (candidate.exists()) {
            candidate = File(root, "$safe-$i$suffix")
            i++
        }
        return candidate
    }

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").trim().trimEnd('.')

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun freeSpace(): Long = runCatching { root.usableSpace }.getOrDefault(Long.MAX_VALUE)

    private fun dirSize(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    companion object {
        private const val DIR = "offline_maps"
        private const val TEMP_PREFIX = ".import-"
        private const val BUFFER = 256 * 1024
        /** 预留 200MB 余量,避免复制到一半把系统盘写满。 */
        private const val SAFETY_MARGIN = 200L * 1024 * 1024

        fun formatSize(bytes: Long): String = when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
            else -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
        }
    }
}
