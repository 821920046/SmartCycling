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

        // 高德地图 Key(通过 -PAMAP_KEY=xxx 或 gradle.properties 注入)
        manifestPlaceholders["AMAP_KEY"] =
            (project.findProperty("AMAP_KEY") as String? ?: "PUT_YOUR_AMAP_KEY_HERE")
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
            isMinifyEnabled = true
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
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
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

    // 定位
    implementation("com.google.android.gms:play-services-location:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 高德一体化 SDK:单个 AAR 已含 3D 地图 + 定位 + 搜索(地理编码/骑行路径规划)。
    // 切勿再单独添加 3dmap/location/search,否则会因重复类(duplicate class)构建失败。
    implementation("com.amap.api:navi-3dmap:latest.integration")
}
