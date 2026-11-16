package com.suantangyu.shuizucalendar

import android.content.SharedPreferences
import android.content.res.AssetManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * 纯原生版（2.0.0）：界面全部用 Compose 画，不再有 WebView。
 *
 * 数据仍是原包 1212 个月 JSON：首次启动从 assets 解压到内部存储，
 * 主界面（详情卡 + 月历格子）全部读这份 JSON；选择器的农历模式用
 * LunarCalendar 的换算表（与网页同款）。全程不联网，无 INTERNET 权限。
 *
 * 沉浸式：内容延伸到系统栏之下，系统栏透明；状态栏图标颜色跟随主题。
 */
class MainActivity : ComponentActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        insetsController = WindowInsetsControllerCompat(window, window.decorView)

        val prefs = getSharedPreferences("shuizu-calendar", MODE_PRIVATE)
        setContent {
            CalendarApp(
                filesDir = filesDir,
                assets = assets,
                prefs = prefs,
                onThemeChanged = { dark -> setSystemBarIcons(!dark) },
            )
        }
    }

    fun setSystemBarIcons(light: Boolean) {
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light
    }

    companion object {
        /** 数据版本：改了 assets 里的数据就升这个数，触发重新解压。 */
        const val DATA_VERSION = "2.0.0"
    }
}

/** 北京时间今日（网页 window.TODAY 用的就是 Asia/Shanghai）。 */
private fun beijingToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun LocalDate.key(): String = format(DATE_FMT)

@Composable
private fun CalendarApp(
    filesDir: File,
    assets: AssetManager,
    prefs: SharedPreferences,
    onThemeChanged: (Boolean) -> Unit,
) {
    var dark by remember {
        mutableStateOf(prefs.getString("cal-theme", "light") == "dark")
    }
    val colors = if (dark) DarkColors else LightColors

    LaunchedEffect(dark) {
        prefs.edit().putString("cal-theme", if (dark) "dark" else "light").apply()
        onThemeChanged(dark)
    }

    var ready by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf<MonthData?>(null) }
    var selectedDate by remember { mutableStateOf(beijingToday().key()) }
    var pickerOpen by remember { mutableStateOf(false) }

    val repo = remember { MonthRepository(File(filesDir, "web")) }
    val today = remember { beijingToday() }
    val scope = rememberCoroutineScope()

    suspend fun loadMonth(year: Int, monthNum: Int): MonthData? =
        withContext(Dispatchers.IO) { repo.load(year, monthNum) }

    suspend fun goTo(year: Int, monthNum: Int, dateKey: String) {
        val data = loadMonth(year, monthNum)
        if (data != null) {
            month = data
            if (data.days.any { it.date == dateKey }) selectedDate = dateKey
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { prepareDataFiles(filesDir, assets) }
        goTo(today.year, today.monthValue, today.key())
        ready = true
    }

    val selectedDay = month?.days?.firstOrNull { it.date == selectedDate }
        ?: month?.days?.firstOrNull { it.date == today.key() }
        ?: month?.days?.firstOrNull()

    // 局部 val 才能 smart cast（month 是 delegated property，分支里无法自动收窄）
    val currentMonth = month
    val currentDay = selectedDay

    Box(modifier = Modifier.fillMaxSize()) {
        // 渐变背景（固定，不跟随滚动）
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (brush in backgroundBrushes(colors, size)) drawRect(brush = brush)
        }
        if (!ready || currentMonth == null || currentDay == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(48.dp),
            ) {
                Text(text = "正在准备日历数据…", fontSize = 18.sp, color = colors.text)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                TopBar(colors = colors, dark = dark, onToggleTheme = { dark = !dark })
                DetailCard(colors = colors, day = currentDay)
                CalendarCard(
                    colors = colors,
                    month = currentMonth,
                    todayKey = today.key(),
                    selectedDate = selectedDate,
                    onSelectDay = { selectedDate = it.date },
                    onOpenPicker = { pickerOpen = true },
                    onGoToday = {
                        scope.launch { goTo(today.year, today.monthValue, today.key()) }
                    },
                )
                Text(
                    text = "2000–2100 · calendar-data · suantangyu",
                    fontSize = 11.2.sp,
                    color = colors.muted.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
        if (pickerOpen) {
            DatePickerDialog(
                colors = colors,
                baseDate = selectedDate,
                onDismiss = { pickerOpen = false },
                onConfirm = { y, m, d ->
                    pickerOpen = false
                    scope.launch {
                        goTo(y, m, LocalDate.of(y, m, d).key())
                    }
                },
            )
        }
    }
}

/** 首次启动（或数据版本变化）把 assets 里的数据 zip 解压到内部存储。 */
private fun prepareDataFiles(filesDir: File, assets: AssetManager) {
    val webDir = File(filesDir, "web")
    val marker = File(webDir, ".ready")
    if (marker.exists() && marker.readText() == MainActivity.DATA_VERSION) return

    webDir.deleteRecursively()
    webDir.mkdirs()

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

    marker.writeText(MainActivity.DATA_VERSION)
}

// ---- 顶栏 ----

@Composable
private fun TopBar(colors: CalColors, dark: Boolean, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "水族日历",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.84).sp,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onToggleTheme() },
            contentAlignment = Alignment.Center,
        ) {
            ThemeIcon(dark = dark, color = colors.blue)
        }
    }
}

