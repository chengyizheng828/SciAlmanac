package com.cyz.scialmanac.ui.component

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cyz.scialmanac.data.Meme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

/**
 * 通用纵向拖动排序容器：长按每行右侧的拖动柄进入拖动模式，
 * 上下拖动实时预览新顺序，松手后通过 [onReorder] 一次性提交。
 * 用长按触发是为了和列表上下滚动手势错开，不会互相抢事件。
 */
@Composable
fun ReorderableColumn(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 52.dp,
    onDraggingChange: (Boolean) -> Unit = {},
    row: @Composable (item: String, isDragging: Boolean, dragHandle: Modifier) -> Unit,
) {
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val view: View = LocalView.current
    val scope = rememberCoroutineScope()
    var display by remember { mutableStateOf(items) }
    // 拖动期间 display 顺序保持不变，只做视觉位移；
    // 避免重组交换列表项导致手势协程被 pointerInput key 变化取消、
    // 事件停止消费后被列表滚动抢走。
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragTarget by remember { mutableIntStateOf(-1) }
    var settling by remember { mutableStateOf(false) }
    val accumulated = remember { Animatable(0f) }
    // 拖动中的实时位移用普通状态读写（受限协程内不能调用 Animatable 的挂起方法）
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(items) {
        if (dragIndex == -1 && !settling) display = items
    }

    Column(modifier = modifier) {
        display.forEachIndexed { index, item ->
            val isDragging = index == dragIndex
            val dragScale by animateFloatAsState(
                targetValue = if (isDragging) 1.02f else 1f,
                animationSpec = spring(stiffness = 900f, dampingRatio = 0.8f),
                label = "dragScale",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .scale(dragScale)
                    // 所有行的位移都由拖动项的实时偏移唯一驱动：拖动项滑过某行一半时
                    // 该行才开始平滑让位；归位动画结束瞬间各行的位移恰好等于提交后的
                    // 新位置，开始和结束都严丝合缝，不会抽搐。
                    .offset {
                        val acc = if (settling) accumulated.value else dragOffset
                        val y = when {
                            dragIndex == -1 -> 0
                            index == dragIndex -> acc.roundToInt()
                            index > dragIndex ->
                                (-(acc - (index - dragIndex - 1) * rowPx).coerceIn(0f, rowPx)).roundToInt()
                            else ->
                                (-acc - (dragIndex - index - 1) * rowPx).coerceIn(0f, rowPx).roundToInt()
                        }
                        IntOffset(0, y)
                    }
            ) {
                row(
                    item,
                    isDragging,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .pointerInput(item) {
                            // 手动手势：长按触发后立刻消费该手指的全部事件，
                            // 列表滚动拿不到任何未消费的移动事件，彻底告别手势抢夺。
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress != null && !settling) {
                                    longPress.consume()
                                    dragIndex = display.indexOf(item)
                                    dragTarget = dragIndex
                                    dragOffset = 0f
                                    onDraggingChange(true)
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                        val deltaY = change.positionChange().y
                                        change.consume()
                                        if (deltaY != 0f) {
                                            dragOffset += deltaY
                                            var target = dragIndex
                                            var rest = dragOffset
                                            while (rest > rowPx / 2 && target < display.lastIndex) {
                                                target += 1
                                                rest -= rowPx
                                            }
                                            while (rest < -rowPx / 2 && target > 0) {
                                                target -= 1
                                                rest += rowPx
                                            }
                                            dragTarget = target
                                        }
                                    }
                                    // 松手：拖动项先弹性归位到目标行，再一次性提交，避免闪现
                                    val from = dragIndex
                                    val to = dragTarget
                                    val finalOffset = (to - from) * rowPx
                                    settling = true
                                    onDraggingChange(false)
                                    scope.launch {
                                        accumulated.snapTo(dragOffset)
                                        accumulated.animateTo(
                                            finalOffset,
                                            spring(stiffness = 900f, dampingRatio = 0.85f),
                                        )
                                        if (from in display.indices && to in display.indices && from != to) {
                                            val list = display.toMutableList()
                                            list.add(to, list.removeAt(from))
                                            display = list
                                            onReorder(list)
                                        }
                                        dragIndex = -1
                                        dragTarget = -1
                                        dragOffset = 0f
                                        accumulated.snapTo(0f)
                                        settling = false
                                    }
                                }
                            }
                        },
                )
            }
        }
    }
}
/** 三横线拖动柄。 */
@Composable
fun DragHandle(tint: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(tint, CircleShape),
            )
        }
    }
}


/**
 * 字符串列表编辑器：拖动排序 + 删除 + 底部添加按钮。
 * [undeletable] 中的条目不显示删除按钮，且排序时会被固定在列表末尾。
 */
