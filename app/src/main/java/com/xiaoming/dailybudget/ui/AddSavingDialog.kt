package com.xiaoming.dailybudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoming.dailybudget.ui.theme.AppMono
import com.xiaoming.dailybudget.util.yuanTextToCents

/** 常用攒钱方式，点一下直接填进输入框，不用每个字都打 */
private val SUGGESTIONS = listOf("少喝奶茶", "自己做饭", "今天没打车", "卖掉闲置")

/**
 * 记一笔攒钱：怎么攒的 + 攒了多少。
 *
 * 只问这两个问题。攒钱是件高兴事，别让人先填三张表。
 */
@Composable
fun AddSavingDialog(
    onDismiss: () -> Unit,
    onConfirm: (source: String, amountCents: Long) -> Unit
) {
    var source by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val amountCents = yuanTextToCents(amountText)
    val valid = source.isNotBlank() && amountCents != null && amountCents > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("攒一笔", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it.take(20) },
                    label = { Text("怎么攒的") },
                    placeholder = { Text("比如：少喝奶茶") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                // 两行四个预设，一个都点不动的时候还能自由输入
                SUGGESTIONS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { text ->
                            Suggestion(label = text, onClick = { source = text })
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                }
                Spacer(Modifier.height(3.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("金额（元）") },
                    placeholder = { Text("50") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AppMono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amountCents?.let { onConfirm(source, it) } },
                enabled = valid
            ) { Text("记下") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 细描边的小方块，点一下把那句话填进输入框 */
@Composable
private fun Suggestion(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant
        )
    }
}
