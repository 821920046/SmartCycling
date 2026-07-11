plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.honglian.smartcycling"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.honglian.smartcycling"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }

        // 只保留真机常用架构:去掉模拟器专用的 x86/x86_64,大幅减小高德 native 库体积。
        ndk {
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
        }

        // 高德地图 Key(通过 -PAMAP_KEY=xxx 或 gradle.properties 注入)
        manifestPlaceholders["AMAP_KEY"] =
            (project.findProperty("AMAP_KEY") as String? ?: "PUT_YOUR_AMAP_KEY_HERE")

        // Cloudflare 中控地址与令牌(留空则 App 自动跳过云端上传)
        buildConfigField(
            "String",
            "CLOUD_SYNC_URL",
            "\"${project.findProperty("CLOUD_SYNC_URL") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "CLOUD_SYNC_TOKEN",
            "\"${project.findProperty("CLOUD_SYNC_TOKEN") ?: ""}\"",
        )
    }

    signingConfigs {
        // 固定签名证书:本地与云端构建 SHA1 一致,保证高德 Key 可用。
        create("shared") {
            storeFile = file("keystore/smartcycling.keystore")
            storePassword = "smartcycling"
            keyAlias = "smartcycling"
            keyPassword = "smartcycling"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // 高德导航 SDK 在 R8 混淆/资源压缩下极易崩溃(调试版导航正常、Release 混淆后闪退)。
            // 关闭混淆与资源压缩以保证导航稳定;安装包体积主要由 ABI 过滤控制(已去掉 x86)。
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // BLE - Nordic (Kotlin 扩展)
    implementation("no.nordicsemi.android:ble-ktx:2.7.5")
    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")

    // Room 本地存储
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 定位:统一使用高德定位(已含于下方 navi-3dmap-location-search 合包)。
    // 移除 Google play-services-location:国内无 Google Play 服务的手机上 FusedLocation 永不回调,
    // 会导致骑行里程/速度/实时跟随全部失效(病因之一)。

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 高德官方一体化合包:单个 AAR 已含 3D 地图 + 定位 + 搜索(地理编码/骑行路径规划)。
    // 必须用带 -location-search 后缀的固定版本坐标(普通 navi-3dmap 不含搜索包、单独 3dmap/location/search 又会 duplicate class)。
    implementation("com.amap.api:navi-3dmap-location-search:10.0.700_3dmap10.0.700_loc6.4.5_sea9.7.2")
}
