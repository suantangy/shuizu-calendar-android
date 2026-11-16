package com.suantangyu.shuizucalendar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 检查更新的结果。 */
sealed interface UpdateResult {
    /** 有新版：remoteVersion 是远端版本号，url 是该版本的发布页。 */
    data class Available(val remoteVersion: String, val url: String) : UpdateResult

    /** 已是最新。 */
    data class UpToDate(val version: String) : UpdateResult

    /** 查不到：reason 是给人看的原因。 */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * GitHub release 的 tag 是 v<versionName>-<run_number>，取中间的版本号。
 * 格式不对返回 null。
 */
fun parseReleaseTag(tag: String): String? {
    val t = tag.removePrefix("v")
    val dash = t.indexOf('-')
    val v = if (dash >= 0) t.substring(0, dash) else t
    if (v.isEmpty()) return null
    if (!v.all { it.isDigit() || it == '.' }) return null
    return v
}

/** 语义版本比较：a > b 返回正数，相等返回 0，a < b 返回负数。 */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/suantangy/shuizu-calendar-android/releases/latest"

/**
 * 问 GitHub 最新发布版，跟 currentVersion 比。
 * 只在用户点「检查更新」时调；在 IO 线程跑，别在主线程调。
 */
fun checkForUpdate(currentVersion: String): UpdateResult {
    val conn = try {
        (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    } catch (e: Exception) {
        return UpdateResult.Failed("无法连接网络，请检查网络后重试")
    }
    return try {
        conn.connect()
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            return UpdateResult.Failed("服务器返回 ${conn.responseCode}，请稍后再试")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val remote = parseReleaseTag(json.optString("tag_name", ""))
            ?: return UpdateResult.Failed("版本号解析失败，请稍后再试")
        val url = json.optString("html_url", "").ifEmpty {
            "https://github.com/suantangy/shuizu-calendar-android/releases/latest"
        }
        if (compareVersions(remote, currentVersion) > 0) UpdateResult.Available(remote, url)
        else UpdateResult.UpToDate(currentVersion)
    } catch (e: Exception) {
        UpdateResult.Failed("无法连接网络，请检查网络后重试")
    } finally {
        conn.disconnect()
    }
}
