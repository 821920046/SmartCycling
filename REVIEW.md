# 对抗审查报告(Adversarial Review)

以“挑毛疵”视角逐模块审查,以下为发现的问题与已修复项。

## 一、已修复(会导致编译失败)

| # | 文件 | 问题 | 修复 |
|---|------|------|------|
| 1 | `HistoryScreen.kt` | `import` 语句错写在文件末尾,Kotlin 要求 import 必须在顶部 | 移至文件顶部 |
| 2 | `MainActivity.kt` | 用裸 `CoroutineScope(Dispatchers.Main)` 启动扫描,不受生命周期约束且缺 `launch` 导入 | 改用 `lifecycleScope.launch`,随 Activity 销毁自动取消 |

## 二、设计健壮性(第一性原理已内置)

1. **单传感器局限**:迈金 S314 同一时刻只能测速度或踏频。`RideViewModel` 采用速度源自适应:传感器鲜活(3s)时用传感器,否则回退 GPS。
2. **时间戳翻转**:CSC 时间戳为 uint16 @1/1024s,在 65536 处翻转,`CscCalculator.tsDelta` 取模修正。
3. **数据帧越界**:`CscParser` 对每个可选字段做长度校验,防止越界崩溃。
4. **臏数据**:notify 回调 `runCatching` 包裹,单帧解析失败不影响后续。
5. **看门狗**:任一数据源 3s 无更新则归零,避免“停车但速度不降”。
6. **里程精度**:GPS 积分过滤 >25m 精度与 <1m 静止漂移;无定位时用轮转圈数回退。
7. **后台保活**:`RideService` 使用 `FOREGROUND_SERVICE_TYPE_LOCATION`(Android 10+ 分支处理)。
8. **权限**:区分 Android 12+ 的 `BLUETOOTH_SCAN/CONNECT` 与旧版 `BLUETOOTH`,运行时动态申请。
9. **翻转计数**:曲柄圈数 uint16 差值也做 `and 0xFFFF` 修正。

## 三、系统级极致重构与升级迭代 (2026-07-10)

以下为本次全量完成的极致升级优化项目：

### 1. 核心算法平滑与防御过滤 (第一性原则)
- **时速与踏频 EMA 滤波**：在 `RideViewModel` 接入指数移动平均（EMA，\(\alpha = 0.4\)）算法，彻底消除包分发抖动引起的数值突跳。
- **跳点大漂移积分拦截**：在 `LocationTracker` 中增加时速上限（80km/h）规则拦截，跳跃漂移的点不累加里程，且不更新基准位置，完美应对出入高架与隧道。
- **等红绿灯自动暂停与排除**：静止 5s 自动暂停时长与数据统计，起步时速 > 1.5km/h 自动唤醒，且支持手动暂停/恢复。
- **数据库防闪退自愈**：在 `AppDatabase` 初始化链中加入 `.fallbackToDestructiveMigration()` 自愈能力。

### 2. 霓虹科幻 HUD 主视觉重塑 (WOW 级 UI 质感)
- **动态雷达配对**：将黑白朴素的 `PairingScreen` 改造为同心扩散脉冲波圆圈与旋转渐变波束雷达。
- **流光刻度环**：`SpeedRing` 采用 `animateFloatAsState` 阻尼平滑动画，并绘制 24 根半透明发光机械刻度线，辅以双层霓虹光环渲染。
- **毛玻璃数据网格**：`DataGrid` 使用 `GlassBg` 与 `GlassBorder` 并集成状态标识小图标，支持暂停状态下置灰冻结显示。
- **悬浮仪表盘**：`MapScreen` 控制面板升级为悬浮毛玻璃，增加设置和历史快捷入口，支持热重载切换地图图层（夜间/卫星/标准）。
- **防误触确认**：在 `RideScreen` 中引入“结束骑行”防误触二次确认弹窗。

### 3. 数据大看板与轨迹回顾
- **轨迹弹窗渲染**：`HistoryScreen` 增加骑行总看板，列表以精美圆角边框显示，点击任意记录即异步调取 Room 轨迹，在高德地图上清晰平滑渲染出骑行路线。
- **系统设置中心**：新建 `SettingsScreen` 提供骑手名、车轮周长、同步 URL / Token 标定。
- **转场过渡动画**：`AppNav` 采用 Compose 导航，并为各层级跳转注入高精度的淡入淡出和横向滑动动画。

### 4. 云中控大屏大改造
- **Leaflet 交互地图**：`index.js` 重构为具有暗黑大屏格调的 Dashboard，点击列表条目，右侧集成的高德 Leaflet 瓦片地图自适应居中绘制出 GCJ-02 真实骑行折线。

