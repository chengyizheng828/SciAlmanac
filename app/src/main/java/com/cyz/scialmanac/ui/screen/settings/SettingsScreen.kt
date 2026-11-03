package com.cyz.scialmanac.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyz.scialmanac.BuildConfig
import com.cyz.scialmanac.R
import com.cyz.scialmanac.ai.AiClient
import com.cyz.scialmanac.data.BackupManager
import com.cyz.scialmanac.data.Meme
import com.cyz.scialmanac.data.SettingsRepository
import com.cyz.scialmanac.ui.AppState
import com.cyz.scialmanac.ui.component.MemeListEditor
import com.cyz.scialmanac.ui.component.StringListEditor
import com.cyz.scialmanac.ui.util.BlurredBar
import com.cyz.scialmanac.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface SettingsDialog {
    data object ApiBase : SettingsDialog
    data object ApiKey : SettingsDialog
    data object Model : SettingsDialog
    data object Keywords : SettingsDialog
}

/** 设置页二级菜单。 */
private enum class SubPage(val title: String) {
    Root("设置"),
    Ai("AI 接口"),
    AskOptions("问卦快捷选项"),
    Memes("热梗库"),
    DiaryTags("日记标签"),
    YiJiPool("宜忌候选库"),
    Profile("科研画像"),
    Appearance("外观"),
    Data("本地数据"),
    About("关于"),
}

