package com.honglian.smartcycling.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * GPX 落盘 / 分享的 Android 胶水层。
 *
 * 两条出口,覆盖不同使用场景(参考 OpenTracks 的导出体验):
 *  - [writeToUri] —— SAF(`ACTION_CREATE_DOCUMENT`),用户自选保存位置,适合"导出备份"。
 *  - [shareGpx]  —— `ACTION_SEND` + FileProvider,直接分享给 Strava / Komoot / 微信 / 邮件。
 */
object RideExporter {

    private const val CACHE_DIR = "gpx"
    const val MIME_GPX = "application/gpx+xml"

    fun fileName(startedAtMs: Long): String = RideGpx.defaultName(startedAtMs) + ".gpx"

    /** 写入用户通过 SAF 选定的 Uri。返回是否成功。 */
    fun writeToUri(context: Context, uri: Uri, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        } != null
    }.getOrDefault(false)

    /**
     * 写入应用缓存目录并通过 FileProvider 分享。
     * 必须在清单里声明 `${applicationId}.fileprovider` 与 `res/xml/file_paths.xml`。
     */
    fun shareGpx(context: Context, fileName: String, content: String): Boolean = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_GPX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "分享 GPX 轨迹")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
