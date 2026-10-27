package com.xiaoming.dailybudget.util

import java.time.LocalDate
import java.util.Locale

/** 分 → "¥12.34" */
fun centsToYuanText(cents: Long): String =
    String.format(Locale.CHINA, "¥%.2f", cents / 100.0)

/** 分 → "12.34"（不带符号，用于输入框回填） */
fun centsToPlainYuan(cents: Long): String =
    String.format(Locale.CHINA, "%.2f", cents / 100.0)

/**
 * 分 → 日历格子里的紧凑写法。
 * 格子只有 40dp 宽，塞不下 "1234.00"：
 *   2000   → "20"     整元不显示小数
 *   2550   → "25.5"   有角无分时留一位
 *   2565   → "25.7"   四舍五入到角
 *   123400 → "1.2k"   超过千元换成 k，保住可读性
 */
fun centsToCompactYuan(cents: Long): String {
    val yuan = cents / 100.0
    return when {
        yuan >= 10_000 -> String.format(Locale.CHINA, "%.0fk", yuan / 1000)
        yuan >= 1_000 -> String.format(Locale.CHINA, "%.1fk", yuan / 1000)
        cents % 100 == 0L -> (cents / 100).toString()
        else -> String.format(Locale.CHINA, "%.1f", yuan)
    }
}

/** 输入框里的「元」文本 → 分；输错了返回 null */
fun yuanTextToCents(text: String): Long? {
    val value = text.trim().toDoubleOrNull() ?: return null
    if (value < 0) return null
    return Math.round(value * 100)
}

/** 星期几的中文名，1=周一 ... 7=周日 */
fun weekdayText(weekday: Int): String = when (weekday) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

/** "10月26日 周一" */
fun LocalDate.toChineseDate(): String =
    "${monthValue}月${dayOfMonth}日 ${weekdayText(dayOfWeek.value)}"

/** 是否今天 */
fun LocalDate.isToday(): Boolean = this == LocalDate.now()
