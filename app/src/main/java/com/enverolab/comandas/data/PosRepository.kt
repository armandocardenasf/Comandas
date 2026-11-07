package com.enverolab.comandas.data

import android.database.sqlite.SQLiteDatabase
import com.enverolab.comandas.corporate.CorporateStore
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import com.enverolab.comandas.domain.*

class PosRepository(val store: PosDatabase) {
    var user: Row? = null
        private set

    val tables =
        linkedMapOf(
            "Productos" to "productos",
            "Variantes" to "producto_variantes",
            "Categorías" to "categories",
            "Empleados" to "empleados",
            "Clientes" to "clientes",
            "Mesas" to "mesas",
            "Métodos de pago" to "tipo_pago",
            "Empresa" to "Empresa",
            "Folios" to "Folios",
            "Menú" to "menu",
            "Usuarios" to "users",
        )

    fun login(name: String, password: String): Row {
        val row =
            store
                .rows("SELECT * FROM users WHERE username=? AND status='Activo'", listOf(name))
                .firstOrNull() ?: error("Usuario o contraseña incorrectos")
        check(Passwords.verify(password, row.text("password"))) {
            "Usuario o contraseña incorrectos"
        }
        if (!row.text("password").startsWith("pbkdf2:"))
            store.update("users", row.id(), mapOf("password" to Passwords.hash(password)))
        store.update(
            "users",
            row.id(),
            mapOf("ultimo_acceso" to "${LocalDate.now()}T${LocalTime.now()}"),
        )
        user = row - "password"
        return user!!
    }

    fun restoreSession(id: Long) {
        user =
            store
                .rows("SELECT * FROM users WHERE id=? AND status='Activo'", listOf(id))
                .firstOrNull()
                ?.minus("password")
    }

    fun logout() {
        user = null
    }

    fun requireUser() {
        if (CorporateStore.get(store,"enabled")=="1") {
            val name=user?.text("username").orEmpty()
            if ((CorporateStore.get(store,"lease:$name").toLongOrNull()?:0)<=System.currentTimeMillis()) { logout();error("La autorización local venció. Inicie sesión con conexión") }
        }
        val id = user?.id() ?: error("Inicie sesión")
        val row =
            store
                .rows("SELECT * FROM users WHERE id=? AND status='Activo'", listOf(id))
                .firstOrNull()
                ?: run {
                    logout()
                    error("Usuario inactivo")
                }
        user = row - "password"
    }

    fun requireAdmin() {
        requireUser()
        check(user!!.text("role") in listOf("admin", "super_cajero")) { "Acceso restringido" }
    }

    fun catalog(table: String): List<Row> {
        requireUser()
        if (table == "users") requireAdmin()
        check(table in tables.values)
        return store.rows("SELECT * FROM $table ORDER BY id").map {
            if (table == "users") it - "password" else it
        }.filter { CorporateStore.get(store,"enabled")!="1" || CorporateStore.get(store,"inactive:$table:${it.id()}")!="1" }
    }

    fun columns(table: String): List<Row> {
        requireAdmin()
        check(table in tables.values)
        return store.rows("PRAGMA table_xinfo($table)").filter {
            it.id("hidden") == 0L && it.text("name") !in listOf("id", "fecha_alta", "ultimo_acceso")
        }
    }

    fun saveCatalog(table: String, id: Long?, values: Row): Long {
        requireAdmin()
        check(CorporateStore.get(store,"enabled")!="1" || table=="Folios"){ "Administre catálogos en el corporativo" }
        check(table in tables.values)
        val allowed = columns(table).map { it.text("name") }
        require(values.keys.all { it in allowed }) { "Columna inválida" }
        val data = values.toMutableMap()
        if (table == "users") {
            require(data["role"] in listOf("admin", "super_cajero", "cajero")) { "Rol inválido" }
            val password = data["password"]?.toString().orEmpty()
            if (password.isNotEmpty()) data["password"] = Passwords.hash(password)
            else {
                require(id != null) { "Contraseña requerida" }
                data.remove("password")
            }
            if (id == user!!.id())
                require(
                    data["role"] == user!!.text("role") &&
                        (data["status"] == null || data["status"] == "Activo")
                ) {
                    "No cambie su propio rol o estado"
                }
        }
        if (table in listOf("Empresa", "Folios") && id == null)
            check(catalog(table).isEmpty()) { "Edite el registro existente" }
        return store.transaction { db ->
            val oldStock =
                if (table == "productos" && id != null)
                    store
                        .rows("SELECT stock FROM productos WHERE id=?", listOf(id), db)
                        .first()
                        .decimal("stock")
                else BigDecimal.ZERO
            val result =
                if (id == null) store.insert(table, data, db)
                else {
                    store.update(table, id, data, db)
                    id
                }
            if (table == "productos" && data.containsKey("stock")) {
                val delta = data["stock"].toString().toBigDecimal() - oldStock
                if (delta.signum() != 0)
                    inventoryMove(
                        db,
                        result,
                        delta.abs(),
                        if (delta.signum() > 0) "entrada" else "salida",
                        null,
                    )
            }
            result
        }
    }

