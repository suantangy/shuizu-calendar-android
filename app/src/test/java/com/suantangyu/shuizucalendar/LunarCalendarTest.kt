package com.suantangyu.shuizucalendar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 农历换算单元测试：向量全部取自网页版 JS 的实测输出（node 跑 index.html 里的
 * 原函数得到），Kotlin 移植必须与之一字不差——包括网页自己的两个已知偏差：
 * 2001-06-22 的 isLeap 误报、2057-09-28 与 JSON 差一天。
 */
class LunarCalendarTest {

    @Test
    fun solarToLunar_matchesWebJs() {
        assertEquals(LunarDate(2026, 7, 5, false), LunarCalendar.solarToLunar(2026, 8, 17))
        assertEquals(LunarDate(2026, 6, 30, false), LunarCalendar.solarToLunar(2026, 8, 12))
        assertEquals(LunarDate(2034, 10, 7, false), LunarCalendar.solarToLunar(2034, 11, 17))
        assertEquals(LunarDate(1999, 11, 25, false), LunarCalendar.solarToLunar(2000, 1, 1))
        assertEquals(LunarDate(2100, 12, 1, false), LunarCalendar.solarToLunar(2100, 12, 31))
    }

    @Test
    fun solarToLunar_leapMonth() {
        // 2025 闰六月：7 月 25 日是闰六月初一，8 月 22 日是闰六月廿九，8 月 23 日回七月初一
        assertEquals(6, LunarCalendar.leapMonth(2025))
        assertEquals(29, LunarCalendar.leapDays(2025))
        assertEquals(LunarDate(2025, 6, 1, true), LunarCalendar.solarToLunar(2025, 7, 25))
        assertEquals(LunarDate(2025, 6, 29, true), LunarCalendar.solarToLunar(2025, 8, 22))
        assertEquals(LunarDate(2025, 7, 1, false), LunarCalendar.solarToLunar(2025, 8, 23))
        // 2026 无闰月
        assertEquals(0, LunarCalendar.leapMonth(2026))
    }

    @Test
    fun solarToLunar_webQuirksKept() {
        // 闰月后一个月 isLeap 误报 true：网页原函数同款，照搬不改
        assertEquals(LunarDate(2001, 5, 2, true), LunarCalendar.solarToLunar(2001, 6, 22))
        // 换算表与 JSON 在 2057 年 9–10 月差一天：网页原函数同款，照搬不改
        assertEquals(LunarDate(2057, 8, 30, false), LunarCalendar.solarToLunar(2057, 9, 28))
    }

    @Test
    fun lunarToSolar_matchesWebJs() {
        assertEquals(SolarDate(2026, 8, 17), LunarCalendar.lunarToSolar(2026, 7, 5, false))
        assertEquals(SolarDate(2025, 7, 25), LunarCalendar.lunarToSolar(2025, 6, 1, true))
        assertEquals(SolarDate(2025, 8, 22), LunarCalendar.lunarToSolar(2025, 6, 29, true))
        assertEquals(SolarDate(2001, 6, 22), LunarCalendar.lunarToSolar(2001, 5, 2, true))
        assertEquals(SolarDate(2026, 7, 28), LunarCalendar.lunarToSolar(2026, 6, 15, true))
    }

    @Test
    fun helpers() {
        assertEquals(30, LunarCalendar.monthDays(2025, 6))
        assertEquals(30, LunarCalendar.monthDays(2025, 7))
        assertEquals(31, LunarCalendar.daysInMonth(2026, 8))
        assertEquals(28, LunarCalendar.daysInMonth(2026, 2))
        assertEquals(29, LunarCalendar.daysInMonth(2028, 2))
        // 2026-08-17 是星期一（weekday 1=一）
        assertEquals(1, LunarCalendar.weekday(2026, 8, 17))
        assertEquals("一", LunarCalendar.weekName(2026, 8, 17))
        assertEquals("17日 周一", LunarCalendar.solarDayLabel(2026, 8, 17))
        // 农历 2025 闰六月初一是 2025-07-25 星期五
        assertEquals("初一 周五", LunarCalendar.lunarDayLabel(2025, 6, 1, true))
    }
}

class DayTextTest {

    private val day0817 = DayInfo(
        date = "2026-08-17", year = 2026, month = 8, day = 17, weekIndex = 1,
        lunarYear = 2026, lunarMonth = 7, lunarDay = 5, isLeap = false,
        lunarMonthText = "七月", lunarDayText = "初五",
        waterMonthIndex = 10, waterDay = 5,
        yearGanzhiText = "丙午马年", monthGanzhiText = "丙申月", dayGanzhiText = "癸亥日",
        market = "廷牌、九阡、合江", solarTerm = "", festival = "", duanFestival = "",
        yi = listOf("修饰垣墙", "平治道涂"), ji = listOf("嫁娶", "词讼"),
        jianchu = "平", clash = "冲(丁巳)蛇 · 煞西 · 平日",
    )

    @Test
    fun formats() {
        assertEquals("星期一", DayText.week(day0817))
        assertEquals("农历 七月初五", DayText.lunar(day0817))
        assertEquals("水历 十一月初五", DayText.water(day0817))
        assertEquals("", DayText.festivalText(day0817))
        assertEquals(DayText.Label("七月初五", false), DayText.dayLabel(day0817))
        assertEquals("冲(丁巳)蛇 · 煞西 · 平日", DayText.clashText(day0817))
        assertEquals(listOf("廷牌", "九阡", "合江"), DayText.marketTags(day0817))
    }

    @Test
    fun festivalLabel() {
        // 七夕：节日红字；端节优先
        val qixi = day0817.copy(date = "2026-08-19", day = 19, festival = "七夕")
        assertEquals(DayText.Label("七夕", true), DayText.dayLabel(qixi))
        assertEquals("七夕", DayText.festivalText(qixi))
        val duan = day0817.copy(festival = "套头端", duanFestival = "套头端")
        assertEquals(DayText.Label("套头端", true), DayText.dayLabel(duan))
        // 节日里混着端节名：端节优先，只显示端节名
        val mixed = day0817.copy(festival = "端午套头端", solarTerm = "芒种")
        assertEquals(DayText.Label("套头端", true), DayText.dayLabel(mixed))
        // 无端节时节日 + 节气拼在一起
        val festTerm = day0817.copy(festival = "端午", solarTerm = "芒种")
        assertEquals(DayText.Label("端午 芒种", true), DayText.dayLabel(festTerm))
    }

    @Test
    fun clashAppendsJianchu() {
        // 建除不在冲里：补“· 建日”
        val d = day0817.copy(jianchu = "建", clash = "冲(辛丑)牛 · 煞西")
        assertEquals("冲(辛丑)牛 · 煞西 · 建日", DayText.clashText(d))
        // 已在冲里：不重复补
        assertEquals("冲(丁巳)蛇 · 煞西 · 平日", DayText.clashText(day0817))
    }

    @Test
    fun yiJiFallback() {
        // 宜为空或只有“诸事不宜”时两列都显示“诸事不宜”
        val empty = day0817.copy(yi = emptyList(), ji = emptyList())
        assertEquals(listOf("诸事不宜") to listOf("诸事不宜"), DayText.yiJi(empty))
        val only = day0817.copy(yi = listOf("诸事不宜"), ji = listOf("诸事不宜"))
        assertEquals(listOf("诸事不宜") to listOf("诸事不宜"), DayText.yiJi(only))
        assertEquals(
            listOf("修饰垣墙", "平治道涂") to listOf("嫁娶", "词讼"),
            DayText.yiJi(day0817),
        )
    }
}
