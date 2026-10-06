# 对抗审查报告(Adversarial Review)

> 审查对象:SmartCycling 历轮改动(双主题 UI + 双引擎离线地图 + 训练统计/导航增强
> + GPX 生态 / 心率带 / 个人纪录)。
> 审查姿态:**假定实现是错的**,用"挑毛病"的视角逐层证伪;凡不能证伪的,才允许保留。
> 审查方法:源码级静态审查 + 与 osmdroid 6.1.20 官方源码逐接口比对(而非凭记忆)
> + 全工程符号表比对 + 关键算法独立建模验证。
> 累计发现并修复缺陷 **D1 ~ D15**(见 §一、§五)。
> 说明:本机无 JDK / Android SDK / gradle-wrapper.jar,**无法本地编译**,
> 因此所有结论均来自源码比对与静态检查,并已在文末"验证局限"中显式声明。
> ⚠️ 尤其注意 **D10**:它证明了"静态检查全绿"并不等于"能编译",务必在本地/CI 真跑一次构建。

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
| R10 | 地图页 → 骑行页切换时,两路定位(现为高德 AMapLocation)有 ≤5s 重叠 | `MapViewModel.currentLatLng` 用 `WhileSubscribed(5s)`,离开地图页后 5s 才停止;而 `RideViewModel` 立刻开始订阅。重叠窗口内 `container.locationTracker` 被调用两次 `track()` → 两次 `requestLocationUpdates`。影响:约 5 秒的双注册,自动收敛。若要彻底消除,可把 `track()` 收敛为 `shareIn` 的单例上游。 |
| R11 | 离线底图上"我的位置"点与"目的地"图钉颜色硬编码 | 分别为 `#1B6EF3` / `#E5484D`,不随主题变化。二者在明暗两套色板下对比度均可接受,故暂不参数化。 |
| R12 | `RideViewModel.collectLocation` 在可信性判断**之前**累加 `deltaMeters` | 经核验为**无害**:不可信点的 `deltaMeters` 恒为 0(跳点直接置 0;低精度点在 `LocationTracker` 中根本不进入距离计算分支),累加 0 不改变里程。 |
| R13 | `canDecode()` 对超过 8MB 的首张瓦片直接判"不可解码" | 单张栅格瓦片极少超过 8MB,阈值足够宽松;极端情况只会产生一条无害的琥珀色提醒,不阻断导入。 |

---

## 四、验证手段与局限

**已做**
1. 与 osmdroid 6.1.20 官方源码**逐接口比对**:`IArchiveFile`、`MapTileProviderArray`、`MapTileFileArchiveProvider`、`MapTileApproximater`、`ZipFileArchive`、`DatabaseFileArchive`、`MBTilesFileArchive`、`MapView`、`MapTileIndex`、`MapTileProviderBase`、`OfflineTileProvider`。
2. 全量 **66** 个 `.kt` 文件**结构静态检查**:用状态机剥离注释/字符串/字符字面量后统计括号配平(早期版本的正则剥离会被 `'"'` 这类字符字面量骗过,已修正为逐字符状态机)—— 全部通过。
3. 交叉引用检查:确认无 `_activeId/_mapSource/...` 等已删除字段的残留引用;确认 `VECTOR_FORMATS / TILE_EXTENSIONS` 定义与使用一致;确认 `LocationSample` 全工程仅 1 处构造点且已同步新增字段;确认 `ImportResult.Success` / `ImportUiState` 的全部构造点均使用具名参数(故新增带默认值的字段不会破坏调用)。
4. **第二轮全量通读**:补齐首轮未审的文件(全部界面、`RideViewModel`、`LocationTracker`、`AppPalette`、`Type`、`MainActivity`、`Theme`、`AndroidManifest`、`build.gradle.kts`),逐项核对"界面引用的色板字段 / 组件参数 / 数据类字段"是否都已定义 —— 由此发现并修复 D6、D7。
5. **第三轮(本轮)对抗审查**:把本轮新增的 P1/P2 改动本身当作审查对象,重点回查"为修复 A 是否引入了缺陷 B"。由此发现并修复 **D8**(双路定位)、**D9**(成功路径的提醒被吞)。同时确认 R1/R2/R3/R4/R7/R8 已闭环,并复核 D1 的单例前提 —— `Container.settings` 为 `by lazy` 进程级单例、`SmartCyclingApp.container` 全局唯一,故"共享流"方案成立。

**未做(受环境限制,务必在本地/CI 复核)**
1. **未编译**。本机无 JDK 17、无 Android SDK、无 gradle-wrapper.jar。所有结论为源码级静态推断。
2. **未运行**。未在真机验证 MBTiles / ZIP / 文件夹 / osmdroid-SQLite 四类包的实际渲染与坐标系对齐效果。
3. 未覆盖 Compose 运行时行为(重组、生命周期回调时序)与 osmdroid 线程池的实机表现。

> 复核建议:在具备 Android SDK 的环境执行 `./gradlew :app:assembleDebug`,并优先用一张**高德(GCJ-02)MBTiles**与一张**OSM(WGS-84)文件夹瓦片**各导入一次,重点验证"激活即生效"(D1)、"车标与底图对齐"(2.5),以及"在线模式下 FusedLocation 是否随地图页离开而停止"(D8,可用 Profiler 观察定位回调)。

---

## 五、第四轮对抗审查:GPX 生态 + 心率带 + 个人纪录

本轮目标是"结合同类开源项目的长处把工程补完整"。对照项目:
**OpenTracks**(GPX 导出 / 隐私优先)、**Trackbook**(离线优先记录)、
**OSMBonusPack**(GPX/KML 解析)、**Gadgetbridge / pizero_bikecomputer**(BLE 心率带)、
**Strava / Komoot**(个人纪录、轨迹分享)。审查对象是**本轮新增的全部代码**,
重点仍是"新代码是否引入新缺陷"以及"跨模块契约是否自洽"。

