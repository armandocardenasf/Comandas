package com.enverolab.comandas.corporate

import android.database.sqlite.SQLiteDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import com.enverolab.comandas.data.PosDatabase
import com.enverolab.comandas.domain.*
import org.json.JSONArray
import org.json.JSONObject

object CorporateStore {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_corporate_state (key TEXT PRIMARY KEY,value TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_corporate_map (kind TEXT NOT NULL,remote_id INTEGER NOT NULL,local_id INTEGER NOT NULL,PRIMARY KEY(kind,remote_id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_corporate_sales (sale_id INTEGER PRIMARY KEY,payload TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_corporate_moves (move_id INTEGER PRIMARY KEY,payload TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_corporate_outbox (id TEXT PRIMARY KEY,closure_id INTEGER UNIQUE NOT NULL,payload TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'pending',receipt TEXT,error TEXT NOT NULL DEFAULT '',attempts INTEGER NOT NULL DEFAULT 0,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        )
    }

    fun get(s: PosDatabase, k: String, db: SQLiteDatabase? = null) =
        s.rows("SELECT value FROM native_corporate_state WHERE key=?", listOf(k), db)
            .firstOrNull()
            ?.text("value")
            .orEmpty()

    fun set(s: PosDatabase, k: String, v: String, db: SQLiteDatabase? = null) {
        (db ?: s.writableDatabase).execSQL(
            "INSERT OR REPLACE INTO native_corporate_state(key,value) VALUES (?,?)",
            arrayOf(k, v),
        )
    }

    fun json(r: Row) =
        JSONObject().apply {
            r.forEach { (k, v) ->
                put(
                    k,
                    when (v) {
                        null -> JSONObject.NULL
                        is BigDecimal -> v.toPlainString()
                        else -> v
                    },
                )
            }
        }

    fun array(rows: List<Row>) = JSONArray().apply { rows.forEach { put(json(it)) } }

    fun freezeSale(s: PosDatabase, db: SQLiteDatabase, id: Long, actor: Row? = null) {
        if (
            s.rows("SELECT sale_id FROM native_corporate_sales WHERE sale_id=?", listOf(id), db)
                .isNotEmpty()
        )
            return
        val sale = s.rows("SELECT * FROM sales WHERE id=?", listOf(id), db).first()
        val items =
            s.rows(
                "SELECT si.*,COALESCE(c.name,'Sin categoría') AS category FROM sale_items si LEFT JOIN productos p ON p.id=si.product_id LEFT JOIN categories c ON c.id=p.category_id WHERE si.sale_id=? ORDER BY si.id",
                listOf(id),
                db,
            )
        val total = sale.decimal("monto_pagado").setScale(2, RoundingMode.HALF_UP)
        val sum = items.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("total") }
        var left = total
        val lines = JSONArray()
        items.forEachIndexed { i, r ->
            val n =
                if (i == items.lastIndex) left
                else if (sum.signum() == 0) BigDecimal.ZERO
                else
                    r.decimal("total")
                        .multiply(total)
                        .divide(sum, 2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO)
                        .min(left)
            left -= n
            lines.put(json(r).put("net", n.toPlainString()))
        }
        val pays =
            s.rows(
                "SELECT sp.*,tp.nombre AS method,COALESCE(nm.factor,'1') AS factor,COALESCE(nm.received,sp.monto_pagado) AS received,nm.tip_received FROM sale_payments sp LEFT JOIN tipo_pago tp ON tp.id=sp.tipo_pago_id LEFT JOIN native_payment_meta nm ON nm.payment_id=sp.id WHERE sp.sale_id=? ORDER BY sp.id",
                listOf(id),
                db,
            )
        val assignments =
            s.rows(
                "SELECT sa.*,nq.quantity FROM sale_item_assignment sa JOIN sale_payments sp ON sp.id=sa.sale_payment_id LEFT JOIN native_item_quantities nq ON nq.payment_id=sa.sale_payment_id AND nq.order_item_id=sa.order_item_id WHERE sp.sale_id=? ORDER BY sa.id",
                listOf(id),
                db,
            )
        db.execSQL(
            "INSERT INTO native_corporate_sales(sale_id,payload) VALUES (?,?)",
            arrayOf<Any>(
                id,
                json(sale)
                    .put("registered_by", actor?.let { json(it) } ?: JSONObject.NULL)
                    .put("items", lines)
                    .put("payments", array(pays))
                    .put("assignments", array(assignments))
                    .toString(),
            ),
        )
    }

    fun freezeMove(s: PosDatabase, db: SQLiteDatabase, id: Long, actor: Row? = null) {
        if (
            s.rows("SELECT move_id FROM native_corporate_moves WHERE move_id=?", listOf(id), db)
                .isNotEmpty()
        )
            return
        val r =
            s.rows(
                    "SELECT m.*,p.codigo AS product_code,p.precio FROM native_inventory_moves m LEFT JOIN productos p ON p.id=m.product_id WHERE m.id=?",
                    listOf(id),
                    db,
                )
                .first()
        db.execSQL(
            "INSERT INTO native_corporate_moves(move_id,payload) VALUES (?,?)",
            arrayOf<Any>(
                id,
                json(r).put("registered_by", actor?.let { json(it) } ?: JSONObject.NULL).toString(),
            ),
        )
    }

    fun queueClosure(s: PosDatabase, db: SQLiteDatabase, id: Long, actor: Row? = null) {
        if (get(s, "enabled", db) != "1") return
        val install = get(s, "installation", db)
        check(install.isNotBlank()) { "Configure primero la terminal" }
        val c = s.rows("SELECT * FROM cierres WHERE id=?", listOf(id), db).first()
        val sales = JSONArray()
        s.rows("SELECT id FROM sales WHERE fecha=? ORDER BY id", listOf(c.text("fecha")), db)
            .forEach { r ->
                freezeSale(s, db, r.id())
                sales.put(
                    JSONObject(
                        s.rows(
                                "SELECT payload FROM native_corporate_sales WHERE sale_id=?",
                                listOf(r.id()),
                                db,
                            )
                            .first()
                            .text("payload")
                    )
                )
            }
        val moves = JSONArray()
        s.rows(
                "SELECT id FROM native_inventory_moves WHERE id>? ORDER BY id",
                listOf(get(s, "last_move", db).toLongOrNull() ?: 0),
                db,
            )
            .forEach { r ->
                freezeMove(s, db, r.id())
                moves.put(
                    JSONObject(
                        s.rows(
                                "SELECT payload FROM native_corporate_moves WHERE move_id=?",
                                listOf(r.id()),
                                db,
                            )
                            .first()
                            .text("payload")
                    )
                )
            }
        val orders = JSONArray()
        s.rows(
                "SELECT * FROM orders WHERE fecha=? OR status='abierto' ORDER BY id",
                listOf(c.text("fecha")),
                db,
            )
            .forEach { r ->
                orders.put(
                    json(r)
                        .put(
                            "items",
                            array(
                                s.rows(
                                    "SELECT * FROM order_items WHERE order_id=? ORDER BY id",
                                    listOf(r.id()),
                                    db,
                                )
                            ),
                        )
                )
            }
        val cut =
            json(c)
                .put("registered_by", actor?.let { json(it) } ?: JSONObject.NULL)
                .put(
                    "stock",
                    array(
                        s.rows(
                            "SELECT id,codigo,nombre,stock,precio FROM productos ORDER BY id",
                            db = db,
                        )
                    ),
                )
                .put("semantics", "cumulative")
        val batch = UUID.randomUUID().toString()
        val payload =
            JSONObject()
                .put("version", 1)
                .put("id", batch)
                .put("installation", install)
                .put("closure", cut)
                .put("sales", sales)
                .put("orders", orders)
                .put("inventory", moves)
        db.execSQL(
            "INSERT INTO native_corporate_outbox(id,closure_id,payload) VALUES (?,?,?)",
            arrayOf<Any>(batch, id, payload.toString()),
        )
    }
}
