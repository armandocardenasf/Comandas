package com.enverolab.comandas

import java.math.BigDecimal
import com.enverolab.comandas.domain.*
import org.junit.Assert.*
import org.junit.Test

class TenderAmountsTest {
    @Test
    fun convertsRemainingPesosIntoTheSelectedTenderWithoutUnderpaying() {
        val factor = BigDecimal("19.5")
        val suggested = TenderAmounts.suggested(BigDecimal("130"), factor)
        assertEquals(BigDecimal("6.67"), suggested)
        assertTrue(money(suggested * factor) >= BigDecimal("130"))
        assertEquals(
            BigDecimal("32.50"),
            TenderAmounts.suggested(BigDecimal("32.50"), BigDecimal.ONE),
        )
        assertEquals(BigDecimal("0.00"), TenderAmounts.suggested(BigDecimal.ZERO, factor))
    }

    @Test
    fun rejectsInvalidRatesAndBalances() {
        assertThrows(IllegalArgumentException::class.java) {
            TenderAmounts.suggested(BigDecimal.TEN, BigDecimal.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TenderAmounts.suggested(BigDecimal.TEN, BigDecimal("-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TenderAmounts.suggested(BigDecimal("-1"), BigDecimal.ONE)
        }
    }
}
