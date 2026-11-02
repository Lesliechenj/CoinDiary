package com.xiaoming.dailybudget

import com.xiaoming.dailybudget.logic.WishLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 心愿单的判断逻辑。
 * 这条判断很容易写成「大于」而不是「大于等于」，所以单独测一下。
 */
class WishLogicTest {

    /** 存钱罐刚好等于商品价格，也算买得起（花光为止） */
    @Test
    fun `余额刚好等于价格算买得起`() {
        assertTrue(WishLogic.canAfford(priceCents = 50000, availableCents = 50000))
    }

    /** 差一分钱就是买不起 */
    @Test
    fun `余额少一分就买不起`() {
        assertFalse(WishLogic.canAfford(priceCents = 50000, availableCents = 49999))
        assertEquals(1L, WishLogic.shortfallCents(priceCents = 50000, availableCents = 49999))
    }

    /** 买得起的时候，还差多少应该是 0，而不是负数 */
    @Test
    fun `买得起时不显示还差`() {
        assertEquals(0L, WishLogic.shortfallCents(priceCents = 30000, availableCents = 50000))
        assertEquals(20000L, WishLogic.remainingAfterBuy(priceCents = 30000, availableCents = 50000))
    }

    /** 买不起时，买完的余额是负数 —— 这就是「透支」 */
    @Test
    fun `买不起时买完余额为负`() {
        assertEquals(-18400L, WishLogic.remainingAfterBuy(priceCents = 49900, availableCents = 31500))
    }

    /** 结余为负（已经透支）时，任何正价商品都买不起 */
    @Test
    fun `存钱罐透支时买不起任何东西`() {
        assertFalse(WishLogic.canAfford(priceCents = 100, availableCents = -500))
        assertEquals(600L, WishLogic.shortfallCents(priceCents = 100, availableCents = -500))
    }
}
