package com.xiaoming.dailybudget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 视觉方向：收银小票 / 存根
 *
 * 为什么不是常见的绿色 Material 卡片？
 * 因为记账这个动作的本质就是「一张张票据」。
 * 所以：纸面底色、细描边、虚线撕口、等宽数字，
 * 红色只留给「超支」这一件事，其他全部安静。
 */

// ---------- 纸与墨 ----------
val Paper = Color(0xFFF2F1EC)      // 页面底色，微暖灰，长时间看不刺眼
val Receipt = Color(0xFFFBFAF6)    // 票据白，比底色亮一档形成层次
val Ink = Color(0xFF17181A)        // 主文字
val InkSoft = Color(0xFF6B6F73)    // 次级文字
val Seal = Color(0xFFC0392B)       // 印章红：超支、删除，全应用只在这一类地方出现
val Moss = Color(0xFF2F6B4F)       // 苔绿：结余

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Receipt,
    secondary = Moss,
    onSecondary = Receipt,
    tertiary = Seal,
    background = Paper,
    onBackground = Ink,
    surface = Receipt,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E5DC),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFB7B5AB),
    outlineVariant = Color(0xFFDAD8CE),
    error = Seal,
    onError = Receipt
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFECEDE7),
    onPrimary = Color(0xFF14151A),
    secondary = Color(0xFF7FC9A3),
    onSecondary = Color(0xFF10231A),
    tertiary = Color(0xFFE2695A),
    background = Color(0xFF101114),
    onBackground = Color(0xFFECEDE7),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFECEDE7),
    surfaceVariant = Color(0xFF24252B),
    onSurfaceVariant = Color(0xFF9CA0A6),
    outline = Color(0xFF4B4E54),
    outlineVariant = Color(0xFF2C2E34),
    error = Color(0xFFE2695A),
    onError = Color(0xFF14151A)
)

/**
 * 字阶：标题收紧字距，金额统一等宽。
 * 等宽是这套设计的骨架 —— 数字竖着对齐，像打印出来的小票。
 */
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = (-1).sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.7.sp
    )
)

/** 近乎直角：票据不需要圆润 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun DailyBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
