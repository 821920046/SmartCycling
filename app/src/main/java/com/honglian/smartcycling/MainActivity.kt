package com.honglian.smartcycling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.honglian.smartcycling.core.CrashHandler
import com.honglian.smartcycling.nav.AppNav
import com.honglian.smartcycling.ride.RideService
import com.honglian.smartcycling.ui.theme.SmartCyclingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动页(展示 logo);必须在 super.onCreate 之前安装
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val crashLog = CrashHandler.consumeCrashLog(this)

        setContent {
            SmartCyclingTheme {
                var crash by remember { mutableStateOf(crashLog) }
                AppNav(
                    onPaired = {
                        // 配对完成进入地图即锁定横屏,方便横屏输入目的地与导航
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },
                    onEnterRide = {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        startRideService()
                    },
                    onExitRide = {
                        // 退出骑行回到地图,保持横屏(方便继续换目的地),仅停止前台服务
                        stopRideService()
                    },
                )
                crash?.let { log ->
                    CrashDialog(log = log, onDismiss = { crash = null })
                }
            }
        }
    }

    private fun startRideService() {
        // 包一层兑底:即使启动前台服务抛异常,也不影响进入骑行界面。
        runCatching {
            val intent = Intent(this, RideService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun stopRideService() {
        stopService(Intent(this, RideService::class.java))
    }
}

/** 上次崩溃提示弹窗:展示堆栈并可一键复制,方便定位闪退。 */
@androidx.compose.runtime.Composable
private fun CrashDialog(log: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次运行发生崩溃") },
        text = {
            Text(
                text = log,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", log))
                onDismiss()
            }) { Text("复制日志") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
