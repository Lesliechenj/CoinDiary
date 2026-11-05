package com.xiaoming.dailybudget.logic

import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.SavingEntity
import com.xiaoming.dailybudget.data.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * 存钱罐。
 *
 * 一句话公式：
 *   存钱罐 = 预算结余 + 攒进去的钱
 *
 * 这两块钱来路不同，必须分开算再相加：
 *   预算结余 —— 每天少花一点，自然留下来的（由 [BudgetCalculator] 管）
 *   攒的钱   —— 主动省下或额外赚到，手动记进来的（[SavingEntity]）
 * 合在一起才是「现在手上能动用多少」，也正是心愿单判断买不买得起的那个数。
 *
 * 攒钱不进支出表，所以它不会让任何一天的结余变好看，
 * 也不会影响月视图、分类占比 —— 那几处看的是「花在哪了」，不是「攒了多少」。
 */
object PiggyBank {

    /**
     * 算某段时间内攒了多少钱，含首尾两天。
     *
     * @param from 起点，传 null 表示不限（从最早的一笔算起）
     * @param until 终点，晚于这一天的记录不计入
     */
    fun savedBetween(
        savings: List<SavingEntity>,
        from: LocalDate?,
        until: LocalDate
    ): Long = savings.sumOf { item ->
        val date = runCatching { LocalDate.parse(item.date) }.getOrNull()
        when {
            date == null -> 0L
            date.isAfter(until) -> 0L
            from != null && date.isBefore(from) -> 0L
            else -> item.amountCents
        }
    }

    /**
     * 一个存钱罐的三个来源。
     * 拆成三个数而不是只留一个总数，是为了界面上能说清「这钱哪来的」。
     */
    data class Stat(
        /** 这段时间分到的预算 */
        val budgetCents: Long,
        /** 这段时间花掉的 */
        val spentCents: Long,
        /** 这段时间攒进去的 */
        val savedCents: Long
    ) {
        /** 预算结余：预算 − 已花，超支时为负 */
        val budgetBalanceCents: Long get() = budgetCents - spentCents

        /** 存钱罐总额：预算结余 + 攒的钱 */
        val totalCents: Long get() = budgetBalanceCents + savedCents

        /** 攒过钱没有（界面上决定要不要多显示一行「其中攒了」） */
        val hasSaving: Boolean get() = savedCents != 0L
    }

    /**
     * 本月 1 号到今天的存钱罐 —— 首页那张卡显示的就是它。
     *
     * 用「至今」而不是「整月」：钱是按天到账的，本月还没到的日子不该提前算进来。
     */
    fun monthToDate(
        today: LocalDate,
        transactions: List<TransactionEntity>,
        rules: List<BudgetRuleEntity>,
        savings: List<SavingEntity>
    ): Stat {
        val month = BudgetCalculator.monthToDateStat(today, transactions, rules)
        return Stat(
            budgetCents = month.budgetCents,
            spentCents = month.spentCents,
            savedCents = savedBetween(savings, YearMonth.from(today).atDay(1), today)
        )
    }

    /**
     * 从记账第一天到今天的存钱罐 —— 心愿单的「总存钱罐」用它。
     *
     * 起始日由 [BudgetCalculator.calculate] 决定（第一笔支出或第一天设的预算），
     * 攒钱记录不参与起点的判断：攒的钱是纯加法，跟预算从哪天开始算没有关系。
     */
    fun total(
        today: LocalDate,
        transactions: List<TransactionEntity>,
        rules: List<BudgetRuleEntity>,
        savings: List<SavingEntity>
    ): Stat {
        val summary = BudgetCalculator.calculate(today, transactions, rules)
        return Stat(
            budgetCents = summary.totalBudgetCents,
            spentCents = summary.totalSpentCents,
            savedCents = savedBetween(savings, null, today)
        )
    }
}
