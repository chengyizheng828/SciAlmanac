package com.cyz.scialmanac.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 网络热梗：text 为梗本身，usage 为用法/由来（可空）。 */
@Serializable
data class Meme(val text: String, val usage: String = "")

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scialmanac_settings", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) = prefs.edit().putString(KEY_API_BASE, value.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var profileKeywords: List<String>
        get() = (prefs.getString(KEY_KEYWORDS, null) ?: DEFAULT_KEYWORDS)
            .split("、", ",", "，", ";", "；")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_KEYWORDS, value.joinToString("、")).apply()

    var enableBlur: Boolean
        get() = prefs.getBoolean(KEY_BLUR, true)
        set(value) = prefs.edit().putBoolean(KEY_BLUR, value).apply()

    /** 问卦结果缓存：同一问题当天不重复问 AI。 */
    fun getAskAnswer(date: String, question: String): String? =
        prefs.getString(KEY_ASK_PREFIX + date + "|" + question, null)

    fun putAskAnswer(date: String, question: String, answer: String) {
        prefs.edit().putString(KEY_ASK_PREFIX + date + "|" + question, answer).apply()
    }

    /** 界面小提示：关闭后隐藏各处辅助说明小字（如日记卡的「长按可编辑」）。 */
    var showHints: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HINTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HINTS, value).apply()

    var floatingBottomBar: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_BAR, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_BAR, value).apply()

    /** 问卦快捷选项（首页问卦下拉里出现的常见操作）。 */
    var askOptions: List<String>
        get() = parseStringList(prefs.getString(KEY_ASK_OPTIONS, null)) ?: DEFAULT_ASK_OPTIONS.toList()
        set(value) = prefs.edit()
            .putString(KEY_ASK_OPTIONS, writeStringList(value.map { it.trim() }.filter { it.isNotEmpty() }))
            .apply()

    /** 日记标签（编辑器的下拉列表）。"其他"固定保留在末尾，不允许删除。 */
    var diaryTags: List<String>
        get() = parseStringList(prefs.getString(KEY_DIARY_TAGS, null)) ?: DIARY_TAGS.toList()
        set(value) {
            val cleaned = value.map { it.trim() }.filter { it.isNotEmpty() && it != "其他" }.distinct()
            prefs.edit().putString(KEY_DIARY_TAGS, writeStringList(cleaned + "其他")).apply()
        }

    /** 宜忌候选库：用户自定义的具体实验操作条目。AI 生成每日宜忌时优先从中挑选；空库则自行生成。 */
    var yiJiPool: List<String>
        get() = parseStringList(prefs.getString(KEY_YIJI_POOL, null)) ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_YIJI_POOL, writeStringList(value.map { it.trim() }.filter { it.isNotEmpty() }.distinct()))
            .apply()

    /** 用户自定义热梗库，注入 AI 提示词让批注更有人味。 */
    var memes: List<Meme>
        get() = prefs.getString(KEY_MEMES, null)?.let { raw ->
            runCatching { json.decodeFromString<List<Meme>>(raw) }.getOrNull()
        } ?: DEFAULT_MEMES.toList()
        set(value) = prefs.edit()
            .putString(
                KEY_MEMES,
                writeMemes(
                    value.filter { it.text.isNotBlank() }
                        .map { Meme(it.text.trim(), it.usage.trim()) }
                ),
            )
            .apply()

    private fun parseStringList(raw: String?): List<String>? =
        raw?.let {
            runCatching {
                json.parseToJsonElement(it).jsonArray.map { e -> e.jsonPrimitive.content }
            }.getOrNull()
        }

    private fun writeStringList(items: List<String>): String =
        JsonArray(items.map { JsonPrimitive(it) }).toString()

    /** 热梗写入用：Meme 列表序列化为 [{text,usage},…] 的 JSON 数组。 */
    private fun writeMemes(items: List<Meme>): String = json.encodeToString(items)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val KEY_API_BASE = "api_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_KEYWORDS = "profile_keywords"
        private const val KEY_BLUR = "enable_blur"
        private const val KEY_FLOATING_BAR = "floating_bottom_bar"
        private const val KEY_SHOW_HINTS = "show_hints"
        private const val KEY_ASK_PREFIX = "ask_cache|"
        private const val KEY_ASK_OPTIONS = "ask_options"
        private const val KEY_DIARY_TAGS = "diary_tags"
        private const val KEY_YIJI_POOL = "yiji_pool"
        private const val KEY_MEMES = "memes"

        const val DEFAULT_KEYWORDS =
            "有机光电功能材料、OLED器件、有机太阳能电池OPV、旋涂成膜、真空蒸镀、手套箱、光致发光PL、量子效率、循环伏安CV、紫外可见吸收、器件封装、J-V测试"

        val DIARY_TAGS = listOf("器件性能翻车", "旋涂翻车", "蒸镀故障", "手套箱事故", "仪器故障", "数据画图爆炸", "心态爆炸", "其他")

        val DEFAULT_ASK_OPTIONS = listOf("旋涂/蒸镀新器件", "测光谱/跑J-V", "手套箱无水无氧操作", "写文章/处理数据")

        val DEFAULT_MEMES = listOf(
            Meme("包的", "表示十拿九稳、稳了"),
            Meme("红温", "实验崩盘、心态爆炸的状态"),
            Meme("听劝", "劝人三思而后行的预警"),
            Meme("浇给", "形容收尾动作，如封装、收尾实验"),
        )

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
