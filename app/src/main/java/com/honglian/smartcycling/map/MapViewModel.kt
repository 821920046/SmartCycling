package com.honglian.smartcycling.map

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.honglian.smartcycling.SmartCyclingApp
import com.honglian.smartcycling.export.GpxFormat
import com.honglian.smartcycling.offline.GeoTransform
import com.honglian.smartcycling.offline.MapCrs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 地图/路线视图模型:输入目的地 → 定位 → 反查城市 → POI 解析 → 骑行路线规划。
 *
 * 修复点(相对旧实现):
 *  - 旧版 `searchSuggestions` 注释写着"防抖",实际只做 `job.cancel()` 而**没有 delay**,
 *    用户每敲一个字符都会真实发起一次高德请求,既耗电又容易触发配额限流。
 *    现在改为真正的 300ms 防抖。
 *  - 新增 `planning` 状态,供 UI 显示"规划中"并禁用按钮,避免重复提交。
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AMapRouteRepository(app)
    private val container = (app as SmartCyclingApp).container

    /**
     * 纠偏后的位置流,**显式标注为可空**。
     *
     * 必须写成 `Flow<LatLng?>` 而不是让编译器从 `map { LatLng(...) }` 推成 `Flow<LatLng>`:
     * `stateIn` 是 `Flow<T>` 的扩展,T 由**接收者**固定,函数返回类型 `StateFlow<LatLng?>` 不会
     * 反向约束到接收者。若 T 被推成非空 `LatLng`,后面的 `catch { emit(null) }` 与
     * `stateIn(..., null)` 都会报 "Null can not be a value of a non-null type LatLng"。
     *
     * 注意:必须声明在 [currentLatLng] **之前**——Kotlin 类属性按声明顺序初始化,
     * 前置引用会读到未初始化的 null。
     */
    private val correctedLocation: Flow<LatLng?> = container.locationTracker.track()
        .filter { it.isReliable }
        .map {
            val w = GeoTransform.convert(it.latitude, it.longitude, MapCrs.GCJ02, MapCrs.WGS84)
            LatLng(w[0], w[1])
        }

    /**
     * 当前位置(**WGS-84**),用于在**离线底图**上绘制"我的位置"。
     *
     * 关键:定位源([com.honglian.smartcycling.location.LocationTracker])统一走高德定位,
     * 输出 **GCJ-02**;而离线瓦片按 WGS-84 网格渲染,若直接喂进去车标会偏移 300~600m。
     * 因此这里显式做一次 GCJ-02 → WGS-84 纠偏,保证"蓝点"与离线底图对齐。
     *
     * 采用 `WhileSubscribed`:只有地图页真正在观察时才启动定位,离开 5s 后自动停止,不长期耗电。
     * 无定位权限等异常静默降级为"不显示蓝点",不影响其余功能。
     */
    val currentLatLng: StateFlow<LatLng?> = correctedLocation
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _route = MutableStateFlow<List<LatLng>>(emptyList())
    val route: StateFlow<List<LatLng>> = _route.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _startPoint = MutableStateFlow<LatLng?>(null)
    val startPoint: StateFlow<LatLng?> = _startPoint.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _planning = MutableStateFlow(false)
    val planning: StateFlow<Boolean> = _planning.asStateFlow()

    private val _suggestions = MutableStateFlow<List<com.amap.api.services.core.PoiItem>>(emptyList())
    val suggestions: StateFlow<List<com.amap.api.services.core.PoiItem>> = _suggestions.asStateFlow()

    private var suggestionJob: Job? = null

    /** 退出骑行后清空路线/目的地,回到干净的可输入状态。 */
    fun reset() {
        _route.value = emptyList()
        _destination.value = null
        _suggestions.value = emptyList()
        _status.value = ""
        _planning.value = false
        suggestionJob?.cancel()
    }

    /** 实时联想:300ms 防抖 + 取消上一次未完成请求。 */
    fun searchSuggestions(keyword: String) {
        suggestionJob?.cancel()
        if (keyword.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val from = repo.currentPoint() ?: return@launch
            val city = runCatching { repo.cityOf(from) }.getOrDefault("")
            // 若在等待期间用户又输入了新内容,这个协程已被 cancel,不会走到这里。
            _suggestions.value = repo.searchPoiList(keyword, city)
        }
    }

    /** 从联想列表中选择某一项并精确规划。 */
    fun planToPoi(poi: com.amap.api.services.core.PoiItem) {
        val dest = poi.latLonPoint ?: return
        planToPoint(LatLng(dest.latitude, dest.longitude), poi.title ?: "目的地")
    }

    /** 旧接口:直接文字搜索的兜底路径。 */
    fun planTo(destination: String) {
        if (destination.isBlank()) return
        viewModelScope.launch {
            _suggestions.value = emptyList()
            _planning.value = true
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                finish("定位失败,请检查定位权限与 GPS 是否开启")
                return@launch
            }
            _startPoint.value = LatLng(from.latitude, from.longitude)
            _status.value = "搜索目的地…"
            val city = runCatching { repo.cityOf(from) }.getOrDefault("")
            val to = repo.resolveDestination(destination, city)
            if (to == null) {
                finish("未找到「$destination」,换个更具体的名称试试")
                return@launch
            }
            _destination.value = LatLng(to.latitude, to.longitude)
            _status.value = "规划骑行路线…"
            val points = repo.planRide(from, to)
            _route.value = points
            finish(
                if (points.isEmpty()) "未找到骑行路线(请确认高德 Key 已开通搜索与路线服务)"
                else "路线已规划,可以开始骑行",
            )
        }
    }

    private fun planToPoint(dest: LatLng, label: String) {
        viewModelScope.launch {
            _suggestions.value = emptyList()
            _planning.value = true
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                finish("定位失败,请检查定位权限与 GPS 是否开启")
                return@launch
            }
            _startPoint.value = LatLng(from.latitude, from.longitude)
            _destination.value = dest
            _status.value = "规划骑行路线…"
            val points = repo.planRide(from, com.amap.api.services.core.LatLonPoint(dest.latitude, dest.longitude))
            _route.value = points
            finish(
                if (points.isEmpty()) "未找到通往「$label」的骑行路线"
                else "路线已规划,可以开始骑行",
            )
        }
    }

    private fun finish(status: String) {
        _status.value = status
        _planning.value = false
    }

    /**
     * 从用户选定的 GPX 文件导入一条路线并直接用于导航。
     *
     * 坐标系(关键):GPX 是国际格式,内容为 **WGS-84**;而高德底图/导航引擎吃 **GCJ-02**。
     * 因此必须整体做一次 WGS-84 → GCJ-02 纠偏,否则导入的路线会整体偏移 300~600 米,
     * 表现为"路线画在隔壁街道上、导航一路报偏航"。
     */
    fun importRoute(uri: Uri) {
        viewModelScope.launch {
            _suggestions.value = emptyList()
            _planning.value = true
            _status.value = "正在读取 GPX 文件…"

            val track = runCatching {
                val xml = getApplication<Application>().contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    .orEmpty()
                GpxFormat.parse(xml)
            }.getOrNull()

            if (track == null || track.points.size < 2) {
                finish("GPX 解析失败,或轨迹点不足(至少需要 2 个点)")
                return@launch
            }

            val gcj = track.points.map {
                val g = GeoTransform.convert(it.latitude, it.longitude, MapCrs.WGS84, MapCrs.GCJ02)
                LatLng(g[0], g[1])
            }
            _route.value = gcj
            _startPoint.value = gcj.first()
            _destination.value = gcj.last()
            finish("已导入「${track.name}」共 ${gcj.size} 个点,可直接开始骑行")
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
