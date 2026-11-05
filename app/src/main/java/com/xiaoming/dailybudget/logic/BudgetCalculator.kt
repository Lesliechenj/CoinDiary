package com.xiaoming.dailybudget.logic

import com.xiaoming.dailybudget.data.BudgetRuleEntity
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DATE
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_DEFAULT
import com.xiaoming.dailybudget.data.BudgetRuleEntity.Companion.TYPE_WEEKDAY
import com.xiaoming.dailybudget.data.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * 核心算法：每日预算 + 结余滚存。
 *
 * 一句话公式：
 *   总剩余预算 = 从起始日到今天，每天的有效预算之和 − 这段时间的总支出
 *
 * 举例（日预算 30 元）：
 *   第1天花20 → 30×1−20 = 10
 *   第2天花25 → 30×2−45 = 15（前天省的10自动滚进来了）
 *   第3天花35 → 30×3−80 = 10（超支的5自动从池子里扣掉了）
 *
 * 某一天的「有效预算」按优先级取：
 *   指定日期的 > 周几的 > 默认日预算
 * 并且都要求「生效日期 ≤ 当天」，所以改预算不会影响已经过去的日子。
 */
object BudgetCalculator {

    /** 单日统计 */
    data class DayStat(
        val date: LocalDate,
        /** 这天分到的预算 */
        val budgetCents: Long,
        /** 这天花了多少 */
        val spentCents: Long
    ) {
        /** 这天结余（负数表示超支） */
        val balanceCents: Long get() = budgetCents - spentCents
    }

    /** 汇总结果 */
    data class Summary(
        val startDate: LocalDate,
        /** 今天分到的预算 */
        val todayBudgetCents: Long,
        /** 今天已花 */
        val todaySpentCents: Long,
        /** 昨天为止结转下来的余额（可正可负） */
        val carriedInCents: Long,
        /** 总剩余预算 */
        val totalRemainingCents: Long,
        /** 按天统计，日期倒序（最近的在前面） */
        val days: List<DayStat>
    ) {
        /**
         * 今天这一天自己的结余：今日预算 − 今日已花。
         * 不含之前结转，所以超支就是负数 —— 首页大字显示的是这个。
         */
        val todayBalanceCents: Long get() = todayBudgetCents - todaySpentCents

        /** 截至今天的累计结余（含之前结转），也就是预算这一侧攒下的钱 */
        val todayRemainingCents: Long get() = carriedInCents + todayBudgetCents - todaySpentCents

        /** 起始日到今天一共分到多少预算（不含攒钱） */
        val totalBudgetCents: Long get() = days.sumOf { it.budgetCents }

        /** 起始日到今天一共花掉多少（不含攒钱） */
        val totalSpentCents: Long get() = days.sumOf { it.spentCents }
    }

    /**
     * 算出某一天的有效预算。
     * 注意：如果这天没设任何预算，返回 0。
     */
    fun budgetForDay(date: LocalDate, rules: List<BudgetRuleEntity>): Long {
        val key = date.toString()

        // 1. 专门指定这一天的预算，优先级最高
        rules.filter { it.type == TYPE_DATE && it.specificDate == key }
            .maxByOrNull { it.createdAt }
            ?.let { return it.amountCents }

        // 2. 这个星期几的预算（取生效日期最晚的那条）
        val weekday = date.dayOfWeek.value // 1=周一 ... 7=周日
        rules.filter { it.type == TYPE_WEEKDAY && it.weekday == weekday && it.effectiveDate <= key }
            .maxWithOrNull(compareBy({ it.effectiveDate }, { it.createdAt }))
            ?.let { return it.amountCents }

        // 3. 兜底：默认日预算（同样取生效日期最晚的那条）
        return rules.filter { it.type == TYPE_DEFAULT && it.effectiveDate <= key }
            .maxWithOrNull(compareBy({ it.effectiveDate }, { it.createdAt }))
            ?.amountCents ?: 0L
    }

    /**
     * 从起始日算到今天，得到完整汇总。
     *
     * 起始日的确定方式：取「第一笔支出日期」和「第一条默认预算生效日」中较早的那个。
     */
    fun calculate(
        today: LocalDate,
        transactions: List<TransactionEntity>,
        rules: List<BudgetRuleEntity>
    ): Summary {
        // 按天汇总支出
        val spentByDate: Map<String, Long> = transactions
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        val firstTxDate = transactions.minByOrNull { it.date }?.date
        val firstRuleDate = rules
            .filter { it.type == TYPE_DEFAULT }
            .minByOrNull { it.effectiveDate }
            ?.effectiveDate

        var start: LocalDate = listOfNotNull(firstTxDate, firstRuleDate)
            .minOrNull()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today
        if (start.isAfter(today)) start = today

        var carried = 0L
        val days = ArrayList<DayStat>()

        var cursor = start
        while (!cursor.isAfter(today)) {
            val budget = budgetForDay(cursor, rules)
            val spent = spentByDate[cursor.toString()] ?: 0L
            days.add(DayStat(cursor, budget, spent))
            // 今天还包含在「剩余」里由调用方处理，之前的全部滚进 carried
            if (cursor != today) carried += budget - spent
            cursor = cursor.plusDays(1)
        }

        val todaySpent = spentByDate[today.toString()] ?: 0L
        val todayBudget = budgetForDay(today, rules)

        return Summary(
            startDate = start,
            todayBudgetCents = todayBudget,
            todaySpentCents = todaySpent,
            carriedInCents = carried,
            totalRemainingCents = carried + todayBudget - todaySpent,
            days = days.asReversed()
        )
    }

