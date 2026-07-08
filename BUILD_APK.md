# 如何得到可安装的 APK

工程本身是源代码,需编译后才能得到 APK。下面三种方式任选一。

---

## 方式 A、GitHub 云端自动构建(无需本地装环境,推荐)

工程已内置流水线 `.github/workflows/build-apk.yml`。

1. 在 [github.com](https://github.com) 新建一个仓库(Private 即可)。
2. 把解压后的 `SmartCycling` 内容上传到仓库:
   - 网页方式:仓库页 `Add file → Upload files`,把整个目录拖进去提交。
   - 或命令行:
     ```bash
     cd SmartCycling
     git init && git add . && git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/<你的用户名>/<仓库名>.git
     git push -u origin main
     ```
3. 推送后打开仓库的 **Actions** 页,等 `Build APK` 跑完(约 3–5 分钟)。
4. 进入这次运行,在页面底部 **Artifacts** 下载 `smart-cycling-debug-apk`,解压即 `app-debug.apk`。
5. 传到手机,允许“安装未知来源应用”,点击安装。

> 云端运行时才会联网下载 Compose / Nordic BLE / Room 等依赖,你本地无需任何环境。

---

## 方式 B、Android Studio(本地,最直观)

1. 装 [Android Studio](https://developer.android.com/studio)(自带 SDK)。
2. `File → Open` 打开 `SmartCycling` 目录,等自动同步(会联网补依赖与 wrapper)。
3. `Build → Build App Bundle(s)/APK(s) → Build APK(s)`。
4. 产物:`app/build/outputs/apk/debug/app-debug.apk`。

或直接插手机(开 USB 调试),点 ▶️ Run 一键安装运行。

---

## 方式 C、命令行(本地已装 JDK17 + Android SDK)

```bash
cd SmartCycling
gradle wrapper --gradle-version 8.9   # 首次生成 wrapper
./gradlew assembleDebug               # 产出 app-debug.apk
```

需设环境变量 `ANDROID_HOME` 指向 SDK,并安装 platform android-34 与 build-tools。

---

## 注意

- 产出的是 **debug 版 APK**,可直接安装调试;上架需另行签名生成 release 版。
- 地图界面当前为占位,装上后左侧导航区为空白属正常;接入高德/Mapbox 后才会显示真实地图。
