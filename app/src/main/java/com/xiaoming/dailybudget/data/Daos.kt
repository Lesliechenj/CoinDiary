package com.xiaoming.dailybudget.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(item: TransactionEntity)

    @Delete
    suspend fun delete(item: TransactionEntity)

    /** 按日期倒序返回全部支出记录，数据一变界面会自动刷新 */
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
}

@Dao
interface BudgetRuleDao {

    @Insert
    suspend fun insert(item: BudgetRuleEntity)

    @Delete
    suspend fun delete(item: BudgetRuleEntity)

    @Query("SELECT * FROM budget_rules")
    fun observeAll(): Flow<List<BudgetRuleEntity>>
}
