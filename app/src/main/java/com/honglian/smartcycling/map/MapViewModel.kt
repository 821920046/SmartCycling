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
 * 地图/路线视图模型:输入目的地 → 定位→地理编码→骑行路线规划。
 * 路线结果在 MapScreen 与 RideScreen 之间共享(同一 Activity 作用域)。
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AMapRouteRepository(app)

    private val _route = MutableStateFlow<List<LatLng>>(emptyList())
    val route: StateFlow<List<LatLng>> = _route.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun planTo(destination: String) {
        if (destination.isBlank()) return
        viewModelScope.launch {
            _status.value = "定位中…"
            val from = repo.currentPoint()
            if (from == null) {
                _status.value = "定位失败,请检查定位权限"
                return@launch
            }
            _status.value = "搜索目的地…"
            val to = repo.geocode(destination)
            if (to == null) {
                _status.value = "未找到目的地"
                return@launch
            }
            _status.value = "规划骑行路线…"
            val points = repo.planRide(from, to)
            _route.value = points
            _status.value = if (points.isEmpty()) "未找到骑行路线" else "路线已规划"
        }
    }
}
