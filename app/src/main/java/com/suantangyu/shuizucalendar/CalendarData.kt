package com.suantangyu.shuizucalendar

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 日历数据模型：与 calendar-data/YYYY-MM.json 的字段一一对应。
 * 主界面（详情卡 + 月历格子）全部走这份 JSON，不走农历换算。
 */
data class DayInfo(
    val date: String,          // "2026-08-17"
    val year: Int,
    val month: Int,
    val day: Int,
    val weekIndex: Int,        // 0=日 … 6=六
    val lunarYear: Int,
    val lunarMonth: Int,
    val lunarDay: Int,
    val isLeap: Boolean,
    val lunarMonthText: String,
    val lunarDayText: String,
    val waterMonthIndex: Int,  // 0-based：0=一月 … 12=十三月
    val waterDay: Int,
    val yearGanzhiText: String,
    val monthGanzhiText: String,
    val dayGanzhiText: String,
    val market: String,        // "周覃、坝街、巫不"
    val solarTerm: String,
    val festival: String,
    val duanFestival: String,
    val yi: List<String>,
    val ji: List<String>,
    val jianchu: String,
    val clash: String,
)

data class MonthData(
    val year: Int,
    val month: Int,
    val firstWeek: Int,        // 当月 1 号的 weekIndex
    val length: Int,
    val days: List<DayInfo>,
)

/**
 * 月数据仓库：读内部存储 filesDir/web/calendar-data/YYYY-MM.json，
 * 内存缓存一份。文件由 MainActivity 首次启动时从 assets 解压得到。
 */
class MonthRepository(private val dataDir: File) {
    private val cache = mutableMapOf<String, MonthData?>()

    fun load(year: Int, month: Int): MonthData? {
        val key = "%04d-%02d".format(year, month)
        if (cache.containsKey(key)) return cache[key]
        val file = File(dataDir, "calendar-data/$key.json")
        val data = if (file.exists()) runCatching { parseMonth(JSONObject(file.readText())) }.getOrNull() else null
        cache[key] = data
        return data
    }

    private fun parseMonth(json: JSONObject): MonthData {
        val days = json.getJSONArray("days").let { arr ->
            List(arr.length()) { i -> parseDay(arr.getJSONObject(i)) }
        }
        return MonthData(
            year = json.getInt("year"),
            month = json.getInt("month"),
            firstWeek = json.optInt("firstWeek", 0),
            length = json.optInt("length", days.size),
            days = days,
        )
    }

    private fun parseDay(j: JSONObject): DayInfo {
        fun strArray(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { i -> a.optString(i) }
        return DayInfo(
            date = j.getString("date"),
            year = j.getInt("year"),
            month = j.getInt("month"),
            day = j.getInt("day"),
            weekIndex = j.optInt("weekIndex", 0),
            lunarYear = j.optInt("lunarYear", 0),
            lunarMonth = j.optInt("lunarMonth", 0),
            lunarDay = j.optInt("lunarDay", 0),
            isLeap = j.optBoolean("isLeap", false),
            lunarMonthText = j.optString("lunarMonthText", ""),
            lunarDayText = j.optString("lunarDayText", ""),
            waterMonthIndex = j.optInt("waterMonthIndex", 0),
            waterDay = j.optInt("waterDay", 0),
            yearGanzhiText = j.optString("yearGanzhiText", ""),
            monthGanzhiText = j.optString("monthGanzhiText", ""),
            dayGanzhiText = j.optString("dayGanzhiText", ""),
            market = j.optString("market", ""),
            solarTerm = j.optString("solarTerm", ""),
            festival = j.optString("festival", ""),
            duanFestival = j.optString("duanFestival", ""),
            yi = strArray(j.optJSONArray("yi")),
            ji = strArray(j.optJSONArray("ji")),
            jianchu = j.optString("jianchu", ""),
            clash = j.optString("clash", ""),
        )
    }
}

/** 详情区各行文本，与网页 formatWeek / formatLunar / formatWater / dayLabel 一致。 */
object DayText {
    private val WEEK_NAMES = arrayOf("日", "一", "二", "三", "四", "五", "六")
    private val WATER_MONTHS = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三")
    private val CN_DAY = mapOf(
        1 to "初一", 2 to "初二", 3 to "初三", 4 to "初四", 5 to "初五",
        6 to "初六", 7 to "初七", 8 to "初八", 9 to "初九", 10 to "初十",
        11 to "十一", 12 to "十二", 13 to "十三", 14 to "十四", 15 to "十五",
        16 to "十六", 17 to "十七", 18 to "十八", 19 to "十九", 20 to "二十",
        21 to "廿一", 22 to "廿二", 23 to "廿三", 24 to "廿四", 25 to "廿五",
        26 to "廿六", 27 to "廿七", 28 to "廿八", 29 to "廿九", 30 to "三十", 31 to "卅一",
    )
    private val DUAN = listOf("套头端", "水东端", "端十六", "塘州端", "三洞端", "牛场端", "扬拱端")

