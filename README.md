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
支持**导入各大平台的离线地图**并在无网环境下导航,支持**心率带**与**GPX 轨迹导入导出**。

## 核心能力

- 🗺️ **双地图引擎** — 在线走高德(路线规划 / POI 搜索 / 语音播报),离线走 osmdroid 本地瓦片,一键切换
- 📦 **离线地图导入** — 支持 MBTiles / ZIP 瓦片包 / `{z}/{x}/{y}` 瓦片文件夹 / osmdroid SQLite,并自动识别元数据
- 🧭 **坐标系纠偏** — 内建 WGS-84 / GCJ-02 / BD-09 互转,导入高德、腾讯、百度瓦片也能与 GPS 精准对齐
- ❤️ **心率带支持** — 标准 BLE HRS(0x180D),自动识别/记忆回连,实时 + 平均 + 最大心率,与速度/踏频传感器互相独立
- 🛤️ **GPX 导入导出** — 骑行记录导出为 GPX 1.1(WGS-84,含高程),可直接导入 Strava / Komoot / Google Earth;也能导入他人的 GPX 路线直接导航
- 🏆 **个人纪录** — 最远单次 / 最快均速 / 最长时长 / 最大爬升
- 🤖 **智能分析** — 速度 / 踏频 / 里程 / 时长 / 均速 / 热量 / 爬升实时计算与历史统计
- 🎨 **双主题设计系统** — 浅色为主(户外日光可读),深色适配夜骑,可跟随系统
- 🖥️ **骑行期显示控制** — 屏幕常亮与方向锁定**只在骑行中生效**,退出即恢复(避免后台常亮耗电、颠簸误转屏)
- 📏 **单位切换** — 公制(km / km/h / m)与英制(mi / mph / ft)一键切换;**内部一律公制存储**,只有显示层换算,历史数据不会因切换单位而"变数"
- 🔁 **自动分圈** — 每骑行指定距离自动记一圈,骑行中显示当前圈进度,结束后给出**分圈明细**(距离 / 用时 / 均速 / 爬升 / 心率)
- 💾 **断点续记** — 骑行**一开始就落库**,每 10 秒增量落盘;闪退或被系统回收后重启可**继续骑 / 直接收尾 / 丢弃**,不必担心"骑了 80 公里一次闪退全丢"
- 🌐 **文案资源化** — 全部 UI 文案已抽取到 `strings.xml`,为多语言与无障碍(TalkBack)打底
- 🛡️ **安全守护** — 前台服务锁屏保活,骑行中数据不中断

## 技术栈

- Kotlin 1.9 + Jetpack Compose + Material3
- MVVM + Repository + 轻量 DI(`core/Container`)
- Nordic Android-BLE-Library(`ble-ktx`)接入 CSC(0x1816 / 0x2A5B)与 HRS(0x180D / 0x2A37)
- 高德定位(AMapLocation)融合定位与里程积分
- Room 本地存储骑行记录、轨迹(含海拔/心率)与离线地图注册表
- **osmdroid 6.1.20** 离线栅格瓦片渲染(Apache-2.0)
- GPX 1.1 读写:JDK 自带 DOM 解析器,零第三方依赖
- Foreground Service 锁屏保活

## 目录结构

