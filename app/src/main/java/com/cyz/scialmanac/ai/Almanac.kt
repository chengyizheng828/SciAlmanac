package com.cyz.scialmanac.ai

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 离线内置黄历：干支纪日 + 建除十二神（值星）+ 值星宜忌 + 彭祖百忌。
 * 不依赖网络与农历库，供 AI 作为每日推算的真实根据。
 *
 * 校验锚点：1900-01-01 为甲戌日（六十甲子第 11 位，index 10）。
 * 节气按常年平均日期（±1 天）定节气月，用于确定建除值星。
 */
object Almanac {

    private val GAN = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val ZHI = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    /** 六十甲子 index -> 干支 */
    private fun ganzhi(index: Int): String = GAN[index % 10] + ZHI[index % 12]

    private val ANCHOR = LocalDate.of(1900, 1, 1)          // 甲戌日
    private const val ANCHOR_INDEX = 10                     // 甲子=0 ... 甲戌=10

    /** 日干支（60 进制循环） */
    fun dayGanZhi(date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(ANCHOR, date)
        val idx = ((ANCHOR_INDEX + days) % 60 + 60) % 60
        return ganzhi(idx.toInt())
    }

    /** 节气（常年近似日期）与对应的节气月地支：寅月起立春 */
    private val TERMS = listOf(
        Triple(2, 4, 2),    // 立春 -> 寅月
        Triple(3, 6, 3),    // 惊蛰 -> 卯月
        Triple(4, 5, 4),    // 清明 -> 辰月
        Triple(5, 6, 5),    // 立夏 -> 巳月
        Triple(6, 6, 6),    // 芒种 -> 午月
        Triple(7, 7, 7),    // 小暑 -> 未月
        Triple(8, 8, 8),    // 立秋 -> 申月
        Triple(9, 8, 9),    // 白露 -> 酉月
        Triple(10, 8, 10),  // 寒露 -> 戌月
        Triple(11, 8, 11),  // 立冬 -> 亥月
        Triple(12, 7, 0),   // 大雪 -> 子月
        Triple(1, 6, 1),    // 小寒 -> 丑月
    )

    /** 节气月地支（建除基准）。立春前（含1月1-5日）为上一年的丑月。 */
    fun monthBranch(date: LocalDate): Int {
        val md = date.monthValue * 100 + date.dayOfMonth
        if (md < 204) return 1 // 小寒后至立春前：丑月
        var branch = 2
        for ((m, d, b) in TERMS) {
            if (m >= 2 && md >= m * 100 + d) branch = b
        }
        return branch
    }

    /** 建除十二神：建、除、满、平、定、执、破、危、成、收、开、闭 */
    private val ZHIXING = listOf("建", "除", "满", "平", "定", "执", "破", "危", "成", "收", "开", "闭")

    /** 口诀：建满平收黑，除危定执黄，成开皆可用，闭破不相当 */
    private val HUANG_DAO = setOf("除", "危", "定", "执", "成", "开")

    fun zhiXing(date: LocalDate): String {
        val dayBranch = ZHI.indexOf(dayGanZhi(date).substring(1))
        val offset = (dayBranch - monthBranch(date) + 12) % 12
        return ZHIXING[offset]
    }

    fun isHuangDao(zhiXing: String): Boolean = zhiXing in HUANG_DAO

    /** 值星传统宜忌（供 AI 映射为实验室操作） */
    private val YI = mapOf(
        "建" to "出行、上任、会友、上书、见工",
        "除" to "除服、疗病、出行、拆卸、入宅",
        "满" to "祈福、祭祀、结亲、开市、交易",
        "平" to "修饰垣墙、平治道涂、修饰粉饰",
        "定" to "交易、立券、会友、签约、纳畜",
        "执" to "祈福、祭祀、求子、结婚、立约",
        "破" to "破屋坏垣、求医、治病、服药",
        "危" to "祭祀、祈福、安床、拆卸",
        "成" to "开市、嫁娶、入学、赴任、安床",
        "收" to "祭祀、求财、签约、嫁娶、纳畜",
        "开" to "祭祀、祈福、入学、上任、修造",
        "闭" to "祭祀、祈福、筑堤、埋池、补垣",
    )
    private val JI = mapOf(
        "建" to "动土、开仓、嫁娶、纳财、掘井",
        "除" to "求官、上任、开张、搬家、探病",
        "满" to "服药、求医、栽种、动土、迁移",
        "平" to "祈福、求嗣、赴任、嫁娶、开市、安葬",
        "定" to "种植、置业、卖田、掘井、造船",
        "执" to "开市、交易、搬家、远行、开仓",
        "破" to "嫁娶、出行、搬迁、筑堤、签约",
        "危" to "登山、乘船、出行、嫁娶、造葬",
        "成" to "诉讼、安门、放水、开渠、动土",
        "收" to "开市、安床、安葬、入宅、出行",
        "开" to "安葬、动土、词讼、求医、伐木",
        "闭" to "开市、出行、求医、手术、嫁娶",
    )

    /** 彭祖百忌：日干 */
    private val PENG_GAN = mapOf(
        "甲" to "甲不开仓，财物耗亡", "乙" to "乙不栽植，千株不长",
        "丙" to "丙不修灶，必见灾殃", "丁" to "丁不剃头，头主生疮",
        "戊" to "戊不受田，田主不祥", "己" to "己不破券，二比并亡",
        "庚" to "庚不经络，织机虚张", "辛" to "辛不合酱，主人不尝",
        "壬" to "壬不泱水，更难提防", "癸" to "癸不词讼，理弱敌强",
    )
    /** 彭祖百忌：日支 */
    private val PENG_ZHI = mapOf(
        "子" to "子不问卜，自惹祸殃", "丑" to "丑不冠带，主不还乡",
        "寅" to "寅不祭祀，神鬼不尝", "卯" to "卯不穿井，水泉不香",
        "辰" to "辰不哭泣，必主重丧", "巳" to "巳不远行，财物伏藏",
        "午" to "午不苫盖，屋主更张", "未" to "未不服药，毒气入肠",
        "申" to "申不安床，鬼祟入房", "酉" to "酉不会客，醉坐颠狂",
        "戌" to "戌不吃犬，作怪上床", "亥" to "亥不嫁娶，不利新郎",
    )

    /** 供 AI 参考的当日黄历文本块 */
    fun dailyBlock(date: LocalDate): String {
        val gz = dayGanZhi(date)
        val zx = zhiXing(date)
        val hd = if (isHuangDao(zx)) "黄道日" else "黑道日"
        return "【今日真实黄历】${gz}日，建除十二神「${zx}日」（${hd}）；" +
            "值星宜：${YI[zx]}；值星忌：${JI[zx]}；" +
            "彭祖百忌：${PENG_GAN[gz.substring(0, 1)]}；${PENG_ZHI[gz.substring(1)]}。"
    }
}
