package com.cyz.scialmanac.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyz.scialmanac.data.SettingsRepository

/** In-memory mirror of [SettingsRepository] so Compose recomposes on changes. */
class AppState(context: Context) {
    val settings = SettingsRepository.get(context)

    var apiBaseUrl by mutableStateOf(settings.apiBaseUrl)
    var apiKey by mutableStateOf(settings.apiKey)
    var model by mutableStateOf(settings.model)
    var profileKeywords by mutableStateOf(settings.profileKeywords)
    var enableBlur by mutableStateOf(settings.enableBlur)
    var floatingBar by mutableStateOf(settings.floatingBottomBar)
    var showHints by mutableStateOf(settings.showHints)

    /** 每次重新算卦后自增，让日历等页面自动刷新运势数据。 */
    var fortuneVersion by mutableIntStateOf(0)

    /** 全局错误弹窗内容（AI 接口报错等），非空时 MainScreen 弹窗提示。 */
    var errorMessage by mutableStateOf<String?>(null)

    fun syncToRepo() {
        settings.apiBaseUrl = apiBaseUrl
        settings.apiKey = apiKey
        settings.model = model
        settings.profileKeywords = profileKeywords
        settings.enableBlur = enableBlur
        settings.floatingBottomBar = floatingBar
        settings.showHints = showHints
    }
}
