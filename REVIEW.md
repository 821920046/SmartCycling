# 对抗审查报告(Adversarial Review)

> 审查对象:SmartCycling 本轮"双主题 UI 重构 + 双引擎离线地图"改动
> 审查姿态:**假定实现是错的**,用"挑毛病"的视角逐层证伪;凡不能证伪的,才允许保留。
> 审查方法:源码级静态审查 + 与 osmdroid 6.1.20 官方源码逐接口比对(而非凭记忆)。
> 说明:本机无 JDK / Android SDK / gradle-wrapper.jar,**无法本地编译**,
> 因此所有结论均来自源码比对与静态检查,并已在文末"验证局限"中显式声明。

---

## 一、本次对抗审查发现并修复的缺陷

### D1 —(致命,功能性)离线地图"激活了却不生效"

| 项 | 内容 |
|---|---|
| 现象 | 在「离线地图」页选中并激活某个包后,返回地图页仍显示高德在线底图;必须重启 App 才生效。 |
| 定位 | `nav/AppNav.kt` 读取的是 `settingsViewModel.mapSource` / `activeOfflineMapId`;而写入方是 `offline/OfflineMapsViewModel.activate()`。二者是**两个不同的 ViewModel**。 |
| 根因(第一性原理) | **同一份状态存在多个副本**。两个 ViewModel 各自 `MutableStateFlow(container.settings.xxx)` 缓存了同一 SharedPreferences 值,写入方改的是 SharedPreferences,读取方的 StateFlow 永不刷新。又因二者均以 Activity 为 ViewModelStoreOwner(`AppNav` 与 `MainActivity` 中的 `viewModel()` 取到的是**同一个** Activity 级实例,而 `OfflineMapsViewModel` 是另一个实例),进程存活期间**不会自愈**。 |
| 修复 | 把 `mapType / themeMode / mapSource / activeOfflineMapId` 收敛为 `core/Settings.kt` 内的**单一可观察数据源**(`mapTypeFlow / themeModeFlow / mapSourceFlow / activeOfflineMapIdFlow`),其 setter 同时写 SharedPreferences 与该流;`SettingsViewModel` 与 `OfflineMapsViewModel` 均**直接复用同一条流**。从缺陷类别上根除"状态副本不一致"。 |
| 影响面 | `core/Settings.kt`、`core/SettingsViewModel.kt`、`offline/OfflineMapsViewModel.kt` |

### D2 —(编译失败)引用了未定义的常量 `VECTOR_FORMATS`

`offline/OfflineMapInspector.kt` 使用了 `VECTOR_FORMATS`,但该常量在工程中**从未定义** → Kotlin 编译直接失败。
**修复**:在 `offline/MapCrs.kt` 中与 `TILE_EXTENSIONS` 并列定义 `internal val VECTOR_FORMATS = setOf("pbf","mvt","vector","protobuf")`。

### D3 —(类型错误)`String?` 被喂给 `Set<String>.contains`

原写法 `declaredFormat?.lowercase() in VECTOR_FORMATS` 中,左侧为 `String?`,右侧 `Set<String>` 的 `contains` 形参要求非空 `String` → 类型不匹配。
**修复**:改写为 `declaredFormat?.lowercase()?.let { it in VECTOR_FORMATS } == true`,语义不变且类型安全。

### D4 —(功能性)"导入成功但一片空白":ZIP 内非 PNG 瓦片

| 项 | 内容 |
|---|---|
| 依据 | 比对 osmdroid 源码 `ZipFileArchive.getTileRelativeFilenameString()` —— 瓦片路径**硬编码 `.png` 后缀**(即便 `setIgnoreTileSource(true)` 亦然)。 |
| 后果 | 若 ZIP 内为 `.jpg/.webp`,导入会"成功",但渲染永远取不到图 → 用户看到空白底图却无任何提示。 |
| 修复 | 导入探测阶段读取首个瓦片扩展名,非 `png` 直接 `Rejected`,并提示"请先解压,再用「导入文件夹」(文件夹模式支持 png/jpg/webp)"。 |

### D5 —(功能性)矢量瓦片包被当作栅格渲染

