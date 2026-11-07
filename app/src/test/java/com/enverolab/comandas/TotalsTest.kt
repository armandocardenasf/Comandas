package com.enverolab.comandas

import java.math.BigDecimal
import com.enverolab.comandas.data.Passwords
import com.enverolab.comandas.domain.*
import org.junit.Assert.*
import org.junit.Test

class TotalsTest {
    private fun n(s: String) = BigDecimal(s)

    private fun line(price: String, qty: String = "1", discount: String = "0") =
        Line(
            productId = 1,
            code = "1",
            name = "Prueba",
            price = n(price),
            quantity = n(qty),
            discountAmount = n(discount),
        )

    @Test
    fun itemAndGeneralDiscounts() {
        assertEquals(n("171.00"), Totals.total(listOf(line("100", "2", "10")), "%", n("10")))
    }

    @Test
    fun fixedDiscount() {
        assertEquals(n("175.00"), Totals.total(listOf(line("100", "2")), "$", n("25")))
    }

    @Test
    fun clampFixedDiscount() {
        assertEquals(n("0.00"), Totals.total(listOf(line("100")), "$", n("150")))
    }

    @Test
    fun clampPercent() {
        assertEquals(n("0.00"), Totals.total(listOf(line("100")), "%", n("200")))
    }

    @Test
    fun halfUpRounding() {
        assertEquals(n("0.11"), money(n("0.105")))
    }

    @Test
    fun fractionalQuantity() {
        assertEquals(n("25.00"), Totals.total(listOf(line("100", "0.25")), "%", n("0")))
    }

    @Test
    fun exactEqualSplit() {
        val result = Totals.split(n("100"), 3)
        assertEquals(listOf(n("33.34"), n("33.33"), n("33.33")), result)
        assertEquals(n("100.00"), result.reduce(BigDecimal::add))
    }

    @Test
    fun pennyAcrossPeople() {
        assertEquals(listOf(n("0.01"), n("0.00"), n("0.00")), Totals.split(n("0.01"), 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPeople() {
        Totals.split(n("100"), 0)
    }

    @Test
    fun mixedCurrencyAndChange() {
        val p =
            listOf(
                Payment(1, "Efectivo", n("50"), n("1")),
                Payment(2, "Dolares", n("10"), n("19.5")),
            )
        assertEquals(n("45.00"), Totals.change(n("200"), p))
    }

    @Test
    fun tipsDoNotPayOrder() {
        val p = Payment(1, "Efectivo", n("100"), n("1"), n("15"))
        assertEquals(n("100.00"), p.pesos)
        assertEquals(n("15.00"), p.tipPesos)
    }

    @Test
    fun usdTipConverted() {
        assertEquals(n("39.00"), Payment(2, "Dolares", n("10"), n("19.5"), n("2"), "USD").tipPesos)
    }

    @Test
    fun legacyPassword() {
        assertTrue(Passwords.verify("1234", "1234"))
        assertFalse(Passwords.verify("x", "1234"))
    }

    @Test
    fun hashedPassword() {
        val hash = Passwords.hash("secreto")
        assertTrue(Passwords.verify("secreto", hash))
        assertFalse(Passwords.verify("otro", hash))
        assertNotEquals(hash, Passwords.hash("secreto"))
    }

    @org.junit.Test
    fun quantityButtonsKeepDiscountsProportionalWithFractionalAmounts() {
        val line =
            Line(
                productId = 1,
                code = "JR",
                name = "JR",
                quantity = BigDecimal("3"),
                price = BigDecimal("130"),
                discountAmount = BigDecimal("10"),
            )
        val more = line.withQuantity(BigDecimal("4"))
        org.junit.Assert.assertEquals(BigDecimal("13.33"), more.discountAmount)
        org.junit.Assert.assertEquals(
            BigDecimal("10.00"),
            more.withQuantity(BigDecimal("3")).discountAmount,
        )
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            line.withQuantity(BigDecimal.ZERO)
        }
    }
}
