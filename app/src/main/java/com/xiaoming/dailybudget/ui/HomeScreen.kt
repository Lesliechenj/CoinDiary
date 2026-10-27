package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DATE
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_WEEKDAY
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import com.xiaoming.dailybudget.util.weekdayText
import java.time.LocalDate
import kotlin.math.abs

/**
 * 首页。整个页面只回答一个问题：今天还能花多少。
 * 所以最大的字只留给那个数字，其余信息按票据分栏排在下面。
 */
@Composable
fun HomeScreen(
    summary: BudgetCalculator.Summary,
    rules: List<BudgetRuleEntity>,
    todayTransactions: List<TransactionEntity>,
    onDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val todayKey = today.toString()
    // 大字只看今天这一天：今日预算 − 今日已花。跨天的累计放在下面的存钱罐里。
    val remaining = summary.todayBalanceCents
    val overspent = remaining < 0L
    val accent = if (overspent) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val ratio = if (summary.todayBudgetCents <= 0L) {
        1f
    } else {
        (summary.todaySpentCents.toFloat() / summary.todayBudgetCents).coerceIn(0f, 1f)
    }

    // 今日预算从哪条规则来的
    val sourceText = when {
        rules.any { it.type == TYPE_DATE && it.specificDate == todayKey } -> "今天用的是单独设置的预算"
        rules.any {
            it.type == TYPE_WEEKDAY && it.weekday == today.dayOfWeek.value && it.effectiveDate <= todayKey
        } -> "今天按${weekdayText(today.dayOfWeek.value)}的预算算"
        else -> "按默认日预算算"
    }

    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 今日票据 ----------
        item {
            TicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(if (overspent) "今日超支" else "今日结余")
                    Spacer(Modifier.weight(1f))
                    if (overspent) {
                        StampChip("超支 " + centsToPlainYuan(-remaining))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        modifier = Modifier.padding(bottom = 7.dp, end = 5.dp)
                    )
                    MoneyText(
                        text = centsToPlainYuan(remaining),
                        fontSize = 42.sp,
                        color = accent
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                DashedDivider()
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    MiniStat(
                        label = "今日预算",
                        value = centsToPlainYuan(summary.todayBudgetCents),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "今日已花",
                        value = centsToPlainYuan(summary.todaySpentCents),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStat(
                        label = "之前结余",
                        value = centsToPlainYuan(summary.carriedInCents),
                        modifier = Modifier.weight(1f),
                        valueColor = if (summary.carriedInCents < 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                TicksBar(
                    ratio = ratio,
                    active = if (ratio >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---------- 存钱罐（截至今日） ----------
        item {
            TicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("存钱罐 · 截至今日")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "自 ${summary.startDate.toChineseDate()} 起",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                MoneyText(
                    text = centsToPlainYuan(summary.totalRemainingCents),
                    fontSize = 28.sp,
                    color = if (summary.totalRemainingCents < 0L) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "存钱罐 = 之前结余 + 今日结余",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                DashedDivider()
                Spacer(Modifier.height(14.dp))
                WeekBars(summary.days)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("近 7 天每天的结余")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (summary.totalRemainingCents < 0L) "已透支" else "有结余",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (summary.totalRemainingCents < 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }
        }

        // ---------- 今日花费 ----------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日花费",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                SectionLabel("共 ${todayTransactions.size} 笔")
            }
        }

        if (todayTransactions.isEmpty()) {
            item { EmptyHint("今天还没记账，点右下角「记一笔」") }
        } else {
            items(todayTransactions, key = { it.id }) { item ->
                TransactionRow(item = item, onDelete = { pendingDelete = item })
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

/**
 * 印章样式的小标签，轻微倾斜，像盖在票据上的章。
 * 只有超支时才出现，全 App 就这一处用红色。
 */
@Composable
private fun StampChip(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .rotate(-4f)
            .border(1.5.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = color
        )
    }
}

/**
 * 刻度尺式进度：24 段小刻度，每 4 段一根高的，
 * 比一条平滑进度条更像仪表，扫一眼就知道花了多少。
 */
@Composable
private fun TicksBar(ratio: Float, active: Color) {
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val filled = (ratio * 24f).toInt().coerceIn(0, 24)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(24) { index ->
            val major = index % 4 == 0
            Box(
                Modifier
                    .weight(1f)
                    .height(if (major) 14.dp else 9.dp)
                    .background(
                        color = if (index < filled) active else inactive,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

/** 近 7 天柱状图：正数向上用苔绿，超支用印章红，一眼看出滚存走势 */
@Composable
private fun WeekBars(days: List<BudgetCalculator.DayStat>) {
    val last7 = days.take(7).reversed()
    if (last7.isEmpty()) return

    val maxAbs = last7.maxOf { abs(it.balanceCents) }.coerceAtLeast(1L)
    val positive = MaterialTheme.colorScheme.secondary
    val negative = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        last7.forEach { day ->
            val fraction = (abs(day.balanceCents).toFloat() / maxAbs).coerceIn(0.06f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((40f * fraction).dp)
                        .background(
                            color = if (day.balanceCents < 0L) negative else positive,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = weekdayText(day.date.dayOfWeek.value).takeLast(1),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