osmdroid 是**纯栅格**管线;`.pbf/.mvt` 为 protobuf 矢量切片,必须经矢量引擎解释样式才能出图,直接喂给 osmdroid 只会渲染空白。
**修复**:MBTiles 探测时读取 `metadata.format`,命中 `pbf/mvt/vector/protobuf` 即拒绝并提示重新导出为 PNG/JPEG 栅格瓦片。

### D6 —(真实缺陷)切换离线地图后,旧包的"滚动范围限制"残留

| 项 | 内容 |
|---|---|
| 现象 | 从"带覆盖范围的包"切到"无范围的包"后,地图无法拖出上一张图的范围,像是被锁死。 |
| 根因 | `OfflineMapView` 只在 `bounds != null` 时调用 `mapView.setScrollableAreaLimitDouble(...)`;而 osmdroid **仅在传入 `null` 时才清除限制**,于是旧限制一直生效。 |
| 修复 | 改为**无条件调用**:有有效范围传 `BoundingBox`,否则传 `null`(含"跨度太小"的降级分支)。 |

### D7 —(数据质量缺陷)GPS 跳点/低精度点污染历史轨迹

| 项 | 内容 |
|---|---|
| 现象 | 经过隧道/高架后,历史轨迹上出现一段明显的"尖刺",车标也会瞬移。 |
| 根因 | `LocationTracker` 已识别跳点(相邻点隐含时速 > 80km/h)并**刻意不更新 `lastLocation`**("防止持续受影响"),但仍把该点 `trySend` 出去;`RideViewModel.collectLocation` **无条件**写入 `trackPoints` 并更新 `_currentLatLng` —— 与上游意图自相矛盾。低精度(>25m)点同样会被落库。 |
| 修复 | `LocationSample` 新增 `isReliable = !isJumpPoint && accuracy ≤ 25m`;`RideViewModel` 仅对**可信点**写入轨迹与移动车标,不可信点仍更新 `lastGpsAt` 以保活 GPS 看门狗。魔数 25f 提取为 `ACCURACY_LIMIT_M` 常量。 |

### D8 —(本轮自引入,能耗缺陷)在线模式下双路定位同时运行

| 项 | 内容 |
|---|---|
| 背景 | 为补齐 R3(离线底图不显示蓝点),在 `AppNav` 的地图页订阅了 `mapViewModel.currentLatLng`(FusedLocation)并透传给 `OfflineMapView`。 |
| 现象 | **只要停在地图页,无论在线还是离线,FusedLocation 都在跑**。而在线模式下高德底图自带定位(GCJ-02)也同时在跑 → 两路定位并行,持续耗电。 |
| 根因(第一性原理) | 订阅动作与"是否真的需要它"**解耦**了:蓝点只在离线底图上绘制,在线模式根本用不到这路流,却因代码无条件 `collectAsState()` 而保持激活(`WhileSubscribed` 有订阅者即不停止上游)。 |
| 修复 | 在 `AppNav` 按 `effectiveSource` 分流:离线 → 订阅真实 `mapViewModel.currentLatLng`;在线 → 订阅一个常量空流 `remember { flowOf<LatLng?>(null) }`。后者使真实 GPS 流**失去订阅者**,`WhileSubscribed(5s)` 到期后自动停止定位。 |
| 影响面 | `nav/AppNav.kt` |
| 备注 | 这属于"修一个缺口时引入另一个缺口"的典型模式,故本轮专门把它作为对抗审查对象回查。 |

### D9 —(本轮自引入,信息丢失)导入成功的"非致命提醒"被静默吞掉

| 项 | 内容 |
|---|---|
| 背景 | 为提升导入体验,`OfflineMapInspector` 会计算非致命 `warning`(如"首张瓦片无法解码,该包可能已损坏")。 |
| 现象 | 该提醒**只在 `Rejected` 分支被透出**(作为 hint);一旦走 `Success` 分支,`warning` 直接丢弃 → 用户导入了一个可能损坏的包,却只看到绿色的"已导入"。 |
| 根因 | `ImportResult.Success` 未携带 `warning` 字段,`OfflineMapsViewModel` 的成功分支也无处可显示它 —— 信息在数据模型层就被截断了。 |
| 修复 | ① `ImportResult.Success` 新增 `warning: String? = null`,两处构造点(`importFile`/`importFolder`)均传入 `probe.warning`;② `ImportUiState` 新增 `isWarning`,`OfflineMapsScreen` 由"二态(红/绿)"改为**三态(红/琥珀/绿)**,琥珀色配 `Icons.Outlined.Warning`,避免"带病成功"被误读为完全正常。 |
| 影响面 | `offline/OfflineMapRepository.kt`、`offline/OfflineMapsViewModel.kt`、`ui/screens/OfflineMapsScreen.kt` |

