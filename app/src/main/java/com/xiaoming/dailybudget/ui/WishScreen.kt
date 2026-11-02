package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.WishEntity
import com.xiaoming.dailybudget.logic.WishLogic
import com.xiaoming.dailybudget.ui.theme.AppMono
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.toChineseDate
import com.xiaoming.dailybudget.util.yuanTextToCents
import java.time.LocalDate

/**
 * 心愿单：把「想买的东西」写下来，再拿存钱罐的钱去对一对，够不够。
 *
 * 为什么要单独做一页？
 * 记账是回头看，心愿单是往前看 ——「照这个攒法，什么时候能买得起」。
 * 它把存钱罐从一个抽象数字，变成一件具体的、正在靠近的东西。
 *
 * @param monthBalanceCents 「本月 1 号至今」攒下的钱，不是整月结余
 * @param monthThroughDay   统计到本月的第几天，用于把口径写清楚
 */
@Composable
fun WishScreen(
    wishes: List<WishEntity>,
    monthBalanceCents: Long,
    monthBudgetCents: Long,
    monthSpentCents: Long,
    monthThroughDay: Int,
    totalRemainingCents: Long,
    startDate: LocalDate,
    onAdd: (name: String, priceCents: Long) -> Unit,
    onAchieve: (WishEntity, String) -> Unit,
    onUndo: (WishEntity) -> Unit,
    onDelete: (WishEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // 比较基准：当月结余 还是 总存钱罐
    var source by rememberSaveable { mutableStateOf(WishEntity.FROM_MONTH) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WishEntity?>(null) }
    // 钱不够却想买时，先弹一次确认
    var pendingAchieve by remember { mutableStateOf<WishEntity?>(null) }

    val fromMonth = source == WishEntity.FROM_MONTH
    val availableCents = if (fromMonth) monthBalanceCents else totalRemainingCents

    val pending = wishes.count { !it.achieved }
    val achieved = wishes.count { it.achieved }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- 比较基准 ----------
        item {
            TicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("拿哪笔钱去比")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "够就买得起",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceTab(
                        label = "本月至今",
                        amountCents = monthBalanceCents,
                        selected = fromMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { source = WishEntity.FROM_MONTH }
                    )
                    SourceTab(
                        label = "总存钱罐",
                        amountCents = totalRemainingCents,
                        selected = !fromMonth,
                        modifier = Modifier.weight(1f),
                        onClick = { source = WishEntity.FROM_TOTAL }
                    )
                }
                Spacer(Modifier.height(10.dp))
                DashedDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (fromMonth) {
                        val span = if (monthThroughDay == 1) "本月 1 日" else "本月 1–${monthThroughDay} 日"
                        "$span：预算 ${centsToPlainYuan(monthBudgetCents)}" +
                            " − 已花 ${centsToPlainYuan(monthSpentCents)}"
                    } else {
                        "${startDate.toChineseDate()}起，所有日子攒下来的"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 清单标题 ----------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "心愿清单", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    SectionLabel("还没实现 $pending 件 · 已实现 $achieved 件")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showAddDialog = true }) {
                    Text("＋ 添加心愿", fontSize = 13.sp)
                }
            }
        }

        if (wishes.isEmpty()) {
            item { EmptyHint("把想买的东西写下来，看看存钱罐够不够") }
        } else {
            items(wishes, key = { it.id }) { wish ->
                WishCard(
                    wish = wish,
                    availableCents = availableCents,
                    onAchieve = {
                        if (WishLogic.canAfford(wish.priceCents, availableCents)) {
                            onAchieve(wish, source)
                        } else {
                            pendingAchieve = wish
                        }
                    },
                    onUndo = { onUndo(wish) },
                    onDelete = { pendingDelete = wish }
                )
            }
        }
    }

    if (showAddDialog) {
        WishAddDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, priceCents ->
                onAdd(name, priceCents)
                showAddDialog = false
            }
        )
    }

    pendingAchieve?.let { wish ->
        val shortfall = WishLogic.shortfallCents(wish.priceCents, availableCents)
        AlertDialog(
            onDismissRequest = { pendingAchieve = null },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("钱还不够", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "「${wish.name}」要 ${centsToPlainYuan(wish.priceCents)} 元，" +
                        "现在还差 ${centsToPlainYuan(shortfall)} 元。\n\n" +
                        "现在实现会让存钱罐透支，结余变成负数。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAchieve(wish, source)
                    pendingAchieve = null
                }) { Text("仍要实现") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAchieve = null }) { Text("再等等") }
            }
        )
    }

    pendingDelete?.let { wish ->
        DeleteConfirmDialog(
            message = if (wish.achieved) {
                "删除「${wish.name}」？已实现记的那笔支出会一起删掉，存钱罐随之恢复。"
            } else {
                "删除心愿「${wish.name}」？"
            },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDelete(wish)
                pendingDelete = null
            }
        )
    }
}

