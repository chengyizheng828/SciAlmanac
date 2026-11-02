package com.cyz.scialmanac.ui.screen.diary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyz.scialmanac.data.DatabaseHolder
import com.cyz.scialmanac.data.DiaryEntity
import com.cyz.scialmanac.data.SettingsRepository
import com.cyz.scialmanac.ui.AppState
import com.cyz.scialmanac.ui.util.BlurredBar
import com.cyz.scialmanac.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiaryScreen(appState: AppState, bottomInnerPadding: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(appState.enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    var diaries by remember { mutableStateOf<List<DiaryEntity>>(emptyList()) }
    var editing by remember { mutableStateOf<DiaryEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var menuEntry by remember { mutableStateOf<DiaryEntity?>(null) }
    // 每次打开编辑器自增，重置输入内容与标签选择
    var editorSession by remember { mutableIntStateOf(0) }
    LaunchedEffect(showEditor) { if (showEditor) editorSession++ }

    fun reload() {
        scope.launch {
            diaries = withContext(Dispatchers.IO) {
                DatabaseHolder.get(context).diaryDao().latest(500)
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(color = barColor, title = "挫折日记", scrollBehavior = scrollBehavior)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                modifier = Modifier.padding(bottom = bottomInnerPadding),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "记一笔")
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
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .overScrollVertical()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            if (diaries.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "还没有翻车记录，实验生活居然如此顺利？",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                "点右下角 + 记录今天的崩溃瞬间。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            items(diaries, key = { it.id }) { entry ->
                DiaryCard(
                    entry = entry,
                    onLongPress = { menuEntry = entry },
                    showHints = appState.showHints,
                )
                Spacer(Modifier.height(10.dp))
            }
            item {
                Spacer(
                    Modifier.height(
                        bottomInnerPadding + 72.dp + if (!appState.floatingBar)
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                    )
                )
            }
        }
    }

    // 常驻挂载 + show 控制；退出动画期间用 shownMenu 保持内容稳定
    val menu = menuEntry
    var lastMenu by remember { mutableStateOf<DiaryEntity?>(null) }
    LaunchedEffect(menu) { if (menu != null) lastMenu = menu }
    val shownMenu = menu ?: lastMenu
    WindowDialog(
        title = "这条翻车记录",
        show = menu != null,
        onDismissRequest = { menuEntry = null },
        onDismissFinished = { menuEntry = null },
    ) {
        Column {
            val m = shownMenu
            if (m != null) {
                Text(
                    text = "[" + m.tag + "] " + m.content,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "编辑",
                    onClick = {
                        editing = m
                        showEditor = true
                        menuEntry = null
                    },
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        menuEntry = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                DatabaseHolder.get(context).diaryDao().delete(m.id)
                            }
                            reload()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(color = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    DiaryEditorDialog(
        show = showEditor,
        session = editorSession,
        initial = editing,
        tags = appState.settings.diaryTags,
        onDismiss = { showEditor = false },
        onSave = { content, tag ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    val dao = DatabaseHolder.get(context).diaryDao()
                    val existing = editing
                    val now = System.currentTimeMillis()
                    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    if (existing == null) {
                        dao.upsert(
                            DiaryEntity(
                                date = dateFmt.format(Date()),
                                content = content,
                                tag = tag,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    } else {
                        dao.upsert(existing.copy(content = content, tag = tag, updatedAt = now))
                    }
                }
                showEditor = false
                reload()
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryCard(
    entry: DiaryEntity,
    onLongPress: () -> Unit,
    showHints: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                    onLongClick = onLongPress,
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.tag,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.date,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(2.dp))
            if (showHints) Text(
                text = "长按可编辑或删除",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DiaryEditorDialog(
    show: Boolean,
    session: Int,
    initial: DiaryEntity?,
    tags: List<String>,
    onDismiss: () -> Unit,
    onSave: (content: String, tag: String) -> Unit,
) {
    val contentState = remember(session) { TextFieldState(initial?.content ?: "") }
    // 「其他」固定在标签列表末尾（设置页里不可删除、排序时自动沉底）
    val otherIndex = tags.indexOf("其他")
    var tagIndex by remember(session) { mutableIntStateOf(initial?.let { tags.indexOf(it.tag).takeIf { i -> i >= 0 } } ?: 0) }
    val customTagState = remember(session) { TextFieldState(if (initial != null && tags.indexOf(initial.tag) < 0) initial.tag else "") }

    WindowDialog(
        title = if (initial == null) "记录新的翻车" else "编辑翻车记录",
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        Column {
            TextField(
                state = contentState,
                modifier = Modifier.fillMaxWidth(),
                label = "发生了什么？例如：蒸镀到一半真空度崩了，一锅电极全废…",
            )
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                WindowSpinnerPreference(
                    title = "标签",
                    items = tags.map { DropdownItem(text = it) },
                    selectedIndex = tagIndex,
                    onSelectedIndexChange = { tagIndex = it },
                )
            }
            if (otherIndex >= 0 && tagIndex == otherIndex) {
                Spacer(Modifier.height(10.dp))
                TextField(
                    state = customTagState,
                    modifier = Modifier.fillMaxWidth(),
                    label = "自定义标签…",
                    lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                )
            }
            Spacer(Modifier.height(14.dp))
            TextButton(
                text = "保存",
                onClick = {
                    val tag = if (otherIndex >= 0 && tagIndex == otherIndex)
                        customTagState.text.toString().trim().ifBlank { "其他" } else tags[tagIndex]
                    onSave(contentState.text.toString().trim(), tag)
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
                enabled = contentState.text.isNotBlank(),
            )
        }
    }
}
