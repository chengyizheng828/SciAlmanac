package com.cyz.scialmanac.ai

import com.cyz.scialmanac.data.DatabaseHolder
import com.cyz.scialmanac.data.DiaryEntity
import com.cyz.scialmanac.data.FortuneEntity
import com.cyz.scialmanac.data.Meme
import com.cyz.scialmanac.data.SettingsRepository
import com.cyz.scialmanac.model.LuckLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FortuneEngine(
    private val context: android.content.Context,
    private val settings: SettingsRepository,
) {
    private val client get() = AiClient(settings)

    fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    fun todayDisplay(): Pair<String, String> {
        val now = Calendar.getInstance()
        val date = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(now.time)
        val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[now.get(Calendar.DAY_OF_WEEK) - 1]
        return date to ("星期" + week)
    }

    suspend fun todayFortune(): FortuneEntity? =
        DatabaseHolder.get(context).fortuneDao().get(today())

    suspend fun fortuneFor(date: String): FortuneEntity? =
        DatabaseHolder.get(context).fortuneDao().get(date)

    suspend fun diariesFor(date: String): List<DiaryEntity> =
        DatabaseHolder.get(context).diaryDao().getByDate(date)

    suspend fun recentDiaries(limit: Int = 12): List<DiaryEntity> =
        DatabaseHolder.get(context).diaryDao().latest(limit)

    suspend fun fortunesInRange(start: String, end: String): List<FortuneEntity> =
        DatabaseHolder.get(context).fortuneDao().getBetween(start, end)

    suspend fun diariesInRange(start: String, end: String): List<DiaryEntity> =
        DatabaseHolder.get(context).diaryDao().getBetween(start, end)

    /**
     * Generate today's fortune: AI first, deterministic local fallback on any failure.
     * [force] bypasses the cache so 重新算卦 actually regenerates.
     * [onThinking] receives the raw stream while AI is thinking (for the live summary animation).
     */
    /**
     * Generate today's fortune: AI first, deterministic local fallback on any failure.
     * 一日一卦：已有卦象直接返回并标记 locked，绝不逆天改命。
     * [onThinking] receives the raw stream while AI is thinking.
     */
    suspend fun generateToday(
        onThinking: ((String) -> Unit)? = null,
    ): GenResult {
        val cached = todayFortune()
        if (cached != null) return GenResult(cached, locked = true)

        val diaries = recentDiaries(12)
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to fortuneUserPrompt(today(), settings.profileKeywords, diaries, settings.memes),
        )
        val entity = try {
            if (!client.isConfigured) throw AiException("AI 接口未配置")
            val raw = try {
                client.chatStream(messages) { partial -> onThinking?.invoke(partial) }
            } catch (_: Exception) {
                // 部分网关不支持流式，回退为非流式
                client.chat(messages)
            }
            parseFortune(today(), raw)
        } catch (e: Exception) {
            localFallback(today(), settings.profileKeywords, diaries)
        }
        DatabaseHolder.get(context).fortuneDao().upsert(entity)
        return GenResult(entity, locked = false)
    }

    /**
     * 重新算卦：基于今日已有卦象再算一签「类似的」——等级、主题色、宜忌主题保持不变，
     * 宜忌换措辞微调，批注换个说法重写，并在批注末尾提醒强行再卦会遭实验反噬。
     * 任何失败都保留原卦，绝不算出不同的运势。
     */
    suspend fun regenerateToday(
        reference: FortuneEntity,
        onThinking: ((String) -> Unit)? = null,
    ): GenResult {
        val diaries = recentDiaries(12)
        val messages = listOf(
            "system" to REGEN_SYSTEM_PROMPT,
            "user" to regenUserPrompt(reference, settings.profileKeywords, diaries, settings.memes),
        )
        val entity = try {
            if (!client.isConfigured) throw AiException("AI 接口未配置")
            val raw = try {
                client.chatStream(messages) { partial -> onThinking?.invoke(partial) }
            } catch (_: Exception) {
                // 部分网关不支持流式，回退为非流式
                client.chat(messages)
            }
            // 双保险：等级绝不允许被改命
            val parsed = parseFortune(reference.date, raw)
            if (LuckLevel.from(parsed.luckLevel) != LuckLevel.from(reference.luckLevel)) {
                parsed.copy(luckLevel = reference.luckLevel)
            } else {
                parsed
            }
        } catch (e: Exception) {
            reference
        }
        DatabaseHolder.get(context).fortuneDao().upsert(entity)
        return GenResult(entity, locked = false)
    }

    data class GenResult(val fortune: FortuneEntity, val locked: Boolean)

    /** 命中当天同问题缓存则为 true（直接呈上旧卦）。 */
    suspend fun askFortune(
        question: String,
        onDelta: (String) -> Unit,
    ): Pair<String, Boolean> {
        val cached = settings.getAskAnswer(today(), question)
        if (cached != null) {
            onDelta(cached)
            return cached to true
        }
        val diaries = recentDiaries(12)
        val fortune = todayFortune()
        val messages = listOf(
            "system" to ASK_SYSTEM_PROMPT,
            "user" to askUserPrompt(
                question = question,
                date = today(),
                keywords = settings.profileKeywords,
                diaries = diaries,
                todayLuck = fortune?.luckLevel,
                todayComment = fortune?.aiComment,
                memes = settings.memes,
            ),
        )
        val answer = try {
            client.chatStream(messages, onDelta)
        } catch (e: Exception) {
            // 部分网关不支持流式，回退为非流式
            val text = client.chat(messages)
            onDelta(text)
            text
        }
        settings.putAskAnswer(today(), question, answer)
        return answer to false
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        const val USER_IDENTITY = "用户是海南大学材料科学与工程学院黄坊坊老师课题组的研究生，" +
            "研究方向是有机光电功能材料（OLED、有机太阳能电池等），平时做旋涂、真空蒸镀、手套箱、PL/吸收光谱、循环伏安、器件封装与 J-V 测试。" +
            "本科在有机合成实验室受过训练，TLC、柱层析、无水无氧、核磁这些老本行也熟。" +
            "点评可结合光电材料实验室日常与研究生生活，幽默毒舌但保持善意，不要人身攻击。"

        const val SYSTEM_PROMPT = "你是一位精通实验玄学的科研黄历大师，说话像实验室里懂行的师兄：专业、幽默、毒舌但有分寸。" +
            USER_IDENTITY +
            "结合用户的科研画像、最近的实验挫折记录和热梗库，为指定日期生成科研运势。" +
            "必须只返回纯 JSON（不要 markdown 代码块），格式：{\"luck_level\":\"大吉\",\"theme_color\":\"#10B981\",\"yi\":[\"...\",\"...\"],\"ji\":[\"...\",\"...\"],\"ai_comment\":\"点评\"}。" +
            "luck_level 只能是：大吉、中吉、小吉、平、凶、诸事不宜。" +
            "判级铁律：不准逢迎、不准默认报喜，黄历不敢判凶就是骗子。挫折记录是发现规律的线索，不是按数量定罪：" +
            "要分析日记里翻车的共性（同类事故反复出现=该方向今日凶相；问题已化解、近期顺利=可以放心给吉），" +
            "再结合当日缘分开诚布公地判级。即使一条记录也没有，也要敢于给出「凶」和「诸事不宜」，两者合计占比不得低于三成；「大吉」是稀缺品，不能随手就给。" +
            "theme_color 为与等级匹配的十六进制颜色码。" +
            "宜忌要贴合有机光电材料实验室日常（旋涂、蒸镀、测器件、手套箱、测光谱、封装等），也可以玩有机合成的梗。" +
            "内容务必精简：yi 给 2~3 项、ji 给 2 项，每项不超过 12 个字；" +
            "ai_comment 控制在 50~80 字，一针见血，不要废话。" +
            "玩梗纪律：从热梗库里挑 0~1 个与当天情境自然契合的梗，点到为止；" +
            "绝对禁止为了搞笑而搞笑、禁止棒读、禁止堆砌梗；热梗库为空就老老实实说话。emoji 最多一个，克制使用。"

        const val ASK_SYSTEM_PROMPT = "你是一位科研黄历大师，根据今日运势、用户科研画像、近期挫折记录和热梗库，" +
            USER_IDENTITY +
            "回答用户今天是否适合进行某项实验操作。要求：先给明确结论（适合/不适合/谨慎），再给 2~3 条简短理由。" +
            "理由必须有趣具体：结合今日运势、近期翻车记录和实验室日常打比方，禁止复读用户的问题本身当结论。" +
            "语气像懂行的师兄师姐，幽默毒舌但有善意；玩梗从热梗库里挑，最多一个、自然融入，禁止为了搞笑而搞笑；" +
            "emoji 或颜文字（黑脸🌚、坏笑😏之类）至多一个，贴合情绪即可，别刷屏。" +
            "最后给一条实操建议。总长度控制在 150 字以内。"

        const val REGEN_SYSTEM_PROMPT = "你是一位精通实验玄学的科研黄历大师，说话像实验室里懂行的师兄：专业、幽默、毒舌但有分寸。" +
            USER_IDENTITY +
            "用户想对今天已有的卦象再算一签。这是「问天二次」，不是改命：必须基于用户给出的原卦象推演一签类似的卦，绝不允许改运势。" +
            "只返回纯 JSON（不要 markdown 代码块），格式：{\"luck_level\":\"…\",\"theme_color\":\"#…\",\"yi\":[\"…\"],\"ji\":[\"…\"],\"ai_comment\":\"…\"}。" +
            "硬性规则：luck_level 必须与原卦象完全一致；theme_color 必须与原卦象完全一致；" +
            "yi、ji 保持与原卦象相同的主题和条数，只换措辞、可微调顺序，每项不超过 12 个字；" +
            "ai_comment 用全新的说法重新写同一句断语（50~80 字，一针见血，不与原批注雷同），" +
            "并在末尾用一句话提醒：强行再问一卦会遭实验反噬（如柱子必堵、器件必短路、水氧必飙），语气玩味、点到为止，不要恐吓。" +
            "玩梗纪律同前：热梗库里挑 0~1 个自然契合的梗，禁止为了搞笑而搞笑；emoji 最多一个。"

        fun fortuneUserPrompt(
            date: String,
            keywords: List<String>,
            diaries: List<DiaryEntity>,
            memes: List<Meme> = emptyList(),
        ): String {
            val sb = StringBuilder()
            sb.append("日期：").append(date).append('\n')
            sb.append("科研画像：").append(keywords.joinToString("、")).append('\n')
            sb.append(memeSection(memes))
            sb.append("最近挫折记录：").append('\n')
            if (diaries.isEmpty()) {
                sb.append("（暂无记录，可以适当毒舌地调侃用户还没记录过翻车）").append('\n')
            } else {
                for (d in diaries.take(12)) {
                    sb.append("- [").append(d.date).append("][").append(d.tag).append("] ").append(d.content).append('\n')
                }
            }
            sb.append("请生成这一天的科研运势 JSON。")
            return sb.toString()
        }

        /** 把热梗库拼进用户提示词；空库时明确告诉模型别硬玩梗。 */
        fun memeSection(memes: List<Meme>): String {
            if (memes.isEmpty()) {
                return "热梗库：（空，本条回复不要使用网络热梗）\n"
            }
            val sb = StringBuilder("热梗库（每条批注最多自然使用其中一个，别硬凑）：\n")
            for (m in memes) {
                sb.append("- ").append(m.text)
                if (m.usage.isNotBlank()) sb.append("：").append(m.usage)
                sb.append('\n')
            }
            return sb.toString()
        }

        /** 重算一签的用户提示词：把原卦象完整喂给模型。 */
        fun regenUserPrompt(
            reference: FortuneEntity,
            keywords: List<String>,
            diaries: List<DiaryEntity>,
            memes: List<Meme> = emptyList(),
        ): String {
            val sb = StringBuilder()
            sb.append("日期：").append(reference.date).append('\n')
            sb.append("科研画像：").append(keywords.joinToString("、"))
            sb.append('\n')
            sb.append(memeSection(memes))
            sb.append("今日原卦象（再算一签必须与此一致，不得改运）：").append('\n')
            sb.append("- 运势等级：").append(reference.luckLevel).append('\n')
            sb.append("- 主题色：").append(reference.themeColor).append('\n')
            sb.append("- 宜：").append(decodeJsonList(reference.yi).joinToString("、"))
            sb.append('\n')
            sb.append("- 忌：").append(decodeJsonList(reference.ji).joinToString("、"))
            sb.append('\n')
            sb.append("- 原批注：").append(reference.aiComment).append('\n')
            sb.append("最近挫折记录：").append('\n')
            if (diaries.isEmpty()) {
                sb.append("（暂无记录）").append('\n')
            } else {
                for (d in diaries.take(12)) {
                    sb.append("- [").append(d.date).append("][").append(d.tag).append("] ").append(d.content).append('\n')
                }
            }
            sb.append("请基于以上原卦象再算一签类似的：等级与主题色不变，宜忌主题不变只换说法，批注重新写并在末尾提醒强行再卦会遭实验反噬。")
            return sb.toString()
        }

        private fun decodeJsonList(raw: String): List<String> =
            try {
                json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
            } catch (_: Exception) {
                emptyList()
            }

        fun askUserPrompt(
            question: String,
            date: String,
            keywords: List<String>,
            diaries: List<DiaryEntity>,
            todayLuck: String?,
            todayComment: String?,
            memes: List<Meme> = emptyList(),
        ): String {
            val sb = StringBuilder()
            sb.append("日期：").append(date).append('\n')
            sb.append("科研画像：").append(keywords.joinToString("、")).append('\n')
            sb.append(memeSection(memes))
            sb.append("今日运势：").append(todayLuck ?: "未知").append("；今日批注：")
                .append(todayComment ?: "无").append('\n')
            sb.append("最近挫折记录：").append('\n')
            for (d in diaries.take(12)) {
                sb.append("- [").append(d.date).append("][").append(d.tag).append("] ").append(d.content).append('\n')
            }
            if (diaries.isEmpty()) sb.append("（暂无记录）").append('\n')
            sb.append("想问的操作：").append(question).append('\n')
            sb.append("请回答今天是否适合进行该操作。")
            return sb.toString()
        }

        fun parseFortune(date: String, raw: String): FortuneEntity {
            val cleaned = extractJson(raw)
            val root = json.parseToJsonElement(cleaned).jsonObject
            val level = root["luck_level"]?.jsonPrimitive?.content ?: "平"
            val theme = root["theme_color"]?.jsonPrimitive?.content ?: "#F59E0B"
            val yi = root["yi"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val ji = root["ji"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val comment = root["ai_comment"]?.jsonPrimitive?.content ?: ""
            return FortuneEntity(
                date = date,
                luckLevel = LuckLevel.from(level).label,
                themeColor = theme,
                yi = JsonArray(yi.map { JsonPrimitive(it) }).toString(),
                ji = JsonArray(ji.map { JsonPrimitive(it) }).toString(),
                aiComment = comment,
            )
        }

        private fun extractJson(raw: String): String {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start >= 0 && end > start) return raw.substring(start, end + 1)
            return raw
        }

        /** Offline deterministic fallback so the app always works. */
        fun localFallback(date: String, keywords: List<String>, diaries: List<DiaryEntity>): FortuneEntity {
            val seed = date.hashCode() * 31L + diaries.size
            val random = java.util.Random(seed)
            val levels = LuckLevel.entries
            val level = levels[random.nextInt(levels.size)]

            val yiPool = listOf(
                "旋涂薄膜", "蒸镀电极", "测PL光谱", "跑J-V曲线", "手套箱换源", "封装器件",
                "测紫外吸收", "跑循环伏安", "等离子清洗", "超声洗基片", "文献调研", "预约测试机时",
                "写实验记录", "配标准溶液", "整理数据",
            )
            val jiPool = listOf(
                "不戴手套摸基片", "湿度超标蒸镀", "器件不封装就测", "徒手折ITO玻璃",
                "旋涂不开抽风", "凭感觉设膜厚", "深夜单独做高危操作", "不记实验条件",
            )
            fun pick(pool: List<String>, n: Int) =
                pool.shuffled(random).take(n)

            val recent = diaries.take(3)
            val comment = buildString {
                append("今日").append(level.label).append("。")
                if (recent.isNotEmpty()) {
                    append("鉴于你最近在「").append(recent.first().tag).append("」上的惨痛经历，")
                    append("建议今天把").append(keywords.getOrElse(random.nextInt(keywords.size)) { "实验" })
                    .append("先放一放，稳扎稳打。")
                } else {
                    append("你还没记录过翻车，别得意，实验玄学讲究因果。")
                }
                append("（离线模式生成，配置 AI 接口后批注更毒舌。）")
            }

            return FortuneEntity(
                date = date,
                luckLevel = level.label,
                themeColor = "#%06X".format(level.hex and 0xFFFFFF),
                yi = JsonArray(pick(yiPool, 3).map { JsonPrimitive(it) }).toString(),
                ji = JsonArray(pick(jiPool, 2).map { JsonPrimitive(it) }).toString(),
                aiComment = comment,
            )
        }
    }
}
