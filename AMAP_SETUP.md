# 高德地图接入与出包详细步骤

地图代码已集成完毕(地图显示 + 定位蓝点 + 目的地搜索 + 骑行路线规划)。
你只需申请一个高德 Key 并填入,就能构出一装即能看到真实地图的 APK。

---

## 一、先准备两个值(申请 Key 时要填)

| 项 | 值 |
| --- | --- |
| 应用包名 PackageName | `com.honglian.smartcycling` |
| 签名证书 SHA1 | `D8:91:0D:4B:7D:A1:60:57:1F:1D:00:3E:02:BA:67:A6:E3:DB:73:57` |

> 工程已内置固定签名证书 `app/keystore/smartcycling.keystore`(密码均为 `smartcycling`)。
> 本地、GitHub 云端都用它签名,所以 SHA1 永远是上面这个,不会变——这正是地图能在各处都显示的关键。
> （如你想自己核对 SHA1:
> `keytool -list -v -keystore app/keystore/smartcycling.keystore -storepass smartcycling -alias smartcycling`）

---

## 二、申请高德 Key

1. 浏览器打开 **高德开放平台** 并登录/注册(个人开发者即可,免费):
   https://lbs.amap.com/
2. 进入控制台 **应用管理 → 我的应用 → 创建新应用**,随便命名(如“智慧骑行”),类型选“出行/导航”均可。
3. 在该应用下点 **添加 Key → 服务平台选 Android 平台**,填写:
   - **发布版安全码 SHA1**:填上面表格里的 SHA1
   - **PackageName**:`com.honglian.smartcycling`
   - 勾选服务:同时勾选“地图”与“定位”与“搜索/路径规划”相关权限(默认均开)
4. 提交后获得一串 **Key**(形如 `a1b2c3d4...`),复制备用。

---

## 三、把 Key 填进工程(三选一)

**方式 1、本地写死(最简单)**
编辑项目根目录 `gradle.properties`,把最后一行改为:
```
AMAP_KEY=你刚拿到的Key
```

**方式 2、命令行传入(不改文件)**
```
./gradlew assembleDebug -PAMAP_KEY=你的Key
```

**方式 3、GitHub 云端构建用 Secret(推荐,不泄露 Key)**
- 仓库页 **Settings → Secrets and variables → Actions → New repository secret**
- Name 填 `AMAP_KEY`,Value 填你的 Key
- 内置的流水线会自动把它传给构建

---

## 四、出 APK

**云端(推荐,本地免装)**
1. 把工程传到 GitHub(见 `BUILD_APK.md`)
2. 设好上面的 `AMAP_KEY` Secret
3. Actions 跑完 → 下载 `smart-cycling-debug-apk` → 解压得 `app-debug.apk`

**本地**
```
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug -PAMAP_KEY=你的Key
# 产物:app/build/outputs/apk/debug/app-debug.apk
```

---

## 五、装到手机

1. 把 `app-debug.apk` 传到手机,允许“安装未知来源应用”,安装。
2. 首次打开同意权限:**位置**(精确)与**附近设备/蓝牙**必选。
3. 进地图页即可看到真实地图与当前位置蓝点;输入目的地点“骑行”会规划并绘制骑行路线。

---

## 六、地图白屏怎么排查

按概率排序:
1. **Key / SHA1 / 包名不匹配** — 最常见。确认高德后台填的 SHA1 与包名与本文一致。
2. **Key 没真正注入** — 确认 `gradle.properties` 或 `-PAMAP_KEY` 或 Secret 确实生效
   (打开未填时地图为空,占位值是 `PUT_YOUR_AMAP_KEY_HERE`)。
3. **隐私合规未同意** — 本工程已在 `SmartCyclingApp` 里调用合规接口,正常无需改。
4. **无网络/无定位权限** — 检查手机网络与已授予定位权限。

---

## 七、安全提醒

- 内置签名证书密码公开,仅适合个人调试/内部分发。
- 若要上架应用商店,请另行生成一个**不公开的 release 签名证书**,并用其 SHA1 重新申请高德发布版 Key。
