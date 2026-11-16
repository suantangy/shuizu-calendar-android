package com.suantangyu.shuizucalendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 配色与背景：与网页 :root / [data-theme="dark"] 的变量一一对应。
 * 1px = 1dp，1rem = 16sp（网页默认字号）。
 */
data class CalColors(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val text: Color,
    val muted: Color,
    val separator: Color,
    val blue: Color,
    val red: Color,
    val green: Color,
    val card: Color,
    val cardShadow: Color,
    val lunarBg: Color,
    val pillBg: Color,
    val pillBorder: Color,
    val ganzhiBg: Color,
    val ganzhiBorder: Color,
    val ganzhiText: Color,
    val detailMonth: Color,
    val detailDot: Color,
    val weekText: Color,
    val marketBg: Color,
    val yiBg: Color,
    val yiText: Color,
    val jiBg: Color,
    val jiText: Color,
    val chipBg: Color,
    val pickerSheet: Color,
    val pickerItem: Color,
    val cancelText: Color,
)

val LightColors = CalColors(
    bg0 = Color(0xFFC8D9F0),
    bg1 = Color(0xFFE8DFF5),
    bg2 = Color(0xFFD4E8E0),
    text = Color(0xFF1C1C1E),
    muted = Color(0xFF6C6C70),
    separator = Color(0x1F3C3C43),
    blue = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    card = Color(0xFFFFFFFF),
    cardShadow = Color(0x0F000000),
    lunarBg = Color(0xFFF2F2F7),
    pillBg = Color(0xFFFFFFFF),
    pillBorder = Color(0x0F000000),
    ganzhiBg = Color(0xB8FFFFFF),
    ganzhiBorder = Color(0x0A000000),
    ganzhiText = Color(0xFF636366),
    detailMonth = Color(0xFF3A3A3C),
    detailDot = Color(0xFF3A3A3C),
    weekText = Color(0xFF636366),
    marketBg = Color(0x1F007AFF),
    yiBg = Color(0x2634C759),
    yiText = Color(0xFF1B7F3A),
    jiBg = Color(0x1FFF3B30),
    jiText = Color(0xFFC0392B),
    chipBg = Color(0x1F007AFF),
    pickerSheet = Color(0xFFFFFFFF),
    pickerItem = Color(0xFFC7C7CC),
    cancelText = Color(0xFF8E8E93),
)

val DarkColors = CalColors(
    bg0 = Color(0xFF0B1220),
    bg1 = Color(0xFF1A1030),
    bg2 = Color(0xFF0F1F1A),
    text = Color(0xFFF5F5F7),
    muted = Color(0xFF98989F),
    separator = Color(0x73545458),
    blue = Color(0xFF0A84FF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF30D158),
    card = Color(0xFF1C1C1E),
    cardShadow = Color(0x66000000),
    lunarBg = Color(0x38787880),
    pillBg = Color(0xF22C2C2E),
    pillBorder = Color(0x14FFFFFF),
    ganzhiBg = Color(0xB32C2C2E),
    ganzhiBorder = Color(0x0FFFFFFF),
    ganzhiText = Color(0xFF98989F),
    detailMonth = Color(0xFFE5E5EA),
    detailDot = Color(0xFFE5E5EA),
    weekText = Color(0xFF98989F),
    marketBg = Color(0x330A84FF),
    yiBg = Color(0x2630D158),
    yiText = Color(0xFF30D158),
    jiBg = Color(0x26FF453A),
    jiText = Color(0xFFFF6961),
    chipBg = Color(0x330A84FF),
    pickerSheet = Color(0xFF1C1C1E),
    pickerItem = Color(0xFF636366),
    cancelText = Color(0xFF8E8E93),
)

/**
 * 页面渐变背景（与网页 body 的 background 相同图层顺序）：
 * 底下一层 165° 线性渐变，上面三团径向光斑。
 * Compose 里按“先画线性、再依次画光斑”的顺序叠。
 */
fun backgroundBrushes(colors: CalColors, size: androidx.compose.ui.geometry.Size): List<Brush> {
    val w = size.width
    val h = size.height
    val linear = Brush.linearGradient(
        0.0f to colors.bg0,
        0.45f to colors.bg1,
        1.0f to colors.bg2,
        start = Offset(w * 0.85f, 0f),
        end = Offset(w * 0.15f, h),
    )
    // CSS 里 radial-gradient(1200px 800px at 10% -10%, bg1, transparent 55%)
    val blob1 = Brush.radialGradient(
        0.0f to colors.bg1,
        0.55f to colors.bg1.copy(alpha = 0f),
        1.0f to colors.bg1.copy(alpha = 0f),
        center = Offset(w * 0.10f, h * -0.10f),
        radius = maxOf(w, h) * 0.55f,
    )
    val blob2 = Brush.radialGradient(
        0.0f to colors.bg2,
        0.50f to colors.bg2.copy(alpha = 0f),
        1.0f to colors.bg2.copy(alpha = 0f),
        center = Offset(w, 0f),
        radius = maxOf(w, h) * 0.45f,
    )
    val blob3 = Brush.radialGradient(
        0.0f to colors.bg0,
        0.55f to colors.bg0.copy(alpha = 0f),
        1.0f to colors.bg0.copy(alpha = 0f),
        center = Offset(w * 0.50f, h),
        radius = maxOf(w, h) * 0.40f,
    )
    return listOf(linear, blob3, blob2, blob1)
}
