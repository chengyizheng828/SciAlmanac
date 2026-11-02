package com.cyz.scialmanac.model

import androidx.compose.ui.graphics.Color

enum class LuckLevel(val label: String, val hex: Long) {
    DA_JI("大吉", 0xFF10B981),
    ZHONG_JI("中吉", 0xFF3B82F6),
    XIAO_JI("小吉", 0xFF06B6D4),
    PING("平", 0xFFF59E0B),
    XIONG("凶", 0xFFEF4444),
    ZHISHI_BUYI("诸事不宜", 0xFF991B1B),
    ;

    val color: Color get() = Color(hex)

    companion object {
        fun from(label: String?): LuckLevel =
            entries.firstOrNull { it.label == label } ?: PING

        fun allLabels(): List<String> = entries.map { it.label }
    }
}
