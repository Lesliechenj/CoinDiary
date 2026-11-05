package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.SavingEntity
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import java.time.LocalDate

/**
 * 明细页：一本流水账。
 *
 * 分两本：**支出** 是往回看的账（花在哪了），**攒钱** 是往前看的账（省下多少了）。
 * 两本不混排，因为混在一起后「这天花了多少」就说不清了 —— 攒钱不是支出。
 */
@Composable
fun DetailScreen(
    transactions: List<TransactionEntity>,
    rules: List<BudgetRuleEntity>,
    savings: List<SavingEntity>,
    onDelete: (TransactionEntity) -> Unit,
    onDeleteSaving: (SavingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDeleteSaving by remember { mutableStateOf<SavingEntity?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailTab("支出", selected = tab == 0, onClick = { tab = 0 })
            Spacer(Modifier.width(18.dp))
            DetailTab("攒钱", selected = tab == 1, onClick = { tab = 1 })
            Spacer(Modifier.weight(1f))
            SectionLabel(if (tab == 0) "共 ${transactions.size} 笔" else "共 ${savings.size} 笔")
        }

        if (tab == 0) {
            if (transactions.isEmpty()) {
                EmptyHint("还没有记录，先回首页记一笔")
            } else {
                ExpenseList(transactions, rules, onDelete = { pendingDelete = it })
            }
        } else {
            if (savings.isEmpty()) {
                EmptyHint("还没攒过钱，点首页存钱罐的「攒一笔」")
            } else {
                SavingList(savings, onDelete = { pendingDeleteSaving = it })
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

    pendingDeleteSaving?.let { item ->
        DeleteConfirmDialog(
            message = "删除「${item.source} ${centsToPlainYuan(item.amountCents)} 元」这笔攒钱？存钱罐会少这些钱。",
            onDismiss = { pendingDeleteSaving = null },
            onConfirm = {
                onDeleteSaving(item)
                pendingDeleteSaving = null
            }
        )
    }
}

/** 支出流水：每天一条抬头（日期 + 预算 + 结余），下面是这一天的条目 */
@Composable
private fun ExpenseList(
    transactions: List<TransactionEntity>,
    rules: List<BudgetRuleEntity>,
    onDelete: (TransactionEntity) -> Unit
) {
    // 数据已按日期倒序，groupBy 会保持这个顺序
    val grouped = transactions.groupBy { it.date }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 100.dp)
    ) {
        grouped.forEach { (dateText, list) ->
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
            val budget = date?.let { BudgetCalculator.budgetForDay(it, rules) } ?: 0L
            val spent = list.sumOf { it.amountCents }
            val balance = budget - spent

            item(key = "header-$dateText") {
                Column(Modifier.padding(top = 18.dp)) {
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
                TransactionRow(item = item, onDelete = { onDelete(item) })
            }
        }
    }
}

/** 攒钱流水：一行一笔，绿色的加号表示往存钱罐里放钱 */
@Composable
private fun SavingList(
    savings: List<SavingEntity>,
    onDelete: (SavingEntity) -> Unit
) {
    val total = savings.sumOf { it.amountCents }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("累计攒了")
                Spacer(Modifier.weight(1f))
                MoneyText(
                    text = centsToPlainYuan(total),
                    fontSize = 15.sp,
                    weight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        items(savings, key = { it.id }) { item ->
            SavingRow(item = item, onDelete = { onDelete(item) })
        }
    }
}

/** 一笔攒钱：色点 + 方式 + 日期 …… +金额 + 删除 */
@Composable
private fun SavingRow(item: SavingEntity, onDelete: () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val date = runCatching { LocalDate.parse(item.date) }.getOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = size.height * 0.02f + 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text = item.source, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = date?.toChineseDate() ?: item.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MoneyText(
            text = "+" + centsToPlainYuan(item.amountCents),
            fontSize = 15.sp,
            weight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 顶部两本账的切换：选中的字加点分量，下面压一条短横线 */
@Composable
private fun DetailTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(14.dp)
                .height(2.dp)
                .background(if (selected) scheme.onSurface else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}
