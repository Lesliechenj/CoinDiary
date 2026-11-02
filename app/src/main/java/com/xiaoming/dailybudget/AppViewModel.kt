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
import com.xiaoming.dailybudget.data.WishEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 全局状态管理：读数据库、算汇总、写数据。
 * 界面上显示的所有数字都从这里来。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val transactionDao = db.transactionDao()
    private val budgetRuleDao = db.budgetRuleDao()
    private val wishDao = db.wishDao()

    /** 全部支出记录（数据库一变，这个流就自动推新值） */
    val transactions: StateFlow<List<TransactionEntity>> = transactionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部预算规则 */
    val rules: StateFlow<List<BudgetRuleEntity>> = budgetRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部心愿（未实现在前） */
    val wishes: StateFlow<List<WishEntity>> = wishDao.observeAll()
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

    /**
     * 本月的统计（整月口径）：本月预算合计、本月已花、本月结余。
     * 只用于日历/月视图那种「看整月」的场景。
     */
    val monthStat: StateFlow<BudgetCalculator.MonthStat> =
        combine(transactions, rules) { txs, rs ->
            BudgetCalculator.monthStat(YearMonth.now(), txs, rs)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetCalculator.monthStat(YearMonth.now(), emptyList(), emptyList())
        )

    /**
     * 「本月 1 号至今」的统计 —— 首页存钱罐、心愿单的「本月可花」用它。
     *
     * 为什么不用 [monthStat]？整月口径会把本月还没到的日子的预算也算进来，
     * 月初看存钱罐就会显示成「一整个月的钱」，跟手上真正能动的钱对不上。
     * 只算到今天是唯一说得通的：钱是按天到账的。
     */
    val monthToDateStat: StateFlow<BudgetCalculator.MonthStat> =
        combine(transactions, rules) { txs, rs ->
            val today = LocalDate.now()
            BudgetCalculator.monthToDateStat(today, txs, rs)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetCalculator.monthToDateStat(LocalDate.now(), emptyList(), emptyList())
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

    // ---------- 心愿单 ----------

    fun addWish(name: String, priceCents: Long, note: String = "") {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || priceCents <= 0L) return
        viewModelScope.launch {
            wishDao.insert(
                WishEntity(
                    name = trimmed,
                    priceCents = priceCents,
                    note = note.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 实现心愿：往账本里记一笔「心愿」支出 → 存钱罐自动减少。
     *
     * 为什么走记账而不是单独扣一个数字？
     * 这样账本只有一套：明细里能看到「为了这个东西花了多少」，
     * 首页、月视图、存钱罐都会同步变，不会出现两套账对不上的情况。
     */
    fun achieveWish(item: WishEntity, from: String) {
        if (item.achieved) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val txId = transactionDao.insertReturningId(
                TransactionEntity(
                    date = LocalDate.now().toString(),
                    amountCents = item.priceCents,
                    category = "心愿",
                    note = item.name,
                    createdAt = now
                )
            )
            wishDao.update(
                item.copy(
                    achievedAt = now,
                    achievedFrom = from,
                    transactionId = txId
                )
            )
        }
    }

    /** 撤销实现：把那笔支出删掉，心愿回到未实现 */
    fun undoWish(item: WishEntity) {
        viewModelScope.launch {
            item.transactionId?.let { transactionDao.deleteById(it) }
            wishDao.update(item.copy(achievedAt = null, achievedFrom = null, transactionId = null))
        }
    }

    /** 删除心愿（如果已经实现，连带删掉那笔支出） */
    fun deleteWish(item: WishEntity) {
        viewModelScope.launch {
            item.transactionId?.let { transactionDao.deleteById(it) }
            wishDao.delete(item)
        }
    }
}
