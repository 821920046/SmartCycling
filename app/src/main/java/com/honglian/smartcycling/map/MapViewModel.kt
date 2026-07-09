package com.honglian.smartcycling.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 地图/路线视图模型:输入目的地 → 定位→反查城市→POI解析→骑行路线规划。
 * route 用于预览绘线;destination 用于启动 turn-by-turn 导航。
 * 两者在 MapScreen 与 RideScreen 之间共享(同一 Activity 作用域)。
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AMapRouteRepository(app)

    private val _route = MutableStateFlow<List<LatLng>>(emptyList())
    val route: StateFlow<List<LatLng>> = _route.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun planTo(destination: String) {
        if (destination.isBlank()) return
        viewModelScope.launch {
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                _status.value = "定位失败,请检查定位权限/GPS"
                return@launch
            }
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
