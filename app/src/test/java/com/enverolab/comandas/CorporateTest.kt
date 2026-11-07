package com.enverolab.comandas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import java.util.UUID
import com.enverolab.comandas.corporate.CorporateStore
import com.enverolab.comandas.data.*
import com.enverolab.comandas.domain.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CorporateTest {
    lateinit var db: PosDatabase
    lateinit var repo: PosRepository
    lateinit var context: Context

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("restaurant.db")
        db = PosDatabase(context)
        repo = PosRepository(db)
        repo.login("admin", "1234")
        repo.saveCatalog(
            "mesas",
            null,
            mapOf("numero" to 1, "nombre" to "Mesa 1", "disponibilidad" to "Disponible"),
        )
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase("restaurant.db")
    }

    private fun sale() {
        val order =
            repo.saveOrder(
                Draft(
                    tableId = 1,
                    persons = 1,
                    clientId = 1,
                    lines =
                        listOf(
                            Line(
                                productId = 1,
                                variantId = 1,
                                code = "1",
                                name = "Vino",
                                quantity = BigDecimal.ONE,
                                price = BigDecimal("100"),
                            )
                        ),
                )
            )
        repo.pay(order, listOf(Payment(1, "Efectivo", BigDecimal("100"), BigDecimal.ONE)))
    }

    @Test
    fun standaloneDoesNotQueue() {
        sale()
        repo.closeDay("parcial", "")
        assertTrue(db.rows("SELECT * FROM native_corporate_outbox").isEmpty())
        assertEquals(1, db.rows("SELECT * FROM native_corporate_sales").size)
    }

    @Test
    fun cutSnapshotIsDurableAndCumulative() {
        sale()
        CorporateStore.set(db, "enabled", "1")
        CorporateStore.set(db, "lease:admin", (System.currentTimeMillis() + 86400000L).toString())
        CorporateStore.set(db, "installation", UUID.randomUUID().toString())
        repo.closeDay("parcial", "")
        repo.closeDay("total", "")
        val rows = db.rows("SELECT * FROM native_corporate_outbox ORDER BY closure_id")
        assertEquals(2, rows.size)
        val a = JSONObject(rows[0].text("payload"))
        val b = JSONObject(rows[1].text("payload"))
        assertEquals(1, a.getJSONArray("sales").length())
        assertEquals(a.getJSONArray("sales").toString(), b.getJSONArray("sales").toString())
        assertEquals("pending", rows[0].text("status"))
        db.close()
        db = PosDatabase(context)
        assertEquals(2, db.rows("SELECT * FROM native_corporate_outbox").size)
    }

    @Test
    fun failedSnapshotRollsBackLocalCut() {
        CorporateStore.set(db, "enabled", "1")
        CorporateStore.set(db, "lease:admin", (System.currentTimeMillis() + 86400000L).toString())
        try {
            repo.closeDay("parcial", "")
            fail("Debe rechazar identidad incompleta")
        } catch (_: IllegalStateException) {}
        assertTrue(db.rows("SELECT * FROM cierres").isEmpty())
    }

    @Test
    fun corporateCatalogRequiresPortal() {
        CorporateStore.set(db, "enabled", "1")
        CorporateStore.set(db, "lease:admin", (System.currentTimeMillis() + 86400000L).toString())
        try {
            repo.saveCatalog("categories", null, mapOf("name" to "No permitido"))
            fail("No debe modificar catálogo corporativo")
        } catch (_: IllegalStateException) {}
        CorporateStore.set(db, "enabled", "0")
        repo.saveCatalog("categories", null, mapOf("name" to "Local permitido"))
        assertTrue(repo.catalog("categories").any { it.text("name") == "Local permitido" })
    }
}
