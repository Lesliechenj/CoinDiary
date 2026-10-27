package com.xiaoming.dailybudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaoming.dailybudget.ui.AddTransactionDialog
import com.xiaoming.dailybudget.ui.BudgetScreen
import com.xiaoming.dailybudget.ui.CalendarScreen
import com.xiaoming.dailybudget.ui.DashedDivider
import com.xiaoming.dailybudget.ui.DetailScreen
import com.xiaoming.dailybudget.ui.HomeScreen
import com.xiaoming.dailybudget.ui.theme.DailyBudgetTheme
import com.xiaoming.dailybudget.util.toChineseDate
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailyBudgetTheme {
                AppRoot()
            }
        }
    }
}

/** 底部四项只要文字，不要图标药丸 —— 票据风格不需要 Material 的胶囊指示器 */
private val TABS = listOf("今天", "日历", "明细", "预算")

private val TAB_TITLES = listOf("碎银集", "月视图", "明细", "预算设置")

@Composable
private fun AppRoot() {
    val viewModel: AppViewModel = viewModel()

    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    // 记一笔时预填的日期（日历页点某天补记），null 表示今天
    var addDateKey by rememberSaveable { mutableStateOf<String?>(null) }

    val todayKey = LocalDate.now().toString()
    val todayTransactions = remember(transactions, todayKey) {
        transactions.filter { it.date == todayKey }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = TAB_TITLES[tab],
                subtitle = LocalDate.now().toChineseDate()
            )
        },
        bottomBar = {
            AppBottomBar(selected = tab, onSelect = { tab = it })
        },
        floatingActionButton = {
            if (tab != 3) {
                ExtendedFloatingActionButton(
                    onClick = {
                        addDateKey = null
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("记一笔", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                0 -> HomeScreen(
                    summary = summary,
                    rules = rules,
                    todayTransactions = todayTransactions,
                    onDelete = viewModel::deleteTransaction
                )
                1 -> CalendarScreen(
                    transactions = transactions,
                    rules = rules,
                    onDelete = viewModel::deleteTransaction,
                    onAddForDate = { date ->
                        addDateKey = date.toString()
                        showAddDialog = true
                    }
                )
                2 -> DetailScreen(
                    transactions = transactions,
                    rules = rules,
                    onDelete = viewModel::deleteTransaction
                )
                else -> BudgetScreen(
                    rules = rules,
                    onSetDefault = viewModel::setDefaultBudget,
                    onSetWeekday = viewModel::setWeekdayBudget,
                    onSetDate = viewModel::setDateBudget,
                    onDeleteRule = viewModel::deleteRule
                )
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            initialDate = addDateKey
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(),
            onDismiss = {
                showAddDialog = false
                addDateKey = null
            },
            onConfirm = { date, amountCents, category, note ->
                viewModel.addExpense(date, amountCents, category, note)
                showAddDialog = false
                addDateKey = null
            }
        )
    }
}

/** 顶部：标题左对齐，日期靠右，下面一条虚线像票据抬头 */
@Composable
private fun AppHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        DashedDivider()
    }
}

/** 底部：纯文字 + 一条短横线表示选中，安静地待在页面底部 */
@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .drawBehind {
                drawLine(
                    color = hairline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            TABS.forEachIndexed { index, label ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .background(
                                color = if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent
                            )
                    )
                }
            }
        }
    }
}
