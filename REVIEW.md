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

## 三、待完善(非阻断,下一迭代)

- 地图 SDK 仍为占位(`ui/components/MapView.kt`),需接入高德/Mapbox 真实 MapView 与路径规划。
- 未附 `gradle-wrapper.jar`(二进制),首次需 IDE 或 `gradle wrapper` 生成(见 README)。
- 单元测试:建议为 `CscParser`/`CscCalculator` 补充 JUnit 用例(纯函数,易测)。
- 踏频/速度平滑:可选对 `SensorReading` 加 EMA 滤波降低拖尾拖动。
