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
open class InterfaceTest : InterfaceTestBase() {
    @Test
    fun loginAndNavigate() {
        login()
        compose.onNodeWithText("Bienvenido, admin").assertIsDisplayed()
        compose.onNodeWithTag("category-0").assertExists()
        snapshot("menu")
        navigate("Comandas")
        compose.onNodeWithTag("orders-title").assertIsDisplayed()
        compose.onNodeWithText("Todas").assertExists()
        compose.onNodeWithText("Abiertos").assertExists()
        compose.onNodeWithText("Pagados").assertExists()
        navigate("Inventario")
        compose.onNodeWithTag("inventory-products").assertExists()
        compose.onNodeWithText("Ver movimientos").assertExists()
        compose.onAllNodesWithTag("inventory-movements-dialog").assertCountEquals(0)
        navigate("Reportes")
        compose.onNodeWithText("Reportes y cierres").assertIsDisplayed()
        compose.onNodeWithContentDescription("Calendario Desde").assertExists()
        compose.onNodeWithContentDescription("Calendario Hasta").assertExists()
        compose.onNodeWithContentDescription("Calendario Desde").performClick()
        compose.runOnUiThread {
            val dialog =
                org.robolectric.shadows.ShadowDialog.getLatestDialog()
                    as android.app.DatePickerDialog
            dialog.datePicker.updateDate(2026, 0, 2)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("2026-01-02").assertExists()
        compose.onNodeWithText("Desde").performTextReplacement("2026-02-03")
        compose.onNodeWithText("2026-02-03").assertExists()
        snapshot("reportes")
        navigate("Catálogos")
        compose.onNodeWithText("Nuevo").assertExists()
        compose.waitUntil(15000) {
            compose
                .onAllNodes(hasContentDescription("Eliminar", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose
            .onAllNodes(hasContentDescription("Eliminar", substring = true))
            .onFirst()
            .performClick()
        compose.onNodeWithText("Cancelar").assertExists()
        click("Cancelar")
        val verify = PosDatabase(ApplicationProvider.getApplicationContext())
        assertEquals(14, verify.rows("SELECT id FROM productos").size)
        verify.close()
        snapshot("catalogos")
    }

    @Test
    fun originalMenuDraftEditOrderAndPaymentFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PosDatabase(context)
        db.insert(
            "mesas",
            mapOf("numero" to 1, "nombre" to "Mesa 1", "disponibilidad" to "Disponible"),
        )
        try {
            login()
            // Presentations selected on the original product card remain separate cart lines.
            compose.onNodeWithTag("product-search").performTextInput("JR")
            compose.onNodeWithTag("product-grid").performScrollToNode(hasTestTag("product-add-1"))
            compose.onNodeWithTag("variant-2").assertIsSelected()
            clickTag("variant-2")
            clickTag("product-add-1")
            clickTag("variant-1")
            clickTag("product-add-1")
            summaryScroll("line-remove-0")
            compose.onNodeWithText("JR (Botella)").assertExists()
            clickTag("line-remove-0")
            compose.onNodeWithText("JR (Copa)").assertExists()
            summaryScroll("table-selector")
            clickTag("table-selector")
            clickTag("table-selector-option-1")
            summaryScroll("people-input")
            compose.onNodeWithTag("people-input").performTextReplacement("12")
            compose.onNodeWithTag("people-input").assertTextContains("12")
            clickTag("people-1")
            summaryScroll("line-plus-0")
            clickTag("line-plus-0")
            summaryScroll("line-minus-0")
            clickTag("line-minus-0")
            summaryScroll("line-edit-0")
            clickTag("line-edit-0")
            compose.onNodeWithText("Valor").performTextReplacement("10")
            click("Aceptar")
            compose.onNodeWithTag("summary-list").performScrollToNode(hasText("Descuento"))
            click("Descuento")
            compose.onNodeWithText("Valor").performTextReplacement("5")
            click("Guardar descuento")
            compose.onNodeWithTag("summary-list").performScrollToNode(hasText("Nota"))
            click("Nota")
            compose.onNodeWithText("Observaciones").performTextInput("Sin hielo")
            click("Guardar nota")
            summaryScroll("client-selector")
            compose.onNodeWithContentDescription("Agregar cliente").performSemanticsAction(
                SemanticsActions.OnClick
            ) {
                it()
            }
            compose.onNodeWithText("Nombre").performTextInput("Cliente de prueba")
            compose.onNodeWithText("Teléfono").performTextInput("6461234567")
            compose.onNodeWithText("Email").performTextInput("cliente@example.test")
            click("Agregar")
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Agregar Cliente").fetchSemanticsNodes().isEmpty()
            }
            // Switching pages must retain the original IndexedStack-style draft.
            navigate("Comandas")
            navigate("Menú")
            summaryScroll("save-order")
            compose.onAllNodesWithText("Total (USD)").onLast().assertExists()
            snapshot("resumen")
            compose.activityRule.scenario.recreate()
            summaryScroll("save-order")
            compose.onNodeWithText("Sin hielo").assertExists()
            clickTag("save-order")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("order-1").fetchSemanticsNodes().isNotEmpty()
            }
            clickTag("order-1")
            secondPane()
            compose.onNodeWithTag("order-detail").performScrollToNode(hasTestTag("edit-order"))
            clickTag("edit-order")
            summaryScroll("line-plus-0")
            clickTag("line-plus-0")
            summaryScroll("save-order")
            clickTag("save-order")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("order-1").fetchSemanticsNodes().isNotEmpty()
            }
            clickTag("order-1")
            secondPane()
            compose.onNodeWithTag("order-detail").performScrollToNode(hasTestTag("pay-order"))
            firstPane()
            compose
                .onNode(
                    hasText("Total $228.00") and hasAnyAncestor(hasTestTag("order-1")),
                    useUnmergedTree = true,
                )
                .assertExists()
            secondPane()
            compose.onNodeWithTag("order-detail").performScrollToNode(hasTestTag("pay-order"))
            snapshot("comandas")
            clickTag("pay-order")
            secondPane()
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Agregar pago / asignar artículos")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onAllNodesWithText("Total (USD)").onLast().assertExists()
            compose.onNodeWithText("Recibido (MXN)").assertExists()
            compose.onNodeWithText("Falta (MXN)").assertExists()
            compose.onNodeWithText("Cambio (MXN)").assertExists()
            snapshot("cobro")
            click("Agregar pago / asignar artículos")
            click("Agregar")
            compose.activityRule.scenario.recreate()
            secondPane()
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasText("Confirmar cobro") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            click("Confirmar cobro")
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Finalizar").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("pagado", db.rows("SELECT status FROM orders").first()["status"])
            assertEquals(1, db.rows("SELECT * FROM sales").size)
            assertEquals(
                "Sin hielo",
                db.rows("SELECT observaciones FROM orders").first()["observaciones"],
            )
            click("Finalizar")
            firstPane()
            clickTag("orders-filter-pagado")
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("order-1").fetchSemanticsNodes().isNotEmpty()
            }
            clickTag("order-1")
            secondPane()
            compose.onNodeWithTag("order-detail").performScrollToNode(hasTestTag("pay-order"))
            compose.onNodeWithTag("pay-order").assertIsNotEnabled()
            compose.onNodeWithTag("edit-order").assertIsNotEnabled()
            navigate("Reportes")
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithContentDescription("Ver comanda")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onAllNodesWithContentDescription("Ver comanda")
                .onFirst()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText("Detalle · Ventas").assertExists()
            click("Cerrar ventana")
            click("Reporte general / detallado y XLSX")
            compose.onAllNodesWithText("Exportar XLSX").onLast().assertExists()
            click("Cerrar ventana")
        } finally {
            db.close()
        }
    }
}
