package com.enverolab.comandas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import com.enverolab.comandas.data.*
import com.enverolab.comandas.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RepositoryTest {
    lateinit var context: Context
    lateinit var database: PosDatabase
    lateinit var repo: PosRepository

    private fun n(s: String) = BigDecimal(s)

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("restaurant.db")
        database = PosDatabase(context)
        repo = PosRepository(database)
        repo.login("admin", "1234")
        repo.saveCatalog(
            "mesas",
            null,
            mapOf("numero" to 1, "nombre" to "Mesa 1", "disponibilidad" to "Disponible"),
        )
    }

    @After
    fun cleanup() {
        database.close()
        context.deleteDatabase("restaurant.db")
    }

    private fun draft() =
        Draft(
            tableId = 1,
            persons = 3,
            clientId = 1,
            discountType = "%",
            discount = n("10"),
            lines =
                listOf(
                    Line(
                        productId = 1,
                        variantId = 1,
                        code = "1",
                        name = "JR Copa",
                        quantity = n("2"),
                        price = n("130"),
                        discountAmount = n("10"),
                    )
                ),
        )

    private fun cash(value: String) = Payment(1, "Efectivo", n(value), n("1"))

    @Test
    fun productReportDistributesDiscountWithoutLosingCents() {
        val lines =
            (1L..3L).map { id ->
                Line(productId = id, code = "$id", name = "Producto $id", price = BigDecimal.ONE)
            }
        val id =
            repo.saveOrder(
                draft().copy(lines = lines, discountType = "$", discount = BigDecimal.ONE)
            )
        repo.pay(id, listOf(cash("2")))
        val day = java.time.LocalDate.now().toString()
        val report = repo.report("Productos", day, day)
        assertEquals(
            0,
            n("2.00").compareTo(report.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("vendido") }),
        )
        assertTrue(report.all { it.decimal("vendido").scale() == 2 })
    }

    @Test
    fun reportsGroupSalesAndShowOrderPayments() {
        val first = repo.saveOrder(draft())
        repo.pay(first, listOf(cash("225")))
        val second = repo.saveOrder(draft())
        repo.pay(second, listOf(cash("225")))
        val day = java.time.LocalDate.now().toString()
        repo.saveOrder(draft()) // La comanda abierta no constituye una venta.
        assertEquals(2, repo.report("Comandas", day, day).size)
        val sales = repo.report("Ventas", day, day)
        assertEquals(1, sales.size)
        assertEquals(0, n("450").compareTo(sales.first().decimal("vendido")))
        assertEquals(0, n("4").compareTo(sales.first().decimal("cantidad")))
        assertEquals(1, repo.report("Pagos", day, day).size)
        assertEquals(
            0,
            n("450").compareTo(repo.report("Pagos", day, day).first().decimal("importe_moneda")),
        )
        assertEquals(2, repo.report("Detalles comandas", day, day).size)
        val ticket = repo.reportTicket("Detalles comandas", day, day)
        assertTrue(ticket.contains("COMANDA 1"))
        assertTrue(ticket.contains("COMANDA 2"))
        assertTrue(ticket.contains("Efectivo"))
        assertTrue(ticket.contains("TOTAL VENTAS MXN"))
        assertTrue(repo.orderReceipt(first).contains("NOTA DE VENTA"))
    }

    @Test
    fun detailedReportsReconcileDiscountAndClosure() {
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(cash("225")))
        val day = java.time.LocalDate.now().toString()
        val report = repo.detailedReport(day, day)
        assertEquals(0, n("225").compareTo(report.getValue("Productos").first().decimal("vendido")))
        assertEquals(0, n("225").compareTo(report.getValue("Resumen").first().decimal("total_mxn")))
        assertEquals(
            0,
            n("225").compareTo(report.getValue("Pagos").first().decimal("importe_moneda")),
        )
        assertTrue(
            repo
                .detailedReport(day, day, mapOf("fecha" to day, "hora" to "00:00:00"))
                .getValue("Comandas")
                .isEmpty()
        )
        val output = com.enverolab.comandas.io.PosFiles(context, database).xlsx("Prueba", report)
        java.util.zip.ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("xl/workbook.xml"))
            assertNotNull(zip.getEntry("xl/worksheets/sheet6.xml"))
        }
    }

    @Test
    fun companyLogoSurvivesBackupAndRestore() {
        val photo = java.io.File(context.filesDir, "test-logo.png")
        photo.writeBytes(byteArrayOf(1, 2, 3))
        val company = repo.catalog("Empresa").first()
        repo.saveCatalog("Empresa", company.id(), mapOf("logo" to photo.path))
        val files = com.enverolab.comandas.io.PosFiles(context, database)
        val backup = files.backup()
        files.restore(android.net.Uri.fromFile(backup))
        val restored = repo.catalog("Empresa").first().text("logo")
        assertArrayEquals(photo.readBytes(), java.io.File(restored).readBytes())
    }

    @Test
    fun preservesOriginalSchemaAndSeeds() {
        assertEquals(14, repo.products().size)
        assertEquals(
            18,
            database
                .rows(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'native_%' AND name NOT IN ('sqlite_sequence','android_metadata')"
                )
                .size,
        )
        assertEquals(12, database.readableDatabase.version)
    }

    @Test
    fun saveEditOrder() {
        val id = repo.saveOrder(draft())
        assertEquals(n("225.0"), repo.order(id).decimal("monto_por_pagar"))
        repo.saveOrder(draft().copy(id = id, persons = 4, discount = n("20")))
        assertEquals(4L, repo.order(id).id("personas"))
        assertEquals("Ocupada", repo.catalog("mesas").first().text("disponibilidad"))
    }

    @Test
    fun fullCheckout() {
        val id = repo.saveOrder(draft())
        val sale = repo.pay(id, listOf(cash("230")))
        assertEquals("pagado", repo.order(id).text("status"))
        assertEquals(1, repo.saleLines(sale).size)
        assertEquals(n("99.6"), repo.products().first { it.id() == 1L }.decimal("stock"))
        assertEquals("Disponible", repo.catalog("mesas").first().text("disponibilidad"))
    }

    @Test
    fun duplicateCheckoutPrevented() {
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(cash("225")))
        assertThrows(IllegalStateException::class.java) { repo.pay(id, listOf(cash("225"))) }
        assertEquals(1, database.rows("SELECT * FROM sales").size)
    }

    @Test
    fun insufficientPaymentRollsBack() {
        val id = repo.saveOrder(draft())
        assertThrows(IllegalStateException::class.java) { repo.pay(id, listOf(cash("10"))) }
        assertEquals(0, database.rows("SELECT * FROM sales").size)
        assertEquals("abierto", repo.order(id).text("status"))
    }

    @Test
    fun failureAfterSaleInsertRollsBackEverything() {
        val id = repo.saveOrder(draft())
        database.writableDatabase.execSQL(
            "CREATE TRIGGER fail_stock BEFORE UPDATE OF stock ON productos BEGIN SELECT RAISE(ABORT,'test'); END"
        )
        assertThrows(Exception::class.java) { repo.pay(id, listOf(cash("225"))) }
        assertEquals(0, database.rows("SELECT * FROM sales").size)
        assertEquals(0, database.rows("SELECT * FROM sale_payments").size)
        assertEquals("abierto", repo.order(id).text("status"))
        assertEquals(n("100.0"), repo.products().first { it.id() == 1L }.decimal("stock"))
    }

    @Test
    fun quantityAssignmentsPersisted() {
        val id = repo.saveOrder(draft())
        val item = repo.lines(id).first()
        repo.pay(
            id,
            listOf(
                cash("112.50").copy(assignments = mapOf(item.id to n("1")), person = "A"),
                cash("112.50").copy(assignments = mapOf(item.id to n("1")), person = "B"),
            ),
        )
        assertEquals(2, database.rows("SELECT * FROM native_item_quantities").size)
        assertEquals(2, database.rows("SELECT * FROM sale_item_assignment").size)
    }

    @Test
    fun duplicateAssignmentsRejected() {
        val id = repo.saveOrder(draft())
        val item = repo.lines(id).first()
        assertThrows(IllegalStateException::class.java) {
            repo.pay(id, listOf(cash("225").copy(assignments = mapOf(item.id to n("3")))))
        }
        assertEquals(0, database.rows("SELECT * FROM sales").size)
    }

    @Test
    fun originalFreeMethod() {
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(Payment(4, "Gratis", n("0"), n("0"))))
        assertEquals("pagado", repo.order(id).text("status"))
    }

    @Test
    fun cashierCannotEditCatalogs() {
        repo.login("cajero", "5678")
        assertThrows(IllegalStateException::class.java) {
            repo.saveCatalog("categories", null, mapOf("name" to "Otra"))
        }
    }

    @Test
    fun closureOnlyOnce() {
        repo.closeDay("total", "")
        assertThrows(IllegalStateException::class.java) { repo.closeDay("total", "") }
        assertThrows(IllegalStateException::class.java) { repo.closeDay("parcial", "") }
    }

    @Test
    fun snapshotRoundTrip() {
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(cash("225")))
        val backup = database.exportDatabase()
        val expected = database.rows("SELECT * FROM sales")
        database.importDatabase(backup.inputStream())
        assertEquals(expected, database.rows("SELECT * FROM sales"))
        assertEquals("pagado", repo.order(id).text("status"))
    }

    @Test
    fun invalidImportLeavesData() {
        val expected = database.rows("SELECT * FROM productos")
        assertThrows(Exception::class.java) { database.importDatabase("bad".byteInputStream()) }
        assertEquals(expected, database.rows("SELECT * FROM productos"))
    }

    @Test
    fun migrationFromLegacy10PreservesRows() {
        val file = java.io.File(context.cacheDir, "legacy10.db")
        file.delete()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            context.assets
                .open("schema_v10.sql")
                .bufferedReader()
                .readText()
                .split(';')
                .filter { it.isNotBlank() }
                .forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO users(id,username,password,role,status) VALUES(42,'legacy','original','admin','Activo')"
            )
            db.execSQL("INSERT INTO categories(id,name) VALUES(77,'Legacy')")
            db.version = 10
        }
        database.importDatabase(file.inputStream())
        repo.login("legacy", "original")
        assertEquals(42L, repo.user!!.id())
        assertEquals("Legacy", repo.catalog("categories").first().text("name"))
        assertEquals(12, database.readableDatabase.version)
        assertTrue(
            database
                .rows("SELECT password FROM users WHERE id=42")
                .first()
                .text("password")
                .startsWith("pbkdf2:")
        )
    }

    @Test
    fun incompatibleSchemaRejectedBeforeReplacing() {
        val backup =
            database
                .exportDatabase()
                .copyTo(java.io.File(context.cacheDir, "incompatible.db"), true)
        android.database.sqlite.SQLiteDatabase.openDatabase(
                backup.path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
            )
            .use { it.execSQL("DROP TABLE cierres") }
        assertThrows(IllegalArgumentException::class.java) {
            database.importDatabase(backup.inputStream())
        }
        assertEquals(14, repo.products().size)
    }

    @Test
    fun stockEntryPersistsHistory() {
        repo.adjustStock(1, n("2.5"), true)
        assertEquals(n("102.5"), repo.products().first { it.id() == 1L }.decimal("stock"))
        assertEquals(1, database.rows("SELECT * FROM native_inventory_moves").size)
    }

    @Test
    fun sharedTableRemainsOccupiedUntilLastOrderCloses() {
        val first = repo.saveOrder(draft())
        val second = repo.saveOrder(draft())
        assertNotEquals(first, second)
        repo.pay(first, listOf(cash("225")))
        assertEquals("Ocupada", repo.catalog("mesas").first().text("disponibilidad"))
        repo.deleteOrder(second)
        assertEquals("Disponible", repo.catalog("mesas").first().text("disponibilidad"))
    }

    @Test
    fun movingOneSharedOrderKeepsTheOtherTableOccupied() {
        repo.saveCatalog(
            "mesas",
            null,
            mapOf("numero" to 2, "nombre" to "Mesa 2", "disponibilidad" to "Disponible"),
        )
        val first = repo.saveOrder(draft())
        val second = repo.saveOrder(draft())
        repo.saveOrder(draft().copy(id = second, tableId = 2))
        assertTrue(repo.catalog("mesas").all { it.text("disponibilidad") == "Ocupada" })
        repo.deleteOrder(first)
        assertEquals(
            "Disponible",
            repo.catalog("mesas").first { it.id() == 1L }.text("disponibilidad"),
        )
        assertEquals(
            "Ocupada",
            repo.catalog("mesas").first { it.id() == 2L }.text("disponibilidad"),
        )
    }

    @Test
    fun inventorySummaryAndMovementRangeUseOnlySelectedProduct() {
        repo.adjustStock(1, n("2"), true)
        repo.adjustStock(1, n("0.5"), false)
        repo.adjustStock(2, n("3"), true)
        val today = java.time.LocalDate.now()
        val old = today.minusMonths(2).toString()
        database.writableDatabase.execSQL(
            "UPDATE native_inventory_moves SET fecha=? WHERE product_id=1 AND direction='entrada'",
            arrayOf(old),
        )
        val products = repo.inventoryProducts()
        val p = products.first { it.id() == 1L }
        assertEquals(0, n("101.5").compareTo(p.decimal("stock")))
        assertTrue(p.text("ultima_entrada").startsWith(old))
        assertTrue(p.text("ultima_salida").startsWith(today.toString()))
        val moves = repo.inventoryMovements(1, today.minusMonths(1).toString(), today.toString())
        assertEquals(1, moves.size)
        assertEquals("salida", moves.first().text("direction"))
        assertEquals(0, n("380").compareTo(moves.first().decimal("precio_venta_actual")))
        val text =
            repo.inventoryMovementTicket(1, today.minusMonths(1).toString(), today.toString())
        assertTrue(text.contains("Total salidas"))
        assertTrue(text.contains("Precio venta actual"))
        assertThrows(IllegalArgumentException::class.java) {
            repo.inventoryMovements(1, today.toString(), old)
        }
    }

    @Test
    fun oversizedStockWithdrawalLeavesNoFalseMovement() {
        assertThrows(IllegalArgumentException::class.java) { repo.adjustStock(1, n("101"), false) }
        assertEquals(
            0,
            n("100").compareTo(repo.inventoryProducts().first { it.id() == 1L }.decimal("stock")),
        )
        assertTrue(database.rows("SELECT * FROM native_inventory_moves").isEmpty())
    }

    @Test
    fun changedExchangeRateRejectsStalePayment() {
        val id = repo.saveOrder(draft())
        val payment = Payment(3, "Dolares", n("20"), n("19.5"), currency = "USD")
        repo.saveCatalog("tipo_pago", 3, mapOf("factor" to n("20")))
        assertThrows(IllegalStateException::class.java) { repo.pay(id, listOf(payment)) }
        assertTrue(database.rows("SELECT * FROM sales").isEmpty())
    }

    @Test
    fun zipBackupRestoresPhotosAndSales() {
        val photo = java.io.File(context.filesDir, "sample-photo.jpg")
        photo.writeBytes(byteArrayOf(1, 2, 3, 4))
        database.update("productos", 1, mapOf("foto" to photo.path))
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(cash("225")))
        val files = com.enverolab.comandas.io.PosFiles(context, database)
        val backup = files.backup()
        files.restore(android.net.Uri.fromFile(backup))
        val imported = java.io.File(repo.products().first { it.id() == 1L }.text("foto"))
        assertArrayEquals(photo.readBytes(), imported.readBytes())
        assertEquals(1, database.rows("SELECT * FROM sales").size)
    }

    @Test
    fun zipTraversalRejectedWithoutReplacingData() {
        val zip = java.io.File(context.cacheDir, "unsafe.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("../outside"))
            it.write(byteArrayOf(1))
            it.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.enverolab.comandas.io
                .PosFiles(context, database)
                .restore(android.net.Uri.fromFile(zip))
        }
        assertEquals(14, repo.products().size)
    }

    @Test
    fun allOrderFiltersIncludePaidOrdersWithoutReopeningThem() {
        val id = repo.saveOrder(draft())
        repo.pay(id, listOf(cash("225")))
        database.update("orders", id, mapOf("monto_por_pagar" to 0))
        assertEquals(n("225.00"), repo.orders("pagado").first().decimal("total"))
        assertEquals(
            0.0,
            database
                .rows("SELECT monto_por_pagar FROM orders WHERE id=?", listOf(id))
                .first()["monto_por_pagar"],
        )
        repo.saveOrder(draft())
        assertEquals(2, repo.orders(null).size)
        assertEquals(1, repo.orders("abierto").size)
        assertEquals(1, repo.orders("pagado").size)
        assertEquals(id, repo.orders("pagado").first().id())
        assertThrows(IllegalArgumentException::class.java) { repo.orders("unexpected") }
    }

    @Test
    fun cashierQuickClientAndPhotoActionsStayScoped() {
        repo.login("cajero", "5678")
        val id = repo.addClient("Prueba", "6461234567", "prueba@example.test")
        assertEquals("Prueba", repo.catalog("clientes").first { it.id() == id }.text("nombre"))
        assertThrows(IllegalArgumentException::class.java) { repo.addClient("", "", "") }
        val file =
            java.io.File(context.cacheDir, "photo-test.jpg").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
        val before = repo.products().first { it.id() == 1L }
        repo.updateProductPhoto(1, file.path)
        val after = repo.products().first { it.id() == 1L }
        assertEquals(file.path, after.text("foto"))
        assertEquals(before.decimal("precio"), after.decimal("precio"))
        assertThrows(IllegalStateException::class.java) {
            repo.saveCatalog("productos", 1, mapOf("precio" to 1))
        }
        repo.logout()
        assertThrows(IllegalStateException::class.java) { repo.addClient("A", "B", "C") }
    }
}