---

## 二、逐层核验:确认正确、无需修改的关键设计

以下为"重点怀疑但最终证伪(即实现是对的)"的项,记录结论与依据,便于后续维护者不必重复考古。

### 2.1 瓦片提供器链 —— 与 osmdroid 官方 `OfflineTileProvider` 完全同构

逐行比对 `OfflineTileProvider.java` 后确认,`offline/OfflineTileProviderFactory.kt` 的构造方式与官方离线提供器**一致**:

- 链 = `[MapTileFileArchiveProvider, MapTileApproximater]`,且 `approximater.addProvider(archiveProvider)`;
- `isDowngradedMode(index)` 覆写为 `true` —— 官方 `OfflineTileProvider` 亦如此。该返回值使 `MapTileProviderArray.getMapTile()` 对"非 UP_TO_DATE(如 SCALED)"的缓存瓦片直接返回,**不再反复回链重取**,避免了近似瓦片导致的请求风暴。

### 2.2 离线场景"绝不偷偷联网"

- `MapView.setUseDataConnection(false)`(在 `onResume()` 之前设置);
- `MapTileApproximater.getUsesDataConnection()` 官方返回 `false`;
- `MapTileFileArchiveProvider.getUsesDataConnection()` 官方返回 `false`;
- `MapTileProviderArray.findNextAppropriateProvider()` 会跳过"需要数据连接而当前禁用"的提供器。
→ 结论:整条链上不存在任何网络提供器,离线渲染期间零网络请求。

### 2.3 切换地图包不泄漏文件句柄

- `MapView.setTileProvider()` 官方实现**先 `detach()` 旧提供器**;
- `MapView.onDetach()` 亦会 `detach()` 当前提供器;
- `MapTileFileArchiveProvider.detach()` → `clearArcives()` → 逐个 `archiveFile.close()`。
→ 结论:反复切换/退出,MBTiles/ZIP 的 SQLite/ZipFile 句柄都会被正确关闭。`OfflineMapView` 已在 `onDispose` 调用 `onDetach()`。

### 2.4 `MapTileIndex.getTileIndex()` 的越界抛异常已被拦截

官方 `MapTileIndex.checkValues()` 在 `x/y ≥ 2^zoom` 或 `zoom > mMaxZoomLevel` 时**抛 `IllegalArgumentException`**。
`offline/ArchiveFiles.kt` 的 `CrsRemapArchiveFile` 在调用前已用 `TileMath.limit(zoom) = 1 shl zoom` 做 `x/y < limit` 且 `zoom ∈ [0,24]` 的双重边界校验 → 不会触发异常。

### 2.5 坐标系纠偏放在"瓦片读取层"是正确解

- `TileSystem` 的接口 `getX01FromLongitude / getY01FromLatitude` 是**一维可分离**的;
- 而 GCJ-02 偏移 `(dLat, dLon)` 是**经纬度二元耦合**的。
→ 无法用自定义 `TileSystem` 精确表达。改在 `IArchiveFile.getInputStream()` 里做"请求瓦片中心 → 转包坐标系 → 反查瓦片"的一次换算,残余误差为偏移场在单张瓦片(约 150m)内的变化量(亚米级,不可见)。**且 `CrsRemapArchiveFile` 无任何可变字段 → 线程安全**,可被多个瓦片加载线程并发调用。近似瓦片(lower-zoom)同样经过该层,故缩放过程中的拼贴也不会错位。

### 2.6 归档驱动的"瓦片源名"过滤已按格式正确处置

