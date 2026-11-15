# 水族日历 · 离线混合版

水族日历 2000–2100，离线可用，不联网。

- 包名：`com.suantangyu.shuizucalendar`
- 应用名：水族日历
- 版本：1.1.0（versionCode 2）

## 怎么做到的

- 界面就是 Cloudflare 网页版原样：`index.html` 一字未改，`calendar-data` 1212 个月 JSON 原样打包。
- 网页文件打在 APK 的 `assets/web/` 里（`index.html` + `calendar-data.zip` 约 4.3MB），首次启动解压到内部存储，WebView 用 WebViewAssetLoader 本地加载。
- 页面里的 `fetch("calendar-data/2026-08.json")` 读的也是本地文件，全程没有任何网络请求，所以 **不需要 INTERNET 权限，断网、飞行模式都能用**。
- 主题（`cal-theme`）存在 WebView 的 localStorage 里，和网页版一样。

## 结构

- 界面入口：`app/src/main/java/com/suantangyu/shuizucalendar/MainActivity.kt`（WebView + 首次解压）
- 网页原文件：`app/src/main/assets/web/index.html`（勿改）、`app/src/main/assets/web/calendar-data.zip`
- 应用配置：`app/build.gradle.kts`（包名、版本号、依赖）

## 构建

推到 `main` 后看 Actions 的 `Build debug APK`，绿了在 Release 里下载 APK。
