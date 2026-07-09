# 智慧骑行 ProGuard 规则
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Nordic BLE
-keep class no.nordicsemi.android.ble.** { *; }
-dontwarn no.nordicsemi.android.ble.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 高德地图 / 导航 / 定位 / 搜索(官方推荐:大量反射与 JNI,必须完整保留,否则 Release 会崩溃)
-keep class com.amap.api.** { *; }
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keep class com.a.a.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**

# Google Play Services 定位
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