| 格式 | osmdroid 行为 | 处置 |
|---|---|---|
| MBTiles | `setIgnoreTileSource()` 为**空实现**(从不按源名过滤) | 无需调用 |
| osmdroid SQLite | 默认按 `provider` 列过滤 | 显式 `setIgnoreTileSource(true)` |
| ZIP | 默认按内部顶层目录名过滤 | 显式 `setIgnoreTileSource(true)` |

→ 用户自行打包的瓦片包(顶层目录名与文件名不一致)不会再静默显示空白。

### 2.7 Room 迁移不会清空历史骑行数据

`AppDatabase` v1→v2 提供了**显式 `MIGRATION_1_2`** 建表语句。若仅依赖 `fallbackToDestructiveMigration()`,升级版本号会**直接清空用户全部骑行记录** —— 现已避免。

### 2.8 导入路径无路径穿越风险

`OfflineMapRepository.uniqueTarget()` 经 `sanitize()` 剥离 `\ / : * ? " < > |` 及控制字符,再 `trimEnd('.')`,目标文件始终落在 `filesDir/offline_maps/` 之下;导入先写 `.import-*.tmp` 临时文件,失败/中断时删除,启动时 `cleanupStaleImports()` 兜底清理。

### 2.9 既有骑行算法的健壮性(沿用,未回归)

- **EMA 平滑**(`RideViewModel`,α=0.4)抑制包分发抖动;
- **跳点拦截**(`LocationTracker`,时速上限 80km/h)防止隧道/高架漂移污染里程;
- **静止自动暂停 / 起步唤醒**;
- CSC 时间戳 65536 翻转取模修正、曲柄圈数 `and 0xFFFF` 修正、逐字段长度校验防越界。

---

## 三、遗留风险与非阻塞项(已知,评估后暂不改)

### 3.1 本轮已修复(上表 R1~R8 中)

| 原编号 | 项 | 本轮处置 |
|---|---|---|
| R1 | 文件夹型导入无法中途取消 | **已修复**。`copyDocument` 改为 `suspend` 并在每个条目/每段缓冲区 `currentCoroutineContext().ensureActive()`,取消可即时生效;`importFolder/importFile` 捕获 `CancellationException` 后清理半成品并原样抛出,不再被误记为"导入失败"。 |
| R2 | `runCatching` 吞掉 `CancellationException` | **已修复**。`OfflineMapsViewModel.runImport` 显式 `catch (ce: CancellationException)` → 提示"已取消导入"并 `throw ce`;仓库侧同样区分处理。 |
| R3 | 地图页离线底图不显示"我的位置"蓝点 | **已修复**。新增 `MapViewModel.currentLatLng`(WGS-84,`WhileSubscribed(5s)`)→ `AppNav` 透传 → `MapScreen` → `OfflineMapView`。(注意:修复过程中引入了 D8,已一并修正。) |
| R4 | 版本号不一致 | **已修复**。`app/build.gradle.kts` 统一为 `versionName = "1.1.0"`、`versionCode = 2`,与设置页「关于」一致。 |
| R7 | 主题切换时路线颜色不跟随 | **已修复**。`OfflineMapView` 新增 `LaunchedEffect(routeColor)`,并在全部 3 个调用点传入 `palette.primary.toArgb()`。 |
| R8 | 文件夹导入进度按"顶层条目数"跳变 | **已修复**。新增 `countFiles()` 递归统计文件总数,进度按"已拷文件/总文件"平滑推进。 |

### 3.2 仍然存在(已评估,暂不改)

