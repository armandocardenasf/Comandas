package com.enverolab.comandas.corporate

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import com.enverolab.comandas.data.*
import com.enverolab.comandas.domain.*
import org.json.JSONObject

class CorporateManager(val context: Context, val store: PosDatabase) {
    private val secrets = SecretStore(context)

    private fun get(k: String) = CorporateStore.get(store, k)

    private fun set(k: String, v: String) = CorporateStore.set(store, k, v)

    fun enabled() = get("enabled") == "1"

    fun url() = get("url")

    fun terminal() = get("terminal").ifBlank { "1" }

    fun pending() =
        store
            .rows("SELECT COUNT(*) AS n FROM native_corporate_outbox WHERE status<>'confirmed'")
            .first()
            .id("n")

    fun info() =
        "Empresa: ${get("company_name")} · Terminal ${terminal()}\nCatálogo: ${get("revision").ifBlank{"0"}} · Cortes pendientes: ${pending()}\nÚltima comunicación: ${get("last_sync").ifBlank{"Sin sincronizar"}}\n${get("last_error")}"

    private fun client(): CorporateClient {
        check(
            get("installation") == secrets.get("installation") &&
                secrets.get("terminal").isNotBlank()
        ) {
            "Identidad del equipo inválida; requiere registro supervisado"
        }
        return CorporateClient(url(), secrets.get("terminal"), secrets.get("access"))
    }

    fun configure(url: String, number: String, active: Boolean) {
        require(number.toIntOrNull()?.let { it > 0 } == true) { "Número de terminal inválido" }
        if (url.isNotBlank()) CorporateClient(url, "")
        if (
            get("installation").isNotBlank() &&
                (url.trimEnd('/') != this.url() || number != terminal())
        )
            error(
                "La terminal registrada requiere un proceso supervisado para cambiar de servidor o número"
            )
        if (!active) check(pending() == 0L) { "Envíe los cortes pendientes antes de desactivar" }
        if (active)
            check(secrets.get("terminal").isNotBlank() && get("revision").isNotBlank()) {
                "Registre y prepare primero la terminal"
            }
        set("url", url.trimEnd('/'))
        set("terminal", number)
        set("enabled", if (active) "1" else "0")
        if (active) CorporateWorker.periodic(context) else CorporateWorker.cancel(context)
    }

    fun enroll(url: String, company: String, id: String, code: String, adopt: Boolean) {
        check(get("installation").isBlank()) { "Este equipo ya está registrado" }
        check(pending() == 0L) { "Hay cortes pendientes" }
        check(store.rows("SELECT id FROM orders WHERE status='abierto' LIMIT 1").isEmpty()) {
            "Finalice las comandas abiertas antes de registrar"
        }
        val history = store.rows("SELECT id FROM sales LIMIT 1").isNotEmpty()
        check(!history || adopt) { "Confirme la conciliación de catálogos existentes" }
        val install = UUID.randomUUID().toString()
        val r =
            CorporateClient(url, "")
                .json(
                    "mobile/enroll/",
                    JSONObject()
                        .put("company", company)
                        .put("terminal", id)
                        .put("code", code)
                        .put("installation", install),
                )
        secrets.put("terminal", r.getString("terminal_token"))
        secrets.put("installation", install)
        val c = r.getJSONObject("company")
        store.transaction { db ->
            for ((k, v) in
                mapOf(
                    "url" to url.trimEnd('/'),
                    "installation" to install,
                    "company_id" to c.getString("id"),
                    "company_name" to c.getString("name"),
                    "terminal" to r.getInt("number").toString(),
                    "adopt" to if (adopt) "1" else "0",
                    "enabled" to "0",
                )) CorporateStore.set(store, k, v, db)
        }
        syncCatalog()
        set("enabled", "1")
        CorporateWorker.periodic(context)
        CorporateWorker.schedule(context)
    }

    fun ping(): String {
        val r = client().json("mobile/ping/")
        return "Comunicación correcta · ${r.getString("company")} · Terminal ${r.getInt("terminal")}"
    }

    fun canRestore(name: String) =
        !enabled() || (get("lease:$name").toLongOrNull() ?: 0) > System.currentTimeMillis()

    fun login(repo: PosRepository, name: String, password: String) {
        if (!enabled()) {
            repo.login(name, password)
            return
        }
        try {
            val r =
                client()
                    .json(
                        "mobile/login/",
                        JSONObject().put("username", name).put("password", password),
                    )
            val u = r.getJSONObject("user")
            secrets.put("access", r.getString("access"))
            secrets.put("refresh", r.getString("refresh"))
            val role =
                when (u.getString("role")) {
                    "admin" -> "admin"
                    "supervisor" -> "super_cajero"
                    else -> "cajero"
                }
            val days = r.optInt("offline_days", 7).coerceIn(0, 30)
            store.transaction { db ->
                val row =
                    store
                        .rows("SELECT id FROM users WHERE username=?", listOf(name), db)
                        .firstOrNull()
                val values =
                    mapOf(
                        "username" to name,
                        "password" to Passwords.hash(password),
                        "role" to role,
                        "status" to "Activo",
                        "permissions" to "",
                    )
                if (row == null) store.insert("users", values, db)
                else store.update("users", row.id(), values, db)
                CorporateStore.set(
                    store,
                    "lease:$name",
                    (System.currentTimeMillis() + days * 86400000L).toString(),
                    db,
                )
            }
        } catch (e: IOException) {
            check(canRestore(name)) {
                "Sin conexión; el usuario requiere validación con el servidor"
            }
        } catch (e: CorporateHttpError) {
            if (e.status in listOf(401, 403)) set("lease:$name", "0")
            throw e
        }
        repo.login(name, password)
        try {
            sendPending()
            syncCatalog()
        } catch (e: Exception) {
            set("last_error", "Acceso autorizado. Sincronización pendiente: ${e.message}")
        }
    }