/** 主题图标：太阳 / 月亮（与网页 themeIconSun / themeIconMoon 的 SVG 同款）。 */
@Composable
private fun ThemeIcon(dark: Boolean, color: androidx.compose.ui.graphics.Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val s = size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val stroke = (1.8f / 24f) * s
        if (!dark) {
            // 太阳：圆 + 8 条射线
            drawCircle(
                color = color,
                radius = (4f / 24f) * s,
                center = Offset(cx, cy),
                style = Stroke(width = stroke),
            )
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4).toFloat()
                val r1 = (10f / 24f) * s
                val r2 = (12f / 24f) * s
                drawLine(
                    color = color,
                    start = Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                    end = Offset(cx + cos(a) * r2, cy + sin(a) * r2),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        } else {
            // 月亮：大圆减去偏移小圆的月牙
            val outer = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        Offset(cx - (8.5f / 24f) * s, cy - (8.5f / 24f) * s),
                        androidx.compose.ui.geometry.Size((17f / 24f) * s, (17f / 24f) * s),
                    ),
                )
            }
            val cut = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        Offset(cx + (1.5f / 24f) * s, cy - (7f / 24f) * s),
                        androidx.compose.ui.geometry.Size((14f / 24f) * s, (14f / 24f) * s),
                    ),
                )
            }
            val crescent = Path.combine(PathOperation.Difference, outer, cut)
            drawPath(
                path = crescent,
                color = color,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

// ---- 详情卡 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailCard(colors: CalColors, day: DayInfo) {
    val festival = DayText.festivalText(day)
    val isFestival = festival.isNotEmpty()
    val (yi, ji) = DayText.yiJi(day)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = colors.cardShadow, ambientColor = colors.cardShadow)
            .background(colors.card, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Column {
            // 顶部：左年月/星期，右大号日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day.year.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.48).sp,
                            color = colors.blue,
                        )
                        Text(
                            text = "·",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.detailDot,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Text(
                            text = "${day.month}月",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.48).sp,
                            color = colors.detailMonth,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DayText.week(day),
                            fontSize = 16.sp,
                            color = colors.weekText,
                        )
                        Text(
                            text = "·",
                            fontSize = 16.sp,
                            color = colors.weekText,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Text(
                            text = if (isFestival) festival else "平日",
                            fontSize = 16.sp,
                            fontWeight = if (isFestival) FontWeight.Medium else FontWeight.Normal,
                            color = if (isFestival) colors.red else colors.weekText,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = day.day.toString(),
                    fontSize = 67.sp,
                    lineHeight = 67.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-3.36).sp,
                    color = colors.blue,
                )
            }
            // 水历/农历/干支
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.lunarBg, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column {
                    Row {
                        Pill(text = DayText.water(day), fontSize = 16.8.sp, weight = FontWeight.SemiBold, textColor = colors.text, colors = colors)
                        Spacer(modifier = Modifier.width(8.dp))
                        Pill(text = DayText.lunar(day), fontSize = 15.2.sp, weight = FontWeight.Medium, textColor = colors.detailMonth, colors = colors)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        GanzhiPill(text = day.yearGanzhiText, colors = colors)
                        Spacer(modifier = Modifier.width(8.dp))
                        GanzhiPill(text = day.monthGanzhiText, colors = colors)
                        Spacer(modifier = Modifier.width(8.dp))
                        GanzhiPill(text = day.dayGanzhiText, colors = colors)
                    }
                }
            }
            // 今日赶集
            Text(
                text = "今日赶集",
                fontSize = 15.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )
            FlowRow {
                for (tag in DayText.marketTags(day)) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp, bottom = 6.dp)
                            .background(colors.marketBg, RoundedCornerShape(980.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "✓ $tag",
                            fontSize = 12.8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.blue,
                        )
                    }
                }
            }
            // 宜忌摘要
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.separator),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = "宜忌摘要",
                        fontSize = 15.2.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = DayText.clashText(day),
                        fontSize = 12.5.sp,
                        color = colors.muted,
                    )
                }
                Row {
                    AlmanacPanel(
                        title = "宜",
                        titleColor = colors.green,
                        badges = yi,
                        badgeBg = colors.yiBg,
                        badgeText = colors.yiText,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    AlmanacPanel(
                        title = "忌",
                        titleColor = colors.red,
                        badges = ji,
                        badgeBg = colors.jiBg,
                        badgeText = colors.jiText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    textColor: androidx.compose.ui.graphics.Color,
    colors: CalColors,
) {
    Box(
        modifier = Modifier
            .background(colors.pillBg, RoundedCornerShape(980.dp))
            .border(1.dp, colors.pillBorder, RoundedCornerShape(980.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, fontSize = fontSize, fontWeight = weight, color = textColor)
    }
}

@Composable
private fun GanzhiPill(text: String, colors: CalColors) {
    Box(
        modifier = Modifier
            .background(colors.ganzhiBg, RoundedCornerShape(980.dp))
            .border(1.dp, colors.ganzhiBorder, RoundedCornerShape(980.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, fontSize = 12.8.sp, fontWeight = FontWeight.Medium, color = colors.ganzhiText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlmanacPanel(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    badges: List<String>,
    badgeBg: androidx.compose.ui.graphics.Color,
    badgeText: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 13.6.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
        )
        FlowRow(modifier = Modifier.padding(top = 6.dp)) {
            for (b in badges) {
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp, bottom = 5.dp)
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(text = b, fontSize = 11.2.sp, fontWeight = FontWeight.Medium, color = badgeText)
                }
            }
        }
    }
}

// ---- 月历卡 ----

@Composable
private fun CalendarCard(
    colors: CalColors,
    month: MonthData,
    todayKey: String,
    selectedDate: String,
    onSelectDay: (DayInfo) -> Unit,
    onOpenPicker: () -> Unit,
    onGoToday: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp), spotColor = colors.cardShadow, ambientColor = colors.cardShadow)
            .background(colors.card, RoundedCornerShape(20.dp))
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Column {
            // 工具栏：年月触发 + 今日
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.clickable { onOpenPicker() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${month.year}年${"%02d".format(month.month)}月",
                        fontSize = 19.2.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.blue,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                    )
                    Text(
                        text = "›",
                        fontSize = 21.6.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.blue,
                        modifier = Modifier
                            .rotate(90f)
                            .padding(start = 2.dp),
                    )
                }
                if (selectedDate != todayKey) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(colors.chipBg, RoundedCornerShape(980.dp))
                            .clickable { onGoToday() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "今日",
                            fontSize = 14.1.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.blue,
                        )
                    }
                }
            }
            // 星期行
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                for ((i, w) in weekLabels.withIndex()) {
                    Text(
                        text = w,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i >= 5) colors.blue else colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 月格：前导空白 + 日期 + 尾随空白，补满 35 或 42 格
            val leading = (month.firstWeek + 6) % 7
            val cells: List<DayInfo?> = buildList {
                repeat(leading) { add(null) }
                addAll(month.days)
                val used = leading + month.days.size
                val total = if (used <= 35) 35 else 42
                repeat(total - used) { add(null) }
            }
            for (row in cells.chunked(7)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (day in row) {
                        if (day == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                            )
                        } else {
                            DayCell(
                                colors = colors,
                                day = day,
                                isToday = day.date == todayKey,
                                isSelected = day.date == selectedDate,
                                onClick = { onSelectDay(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    colors: CalColors,
    day: DayInfo,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = DayText.dayLabel(day)
    val isWeekend = day.weekIndex == 0 || day.weekIndex == 6
    val numberColor = when {
        isToday -> androidx.compose.ui.graphics.Color.White
        isSelected -> colors.blue
        isWeekend -> colors.blue
        else -> colors.text
    }
    val labelColor = when {
        label.isFestival -> colors.red
        isToday || isSelected -> colors.blue
        isWeekend -> colors.blue.copy(alpha = 0.9f)
        else -> colors.muted
    }
    Box(
        modifier = modifier
            .height(54.dp)
            .padding(start = 1.dp, end = 1.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isToday) colors.blue else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(11.dp),
                    )
                    .then(
                        if (isSelected && !isToday) Modifier.border(1.5.dp, colors.blue, RoundedCornerShape(11.dp))
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.day.toString(),
                    fontSize = 19.2.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = numberColor,
                )
            }
            Text(
                text = label.text,
                fontSize = 9.6.sp,
                fontWeight = if (label.isFestival || isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 1.dp, end = 1.dp, top = 1.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
