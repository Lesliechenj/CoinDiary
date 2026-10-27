package com.xiaoming.dailybudget

import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DATE
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DEFAULT
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_WEEKDAY
import com.xiaoming.dailybudget.data.TransactionEntity
import com.xiaoming.dailybudget.logic.BudgetCalculator
import com.xiaoming.dailybudget.util.centsToCompactYuan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 核心算法测试。第一个用例就是你（小明）举的原始例子，
 * 跑通就说明滚存逻辑没错。
 */
class BudgetCalculatorTest {

    private fun tx(date: String, yuan: Long) = TransactionEntity(
        date = date,
        amountCents = yuan * 100,
        category = "餐饮",
        note = "",
        createdAt = System.currentTimeMillis()
    )

    private fun defaultRule(yuan: Long, from: String) = BudgetRuleEntity(
        type = TYPE_DEFAULT,
        amountCents = yuan * 100,
        effectiveDate = from,
        createdAt = 1L
    )

    /** 小明的例子：日预算30，第1天花20，第2天花25，第3天花35 → 总剩余应该是 10 元 */
    @Test
    fun `小明的三天例子`() {
        val rules = listOf(defaultRule(30, "2026-10-01"))
        val transactions = listOf(
            tx("2026-10-01", 20),
            tx("2026-10-02", 25),
            tx("2026-10-03", 35)
        )

        val summary = BudgetCalculator.calculate(
            today = LocalDate.parse("2026-10-03"),
            transactions = transactions,
            rules = rules
        )

        // 90 - 80 = 10 元
        assertEquals(1000L, summary.totalRemainingCents)
        // 今天预算 30 元
        assertEquals(3000L, summary.todayBudgetCents)
        // 今天花了 35 元
        assertEquals(3500L, summary.todaySpentCents)
        // 昨天为止结转：第1天 +10、第2天 +5 = 15 元
        assertEquals(1500L, summary.carriedInCents)
        // 今天还能花：15 + 30 - 35 = 10 元
        assertEquals(1000L, summary.todayRemainingCents)
        // 今天这一天自己的结余：30 - 35 = -5 元（当天超支 5 元）
        assertEquals(-500L, summary.todayBalanceCents)
    }

    /**
     * 首页两个数字必须分别对应两种口径，不能混：
     * 大字「今日结余」= 只有今天这一天；「存钱罐」= 截至今天的累计。
     */
    @Test
    fun `今日结余只看当天 存钱罐看累计`() {
        val rules = listOf(defaultRule(30, "2026-10-01"))
        val transactions = listOf(
            tx("2026-10-01", 20),
            tx("2026-10-02", 25),
            tx("2026-10-03", 35)
        )

        // 第 2 天：当天 30-25=5，累计 30×2-45=15
        val day2 = BudgetCalculator.calculate(
            today = LocalDate.parse("2026-10-02"),
            transactions = transactions,
            rules = rules
        )
        assertEquals(500L, day2.todayBalanceCents)
        assertEquals(1500L, day2.todayRemainingCents)

        // 第 3 天：当天超支 -5，累计仍是 10
        val day3 = BudgetCalculator.calculate(
            today = LocalDate.parse("2026-10-03"),
            transactions = transactions,
            rules = rules
        )
        assertEquals(-500L, day3.todayBalanceCents)
        assertEquals(1000L, day3.todayRemainingCents)
        // 两个口径的关系：累计 = 昨天为止 + 今天这一天
        assertEquals(day3.carriedInCents + day3.todayBalanceCents, day3.totalRemainingCents)
    }

    /** 改预算：从改的那天起生效，过去的日子仍按旧金额 */
    @Test
    fun `改预算只影响之后的日期`() {
        val rules = listOf(
            defaultRule(30, "2026-10-01"),
            defaultRule(40, "2026-10-03")
        )

        // 10-03 这天的预算是新的 40 元
        assertEquals(
            4000L,
            BudgetCalculator.budgetForDay(LocalDate.parse("2026-10-03"), rules)
        )
        // 10-02 仍然按 30 元算
        assertEquals(
            3000L,
            BudgetCalculator.budgetForDay(LocalDate.parse("2026-10-02"), rules)
        )
        // 三天总配额 = 30 + 30 + 40 = 100 元
        val summary = BudgetCalculator.calculate(
            today = LocalDate.parse("2026-10-03"),
            transactions = emptyList(),
            rules = rules
        )
        assertEquals(10000L, summary.totalRemainingCents)
    }