```
app/src/main/java/com/honglian/smartcycling/
├─ SmartCyclingApp.kt        # Application + 通知渠道 + osmdroid 初始化
├─ MainActivity.kt           # 权限、自动配对、横屏切换、前台服务、主题注入
├─ core/                     # 轻量依赖容器 / 设置 / 主题模式 / 车轮预设 / 单位制与换算
├─ ble/                      # CSC + HRS UUID / 解析 / 计算 / BleManager
├─ pairing/                  # 扫描与自动配对(CSC 与心率带双通道)
├─ location/                 # GPS 轨迹与速度
├─ data/                     # Room 实体 / DAO / 仓库(含离线地图注册表),v5 起支持断点续记
├─ export/                   # GPX 读写 / 骑行记录导出 / 分享
├─ ride/                     # RideState / RideViewModel / RideService / Laps(分圈推导)
├─ map/                      # 高德在线引擎(搜索 / 反查 / 路径规划 / GPX 路线导入)
├─ offline/                  # 离线引擎:坐标变换 / 格式探测 / 瓦片源 / Compose 视图
├─ ui/theme/                 # 设计系统:色板 / 排版 / 间距 / 形状
├─ ui/components/            # SpeedRing / DataGrid / 地图视图
├─ ui/screens/               # Pairing / Map / Ride / History / Settings / OfflineMaps
└─ nav/AppNav.kt             # 配对→地图→骑行→成绩→设置/历史/离线地图
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

## GPX 轨迹导入 / 导出

### 导出

| 入口 | 行为 |
|---|---|
| 骑行结束页 →「💾 导出 GPX 轨迹」 | 拉起系统分享面板(Strava / Komoot / 微信 / 邮件 / 保存到文件) |
| 历史页 → 点开某次骑行 → 右上角 ⬇ | 走 SAF,自选保存位置 |
| 历史页 → 右上角 ⬇ | 导出**全部**骑行(一个文件内多条 `<trk>`) |

导出的文件为 **GPX 1.1**,包含 `lat/lon`、`<ele>` 高程与 `<time>` 时间戳。

> **为什么导出的是 WGS-84?** 应用内部所有高德坐标都是 GCJ-02(火星坐标),而 GPX 是国际格式,
> 行业约定用 WGS-84。导出前统一纠偏(`RideGpx.fromRide`),否则轨迹导进 Strava / Google Earth
> 会整体偏移 300~600 米 —— 表现为"在自己软件里看着对,放到别的软件里就偏了"。

### 导入路线

地图页搜索框右侧的 **📂 图标** → 选择 `.gpx` 文件 → 解析为路线并直接导航。

兼容 `<trkpt>`(轨迹)/ `<rtept>`(路线)/ `<wpt>`(航点)三种点标签,因此
Garmin Connect、行者、黑鸟、Komoot、Strava 等导出的文件都能直接吃。
导入后按 **WGS-84 → GCJ-02** 纠偏后交给高德导航引擎。

## 心率带(BLE HRS)

标准蓝牙心率带(`0x180D` / `0x2A37` 测量特征)即插即用:

- 配对页扫描时自动识别并静默连接,不阻塞速度/踏频传感器的配对流程;
- 连接过一次后会**记住 MAC 地址**,下次开机扫描到即自动回连;
- 骑行中数据页自动多出一行「实时心率 / 平均心率」,成绩页与历史卡片同步展示;
- 未连接心率带时,相关 UI 完全不出现,不影响任何原有指标。

> 心率带与速度/踏频传感器是**两条互相独立的 BLE 连接**,任一掉线不影响另一路。

## 骑行期显示控制

设置页「训练与骑行偏好」下有两项**只在骑行期间生效**的开关:

- **骑行时保持屏幕常亮**(默认开)—— 进入骑行页时开启 `FLAG_KEEP_SCREEN_ON`,退出骑行立即清除。
  第一性原理:常亮只在"需要随时瞥一眼仪表盘"这一种状态下才有意义;若全局无条件开启,
  浏览历史/翻设置时屏幕也永不熄灭,纯属耗电。
- **骑行时锁定屏幕方向**(默认关)—— 开启后进入骑行锁定为进入瞬间的方向(`SCREEN_ORIENTATION_LOCKED`),
  退出恢复自适应(`SCREEN_ORIENTATION_SENSOR`)。手机固定在车把支架上时,
  路面颠簸会让重力感应误判方向,导致横竖屏来回切换。

两项均即时生效,无需重启。

## 单位制与自动分圈

### 单位切换

设置页「训练与骑行偏好 → 单位制」可在**公制**(km / km/h / m)与**英制**(mi / mph / ft)之间切换。

第一性原理:**内部一律公制**。距离 / 速度 / 爬升在数据库、GPX、云端载荷里全部是米与 km/h,
只有显示层才按偏好换算(`core/UnitSystem.kt` 的 `Units` 是纯函数,可单测)。
这样切换单位不会污染任何持久化数据,也不会让历史记录出现"同一段路两个数值"。

单位符号(km / mi / mph / ft)不放进 `strings.xml` —— 它们在所有语言下写法一致,
放进去只会让每个语言重复一遍相同符号。需要翻译的**句子**仍走资源。

**语音播报同样跟随单位制**,但不能直接用符号:中文 TTS 会把 "m" 念成字母 M,
因此播报里用的是语言里的**词**(`unit_word_km` / `unit_word_meter` / `unit_word_mile` / `unit_word_foot`)。
公制下的播报文本与改造前逐字一致("120 米" / "12.3 公里")。

### 自动分圈

设置页可开启自动分圈并设置圈长(0.5–50 km,滑块按当前单位制显示)。
骑行中 HUD 会显示「第 N 圈 · 当前圈已骑距离」,结束后成绩页与历史详情给出**分圈明细**。

第一性原理:**分圈是可重算的派生数据,不建 `laps` 表**。每圈边界完全由"轨迹点 + 分圈距离"决定,
把它物化成表会带来写入时机 / 迁移 / 级联删除三处一致性负担,却换不来任何无法从轨迹点算出的信息。
因此只在 `rides` 上存一个 `lapDistanceM`(记录当时用的阈值),分圈本身一律用 `Laps.split()` 现算 ——
好处是**历史记录也能立刻显示分圈**,无需补数据。

唯一的取舍:分圈依据是 GPS 轨迹几何,而非轮速计程。GPS 长时间失锁的那一段(如长隧道)
不会产生轨迹点,该段的距离与时长都不会计入任何一圈。

## 断点续记(崩溃不丢骑行)

骑行**一开始就落库**一行 `endedAt = 0` 的占位记录,之后每 10 秒把新增轨迹点与最新成绩增量写回。
若进程被杀(闪退 / 被系统回收),下次启动会检测到这行记录并提示三选一:

| 选择 | 行为 |
|---|---|
| **继续骑行** | 复用同一条记录接着记,不产生第二条;距离/爬升/心率均值由已落盘的**逐点数据**精确还原 |
| **保存并结束** | 直接补上 `endedAt` 收尾成一条完整记录 |
| **丢弃** | 连同轨迹点一起删除(外键级联) |

第一性原理:**用 `endedAt = 0` 当哨兵值,而不是新增一列 `isInProgress`**。
既省一次迁移,又天然让"进行中"与"已完成"的记录共用同一张表与同一套级联删除;
历史查询只需加一个 `endedAt > 0` 过滤条件,进行中的记录就不会出现在历史里。

10 秒是刻意的取舍:最坏情况丢 10 秒数据,而写入开销可忽略。

## 文案与多语言

所有用户可见文案(含 Toast / 语音播报 / 无障碍 `contentDescription`)已从代码中抽取到
`app/src/main/res/values/strings.xml`,统一通过 `stringResource()` / `context.getString()` 读取。

- 新增 `values-en/strings.xml` 即可接入英文,无需改动任何 Kotlin 代码;
- 带变量的文案一律使用**位置占位符**(`%1$d` / `%1$.1f` / `%1$s`),保证语序可调整;
- 版本号取自 `BuildConfig.VERSION_NAME`,不再手写于字符串中(避免"关于"页版本落后于实际构建)。

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
7. **GPX 导出必须纠到 WGS-84**:国际格式用 WGS-84,应用内部用 GCJ-02,不纠偏则跨软件偏移 300~600 米。
8. **心率带与 CSC 分属两个 BleManager**:两路独立连接,避免"心率带掉线把速度踏频一起拖垮"。
9. **GPX 解析关掉 DTD/外部实体**:防 XXE —— GPX 是用户从外部拿来的文件,属于不可信输入。
10. **`FLAG_KEEP_SCREEN_ON` 与骑行状态绑定**,不做全局设置:常亮的收益只存在于骑行页,全局开启等于纯粹的待机耗电。
11. **方向策略按场景分流**:普通页面 `SENSOR`(横放自动横屏),骑行页可选 `LOCKED`(锁定进入瞬间方向,防颠簸误转)。
12. **单位只在显示层换算**:落库 / GPX / 云端一律公制,`UnitSystem` 通过 `CompositionLocal` 下发,避免十几个函数签名透传参数。
13. **分圈不建表**:可重算的派生数据不做物化,`rides.lapDistanceM` 存阈值、`Laps.split()` 现算,历史记录也能立刻看到分圈。
14. **断点续记用 `endedAt = 0` 哨兵**:省一次迁移,且"进行中"与"已完成"共用同一张表、同一套级联删除,历史查询只需 `endedAt > 0`。
15. **逐点心率入库**:分圈心率的前提 —— 轨迹点上没有心率,分圈就只能给整段平均值,间歇训练复盘时基本没用。

## 开源合规

本项目采用 MIT。接入第三方代码时需避开 GPL/AGPL 传染性许可(如 pizero_bikecomputer 为 GPL-3.0),仅作算法参考。
osmdroid 为 Apache-2.0,可安全商用。

## 对抗审查

见 `REVIEW.md`。
