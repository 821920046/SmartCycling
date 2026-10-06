# 智能骑行 ProGuard / R8 规则
# =================== 通用保留 ===================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes SourceFile, LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-renamesourcefileattribute SourceFile

# JNI native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# =================== Kotlin ===================
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlin.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# =================== Jetpack Compose ===================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# =================== Room ===================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# 本项目数据模型与状态(防字段被裁剪)
-keep class com.honglian.smartcycling.data.** { *; }
-keep class com.honglian.smartcycling.ride.RideState { *; }
-keep class com.honglian.smartcycling.BuildConfig { *; }

# =================== Lifecycle / ViewModel ===================
# ViewModelProvider 通过反射调用 (Application) 构造器实例化 ViewModel;
# Release 混淆下若构造器被裁剪/优化，viewModel() 会抛异常导致进入界面即闪退。
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# =================== Nordic BLE ===================
-keep class no.nordicsemi.android.ble.** { *; }
-keep class no.nordicsemi.android.support.v18.scanner.** { *; }
-dontwarn no.nordicsemi.android.**

# =================== 高德地图 / 导航 / 定位 / 搜索 ===================
# 官方要求:大量反射与 JNI,必须完整保留,否则 Release 会崩溃。
-keep class com.amap.api.** { *; }
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keep class com.a.a.** { *; }
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.navi.** { *; }
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.services.** { *; }
-keep class com.amap.api.col.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.amap.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**

# =================== 其它 ===================
# 已移除 Google Play Services 定位,仅防残留引用告警。
-dontwarn com.google.android.gms.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