    /** 优先级：指定某天 > 周几 > 默认 */
    @Test
    fun `预算优先级`() {
        val rules = listOf(
            defaultRule(30, "2026-09-01"),
            BudgetRuleEntity(
                type = TYPE_WEEKDAY,
                amountCents = 5000,
                weekday = 6, // 周六
                effectiveDate = "2026-09-01",
                createdAt = 2L
            ),
            BudgetRuleEntity(
                type = TYPE_DATE,
                amountCents = 8000,
                specificDate = "2026-10-03",
                effectiveDate = "2026-10-03",
                createdAt = 3L
            )
        )

        // 2026-10-03 是周六，同时被单独指定了 → 取 80 元
        assertEquals(
            8000L,
            BudgetCalculator.budgetForDay(LocalDate.parse("2026-10-03"), rules)
        )
        // 2026-10-10 也是周六，但没有单独指定 → 取周几的 50 元
        assertEquals(
            5000L,
            BudgetCalculator.budgetForDay(LocalDate.parse("2026-10-10"), rules)
        )
        // 2026-10-07 是周三，没有周几规则 → 取默认 30 元
        assertEquals(
            3000L,
            BudgetCalculator.budgetForDay(LocalDate.parse("2026-10-07"), rules)
        )
    }

    /** 没设任何预算时不应该崩，返回 0 */
    @Test
    fun `没有预算规则时不崩溃`() {
        val summary = BudgetCalculator.calculate(
            today = LocalDate.parse("2026-10-03"),
            transactions = listOf(tx("2026-10-03", 12)),
            rules = emptyList()
        )
        assertEquals(0L, summary.todayBudgetCents)
        assertEquals(-1200L, summary.totalRemainingCents)
    }

    /** 月视图：整月摊开算，31 天 × 30 元 = 930 元配额，花了 80 元 */
    @Test
    fun `月视图统计整月`() {
        val rules = listOf(defaultRule(30, "2026-10-01"))
        val transactions = listOf(
            tx("2026-10-01", 20),
            tx("2026-10-02", 25),
            tx("2026-10-03", 35)
        )

        val stat = BudgetCalculator.monthStat(
            yearMonth = YearMonth.of(2026, 10),
            transactions = transactions,
            rules = rules
        )

        assertEquals(31, stat.days.size)
        assertEquals(31 * 3000L, stat.budgetCents)
        assertEquals(8000L, stat.spentCents)
        // 930 - 80 = 850 元
        assertEquals(85000L, stat.balanceCents)
        // 只有 3 天有消费
        assertEquals(3, stat.activeDays)
        // 花得最多的是 10-03 的 35 元
        assertEquals(LocalDate.parse("2026-10-03"), stat.busiestDay?.date)
        assertEquals(3500L, stat.busiestDay?.spentCents)
        // 消费日均 = 80 / 3 = 26.66 元
        assertEquals(2666L, stat.dailyAverageCents)
    }

    /** 月视图不能串月：9 月的账不能算进 10 月 */
    @Test
    fun `月视图不串月`() {
        val rules = listOf(defaultRule(30, "2026-09-01"))
        val transactions = listOf(tx("2026-09-30", 10), tx("2026-10-01", 20))

        val sep = BudgetCalculator.monthStat(YearMonth.of(2026, 9), transactions, rules)
        val oct = BudgetCalculator.monthStat(YearMonth.of(2026, 10), transactions, rules)

        assertEquals(1000L, sep.spentCents)
        assertEquals(2000L, oct.spentCents)
        assertEquals(30, sep.days.size)
        assertEquals(31, oct.days.size)

        // 10 月只有一笔 20 元的餐饮
        assertEquals(
            listOf("餐饮" to 2000L),
            BudgetCalculator.monthCategoryTotals(YearMonth.of(2026, 10), transactions)
        )
    }

    /** 空月份：没有消费时日均不能除零，最花的一天为 null */
    @Test
    fun `空月份不除零`() {
        val stat = BudgetCalculator.monthStat(
            yearMonth = YearMonth.of(2026, 11),
            transactions = emptyList(),
            rules = listOf(defaultRule(30, "2026-10-01"))
        )
        assertEquals(0L, stat.spentCents)
        assertEquals(0, stat.activeDays)
        assertEquals(0L, stat.dailyAverageCents)
        assertEquals(null, stat.busiestDay)
    }

    /** 日历格子里的金额要短到塞得进 40dp */
    @Test
    fun `日历格子金额写法`() {
        assertEquals("20", centsToCompactYuan(2000))
        assertEquals("25", centsToCompactYuan(2500))
        assertEquals("25.6", centsToCompactYuan(2560))
        assertEquals("999.9", centsToCompactYuan(99990))
        assertEquals("1.2k", centsToCompactYuan(123400))
        assertEquals("10k", centsToCompactYuan(1_000_000))
    }
}
