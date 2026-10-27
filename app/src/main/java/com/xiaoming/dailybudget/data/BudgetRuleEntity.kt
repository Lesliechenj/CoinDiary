package com.xiaoming.dailybudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预算规则。整个 App 的「每日预算 + 滚存」都建立在这张表上。
 *
 * 一共三种类型，优先级从高到低：
 * 1. date     只针对某一天（specificDate），优先级最高
 * 2. weekday  每个星期几（weekday: 1=周一 ... 7=周日）
 * 3. default  默认日预算
 *
 * effectiveDate 是「从哪天起生效」。
 * 这样改预算时只追加一条新规则，历史日子的预算不会被改掉，
 * 正好满足「从改的那天起按新金额算」。
 */
@Entity(tableName = "budget_rules")
data class BudgetRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 规则类型：default / weekday / date */
    val type: String,
    /** 预算金额（分） */
    val amountCents: Long,
    /** 周几：1=周一 ... 7=周日，仅 weekday 类型使用 */
    val weekday: Int? = null,
    /** 指定的某一天，仅 date 类型使用 */
    val specificDate: String? = null,
    /** 生效日期 yyyy-MM-dd */
    val effectiveDate: String,
    /** 创建时间戳，用于同一天多次修改时判断哪条更新 */
    val createdAt: Long
) {
    companion object {
        const val TYPE_DEFAULT = "default"
        const val TYPE_WEEKDAY = "weekday"
        const val TYPE_DATE = "date"
    }
}
