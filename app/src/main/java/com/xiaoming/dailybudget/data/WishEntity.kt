package com.xiaoming.dailybudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心愿单里的一个商品。
 *
 * 设计要点：心愿本身不参与预算计算。
 * 只有点「已实现心愿」时，才会自动往账本里记一笔支出（分类「心愿」），
 * 存钱罐因此自然减少 —— 这样账目只有一套，明细里也能看到这笔钱花在哪了。
 */
@Entity(tableName = "wish")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 想买的东西，比如「机械键盘」 */
    val name: String,
    /** 价格（分） */
    val priceCents: Long,
    /** 备注，可为空 */
    val note: String = "",
    /** 加入心愿单的时间 */
    val createdAt: Long,
    /** 实现时间；null 表示还没买 */
    val achievedAt: Long? = null,
    /** 实现时参考的是哪个存钱罐：MONTH（当月）或 TOTAL（总计） */
    val achievedFrom: String? = null,
    /** 实现时自动记的那笔支出的 id，撤销/删除时要连带删掉 */
    val transactionId: Long? = null
) {
    /** 是否已经买到了 */
    val achieved: Boolean get() = achievedAt != null

    companion object {
        /** 参考「当月结余」判断能不能买 */
        const val FROM_MONTH = "MONTH"

        /** 参考「总存钱罐」判断能不能买 */
        const val FROM_TOTAL = "TOTAL"
    }
}