### D10 —(编译阻断,致命)`DataCell` 被引用但全工程无定义

**现象**:`ui/components/DataGrid.kt` 底部两栏指标调用了 `DataCell(Modifier.weight(1f), …)`,
但**全仓找不到该函数的定义**。Kotlin 没有隐式声明,这是**硬编译错误** ——
也就是说当前 `main` 分支的 `app` 模块**根本无法编译**。

**成因**:两条开发线合并时,`DataGrid.kt` 取了其中一条线的版本(含 `DataCell` 调用),
而 `DataCell` 的定义随另一条线的版本被丢弃,合并时没有察觉。

**为什么前几轮没发现**:前几轮静态检查只做"括号配平 + 已删除字段残留引用",
**没有做"被调用的符号是否存在"的全局校验**。这类"单侧丢失"正是合并冲突的典型产物。

**修复**:补回 `DataCell` 实现(两栏:大数值 + 小标签,水平居中),并把检查手段升级为
**全工程符号表比对**(见 §5.4),这类缺陷以后会被自动拦下。

### D11 —(数据正确性)GPX 导出的坐标系必须纠到 WGS-84

**现象(若不做处理)**:应用内部所有来自高德的坐标(定位、路线、轨迹点)都是 **GCJ-02**。
把库里的经纬度**原样**写进 GPX,文件本身完全合法,但在 Strava / Komoot / Google Earth /
Garmin BaseCamp 打开会**整体偏移 300~600 米**。

**为什么危险**:这是典型的"看起来对"的缺陷 —— 在自己的 App 里回放轨迹完全正常,
只有导入第三方软件才暴露,用户会误以为是对方软件的问题。

**修复**:`export/RideGpx.fromRide()` 写 GPX 前统一做一次
`GeoTransform.convert(lat, lon, GCJ02, WGS84)`;反向的 `MapViewModel.importRoute()`
做 `WGS84 → GCJ02`,保证"导出 → 导入"闭环一致。

> 这与 §2.5 的"离线瓦片层纠偏"是同一第一性原理的两处应用:
> **凡跨越坐标系边界,必须在边界处显式换算,绝不允许"碰巧数字接近"而蒙混过关。**

### D12 —(Room 迁移校验)新增列必须 `NOT NULL DEFAULT 0`,且实体要同步声明默认值

SQLite 无法 `ADD COLUMN` 一个"NOT NULL 且无默认值"的列,因此 v3→v4 新增的三列
(`avgHeartRateBpm` / `maxHeartRateBpm` / `elevationM`)必须带 `DEFAULT 0`。
而 Room 在迁移执行完后会**校验实际 schema 与实体声明是否一致**;
若实体上不同步标注 `@ColumnInfo(defaultValue = "0")`,校验会判定"迁移未正确执行"并抛异常。

