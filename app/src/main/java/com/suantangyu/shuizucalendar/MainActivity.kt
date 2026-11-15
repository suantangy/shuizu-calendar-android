package com.suantangyu.shuizucalendar

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
 *
 * 沉浸式：内容延伸到系统栏之下，状态栏/导航栏透明，不再有灰条；
 * 网页本身只有底部安全区（--safe-bottom），没有顶部安全区，
 * 所以壳在页面加载完后注入一段样式，把系统状态栏高度补进
 * .page 的 padding-top，第一个卡片不会被时间电量压住。
 * 状态栏图标颜色跟随页面主题（浅色主题深色图标，深色主题浅色图标）。
 */
class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // —— 沉浸式：内容延伸到系统栏之下，系统栏透明（顶部全屏，不再有灰条） ——
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        insetsController = WindowInsetsControllerCompat(window, window.decorView)
        // 默认浅色主题，状态栏和导航栏图标用深色；页面加载后按实际主题再纠正
        setSystemBarIcons(light = true)

        // 先显示一行提示，数据准备好再换成 WebView
        val loading = TextView(this).apply {
            text = "正在准备日历数据…"
            textSize = 18f
            setPadding(48, 48 + statusBarHeightPx(), 48, 48)
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

    fun setSystemBarIcons(light: Boolean) {
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light
    }

    /** 系统状态栏高度（px），用于给页面补顶部避让。 */
    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
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
            // 先隐身：等顶部避让样式注入画好再显示，卡片不会先顶上去再跳下来
            visibility = View.INVISIBLE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // localStorage 存主题 cal-theme
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(ThemeBridge(this@MainActivity), "ShuizuBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectEdgeCss(view)
                }
            }
            loadUrl("https://appassets.androidplatform.net/web/index.html")
            webView = this
        }
    }

    /** 注入顶部避让样式，并监听页面主题变化改状态栏图标颜色。 */
    private fun injectEdgeCss(view: WebView) {
        val topCss = (statusBarHeightPx() / resources.displayMetrics.density).toInt()
        val js = "(function(){try{" +
            "var el=document.getElementById('shuizu-edge');" +
            "if(!el){el=document.createElement('style');el.id='shuizu-edge';document.head.appendChild(el);}" +
            "el.textContent='.page{padding-top:calc(" + topCss + "px + 12px) !important}';" +
            "if(!window.__shuizuThemeObs){window.__shuizuThemeObs=true;" +
            "var apply=function(){try{ShuizuBridge.onThemeChanged(document.documentElement.getAttribute('data-theme')==='dark');}catch(e){}};" +
            "new MutationObserver(apply).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});" +
            "apply();}" +
            "}catch(e){}})()"
        view.evaluateJavascript(js) { view.visibility = View.VISIBLE }
    }

    companion object {
        /** 数据版本：改了 assets 里的网页或数据就升这个数，触发重新解压。 */
        const val DATA_VERSION = "1.1.0"
    }
}

/** 页面主题变化时通知壳改状态栏/导航栏图标颜色。 */
class ThemeBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun onThemeChanged(dark: Boolean) {
        activity.runOnUiThread { activity.setSystemBarIcons(!dark) }
    }
}
