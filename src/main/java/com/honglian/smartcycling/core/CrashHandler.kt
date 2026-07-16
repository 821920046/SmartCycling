package com.honglian.smartcycling.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃兜底:把未捕获异常写入本地文件,下次启动时由界面弹窗展示,避免“默默闪退无信息”。
 * 写完日志后仍交给系统默认处理器,保持正常崩溃行为。
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val default = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val text = "时间: $time\n线程: ${t.name}\n\n$sw"
            crashFile(context).writeText(text)
        }
        default?.uncaughtException(t, e)
    }

    companion object {
        private const val FILE_NAME = "last_crash.txt"

        fun crashFile(context: Context): File = File(context.filesDir, FILE_NAME)

        /** 读取并清除上次崩溃日志(若有)。 */
        fun consumeCrashLog(context: Context): String? {
            val f = crashFile(context)
            if (!f.exists()) return null
            return runCatching {
                val s = f.readText()
                f.delete()
                s.ifBlank { null }
            }.getOrNull()
        }
    }
}
