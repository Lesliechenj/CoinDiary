package com.xiaoming.dailybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoming.dailybudget.data.AppDatabase
import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DATE
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DEFAULT
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_WEEKDAY
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 全局状态管理：读数据库、算汇总、写数据。
 * 界面上显示的所有数字都从这里来。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val transactionDao = db.transactionDao()
    private val budgetRuleDao = db.budgetRuleDao()

    /** 全部支出记录（数据库一变，这个流就自动推新值） */
    val transactions: StateFlow<List<TransactionEntity>> = transactionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部预算规则 */
    val rules: StateFlow<List<BudgetRuleEntity>> = budgetRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 汇总结果：今日预算、今日已花、结转、总剩余 */
    val summary: StateFlow<BudgetCalculator.Summary> =
        combine(transactions, rules) { txs, rs ->
            BudgetCalculator.calculate(LocalDate.now(), txs, rs)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetCalculator.calculate(LocalDate.now(), emptyList(), emptyList())
        )

    /** 记一笔支出 */
    fun addExpense(date: LocalDate, amountCents: Long, category: String, note: String) {
        viewModelScope.launch {
            transactionDao.insert(
                TransactionEntity(
                    date = date.toString(),
                    amountCents = amountCents,
                    category = category,
                    note = note.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteTransaction(item: TransactionEntity) {
        viewModelScope.launch { transactionDao.delete(item) }
    }

    /** 改默认日预算：从今天起生效，过去的日期仍按当时的预算算 */
    fun setDefaultBudget(amountCents: Long) {
        viewModelScope.launch {
            budgetRuleDao.insert(
                BudgetRuleEntity(
                    type = TYPE_DEFAULT,
                    amountCents = amountCents,
                    effectiveDate = LocalDate.now().toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 设置周几的预算：从今天起，每个星期这一天都按这个金额算 */
    fun setWeekdayBudget(weekday: Int, amountCents: Long) {
        viewModelScope.launch {
            budgetRuleDao.insert(
                BudgetRuleEntity(
                    type = TYPE_WEEKDAY,
                    amountCents = amountCents,
                    weekday = weekday,
                    effectiveDate = LocalDate.now().toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 只给某一天单独设预算：优先级最高，只影响那一天 */
    fun setDateBudget(date: LocalDate, amountCents: Long) {
        viewModelScope.launch {
            budgetRuleDao.insert(
                BudgetRuleEntity(
                    type = TYPE_DATE,
                    amountCents = amountCents,
                    specificDate = date.toString(),
                    effectiveDate = date.toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 删除一条预算规则（删掉后那天会退回上一级预算） */
    fun deleteRule(rule: BudgetRuleEntity) {
        viewModelScope.launch { budgetRuleDao.delete(rule) }
    }
}
