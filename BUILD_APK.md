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
4. 进入这次运行,在页面底部 **Artifacts** 下载 `smart-cycling-release-apk`,
   解压即 `app-release.apk`。
5. 传到手机,允许“安装未知来源应用”,点击安装。

> 云端运行时才会联网下载 Compose / Nordic BLE / Room 等依赖,你本地无需任何环境。
>
> 该流水线包含两个 **job**:
> - `build` —— 构建并上传 Release APK(产物名 `smart-cycling-release-apk`)。
> - `test` —— 运行 `app/src/test` 下的 JVM 单元测试(坐标换算 / 瓦片数学 / 码表时间戳翻转 /
>   心率解析 / GPX 格式 / 单位换算 / 分段计圈),失败时会上传 HTML 报告。
>   两者独立,测试挂掉**不会**让你拿不到 APK,但会标红提示。

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

- 云端与本地默认产出的都是 **Release 版 APK**(`app-release.apk`),已开启 R8 混淆 + 资源压缩。
  调试请用 `assembleDebug`(产物 `app-debug.apk`),或直接插手机点 ▶️ Run。
- **签名**:工程内置固定签名证书 `app/keystore/smartcycling.keystore`(随仓库提交),
  debug 与 release 共用。这样本地与云端构建出的 SHA1 一致,**高德地图 Key 才能在两种构建下都可用**;
  换成自己的证书会导致高德 Key 失效,需同步去高德控制台改绑。
- 上架应用商店请另行生成你自己的 release 证书(不要用这个共享证书)。
- 地图为**双引擎**:在线底图走高德(路径规划 / POI / 语音),离线底图走 osmdroid 渲染
  本地瓦片包(MBTiles / ZIP / 瓦片文件夹 / GeoPackage)。未配置高德 Key 时在线部分不可用,
  需在构建时通过 `-PAMAP_KEY=xxx` 注入。