**已处置**:三个新列同时具备"迁移里的 `DEFAULT 0`"与"实体上的 `@ColumnInfo(defaultValue = "0")"`。

**顺带发现的既有隐患(评估后不改)**:v1→v2 加的 `calories` / `elevationGainM`
迁移里带了 `DEFAULT 0`,但实体上**没有**声明 `defaultValue`。理论上会让
"从 v1 一路升上来"的设备在校验时不一致。之所以不动它:

- 若给实体补上 `defaultValue = "0"`,则**全新安装 v3** 的设备(其 `rides` 表在
  `CREATE TABLE` 时并没有 `DEFAULT` 子句,实际默认值为 NULL)会**反而**校验失败 ——
  等于用一个新缺陷换掉一个旧隐患;
- 该隐患只影响"v1 → v2 → v3 → v4"这条多级升级路径,而本应用尚处早期(versionCode 曾为 2),
  实际存在此类设备库的概率极低。

**结论**:保持原样,并在 `Entities.kt` 就地留注释说明,避免后来者"好心改坏"。

### D13 —(安全)GPX 是不可信输入,解析前必须关闭 DTD / 外部实体

GPX 是用户从外部(论坛、群文件、他人分享)拿到的文件,属于**不可信输入**。
XML 外部实体注入(XXE)可让恶意 GPX 读取设备本地文件并把内容带出去。

**修复**:`GpxFormat.parse()` 构造 `DocumentBuilderFactory` 时设
`isExpandEntityReferences = false`、`isXIncludeAware = false`,并关闭
`disallow-doctype-decl` / `external-general-entities` / `external-parameter-entities`。
所有 `setFeature` 均用 `runCatching` 包裹 —— 个别实现不支持这些 feature 会抛
`ParserConfigurationException`,不能因此让正常文件也解析失败。

### D14 —(功能性)心率带的"名称兜底识别"不能纳入歧义品牌名

初版把 `magene / garmin / wahoo / bryton / igpsport` 等品牌名都放进心率带关键词表。
问题是这些品牌**既做心率带也做码表/速度踏频传感器** —— 迈金 S314 正是 Magene 的产品。
结果:配对页会把 S314 同时判定为"心率带",用 `HeartRateManager` 去连它,而它并不提供 0x180D,
连接必然失败并干扰正常的 CSC 配对。

**修复**:关键词表只保留**明确指向心率**的词(`heart` / `hrm` / `hr-` / `hr_` / `polar` /
`tickr` / `h10` / `h9` / `coospo` / `心率`);**主判据始终是广播包里的 0x180D 服务 UUID**
(标准心率带几乎都会携带)。品牌名一律不进表。

### D15 —(架构)心率带与 CSC 必须是两条独立连接

若共用一个 `BleManager`,心率带掉线会连带把速度/踏频连接一起拖垮,反之亦然。
**处置**:`HeartRateManager` 与 `S314Manager` 各自独立(同构实现,各自独立的重连策略),
由 `Container` 分别持有。配对页中只有 CSC 的连接状态决定是否放行进入主界面;
心率带**后台静默连接**,连上与否都不阻塞主流程。

同时给心率读数加了**陈旧检测**(`HR_STALE_MS = 5000`,比 CSC 的 3000 宽松,因心率带
上报间隔本就较长),掉线后实时心率自动归零,不会"卡在最后一个读数";
`hasHeartRate` 作为 UI 门控 —— 没有心率带时,骑行页 / 成绩页 / 历史卡片**完全不出现**心率行。

### 5.4 本轮验证手段(相比前几轮升级)

1. **全工程符号表比对**(新增,专为抓 D10 这类缺陷):抽取所有 `.kt` 的声明名
   (fun/class/object/val/var/枚举项/构造参数)+ 各文件 `import` + 项目内声明集合,
   再扫描所有 `Identifier(` 调用点;凡"未导入、本文件未声明、全工程未声明、不在白名单"
   者一律报出。结果:仅剩 18 处**已知误报**(`synchronized`/`buildString`/`doubleArrayOf`
   等 stdlib,以及 `setNotificationCallback`/`drawArc` 等继承或他对象成员),**无真实缺失符号**;
   `DataCell` 在修复后已从报告中消失。
2. **括号配平改为逐字符状态机**:旧的正则剥离会被 `.append('"')` 这类
   **字符字面量内含双引号**的写法骗过,产生 4 个假警报;改状态机后 66 个文件全部配平。
3. **GPX 格式语义独立建模验证**:在无 JDK / 无 Kotlin 编译器的前提下,用 Python
   **独立复现** `GpxFormat.write()` 的字符串生成逻辑,再用标准 XML 解析器回读,验证:
   命名空间 / schemaLocation 合法、`lat/lon/ele/time` 往返一致、XML 特殊字符正确转义、
   多轨迹文档合法、`rtept` 可解析、带 `+08:00` 偏移的时间能正确解析、空白输入被拒绝。
   **全部通过**。这验证了"格式设计"本身无误(但不等于 Kotlin 代码已编译通过)。
4. **图标名合法性核对**:新增的 `FileOpen` / `FileDownload` / `EmojiEvents` / `Favorite`
   逐一对照 Material Icons 官方码点表(`MaterialIcons-Regular.codepoints`,2235 个图标),
   确认全部存在 —— 图标名写错同样是硬编译错误。
5. **主题 token 核对**:确认新代码引用的 `palette.danger/hudValue/hudLabel/primaryContainer`
   与 `Space.xs/sm/md/lg`、`Radius.md/lg`、`MaterialTheme.typography.*` 均已定义。

### 5.5 本轮仍未做(受环境限制)

- **依旧未编译、未运行**。本机无 JDK 17 / Android SDK / gradle-wrapper.jar。
  D10 的教训说明"静态检查通过"≠"能编译":**必须在本地或 CI 真跑一次**
  `./gradlew :app:assembleDebug`,并跑通 `./gradlew :app:testDebugUnitTest`
  以执行 `GpxFormatTest`(7 例)与 `HrParserTest`(5 例)。
- 未在真机验证:GPX 导入 Strava 后的对齐效果、SAF 写入、FileProvider 分享、
  心率带实际连接与回连、Room v3→v4 迁移。
- 云同步(`CloudSyncRepository`)载荷**未包含**新增的心率/海拔字段,
  即云端目前仍只存 lat/lon/speed/时间戳。如需同步,要一并修改 `cloudflare/` 的
  worker 与表结构 —— 本轮未做,以免在未知表结构下擅自改动。

## 六、第五轮对抗审查:P0 缺陷修复 + 文案资源化

本轮范围来自"免费个人骑行软件还能怎么升级"的缺口分析,只做 **P0(缺陷级)**,不做新功能。

### D16 —(能耗缺陷,真实存在)`FLAG_KEEP_SCREEN_ON` 被全局无条件设置

- **现象**:`MainActivity.onCreate` 里直接 `window.addFlags(FLAG_KEEP_SCREEN_ON)`,
  与骑行状态无关。用户在"骑行历史 / 设置 / 离线地图"等页面停留时,屏幕同样永不熄灭。
- **根因**:常亮的收益只存在于骑行页(需要随时瞥一眼仪表盘),被写成了全局策略。
- **修复**:移除全局设置;改为在 `onEnterRide` 里**按用户设置**开启、
  在 `onExitRide` 里 `clearFlags` 清除。新增设置项 `keepScreenOnWhileRiding`(默认开)。
- **顺带**:新增 `lockOrientationWhileRiding`(默认关)。开启后进入骑行
  `SCREEN_ORIENTATION_LOCKED`(锁定进入瞬间方向),退出恢复 `SENSOR` ——
  解决"手机固定在车把支架上、颠簸导致横竖屏来回切换"的可用性问题。

### D17 —(可维护性 / 无障碍)全部 UI 文案硬编码在 Kotlin 源码里

- **现象**:`strings.xml` 只有 4 条(应用名 + 通知文案),而 UI 层散落 **193 处**中文
  字符串字面量,分布在 11 个文件;`Text("...")`、`contentDescription`、
  语音播报文本、`AlertDialog` 文案全都在代码里。
- **根因**:缺少资源化约定。后果是:改文案要翻代码、无法做多语言、
  TalkBack 读屏拿不到可本地化文本。
- **修复**:把 UI 层(Compose)文案**全部**抽到 `strings.xml`(共 194 条资源),
  统一用 `stringResource()`;非组合上下文(语音 TTS、Marker title、
  `ifBlank{}` 回退、`onClick` 内的分享文案)改用 `context.getString()` 或提前在组合期取值。
  带变量的文案一律用位置占位符(`%1$d` / `%1$.1f` / `%1$s`)。
- **附带修正**:「关于」页版本号原本手写为 `1.1.0`,与 `build.gradle.kts`
  的 `1.2.0` 已经不一致 —— 改为读 `BuildConfig.VERSION_NAME`,从此不会漂移。

### 6.1 本轮验证手段(针对"大规模机械改写"的风险)

机械改写的风险是**资源名拼错 / 漏 import / 在非组合上下文调用 `stringResource`**,
这三类都是硬编译错误。因此本轮补了针对性校验:

1. **资源引用闭合性**:用 XML 解析器读 `strings.xml`(验证合法性 + 资源名唯一),
   再全量扫描 `.kt` 中所有 `R.string.X` 引用 —— **193 个引用全部命中定义,0 缺失**。
2. **import 完备性**:凡调用 `stringResource(` 的文件必须 import
   `androidx.compose.ui.res.stringResource`;凡引用 `R.string.` 且不在
   `com.honglian.smartcycling` 包内的文件必须 import `R` —— **0 违规**。
3. **占位符类型核对**:逐个比对 `%d` / `%f` 对应的 Kotlin 字段类型
   (如 `OfflineMapEntity.minZoom:Int` → `%d`、`RideState.calories:Double` → `%f`、
   `tileCount:Long` → `%d`、`maxHeartRateBpm:Int` → `%d`),避免
   `IllegalFormatConversionException` 这类**运行时**崩溃。
4. **组合上下文核对**:逐处确认 `stringResource` 的调用点都在 `@Composable` 作用域内
   (为此把 `RideScreen.speedSourceLabel` / `fmtDuration` 改标为 `@Composable`,
   并把分享文案、`ifBlank` 回退文案、Marker title 提到组合期或改用 `context.getString`)。
5. **残留扫描**:对 11 个 UI 文件做"非注释行内的中文字符串字面量"扫描 —— **残留 0**。
6. **括号配平**:逐字符状态机(能正确跳过字符串/字符字面量与注释)全工程复扫 —— **全部配平**。

### 6.2 本轮仍未做(受环境限制)

- **依旧未编译、未运行**。本机无 JDK 17 / Android SDK / gradle-wrapper.jar。
  上述 6 项静态校验能覆盖"资源引用类"错误,但**不能替代真实编译**;
  仍需在 Android Studio 或 CI 跑 `./gradlew :app:assembleDebug` 与
  `./gradlew :app:testDebugUnitTest`。
- 未在真机验证:骑行页常亮开关的实际生效与退出清除、方向锁定在颠簸场景的表现。
- **未资源化**的非 UI 文案:各 `enum` 的 `label`(`WheelPreset`、`MapCrs`、
  `OfflineMapFormat`)以及非 Compose 层的日志/错误消息仍在代码里 ——
  枚举 label 需要改成持有 `@StringRes` 才能资源化,属于独立重构,本轮未做。

## 七、第六轮对抗审查:P1 单位切换 + 自动分圈 + 断点续记

本轮实现上一轮路线图里的 P1 三项。它们都是**纯本地逻辑、不需要新依赖、改动可控**的功能,
但共同点是"会写数据、会算数字"——恰恰是最容易悄悄算错却看不出来的地方。
因此本轮审查的重点从"能否编译"转向"**算得对不对**"。

### D18 —(编译阻断,真实存在)`fmtDistance()` 被删除但仍在调用

- **现象**:`RideScreen.kt` 的 `TurnBanner` 里 `fmtDistance(info.routeRemainMeters)` 仍在调用,
  而该私有函数已在上一步的单位制改造中被删除。这是**硬编译错误**,整个 module 无法构建。
- **根因**:删除函数时只检查了"我改过的那些调用点",没有做全工程残留扫描。
- **修复**:改为 `Units.shortDistanceText(info.routeRemainMeters.toDouble(), units)`,
  顺带让转向卡的距离也跟随单位制。
- **教训**:删函数必须配一次 `grep "\bname\s*("` 全工程残留扫描,已纳入本轮校验脚本。

### D19 —(数值缺陷,真实存在)`Laps` 把**毫秒**当**秒**用,均速算小 1000 倍

- **现象**:`LapSplit.durationSec` 直接取 `lapSeconds`,而 `lapSeconds` 累加的是
  `timestampMs` 之差(毫秒);`avgSpeedKmh = km / (lapSeconds / 3600.0)` 里也把它当秒。
  结果:一圈骑了 30 秒,分圈却显示 `durationSec = 30000`、均速只有实际值的 1/1000。
- **根因**:变量名 `lapSeconds` 与实际单位不符,掩盖了单位错误。
- **修复**:重命名为 `lapMillis` 并在注释里写明单位;结算时一次性折算 ——
  `durationSec = lapMillis / 1000`、`hours = lapMillis / 3_600_000.0`。
- **测试覆盖**:`LapsTest` 用 10 s 间隔的轨迹断言"每圈 3 段 = 30 s、均速 ≈ 40.03 km/h",
  这个断言在旧实现下会得到 30 000 s / 0.04 km/h,必然失败。

### D20 —(数值缺陷,真实存在)跨圈时把海拔基线重置回**整段起点**

- **现象**:`resetLap()` 里 `lastAltitude = lapStartAltitude`,而 `lapStartAltitude` 是
  **整段第一个点**的海拔。于是新一圈的首个高差被算成"起点 → 圈首"的整段落差。
  爬升 200 m 的路线,从第 2 圈起每圈都会凭空多记约 200 m。
- **根因**:把"每圈独立统计"误解为"每圈重置基线"。圈边界点本身就是上一圈的末点,
  它才是新圈的正确基线;重置回整段起点反而制造了一个跨圈的巨大假高差。
- **修复**:删掉 `lapStartAltitude`,`resetLap()` 不再触碰 `lastAltitude`。
- **测试覆盖**:`LapsTest.跨圈不会把整段落差算进新一圈的爬升` 断言每圈爬升恒为 30 m ——
  旧实现下第 2 圈会得到 60 m。

### D21 —(架构决策)分圈不建表,断点续记用哨兵值

两项都是"看起来该加一张表 / 加一列,其实不该"的决策,已写进 `README.md` 的第一性原理章节:

- **分圈**:边界完全由"轨迹点 + 阈值"决定,是可重算的派生数据。物化成表要额外承担
  写入时机 / 迁移 / 级联删除三处一致性负担,却换不来任何新信息。只在 `rides` 存
  `lapDistanceM`(阈值),分圈一律 `Laps.split()` 现算 —— **历史记录因此也能立刻显示分圈**。
- **断点续记**:用 `endedAt = 0` 当哨兵,而不是新增 `isInProgress` 列。省一次迁移,
  且"进行中"与"已完成"共用同一张表与同一套级联删除;历史查询只需 `endedAt > 0`。

### D22 —(数据迁移)v4 → v5 的 `NOT NULL DEFAULT 0` 契约

新增 `rides.lapDistanceM` 与 `track_points.heartRateBpm`。两列都必须在迁移里写成
`ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0`,**并且**在实体上标注
`@ColumnInfo(defaultValue = "0")` —— 两者缺一,Room 的迁移后 schema 校验就会抛异常。
历史记录两列取默认 0,表现为"没有分圈、没有心率",UI 自动降级,不会报错。

### D23 —(一致性缺陷)界面切到英制后,语音播报仍说"公里 / 米"

- **现象**:单位制只改到了 Compose 层。TTS 走 `context.getString(R.string.navi_tts_start,
  meters / 1000.0)`,而该资源文案硬编码为"…全程约 %1$.1f 公里",转向播报同理是"%1$d 米"。
  结果:屏幕显示 "7.7 mi",耳朵听到"全程约 12.3 公里"。
- **根因**:`NaviVoiceGuide` 是**非组合上下文**的独立组件,不读 `CompositionLocal`,
  因此没有拿到单位制。
- **修复**:给 `NaviVoiceGuide` 加 `units` 参数(由 `RideScreen` 传入),
  新增 `spokenDistance()` 把距离格式化成**可朗读**文本。
- **一个关键细节**:语音**不能**直接用 `Units.shortDistanceText()` —— 它返回 "320 m" / "1.5 km",
  而中文 TTS 会把 "m" 念成字母 M。因此单位改用语言里的**词**
  (`unit_word_km` / `unit_word_meter` / `unit_word_mile` / `unit_word_foot`),
  随 `strings.xml` 本地化。公制下的播报文本与改造前**逐字一致**("120 米" / "12.3 公里"),
  英制下才切换为"英尺 / 英里"。

### D24 —(范围误判 + 文档夸大)上一轮的"非 UI 文案"分类过粗,漏掉了真实用户可见文案

- **现象**:第五轮(D17)宣称"全部 UI 文案已资源化",并把剩余 74 处字面量归为
  "非 UI 文案(日志/错误消息/枚举 label)"一笔带过。本轮用**泛化后的扫描器**复核,
  发现其中相当一部分**确实会显示到界面上**:
  - `MapViewModel` 的 `status`(`"定位中…"` / `"路线已规划,可以开始骑行"` …)
    由 `MapScreen` 第 269 行直接渲染到状态条;
  - `OfflineMapsViewModel` 的 `message`(导入进度 / 结果)由 `OfflineMapsScreen` 第 197 行渲染;
  - `MapCrs.label` / `WheelPreset.label` 由坐标系、车轮周长选择弹窗渲染;
  - `OfflineMapInspector` 的失败原因经 `ImportResult.message` 展示。
- **根因(值得记住)**:上一轮的残留扫描**按目录白名单**判定"是不是 UI"
  (`ui/`、`nav/`、`MainActivity.kt`)。这个口径天然漏掉两类:
  ① `offline/OfflineMapView.kt` 这类**放在非 UI 目录下的 Compose 组件**;
  ② 由 ViewModel 持有、但最终被界面渲染的**字符串状态**。
- **本轮实际修复的两处**(属于上面的第 ① 类,一行即可,已修):
  - `offline/OfflineMapView.kt`:`destMarker.title = "目的地"` → `context.getString(R.string.map_marker_destination)`
    (该资源早已存在却没用上);
  - `export/RideExporter.kt`:系统分享面板标题 `"分享 GPX 轨迹"` → 新增 `R.string.gpx_share_chooser`。
- **第 ② 类未修**:它需要把"枚举 / 状态机里的 `String`"改成"`@StringRes` + 格式化参数",
  再由界面层解析 —— 跨 6 个文件的独立重构。**本轮不做,但已在 `README.md` 中如实列出缺口**
  (此前的表述"所有用户可见文案已抽取"属于夸大,已改写)。
- **校验器改进(核心收获)**:判定"是否 UI"**不能按目录**。已把规则改为
  "路径含 `ui|nav|screens|components` **或**文件名匹配 `*Activity|*Screen|*Dialog|*View|*Sheet|*Page`",
  并把泛化后的脚本收进技能 `android-kotlin-static-verification`(`scripts/resource_check.py`),
  以免下次再漏。

### 7.1 本轮验证手段

1. **全工程残留扫描**:对已删除符号(本轮为 `fmtDistance`)做 `\bname\s*\(` 全量扫描 —— **0 残留**。
   (这正是 D18 的成因,现已成为固定检查项。)
2. **资源引用闭合性**:`strings.xml` 用 XML 解析器读取(213 条、无重名),
   全量扫描 `.kt` 的 `R.string.X` 引用 —— **212 个引用全部命中,0 缺失**。
   唯一"未引用"的 `app_name` 由 `AndroidManifest.xml` 的 `android:label` 使用(非 `.kt` 引用)。
3. **占位符数量核对(本轮修好了两个校验器自身的坑)**:
   用**括号深度扫描**取调用的完整实参列表,而不是正则 `[^()]*` ——
   后者遇到 `spokenDistance(context, a, b)` 这类嵌套实参会**静默跳过整条检查**
   (假阴性,比假阳性更危险)。同时剔除 **Kotlin 尾随逗号**:本项目风格是每个实参独占一行、
   末尾留逗号,不剔除会把"1 个实参"数成 2 个(8 处假警报)。
   修正后:`stringResource` / `getString` 全部匹配 —— **0 错配**。
4. **占位符类型启发式核对**:针对最危险的一类 —— "String 喂给 `%f`"会在运行期抛
   `IllegalFormatConversionException`。建立"返回 String 的已知函数名"白名单
   (`Units.*Text` / `formatDuration` / `formatSize` / `spokenDistance` …),
   与 `%n$d/%n$f` 的实参交叉比对 —— **无疑似错配**。
   首轮曾报出 1 处误报(`formatSize(entity.sizeBytes)` 因函数名含 `size` 被误判为数值),
   已通过把 `formatSize(` 加入白名单消除。
5. **括号配平**:逐字符状态机(正确跳过字符串 / 字符字面量 / 行注释 / 块注释 / 三引号字符串),
   主源码 63 个 + 测试 7 个文件 —— **全部配平**。
6. **UI 层中文硬编码残留扫描**:18 个 UI/Nav/MainActivity 文件,剥离注释后扫描
   字符串字面量中的 CJK —— **残留 0**。
7. **关键符号 import 完整性**:对 9 个改动文件逐一确认新增符号
   (`Units` / `UnitSystem` / `Laps` / `LapSplit` / `Context` / `horizontalScroll` /
   `rememberScrollState` / `HorizontalDivider` / `Icons.Outlined.Flag`)的 import 或通配符覆盖
   —— **全部就位**。
8. **主题 token 核对**:确认新代码引用的 `BrandGreen` / `StopRed` / `DataValue` /
   `PanelBgTop` / `GlassBg` / `DividerNavy` / `CardBg` / `SpeedText` / `DataLabel` / `BrandCyan`
   均在 `ui/theme/HudColors.kt` 有定义。
9. **算法独立复现(本轮最强的一环)**:在无 JDK 的前提下,用 Python **逐行等价重写**
   `Laps.split()` 与 `Units` 换算(注意照抄 Java 的 `Math.toRadians` 实现
   `angdeg / 180.0 * PI`,与 `angdeg * (PI/180)` 末位可能不同),
   再跑一遍 `LapsTest` / `UnitsTest` 的全部期望值 —— **12 组断言全部成立**。
   更关键的是,同一脚本里**刻意复现了 D20 的旧实现**,得到各圈爬升 `[30, 60, 90, 100]`
   而非正确的 `[30, 30, 30, 10]` —— 这证明该回归测试确实能捕获缺陷,而不是"写了个恒真断言"。
10. **浮点边界规避**:`LapsTest` 刻意不踩"恰好等于阈值"的浮点相等边界
    (用"2 段再少 1 米"代替"恰好 2 段"),避免测试本身成为不稳定源。

### 7.2 本轮仍未做(受环境限制)

- **依旧未编译、未运行**。本机无 JDK 17 / Android SDK / gradle-wrapper.jar。
  D18 再次说明"人工检查"会漏 —— 上述 8 项静态校验能覆盖资源引用 / 括号 / 残留 / 符号类错误,
  但**不能替代真实编译**。仍需在 Android Studio 或 CI 跑:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:testDebugUnitTest`(应执行 `LapsTest` 11 例、`UnitsTest` 9 例,
    以及既有的 `GeoTransformTest` / `TileMathTest` / `CscCalculatorTest` / `HrParserTest` / `GpxFormatTest`)
- 未在真机验证:自动分圈在真实 GPS 采样率下的圈长误差、断点续记在"杀进程"场景下的恢复完整度、
  Room v4→v5 迁移、英制下各页面排版是否会因数值变长而换行。
- 云同步(`CloudSyncRepository`)载荷仍**未包含** `lapDistanceM` 与逐点心率,
  云端目前只存 lat/lon/speed/时间戳 —— 与上一轮同一处遗留,未做以免在未知表结构下擅自改动。
- **接入新语言时需补译**语音单位词(`unit_word_km/meter/mile/foot`)。
  本轮只保证中文下的播报自然;英制 + 非中文 TTS 的组合尚未实测。
- **既有死代码**:`RideScreen.kt` 的 `HeroStat` / `StatChip` 两个私有 Composable
  已无任何引用(仅产生"未使用"警告,不影响构建),可择机清理。
- **仍待资源化**的非 UI 文案:`WheelPreset` / `MapCrs` / `OfflineMapFormat` 等枚举的 `label`
  (需改为持有 `@StringRes`,属独立重构),与上一轮一致。
- 本轮用于静态校验与算法独立复现的脚本存放在 `.workbuddy-ai/checks/`(该目录已在
  `.gitignore` 中),可在无 JDK 环境下重复运行:
  - `static_check.py` —— 资源闭合 / 占位符数量与类型 / 括号配平 / 中文残留 / import 完整性
  - `laps_simulation.py` —— `Laps.split()` 与 `Units` 换算的独立复现(含 D20 旧实现对照)


---

## 八、第七轮对抗审查:CI 首次真正编译 + 单元测试首次真正执行

本轮的起点是用户反馈"CI 报错了"。结论是**两个独立问题串在一起**,而且第一个问题
长期把第二个问题挡在后面 —— 也就是说,前面几轮所有"静态校验全绿"的结论,
**都建立在一个从未被编译器检验过的仓库上**。

### D25 —(流程缺陷,真实存在且影响面最大)CI 卡在编译之前的步骤,长期无人察觉

`.github/workflows/build-apk.yml` 自 2026-07-09 起未改动,2026-07-16 还成功过,
之后连续 4 个提交全红,**每次耗时都只有 20 秒左右**。

根因与 Kotlin 代码无关:`android-actions/setup-android@v3` 的 `packages` 默认值是
`"tools platform-tools"`,而 Google 已把独立的 `tools` 包从 SDK 仓库下架(被
cmdline-tools 取代)。新版 sdkmanager(16.0)遇到找不到的包**直接 exit 1**:

```
Warning: Failed to find package 'tools'
Error: The process '.../cmdline-tools/16.0/bin/sdkmanager' failed with exit code 1
```

修法:显式覆盖 `packages`,只装真正需要的 `platform-tools platforms;android-34
build-tools;34.0.0`,顺带摆脱对 runner 镜像预装内容的隐式依赖。

**可复用的判据**:① 看耗时 —— 真编译一个 Compose 项目不可能 20 秒完成,
耗时异常短说明根本没走到编译,该查 workflow 的**前置步骤**而不是 Kotlin 代码;
② 看历史 —— 用 `gh run list` 与 `git log --date=short` 对齐时间线,若"最后一次绿的 CI"
早于某次大重构,则那次重构**从未被编译过**,必须当作"未验证代码"看待。

### D26 —(编译阻断 ×100)双主题重构的 95 处 `MaterialTheme.typography.<短名>` 不存在

修好 CI 后立刻暴露 100 条编译错误(**Kotlin 编译器默认最多只报 100 条**,实际可能更多)。

项目自定义的 `AppType` 使用短名(`title`/`subtitle`/`body`/`label`/`caption`/`display`),
而 Material3 的 `Typography` 只有 `titleLarge`/`bodySmall` 这类名字 —— 两者命名体系不同,
`MaterialTheme.typography.title` 是**硬编译错误**。涉及 5 个屏幕:
MapScreen(14) / HistoryScreen(18) / SettingsScreen(21) / PairingScreen(21) / OfflineMapsScreen(22)。
这 5 个文件里 `MaterialTheme` **只**用于排版,说明原作者本意就是 `AppType.*`,只是漏了改。
统一替换为 `AppType.*` 并移除因此变为无用的 import。

### D27 —(编译阻断 ×5)重构后签名变更,调用点未同步

同一批重构留下的其他四类错误:

| 位置 | 编译器报错 | 根因 |
|---|---|---|
| `MapViewModel` | `Null can not be a value of a non-null type LatLng` | `stateIn` 是 `Flow<T>` 的扩展,T 由**接收者**固定;`StateFlow<LatLng?>` 的返回类型**不会反向约束**接收者,于是 T 被推成非空 `LatLng`,`catch{emit(null)}` 与 `stateIn(...,null)` 双双报错。抽出显式标注 `Flow<LatLng?>` 的中间属性(且**必须声明在引用它的属性之前** —— 类属性按声明顺序初始化) |
| `AppNav` | `No value passed for parameter 'initial'` | 普通 `Flow` 的 `collectAsState()` **没有无参重载**(`StateFlow` 才有) |
| `OfflineMapInspector` | `'when' must be exhaustive, add 'FOLDER' branch` | 枚举新增了成员,`when` 未同步 |
| `RideScreen` | `No value passed for parameter 'accent'` | `SpeedRing` 新增必填参数 `accent`,两处调用点未更新 |

**共性**:四类都发生在"改了一个文件的定义、漏了另一个文件的调用点"。
只看被编辑的文件永远发现不了,必须做全工程调用点比对(见 8.1 新增脚本)。

### D28 —(数值缺陷,真实存在)`TileMath.latToTileY` 在极区返回 `-1`,行号越界

单元测试 job 首次运行即抓出(51 例中 3 例失败,全部落在离线地图那条线)。

边界纬度 ±85.0511… 在数学上正好落在 `y = 0` / `y = n-1`,但浮点误差会让中间量
变成约 `-1e-10` 的"负零",`floor()` 于是取到 **-1**(另一端则取到 `n`)。
拿这个行号去请求瓦片就是越界。实测 `lat=89.9 / zoom=4` 得到 `y=-1`。

另有一处叠加错误:纬度上限被写成四舍五入的 `85.05112878`,它比精确值
`degrees(atan(sinh(PI))) = 85.05112877980659` 大 1.9e-10 度,恰好把"正好落在边界的纬度"
推到框外。**两步都要修**:① 改用精确常量;② 结果兜底 `coerceIn(0, limit(zoom)-1)`
(第 ② 步才是真正兜住浮点误差的那一步 —— 即使常量精确,`floor(-1e-10)` 仍是 -1)。

### D29 —(测试缺陷)BD09 往返容差定得比公式固有精度还紧

`GCJ↔BD09` 用的是业界公开的**近似**互逆公式(正向用 GCJ 坐标算扰动项,反向用扣掉
0.006/0.0065 偏移后的坐标算扰动项),**并非严格数学逆**。实测华中华东境内
12300 个采样点,最大往返残差 `1.86e-6` 度 ≈ **0.21 米**,而测试容差写的是 `1e-6` 度。

**这不是代码缺陷**:所有工具(含百度自家)都用这组公式,把反向改成严格数值逆反而
会与它们不一致,导入导出更容易对不上。故容差改为 `5e-6` 度(≈0.56 米)并写明理由;
同时补一条"偏移量必须达数百米"的断言 —— 否则一旦实现退化成"原样返回",
往返断言会因 `0 == 0` 恒真,回归测试沦为摆设。

### D30 —(坐标系语义缺陷,真实存在,本轮**有意不修**只记录)

`GeoTransform.outOfChina` 存在两处固有问题:

1. **正反向判定不一致**。正向 `wgs84ToGcj02` 用 **WGS** 坐标判定,反向 `gcj02ToWgs84`
   用 **GCJ** 坐标判定。偏移量约 0.003~0.006 度,于是紧贴判定框**内侧**的点,
   其 GCJ 坐标可能落到框外 → 反向时被判为"未偏移"而**拒绝还原**,往返误差实测达
   **350~900 米**(南 472 / 北 894 / 西 350 / 东 900)。受影响的是一条约 0.006 度
   (≈700 米)宽的窄带;框的北边与东边落在黑龙江边境(漠河 / 抚远附近),骑车能到。
2. **判定框是粗糙矩形**。它同时框进了印度北部 / 尼泊尔 / 孟加拉 / 朝鲜半岛 / 俄远东,
   这些境外点会被**误加偏移**(实测海参崴 (43.12, 131.89) 约偏 500 米)。
   这是所有同款实现的通病,精确判定需引入国界多边形数据。

**为什么不在本轮修**:这属于坐标系语义变更,正确性只能靠真机在边境实测确认。
已用 Python 验证过一种改法(迭代内部不做判定、仅用收敛结果判定"输入是否被偏移过"):
境内内部点结果**逐位一致**(差异 7e-15)、北边从 894 米降到 **0.11 米**,
但另外三条边无改善 —— 即它只是把突变带挪了个位置,并未消除。
**需要产品侧先决定取舍**(宁可保护境内边界、还是宁可保护境外边界)再动手。
两处限制连同实测数据与已试过的改法均已写入 `GeoTransform.outOfChina` 的 KDoc。

### 8.1 本轮验证手段

1. **把 CI 本身当作被测对象**:先用 `gh run view <id> --log-failed` 定位失败步骤,
   确认是"编译前"还是"编译中",再决定改 workflow 还是改代码。
2. **时间线交叉验证**:`gh run list`(谁绿谁红、各耗时) × `git log --date=short`,
   据此判定哪些提交属于"从未被编译过"。
3. **新增 `arity_check.py`** —— 把三类"编译器必报、纯文本可推断"的错误前移:
   `[A]` 调用点实参数量 / 命名实参 / 实参过多;`[B]` `when` 对枚举穷尽性;
   `[C]` `Flow.collectAsState()` 缺 `initial`。
   **调参过程中踩到并修掉的脚本自身陷阱**(初版 163 条噪音 → 收敛到 1 条已知误报):
   - 形参名取成了**类型名**(`file: File` → `File`)→ 所有命名实参都误报。
     正解:形参名是**顶层 `:` 之前**的最后一个标识符。
   - 剥离字符串时**清空**内容 → `finish("定位失败")` 的实参被吃掉 → 海量假警报。
     正解:换成**等长非空占位符**;注释才清成空白(必须等长,否则行号错位)。
   - `when` 只该看**模式侧**。两次误报:先"扫整个 body"(把分支体里的返回值当模式),
     再"按 `->` 切分回捞模式行"(裸枚举常量也满足"模式行"的字符集)。
     最终采用最省事又可靠的判据:**枚举常量后紧跟 `,` 或 `->` 才算模式**。
   - `StateFlow.collectAsState()` 是**合法**的无参重载 → 必须先判接收者类型,
     否则 45 条里 44 条是误报。
   - 声明表必须存**重载列表**,调用点匹配上任一即通过;`get/set/invoke` 等访问器名直接跳过。
4. **第二语言独立复现关键数值**:用 Python 重写 `TileMath` 的极区裁剪与
   `GeoTransform` 的全部换算,用来
   (a) 证实 `floor(-1e-10) = -1` 这个"负零"陷阱,
   (b) 量化 BD09 的固有残差(1.86e-6 度)与边界突变(350~900 米),
   (c) 验证 TileMath 修复后**常规点结果与修复前逐位一致**,
   (d) 验证"已试过的 GeoTransform 改法"在境内内部点逐位一致、北边改善但三边无改善。
5. **把测试 job 拆成独立 job**:`assembleRelease` **完全不编译测试源码**,所以
   `app/src/test` 下的 7 个文件、51 个 `@Test` 此前**既没被编译过也没被运行过**。
   拆开的好处是测试失败**不会**让已产出的 Release APK 拿不到,但会明确标红不被忽略;
   并加 `if: always()` 上传 HTML 报告便于定位。

### 8.2 本轮仍未做(受环境限制)

- **仍未在真机运行**。本轮首次拿到"编译通过 + 51 个单元测试通过"的客观证据,
  但编译通过 ≠ 运行正确。仍需真机验证:权限流程、离线瓦片包实际导入与渲染、
  BLE 实际连接、Room v4→v5 迁移、自动分圈在真实 GPS 采样率下的圈长误差、
  断点续记在"杀进程"场景下的恢复完整度、英制下各页面排版。
- **D30 有意未修**,理由与已试过的改法见上。
- 云同步载荷仍**未包含** `lapDistanceM` 与逐点心率(与上一轮同一处遗留)。
- **既有死代码**:`RideScreen.kt` 的 `HeroStat` / `StatChip` 已无引用,可择机清理。
- **仍待资源化**的非 UI 文案:`WheelPreset` / `MapCrs` / `OfflineMapFormat` 等枚举的 `label`。
- `AppType.metric` / `AppType.metricSmall` 两档字号当前**无任何引用**
  (HUD 大数字是就地写 `fontSize`),属设计系统与实现脱节,可择机统一。
