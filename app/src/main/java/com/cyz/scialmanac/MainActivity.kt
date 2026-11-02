package com.cyz.scialmanac

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cyz.scialmanac.ui.AppState
import com.cyz.scialmanac.ui.component.bottombar.BottomBar
import com.cyz.scialmanac.ui.screen.calendar.CalendarScreen
import com.cyz.scialmanac.ui.screen.diary.DiaryScreen
import com.cyz.scialmanac.ui.screen.settings.SettingsScreen
import com.cyz.scialmanac.ui.screen.today.TodayScreen
import com.cyz.scialmanac.ui.theme.SciAlmanacTheme
import com.cyz.scialmanac.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class MainActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            SciAlmanacTheme(darkTheme = darkTheme) {
                val appState = remember { AppState(this@MainActivity) }
                MainScreen(appState)
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(appState: AppState) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val selectedPage = pagerState.currentPage

    val enableBlur = appState.enableBlur
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val bottomBar = @Composable {
        Box(modifier = Modifier.fillMaxWidth()) {
            BottomBar(
                blurBackdrop = backdrop,
                backdrop = backdrop,
                selectedPage = selectedPage,
                onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                floating = appState.floatingBar,
                floatingBlur = enableBlur,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    Scaffold(bottomBar = bottomBar) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (appState.floatingBar && enableBlur) Modifier.layerBackdrop(backdrop)
                        else Modifier
                    ),
                beyondViewportPageCount = 3,
                overscrollEffect = null,
            ) { page ->
                when (page) {
                    0 -> TodayScreen(appState, innerPadding.calculateBottomPadding())
                    1 -> CalendarScreen(appState, innerPadding.calculateBottomPadding())
                    2 -> DiaryScreen(appState, innerPadding.calculateBottomPadding())
                    3 -> SettingsScreen(appState, innerPadding.calculateBottomPadding())
                }
            }
        }
    }

    // 全局错误弹窗：AI 接口报错等，居中提示 + 我知道了
    // 常驻挂载并用 show 控制，退出动画才能完整播放
    val errorMsg = appState.errorMessage
    var lastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(errorMsg) { if (errorMsg != null) lastError = errorMsg }
    WindowDialog(
        title = "接口错误",
        show = errorMsg != null,
        onDismissRequest = { appState.errorMessage = null },
        onDismissFinished = { appState.errorMessage = null },
    ) {
        Column {
            Text(text = lastError ?: "", fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "我知道了",
                onClick = { appState.errorMessage = null },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
