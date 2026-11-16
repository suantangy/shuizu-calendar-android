package com.suantangyu.shuizucalendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * 年月选择器：与网页 picker-mask / picker-sheet 一致，是居中弹窗（不是底部抽屉）。
 *
 * 三列滚轮（年 / 月 / 日），每项高 44dp，上下各留 88dp，滚轮区高 220dp，
 * 居中 44dp 高亮带、上下渐隐。公历/农历分段切换时用农历换算转换当前选中值。
 * 打开时以详情区当前选中的日期为基准（不是系统今日）。
 *
 * 注意：分段与确认按钮的蓝色是写死的 #007AFF（网页 CSS 用的是字面值，
 * 深色主题下也不变蓝 #0A84FF）；分段底色在深色主题下也是白色（网页同款）。
 */
data class PickerMonthOpt(val m: Int, val isLeap: Boolean, val label: String)

enum class PickerMode { SOLAR, LUNAR }

private val PICKER_BLUE = Color(0xFF007AFF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatePickerDialog(
    colors: CalColors,
    /** 详情区当前选中的公历日期（"YYYY-MM-DD"）。 */
    baseDate: String,
    onDismiss: () -> Unit,
    /** 确认后回调换算好的公历日期。 */
    onConfirm: (year: Int, month: Int, day: Int) -> Unit,
) {
    val initParts = baseDate.split("-")
    val initY = initParts.getOrNull(0)?.toIntOrNull() ?: 2026
    val initM = initParts.getOrNull(1)?.toIntOrNull() ?: 8
    val initD = initParts.getOrNull(2)?.toIntOrNull() ?: 1

    var mode by remember { mutableStateOf(PickerMode.SOLAR) }
    var pickY by remember { mutableStateOf(initY) }
    var pickM by remember { mutableStateOf(initM) }
    var pickD by remember { mutableStateOf(initD) }
    var pickLeap by remember { mutableStateOf(false) }

    val itemHpx = with(LocalDensity.current) { 44.dp.toPx() }

    // —— 派生列表（与网页 refreshPickerCols 一致） ——
    val years = remember { (2000..2100).map { "${it}年" } }
    val monthOpts: List<PickerMonthOpt> = remember(mode, pickY) {
        if (mode == PickerMode.LUNAR) {
            val leapM = LunarCalendar.leapMonth(pickY)
            val list = mutableListOf<PickerMonthOpt>()
            for (m in 1..12) {
                list += PickerMonthOpt(m, false, LunarCalendar.LUNAR_MONTH_NAMES[m - 1])
                if (leapM == m) list += PickerMonthOpt(m, true, "闰" + LunarCalendar.LUNAR_MONTH_NAMES[m - 1])
            }
            list
        } else {
            (1..12).map { PickerMonthOpt(it, false, "${it}月") }
        }
    }
    // 选中月在列表里的位置；闰月回落到正常月（与网页的 findIndex 回落一致）。
    val monthIdx = remember(monthOpts, pickM, pickLeap) {
        monthOpts.indexOfFirst { it.m == pickM && it.isLeap == pickLeap }
            .takeIf { it >= 0 }
            ?: monthOpts.indexOfFirst { it.m == pickM && !it.isLeap }
                .takeIf { it >= 0 } ?: 0
    }
    val dayCount = remember(mode, pickY, pickM, pickLeap) {
        if (mode == PickerMode.LUNAR) {
            if (pickLeap) LunarCalendar.leapDays(pickY) else LunarCalendar.monthDays(pickY, pickM)
        } else {
            LunarCalendar.daysInMonth(pickY, pickM)
        }
    }
    val clampedDay = pickD.coerceIn(1, maxOf(1, dayCount))
    val days = remember(mode, pickY, pickM, pickLeap, dayCount) {
        (1..dayCount).map { d ->
            if (mode == PickerMode.LUNAR) LunarCalendar.lunarDayLabel(pickY, pickM, d, pickLeap)
            else LunarCalendar.solarDayLabel(pickY, pickM, d)
        }
    }
    val yearIdx = (pickY - 2000).coerceIn(0, years.lastIndex)
    val dayIdx = (clampedDay - 1).coerceIn(0, maxOf(0, days.lastIndex))

    // 年月变化后把 pickM/pickLeap 纠回列表里真实存在的那一项、把日钳到当月天数
    //（与网页 refreshPickerCols 里改 pickM/pickLeap/pickD 的逻辑一致）。
    LaunchedEffect(mode, pickY, pickM, pickLeap, dayCount) {
        val resolved = monthOpts.getOrNull(monthIdx)
        if (resolved != null && (resolved.m != pickM || resolved.isLeap != pickLeap)) {
            pickM = resolved.m
            pickLeap = resolved.isLeap
        }
        if (pickD != clampedDay) pickD = clampedDay
    }

    val yearState = remember { LazyListState(firstVisibleItemIndex = (initY - 2000).coerceIn(0, 100)) }
    val monthState = remember(mode, pickY) { LazyListState(firstVisibleItemIndex = monthIdx) }
    val dayState = remember(mode, pickY, pickM, pickLeap) { LazyListState(firstVisibleItemIndex = dayIdx) }

    fun switchMode(next: PickerMode) {
        if (next == mode) return
        if (next == PickerMode.LUNAR) {
            val lu = LunarCalendar.solarToLunar(pickY, pickM, pickD)
            pickY = lu.year; pickM = lu.month; pickD = lu.day; pickLeap = lu.isLeap
        } else {
            val so = LunarCalendar.lunarToSolar(pickY, pickM, pickD, pickLeap)
            pickY = so.year; pickM = so.month; pickD = so.day; pickLeap = false
        }
        mode = next
    }

    fun confirm() {
        // 按滚轮当前居中项取值（与网页确认时的 snapCol 一致）。
        val yi = centeredIndex(yearState, years.size, itemHpx)
        val mi = centeredIndex(monthState, monthOpts.size, itemHpx)
        val di = centeredIndex(dayState, days.size, itemHpx)
        val py = 2000 + yi
        var pm = pickM
        var pd = di + 1
        var pLeap = pickLeap
        if (mode == PickerMode.LUNAR && monthOpts.getOrNull(mi) != null) {
            pm = monthOpts[mi].m
            pLeap = monthOpts[mi].isLeap
        } else {
            pm = mi + 1
            pLeap = false
        }
        val (sy, sm, sd) = if (mode == PickerMode.LUNAR) {
            val so = LunarCalendar.lunarToSolar(py, pm, pd, pLeap)
            Triple(so.year, so.month, so.day)
        } else {
            Triple(py, pm, pd)
        }
        val dim = LunarCalendar.daysInMonth(sy, sm)
        onConfirm(sy, sm, sd.coerceIn(1, dim))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 340.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .background(colors.pickerSheet, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    // 卡片本身吃掉点击，不透到遮罩上关闭
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                // 头部：取消 / 公历·农历 / 确认
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.2.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.cancelText,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 分段：白底、蓝框，选中蓝底白字
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 168.dp)
                                .height(34.dp)
                                .border(1.5.dp, PICKER_BLUE, RoundedCornerShape(980.dp))
                                .clip(RoundedCornerShape(980.dp))
                                .background(Color.White),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for ((index, m) in PickerMode.values().withIndex()) {
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.5.dp)
                                            .fillMaxHeight()
                                            .background(PICKER_BLUE),
                                    )
                                }
                                val active = mode == m
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (active) PICKER_BLUE else Color.White)
                                        .clickable { switchMode(m) }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (m == PickerMode.SOLAR) "公历" else "农历",
                                        fontSize = 14.4.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (active) Color.White else PICKER_BLUE,
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(PICKER_BLUE, RoundedCornerShape(980.dp))
                            .clickable { confirm() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "确认",
                            fontSize = 15.2.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.separator),
                )
                // 三列滚轮
                Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        PickerWheel(
                            items = years,
                            state = yearState,
                            colors = colors,
                            itemHpx = itemHpx,
                            modifier = Modifier.weight(1f),
                            onSettle = { i -> pickY = 2000 + i },
                        )
                        PickerWheel(
                            items = monthOpts.map { it.label },
                            state = monthState,
                            colors = colors,
                            itemHpx = itemHpx,
                            modifier = Modifier.weight(1f),
                            onSettle = { i ->
                                monthOpts.getOrNull(i)?.let {
                                    pickM = it.m
                                    pickLeap = it.isLeap
                                }
                            },
                        )
                        PickerWheel(
                            items = days,
                            state = dayState,
                            colors = colors,
                            itemHpx = itemHpx,
                            modifier = Modifier.weight(1.2f),
                            onSettle = { i -> pickD = i + 1 },
                        )
                    }
                    // 居中高亮带（44dp，两端分隔线）
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(44.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.separator),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.separator),
                        )
                    }
                    // 上下渐隐
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to colors.pickerSheet,
                                    1f to colors.pickerSheet.copy(alpha = 0f),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to colors.pickerSheet.copy(alpha = 0f),
                                    1f to colors.pickerSheet,
                                ),
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** 滚轮当前居中项（与网页 colSelectFromScroll 的 round(scrollTop/44) 一致）。 */
private fun centeredIndex(state: LazyListState, count: Int, itemHpx: Float): Int {
    if (count <= 0) return 0
    val px = state.firstVisibleItemIndex * itemHpx + state.firstVisibleItemScrollOffset
    return ((px / itemHpx).toInt()).coerceIn(0, count - 1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerWheel(
    items: List<String>,
    state: LazyListState,
    colors: CalColors,
    itemHpx: Float,
    modifier: Modifier = Modifier,
    onSettle: (Int) -> Unit,
) {
    val selected by remember(state, items.size) {
        derivedStateOf { centeredIndex(state, items.size, itemHpx) }
    }
    // 停稳后吸附到最近一项，再回调（与网页 bindCol 的 80ms 节流后吸附一致）。
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collectLatest {
                val idx = centeredIndex(state, items.size, itemHpx)
                state.scrollToItem(idx)
                onSettle(idx)
            }
    }
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 88.dp),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        itemsIndexed(items) { i, text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    fontSize = if (i == selected) 19.2.sp else 16.8.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == selected) colors.text else colors.pickerItem,
                )
            }
        }
    }
}
