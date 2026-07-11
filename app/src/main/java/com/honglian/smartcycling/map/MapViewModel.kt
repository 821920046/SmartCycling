package com.honglian.smartcycling.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 地图/路线视图模型:输入目的地 → 定位→反查城市→POI解析→骑行路线规划。
 * route 用于预览绘线;destination 用于启动 turn-by-turn 导航;
 * startPoint 为真实当前位置,作为导航算路起点(避免导航默认落到北京)。
 * 三者在 MapScreen 与 RideScreen 之间共享(同一 Activity 作用域)。
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AMapRouteRepository(app)

    private val _route = MutableStateFlow<List<LatLng>>(emptyList())
    val route: StateFlow<List<LatLng>> = _route.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _startPoint = MutableStateFlow<LatLng?>(null)
    val startPoint: StateFlow<LatLng?> = _startPoint.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _suggestions = MutableStateFlow<List<com.amap.api.services.core.PoiItem>>(emptyList())
    val suggestions: StateFlow<List<com.amap.api.services.core.PoiItem>> = _suggestions.asStateFlow()

    /** 防抖:每次新关键字进来取消上一次未发出的搜索 */
    private var suggestionJob: Job? = null

    /** 退出骑行后清空上一次的路线/目的地,回到干净的可输入状态(保留起点定位缓存)。 */
    fun reset() {
        _route.value = emptyList()
        _destination.value = null
        _suggestions.value = emptyList()
        _status.value = ""
        suggestionJob?.cancel()
    }

    /** 实时搜索联想词(带防抖:每次新关键字取消上一次未发出的搜索)。 */
    fun searchSuggestions(keyword: String) {
        suggestionJob?.cancel()
        if (keyword.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            val debounced = keyword // 取消机制已替代实际 sleep,但保留结构供后续增加 300ms 延迟
            val from = repo.currentPoint() ?: return@launch
            val city = runCatching { repo.cityOf(from) }.getOrDefault("")
            val pois = repo.searchPoiList(debounced, city)
            _suggestions.value = pois
        }
    }

    /** 从联想词列表中选择某一项并发起精确规划 */
    fun planToPoi(poi: com.amap.api.services.core.PoiItem) {
        val dest = poi.latLonPoint ?: return
        viewModelScope.launch {
            _suggestions.value = emptyList() // 清空下拉
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                _status.value = "定位失败,请检查定位权限/GPS"
                return@launch
            }
            _startPoint.value = LatLng(from.latitude, from.longitude)
            _destination.value = LatLng(dest.latitude, dest.longitude)
            
            _status.value = "规划骑行路线…"
            val points = repo.planRide(from, dest)
            _route.value = points
            _status.value = if (points.isEmpty()) {
                "未找到骑行路线(请确认高德Key已开通搜索/路线服务)"
            } else {
                "路线已规划,可开始导航"
            }
        }
    }

    /** 旧接口(直接文字强搜), 保留用于用户不点下拉直接点搜索的 fallback */
    fun planTo(destination: String) {
        if (destination.isBlank()) return
        viewModelScope.launch {
            _suggestions.value = emptyList()
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                _status.value = "定位失败,请检查定位权限/GPS"
                return@launch
            }
            // 保存真实当前位置作为导航起点(避免导航默认落到北京)
            _startPoint.value = LatLng(from.latitude, from.longitude)
            _status.value = "搜索目的地…"
            val city = runCatching { repo.cityOf(from) }.getOrDefault("")
            val to = repo.resolveDestination(destination, city)
            if (to == null) {
                _status.value = "未找到“$destination”,请换个更具体的名称"
                return@launch
            }
            // 保存目的地,供导航使用
            _destination.value = LatLng(to.latitude, to.longitude)
            _status.value = "规划骑行路线…"
            val points = repo.planRide(from, to)
            _route.value = points
            _status.value = if (points.isEmpty()) {
                "未找到骑行路线(请确认高德Key已开通搜索/路线服务)"
            } else {
                "路线已规划,可开始导航"
            }
        }
    }
}
