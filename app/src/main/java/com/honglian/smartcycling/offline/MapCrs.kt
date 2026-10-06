package com.honglian.smartcycling.offline

/**
 * 离线地图包的坐标系。
 *
 * 这是导入流程里**必须由用户确认**的一项元数据:绝大多数瓦片包自身不携带坐标系信息,
 * 而它决定了车标/轨迹能否与底图对齐(误差 300~600m 量级)。
 */
enum class MapCrs(val label: String, val hint: String) {
    WGS84(
        label = "WGS-84 · 国际标准",
        hint = "OpenStreetMap / Google(海外) / Mapbox / 天地图卫星 等",
    ),
    GCJ02(
        label = "GCJ-02 · 火星坐标",
        hint = "高德 / 腾讯 / Google 中国 / 国内大多数在线瓦片",
    ),
    BD09(
        label = "BD-09 · 百度坐标",
        hint = "百度地图全系瓦片",
    );

    companion object {
        /** 从持久化的字符串安全还原,未知值回退 WGS-84(最保守的国际标准)。 */
        fun fromName(name: String?): MapCrs =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WGS84
    }
}

/** 支持的离线地图包容器格式。 */
enum class OfflineMapFormat(val label: String, val extension: String) {
    /** MBTiles 1.x:SQLite,表 tiles(zoom_level, tile_column, tile_row, tile_data)。兼容面最广。 */
    MBTILES("MBTiles 瓦片包", "mbtiles"),

    /** 纯 SQLite(osmdroid 原生格式):tiles(key, provider, tile)。 */
    SQLITE("osmdroid SQLite", "sqlite"),

    /** ZIP 包,内部为 <任意顶层目录>/<z>/<x>/<y>.png。 */
    ZIP("ZIP 瓦片包", "zip"),

    /** 解压后的瓦片目录,结构 <z>/<x>/<y>.png|jpg|webp。 */
    FOLDER("瓦片文件夹", "dir"),

    /** GeoPackage 栅格:SQLite + gpkg_contents(gpkg_tile_matrix)。 */
    GEOPACKAGE("GeoPackage 栅格", "gpkg"),

    /** PMTiles 单文件:头部魔数 "PMTiles"。 */
    PMTILES("PMTiles 单文件", "pmtiles"),

    /** 未能识别。 */
    UNKNOWN("未知格式", "bin"),
}

/** 瓦片图片扩展名(用于文件夹/ZIP 扫描)。 */
internal val TILE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")

/**
 * 矢量瓦片格式标识。
 *
 * 这些是 protobuf 编码的矢量切片(pbf/mvt),必须由矢量渲染引擎(如 MapLibre)解释样式后才能出图。
 * osmdroid 是**纯栅格**管线,遇到它们只会渲染出空白,因此在导入阶段直接拒绝,给出明确指引。
 */
internal val VECTOR_FORMATS = setOf("pbf", "mvt", "vector", "protobuf")
