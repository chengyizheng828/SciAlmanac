package com.cyz.scialmanac.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyz.scialmanac.R
import com.cyz.scialmanac.ui.component.FloatingBottomBar
import com.cyz.scialmanac.ui.component.FloatingBottomBarItem
import com.cyz.scialmanac.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Today(R.string.nav_today, Icons.Rounded.AutoAwesome),
    Calendar(R.string.nav_calendar, Icons.Rounded.CalendarMonth),
    Diary(R.string.nav_diary, Icons.AutoMirrored.Rounded.MenuBook),
    Settings(R.string.nav_settings, Icons.Rounded.Settings)
}

@Composable
fun BottomBar(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    selectedPage: Int,
    onSelected: (Int) -> Unit,
    floating: Boolean,
    floatingBlur: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = BottomBarDestination.entries.map { destination ->
        NavigationItem(
            label = stringResource(destination.label),
            icon = destination.icon,
        )
    }
    if (!floating) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                content = {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            icon = item.icon,
                            label = item.label,
                            selected = selectedPage == index,
                            onClick = { onSelected(index) },
                        )
                    }
                }
            )
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }
        FloatingBottomBar(
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
            selectedIndex = selectedPage,
            onSelected = onSelected,
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = floatingBlur,
        ) { activateTab ->
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    selected = selectedPage == index,
                    onClick = { activateTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}
