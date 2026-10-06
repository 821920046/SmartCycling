<p align="center">
  <img src="docs/logo.png" alt="智能骑行 SmartCycling" width="200" />
</p>

<h1 align="center">智能骑行 SmartCycling</h1>

<p align="center">
  面向安卓的智能骑行 App · 自动配对传感器 · 在线/离线双地图引擎 · 实时骑行数据
</p>

<p align="center">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-3ddc84" />
  <img alt="language" src="https://img.shields.io/badge/language-Kotlin-7f52ff" />
  <img alt="UI" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285f4" />
  <img alt="offline" src="https://img.shields.io/badge/offline-osmdroid%20MBTiles-1b6ef3" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-blue" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-green" />
</p>

---

开屏自动配对**迈金 S314** 蓝牙速度/踏频传感器 → 进入地图 → 输入目的地开骑 → 横屏数据界面(左导航 / 右仪表盘)。
支持**导入各大平台的离线地图**并在无网环境下导航。

## 核心能力

- 🗺️ **双地图引擎** — 在线走高德(路线规划 / POI 搜索 / 语音播报),离线走 osmdroid 本地瓦片,一键切换
- 📦 **离线地图导入** — 支持 MBTiles / ZIP 瓦片包 / `{z}/{x}/{y}` 瓦片文件夹 / osmdroid SQLite,并自动识别元数据
- 🧭 **坐标系纠偏** — 内建 WGS-84 / GCJ-02 / BD-09 互转,导入高德、腾讯、百度瓦片也能与 GPS 精准对齐
- 🤖 **智能分析** — 速度 / 踏频 / 里程 / 时长 / 均速实时计算与历史统计
- 🎨 **双主题设计系统** — 浅色为主(户外日光可读),深色适配夜骑,可跟随系统
- 🛡️ **安全守护** — 前台服务锁屏保活,骑行中数据不中断

## 技术栈

- Kotlin 1.9 + Jetpack Compose + Material3
- MVVM + Repository + 轻量 DI(`core/Container`)
- Nordic Android-BLE-Library(`ble-ktx`)接入 CSC(0x1816 / 0x2A5B)
- FusedLocationProvider 定位与里程积分
- Room 本地存储骑行记录、轨迹与离线地图注册表
- **osmdroid 6.1.20** 离线栅格瓦片渲染(Apache-2.0)
- Foreground Service 锁屏保活

## 目录结构

```
app/src/main/java/com/honglian/smartcycling/
├─ SmartCyclingApp.kt        # Application + 通知渠道 + osmdroid 初始化
├─ MainActivity.kt           # 权限、自动配对、横屏切换、前台服务、主题注入
├─ core/                     # 轻量依赖容器 / 设置 / 主题模式 / 车轮预设
├─ ble/                      # CSC UUID / 解析 / 计算 / S314 BleManager
├─ pairing/                  # 扫描与自动配对
├─ location/                 # GPS 轨迹与速度
├─ data/                     # Room 实体 / DAO / 仓库(含离线地图注册表)
├─ ride/                     # RideState / RideViewModel / RideService
├─ map/                      # 高德在线引擎(搜索 / 反查 / 路径规划)
├─ offline/                  # 离线引擎:坐标变换 / 格式探测 / 瓦片源 / Compose 视图
├─ ui/theme/                 # 设计系统:色板 / 排版 / 间距 / 形状
├─ ui/components/            # SpeedRing / DataGrid / 地图视图
├─ ui/screens/               # Pairing / Map / Ride / History / Settings / OfflineMaps
└─ nav/AppNav.kt             # 配对→地图→骑行→设置/历史/离线地图
```

## 离线地图使用指南

### 1. 支持哪些格式

