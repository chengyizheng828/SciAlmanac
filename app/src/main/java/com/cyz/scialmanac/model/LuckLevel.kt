package com.cyz.scialmanac.model

import androidx.compose.ui.graphics.Color

enum class LuckLevel(val label: String, val hex: Long) {
    DA_JI("大吉", 0xFF10B981),
    ZHONG_JI("中吉", 0xFF3B82F6),
    XIAO_JI("小吉", 0xFF06B6D4),
    PING("平", 0xFFF59E0B),
    XIAO_XIONG("小凶", 0xFFF97316),
    ZHONG_XIONG("中凶", 0xFFEF4444),
    DA_XIONG("大凶", 0xFF991B1B),
    ;

    val color: Color get() = Color(hex)

    companion object {
        fun from(label: String?): LuckLevel =
            when (label) {
                // 旧版数据兼容：凶 -> 中凶，诸事不宜 -> 大凶
                "凶" -> ZHONG_XIONG
                "诸事不宜" -> DA_XIONG
                else -> entries.firstOrNull { it.label == label } ?: PING
            }

        fun allLabels(): List<String> = entries.map { it.label }
    }
}