/**
 * 比较基准的切换块：选中时墨底纸字，未选中时纸底墨字。
 * 两个块里的金额是同一个东西的两种口径，所以并排放在一起。
 */
@Composable
private fun SourceTab(
    label: String,
    amountCents: Long,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.onSurface else Color.Transparent
    val fg = if (selected) scheme.surface else scheme.onSurface
    val fgSoft = if (selected) scheme.surface.copy(alpha = 0.75f) else scheme.onSurfaceVariant
    val line = if (selected) scheme.onSurface else scheme.outlineVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 0.7.sp,
            fontWeight = FontWeight.Medium,
            color = fgSoft
        )
        Spacer(Modifier.height(4.dp))
        MoneyText(
            text = centsToPlainYuan(amountCents),
            fontSize = 16.sp,
            color = fg,
            weight = FontWeight.SemiBold
        )
    }
}

/** 一个心愿：名称、价格，再加一句「够不够」的结论 */
@Composable
private fun WishCard(
    wish: WishEntity,
    availableCents: Long,
    onAchieve: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val affordable = WishLogic.canAfford(wish.priceCents, availableCents)
    val remainingAfter = WishLogic.remainingAfterBuy(wish.priceCents, availableCents)
    val shortfall = WishLogic.shortfallCents(wish.priceCents, availableCents)

    TicketCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = wish.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (wish.achieved) TextDecoration.LineThrough else null,
                    color = if (wish.achieved) scheme.onSurfaceVariant else scheme.onSurface
                )
                if (wish.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = wish.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            MoneyText(
                text = centsToPlainYuan(wish.priceCents),
                fontSize = 17.sp,
                color = if (wish.achieved) scheme.onSurfaceVariant else scheme.onSurface
            )
        }

        Spacer(Modifier.height(11.dp))
        DashedDivider()
        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 结论：够 / 不够 / 已实现
            when {
                wish.achieved -> Text(
                    text = "已实现 · 已计入支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.secondary
                )

                affordable -> Text(
                    text = "买得起 · 买完还剩 ${centsToPlainYuan(remainingAfter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.secondary
                )

                else -> Text(
                    text = "预算不足 · 还差 ${centsToPlainYuan(shortfall)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error
                )
            }

            Spacer(Modifier.weight(1f))

            if (wish.achieved) {
                TextButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("撤销", fontSize = 12.sp)
                }
            } else if (affordable) {
                Button(
                    onClick = onAchieve,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text("已实现心愿", fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onAchieve,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurfaceVariant)
                ) {
                    Text("仍要实现", fontSize = 12.sp)
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除心愿",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** 添加心愿：名称 + 价格 */
@Composable
private fun WishAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, priceCents: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    val priceCents = yuanTextToCents(priceText)
    val valid = name.isNotBlank() && priceCents != null && priceCents > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("添加心愿", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("想买什么") },
                    placeholder = { Text("比如：机械键盘") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input -> priceText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("价格（元）") },
                    placeholder = { Text("499") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AppMono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { priceCents?.let { onConfirm(name, it) } },
                enabled = valid
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