| 格式 | 扩展名 | 说明 |
|---|---|---|
| MBTiles | `.mbtiles` | **推荐**。SQLite 容器,兼容面最广 |
| ZIP 瓦片包 | `.zip` | 内部为 `<任意顶层目录>/<z>/<x>/<y>.png` |
| 瓦片文件夹 | 目录 | `<z>/<x>/<y>.png\|jpg\|webp`,可直接导入解压后的目录 |
| osmdroid SQLite | `.sqlite` / `.db` | osmdroid 原生 `tiles(key, provider, tile)` |
| GeoPackage | `.gpkg` | 可识别但暂不渲染,请先转 MBTiles |
| PMTiles | `.pmtiles` | 可识别但暂不渲染,请先转 MBTiles |

### 2. 如何获取瓦片包

用 **SAS.Planet** / **Mobile Atlas Creator (MOBAC)** / **QGIS** 选择任意平台
(高德、腾讯、百度、Google、OSM、Mapbox)导出为 **MBTiles** 或瓦片目录,再通过
「离线地图 → 导入文件 / 导入文件夹」载入。

> 瓦片数据请自行确保合法授权。本应用只做**本地渲染**,不提供任何地图数据下载能力。

### 3. 坐标系必须核对

同一个物理地点在不同平台的经纬度数字并不相同:

| 平台 | 坐标系 |
|---|---|
| OSM / Google(海外) / Mapbox | **WGS-84** |
| 高德 / 腾讯 / Google 中国 | **GCJ-02**(火星坐标) |
| 百度 | **BD-09** |

导入后默认按 WGS-84 处理,**请务必在列表条目上点「坐标系」改为实际来源**。
选错会导致车标与底图整体偏移 300~600 米。

### 4. 坐标系是怎么被修正的

`offline/CrsRemapArchiveFile` 在**瓦片读取层**做一次精确换算:
把请求的 WGS-84 瓦片中心转成包坐标系经纬度,再反查包内对应位置的瓦片返回。
这样 osmdroid 始终工作在标准 WGS-84 网格上,相机、覆盖物、GPS 三者天然自洽,
残余误差仅为偏移场在一张瓦片内的变化(亚米级)。

> 为什么不用"自定义投影"?因为 GCJ-02 偏移量是经纬度**二元耦合**的,而
> osmdroid 的 `TileSystem` 接口是**一维可分离**的,强行套用只能取常数近似,
> 误差可达数十米。详见 `REVIEW.md`。

## 构建

> 本仓未附带 `gradle-wrapper.jar`(二进制)。首次构建任选一:
>
> 1. **用 Android Studio 打开**(Ladybug 及以上),IDE 会自动生成 wrapper 并同步;或
> 2. 本地已装 Gradle 8.9 则执行:`gradle wrapper --gradle-version 8.9`,之后 `./gradlew assembleDebug`。

环境要求:JDK 17、Android SDK 34、minSdk 26。

高德 Key 通过 `-PAMAP_KEY=xxx` 或 `gradle.properties` 注入。

## 关键工程决策(第一性原理)

1. **单个 S314 同一时刻只能测速度或踏频。** 速度来源自适应:3 秒内有轮转数据用传感器,否则回退 GPS。
2. **时间戳 uint16 会在 65536 翻转**,`CscCalculator` 均取模修正。
3. **看门狗机制**:任一数据源 3 秒无更新则归零,避免"停车但速度不降"。
4. **里程优先 GPS 积分**(过滤 >25m 精度与 <1m 静止漂移);无定位时用轮转圈数 × 轮周长回退。
5. **离线地图必须换引擎**:高德 SDK 封闭、不开放自定义瓦片源,因此离线能力只能由独立开源引擎承载。
6. **坐标系在瓦片层纠偏**,而非投影层(见上)。

## 开源合规

本项目采用 MIT。接入第三方代码时需避开 GPL/AGPL 传染性许可(如 pizero_bikecomputer 为 GPL-3.0),仅作算法参考。
osmdroid 为 Apache-2.0,可安全商用。

## 对抗审查

见 `REVIEW.md`。
