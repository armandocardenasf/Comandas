package com.enverolab.comandas

import com.enverolab.comandas.domain.Ticket
import org.junit.Assert.*
import org.junit.Test

class TicketTest {
    @Test
    fun alignsEveryAmountAtConfiguredRightEdge() {
        for (width in listOf(32, 42, 48)) {
            val raw =
                listOf(
                        Ticket.center("COMANDAS"),
                        "---",
                        Ticket.pair("Subtotal", "$123.00"),
                        Ticket.pair(
                            "Una descripción de producto muy larga con presentación botella",
                            "$1,200.50",
                        ),
                        Ticket.pair("Cambio", "$0.00"),
                    )
                    .joinToString("\n")
            val lines = Ticket.render(raw, width).lines()
            assertTrue(lines.all { it.length <= width })
            lines
                .filter { '$' in it }
                .forEach {
                    assertEquals(width, it.length)
                    assertFalse(it.endsWith(" "))
                }
            assertTrue(lines[0].startsWith(" "))
        }
    }

    @Test
    fun neverTruncatesLongAmountsOrNames() {
        val text =
            Ticket.render(
                Ticket.pair(
                    "ProductoSinEspaciosMuyLargoEnLaDescripcion",
                    "$12345678901234567890123456789012345.00",
                ),
                32,
            )
        assertTrue(text.lines().all { it.length <= 32 })
        assertTrue(
            text
                .replace("\n", "")
                .replace(" ", "")
                .contains("$12345678901234567890123456789012345.00")
        )
    }
}
