package com.suantangyu.shuizucalendar

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 离线混合版：界面就是网页本身，不联网。
 *
 * 网页文件（index.html + calendar-data.zip）打在 APK 的 assets 里，
 * 首次启动解压到内部存储 files/web/，WebView 用 WebViewAssetLoader
 * 以 https://appassets.androidplatform.net/web/index.html 加载，
 * 页面里的 fetch("calendar-data/2026-08.json") 走的也是本地文件，
 * 全程没有任何网络请求，不需要 INTERNET 权限。
 *
 * index.html 一字未改；数据是原包 1212 个月 JSON 原样打包。
 */
class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先显示一行提示，数据准备好再换成 WebView
        val loading = TextView(this).apply {
            text = "正在准备日历数据…"
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(loading)

        Thread {
            prepareWebFiles()
            runOnUiThread {
                setContentView(createWebView())
            }
        }.start()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    /** 首次启动（或数据版本变化）把 assets 里的网页解压到内部存储。 */
    private fun prepareWebFiles() {
        val webDir = File(filesDir, "web")
        val marker = File(webDir, ".ready")
        if (marker.exists() && marker.readText() == DATA_VERSION) return

        webDir.deleteRecursively()
        webDir.mkdirs()

        // index.html 原样复制
        assets.open("web/index.html").use { input ->
            FileOutputStream(File(webDir, "index.html")).use { output ->
                input.copyTo(output)
            }
        }

        // 1212 个月 JSON 解压到 web/calendar-data/
        assets.open("web/calendar-data.zip").use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(webDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        marker.writeText(DATA_VERSION)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/web/",
                WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "web"))
            )
            .build()

        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // localStorage 存主题 cal-theme
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            loadUrl("https://appassets.androidplatform.net/web/index.html")
            webView = this
        }
    }

    companion object {
        /** 数据版本：改了 assets 里的网页或数据就升这个数，触发重新解压。 */
        const val DATA_VERSION = "1.1.0"
    }
}