    fun deleteCatalog(table: String, id: Long) {
        requireAdmin()
        check(CorporateStore.get(store,"enabled")!="1"){ "Administre catálogos en el corporativo" }
        check(table in tables.values)
        check(table !in listOf("Empresa", "Folios")) { "Este registro debe conservarse" }
        if (table == "users") check(id != user!!.id()) { "No elimine su sesión" }
        store.writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    }

    fun products(): List<Row> {
        requireUser()
        return store.rows(
            "SELECT p.*, c.name AS category FROM productos p LEFT JOIN categories c ON c.id=p.category_id WHERE p.status='Activo' ORDER BY p.nombre"
        )
    }

    fun variants(product: Long) =
        store.rows(
            "SELECT * FROM producto_variantes WHERE producto_id=? ORDER BY id",
            listOf(product),
        ).filter { CorporateStore.get(store,"enabled")!="1" || CorporateStore.get(store,"inactive:producto_variantes:${it.id()}")!="1" }

    fun orders(status: String? = "abierto"): List<Row> {
        requireUser()
        require(status == null || status in listOf("abierto", "pagado"))
        val where = if (status == null) "" else "WHERE o.status=?"
        val args = if (status == null) emptyList() else listOf(status)
        return synchronized(store) {
            val orders =
                store.rows(
                    "SELECT o.*, m.nombre AS mesa_nombre, c.nombre AS cliente_nombre FROM orders o LEFT JOIN mesas m ON m.id=o.mesa LEFT JOIN clientes c ON c.id=o.id_cliente $where ORDER BY o.id DESC",
                    args,
                )
            // Stream item subtotals: history can contain many years of paid orders.
            val subtotals = mutableMapOf<Long, BigDecimal>()
            store.readableDatabase
                .rawQuery(
                    "SELECT oi.order_id,oi.quantity,oi.price,oi.discount_amount FROM order_items oi JOIN orders o ON o.id=oi.order_id $where",
                    args.map { it.toString() }.toTypedArray(),
                )
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        fun number(index: Int) =
                            cursor.getString(index).orEmpty().toBigDecimalOrNull()
                                ?: BigDecimal.ZERO
                        val line =
                            Line(
                                productId = 0,
                                code = "",
                                name = "",
                                quantity = number(1),
                                price = number(2),
                                discountAmount = number(3),
                            )
                        val id = cursor.getLong(0)
                        subtotals[id] = (subtotals[id] ?: BigDecimal.ZERO) + line.net
                    }
                }
            orders.map { row ->
                row +
                    ("total" to
                        Totals.total(
                            subtotals[row.id()] ?: BigDecimal.ZERO,
                            row.text("discount_type"),
                            row.decimal("general_discount"),
                        ))
            }
        }
    }

    /** The original POS allows cashiers to register a customer while taking an order. */
    fun addClient(name: String, phone: String, email: String): Long {
        requireUser()
        require(name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()) {
            "Complete nombre, teléfono y email"
        }
        return store.insert(
            "clientes",
            mapOf(
                "nombre" to name.trim(),
                "telefono" to phone.trim(),
                "email" to email.trim(),
                "nivel" to 1,
            ),
        )
    }

    fun updateProductPhoto(id: Long, path: String) {
        requireUser()
        require(java.io.File(path).isFile) { "Imagen no disponible" }
        check(store.rows("SELECT id FROM productos WHERE id=?", listOf(id)).isNotEmpty()) {
            "Producto no encontrado"
        }
        store.update("productos", id, mapOf("foto" to path))
    }

    fun order(id: Long): Row {
        requireUser()
        return store.rows("SELECT * FROM orders WHERE id=?", listOf(id)).firstOrNull()
            ?: error("Comanda no encontrada")
    }

    fun lines(order: Long, db: SQLiteDatabase? = null) =
        store
            .rows("SELECT * FROM order_items WHERE order_id=? ORDER BY id", listOf(order), db)
            .map { line(it) }

    fun saleLines(sale: Long) =
        store.rows("SELECT * FROM sale_items WHERE sale_id=? ORDER BY id", listOf(sale)).map {
            line(it)
        }

    private fun line(it: Row) =
        Line(
            it.id(),
            it.id("product_id"),
            it.id("product_variant_id").takeIf { n -> n > 0 },
            it.text("product_code"),
            it.text("product_name"),
            it.text("product_descripcion"),
            it.decimal("quantity"),
            it.decimal("price"),
            it.decimal("discount_percent"),
            it.decimal("discount_amount"),
        )

    private fun nextFolio(db: SQLiteDatabase, column: String, table: String): Long {
        require(column in listOf("folio_comanda", "folio_venta", "folio_cierre"))
        if (store.rows("SELECT * FROM Folios", db = db).isEmpty())
            store.insert(
                "Folios",
                mapOf("folio_comanda" to 0, "folio_venta" to 0, "folio_cierre" to 0),
                db,
            )
        val counter = store.rows("SELECT MAX($column) AS n FROM Folios", db = db).first().id("n")
        val max =
            if (table == "cierres")
                store.rows("SELECT MAX(id) AS n FROM cierres", db = db).first().id("n")
            else store.rows("SELECT MAX($column) AS n FROM $table", db = db).first().id("n")
        val next = maxOf(counter, max) + 1
        db.execSQL("UPDATE Folios SET $column=?", arrayOf(next))
        return next
    }

    fun saveOrder(draft: Draft): Long {
        requireUser()
        require(draft.persons > 0 && draft.lines.isNotEmpty()) { "Indique personas y productos" }
        require(
            draft.lines.all {
                it.quantity > BigDecimal.ZERO &&
                    it.price > BigDecimal.ZERO &&
                    it.discountAmount >= BigDecimal.ZERO &&
                    it.discountAmount <= it.price * it.quantity
            }
        ) {
            "Cantidad, precio o descuento inválido"
        }
        return store.transaction { db ->
            val existing =
                draft.id?.let {
                    store.rows("SELECT * FROM orders WHERE id=?", listOf(it), db).first()
                }
            check(existing == null || existing.text("status") == "abierto") { "Comanda ya pagada" }
            val table =
                store
                    .rows("SELECT * FROM mesas WHERE id=?", listOf(draft.tableId), db)
                    .firstOrNull() ?: error("Seleccione mesa")
            // Una misma mesa admite varias comandas; releaseTable comprueba las restantes.
            val total = Totals.total(draft.lines, draft.discountType, draft.discount)
            val values =
                mapOf(
                    "mesa" to draft.tableId,
                    "personas" to draft.persons,
                    "id_cliente" to draft.clientId,
                    "discount_type" to draft.discountType,
                    "general_discount" to draft.discount,
                    "observaciones" to draft.notes,
                    "monto_por_pagar" to total,
                )
            val id =
                if (draft.id == null)
                    store.insert(
                        "orders",
                        values +
                            mapOf(
                                "folio_comanda" to nextFolio(db, "folio_comanda", "orders"),
                                "fecha" to LocalDate.now(),
                                "hora" to LocalTime.now().withNano(0),
                                "status" to "abierto",
                                "monto_pagado" to 0,
                            ),
                        db,
                    )
                else {
                    store.update("orders", draft.id, values, db)
                    draft.id
                }
            db.delete("order_items", "order_id=?", arrayOf(id.toString()))
            draft.lines.forEach {
                store.insert("order_items", lineValues(it) + mapOf("order_id" to id), db)
            }
            if (existing != null && existing.id("mesa") != draft.tableId)
                releaseTable(db, existing.id("mesa"))
            store.update("mesas", draft.tableId, mapOf("disponibilidad" to "Ocupada"), db)
            id
        }
    }

    private fun lineValues(line: Line): Row =
        mapOf(
            "product_id" to line.productId,
            "product_variant_id" to line.variantId,
            "product_code" to line.code,
            "product_name" to line.name,
            "product_descripcion" to line.description,
            "quantity" to line.quantity,
            "price" to line.price,
            "total" to line.net,
            "discount_percent" to line.discountPercent,
            "discount_amount" to line.discountAmount,
        )

    private fun releaseTable(db: SQLiteDatabase, id: Long) {
        if (
            store
                .rows("SELECT id FROM orders WHERE mesa=? AND status='abierto'", listOf(id), db)
                .isEmpty()
        )
            store.update("mesas", id, mapOf("disponibilidad" to "Disponible"), db)
    }

    fun deleteOrder(id: Long) {
        requireUser()
        store.transaction { db ->
            val row = order(id)
            check(row.text("status") == "abierto")
            db.delete("order_items", "order_id=?", arrayOf(id.toString()))
            db.delete("orders", "id=?", arrayOf(id.toString()))
            releaseTable(db, row.id("mesa"))
        }
    }

    fun pay(orderId: Long, payments: List<Payment>): Long {
        requireUser()
        require(payments.isNotEmpty()) { "Agregue un pago" }
        return store.transaction { db ->
            val order = order(orderId)
            check(order.text("status") == "abierto") { "Comanda ya pagada" }
            val items = lines(orderId, db)
            val total =
                Totals.total(items, order.text("discount_type"), order.decimal("general_discount"))
            val assigned = mutableMapOf<Long, BigDecimal>()
            payments.forEach { p ->
                val method =
                    store
                        .rows("SELECT * FROM tipo_pago WHERE id=?", listOf(p.methodId), db)
                        .firstOrNull() ?: error("Método inexistente")
                check(
                    method.decimal("factor").compareTo(p.factor) == 0 &&
                        method.text("nombre") == p.methodName
                ) {
                    "Método modificado; recargue los pagos"
                }
                require(
                    p.tip >= BigDecimal.ZERO &&
                        p.received >= BigDecimal.ZERO &&
                        (p.complimentary || p.factor > BigDecimal.ZERO)
                ) {
                    "Importes inválidos"
                }
                p.assignments.forEach { (id, q) ->
                    require(q > BigDecimal.ZERO)
                    check(items.any { it.id == id }) { "Producto fuera de comanda" }
                    assigned[id] = (assigned[id] ?: BigDecimal.ZERO) + q
                }
            }
            assigned.forEach { (id, q) ->
                check(q <= items.first { it.id == id }.quantity) {
                    "Cantidad asignada más de una vez"
                }
            }
            val received = payments.fold(BigDecimal.ZERO) { a, p -> a + p.pesos }
            val free = payments.any { it.complimentary }
            check(free || received >= total) { "Pago insuficiente" }
            val tip = payments.fold(BigDecimal.ZERO) { a, p -> a + p.tipPesos }
            val sale =
                store.insert(
                    "sales",
                    mapOf(
                        "folio_venta" to nextFolio(db, "folio_venta", "sales"),
                        "folio_comanda" to order.id("folio_comanda"),
                        "fecha" to LocalDate.now(),
                        "hora" to LocalTime.now().withNano(0),
                        "mesa" to order.id("mesa"),
                        "personas" to order.id("personas"),
                        "id_cliente" to order["id_cliente"],
                        "status" to "pagado",
                        "monto_por_pagar" to total,
                        "monto_pagado" to total,
                        "propina" to tip,
                        "discount_type" to order.text("discount_type"),
                        "general_discount" to order.decimal("general_discount"),
                        "observaciones" to order.text("observaciones"),
                    ),
                    db,
                )
            items.forEach {
                store.insert("sale_items", lineValues(it) + mapOf("sale_id" to sale), db)
            }
            payments.forEach { p ->
                val id =
                    store.insert(
                        "sale_payments",
                        mapOf(
                            "sale_id" to sale,
                            "tipo_pago_id" to p.methodId,
                            "fecha" to LocalDate.now(),
                            "hora" to LocalTime.now().withNano(0),
                            "monto_pagado" to p.received,
                            "propina" to p.tipPesos,
                            "moneda" to p.currency,
                            "persona" to p.person,
                        ),
                        db,
                    )
                store.insert(
                    "native_payment_meta",
                    mapOf(
                        "payment_id" to id,
                        "factor" to p.factor,
                        "received" to p.received,
                        "tip_received" to p.tip,
                    ),
                    db,
                )
                p.assignments.forEach { (itemId, q) ->
                    store.insert(
                        "sale_item_assignment",
                        mapOf("sale_payment_id" to id, "order_item_id" to itemId),
                        db,
                    )
                    store.insert(
                        "native_item_quantities",
                        mapOf("payment_id" to id, "order_item_id" to itemId, "quantity" to q),
                        db,
                    )
                }
            }
            items
                .groupBy { it.productId }
                .forEach { (productId, group) ->
                    val product =
                        store
                            .rows("SELECT * FROM productos WHERE id=?", listOf(productId), db)
                            .firstOrNull() ?: error("Producto eliminado")
                    val used =
                        group.fold(BigDecimal.ZERO) { a, line ->
                            a +
                                line.quantity *
                                    (line.variantId?.let { variant ->
                                        store
                                            .rows(
                                                "SELECT factor_descarga FROM producto_variantes WHERE id=?",
                                                listOf(variant),
                                                db,
                                            )
                                            .firstOrNull()
                                            ?.decimal("factor_descarga")
                                            ?: error("Variante eliminada")
                                    } ?: BigDecimal.ONE)
                        }
                    store.update(
                        "productos",
                        productId,
                        mapOf("stock" to (product.decimal("stock") - used).max(BigDecimal.ZERO)),
                        db,
                    )
                    inventoryMove(db, productId, used, "salida", sale)
                }
            store.update(
                "orders",
                orderId,
                mapOf("status" to "pagado", "monto_pagado" to total, "monto_por_pagar" to total),
                db,
            )
            releaseTable(db, order.id("mesa"))
            CorporateStore.freezeSale(store, db, sale, user)
            sale
        }
    }

    private fun inventoryMove(
        db: SQLiteDatabase,
        productId: Long,
        quantity: BigDecimal,
        direction: String,
        saleId: Long?,
    ) {
        val name =
            store
                .rows("SELECT nombre FROM productos WHERE id=?", listOf(productId), db)
                .firstOrNull()
                ?.text("nombre")
                .orEmpty()
        val movement = store.insert(
            "native_inventory_moves",
            mapOf(
                "product_id" to productId,
                "product_name" to name,
                "quantity" to quantity,
                "direction" to direction,
                "fecha" to LocalDate.now(),
                "hora" to LocalTime.now().withNano(0),
                "sale_id" to saleId,
            ),
            db,
        )
        CorporateStore.freezeMove(store, db, movement, user)
    }

    fun adjustStock(id: Long, quantity: BigDecimal, entry: Boolean) {
        requireUser()
        require(quantity > BigDecimal.ZERO) { "La cantidad debe ser mayor que cero" }
        store.transaction { db ->
            val row = store.rows("SELECT * FROM productos WHERE id=?", listOf(id), db).first()
            require(entry || quantity <= row.decimal("stock")) {
                "La salida supera la existencia disponible"
            }
            store.update(
                "productos",
                id,
                mapOf(
                    "stock" to
                        (row.decimal("stock") + if (entry) quantity else -quantity).max(
                            BigDecimal.ZERO
                        )
                ),
                db,
            )
            inventoryMove(db, id, quantity, if (entry) "entrada" else "salida", null)
        }
    }

    fun inventoryProducts(): List<Row> {
        requireUser()
        return store.rows(
            """SELECT p.*,
            MAX(CASE WHEN m.direction='entrada' THEN m.fecha||' '||m.hora END) AS ultima_entrada,
            MAX(CASE WHEN m.direction='salida' THEN m.fecha||' '||m.hora END) AS ultima_salida
            FROM productos p LEFT JOIN native_inventory_moves m ON m.product_id=p.id
            GROUP BY p.id ORDER BY p.codigo,p.id"""
        )
    }

    fun inventoryMovements(product: Long, from: String, to: String): List<Row> {
        requireUser()
        require(!LocalDate.parse(from).isAfter(LocalDate.parse(to))) { "Rango de fechas inválido" }
        return store.rows(
            """SELECT m.*,p.codigo,p.precio AS precio_venta_actual
            FROM native_inventory_moves m LEFT JOIN productos p ON p.id=m.product_id
            WHERE m.product_id=? AND m.fecha BETWEEN ? AND ?
            ORDER BY m.fecha DESC,m.hora DESC,m.id DESC""",
            listOf(product, from, to),
        )
    }

    fun inventoryMovementTicket(product: Long, from: String, to: String): String {
        requireUser()
        val p =
            store.rows("SELECT * FROM productos WHERE id=?", listOf(product)).firstOrNull()
                ?: error("Producto inexistente")
        val rows = inventoryMovements(product, from, to)
        return buildString {
            appendLine(Ticket.center("MOVIMIENTOS DE INVENTARIO"))
            appendLine("${p.text("codigo")} · ${p.text("nombre")}")
            appendLine("$from / $to")
            appendLine(
                Ticket.pair(
                    "Existencia actual",
                    p.decimal("stock").stripTrailingZeros().toPlainString(),
                )
            )
            appendLine(Ticket.pair("Precio venta actual MXN", currency(p.decimal("precio"))))
            appendLine("---")
            rows.forEach { r ->
                appendLine("${r.text("fecha")} ${r.text("hora")}")
                appendLine(
                    Ticket.pair(
                        r.text("direction").replaceFirstChar { it.uppercase() },
                        r.decimal("quantity").stripTrailingZeros().toPlainString() + " uds.",
                    )
                )
                appendLine(
                    Ticket.pair("Precio venta actual", currency(r.decimal("precio_venta_actual")))
                )
                if (r.id("sale_id") > 0) appendLine("Venta #${r.id("sale_id")}")
                appendLine("---")
            }
            appendLine(Ticket.pair("Movimientos", rows.size.toString()))
            listOf("entrada" to "Total entradas", "salida" to "Total salidas").forEach {
                (direction, label) ->
                val quantity =
                    rows
                        .filter { it.text("direction") == direction }
                        .fold(BigDecimal.ZERO) { a, r -> a + r.decimal("quantity") }
                appendLine(
                    Ticket.pair(label, quantity.stripTrailingZeros().toPlainString() + " uds.")
                )
            }
            appendLine("El precio mostrado es de venta actual, no costo ni precio histórico.")
        }
    }

    fun report(section: String, from: String, to: String): List<Row> {
        requireUser()
        require(!LocalDate.parse(from).isAfter(LocalDate.parse(to))) { "Rango inválido" }
        if (section == "Cierres")
            return store.rows(
                "SELECT * FROM cierres WHERE fecha BETWEEN ? AND ? ORDER BY fecha,hora",
                listOf(from, to),
            )
        if (section == "Comandas")
            return store.rows(
                """SELECT s.*,s.id AS sale_id,s.monto_pagado AS total FROM sales s
            WHERE s.fecha BETWEEN ? AND ? ORDER BY s.fecha,s.hora,s.folio_comanda""",
                listOf(from, to),
            )
        val data = detailedReport(from, to)
        return when (section) {
            "Pagos" -> data.getValue("Pagos")
            "Categorías" -> data.getValue("Categorías")
            "Productos" ->
                data
                    .getValue("Detalle")
                    .groupBy { it.id("product_id") to it.text("producto") }
                    .map { (_, group) ->
                        val first = group.first()
                        mapOf(
                            "product_id" to first["product_id"],
                            "producto" to first["producto"],
                            "foto" to first["foto"],
                            "cantidad" to
                                group.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("cantidad") },
                            "vendido" to
                                money(
                                    group.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("vendido") }
                                ),
                        )
                    }
            "Ventas" ->
                data
                    .getValue("Detalle")
                    .groupBy { Triple(it.text("fecha"), it.id("product_id"), it.text("producto")) }
                    .map { (key, group) ->
                        mapOf(
                            "fecha" to key.first,
                            "producto" to key.third,
                            "product_id" to group.first()["product_id"],
                            "foto" to group.first()["foto"],
                            "cantidad" to
                                group.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("cantidad") },
                            "vendido" to
                                money(
                                    group.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("vendido") }
                                ),
                        )
                    }
                    .sortedWith(compareBy<Row> { it.text("fecha") }.thenBy { it.text("producto") })
            "Detalles comandas" ->
                data
                    .getValue("Detalle")
                    .groupBy { it.id("sale_id") to it.text("producto") }
                    .map { (_, group) ->
                        group.first() +
                            mapOf(
                                "cantidad" to
                                    group.fold(BigDecimal.ZERO) { a, r ->
                                        a + r.decimal("cantidad")
                                    },
                                "vendido" to
                                    money(
                                        group.fold(BigDecimal.ZERO) { a, r ->
                                            a + r.decimal("vendido")
                                        }
                                    ),
                            )
                    }
            else -> error("Reporte desconocido")
        }
    }

    fun paymentsForSale(sale: Long): List<Row> {
        requireUser()
        return store.rows(
            "SELECT sp.*,tp.nombre AS metodo FROM sale_payments sp LEFT JOIN tipo_pago tp ON tp.id=sp.tipo_pago_id WHERE sp.sale_id=? ORDER BY sp.id",
            listOf(sale),
        )
    }

    fun salesForProductDay(day: String, product: String): String {
        requireUser()
        val sales =
            store.rows(
                "SELECT DISTINCT s.id FROM sales s JOIN sale_items si ON si.sale_id=s.id WHERE s.fecha=? AND si.product_name=? ORDER BY s.id",
                listOf(day, product),
            )
        return Ticket.center("VENTAS DEL $day") +
            "\n" +
            product +
            "\nComandas que incluyen este producto\n---\n" +
            sales.joinToString("\n---\n") { receipt(it.id()) }
    }

    fun saleForOrder(folio: Long): Long? {
        requireUser()
        return store
            .rows("SELECT id FROM sales WHERE folio_comanda=?", listOf(folio))
            .firstOrNull()
            ?.id()
    }

    fun reportTicket(section: String, from: String, to: String, closure: Row? = null): String {
        val data = detailedReport(from, to, closure)
        val rows =
            if (closure == null) report(section, from, to)
            else data.getValue(if (section == "General") "Categorías" else "Detalle")
        return buildString {
            appendLine(
                Ticket.center(
                    store
                        .rows("SELECT nombre FROM Empresa LIMIT 1")
                        .firstOrNull()
                        .orEmpty()
                        .text("nombre")
                )
            )
            appendLine(
                Ticket.center(
                    if (closure == null) section.uppercase() else "CORTE ${closure.id()} · $section"
                )
            )
            appendLine(
                if (closure == null) "$from / $to"
                else "${closure.text("fecha")} ${closure.text("hora")}"
            )
            appendLine("---")
            if (section == "Detalles comandas" || (closure != null && section != "General")) {
                data.getValue("Comandas").forEach { sale ->
                    appendLine(Ticket.center("COMANDA ${sale.id("folio_comanda")}"))
                    data
                        .getValue("Detalle")
                        .filter { it.id("sale_id") == sale.id() }
                        .forEach { r ->
                            appendLine(
                                Ticket.pair(
                                    "${r.text("cantidad")} x ${r.text("producto")}",
                                    currency(r.decimal("vendido")),
                                )
                            )
                        }
                    appendLine(
                        Ticket.pair("TOTAL COMANDA MXN", currency(sale.decimal("monto_pagado")))
                    )
                    store
                        .rows(
                            "SELECT sp.*,tp.nombre AS metodo FROM sale_payments sp LEFT JOIN tipo_pago tp ON tp.id=sp.tipo_pago_id WHERE sp.sale_id=?",
                            listOf(sale.id()),
                        )
                        .forEach { p ->
                            appendLine(
                                Ticket.pair(
                                    "${p.text("metodo")} ${p.text("moneda")}",
                                    currency(p.decimal("monto_pagado")),
                                )
                            )
                            if (p.decimal("propina").signum() > 0)
                                appendLine(
                                    Ticket.pair(
                                        "Propina ${p.text("moneda")}",
                                        currency(p.decimal("propina")),
                                    )
                                )
                        }
                    appendLine("---")
                }
            } else {
                var day = ""
                rows.forEach { r ->
                    if (section == "Ventas" && day != r.text("fecha")) {
                        day = r.text("fecha")
                        appendLine(Ticket.center(day))
                    }
                    val label =
                        when (section) {
                            "Comandas" -> "#${r.id("folio_comanda")} ${r.text("status")}"
                            "Cierres" -> "${r.text("fecha")} ${r.text("tipo")}"
                            "Pagos" -> "${r.text("metodo")} ${r.text("moneda")}"
                            else ->
                                "${r.text("cantidad")} x ${r.text("producto").ifEmpty { r.text("categoria") }}"
                        }
                    val value =
                        when (section) {
                            "Comandas" -> r.decimal("total")
                            "Cierres" -> r.decimal("monto_total")
                            "Pagos" -> r.decimal("importe_moneda")
                            else -> r.decimal("vendido")
                        }
                    appendLine(Ticket.pair(label, currency(value)))
                }
            }
            appendLine("---")
            appendLine(
                Ticket.pair(
                    "TOTAL VENTAS MXN",
                    currency(data.getValue("Resumen").first().decimal("total_mxn")),
                )
            )
            appendLine(
                Ticket.pair(
                    "PROPINA MXN",
                    currency(data.getValue("Resumen").first().decimal("propinas_mxn")),
                )
            )
            appendLine(Ticket.center("PAGOS RECIBIDOS"))
            data.getValue("Pagos").forEach { p ->
                appendLine(
                    Ticket.pair(
                        "${p.text("metodo")} ${p.text("moneda")}",
                        currency(p.decimal("importe_moneda")),
                    )
                )
                appendLine(
                    Ticket.pair(
                        "Propina ${p.text("moneda")}",
                        currency(p.decimal("propina_moneda")),
                    )
                )
            }
            appendLine("Recibido puede incluir cambio. Ventas no incluyen propina.")
        }
    }

    /** Cortes originales son acumulados del día hasta su hora de registro. */
    fun detailedReport(from: String, to: String, closure: Row? = null): Map<String, List<Row>> {
        requireUser()
        require(!LocalDate.parse(from).isAfter(LocalDate.parse(to))) { "Rango inválido" }
        val condition =
            if (closure == null) "s.fecha BETWEEN ? AND ?" else "s.fecha=? AND s.hora<=?"
        val args =
            if (closure == null) listOf(from, to)
            else listOf(closure.text("fecha"), closure.text("hora"))
        val sales =
            store.rows(
                "SELECT s.* FROM sales s WHERE $condition ORDER BY s.fecha,s.hora,s.id",
                args,
            )
        val items =
            store
                .rows(
                    """SELECT s.id AS sale_id,s.fecha,s.folio_comanda,si.product_id,p.foto,si.product_name AS producto,
            COALESCE(c.name,'Sin categoría') AS categoria,si.quantity AS cantidad,si.price AS precio,
            si.total AS importe_antes_descuento_general,
            CASE WHEN (SELECT SUM(x.total) FROM sale_items x WHERE x.sale_id=s.id)>0
            THEN si.total*s.monto_pagado/(SELECT SUM(x.total) FROM sale_items x WHERE x.sale_id=s.id) ELSE 0 END AS vendido
            FROM sale_items si JOIN sales s ON s.id=si.sale_id
            LEFT JOIN productos p ON p.id=si.product_id LEFT JOIN categories c ON c.id=p.category_id
            WHERE $condition ORDER BY s.id,si.id""",
                    args,
                )
                .let { raw ->
                    val totals = sales.associate { it.id() to money(it.decimal("monto_pagado")) }
                    raw.groupBy { it.id("sale_id") }
                        .flatMap { (id, lines) ->
                            var remaining = totals[id] ?: BigDecimal.ZERO
                            lines.mapIndexed { index, row ->
                                val allocated =
                                    if (index == lines.lastIndex) remaining
                                    else
                                        money(row.decimal("vendido"))
                                            .max(BigDecimal.ZERO)
                                            .min(remaining)
                                remaining -= allocated
                                row + ("vendido" to allocated)
                            }
                        }
                }
        fun grouped(key: String) =
            items
                .groupBy { it.text(key) }
                .map { (label, rows) ->
                    mapOf(
                        key to label,
                        "cantidad" to
                            rows.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("cantidad") },
                        "vendido" to
                            money(rows.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("vendido") }),
                    )
                }
        val payments =
            store.rows(
                """SELECT COALESCE(tp.nombre,'Sin método') AS metodo,sp.moneda,
            SUM(sp.monto_pagado) AS importe_moneda,SUM(sp.propina) AS propina_moneda,
            SUM(sp.monto_pagado+sp.propina) AS total_moneda,
            SUM(CASE WHEN sp.moneda<>'MXN' AND nm.factor IS NULL THEN 1 ELSE 0 END) AS pagos_sin_factor,
            SUM(sp.monto_pagado * COALESCE(CAST(nm.factor AS REAL),CASE WHEN sp.moneda='MXN' THEN 1 ELSE NULL END)) AS equivalente_mxn
            FROM sale_payments sp JOIN sales s ON s.id=sp.sale_id
            LEFT JOIN tipo_pago tp ON tp.id=sp.tipo_pago_id
            LEFT JOIN native_payment_meta nm ON nm.payment_id=sp.id WHERE $condition
            GROUP BY sp.tipo_pago_id,sp.moneda ORDER BY metodo,sp.moneda""",
                args,
            )
        val total = sales.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("monto_pagado") }
        return linkedMapOf(
            "Resumen" to
                listOf(
                    mapOf(
                        "desde" to (closure?.text("fecha") ?: from),
                        "hasta" to
                            (closure?.let { it.text("fecha") + " " + it.text("hora") } ?: to),
                        "ventas" to sales.size,
                        "total_mxn" to total,
                        "propinas_mxn" to
                            sales.fold(BigDecimal.ZERO) { a, r -> a + r.decimal("propina") },
                    )
                ),
            "Comandas" to sales,
            "Categorías" to grouped("categoria"),
            "Productos" to grouped("producto"),
            "Detalle" to items,
            "Pagos" to payments,
        )
    }

    fun closeDay(type: String, notes: String): Long {
        requireUser()
        require(type in listOf("parcial", "total"))
        return store.transaction { db ->
            val today = LocalDate.now().toString()
            check(
                store
                    .rows(
                        "SELECT id FROM cierres WHERE fecha=? AND tipo='total'",
                        listOf(today),
                        db,
                    )
                    .isEmpty()
            ) {
                "El día ya tiene cierre total"
            }
            val total =
                store
                    .rows(
                        "SELECT COALESCE(SUM(monto_pagado),0) AS total FROM sales WHERE fecha=?",
                        listOf(today),
                        db,
                    )
                    .first()
                    .decimal("total")
            nextFolio(db, "folio_cierre", "cierres")
            val closure = store.insert(
                "cierres",
                mapOf(
                    "tipo" to type,
                    "fecha" to today,
                    "hora" to LocalTime.now().withNano(0),
                    "monto_total" to total,
                    "observaciones" to notes,
                ),
                db,
            )
            CorporateStore.queueClosure(store, db, closure, user)
            closure
        }
    }

    fun receipt(sale: Long): String {
        requireUser()
        val row = store.rows("SELECT * FROM sales WHERE id=?", listOf(sale)).first()
        val company = store.rows("SELECT * FROM Empresa LIMIT 1").firstOrNull().orEmpty()
        val payments =
            store.rows(
                "SELECT sp.*,tp.nombre AS metodo,nm.factor FROM sale_payments sp LEFT JOIN tipo_pago tp ON tp.id=sp.tipo_pago_id LEFT JOIN native_payment_meta nm ON nm.payment_id=sp.id WHERE sale_id=?",
                listOf(sale),
            )
        val items = saleLines(sale)
        val subtotal = items.fold(BigDecimal.ZERO) { a, l -> a + l.net }
        return buildString {
            appendLine(Ticket.center(company.text("nombre")))
            listOf("rfc", "direccion", "telefono").forEach {
                if (company.text(it).isNotBlank()) appendLine(Ticket.center(company.text(it)))
            }
            appendLine(Ticket.center("NOTA DE VENTA ${row.id("folio_venta")}"))
            appendLine("Comanda ${row.id("folio_comanda")} · Mesa ${row.id("mesa")}")
            appendLine("${row.text("fecha")} ${row.text("hora")}")
            appendLine("---")
            items.forEach { line ->
                appendLine(line.name)
                appendLine(
                    Ticket.pair(
                        "${line.quantity.stripTrailingZeros().toPlainString()} x ${currency(line.price)}",
                        currency(line.net),
                    )
                )
                if (line.discountAmount.signum() > 0)
                    appendLine(Ticket.pair("Desc. artículo", currency(line.discountAmount)))
            }
            appendLine("---")
            appendLine(Ticket.pair("Subtotal MXN", currency(subtotal)))
            appendLine(
                Ticket.pair(
                    "Descuento general",
                    currency((subtotal - row.decimal("monto_pagado")).max(BigDecimal.ZERO)),
                )
            )
            appendLine(Ticket.pair("TOTAL MXN", currency(row.decimal("monto_pagado"))))
            appendLine(Ticket.pair("Propina MXN", currency(row.decimal("propina"))))
            appendLine(Ticket.center("PAGOS RECIBIDOS"))
            payments.forEach { p ->
                appendLine(
                    Ticket.pair(
                        "${p.text("metodo")} ${p.text("moneda")}",
                        currency(p.decimal("monto_pagado")),
                    )
                )
                if (p.text("persona").isNotBlank()) appendLine(p.text("persona"))
                if (p.decimal("propina").signum() > 0)
                    appendLine(
                        Ticket.pair("Propina ${p.text("moneda")}", currency(p.decimal("propina")))
                    )
            }
            if (payments.all { it.text("moneda") == "MXN" || it.decimal("factor").signum() > 0 }) {
                val received =
                    payments.fold(BigDecimal.ZERO) { a, p ->
                        a +
                            p.decimal("monto_pagado") *
                                (if (p.text("moneda") == "MXN") BigDecimal.ONE
                                else p.decimal("factor"))
                    }
                appendLine(Ticket.pair("Recibido MXN", currency(received)))
                appendLine(
                    Ticket.pair(
                        "Cambio MXN",
                        currency((received - row.decimal("monto_pagado")).max(BigDecimal.ZERO)),
                    )
                )
            }
            if (row.text("observaciones").isNotBlank()) appendLine(row.text("observaciones"))
            appendLine("---")
            appendLine(Ticket.center("Gracias por su visita"))
        }
    }

    fun orderReceipt(id: Long): String {
        val row = order(id)
        val sale = saleForOrder(row.id("folio_comanda"))
        if (sale != null) return receipt(sale)
        val items = lines(id)
        val subtotal = items.fold(BigDecimal.ZERO) { a, l -> a + l.net }
        val total = Totals.total(items, row.text("discount_type"), row.decimal("general_discount"))
        return buildString {
            appendLine(Ticket.center("COMANDA ${row.id("folio_comanda")}"))
            appendLine("Mesa ${row.id("mesa")} · Personas ${row.id("personas")}")
            appendLine("---")
            items.forEach {
                appendLine(it.name)
                appendLine(
                    Ticket.pair(
                        "${it.quantity.stripTrailingZeros().toPlainString()} x ${currency(it.price)}",
                        currency(it.net),
                    )
                )
            }
            appendLine("---")
            appendLine(Ticket.pair("Subtotal MXN", currency(subtotal)))
            appendLine(Ticket.pair("Descuento general", currency(subtotal - total)))
            appendLine(Ticket.pair("TOTAL MXN", currency(total)))
            appendLine(row.text("observaciones"))
        }
    }
}
