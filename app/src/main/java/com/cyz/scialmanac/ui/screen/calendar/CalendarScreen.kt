package com.cyz.scialmanac.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.cyz.scialmanac.ai.FortuneEngine
import com.cyz.scialmanac.data.DiaryEntity
import com.cyz.scialmanac.data.FortuneEntity
import com.cyz.scialmanac.model.LuckLevel
import com.cyz.scialmanac.ui.AppState
import com.cyz.scialmanac.ui.util.BlurredBar
import com.cyz.scialmanac.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.Calendar

private val json = Json { ignoreUnknownKeys = true }

private fun decodeList(raw: String): List<String> =
    try { json.decodeFromString<List<String>>(raw) } catch (_: Exception) { emptyList() }

// 信息流只渲染所选年份的 12 个月，渲染压力固定；数据全量加载，任何年份的记录都不会丢。
private const val FULL_START = "0000-01-01"
private const val FULL_END = "9999-12-31"

@Composable
fun CalendarScreen(appState: AppState, bottomInnerPadding: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { FortuneEngine(context, appState.settings) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(appState.enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    val listState = rememberLazyListState()

    var fortunes by remember { mutableStateOf<Map<String, FortuneEntity>>(emptyMap()) }
    var diariesByDate by remember { mutableStateOf<Map<String, List<DiaryEntity>>>(emptyMap()) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedFortune by remember { mutableStateOf<FortuneEntity?>(null) }
    var selectedDiaries by remember { mutableStateOf<List<DiaryEntity>>(emptyList()) }

    // 顶栏年份下拉框：默认今年；列表可无限上下滚动，任何年份都可选
    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH)
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var todayDate by remember { mutableStateOf(engine.today()) }
    // 每分钟对一次表，跨天时「今天」高亮不错位
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            val t = engine.today()
            if (t != todayDate) todayDate = t
        }
    }

    // 今年定位到当月；切到其他年份回到一月；「回到今年」悬浮钮同样定位到当月
    LaunchedEffect(selectedYear) {
        if (selectedYear == currentYear) listState.scrollToItem(currentMonth) else listState.scrollToItem(0)
    }

    // 全量加载：任何一年的记录都随时可查，不随时间流逝被「刷掉」
    suspend fun reloadAll() {
        withContext(Dispatchers.IO) {
            val f = engine.fortunesInRange(FULL_START, FULL_END)
            val d = engine.diariesInRange(FULL_START, FULL_END)
            withContext(Dispatchers.Main) {
                fortunes = f.associateBy { it.date }
                diariesByDate = d.groupBy { it.date }
            }
        }
    }

    LaunchedEffect(Unit) { reloadAll() }

    LaunchedEffect(appState.fortuneVersion) {
        if (appState.fortuneVersion == 0) return@LaunchedEffect
        reloadAll()
    }

    LaunchedEffect(selectedDate) {
        val date = selectedDate ?: return@LaunchedEffect
        selectedFortune = engine.fortuneFor(date)
        selectedDiaries = engine.diariesFor(date)
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "运势日历",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        YearDropdown(
                            selected = selectedYear,
                            onSelect = { selectedYear = it },
                        )
                        Spacer(Modifier.width(6.dp))
                    },
                )
            }
        },
        floatingActionButton = {
            if (selectedYear != currentYear) {
                FloatingActionButton(
                    onClick = { selectedYear = currentYear },
                    modifier = Modifier.padding(bottom = bottomInnerPadding),
                ) {
                    Icon(Icons.Rounded.Today, contentDescription = "回到今年")
                }
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            items((0..11).toList(), key = { it }) { month ->
                MonthCard(
                    year = selectedYear,
                    month = month,
                    isCurrentMonth = selectedYear == currentYear && month == currentMonth,
                    fortunes = fortunes,
                    diariesByDate = diariesByDate,
                    todayDate = todayDate,
                    onDayClick = { date -> selectedDate = date },
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LegendRow()
                    }
                }
                Spacer(Modifier.height(bottomInnerPadding + if (!appState.floatingBar)
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp))
            }
        }
    }

    // 常驻挂载 + show 控制，退出动画播完期间用 shownDetailDate 保持标题稳定
    val sel = selectedDate
    var shownDetailDate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sel) { if (sel != null) shownDetailDate = sel }
    WindowDialog(
        title = (sel ?: shownDetailDate ?: "") + " 的运势详情",
        show = sel != null,
        onDismissRequest = { selectedDate = null },
        onDismissFinished = { selectedDate = null },
    ) {
            Column {
                val f = selectedFortune
                if (f == null) {
                    Text("这一天还没有运势记录。", fontSize = 13.sp)
                } else {
                    val level = LuckLevel.from(f.luckLevel)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(level.color, CircleShape))
                        Spacer(Modifier.padding(4.dp))
                        Text(level.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = level.color)
                    }
                    Spacer(Modifier.height(8.dp))
                    val yi = decodeList(f.yi)
                    val ji = decodeList(f.ji)
                    if (yi.isNotEmpty()) Text("宜：" + yi.joinToString("、"), fontSize = 13.sp, lineHeight = 19.sp)
                    if (ji.isNotEmpty()) Text("忌：" + ji.joinToString("、"), fontSize = 13.sp, lineHeight = 19.sp)
                    if (f.aiComment.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(f.aiComment, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                if (selectedDiaries.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("当日挫折日记", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    for (d in selectedDiaries) {
                        Text("[" + d.tag + "] " + d.content, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
}

@Composable
private fun MonthCard(
    year: Int,
    month: Int,
    isCurrentMonth: Boolean,
    fortunes: Map<String, FortuneEntity>,
    diariesByDate: Map<String, List<DiaryEntity>>,
    todayDate: String,
    onDayClick: (String) -> Unit,
) {

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = year.toString() + "年" + (month + 1) + "月",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrentMonth) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            MonthGrid(
                year = year,
                month = month,
                fortunes = fortunes,
                diariesByDate = diariesByDate,
                todayDate = todayDate,
                onDayClick = onDayClick,
            )
        }
    }
}

/** 顶栏年份下拉框：点击后弹出年份列表，可无限上下滚动（以当前选中年为起点），选中即切换。 */
@Composable
private fun YearDropdown(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = selected.toString() + "年",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "选择年份",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
        // 弹层显隐动画：展开时淡入并向下铺开，收起时反向收起后再卸载
        val popupAnim = remember { Animatable(0f) }
        var renderPopup by remember { mutableStateOf(false) }
        LaunchedEffect(open) {
            if (open) {
                renderPopup = true
                popupAnim.snapTo(0f)
                popupAnim.animateTo(1f, animationSpec = tween(200))
            } else {
                popupAnim.animateTo(0f, animationSpec = tween(150))
                renderPopup = false
            }
        }
        if (renderPopup) {
            // 无限滚动锚点：index == ANCHOR 对应 selected，向过去和未来都可无限翻
            val anchor = 500_000
            val listState = remember { LazyListState(firstVisibleItemIndex = anchor) }
            LaunchedEffect(Unit) { listState.scrollToItem(anchor) }
            // TopEnd：弹层右上角贴着按钮右下角下方，紧贴下拉箭头展开
            Popup(
                onDismissRequest = { open = false },
                alignment = Alignment.TopEnd,
            ) {
                Card(
                    modifier = Modifier.graphicsLayer {
                        alpha = popupAnim.value
                        scaleY = popupAnim.value.coerceAtLeast(0.01f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(112.dp)
                            .heightIn(max = 232.dp),
                    ) {
                        items(count = 1_000_000) { index ->
                            val y = selected + index - anchor
                            val isSel = index == anchor
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        open = false
                                        if (y != selected) onSelect(y)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = y.toString() + "年",
                                    fontSize = 14.sp,
                                    color = if (isSel) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (isSel) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow() {
    val items = LuckLevel.entries
    Column {
        for (row in items.chunked(4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (lv in row) {
                    Box(Modifier.size(8.dp).background(lv.color, CircleShape))
                    Spacer(Modifier.padding(2.dp))
                    Text(lv.label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.padding(6.dp))
                }
            }
        }
    }
}

private data class DayCell(
    val date: String,
    val dayOfMonth: Int,
    val inMonth: Boolean,
)

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    fortunes: Map<String, FortuneEntity>,
    diariesByDate: Map<String, List<DiaryEntity>>,
    todayDate: String,
    onDayClick: (String) -> Unit,
) {
    val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA) }
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val firstDayWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = remember(year, month) {
        val list = mutableListOf<DayCell>()
        val prev = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val prevDays = prev.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in firstDayWeek - 1 downTo 0) {
            val c = (prev.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, prevDays - i) }
            list.add(DayCell(fmt.format(c.time), prevDays - i, inMonth = false))
        }
        for (d in 1..daysInMonth) {
            val c = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
            list.add(DayCell(fmt.format(c.time), d, inMonth = true))
        }
        var nextDay = 1
        while (list.size % 7 != 0) {
            val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, nextDay) }
            list.add(DayCell(fmt.format(c.time), nextDay, inMonth = false))
            nextDay++
        }
        list
    }

    val weekHeader = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekHeader.forEach { w ->
            Text(
                text = w,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }

    cells.chunked(7).forEach { week ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (cell in week) {
                DayCellView(
                    cell = cell,
                    level = fortunes[cell.date]?.let { LuckLevel.from(it.luckLevel) },
                    hasDiary = diariesByDate.containsKey(cell.date),
                    isToday = cell.date == todayDate,
                    onClick = { onDayClick(cell.date) },
                )
            }
        }
    }
}

/** 固定 40dp 圆形日格：不裁剪内容，日记角标完整露在圆圈右上角。 */
/** 固定 44dp 日格：轻量实现，滚动时新进入的月份才能快速组合。
 * 圆点表示当日运势色，右上角小点为日记角标。 */
@Composable
private fun DayCellView(
    cell: DayCell,
    level: LuckLevel?,
    hasDiary: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayOfMonth.toString(),
                fontSize = 13.sp,
                color = when {
                    !cell.inMonth -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
                    isToday -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (level != null) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.size(5.dp).background(level.color, CircleShape))
            }
        }
        if (hasDiary) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(5.dp).background(MiuixTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

