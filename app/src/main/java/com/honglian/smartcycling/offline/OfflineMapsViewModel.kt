package com.honglian.smartcycling.offline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.data.OfflineMapEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 导入过程的 UI 状态。 */
data class ImportUiState(
    val busy: Boolean = false,
    val progress: Float = 0f,
    val label: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    /** 非致命提醒(导入成功但有隐患):用琥珀色而非绿色呈现,避免"带病成功"被误读为完全正常。 */
    val isWarning: Boolean = false,
)

/**
 * 离线地图管理视图模型。
 *
 * 并发约束:导入是重 IO + 大文件拷贝,**同一时刻只允许一个导入任务**。
 * 因此用一个 [importJob] 做互斥,重复点击直接忽略,避免两个任务同时往同一目录写。
 */
class OfflineMapsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartCyclingApp).container
    private val repo = container.offlineMapRepository
    private val settings = container.settings

    val maps: StateFlow<List<OfflineMapEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    // 与 SettingsViewModel 共用同一条流:无论哪一方激活/取消激活,两边都立即同步。
    val activeId: StateFlow<Long> = settings.activeOfflineMapIdFlow

    private var importJob: Job? = null

    init {
        // 清理上次导入中断留下的临时文件
        viewModelScope.launch { repo.cleanupStaleImports() }
    }

    fun importFile(uri: Uri) = runImport("正在导入文件…") { onProgress ->
        repo.importFile(uri, onProgress)
    }

    fun importFolder(uri: Uri) = runImport("正在导入文件夹…") { onProgress ->
        repo.importFolder(uri, onProgress)
    }

    private fun runImport(label: String, block: suspend ((Float) -> Unit) -> ImportResult) {
        if (importJob?.isActive == true) return
        _importState.value = ImportUiState(busy = true, progress = 0f, label = label)
        importJob = viewModelScope.launch {
            val result = try {
                block { p -> _importState.value = _importState.value.copy(progress = p) }
            } catch (ce: CancellationException) {
                // 用户主动取消:清掉"进行中"状态并原样抛出,绝不能显示成"导入失败"。
                _importState.value = ImportUiState(message = "已取消导入")
                throw ce
            } catch (t: Throwable) {
                ImportResult.Failed(t.message ?: t.javaClass.simpleName)
            }

            _importState.value = when (result) {
                is ImportResult.Success -> ImportUiState(
                    busy = false,
                    progress = 1f,
                    // 探测阶段的非致命提醒(如"首张瓦片无法解码")必须一并透出,否则被静默吞掉。
                    message = buildString {
                        append("已导入「${result.entity.name}」,请在列表中确认坐标系")
                        result.warning?.let { append(" · 注意:").append(it) }
                    },
                    isError = false,
                    isWarning = result.warning != null,
                )
                is ImportResult.Rejected -> ImportUiState(
                    busy = false,
                    message = buildString {
                        append(result.reason)
                        result.hint?.let { append(" · ").append(it) }
                    },
                    isError = true,
                )
                is ImportResult.Failed -> ImportUiState(
                    busy = false,
                    message = "导入失败:${result.message}",
                    isError = true,
                )
            }
            // 首个包导入成功后自动激活,省去用户手动切换
            if (result is ImportResult.Success && settings.activeOfflineMapId == 0L) {
                activate(result.entity.id)
            }
        }
    }

    fun dismissMessage() {
        _importState.value = _importState.value.copy(message = null, isError = false, isWarning = false)
    }

    /** 取消正在进行的导入(半成品文件的清理由仓库负责)。 */
    fun cancelImport() {
        importJob?.cancel()
    }

    fun activate(id: Long) {
        settings.activeOfflineMapId = id
        settings.mapSource = com.honglian.smartcycling.core.MapSource.OFFLINE
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            if (settings.activeOfflineMapId == id) {
                settings.activeOfflineMapId = 0L
            }
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun updateCrs(id: Long, crs: MapCrs) {
        viewModelScope.launch { repo.updateCrs(id, crs) }
    }

    fun isMissing(entity: OfflineMapEntity): Boolean = !repo.exists(entity)

    fun layerSpecOf(entity: OfflineMapEntity): OfflineLayerSpec = repo.layerSpecOf(entity)

    fun formatSize(bytes: Long): String = OfflineMapRepository.formatSize(bytes)
}
