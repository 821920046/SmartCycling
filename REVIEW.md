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
