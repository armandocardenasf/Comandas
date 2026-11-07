package com.enverolab.comandas

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import com.enverolab.comandas.data.PosDatabase
import com.enverolab.comandas.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
open class NavigationPaymentsInventoryTest : InterfaceTestBase() {
    @Test
    fun sharedTablesQuickLinksUsdPaymentAndInventoryFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PosDatabase(context)
        val repo = com.enverolab.comandas.data.PosRepository(db)
        repo.login("admin", "1234")
        val table =
            repo.saveCatalog(
                "mesas",
                null,
                mapOf(
                    "numero" to 1,
                    "nombre" to "Mesa compartida",
                    "disponibilidad" to "Disponible",
                ),
            )
        val draft =
            com.enverolab.comandas.domain.Draft(
                tableId = table,
                persons = 1,
                clientId = 1,
                lines =
                    listOf(
                        com.enverolab.comandas.domain.Line(
                            productId = 1,
                            variantId = 1,
                            code = "1",
                            name = "JR (Copa)",
                            price = java.math.BigDecimal("130"),
                        )
                    ),
            )
        val first = repo.saveOrder(draft)
        val second = repo.saveOrder(draft)
        fun chooseMethod(label: String) {
            compose
                .onNode(hasText("Método:", substring = true) and hasClickAction())
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            click(label)
        }
        fun assertAmount(value: String) {
            compose
                .onNodeWithTag("payment-amount")
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                        androidx.compose.ui.text.AnnotatedString(value),
                    )
                )
        }
        fun scrollClick(label: String) {
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo()
            click(label)
        }
        try {
            login()
            clickTag("quick-orders")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("order-$first").fetchSemanticsNodes().isNotEmpty()
            }
            clickTag("order-$first")
            if (compose.onAllNodesWithTag("pane-second").fetchSemanticsNodes().isNotEmpty()) {
                compose.waitUntil(15000) {
                    compose
                        .onNodeWithTag("pane-second")
                        .fetchSemanticsNode()
                        .config[androidx.compose.ui.semantics.SemanticsProperties.Selected]
                }
                firstPane()
                clickTag("order-$first")
                compose.waitUntil(15000) {
                    compose
                        .onNodeWithTag("pane-second")
                        .fetchSemanticsNode()
                        .config[androidx.compose.ui.semantics.SemanticsProperties.Selected]
                }
            }
            detailScroll("pay-order")
            clickTag("pay-order")
            // No tocar Resumen/Pago: debe entrar directamente al panel Pago.
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Agregar pago / asignar artículos")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            if (compose.onAllNodesWithTag("pane-second").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("pane-second").assertIsSelected()
            scrollClick("Agregar pago / asignar artículos")
            assertAmount("130.00")
            chooseMethod("Dolares (factor 19.5)")
            assertAmount("6.67")
            clickTag("clear-payment-amount")
            assertAmount("")
            click("Agregar")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("floating-notice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText("Capture el monto recibido").onLast().assertExists()
            compose.onNodeWithContentDescription("Cerrar aviso").performSemanticsAction(
                SemanticsActions.OnClick
            ) {
                it()
            }
            compose.onNodeWithTag("payment-amount").performScrollTo().performTextInput("5")
            click("Agregar")
            compose.onAllNodesWithText("($97.50 MXN)", substring = true).onFirst().assertExists()
            snapshot("pagos-usd")
            scrollClick("Agregar pago / asignar artículos")
            assertAmount("32.50")
            chooseMethod("Dolares (factor 19.5)")
            assertAmount("1.67")
            chooseMethod("Efectivo (factor 1.0)")
            assertAmount("32.50")
            click("Agregar")
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasText("Confirmar cobro") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            scrollClick("Confirmar cobro")
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Finalizar").fetchSemanticsNodes().isNotEmpty()
            }
            click("Finalizar")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("quick-new-order").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Ocupada", repo.catalog("mesas").first().text("disponibilidad"))
            assertEquals("abierto", repo.order(second).text("status"))
            clickTag("quick-new-order")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("product-search").fetchSemanticsNodes().isNotEmpty()
            }
            navigate("Inventario")
            compose.onAllNodesWithTag("inventory-movements-dialog").assertCountEquals(0)
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("inventory-product-1").fetchSemanticsNodes().isNotEmpty()
            }
            clickTag("inventory-product-1")
            val before = repo.inventoryProducts().first { it.id() == 1L }.decimal("stock")
            scrollClick("Entrada")
            compose.onNodeWithTag("inventory-quantity").performTextInput("2")
            click("Guardar")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("inventory-quantity").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(
                0,
                (before + java.math.BigDecimal("2")).compareTo(
                    repo.inventoryProducts().first { it.id() == 1L }.decimal("stock")
                ),
            )
            val expectedStock=(before+java.math.BigDecimal("2")).stripTrailingZeros().toPlainString()
            compose.waitUntil(15000) {
                compose.onAllNodes(hasTestTag("inventory-product-1") and hasText(expectedStock)).fetchSemanticsNodes().isNotEmpty()
            }
            snapshot("inventario-productos")
            clickTag("inventory-movements")
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithTag("inventory-movements-dialog")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onNodeWithText(java.time.LocalDate.now().minusMonths(1).toString())
                .assertExists()
            compose.onNodeWithText("Precio de venta actual MXN").assertExists()
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasText("Exportar XLSX") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            val old = java.time.LocalDate.now().minusYears(1).toString()
            compose.onNodeWithText("Desde").performTextReplacement(old)
            compose.onNodeWithText("Hasta").performTextReplacement(old)
            click("Aplicar rango")
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Sin movimientos en el periodo seleccionado")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            click("Cerrar ventana")
            val vm =
                androidx.lifecycle
                    .ViewModelProvider(compose.activity)[
                        com.enverolab.comandas.ui.PosViewModel::class.java]
            compose.runOnUiThread { vm.message("Aviso temporal de prueba") }
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("floating-notice").fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(5500)
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("floating-notice").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            db.close()
        }
    }
}
