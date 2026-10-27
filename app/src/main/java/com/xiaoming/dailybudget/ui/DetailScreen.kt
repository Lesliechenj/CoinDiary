package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import java.time.LocalDate

/**
 * 明细页：一本流水账。
 * 每天一条抬头（日期 + 预算 + 结余），下面是这一天的条目。
 * 天天连起来看，滚存是怎么攒起来／怎么被扣掉的就一目了然。
 */
@Composable
fun DetailScreen(
    transactions: List<TransactionEntity>,
    rules: List<BudgetRuleEntity>,
    onDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    if (transactions.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint("还没有记录，先回首页记一笔")
        }
        return
    }

    // 数据已按日期倒序，groupBy 会保持这个顺序
    val grouped = transactions.groupBy { it.date }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp)
    ) {
        grouped.forEach { (dateText, list) ->
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
            val budget = date?.let { BudgetCalculator.budgetForDay(it, rules) } ?: 0L
            val spent = list.sumOf { it.amountCents }
            val balance = budget - spent

            item(key = "header-$dateText") {
                Column(Modifier.padding(top = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = date?.toChineseDate() ?: dateText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.weight(1f))
                        SectionLabel("预算 ${centsToPlainYuan(budget)}")
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("花 ${centsToPlainYuan(spent)}")
                        Spacer(Modifier.weight(1f))
                        MoneyText(
                            text = (if (balance >= 0L) "+" else "") + centsToPlainYuan(balance),
                            fontSize = 15.sp,
                            weight = FontWeight.SemiBold,
                            color = if (balance < 0L) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    DashedDivider()
                }
            }

            items(list, key = { it.id }) { item ->
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
