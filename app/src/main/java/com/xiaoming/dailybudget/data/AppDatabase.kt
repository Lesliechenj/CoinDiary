package com.xiaoming.dailybudget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地数据库。数据文件存在手机内部存储，不上传任何服务器。
 *
 * 版本历史：
 * 1 → 支出记录 + 预算规则
 * 2 → 增加心愿单表（wish）
 */
@Database(
    entities = [TransactionEntity::class, BudgetRuleEntity::class, WishEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun budgetRuleDao(): BudgetRuleDao
    abstract fun wishDao(): WishDao

    companion object {
        /**
         * 1 → 2：只新增一张表，不动老数据。
         * 这里刻意写手写 SQL 而不是清库重建 —— 升级不能把已经记的账弄丢。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wish` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `priceCents` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `achievedAt` INTEGER,
                        `achievedFrom` TEXT,
                        `transactionId` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_budget.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
