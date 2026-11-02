package com.xiaoming.dailybudget.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xiaoming.dailybudget.R

/**
 * App 内置字体。
 *
 * 为什么要把字体打进安装包，而不是用手机自带字体？
 * 因为各家手机（小米、华为、OPPO…）会替换系统字体，
 * 同一个 App 在不同手机上长得不一样，设计稿里的「票据感」就散了。
 * 把字体打进包里，每台手机看到的都是同一套字。
 *
 * 两种字体分工：
 * - AppSans：思源黑体（Noto Sans SC），负责所有中文和正文。
 *   已裁剪过，只保留 GB2312 全部汉字 + 标点，体积从 8MB 压到 1.9MB；
 *   遇到极生僻字会自动回退到系统字体，不会显示成方块。
 * - AppMono：JetBrains Mono，负责所有金额数字。
 *   等宽的意思是「1」和「0」占一样宽，数字竖着排自动对齐，像小票打印出来的。
 */
val AppSans = FontFamily(
    Font(R.font.app_sans_regular, FontWeight.Normal),
    Font(R.font.app_sans_medium, FontWeight.Medium)
)

val AppMono = FontFamily(
    Font(R.font.app_mono_regular, FontWeight.Normal),
    Font(R.font.app_mono_medium, FontWeight.Medium)
)
