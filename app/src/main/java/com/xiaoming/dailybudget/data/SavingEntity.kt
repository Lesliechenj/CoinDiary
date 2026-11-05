package com.xiaoming.dailybudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一笔「攒钱」记录：通过什么方式攒下了多少钱。
 *
 * 为什么不直接记成一笔负的支出？
 * 因为支出表只有一个作用 —— 从当天预算里扣钱。攒钱是反过来的：
 * 它不占预算，也不该让某一天的结余变好看，它只是往存钱罐里放钱。
 * 两件事混在一张表里，日结余、月视图、分类占比全都会算歪。
 *
 * 所以：攒钱单独一张表，只参与存钱罐的加法。
 */
@Entity(tableName = "saving")
data class SavingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 攒钱方式，比如「少喝奶茶」「闲鱼卖旧手机」 */
    val source: String,
    /** 攒了多少（分） */
    val amountCents: Long,
    /** 记在哪一天，形如 2026-11-05 */
    val date: String,
    /** 记下这笔的时间，同一日期内用来排序 */
    val createdAt: Long
)
