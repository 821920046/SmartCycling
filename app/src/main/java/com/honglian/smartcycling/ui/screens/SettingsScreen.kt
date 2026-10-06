package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.core.MapSource
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.core.ThemeMode
import com.honglian.smartcycling.core.WheelPreset
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space

/**
 * 设置页:按"骑行档案 / 外观 / 地图数据 / 训练偏好 / 云端同步 / 关于"分组。
 * 所有可编辑项在离开页面时统一落盘,避免每次按键都写 SharedPreferences。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToOfflineMaps: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val wheel by viewModel.wheel.collectAsState()
    val riderName by viewModel.riderName.collectAsState()
    val cloudSyncUrl by viewModel.cloudSyncUrl.collectAsState()
    val cloudSyncToken by viewModel.cloudSyncToken.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val mapSource by viewModel.mapSource.collectAsState()
    val riderWeight by viewModel.riderWeightKg.collectAsState()
    val autoPauseEnabled by viewModel.autoPauseEnabled.collectAsState()
    val autoPauseThreshold by viewModel.autoPauseThresholdKmh.collectAsState()
    val highContrast by viewModel.highContrast.collectAsState()
    val localOnly by viewModel.localOnly.collectAsState()

    var nameInput by remember(riderName) { mutableStateOf(riderName) }
    // 注意:weightInput 不能用 riderWeight 作 remember 键 —— 每次输入都会写回 riderWeight,
    // 用作键会导致输入框被重置、无法连续输入。
    var weightInput by remember { mutableStateOf(riderWeight.toInt().toString()) }
    var urlInput by remember(cloudSyncUrl) { mutableStateOf(cloudSyncUrl) }
    var tokenInput by remember(cloudSyncToken) { mutableStateOf(cloudSyncToken) }
    var showWheelDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun persist() {
        viewModel.updateRiderName(nameInput)
        viewModel.updateCloudSyncUrl(urlInput)
        viewModel.updateCloudSyncToken(tokenInput)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .navigationBarsPadding()
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { persist(); onBack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = palette.textPrimary)
                }
                Text(
                    "设置",
                    style = MaterialTheme.typography.title,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                // ---- 骑行档案 ----
                Section(icon = Icons.Outlined.Person, title = "骑行档案") {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("骑手昵称") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.sm))
                    RowItem(
                        title = wheel.label,
                        subtitle = "车轮周长 ${wheel.circumferenceMm} mm",
                        icon = Icons.Outlined.DirectionsBike,
                        onClick = { showWheelDialog = true },
                    )
                }

                // ---- 外观 ----
                Section(icon = Icons.Outlined.Palette, title = "外观") {
                    RowItem(
                        title = "主题模式",
                        subtitle = when (themeMode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色(户外日光可读性最佳)"
                            ThemeMode.DARK -> "深色(夜骑省电)"
                        },
                        icon = Icons.Outlined.Palette,
                        onClick = { showThemeDialog = true },
                    )
                    Spacer(Modifier.height(Space.sm))
                    SegmentedRow(
                        options = listOf(1 to "标准", 2 to "卫星", 3 to "夜间"),
                        selected = mapType,
                        onSelect = { viewModel.updateMapType(it) },
                        label = "默认地图图层(在线引擎)",
                    )
                    Spacer(Modifier.height(Space.sm))
                    SwitchRow(
                        title = "日照高对比模式",
                        subtitle = "强光下加深仪表盘背景、提升文字对比",
                        checked = highContrast,
                        onCheckedChange = { viewModel.updateHighContrast(it) },
                    )
                }

                // ---- 地图数据 ----
                Section(icon = Icons.Outlined.Map, title = "地图数据") {
                    SegmentedRow(
                        options = listOf(0 to "在线", 1 to "离线"),
                        selected = if (mapSource == MapSource.ONLINE) 0 else 1,
                        onSelect = { viewModel.updateMapSource(if (it == 0) MapSource.ONLINE else MapSource.OFFLINE) },
                        label = "底图引擎",
                    )
                    Spacer(Modifier.height(Space.sm))
                    RowItem(
                        title = "离线地图管理",
                        subtitle = "导入 MBTiles / ZIP / 瓦片文件夹,设置坐标系",
                        icon = Icons.Outlined.Map,
                        onClick = onNavigateToOfflineMaps,
                    )
                }

                // ---- 训练与骑行偏好 ----
                Section(icon = Icons.Outlined.DirectionsBike, title = "训练与骑行偏好") {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            weightInput = it
                            it.toFloatOrNull()?.let { w -> viewModel.updateRiderWeight(w) }
                        },
                        label = { Text("体重 (kg,用于卡路里估算)") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.sm))
                    SwitchRow(
                        title = "自动暂停",
                        subtitle = "静止超过 5 秒自动暂停计时,恢复移动自动继续",
                        checked = autoPauseEnabled,
                        onCheckedChange = { viewModel.updateAutoPauseEnabled(it) },
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "自动暂停阈值:%.1f km/h".format(autoPauseThreshold),
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                    Slider(
                        value = autoPauseThreshold,
                        onValueChange = { viewModel.updateAutoPauseThreshold(it) },
                        valueRange = 0.5f..5f,
                        enabled = autoPauseEnabled,
                    )
                }

                // ---- 云端同步 ----
                Section(icon = Icons.Outlined.Cloud, title = "云端同步(可选)") {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("中控 API 地址") },
                        placeholder = { Text("https://your-worker.workers.dev") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("访问令牌") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "留空则不上传;令牌与服务端 SYNC_TOKEN 一致即可。",
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                    Spacer(Modifier.height(Space.sm))
                    SwitchRow(
                        title = "仅本地模式",
                        subtitle = "开启后骑行记录只存本机,绝不上传云端",
                        checked = localOnly,
                        onCheckedChange = { viewModel.updateLocalOnly(it) },
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "隐私说明:定位与轨迹仅用于导航和骑行统计,默认只保存在本机;仅当你填写上方服务器地址且未开启「仅本地模式」时才会上传。",
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = palette.danger,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text("清空本机全部骑行记录", color = palette.danger)
                    }
                }

                // ---- 关于 ----
                Section(icon = Icons.Outlined.Info, title = "关于") {
                    Text(
                        "智能骑行 SmartCycling · 1.1.0",
                        style = MaterialTheme.typography.body,
                        color = palette.textPrimary,
                    )
                    Text(
                        "在线底图与路线规划由高德提供;离线底图由 osmdroid 渲染(Apache-2.0)。",
                        style = MaterialTheme.typography.caption,
                        color = palette.textTertiary,
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text("清空全部记录?", style = MaterialTheme.typography.title) },
            text = { Text("将删除本机所有骑行记录与轨迹点,操作不可撤销。", style = MaterialTheme.typography.body) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRides()
                    showClearConfirm = false
                }) { Text("清空", color = palette.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = palette.primary) }
            },
        )
    }

    if (showWheelDialog) {
        WheelPickDialog(
            current = wheel,
            onSelect = { viewModel.select(it); showWheelDialog = false },
            onDismiss = { showWheelDialog = false },
        )
    }
    if (showThemeDialog) {
        PickerDialog(
            title = "主题模式",
            options = listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色",
            ),
            current = themeMode,
            onSelect = { viewModel.updateThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }
}

// ---------------------------------------------------------------- 复用组件

@Composable
private fun Section(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AppTheme.palette
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(Space.sm))
            Text(title, style = MaterialTheme.typography.subtitle, color = palette.textPrimary)
        }
        Spacer(Modifier.height(Space.sm))
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Space.md)) { content() }
        }
    }
}

@Composable
private fun RowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.caption, color = palette.textTertiary)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textTertiary)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.caption, color = palette.textTertiary)
        }
        Spacer(Modifier.width(Space.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: String,
) {
    val palette = AppTheme.palette
    Column {
        Text(label, style = MaterialTheme.typography.caption, color = palette.textTertiary)
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    shape = RoundedCornerShape(Radius.md),
                    color = if (isSelected) palette.primaryContainer else Color.Transparent,
                    border = BorderStroke(1.dp, if (isSelected) palette.primary else palette.outline),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(vertical = Space.sm), contentAlignment = Alignment.Center) {
                        Text(
                            text,
                            style = MaterialTheme.typography.label,
                            color = if (isSelected) palette.onPrimaryContainer else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppTheme.palette.primary,
    unfocusedBorderColor = AppTheme.palette.outline,
    focusedTextColor = AppTheme.palette.textPrimary,
    unfocusedTextColor = AppTheme.palette.textPrimary,
)

@Composable
private fun WheelPickDialog(
    current: WheelPreset,
    onSelect: (WheelPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = palette.primary) } },
        title = { Text("车轮周长标定", style = MaterialTheme.typography.title) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                WheelPreset.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == current, onClick = { onSelect(p) })
                        Spacer(Modifier.width(Space.sm))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.body, color = palette.textPrimary)
                            Text(
                                "${p.circumferenceMm} mm",
                                style = MaterialTheme.typography.caption,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消", color = palette.primary) } },
        title = { Text(title, style = MaterialTheme.typography.title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onSelect(value) })
                        Spacer(Modifier.width(Space.sm))
                        Text(label, style = MaterialTheme.typography.body, color = palette.textPrimary)
                    }
                }
            }
        },
    )
}