    /** 某个月的完整统计（月视图用） */
    data class MonthStat(
        val yearMonth: YearMonth,
        /** 这个月每一天，按日期正序（用 [until] 收窄时只到这里） */
        val days: List<DayStat>,
        /** 这段时间分到的预算合计 */
        val budgetCents: Long,
        /** 这段时间花掉的合计 */
        val spentCents: Long,
        /** 有消费的天数（没花钱的日子不计入日均） */
        val activeDays: Int
    ) {
        /** 这段时间的结余：只算本月自己的预算与支出，不含上月结转 */
        val balanceCents: Long get() = budgetCents - spentCents

        /** 统计到的最后一天；空列表时为 null */
        val lastDate: LocalDate? get() = days.lastOrNull()?.date

        /** 花得最多的那一天，没花钱则为 null */
        val busiestDay: DayStat?
            get() = days.filter { it.spentCents > 0L }.maxByOrNull { it.spentCents }

        /** 消费日均（只按有消费的天数平均，避免被空白天拉低） */
        val dailyAverageCents: Long
            get() = if (activeDays == 0) 0L else spentCents / activeDays
    }

    /**
     * 算出某个月每一天的预算与支出。
     *
     * 和 [calculate] 的区别：这里不滚存，就是把整月的「每天分到多少 / 花了多少」
     * 摊开来，用于日历格子。未来的日子也会算出一个预算值（按当前规则推演），但支出为 0。
     *
     * @param until 只统计到这一天为止（含）。传 null 就是整月。
     *              传今天，得到的就是「本月 1 号至今」—— 存钱罐用的就是这个口径：
     *              11 月的存钱罐只装 11 月 1 日到今天的钱，不会把后面 28 天还没到的
     *              预算提前算进来，也不会把 10 月的结余混进来。
     */
    fun monthStat(
        yearMonth: YearMonth,
        transactions: List<TransactionEntity>,
        rules: List<BudgetRuleEntity>,
        until: LocalDate? = null
    ): MonthStat {
        val keyPrefix = yearMonth.toString() // 形如 2026-10

        val spentByDate: Map<String, Long> = transactions
            .filter { it.date.startsWith(keyPrefix) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }

        // until 落在别的月份时：比这个月早 → 一天都不算；比这个月晚 → 等于整月
        val lastDay = when {
            until == null -> yearMonth.lengthOfMonth()
            YearMonth.from(until).isBefore(yearMonth) -> 0
            YearMonth.from(until).isAfter(yearMonth) -> yearMonth.lengthOfMonth()
            else -> minOf(until.dayOfMonth, yearMonth.lengthOfMonth())
        }

        val days = (1..lastDay).map { day ->
            val date = yearMonth.atDay(day)
            DayStat(
                date = date,
                budgetCents = budgetForDay(date, rules),
                spentCents = spentByDate[date.toString()] ?: 0L
            )
        }

        return MonthStat(
            yearMonth = yearMonth,
            days = days,
            budgetCents = days.sumOf { it.budgetCents },
            spentCents = days.sumOf { it.spentCents },
            activeDays = days.count { it.spentCents > 0L }
        )
    }

    /**
     * 「本月 1 号至今」的统计 —— 首页存钱罐和心愿单的「本月可花」都用这个。
     *
     * 一定要和整月口径区分开：
     *   整月   = 1 号到月底，把还没到的日子预算也提前加进去（数字虚高）
     *   至今   = 1 号到今天，只算真正到手的预算、真正花掉的钱
     * 用户看存钱罐是为了决定「今天还能不能买」，所以必须是至今。
     */
    fun monthToDateStat(
        today: LocalDate,
        transactions: List<TransactionEntity>,
        rules: List<BudgetRuleEntity>
    ): MonthStat = monthStat(YearMonth.from(today), transactions, rules, until = today)

    /** 某个月的分类合计，金额从高到低 */
    fun monthCategoryTotals(
        yearMonth: YearMonth,
        transactions: List<TransactionEntity>
    ): List<Pair<String, Long>> {
        val keyPrefix = yearMonth.toString()
        return transactions
            .filter { it.date.startsWith(keyPrefix) }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .toList()
            .sortedByDescending { it.second }
    }
}
