package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DATE
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DEFAULT
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_WEEKDAY
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import com.xiaoming.dailybudget.util.weekdayText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 预算设置页。三块：默认日预算、周几预算、单独某一天。
 * 每一块都明确写清「改了从什么时候生效」，避免用户担心动到历史账。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    rules: List<BudgetRuleEntity>,
    onSetDefault: (Long) -> Unit,
    onSetWeekday: (Int, Long) -> Unit,
    onSetDate: (LocalDate, Long) -> Unit,
    onDeleteRule: (BudgetRuleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val todayKey = today.toString()

    val currentDefault = rules.filter { it.type == TYPE_DEFAULT }
        .maxWithOrNull(compareBy({ it.effectiveDate }, { it.createdAt }))

    val dateRules = rules.filter { it.type == TYPE_DATE }
        .sortedByDescending { it.specificDate ?: "" }

    var showDefaultDialog by remember { mutableStateOf(false) }
    var editingWeekday by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingDeleteRule by remember { mutableStateOf<BudgetRuleEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 默认日预算 ----------
        item {
            TicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("默认日预算")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showDefaultDialog = true }) {
                        Text("修改", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp, end = 5.dp)
                    )
                    MoneyText(
                        text = centsToPlainYuan(currentDefault?.amountCents ?: 0L),
                        fontSize = 30.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "/ 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "改完从今天起生效，之前的日子仍按当时的金额算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 按星期几 ----------
        item {
            TicketCard {
                SectionLabel("按星期几")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "没设置的星期几跟随默认预算",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                DashedDivider()
                for (weekday in 1..7) {
                    val amount = weekdayAmount(rules, weekday, todayKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingWeekday = weekday }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = weekdayText(weekday),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.weight(1f))
                        if (amount == null) {
                            SectionLabel("跟随默认")
                        } else {
                            MoneyText(
                                text = centsToPlainYuan(amount),
                                fontSize = 15.sp,
                                weight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "改",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (weekday != 7) DashedDivider()
                }
            }
        }

        // ---------- 单独某一天 ----------
        item {
            TicketCard {
                SectionLabel("单独设置某一天")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只影响那一天，优先级最高。比如朋友生日要聚餐",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("选一天设置预算", fontSize = 13.sp)
                }
                if (dateRules.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    dateRules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.specificDate
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                    ?.toChineseDate() ?: (rule.specificDate ?: ""),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                            MoneyText(
                                text = centsToPlainYuan(rule.amountCents),
                                fontSize = 14.sp,
                                weight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { pendingDeleteRule = rule },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 优先级说明 ----------
        item {
            TicketCard {
                SectionLabel("预算怎么取值")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "单独某天  ＞  周几  ＞  默认日预算",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当天没花完的钱自动进存钱罐；超支的话从存钱罐里扣。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------- 弹窗 ----------
    if (showDefaultDialog) {
        AmountInputDialog(
            title = "默认日预算",
            subtitle = "从今天起，每天按这个金额算",
            initialYuan = currentDefault?.let { centsToPlainYuan(it.amountCents) } ?: "",
            onDismiss = { showDefaultDialog = false },
            onConfirm = { cents ->
                onSetDefault(cents)
                showDefaultDialog = false
            }
        )
    }

    editingWeekday?.let { weekday ->
        AmountInputDialog(
            title = "${weekdayText(weekday)}的预算",
            subtitle = "从今天起，每个${weekdayText(weekday)}都按这个金额算",
            initialYuan = weekdayAmount(rules, weekday, todayKey)
                ?.let { centsToPlainYuan(it) } ?: "",
            onDismiss = { editingWeekday = null },
            onConfirm = { cents ->
                onSetWeekday(weekday, cents)
                editingWeekday = null
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    pickedDate?.let { date ->
        AmountInputDialog(
            title = "${date.toChineseDate()}的预算",
            subtitle = "只影响这一天，优先于周几和默认预算",
            onDismiss = { pickedDate = null },
            onConfirm = { cents ->
                onSetDate(date, cents)
                pickedDate = null
            }
        )
    }

    pendingDeleteRule?.let { rule ->
        DeleteConfirmDialog(
            message = "删除这条预算规则？删除后那一天会退回使用上一级预算。",
            onDismiss = { pendingDeleteRule = null },
            onConfirm = {
                onDeleteRule(rule)
                pendingDeleteRule = null
            }
        )
    }
}

/** 取某个星期几当前生效的预算；返回 null 表示跟随默认 */
private fun weekdayAmount(
    rules: List<BudgetRuleEntity>,
    weekday: Int,
    todayKey: String
): Long? = rules
    .filter { it.type == TYPE_WEEKDAY && it.weekday == weekday && it.effectiveDate <= todayKey }
    .maxWithOrNull(compareBy({ it.effectiveDate }, { it.createdAt }))
    ?.amountCents
