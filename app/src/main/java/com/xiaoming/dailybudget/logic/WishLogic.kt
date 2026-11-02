package com.xiaoming.dailybudget.logic

/**
 * 心愿单的判断题：手里这些钱，够不够买这个东西。
 *
 * 单独抽成纯函数，是因为「够 / 不够」这一条判断贯穿整个心愿单界面，
 * 而且很容易写反（大于还是大于等于），放在这里可以单独测试。
 */
object WishLogic {

    /**
     * 能不能买。
     * 规则：存钱罐余额 ≥ 商品价格就算能买（刚好花光也算能买）。
     */
    fun canAfford(priceCents: Long, availableCents: Long): Boolean = availableCents >= priceCents

    /** 还差多少钱（够买时返回 0） */
    fun shortfallCents(priceCents: Long, availableCents: Long): Long =
        (priceCents - availableCents).coerceAtLeast(0L)

    /** 买完还剩多少（可能是负数，负数表示不够） */
    fun remainingAfterBuy(priceCents: Long, availableCents: Long): Long =
        availableCents - priceCents
}
