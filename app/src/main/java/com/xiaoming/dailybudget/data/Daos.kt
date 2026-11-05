package com.xiaoming.dailybudget.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(item: TransactionEntity)

    /** 插入并返回新记录的 id（心愿单实现时要记下来，方便撤销） */
    @Insert
    suspend fun insertReturningId(item: TransactionEntity): Long

    @Delete
    suspend fun delete(item: TransactionEntity)

    /** 按 id 删除（撤销心愿时只知道交易 id） */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

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

@Dao
interface SavingDao {

    @Insert
    suspend fun insert(item: SavingEntity)

    @Delete
    suspend fun delete(item: SavingEntity)

    /** 按日期倒序返回全部攒钱记录 */
    @Query("SELECT * FROM saving ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<SavingEntity>>
}

@Dao
interface WishDao {

    @Insert
    suspend fun insert(item: WishEntity): Long

    @Update
    suspend fun update(item: WishEntity)

    @Delete
    suspend fun delete(item: WishEntity)

    /**
     * 未实现的排前面，同一组里新加的排前面。
     * SQLite 里 `achievedAt IS NOT NULL` 得到 0/1，升序即「未实现优先」。
     */
    @Query("SELECT * FROM wish ORDER BY (achievedAt IS NOT NULL) ASC, createdAt DESC")
    fun observeAll(): Flow<List<WishEntity>>
}
