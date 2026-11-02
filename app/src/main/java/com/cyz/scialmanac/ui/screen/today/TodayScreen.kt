package com.cyz.scialmanac.ui.screen.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.cyz.scialmanac.ai.FortuneEngine
import com.cyz.scialmanac.data.FortuneEntity
import com.cyz.scialmanac.model.LuckLevel
import com.cyz.scialmanac.ui.AppState
import com.cyz.scialmanac.ui.util.BlurredBar
import com.cyz.scialmanac.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

private val json = Json { ignoreUnknownKeys = true }

private fun decodeList(raw: String): List<String> =
    try {
        json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

@Composable
fun TodayScreen(appState: AppState, bottomInnerPadding: Dp) {
    val context = LocalContext.current
    val engine = remember { FortuneEngine(context, appState.settings) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(appState.enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    var fortune by remember { mutableStateOf<FortuneEntity?>(null) }
    var generating by remember { mutableStateOf(false) }
    var thinkingChars by remember { mutableIntStateOf(0) }
    var resultKey by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var showAskSheet by remember { mutableStateOf(false) }
    var showRegenConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fortune = engine.todayFortune()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BlurredBar(backdrop) {
                    TopAppBar(
                        color = barColor,
                        title = "今日黄历",
                        scrollBehavior = scrollBehavior
                    )
                }
            },
            popupHost = { },
            contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val (dateText, weekText) = engine.todayDisplay()
                        FortuneCard(
                            fortune = fortune,
                            dateText = dateText,
                            weekText = weekText,
                            generating = generating,
                            thinkingChars = thinkingChars,
                            resultKey = resultKey,
                            locked = locked,
                            onRegenerate = {
                                if (fortune == null) {
                                    // 今日还没有卦象：直接推算
                                    scope.launch {
                                        generating = true
                                        thinkingChars = 0
                                        locked = false
                                        val r = engine.generateToday { thinkingChars = it.length }
                                        fortune = r.fortune
                                        locked = r.locked
                                        resultKey++
                                        appState.fortuneVersion++
                                        generating = false
                                    }
                                } else {
                                    // 今日卦象已定：重算是「对着原卦再算一签类似的」，先警告反噬
                                    showRegenConfirm = true
                                }
                            },
                        )
                        AskEntryCard(onOpen = { showAskSheet = true }, showHints = appState.showHints)
                        Spacer(
                            Modifier.height(
                                bottomInnerPadding + if (!appState.floatingBar)
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                            )
                        )
                    }
                }
            }
        }

        AskBottomSheet(
            visible = showAskSheet,
            engine = engine,
            appState = appState,
            bottomPadding = bottomInnerPadding,
            onClose = { showAskSheet = false },
        )

        // 再算一签前的高斯警告：卦不可轻动，强行问天恐遭反噬
        WindowDialog(
            title = "再算一卦？",
            show = showRegenConfirm,
            onDismissRequest = { showRegenConfirm = false },
            onDismissFinished = { showRegenConfirm = false },
        ) {
            Column {
                Text(
                    text = "卦不可轻动。再算一签将基于今日卦象推演，卦意大致不变；但强行问天，恐遭实验反噬——柱子必堵、器件必短路、水氧必飙。确定要再算吗？",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "再算一签",
                    onClick = {
                        showRegenConfirm = false
                        val ref = fortune ?: return@TextButton
                        scope.launch {
                            generating = true
                            thinkingChars = 0
                            locked = false
                            val r = engine.regenerateToday(ref) { thinkingChars = it.length }
                            fortune = r.fortune
                            resultKey++
                            appState.fortuneVersion++
                            generating = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    text = "收手吧",
                    onClick = { showRegenConfirm = false },
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** AI 推算中的实时摘要动画：轮换签语 + 进度条 + 流式字数计数。 */
@Composable
private fun ThinkingBlock(thinkingChars: Int) {
    val tips = remember {
        listOf(
            "正在翻阅你的翻车日记…",
            "正在摇卦，铜钱哐当作响…",
            "正在给蒸镀机掐指一算…",
            "正在询问手套箱里惰性气体的灵魂…",
            "黄历大师眉头一皱…",
        )
    }
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }
    Column {
        Text(
            text = tips[tipIndex],
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (thinkingChars > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "AI 正在推算 · 已接收 " + thinkingChars + " 字",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
            )
        }
    }
}

/** 逐个出字的文本。animKey 变化时重新播放。 */
@Composable
private fun TypewriterText(text: String, animKey: Any, color: Color) {
    var shown by remember(animKey) { mutableIntStateOf(0) }
    LaunchedEffect(animKey) {
        shown = 0
        while (shown < text.length) {
            delay(28)
            shown = (shown + 2).coerceAtMost(text.length)
        }
    }
    Text(
        text = text.take(shown),
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = color,
    )
}

@Composable
private fun FortuneCard(
    fortune: FortuneEntity?,
    dateText: String,
    weekText: String,
    generating: Boolean,
    thinkingChars: Int,
    resultKey: Int,
    locked: Boolean,
    onRegenerate: () -> Unit,
) {
    val level = LuckLevel.from(fortune?.luckLevel)
    val themeColor = fortune?.themeColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: level.color
    val isBad = level == LuckLevel.XIONG || level == LuckLevel.ZHISHI_BUYI

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = themeColor.copy(alpha = if (isBad) 0.14f else 0.12f)
        ),
    ) {
        Box {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(90.dp),
                    imageVector = if (isBad) Icons.Rounded.SentimentDissatisfied
                    else Icons.Rounded.SentimentVerySatisfied,
                    tint = themeColor.copy(alpha = 0.35f),
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText + " " + weekText,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        text = if (generating) "推算中…" else "重新算卦",
                        onClick = onRegenerate,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !generating
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = level.label,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeColor,
                )
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    YiJiColumn(
                        modifier = Modifier.weight(1f),
                        title = "宜",
                        items = fortune?.let { decodeList(it.yi) } ?: emptyList(),
                        tint = Color(0xFF10B981),
                    )
                    Spacer(Modifier.width(10.dp))
                    YiJiColumn(
                        modifier = Modifier.weight(1f),
                        title = "忌",
                        items = fortune?.let { decodeList(it.ji) } ?: emptyList(),
                        tint = Color(0xFFEF4444),
                    )
                }

                Spacer(Modifier.height(12.dp))
                when {
                    generating -> ThinkingBlock(thinkingChars)
                    fortune == null -> Text(
                        text = "今日运势尚未推算，点击右上角「重新算卦」看看今天适不适合进实验室。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    locked -> Column {
                        Text(
                            text = "今日卦象已定，强行逆天改命恐遭实验反噬 🌚",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = fortune.aiComment,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    else -> TypewriterText(
                        text = fortune.aiComment,
                        animKey = fortune.date + "#" + resultKey,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun YiJiColumn(
    modifier: Modifier,
    title: String,
    items: List<String>,
    tint: Color,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Circle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(8.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text("—", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            for (item in items.take(3)) {
                Text(
                    text = "· " + item,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 问卦入口卡片。 */
@Composable
private fun AskEntryCard(onOpen: () -> Unit, showHints: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "今日科研问卦", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (showHints) Text(
                text = "想做什么操作？先问一问今天的运势再动手。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = "开始问卦",
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 底部弹出的问卦面板。 */
@Composable
private fun AskBottomSheet(
    visible: Boolean,
    engine: FortuneEngine,
    appState: AppState,
    bottomPadding: Dp,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 快捷选项来自设置页「问卦快捷选项」，可在设置里增删和拖动排序
    val quickActions = appState.settings.askOptions
    val questionState = rememberTextFieldState("")
    var asking by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var answerKey by remember { mutableIntStateOf(0) }
    var fromCache by remember { mutableStateOf(false) }
    var showQuickPick by remember { mutableStateOf(false) }

    // 返回键先收起面板，而不是直接退出应用
    BackHandler(enabled = visible && !asking) { onClose() }

    fun ask() {
        val q = questionState.text.toString().trim()
        if (q.isEmpty() || asking) return
        scope.launch {
            asking = true
            answer = ""
            fromCache = false
            try {
                val (text, cached) = engine.askFortune(q) { answer = it }
                answer = text
                fromCache = cached
                answerKey++
            } catch (e: Exception) {
                appState.errorMessage = "问卦失败：" + (e.message ?: "未知错误")
            } finally {
                asking = false
            }
        }
    }

    // 面板与遮罩共用 Animatable：进入从底部滑入+遮罩淡入，退出向底部滑出+遮罩淡出
    val panelOffset = remember { Animatable(1f) }
    var render by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            render = true
            panelOffset.snapTo(1f)
            panelOffset.animateTo(0f, animationSpec = tween(340))
        } else {
            panelOffset.animateTo(1f, animationSpec = tween(240))
            render = false
        }
    }
    if (render) {
        Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * (1f - panelOffset.value)))
                .clickable(onClick = { if (!asking) onClose() }, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        )
        // 底部面板滑入滑出动画（由父级 Transition 驱动，进入退出都完整播放）
        // 面板位移挂在父级 Transition 上：进入从底部滑入、退出向底部滑出，首帧即处于初始值，不会跳过动画
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = panelOffset.value * size.height }
                .fillMaxWidth()
                .background(
                    MiuixTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                )
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.height(14.dp))
            Text(text = "你想做什么操作？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            // 快捷操作下拉（选项在设置页「问卦快捷选项」里维护）
            if (quickActions.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { showQuickPick = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "选择常见操作…",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "展开",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            TextField(
                state = questionState,
                modifier = Modifier.fillMaxWidth(),
                label = "输入你想做的实验操作…",
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = !asking,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = if (asking) "占卜中…" else "开始卜卦",
                onClick = { ask() },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = !asking && questionState.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (asking) {
                Spacer(Modifier.height(12.dp))
                ThinkingBlock(0)
            }
            if (fromCache && !asking) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "今日已问过此事，卦象直接呈上 😏",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (answer.isNotEmpty() && !asking) {
                Spacer(Modifier.height(10.dp))
                TypewriterText(
                    text = answer,
                    animKey = answerKey,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            Spacer(
                Modifier.height(
                    bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
        }
    }

    WindowDialog(
        title = "选择常见操作",
        show = showQuickPick,
        onDismissRequest = { showQuickPick = false },
    ) {
            Column {
                for (a in quickActions) {
                    TextButton(
                        text = a,
                        onClick = {
                            questionState.edit { replace(0, length, a) }
                            showQuickPick = false
                        },
                        colors = ButtonDefaults.textButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
}
