package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.util.centsToPlainYuan
import com.xiaoming.dailybudget.util.yuanTextToCents

/**
 * 票据卡片：近乎直角的容器 + 1dp 细描边，不用阴影。
 * 整个 App 的信息块都用它，保证视觉语言统一。
 */
@Composable
fun TicketCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(16.dp),
        content = content
    )
}

/** 虚线分割线：模仿小票的撕口与分栏 */
@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                )
            }
    )
}

/** 小标签：字距拉开，安静地待在数字上方 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 金额文本：全应用统一等宽。
 * 等宽让「20.00 / 6.00」这种竖排数字天然对齐，不用手工补空格。
 */
@Composable
fun MoneyText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    weight: FontWeight = FontWeight.Bold
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-0.3).sp
    )
}

/** 三栏小指标：标签在上，数字在下 */
@Composable
fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(3.dp))
        MoneyText(text = value, fontSize = 14.sp, color = valueColor, weight = FontWeight.SemiBold)
    }
}

/** 通用金额输入弹窗 */
@Composable
fun AmountInputDialog(
    title: String,
    subtitle: String? = null,
    initialYuan: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember { mutableStateOf(initialYuan) }
    val cents = yuanTextToCents(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { cents?.let(onConfirm) }, enabled = cents != null) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 删除确认弹窗 */
@Composable
fun DeleteConfirmDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("确认删除", style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 一条支出记录，做成小票里的一行：
 * 分类色点 + 名称备注 ... 金额（右对齐）+ 细小的删除按钮。
 */
@Composable
fun TransactionRow(
    item: TransactionEntity,
    onDelete: () -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
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
                .background(categoryColor(item.category))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text = item.category, style = MaterialTheme.typography.bodyLarge)
            if (item.note.isNotBlank()) {
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        MoneyText(
            text = "-" + centsToPlainYuan(item.amountCents),
            fontSize = 15.sp,
            weight = FontWeight.SemiBold
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

/** 分类色：低饱和，避免彩色圆点抢走金额的注意力 */
fun categoryColor(category: String): Color = when (category) {
    "餐饮" -> Color(0xFFA65A2E)
    "交通" -> Color(0xFF3A5F8A)
    "购物" -> Color(0xFF8C3B5A)
    "娱乐" -> Color(0xFF5B4A8C)
    "居家" -> Color(0xFF2F6B4F)
    "医疗" -> Color(0xFFA63A3A)
    else -> Color(0xFF6B6F73)
}

/** 空状态：不道歉，直接说下一步做什么 */
@Composable
fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
