package com.xiaoming.dailybudget

import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DEFAULT
import com.xiaoming.dailybudget.data.SavingEntity
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.logic.PiggyBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 存钱罐测试。
 *
 * 要钉死的只有一件事：**攒钱只往存钱罐里加，不动任何一天的结余**。
 * 只要这条守住了，账本和存钱罐就永远不会打架。
 */
class PiggyBankTest {

    private fun tx(date: String, yuan: Long) = TransactionEntity(
        date = date,
        amountCents = yuan * 100,
        category = "餐饮",
        note = "",
        createdAt = 1L
    )

    private fun saving(date: String, yuan: Long, source: String = "少喝奶茶") = SavingEntity(
        source = source,
        amountCents = yuan * 100,
        date = date,
        createdAt = 1L
    )

    private fun defaultRule(yuan: Long, from: String) = BudgetRuleEntity(
        type = TYPE_DEFAULT,
        amountCents = yuan * 100,
        effectiveDate = from,
        createdAt = 1L
    )

    /** 没攒过钱：存钱罐就是预算结余，跟改造之前一模一样 */
    @Test
    fun `没有攒钱时等于预算结余`() {
        val rules = listOf(defaultRule(30, "2026-11-01"))
        val txs = listOf(tx("2026-11-01", 20), tx("2026-11-02", 25))
        val today = LocalDate.parse("2026-11-02")

        val stat = PiggyBank.monthToDate(today, txs, rules, emptyList())

        // 60 - 45 = 15
        assertEquals(1500L, stat.budgetBalanceCents)
        assertEquals(0L, stat.savedCents)
        assertEquals(1500L, stat.totalCents)
        assertFalse(stat.hasSaving)
    }

    /** 攒了 50：存钱罐 = 预算结余 15 + 50 = 65 */
    @Test
    fun `攒的钱加进本月存钱罐`() {
        val rules = listOf(defaultRule(30, "2026-11-01"))
        val txs = listOf(tx("2026-11-01", 20), tx("2026-11-02", 25))
        val savings = listOf(saving("2026-11-02", 50, source = "卖掉闲置"))
        val today = LocalDate.parse("2026-11-02")

        val stat = PiggyBank.monthToDate(today, txs, rules, savings)

        assertEquals(1500L, stat.budgetBalanceCents)
        assertEquals(5000L, stat.savedCents)
        assertEquals(6500L, stat.totalCents)
        assertTrue(stat.hasSaving)
    }

    /** 上月攒的钱不该混进本月的存钱罐 */
    @Test
    fun `上月的攒钱不计入本月`() {
        val rules = listOf(defaultRule(30, "2026-10-01"))
        val savings = listOf(
            saving("2026-10-28", 100), // 上月，不算
            saving("2026-11-01", 20)   // 本月，算
        )
        val today = LocalDate.parse("2026-11-02")

        val stat = PiggyBank.monthToDate(today, emptyList(), rules, savings)

        assertEquals(2000L, stat.savedCents)
    }

    /** 记了未来日期的攒钱（补记/改日期出错）不该提前进存钱罐 */
    @Test
    fun `未来的攒钱不计入`() {
        val rules = listOf(defaultRule(30, "2026-11-01"))
        val savings = listOf(
            saving("2026-11-02", 20),
            saving("2026-11-20", 999)
        )
        val today = LocalDate.parse("2026-11-02")

        val stat = PiggyBank.monthToDate(today, emptyList(), rules, savings)

        assertEquals(2000L, stat.savedCents)
    }

    /** 总存钱罐跨月累计：预算结余 + 所有攒钱 */
    @Test
    fun `总存钱罐累计所有攒钱`() {
        val rules = listOf(defaultRule(30, "2026-10-30"))
        val txs = listOf(
            tx("2026-10-30", 20),  // 结余 +10
            tx("2026-11-01", 40)   // 结余 -10
        )
        val savings = listOf(
            saving("2026-10-05", 100), // 比记账第一天还早，也算进总存钱罐
            saving("2026-11-02", 50)
        )
        val today = LocalDate.parse("2026-11-02")

        val stat = PiggyBank.total(today, txs, rules, savings)

        // 10-30 到 11-02 共 4 天，预算 4×30 = 120，花掉 60 → 结余 60
        assertEquals(6000L, stat.budgetBalanceCents)
        assertEquals(15000L, stat.savedCents)
        assertEquals(21000L, stat.totalCents)
    }

    /**
     * 最关键的一条：攒钱不进支出表，所以当天的结余一分不变。
     * 昨天预算 30 花 30（结余 0），今天攒了 100 —— 今日结余仍是 0，只有存钱罐 +100。
     */
    @Test
    fun `攒钱不影响当日结余`() {
        val rules = listOf(defaultRule(30, "2026-11-01"))
        val txs = listOf(tx("2026-11-01", 30))
        val savings = listOf(saving("2026-11-01", 100))
        val today = LocalDate.parse("2026-11-01")

        val summary = BudgetCalculator.calculate(today, txs, rules)
        val stat = PiggyBank.monthToDate(today, txs, rules, savings)

        // 预算 30 − 已花 30 = 0，攒的 100 完全没进来
        assertEquals(0L, summary.todayBalanceCents)
        assertEquals(0L, stat.budgetBalanceCents)
        // 但存钱罐里多了 100
        assertEquals(10000L, stat.totalCents)
    }

    /** 攒钱能让透支的存钱罐回正 */
    @Test
    fun `攒钱可以补上超支`() {
        val rules = listOf(defaultRule(30, "2026-11-01"))
        val txs = listOf(tx("2026-11-01", 80)) // 超支 50
        val savings = listOf(saving("2026-11-01", 60))
        val today = LocalDate.parse("2026-11-01")

        val stat = PiggyBank.monthToDate(today, txs, rules, savings)

        assertEquals(-5000L, stat.budgetBalanceCents)
        assertEquals(1000L, stat.totalCents)
    }

    /** 区间边界：起点当天要算进来，前一天不算 */
    @Test
    fun `区间含首尾两天`() {
        val savings = listOf(
            saving("2026-10-31", 5),
            saving("2026-11-01", 10),
            saving("2026-11-05", 20)
        )
        val total = PiggyBank.savedBetween(
            savings,
            from = LocalDate.parse("2026-11-01"),
            until = LocalDate.parse("2026-11-05")
        )

        assertEquals(3000L, total)
    }
}
