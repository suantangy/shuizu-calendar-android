package com.suantangyu.shuizucalendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 农历换算：网页版 LUNAR_INFO 表 + solarToLunar / lunarToSolar 的逐行移植。
 *
 * 表和算法与 index.html 一字不差，包括它的两个已知偏差（主界面不受影响，
 * 因为主界面全部走 JSON 数据；只有选择器的农历模式用这套换算）：
 * 1) 闰月后一个月，solarToLunar 会把 isLeap 误报成 true（如 2001-06-22
 *    报闰五月初二）；选择器里 monthMeta 的回落查找会把它纠回正常月，
 *    界面上看不出来。
 * 2) 换算表与 JSON 数据在 2057-09-28..2057-10-27、2060-04-30..2060-05-29
 *    这两段差一天（表里某月 30 天、数据里 29 天）；网页选择器同样如此。
 */
data class LunarDate(val year: Int, val month: Int, val day: Int, val isLeap: Boolean)
data class SolarDate(val year: Int, val month: Int, val day: Int)

object LunarCalendar {
    private val BASE_DATE: LocalDate = LocalDate.of(1900, 1, 31)

    // 数据位：bits 0-12 各月大小，bit13-16 闰月月号（1900–2100，共 201 项）
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    val LUNAR_MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    val LUNAR_DAY_NAMES = arrayOf(
        "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九",
        "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九",
        "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )
    private val WEEK_NAMES = arrayOf("日", "一", "二", "三", "四", "五", "六")

    fun leapMonth(y: Int): Int = LUNAR_INFO[y - 1900] and 0xf

    fun leapDays(y: Int): Int =
        if (leapMonth(y) != 0) {
            if (LUNAR_INFO[y - 1900] and 0x10000 != 0) 30 else 29
        } else 0

    fun monthDays(y: Int, m: Int): Int =
        if (LUNAR_INFO[y - 1900] and (0x10000 shr m) != 0) 30 else 29

    fun yearDays(y: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (LUNAR_INFO[y - 1900] and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    /** 公历转农历（与网页 solarToLunar 逐行一致）。 */
    fun solarToLunar(sy: Int, sm: Int, sd: Int): LunarDate {
        var offset = ChronoUnit.DAYS.between(BASE_DATE, LocalDate.of(sy, sm, sd)).toInt()
        var y = 1900
        while (y < 2101 && offset > 0) {
            val yd = yearDays(y)
            if (offset < yd) break
            offset -= yd
            y++
        }
        if (y > 2100) y = 2100
        val leapM = leapMonth(y)
        var m = 1
        var isLeap = false
        var daysOfMonth = 0
        while (m < 13 && offset > 0) {
            if (leapM > 0 && m == leapM + 1 && !isLeap) {
                m--
                isLeap = true
                daysOfMonth = leapDays(y)
            } else {
                daysOfMonth = monthDays(y, m)
            }
            if (offset < daysOfMonth) break
            offset -= daysOfMonth
            if (isLeap && m == leapM + 1) isLeap = false
            m++
        }
        if (offset == 0 && leapM > 0 && m == leapM + 1) {
            if (isLeap) isLeap = false else { isLeap = true; m-- }
        }
        return LunarDate(y, m, offset + 1, isLeap)
    }

    /** 农历转公历（与网页 lunarToSolar 逐行一致）。 */
    fun lunarToSolar(ly: Int, lm: Int, ld: Int, isLeap: Boolean): SolarDate {
        var offset = 0
        for (y in 1900 until ly) offset += yearDays(y)
        val leapM = leapMonth(ly)
        for (m in 1 until lm) {
            offset += monthDays(ly, m)
            if (leapM > 0 && m == leapM) offset += leapDays(ly)
        }
        if (isLeap && leapM == lm) offset += monthDays(ly, lm)
        offset += ld - 1
        val d = BASE_DATE.plusDays(offset.toLong())
        return SolarDate(d.year, d.monthValue, d.dayOfMonth)
    }

    /** 公历某月天数。 */
    fun daysInMonth(y: Int, m: Int): Int = LocalDate.of(y, m, 1).lengthOfMonth()

    /** 0=日 … 6=六（与 JS getDay() 一致）。 */
    fun weekday(y: Int, m: Int, d: Int): Int = LocalDate.of(y, m, d).dayOfWeek.value % 7

    fun weekName(y: Int, m: Int, d: Int): String = WEEK_NAMES[weekday(y, m, d)]

    /** 选择器公历日列：如 "17日 周一"。 */
    fun solarDayLabel(y: Int, m: Int, d: Int): String = "${d}日 周${weekName(y, m, d)}"

    /** 选择器农历日列：如 "初五 周一"（星期由换算出的公历日期算）。 */
    fun lunarDayLabel(ly: Int, lm: Int, d: Int, isLeap: Boolean): String {
        val so = lunarToSolar(ly, lm, d, isLeap)
        return "${LUNAR_DAY_NAMES.getOrElse(d) { "${d}日" }} 周${weekName(so.year, so.month, so.day)}"
    }
}
