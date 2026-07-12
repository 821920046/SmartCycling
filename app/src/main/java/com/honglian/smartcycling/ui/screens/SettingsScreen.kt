package com.honglian.smartcycling.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.core.SettingsViewModel
import com.honglian.smartcycling.core.WheelPreset
import com.honglian.smartcycling.ui.theme.*

/**
 * 极致高颜值科技 HUD 设置页面。
 * 支持骑手昵称配置、车轮尺寸标定、云服务器定制与地图模式切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wheel by viewModel.wheel.collectAsState()
    val riderName by viewModel.riderName.collectAsState()
    val cloudSyncUrl by viewModel.cloudSyncUrl.collectAsState()
    val cloudSyncToken by viewModel.cloudSyncToken.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val riderWeight by viewModel.riderWeightKg.collectAsState()
    val autoPauseEnabled by viewModel.autoPauseEnabled.collectAsState()
    val autoPauseThreshold by viewModel.autoPauseThresholdKmh.collectAsState()
    val highContrast by viewModel.highContrast.collectAsState()

    var nameInput by remember { mutableStateOf(riderName) }
    var weightInput by remember { mutableStateOf(riderWeight.toInt().toString()) }
    var urlInput by remember { mutableStateOf(cloudSyncUrl) }
    var tokenInput by remember { mutableStateOf(cloudSyncToken) }
    var showWheelDialog by remember { mutableStateOf(false) }

    // 监听输入，并在失焦或返回时自动更新/保存
    fun saveChanges() {
        if (nameInput.isNotBlank()) viewModel.updateRiderName(nameInput)
        viewModel.updateCloudSyncUrl(urlInput)
        viewModel.updateCloudSyncToken(tokenInput)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelBgTop, PanelBgBottom)))
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // 顶栏
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "系统配置中枢", 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp, 
                        color = SpeedText,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { saveChanges(); onBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "返回", 
                            tint = BrandCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 骑手基本信息卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = BrandCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("个人骑行信息", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SpeedText)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("骑手昵称") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandCyan,
                                unfocusedBorderColor = DividerNavy,
                                focusedLabelColor = BrandCyan,
                                unfocusedLabelColor = DataLabel,
                                focusedTextColor = SpeedText,
                                unfocusedTextColor = SpeedText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 2. 物理标定卡片(车轮周长)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = BrandCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("车轮周长标定", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SpeedText)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "基于物理转动计算速度和里程。当前标定:", 
                            color = DataLabel, 
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = { showWheelDialog = true },
                            color = DividerNavy,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(wheel.label, color = SpeedText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("周长: ${wheel.circumferenceMm} mm", color = DataLabel, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ArrowRight, contentDescription = null, tint = BrandCyan)
                            }
                        }
                    }
                }

                // 2b. 训练与骑行偏好(体重/自动暂停)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = BrandCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("训练与骑行偏好", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SpeedText)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = {
                                weightInput = it
                                it.toFloatOrNull()?.let { w -> viewModel.updateRiderWeight(w) }
                            },
                            label = { Text("体重 (kg，用于卡路里估算)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandCyan,
                                unfocusedBorderColor = DividerNavy,
                                focusedLabelColor = BrandCyan,
                                unfocusedLabelColor = DataLabel,
                                focusedTextColor = SpeedText,
                                unfocusedTextColor = SpeedText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自动暂停", color = SpeedText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Switch(
                                checked = autoPauseEnabled,
                                onCheckedChange = { viewModel.updateAutoPauseEnabled(it) }
                            )
                        }
                        Text(
                            "静止超过 5 秒自动暂停计时，恢复移动自动继续。",
                            color = DataLabel,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "触发阈值: %.1f km/h".format(autoPauseThreshold),
                            color = SpeedText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = autoPauseThreshold,
                            onValueChange = { viewModel.updateAutoPauseThreshold(it) },
                            valueRange = 0.5f..5f,
                            enabled = autoPauseEnabled
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("日照高对比模式", color = SpeedText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("强光下加深仪表盘背景、提升文字对比", color = DataLabel, fontSize = 12.sp)
                            }
                            Switch(
                                checked = highContrast,
                                onCheckedChange = { viewModel.updateHighContrast(it) }
                            )
                        }
                    }
                }

                // 3. 地图视觉图层卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = BrandCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("地图显示模式", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SpeedText)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val mapOptions = listOf(
                                1 to "标准图层",
                                2 to "卫星图层",
                                3 to "夜间HUD"
                            )
                            mapOptions.forEach { (type, label) ->
                                val selected = mapType == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) BrandCyan.copy(alpha = 0.2f) else DividerNavy)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) BrandCyan else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.updateMapType(type) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label, 
                                        color = if (selected) BrandCyan else SpeedText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 云同步中控配置卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBg)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = BrandCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("云端数据中控(Cloudflare)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SpeedText)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("中控服务器 API 地址") },
                            placeholder = { Text("https://smart-cycling.xxxx.workers.dev") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandCyan,
                                unfocusedBorderColor = DividerNavy,
                                focusedTextColor = SpeedText,
                                unfocusedTextColor = SpeedText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("云同步安全令牌 (Token)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandCyan,
                                unfocusedBorderColor = DividerNavy,
                                focusedTextColor = SpeedText,
                                unfocusedTextColor = SpeedText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showWheelDialog) {
        WheelCalibrateDialog(
            current = wheel,
            onSelect = { 
                viewModel.select(it)
                showWheelDialog = false 
            },
            onDismiss = { showWheelDialog = false }
        )
    }
}

@Composable
private fun WheelCalibrateDialog(
    current: WheelPreset,
    onSelect: (WheelPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = SpeedText,
        textContentColor = DataLabel,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定", color = BrandCyan, fontWeight = FontWeight.Bold)
            }
        },
        title = { Text("物理轮径参数标定", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                Text(
                    "用于非定位场景下的高精度里程回退积分计算:",
                    fontSize = 12.sp,
                    color = DataLabel,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    WheelPreset.values().forEach { p ->
                        val selected = p == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) BrandCyan.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { onSelect(p) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected, 
                                onClick = { onSelect(p) },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(p.label, fontSize = 15.sp, color = SpeedText, fontWeight = FontWeight.Bold)
                                Text("标定周长: ${p.circumferenceMm} mm", fontSize = 12.sp, color = DataLabel, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    )
}
