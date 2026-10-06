package com.honglian.smartcycling.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honglian.smartcycling.R
import com.honglian.smartcycling.data.OfflineMapEntity
import com.honglian.smartcycling.offline.MapCrs
import com.honglian.smartcycling.offline.OfflineMapFormat
import com.honglian.smartcycling.offline.OfflineMapsViewModel
import com.honglian.smartcycling.ui.theme.AppTheme
import com.honglian.smartcycling.ui.theme.AppType
import com.honglian.smartcycling.ui.theme.Radius
import com.honglian.smartcycling.ui.theme.Space

/**
 * 离线地图管理页:导入、确认坐标系、激活、删除。
 *
 * 交互要点:
 *  - 导入完成后**必须提醒用户确认坐标系**。绝大多数瓦片包不自带 CRS 信息,
 *    而它直接决定车标与底图是否对齐(偏差 300~600m),因此这一项默认 WGS-84、
 *    并显式要求用户核对来源平台。
 */
@Composable
fun OfflineMapsScreen(
    viewModel: OfflineMapsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val maps by viewModel.maps.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val importState by viewModel.importState.collectAsState()

    var pendingDelete by remember { mutableStateOf<OfflineMapEntity?>(null) }
    var editing by remember { mutableStateOf<OfflineMapEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFile) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::importFolder) }

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
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = palette.textPrimary)
                }
                Text(
                    stringResource(R.string.map_nav_offline_maps),
                    style = AppType.title,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                // 导入入口
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !importState.busy,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(stringResource(R.string.offline_import_file), style = AppType.subtitle)
                    }
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        enabled = !importState.busy,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(stringResource(R.string.offline_import_folder), style = AppType.subtitle)
                    }
                }

                if (importState.busy) {
                    Surface(
                        shape = RoundedCornerShape(Radius.lg),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Space.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    importState.label,
                                    style = AppType.body,
                                    color = palette.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::cancelImport) {
                                    Text(stringResource(R.string.action_cancel), color = palette.danger, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (importState.progress > 0f) {
                                LinearProgressIndicator(
                                    progress = { importState.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                if (importState.progress > 0f) {
                                    stringResource(R.string.offline_copying_progress, (importState.progress * 100).toInt())
                                } else {
                                    stringResource(R.string.offline_copying)
                                },
                                style = AppType.caption,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }

                importState.message?.let { msg ->
                    // 三态:错误(红)/ 非致命提醒(琥珀)/ 成功(绿)。
                    // 带 warning 的成功若仍显示绿色对勾,会被用户误读为"完全没问题"。
                    val accent = when {
                        importState.isError -> palette.danger
                        importState.isWarning -> palette.warning
                        else -> palette.success
                    }
                    val accentIcon = when {
                        importState.isError -> Icons.Outlined.ErrorOutline
                        importState.isWarning -> Icons.Outlined.Warning
                        else -> Icons.Outlined.CheckCircle
                    }
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = accent.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = accentIcon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                msg,
                                style = AppType.body,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::dismissMessage) {
                                Text(stringResource(R.string.offline_got_it), color = palette.primary)
                            }
                        }
                    }
                }

                GuideCard()

                if (maps.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.offline_empty),
                            style = AppType.body,
                            color = palette.textTertiary,
                        )
                    }
                }

                maps.forEach { entity ->
                    MapCard(
                        entity = entity,
                        active = entity.id == activeId,
                        missing = viewModel.isMissing(entity),
                        sizeText = viewModel.formatSize(entity.sizeBytes),
                        onActivate = { viewModel.activate(entity.id) },
                        onEdit = { editing = entity },
                        onDelete = { pendingDelete = entity },
                    )
                }

                Spacer(Modifier.height(Space.xl))
            }
        }
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = palette.surface,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text(stringResource(R.string.offline_delete_title), style = AppType.title) },
            text = {
                Text(
                    stringResource(R.string.offline_delete_text, entity.name, viewModel.formatSize(entity.sizeBytes)),
                    style = AppType.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entity.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete), color = palette.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel), color = palette.primary) }
            },
        )
    }

    editing?.let { entity ->
        CrsDialog(
            entity = entity,
            onSelect = { viewModel.updateCrs(entity.id, it) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GuideCard() {
    val palette = AppTheme.palette
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = palette.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(Space.sm))
                Text(
                    stringResource(R.string.offline_guide_title),
                    style = AppType.subtitle,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (expanded) R.string.offline_collapse else R.string.offline_expand),
                    style = AppType.caption,
                    color = palette.primary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(Space.sm))
                val lines = listOf(
                    stringResource(R.string.offline_guide_line1),
                    stringResource(R.string.offline_guide_line2),
                    stringResource(R.string.offline_guide_line3),
                    stringResource(R.string.offline_guide_line4),
                    stringResource(R.string.offline_guide_line5),
                )
                lines.forEach {
                    Text(
                        "· $it",
                        style = AppType.caption,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = Space.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapCard(
    entity: OfflineMapEntity,
    active: Boolean,
    missing: Boolean,
    sizeText: String,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = AppTheme.palette
    val format = OfflineMapFormat.entries.firstOrNull { it.name == entity.format } ?: OfflineMapFormat.UNKNOWN
    val crs = MapCrs.fromName(entity.crs)

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = palette.surface,
        border = BorderStroke(1.dp, if (active) palette.primary else palette.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = active, onClick = onActivate, enabled = !missing)
                Spacer(Modifier.width(Space.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.name,
                        style = AppType.subtitle,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.offline_card_meta, format.label, entity.minZoom, entity.maxZoom, entity.tileCount, sizeText),
                        style = AppType.caption,
                        color = palette.textTertiary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = palette.textTertiary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            if (missing) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(R.string.offline_missing),
                    style = AppType.caption,
                    color = palette.danger,
                )
            }

            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = palette.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.offline_crs), style = AppType.caption, color = palette.textTertiary)
                    Text(
                        crs.label,
                        style = AppType.label,
                        color = palette.textPrimary,
                    )
                }
                Text(stringResource(R.string.offline_edit), style = AppType.caption, color = palette.primary)
            }
        }
    }
}

@Composable
private fun CrsDialog(
    entity: OfflineMapEntity,
    onSelect: (MapCrs) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    val current = MapCrs.fromName(entity.crs)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done), color = palette.primary) } },
        title = { Text(stringResource(R.string.offline_crs_title), style = AppType.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.offline_crs_desc),
                    style = AppType.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(bottom = Space.sm),
                )
                MapCrs.entries.forEach { crs ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(crs) }
                            .padding(vertical = Space.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = crs == current, onClick = { onSelect(crs) })
                        Spacer(Modifier.width(Space.sm))
                        Column {
                            Text(
                                crs.label,
                                style = AppType.body,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                crs.hint,
                                style = AppType.caption,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }
        },
    )
}