    fun logout() {
        secrets.remove("access")
        secrets.remove("refresh")
    }

    @Synchronized
    fun sendPending(): String {
        if (!enabled()) return "Modo independiente"
        var sent = 0
        for (r in
            store.rows(
                "SELECT * FROM native_corporate_outbox WHERE status<>'confirmed' ORDER BY closure_id"
            )) {
            try {
                val payload = JSONObject(r.text("payload"))
                val result = client().json("mobile/batches/", payload)
                check(
                    result.getString("status") == "confirmed" &&
                        result.getString("id") == r.text("id")
                ) {
                    "Acuse incompleto"
                }
                store.transaction { db ->
                    db.execSQL(
                        "UPDATE native_corporate_outbox SET status='confirmed',receipt=?,error='',attempts=attempts+1 WHERE id=?",
                        arrayOf(result.getString("receipt"), r.text("id")),
                    )
                    val a = payload.getJSONArray("inventory")
                    var high = CorporateStore.get(store, "last_move", db).toLongOrNull() ?: 0
                    for (i in 0 until a.length()) high =
                        maxOf(high, a.getJSONObject(i).getLong("id"))
                    CorporateStore.set(store, "last_move", high.toString(), db)
                }
                sent++
            } catch (e: Exception) {
                store.writableDatabase.execSQL(
                    "UPDATE native_corporate_outbox SET error=?,attempts=attempts+1 WHERE id=?",
                    arrayOf(e.message.orEmpty(), r.text("id")),
                )
                set("last_error", e.message.orEmpty())
                throw e
            }
        }
        set("last_sync", Instant.now().toString())
        set("last_error", "")
        return "$sent cortes confirmados. Pendientes: ${pending()}"
    }

