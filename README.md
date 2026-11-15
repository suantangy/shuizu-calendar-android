# 水族日历 · 空壳

纯原生 Android 空壳（Kotlin + Jetpack Compose），**只有壳，没有日历功能**。

- 包名：`com.suantangyu.shuizucalendar`
- 应用名：水族日历
- 版本：1.0.0（versionCode 1）

## 里面有什么

- 一个能编译、能安装的空应用，打开只显示“水族日历 / 空壳 · 从这里开始改”
- GitHub Actions：推到 `main` 就自动编 debug APK并发 Release

## 从哪里开始改

- 界面入口：`app/src/main/java/com/suantangyu/shuizucalendar/MainActivity.kt`
- 应用配置：`app/build.gradle.kts`（包名、版本号、依赖）
- 清单：`app/src/main/AndroidManifest.xml`

## 构建

推到 `main` 后看 Actions 的 `Build debug APK`，绿了在 Release 里下载 APK。

## 说明

这个壳是给你自己慢慢改的起点，日历的月历、农历、水历、宜忌、数据一律不在里面，不懂的地方再问。
