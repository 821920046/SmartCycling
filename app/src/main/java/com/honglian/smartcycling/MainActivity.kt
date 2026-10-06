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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honglian.smartcycling.core.CrashHandler
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.nav.AppNav
import com.honglian.smartcycling.ride.RideService
import com.honglian.smartcycling.ui.theme.SmartCyclingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动页(展示 logo);必须在 super.onCreate 之前安装
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 注意:此处**不再**全局 addFlags(FLAG_KEEP_SCREEN_ON)。
        // 全局常亮会让"浏览历史 / 翻设置"时屏幕也永不熄灭,纯属耗电;
        // 改为仅在进入骑行页时按用户设置开启,退出骑行立即清除(见下方 onEnterRide / onExitRide)。
        // 默认方向:跟随手机(竖放竖屏、横放横屏)。
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        val crashLog = CrashHandler.consumeCrashLog(this)

        setContent {
            // 主题模式由设置页驱动:Activity 级 ViewModel 实例与 AppNav 内共用同一对象。
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            // 骑行期行为偏好:是否常亮、是否锁定方向。随设置页改动即时生效。
            val keepScreenOn by settingsViewModel.keepScreenOn.collectAsState()
            val lockOrientation by settingsViewModel.lockOrientation.collectAsState()
            // 单位制:与主题同级,注入到 SmartCyclingTheme 后由 CompositionLocal 下发到各界面。
            val unitSystem by settingsViewModel.unitSystem.collectAsState()

            SmartCyclingTheme(themeMode = themeMode, unitSystem = unitSystem) {
                var crash by remember { mutableStateOf(crashLog) }
                AppNav(
                    onPaired = {
                        // 配对页跟随手机方向自适应(默认竖屏，横放自动横屏)
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    },
                    onEnterRide = {
                        // 骑行页:按设置决定方向策略 —— 锁定进入瞬间的方向(防颠簸误转)或跟随手机。
                        requestedOrientation = if (lockOrientation) {
                            ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        }
                        // 仅骑行期间保持常亮,方便随时瞥一眼仪表盘。
                        if (keepScreenOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        startRideService()
                    },
                    onExitRide = {
                        // 退出骑行:恢复方向自适应并清除常亮(避免后台常亮耗电)。
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        title = { Text(stringResource(R.string.crash_dialog_title)) },
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
            }) { Text(stringResource(R.string.crash_copy_log)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
