package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.util.centsToCompactYuan
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import com.xiaoming.dailybudget.util.weekdayText
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月视图：一张日历，把整月的消费摊在格子里。
 *
 * 为什么用日历而不是再做一张列表？
 * 因为「哪天花超了、哪天空着」这种模式，只有排成格子才能一眼看出来 ——
 * 列表只能一条一条读，格子能扫。
 *
 * 每个格子的信息按优先级只有三层：
 *   1. 日期数字
 *   2. 这天的支出（整元不显示小数，超支标红）
 *   3. 底部一条细刻度，长度 = 这天花了当天预算的多少
 * 点格子，下面立刻展开当天的完整明细，可就地删除或补记。
 */
@Composable
fun CalendarScreen(
    transactions: List<TransactionEntity>,
    rules: List<BudgetRuleEntity>,
    onDelete: (TransactionEntity) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)

    // 用字符串存月份和选中日，这样旋转屏幕/切页面都不会丢
    var monthKey by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    var selectedKey by rememberSaveable { mutableStateOf(today.toString()) }

    val yearMonth = remember(monthKey) { YearMonth.parse(monthKey) }
    val selectedDate = remember(selectedKey) { runCatching { LocalDate.parse(selectedKey) }.getOrNull() }

    val monthStat = remember(yearMonth, transactions, rules) {
        BudgetCalculator.monthStat(yearMonth, transactions, rules)
    }
    val categories = remember(yearMonth, transactions) {
        BudgetCalculator.monthCategoryTotals(yearMonth, transactions)
    }
    val selectedTransactions = remember(selectedKey, transactions) {
        transactions.filter { it.date == selectedKey }
    }

    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 月度联单：月份 + 汇总 + 日历 ----------
        item {
            TicketCard {
                MonthNavigator(
                    yearMonth = yearMonth,
                    isCurrentMonth = yearMonth == thisMonth,
                    activeDays = monthStat.activeDays,
                    onPrev = {
                        val prev = yearMonth.minusMonths(1)
                        monthKey = prev.toString()
                        selectedKey = defaultSelectedFor(prev, today)
                    },
                    onNext = {
                        val next = yearMonth.plusMonths(1)
                        monthKey = next.toString()
                        selectedKey = defaultSelectedFor(next, today)
                    },
                    onBackToCurrent = {
                        monthKey = thisMonth.toString()
                        selectedKey = today.toString()
                    }
                )

                Spacer(Modifier.height(12.dp))
                DashedDivider()
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth()) {
                    MiniStat(
                        label = "本月预算",
                        value = centsToPlainYuan(monthStat.budgetCents),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "本月已花",
                        value = centsToPlainYuan(monthStat.spentCents),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "本月结余",
                        value = signedYuan(monthStat.balanceCents),
                        modifier = Modifier.weight(1f),
                        valueColor = if (monthStat.balanceCents < 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                DashedDivider()
                Spacer(Modifier.height(10.dp))

                WeekdayHeader()
                Spacer(Modifier.height(2.dp))

                // 日历格子：前面补空位，让 1 号落在正确的星期几
                val leadingBlank = yearMonth.atDay(1).dayOfWeek.value - 1 // 周一 = 0
                val cells: List<BudgetCalculator.DayStat?> =
                    buildList {
                        repeat(leadingBlank) { add(null) }
                        addAll(monthStat.days)
                        while (size % 7 != 0) add(null)
                    }

                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { cell ->
                            if (cell == null) {
                                Spacer(
                                    Modifier
                                        .weight(1f)
                                        .height(CALENDAR_CELL_HEIGHT)
                                )
                            } else {
                                val key = cell.date.toString()
                                DayCell(
                                    stat = cell,
                                    today = today,
                                    selected = selectedKey == key,
                                    onClick = { selectedKey = key },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                CalendarLegend()
            }
        }

        // ---------- 选中日明细 ----------
        item {
            if (selectedDate == null) {
                TicketCard {
                    SectionLabel("当天明细")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "点日历上任意一天，查看那天花了什么",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val dayStat = monthStat.days.getOrNull(selectedDate.dayOfMonth - 1)
                val budget = dayStat?.budgetCents ?: 0L
                val spent = selectedTransactions.sumOf { it.amountCents }
                val balance = budget - spent

                TicketCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedDate.toChineseDate(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedDate == today) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "今天",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        SectionLabel("${selectedTransactions.size} 笔")
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth()) {
                        MiniStat(
                            label = "当天预算",
                            value = if (budget > 0L) centsToPlainYuan(budget) else "—",
                            modifier = Modifier.weight(1f)
                        )
                        MiniStat(
                            label = "当天已花",
                            value = centsToPlainYuan(spent),
                            modifier = Modifier.weight(1f)
                        )
                        MiniStat(
                            label = "当天结余",
                            value = signedYuan(balance),
                            modifier = Modifier.weight(1f),
                            valueColor = if (balance < 0L) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    DashedDivider()

                    if (selectedTransactions.isEmpty()) {
                        Text(
                            text = "这天没有消费记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        selectedTransactions.forEach { item ->
                            TransactionRow(
                                item = item,
                                onDelete = { pendingDelete = item }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onAddForDate(selectedDate) }) {
                            Text(
                                text = "＋ 在这天记一笔",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ---------- 本月分类占比 ----------
        if (categories.isNotEmpty()) {
            item {
                TicketCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("本月花在哪儿")
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "共 ${monthStat.activeDays} 天有消费",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CategoryRanking(categories = categories, totalCents = monthStat.spentCents)
                    Spacer(Modifier.height(6.dp))
                    DashedDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "本月结余只算本月的预算与支出，不含上月结转；" +
                            "跨月的滚存请看首页「存钱罐」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteConfirmDialog(
            message = "删除「${item.category} ${centsToPlainYuan(item.amountCents)} 元」这笔记录？",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDelete(item)
                pendingDelete = null
            }
        )
    }
}

/** 日历格子统一高度，保证 6 行不会因为内容多少而抖动 */
private val CALENDAR_CELL_HEIGHT = 50.dp

/**
 * 切换月份时默认选中哪一天：
 * 本月就选今天，其他月份选 1 号 —— 总要有个落脚点，不能让下面空着。
 */
private fun defaultSelectedFor(target: YearMonth, today: LocalDate): String =
    if (YearMonth.from(today) == target) today.toString() else target.atDay(1).toString()

private fun signedYuan(cents: Long): String =
    (if (cents > 0L) "+" else "") + centsToPlainYuan(cents)

/** 月份导航：左右箭头 + 居中月份，非本月时给一个「回到本月」 */
@Composable
private fun MonthNavigator(
    yearMonth: YearMonth,
    isCurrentMonth: Boolean,
    activeDays: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit
) {
    val soft = MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "上个月",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${yearMonth.year} 年 ${yearMonth.monthValue} 月",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1
            )
            Text(
                text = when {
                    isCurrentMonth -> "本月 · 已记 $activeDays 天"
                    yearMonth.isBefore(YearMonth.now()) -> "已结束的月份"
                    else -> "还没到的月份"
                },
                style = MaterialTheme.typography.labelSmall,
                color = soft
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "下个月",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (!isCurrentMonth) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "回到本月",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onBackToCurrent)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/** 星期表头：周一到周日，和日历格子同宽 */
@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        (1..7).forEach { weekday ->
            Text(
                text = weekdayText(weekday).takeLast(1),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 一个日期格子。
 *
 * 三种状态互相排斥：
 *   - 选中：整块反白（墨色底 + 纸色字），像盖章
 *   - 今天：1dp 描边
 *   - 未来：日期变浅灰，不显示金额
 */
@Composable
private fun DayCell(
    stat: BudgetCalculator.DayStat,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val isToday = stat.date == today
    val isFuture = stat.date.isAfter(today)
    val overspent = stat.spentCents > stat.budgetCents
    val hasSpent = stat.spentCents > 0L

    val dayColor = when {
        selected -> paper
        isFuture -> MaterialTheme.colorScheme.outline
        else -> ink
    }
    val amountColor = when {
        selected -> paper
        !hasSpent -> Color.Transparent
        overspent -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = modifier
            .height(CALENDAR_CELL_HEIGHT)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) ink else Color.Transparent)
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, ink, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = dayColor,
            maxLines = 1
        )

        Spacer(Modifier.height(1.dp))

        // 金额固定占一行，没有支出也留白，保证所有格子对齐
        Box(
            modifier = Modifier.height(13.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasSpent) {
                Text(
                    text = centsToCompactYuan(stat.spentCents),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = amountColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 刻度：长度 = 这天花了当天预算的百分比
        if (hasSpent) {
            val ratio = if (stat.budgetCents > 0L) {
                (stat.spentCents.toFloat() / stat.budgetCents).coerceIn(0f, 1f)
            } else {
                1f
            }
            val track = if (selected) {
                paper.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val barColor = when {
                selected -> paper
                overspent -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(2.dp)
                    .background(track, RoundedCornerShape(1.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/** 图例：三色对应三种状态，说清颜色含义，用户不用猜 */
@Composable
private fun CalendarLegend() {
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(MaterialTheme.colorScheme.secondary, "有结余")
        LegendDot(MaterialTheme.colorScheme.error, "超支")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(5.dp))
            Text(text = "今天", fontSize = 10.sp, color = soft)
        }
        Spacer(Modifier.weight(1f))
        Text(text = "点日期看明细", fontSize = 10.sp, color = soft)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分类排行：每类一条细横条，长度按金额占比。
 * 不做饼图 —— 六个小扇区在手机上很难比较，横向长度更好读。
 */
@Composable
private fun CategoryRanking(categories: List<Pair<String, Long>>, totalCents: Long) {
    val top = categories.take(5)
    val maxCents = top.maxOfOrNull { it.second } ?: 0L

    top.forEachIndexed { index, (category, cents) ->
        val ratio = if (maxCents > 0L) cents.toFloat() / maxCents else 0f
        val percent = if (totalCents > 0L) cents * 100 / totalCents else 0L

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(categoryColor(category))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$percent%",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            MoneyText(
                text = centsToPlainYuan(cents),
                fontSize = 13.sp,
                weight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(1.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(categoryColor(category), RoundedCornerShape(1.dp))
            )
        }
        if (index != top.lastIndex) Spacer(Modifier.height(11.dp))
    }
}
