<p align="center">
  <img src="docs/logo.png" alt="智慧骑行 SmartCycling" width="200" />
</p>

<h1 align="center">智慧骑行 SmartCycling</h1>

<p align="center">
  面向安卓的智能骑行 App · 自动配对传感器 · 高德地图导航 · 实时骑行数据
</p>

<p align="center">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-3ddc84" />
  <img alt="language" src="https://img.shields.io/badge/language-Kotlin-7f52ff" />
  <img alt="UI" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285f4" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-blue" />
  <img alt="license" src="https://img.shields.io/badge/license-MIT-green" />
</p>

---

面向安卓的骑行 App。开屏自动配对**迈金 S314** 蓝牙速度/踏频传感器 → 进入地图 → 输入目的地开骑 → 自动跳转横屏数据界面(左导航 / 右速度与数据)。

### 核心能力

- 🤖 **智能分析** — 速度/踏频/里程/时长/均速实时计算与历史统计
- 🗺️ **路线规划** — 高德地图目的地搜索与骑行路径规划、实时导航
- ❤️ **健康数据** — 骑行记录与轨迹本地存储,可回顾每次骑行
- 🛡️ **安全守护** — 前台服务锁屏保活,骑行中数据不中断

## 技术栈

- Kotlin + Jetpack Compose + Material3
- MVVM + Repository + 轻量 DI(`core/Container`)
- Nordic Android-BLE-Library(`ble-ktx`)接入 CSC(0x1816 / 0x2A5B)
- FusedLocationProvider 定位与里程积分
- Room 本地存储骑行记录与轨迹
- Foreground Service 锁屏保活

## 目录结构

```
app/src/main/java/com/honglian/smartcycling/
├─ SmartCyclingApp.kt        # Application + 通知渠道
├─ MainActivity.kt           # 权限、自动配对、横屏切换、前台服务
├─ core/Container.kt         # 轻量依赖容器
├─ ble/                      # CSC UUID / 解析 / 计算 / S314 BleManager
├─ pairing/                  # 扫描与自动配对
├─ location/                 # GPS 轨迹与速度
├─ data/                     # Room 实体 / DAO / 仓库
├─ ride/                     # RideState / RideViewModel / RideService
├─ ui/components/            # SpeedRing / DataGrid / 地图占位
├─ ui/screens/               # Pairing / Map / Ride / History
└─ nav/AppNav.kt             # 配对→地图→骑行 导航
```

## 构建

> 本仓未附带 `gradle-wrapper.jar`(二进制)。首次构建任选一:
>
> 1. **用 Android Studio 打开**(Ladybug 及以上),IDE 会自动生成 wrapper 并同步;或
> 2. 本地已装 Gradle 8.9则执行:`gradle wrapper --gradle-version 8.9`,之后 `./gradlew assembleDebug`。

环境要求:JDK 17、Android SDK 34、minSdk 26。

## 关键工程决策(第一性原理)

1. **单个 S314 同一时刻只能测速度或踏频。** 因此速度来源自适应:3 秒内有轮转传感器数据则用传感器,否则回退 GPS(`RideViewModel.SpeedSource`)。
2. **时间戳 uint16 会在 65536 翻转**,`CscCalculator` 均取模修正。
3. **看门狗机制**:任一数据源 3 秒无更新则归零,避免“停车但速度不降”。
4. **里程优先 GPS 积分**(过滤 >25m 精度与 <1m 静止漂移);无定位时用轮转圈数 × 轮周长回退。

## 地图 SDK 接入

`ui/components/MapView.kt` 目前为占位。接入真实地图时将其换为 `AndroidView { AMapView / MapboxMapView }`,并在 `app/build.gradle.kts` 打开高德依赖、在清单配置 Key。选型对比见项目页《地图 SDK 选型对比》。

## 开源合规

本项目采用 MIT。接入第三方代码时需避开 GPL/AGPL 传染性许可(如 pizero_bikecomputer 为 GPL-3.0),仅作算法参考。

## 对抗审查

见 `REVIEW.md`。