    @Synchronized
    fun syncCatalog(): String {
        check(store.rows("SELECT id FROM orders WHERE status='abierto' LIMIT 1").isEmpty()) {
            "Actualice catálogos después de terminar las comandas abiertas; puede seguir enviando cortes"
        }
        val r = client().json("mobile/catalog/?since=0")
        val entries = r.getJSONArray("entries")
        val assets = r.getJSONArray("assets")
        val images = mutableMapOf<String, String>()
        val folder = File(context.filesDir, "photos/corporate").apply { mkdirs() }
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val id = a.getString("id")
            val hash = a.getString("sha256")
            val file = File(folder, "$id-$hash.jpg")
            if (!file.exists()) {
                val raw = client().bytes("mobile/assets/$id/")
                val checksum =
                    MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {
                        "%02x".format(it)
                    }
                check(checksum == hash) { "Imagen incompleta" }
                val tmp = File(folder, "$id.tmp")
                tmp.writeBytes(raw)
                check(tmp.renameTo(file)) { "No se pudo guardar imagen" }
            }
            images[id] = file.path
        }
        store.transaction { db ->
            val first = get("revision").isBlank()
            val empty =
                store.rows("SELECT id FROM sales LIMIT 1", db = db).isEmpty() &&
                    store.rows("SELECT id FROM orders LIMIT 1", db = db).isEmpty() &&
                    store.rows("SELECT id FROM native_inventory_moves LIMIT 1", db = db).isEmpty()
            if (first && empty)
                listOf(
                        "menu",
                        "producto_variantes",
                        "productos",
                        "categories",
                        "mesas",
                        "clientes",
                        "empleados",
                        "tipo_pago",
                        "Empresa",
                    )
                    .forEach { db.delete(it, null, null) }
            val order =
                listOf(
                    "categories",
                    "productos",
                    "producto_variantes",
                    "empleados",
                    "clientes",
                    "mesas",
                    "tipo_pago",
                    "menu",
                    "Empresa",
                )
            val list =
                List(entries.length()) { entries.getJSONObject(it) }
                    .sortedBy { order.indexOf(it.getString("kind")) }
            for (e in list) {
                val kind = e.getString("kind")
                check(kind in order)
                val remote = e.getLong("local_id")
                val data = e.getJSONObject("data")
                val values = mutableMapOf<String, Any?>()
                val allowed =
                    store.rows("PRAGMA table_xinfo($kind)", db = db).map { it.text("name") }.toSet()
                data.keys().forEach { k ->
                    if (k in allowed && k !in listOf("id", "stock", "disponibilidad"))
                        values[k] = if (data.isNull(k)) null else data.get(k)
                }
                for ((field, target) in
                    listOf(
                        "category_id" to "categories",
                        "producto_id" to "productos",
                        "product_id" to "productos",
                    )) values[field]?.toString()?.toLongOrNull()?.let { v ->
                    values[field] =
                        store
                            .rows(
                                "SELECT local_id FROM native_corporate_map WHERE kind=? AND remote_id=?",
                                listOf(target, v),
                                db,
                            )
                            .firstOrNull()
                            ?.id("local_id") ?: error("Referencia corporativa incompleta")
                }
                for (field in listOf("foto", "logo")) values[field]
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { values[field] = images[it] ?: error("Imagen pendiente") }
                var local =
                    store
                        .rows(
                            "SELECT local_id FROM native_corporate_map WHERE kind=? AND remote_id=?",
                            listOf(kind, remote),
                            db,
                        )
                        .firstOrNull()
                        ?.id("local_id")
                if (local == null && first && !empty) {
                    check(get("adopt") == "1") { "Requiere conciliación de catálogo" }
                    val key =
                        when (kind) {
                            "productos" -> "codigo"
                            "categories" -> "name"
                            "mesas" -> "numero"
                            "tipo_pago" -> "nombre"
                            else -> null
                        }
                    val matches =
                        if (key != null && values[key] != null)
                            store.rows("SELECT id FROM $kind WHERE $key=?", listOf(values[key]), db)
                        else if (kind == "Empresa")
                            store.rows("SELECT id FROM Empresa LIMIT 1", db = db)
                        else emptyList()
                    check(matches.size <= 1) { "Catálogo ambiguo: $kind" }
                    local = matches.firstOrNull()?.id()
                }
                if (kind in listOf("productos", "empleados") && !e.getBoolean("active"))
                    values["status"] = "No Activo"
                if (local == null) {
                    if (kind == "productos") values["stock"] = 0
                    local = store.insert(kind, values, db)
                } else store.update(kind, local, values, db)
                db.execSQL(
                    "INSERT OR REPLACE INTO native_corporate_map(kind,remote_id,local_id) VALUES (?,?,?)",
                    arrayOf<Any>(kind, remote, local),
                )
                CorporateStore.set(
                    store,
                    "inactive:$kind:$local",
                    if (e.getBoolean("active")) "0" else "1",
                    db,
                )
            }
            CorporateStore.set(
                store,
                "company_name",
                r.getJSONObject("company").getString("name"),
                db,
            )
            CorporateStore.set(store, "revision", r.getLong("revision").toString(), db)
            db.execSQL(
                "UPDATE productos SET status='No Activo' WHERE id NOT IN (SELECT local_id FROM native_corporate_map WHERE kind='productos')"
            )
        }
        allocations()
        val revision = r.getLong("revision")
        client()
            .json(
                "mobile/catalog/",
                JSONObject()
                    .put("revision", revision)
                    .put("images_revision", revision)
                    .put("pending", pending()),
            )
        set("last_sync", Instant.now().toString())
        set("last_error", "")
        return "Catálogos, configuración e imágenes actualizados"
    }

    private fun allocations() {
        val list = client().json("mobile/allocations/").getJSONArray("allocations")
        for (i in 0 until list.length()) {
            val a = list.getJSONObject(i)
            val id = a.getString("id")
            store.transaction { db ->
                if (CorporateStore.get(store, "allocation:$id", db) != "1") {
                    val local =
                        store
                            .rows(
                                "SELECT local_id FROM native_corporate_map WHERE kind='productos' AND remote_id=?",
                                listOf(a.getLong("product_id")),
                                db,
                            )
                            .firstOrNull()
                            ?.id("local_id") ?: error("Producto pendiente de descarga")
                    val product =
                        store.rows("SELECT * FROM productos WHERE id=?", listOf(local), db).first()
                    val q = a.getString("quantity").toBigDecimal()
                    val direction = a.getString("direction")
                    check(q.signum() > 0 && direction in listOf("entrada", "salida"))
                    if (direction == "salida")
                        check(product.decimal("stock") >= q) {
                            "Existencias insuficientes para transferencia"
                        }
                    store.update(
                        "productos",
                        local,
                        mapOf(
                            "stock" to
                                if (direction == "entrada") product.decimal("stock") + q
                                else product.decimal("stock") - q
                        ),
                        db,
                    )
                    val move =
                        store.insert(
                            "native_inventory_moves",
                            mapOf(
                                "product_id" to local,
                                "product_name" to product.text("nombre"),
                                "quantity" to q,
                                "direction" to direction,
                                "fecha" to java.time.LocalDate.now(),
                                "hora" to java.time.LocalTime.now().withNano(0),
                            ),
                            db,
                        )
                    CorporateStore.freezeMove(store, db, move)
                    CorporateStore.set(store, "allocation:$id", "1", db)
                }
            }
            client().json("mobile/allocations/", JSONObject().put("id", id))
        }
    }
}
