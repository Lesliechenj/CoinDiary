package com.xiaoming.dailybudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一笔支出记录。
 *
 * 金额统一用「分」存（30.50 元 = 3050 分），
 * 因为小数用 Float/Double 存会有精度误差，算钱必须用整数。
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 消费日期，格式 yyyy-MM-dd */
    val date: String,
    /** 金额（分） */
    val amountCents: Long,
    /** 分类：餐饮/交通/购物... */
    val category: String,
    /** 备注，可为空字符串 */
    val note: String,
    /** 创建时间戳，用于同一天内排序 */
    val createdAt: Long
)