| # | 项 | 评估 |
|---|---|---|
| R5 | osmdroid 清单会合并进 `WRITE_EXTERNAL_STORAGE`(maxSdkVersion=28) | 在 Android 10+ 被忽略;如需洁癖可 `tools:node="remove"`。 |
| R6 | `OfflineMapInspector` 对超大 ZIP 需遍历中央目录 | 仅读中央目录(不读数据体),数 GB 包仍可秒级完成;仅当条目数极多时有可感耗时。 |
| R9 | `collectSensor()` 在暂停期间直接丢弃读数 | 恢复骑行后速度可能有一瞬为 0(等待下一帧)。可接受。 |
| R10 | 地图页 → 骑行页切换时,两路 FusedLocation 有 ≤5s 重叠 | `MapViewModel.currentLatLng` 用 `WhileSubscribed(5s)`,离开地图页后 5s 才停止;而 `RideViewModel` 立刻开始订阅。重叠窗口内 `container.locationTracker` 被调用两次 `track()` → 两次 `requestLocationUpdates`。影响:约 5 秒的双注册,自动收敛。若要彻底消除,可把 `track()` 收敛为 `shareIn` 的单例上游。 |
| R11 | 离线底图上"我的位置"点与"目的地"图钉颜色硬编码 | 分别为 `#1B6EF3` / `#E5484D`,不随主题变化。二者在明暗两套色板下对比度均可接受,故暂不参数化。 |
| R12 | `RideViewModel.collectLocation` 在可信性判断**之前**累加 `deltaMeters` | 经核验为**无害**:不可信点的 `deltaMeters` 恒为 0(跳点直接置 0;低精度点在 `LocationTracker` 中根本不进入距离计算分支),累加 0 不改变里程。 |
| R13 | `canDecode()` 对超过 8MB 的首张瓦片直接判"不可解码" | 单张栅格瓦片极少超过 8MB,阈值足够宽松;极端情况只会产生一条无害的琥珀色提醒,不阻断导入。 |

---

## 四、验证手段与局限

**已做**
1. 与 osmdroid 6.1.20 官方源码**逐接口比对**:`IArchiveFile`、`MapTileProviderArray`、`MapTileFileArchiveProvider`、`MapTileApproximater`、`ZipFileArchive`、`DatabaseFileArchive`、`MBTilesFileArchive`、`MapView`、`MapTileIndex`、`MapTileProviderBase`、`OfflineTileProvider`。
2. 全量 **55** 个 `.kt` 文件**结构静态检查**:括号/圆括号/方括号配平、`package` 声明与 `import` 位置合法性 —— 全部通过。
3. 交叉引用检查:确认无 `_activeId/_mapSource/...` 等已删除字段的残留引用;确认 `VECTOR_FORMATS / TILE_EXTENSIONS` 定义与使用一致;确认 `LocationSample` 全工程仅 1 处构造点且已同步新增字段;确认 `ImportResult.Success` / `ImportUiState` 的全部构造点均使用具名参数(故新增带默认值的字段不会破坏调用)。
4. **第二轮全量通读**:补齐首轮未审的文件(全部界面、`RideViewModel`、`LocationTracker`、`AppPalette`、`Type`、`MainActivity`、`Theme`、`AndroidManifest`、`build.gradle.kts`),逐项核对"界面引用的色板字段 / 组件参数 / 数据类字段"是否都已定义 —— 由此发现并修复 D6、D7。
5. **第三轮(本轮)对抗审查**:把本轮新增的 P1/P2 改动本身当作审查对象,重点回查"为修复 A 是否引入了缺陷 B"。由此发现并修复 **D8**(双路定位)、**D9**(成功路径的提醒被吞)。同时确认 R1/R2/R3/R4/R7/R8 已闭环,并复核 D1 的单例前提 —— `Container.settings` 为 `by lazy` 进程级单例、`SmartCyclingApp.container` 全局唯一,故"共享流"方案成立。

**未做(受环境限制,务必在本地/CI 复核)**
1. **未编译**。本机无 JDK 17、无 Android SDK、无 gradle-wrapper.jar。所有结论为源码级静态推断。
2. **未运行**。未在真机验证 MBTiles / ZIP / 文件夹 / osmdroid-SQLite 四类包的实际渲染与坐标系对齐效果。
3. 未覆盖 Compose 运行时行为(重组、生命周期回调时序)与 osmdroid 线程池的实机表现。

> 复核建议:在具备 Android SDK 的环境执行 `./gradlew :app:assembleDebug`,并优先用一张**高德(GCJ-02)MBTiles**与一张**OSM(WGS-84)文件夹瓦片**各导入一次,重点验证"激活即生效"(D1)、"车标与底图对齐"(2.5),以及"在线模式下 FusedLocation 是否随地图页离开而停止"(D8,可用 Profiler 观察定位回调)。