    fun week(day: DayInfo): String = "星期" + WEEK_NAMES.getOrElse(day.weekIndex) { "" }

    fun lunar(day: DayInfo): String {
        val m = day.lunarMonthText.ifEmpty { (if (day.isLeap) "闰" else "") + day.lunarMonth + "月" }
        val d = day.lunarDayText.ifEmpty { day.lunarDay.toString() + "日" }
        return "农历 $m$d"
    }

    fun water(day: DayInfo): String {
        val m = WATER_MONTHS.getOrElse(day.waterMonthIndex) { "" }
        val d = CN_DAY[day.waterDay] ?: (day.waterDay.toString() + "日")
        return "水历 ${m}月$d"
    }

    /** 详情区节日行：节气 + 节日，拼完为空则为“平日”。 */
    fun festivalText(day: DayInfo): String =
        "${day.solarTerm} ${day.festival}".trim()

    /** 格子小字：端节优先（红）→ 节日/节气（红）→ 农历月日。 */
    data class Label(val text: String, val isFestival: Boolean)

    fun dayLabel(day: DayInfo): Label {
        val duan = day.duanFestival.trim().ifEmpty {
            DUAN.firstOrNull { day.festival.contains(it) } ?: ""
        }
        if (duan.isNotEmpty()) return Label(duan, true)
        var festShow = day.festival.trim()
        for (n in DUAN) festShow = festShow.replace(n, "")
        festShow = festShow.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        val term = day.solarTerm.trim()
        if (festShow.isNotEmpty() && term.isNotEmpty()) return Label("$festShow $term", true)
        if (festShow.isNotEmpty()) return Label(festShow, true)
        if (term.isNotEmpty()) return Label(term, true)
        val lunarMonth = day.lunarMonthText.ifEmpty { (if (day.isLeap) "闰" else "") + day.lunarMonth + "月" }
        val lunarDay = day.lunarDayText.ifEmpty { day.lunarDay.toString() + "日" }
        return Label(lunarMonth + lunarDay, false)
    }

    /** 宜忌摘要行：建除不在冲里就补上“· 建日”。 */
    fun clashText(day: DayInfo): String {
        var clash = day.clash
        if (day.jianchu.isNotEmpty() && !clash.contains(day.jianchu)) {
            clash = (if (clash.isNotEmpty()) "$clash · " else "") + day.jianchu + "日"
        }
        return clash
    }

    /** 宜忌两列：宜为空或只有“诸事不宜”时，两列都显示“诸事不宜”。 */
    fun yiJi(day: DayInfo): Pair<List<String>, List<String>> {
        if (day.yi.isEmpty() || (day.yi.size == 1 && day.yi[0] == "诸事不宜")) {
            return listOf("诸事不宜") to listOf("诸事不宜")
        }
        return day.yi to day.ji
    }

    /** 赶集标签：按 、,， 切分。 */
    fun marketTags(day: DayInfo): List<String> =
        day.market.split(Regex("[、,，]")).map { it.trim() }.filter { it.isNotEmpty() }
}