@Composable
fun StringListEditor(
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
    undeletable: Set<String> = emptySet(),
    addTitle: String = "添加选项",
    addHint: String = "输入内容…",
    defaults: List<String>? = null,
) {
    var showAdd by remember { mutableStateOf(false) }
    val addState = rememberTextFieldState("")

    fun commitOrder(reordered: List<String>) {
        val locked = reordered.filter { it in undeletable }
        val rest = reordered.filter { it !in undeletable }
        onItemsChange(rest + locked)
    }

    Column(modifier = modifier) {
        Card(modifier = Modifier.fillMaxWidth()) {
            ReorderableColumn(
                items = items,
                onReorder = ::commitOrder,
                onDraggingChange = onDraggingChange,
            ) { item, isDragging, handle ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDragging) MiuixTheme.colorScheme.surfaceVariant
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item !in undeletable) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(3.dp)
                                .clickable {
                                    val list = items.toMutableList()
                                    val at = list.indexOf(item)
                                    if (at >= 0) {
                                        list.removeAt(at)
                                        onItemsChange(list)
                                    }
                                },
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        modifier = handle
                            .height(44.dp)
                            .width(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DragHandle(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            TextButton(
                text = "＋ 添加",
                onClick = { showAdd = true },
                colors = ButtonDefaults.textButtonColors(),
                modifier = Modifier.weight(1f),
            )
            if (defaults != null) {
                Spacer(Modifier.width(10.dp))
                TextButton(
                    text = "恢复默认",
                    onClick = { onItemsChange(defaults) },
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    WindowDialog(
        title = addTitle,
        show = showAdd,
        onDismissRequest = {
            addState.edit { replace(0, length, "") }
            showAdd = false
        },
    ) {
        Column {
            TextField(
                state = addState,
                modifier = Modifier.fillMaxWidth(),
                label = addHint,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "添加",
                onClick = {
                    val text = addState.text.toString().trim()
                    if (text.isNotEmpty() && text !in items) onItemsChange(items + text)
                    addState.edit { replace(0, length, "") }
                    showAdd = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = addState.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/**
 * 热梗库编辑器：每条热梗一行（梗 + 用法/由来副标题），支持拖动排序、删除、添加。
 * [onMemesChange] 在拖动松手、增删时触发。
 */
@Composable
fun MemeListEditor(
    memes: List<Meme>,
    onMemesChange: (List<Meme>) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
    defaults: List<Meme>? = null,
) {
    var showAdd by remember { mutableStateOf(false) }
    val textState = rememberTextFieldState("")
    val usageState = rememberTextFieldState("")

    Column(modifier = modifier) {
        Card(modifier = Modifier.fillMaxWidth()) {
            ReorderableColumn(
                items = memes.map { it.text },
                onReorder = { orderedTexts ->
                    val byText = memes.associateBy { it.text }
                    onMemesChange(orderedTexts.mapNotNull { byText[it] })
                },
                rowHeight = 60.dp,
                onDraggingChange = onDraggingChange,
            ) { text, isDragging, handle ->
                val meme = memes.firstOrNull { it.text == text }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDragging) MiuixTheme.colorScheme.surfaceVariant
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!meme?.usage.isNullOrBlank()) {
                            Text(
                                text = meme!!.usage,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(3.dp)
                            .clickable { onMemesChange(memes.filterNot { it.text == text }) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = handle
                            .height(44.dp)
                            .width(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DragHandle(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            TextButton(
                text = "＋ 添加热梗",
                onClick = { showAdd = true },
                colors = ButtonDefaults.textButtonColors(),
                modifier = Modifier.weight(1f),
            )
            if (defaults != null) {
                Spacer(Modifier.width(10.dp))
                TextButton(
                    text = "恢复默认",
                    onClick = { onMemesChange(defaults) },
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    WindowDialog(
        title = "添加热梗",
        show = showAdd,
        onDismissRequest = {
            textState.edit { replace(0, length, "") }
            usageState.edit { replace(0, length, "") }
            showAdd = false
        },
    ) {
        Column {
            TextField(
                state = textState,
                modifier = Modifier.fillMaxWidth(),
                label = "热梗本身，例如：包的",
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                state = usageState,
                modifier = Modifier.fillMaxWidth(),
                label = "用法 / 由来（可选）…",
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "添加",
                onClick = {
                    val text = textState.text.toString().trim()
                    if (text.isNotEmpty() && memes.none { it.text == text }) {
                        onMemesChange(memes + Meme(text, usageState.text.toString().trim()))
                    }
                    textState.edit { replace(0, length, "") }
                    usageState.edit { replace(0, length, "") }
                    showAdd = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = textState.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