@Composable
fun SettingsScreen(appState: AppState, bottomInnerPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberBlurBackdrop(appState.enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    var page by remember { mutableStateOf(SubPage.Root) }
    val scrollBehavior = MiuixScrollBehavior()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    // 每次打开对话框自增，用来重置输入框状态，同时保证退出动画期间内容不闪
    var dialogSession by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialog) { if (dialog != null) dialogSession++ }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<String?>(null) }

    var askOptions by remember { mutableStateOf(appState.settings.askOptions) }
    var diaryTags by remember { mutableStateOf(appState.settings.diaryTags) }
    var memes by remember { mutableStateOf(appState.settings.memes) }
    var yiJiPool by remember { mutableStateOf(appState.settings.yiJiPool) }

    // 列表编辑器拖动排序期间禁用外层列表滚动，避免手势冲突
    var listDragging by remember { mutableStateOf(false) }

    var modelList by remember { mutableStateOf<List<String>?>(null) }
    var modelListError by remember { mutableStateOf<String?>(null) }
    var modelListLoading by remember { mutableStateOf(false) }

    fun refreshModels() {
        modelListLoading = true
        modelListError = null
        scope.launch {
            try {
                modelList = AiClient(appState.settings).listModels()
            } catch (e: Exception) {
                modelListError = e.message ?: "未知错误"
            }
            modelListLoading = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) { BackupManager.export(context) }
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                backupResult = "备份已导出"
            } catch (e: Exception) {
                backupResult = "导出失败：" + e.message
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("无法读取文件")
                val (f, d) = withContext(Dispatchers.IO) { BackupManager.import(context, content) }
                backupResult = "导入完成：运势 " + f + " 条，日记 " + d + " 条"
            } catch (e: Exception) {
                backupResult = "导入失败：" + e.message
            }
        }
    }

    BackHandler(enabled = page != SubPage.Root) { page = SubPage.Root }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = page.title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (page != SubPage.Root) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { page = SubPage.Root }
                                    .padding(3.dp),
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { it / 3 } + fadeIn() togetherWith
                        slideOutHorizontally { -it / 3 } + fadeOut()
                } else {
                    slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                        slideOutHorizontally { it / 3 } + fadeOut()
                }
            },
            label = "settingsPage",
        ) { p ->
            SettingsList(innerPadding, backdrop, scrollBehavior, scrollEnabled = !listDragging) {
                when (p) {
                    SubPage.Root -> rootItems(
                        appState, askOptions, diaryTags, memes, yiJiPool, bottomInnerPadding,
                        onNavigate = { page = it },
                    )
                    SubPage.Ai -> aiItems(
                        appState, testResult, testing,
                        onTest = {
                            scope.launch {
                                testing = true
                                testResult = try {
                                    AiClient(appState.settings).testConnection()
                                } catch (e: Exception) {
                                    "连接失败：" + e.message
                                }
                                testing = false
                            }
                        },
                        onEditBase = { dialog = SettingsDialog.ApiBase },
                        onEditKey = { dialog = SettingsDialog.ApiKey },
                        onEditModel = {
                            dialog = SettingsDialog.Model
                            modelList = null
                            refreshModels()
                        },
                        bottomInnerPadding,
                    )
                    SubPage.AskOptions -> askOptionsItems(
                        askOptions,
                        onChange = {
                            askOptions = it
                            appState.settings.askOptions = it
                        },
                        onDraggingChange = { listDragging = it },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.Memes -> memesItems(
                        memes,
                        onChange = {
                            memes = it
                            appState.settings.memes = it
                        },
                        onDraggingChange = { listDragging = it },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.YiJiPool -> yiJiPoolItems(
                        appState.showHints,
                        yiJiPool,
                        onChange = {
                            yiJiPool = it
                            appState.settings.yiJiPool = it
                        },
                        onDraggingChange = { listDragging = it },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.DiaryTags -> diaryTagsItems(
                        appState.showHints,
                        diaryTags,
                        onChange = {
                            // 「其他」永远沉底，与仓库写入逻辑保持一致
                            val reordered = it.filter { t -> t != "其他" } + "其他"
                            diaryTags = reordered
                            appState.settings.diaryTags = reordered
                        },
                        onDraggingChange = { listDragging = it },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.Profile -> profileItems(
                        appState,
                        onEditKeywords = { dialog = SettingsDialog.Keywords },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.Appearance -> appearanceItems(appState, appState.floatingBar, bottomInnerPadding)
                    SubPage.Data -> dataItems(
                        backupResult,
                        onExport = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
                            exportLauncher.launch("scialmanac-backup-" + stamp + ".json")
                        },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        appState.floatingBar,
                        bottomInnerPadding,
                    )
                    SubPage.About -> aboutItems(bottomInnerPadding)
                }
            }
        }
    }

    // 四个对话框常驻挂载、用 show 控制：进入/退出动画由 Miuix 内部处理
    val d = dialog
    TextEditDialog(
        title = "API Base URL",
        initial = appState.apiBaseUrl,
        label = "例如 https://api.openai.com/v1",
        show = d == SettingsDialog.ApiBase,
        session = dialogSession,
        onDismiss = { dialog = null },
        onSave = { appState.apiBaseUrl = it; appState.syncToRepo(); dialog = null },
    )
    TextEditDialog(
        title = "API Key",
        initial = appState.apiKey,
        label = "sk-…",
        show = d == SettingsDialog.ApiKey,
        session = dialogSession,
        onDismiss = { dialog = null },
        onSave = { appState.apiKey = it; appState.syncToRepo(); dialog = null },
    )
    ModelPickerDialog(
        current = appState.model,
        loading = modelListLoading,
        error = modelListError,
        models = modelList,
        show = d == SettingsDialog.Model,
        session = dialogSession,
        onRefresh = { modelList = null; refreshModels() },
        onDismiss = { dialog = null },
        onSave = { appState.model = it; appState.syncToRepo(); dialog = null },
    )
    TextEditDialog(
        title = "实验关键词",
        initial = appState.profileKeywords.joinToString("、"),
        label = "用顿号、逗号或换行分隔",
        multiLine = true,
        show = d == SettingsDialog.Keywords,
        session = dialogSession,
        onDismiss = { dialog = null },
        onSave = {
            appState.profileKeywords = it.split("、", ",", "，", ";", "；", "\n")
                .map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
            appState.syncToRepo()
            dialog = null
        },
    )
}

/** 各子页共用的列表骨架。 */
@Composable
private fun SettingsList(
    innerPadding: PaddingValues,
    backdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    scrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
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
        userScrollEnabled = scrollEnabled,
    ) {
        content()
    }
}

/** 列表底部留白：让内容能滚过悬浮底栏。 */
private fun LazyListScope.bottomSpacer(floatingBar: Boolean, bottomInnerPadding: Dp) {
    item {
        Spacer(
            Modifier.height(
                bottomInnerPadding + if (!floatingBar)
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
            )
        )
    }
}

@Composable
private fun EntryCard(title: String, summary: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        ArrowPreference(title = title, summary = summary, onClick = onClick)
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

private fun LazyListScope.rootItems(
    appState: AppState,
    askOptions: List<String>,
    diaryTags: List<String>,
    memes: List<Meme>,
    yiJiPool: List<String>,
    bottomInnerPadding: Dp,
    onNavigate: (SubPage) -> Unit,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item { EntryCard("AI 接口", "模型：" + appState.model) { onNavigate(SubPage.Ai) } }
    item { EntryCard("问卦快捷选项", askOptions.size.toString() + " 个选项") { onNavigate(SubPage.AskOptions) } }
    item { EntryCard("热梗库", memes.size.toString() + " 个热梗 · 注入 AI 批注") { onNavigate(SubPage.Memes) } }
    item { EntryCard("日记标签", diaryTags.size.toString() + " 个标签") { onNavigate(SubPage.DiaryTags) } }
    item {
        EntryCard(
            "宜忌候选库",
            if (yiJiPool.isEmpty()) "未设置 · AI 自行生成" else yiJiPool.size.toString() + " 条候选 · AI 优先挑选",
        ) { onNavigate(SubPage.YiJiPool) }
    }
    item {
        val joined = appState.profileKeywords.joinToString("、")
        EntryCard("科研画像", if (joined.length > 24) joined.take(24) + "…" else joined) { onNavigate(SubPage.Profile) }
    }
    item {
        val features = buildList {
            if (appState.enableBlur) add("液态玻璃模糊")
            if (appState.floatingBar) add("悬浮胶囊底栏")
            if (!appState.showHints) add("无小提示")
            if (isEmpty()) add("默认")
        }
        EntryCard("外观", features.joinToString(" · ")) { onNavigate(SubPage.Appearance) }
    }
    item { EntryCard("本地数据", "导出 / 导入 JSON 备份") { onNavigate(SubPage.Data) } }
    item { EntryCard("关于", "v" + BuildConfig.VERSION_NAME) { onNavigate(SubPage.About) } }
    bottomSpacer(appState.floatingBar, bottomInnerPadding)
}

private fun LazyListScope.aiItems(
    appState: AppState,
    testResult: String?,
    testing: Boolean,
    onTest: () -> Unit,
    onEditBase: () -> Unit,
    onEditKey: () -> Unit,
    onEditModel: () -> Unit,
    bottomInnerPadding: Dp,
) {
    item {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(title = "API Base URL", summary = appState.apiBaseUrl, onClick = onEditBase)
            ArrowPreference(
                title = "API Key",
                summary = if (appState.apiKey.isBlank()) "未配置" else "已配置（点击修改）",
                onClick = onEditKey,
            )
            ArrowPreference(title = "模型名称", summary = appState.model, onClick = onEditModel)
            ArrowPreference(
                title = "测试连接",
                summary = testResult ?: "检查 AI 接口是否可用",
                holdDownState = testing,
                onClick = onTest,
            )
        }
    }
    item {
        HintText("模型名称支持从网关自动拉取模型列表，拉取失败时可手动输入。新 API 配置建议先点「测试连接」。")
    }
    bottomSpacer(appState.floatingBar, bottomInnerPadding)
}

private fun LazyListScope.askOptionsItems(
    askOptions: List<String>,
    onChange: (List<String>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item {
        HintText("首页「今日科研问卦」弹层里「选择常见操作」下拉的选项。长按右侧拖动柄上下拖动即可排序，点 × 删除，底部可添加。")
    }
    item {
        StringListEditor(
            items = askOptions,
            onItemsChange = onChange,
            onDraggingChange = onDraggingChange,
            addTitle = "添加问卦选项",
            addHint = "例如：旋涂新一批器件",
            defaults = SettingsRepository.DEFAULT_ASK_OPTIONS,
        )
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.memesItems(
    memes: List<Meme>,
    onChange: (List<Meme>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item {
        HintText(
            "热梗会注入 AI 提示词，让每日批注和问卦回答更有人味。每条热梗最多用一个、要自然贴合，" +
                "AI 被明令禁止为了搞笑而搞笑。用法/由来可以不填，仅作为备注。长按右侧拖动柄可排序，预置了几个示例，不需要可直接删掉。"
        )
    }
    item {
        MemeListEditor(
            memes = memes,
            onMemesChange = onChange,
            onDraggingChange = onDraggingChange,
            defaults = SettingsRepository.DEFAULT_MEMES,
        )
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.diaryTagsItems(
    showHints: Boolean,
    diaryTags: List<String>,
    onChange: (List<String>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item {
        if (showHints) HintText("日记编辑器里的标签下拉列表。「其他」固定在末尾、不可删除，选中「其他」时可自行输入自定义标签。长按右侧拖动柄可排序。")
    }
    item {
        StringListEditor(
            items = diaryTags,
            onItemsChange = onChange,
            onDraggingChange = onDraggingChange,
            undeletable = setOf("其他"),
            addTitle = "添加标签",
            addHint = "例如：测试机时翻车",
            defaults = SettingsRepository.DIARY_TAGS,
        )
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.yiJiPoolItems(
    showHints: Boolean,
    yiJiPool: List<String>,
    onChange: (List<String>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item {
        if (showHints) {
            HintText(
                "在这里维护你自己的「宜忌字条库」：填入具体的实验操作条目（如：宜旋蒸、宜过硅胶柱、忌动手套箱），" +
                    "AI 生成每日宜忌时会优先从库里挑选、按当天值星微调措辞。库为空时 AI 完全自行生成。长按右侧拖动柄可排序。"
            )
        }
    }
    item {
        StringListEditor(
            items = yiJiPool,
            onItemsChange = onChange,
            onDraggingChange = onDraggingChange,
            addTitle = "添加候选条目",
            addHint = "例如：宜旋蒸 / 忌动手套箱",
            defaults = emptyList(),
        )
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.profileItems(
    appState: AppState,
    onEditKeywords: () -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item { Spacer(Modifier.height(12.dp)) }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "实验关键词",
                summary = appState.profileKeywords.joinToString("、"),
                onClick = onEditKeywords,
            )
        }
    }
    item {
        if (appState.showHints) HintText("科研画像会随每次算卦一起发给 AI，作为宜忌内容和批注的素材。默认画像已按黄坊坊老师课题组（有机光电 OLED/OPV）定制。")
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.appearanceItems(
    appState: AppState,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "液态玻璃模糊",
                summary = "底栏与顶栏实时背景模糊（澎湃 HyperOS 质感）",
                checked = appState.enableBlur,
                onCheckedChange = { appState.enableBlur = it; appState.syncToRepo() },
            )
            SwitchPreference(
                title = "悬浮胶囊底栏",
                summary = "iOS 液态玻璃风格悬浮导航",
                checked = appState.floatingBar,
                onCheckedChange = { appState.floatingBar = it; appState.syncToRepo() },
            )
            SwitchPreference(
                title = "界面小提示",
                summary = "关闭后隐藏各处辅助说明小字",
                checked = appState.showHints,
                onCheckedChange = { appState.showHints = it; appState.syncToRepo() },
            )
        }
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.dataItems(
    backupResult: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    floatingBar: Boolean,
    bottomInnerPadding: Dp,
) {
    item {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(title = "导出备份", summary = "将运势与日记导出为 JSON 文件", onClick = onExport)
            ArrowPreference(title = "导入备份", summary = "从 JSON 备份文件恢复数据", onClick = onImport)
        }
    }
    if (backupResult != null) {
        item {
            Text(
                text = backupResult,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
    bottomSpacer(floatingBar, bottomInnerPadding)
}

private fun LazyListScope.aboutItems(bottomInnerPadding: Dp) {
    item { AboutCard() }
    item { Spacer(Modifier.height(bottomInnerPadding)) }
}

/** KSU 同款关于卡片：居中图标、版本号、底部作者署名。 */
@Composable
private fun AboutCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text("科研黄历 Sci-Almanac", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "v" + BuildConfig.VERSION_NAME,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "给实验员的每日运势黄历。玄学仅供参考，实验安全请遵 SOP。",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "@给我程碗饭 2026",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
        )
    }
}

/** 展示网关模型列表供选择；拉取失败时可手动输入。 */
@Composable
private fun ModelPickerDialog(
    current: String,
    loading: Boolean,
    error: String?,
    models: List<String>?,
    show: Boolean,
    session: Int,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selected by remember(session) { mutableStateOf(current) }
    var manualMode by remember(session) { mutableStateOf(false) }
    val manualState = remember(session) { TextFieldState(current) }

    WindowDialog(
        title = "模型名称",
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        Column {
            when {
                loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "正在拉取模型列表…",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                error != null -> {
                    Text(
                        text = "拉取失败：" + error + "，可手动输入。",
                        fontSize = 13.sp,
                        color = Color(0xFFEF4444),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = "重试",
                        onClick = onRefresh,
                        colors = ButtonDefaults.textButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    val list = models.orEmpty()
                    if (list.isEmpty()) {
                        Text(
                            text = "接口没有返回可用模型，可手动输入。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        val display = if (list.contains(current)) list else listOf(current) + list
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(display) { id ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selected = id },
                                ) {
                                    RadioButton(
                                        selected = selected == id,
                                        onClick = { selected = id },
                                    )
                                    Spacer(Modifier.padding(4.dp))
                                    Text(
                                        text = id,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (manualMode) {
                TextField(
                    state = manualState,
                    modifier = Modifier.fillMaxWidth(),
                    label = "手动输入模型名称",
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Spacer(Modifier.height(8.dp))
            }

            TextButton(
                text = if (manualMode) "收起手动输入" else "手动输入",
                onClick = { manualMode = !manualMode },
                colors = ButtonDefaults.textButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = "使用该模型",
                onClick = {
                    onSave(
                        if (manualMode && manualState.text.isNotBlank()) manualState.text.toString() else selected
                    )
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    label: String,
    multiLine: Boolean = false,
    show: Boolean,
    session: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val state = remember(session) { TextFieldState(initial) }
    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        Column {
            TextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                lineLimits = if (multiLine) {
                    // 不限最大行数，随内容自动变高
                    TextFieldLineLimits.MultiLine(minHeightInLines = 3)
                } else {
                    TextFieldLineLimits.SingleLine
                },
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "保存",
                onClick = { onSave(state.text.toString()) },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
